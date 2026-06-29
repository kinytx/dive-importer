package cn.diveplan.importer.data

import android.util.Base64
import cn.diveplan.importer.ble.GarminBleException
import cn.diveplan.importer.ble.GarminBleGattClient
import cn.diveplan.importer.ble.GarminGattSession
import cn.diveplan.importer.data.network.DivePlanEndpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Garmin BLE ↔ WebSocket sidecar 桥接会话（P5）。
 *
 * 职责：
 *  1. 连接 Garmin BLE GATT（由 [GarminBleGattClient] 完成）
 *  2. 开 WebSocket 到 garmin-sidecar（wss://api.diveplan.cn/ws/garmin-ble-bridge）
 *     - X-Api-Key 由 OkHttp [ApiKeyAuthInterceptor] 自动注入
 *  3. 把 BLE notify 字节转发给 sidecar（transport.notify）
 *  4. 把 sidecar transport.write 写到 BLE write characteristic
 *  5. 收到 device.dive → 写 .fit 文件到 filesDir/import-dumps/garmin-<addr>/<ts>.fit
 *  6. 收到 session.done → 发 [GarminBridgeEvent.Done]
 *
 * 调用方：
 *   val session = GarminWssBridgeSession(...)
 *   session.events.collect { event -> ... }
 *   session.start(address, deviceName, product)
 *   // 在 onCleared 或导航返回时：
 *   session.cancel()
 */
@Singleton
class GarminWssBridgeSession @Inject constructor(
    private val gattClient: GarminBleGattClient,
    private val dumpRepository: DumpRepository,
    private val okHttpClient: OkHttpClient,
) {
    private val _events = Channel<GarminBridgeEvent>(Channel.UNLIMITED)
    val events: Flow<GarminBridgeEvent> = _events.receiveAsFlow()

    private var bridgeScope: CoroutineScope? = null
    private var ws: WebSocket? = null
    private var gattSession: GarminGattSession? = null
    private var sessionId: String = ""

    /**
     * 开始桥接。
     * 协程在内部启动；通过 [events] 获取进度 / 结果。
     * @param address    Garmin 设备 BLE 地址
     * @param deviceName 广播名，用于本地文件目录名
     * @param product    VendorMatch.product，透传给 sidecar
     */
    fun start(address: String, deviceName: String?, product: String) {
        cancel()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { bridgeScope = it }
        sessionId = "garmin-android-${System.currentTimeMillis().toString(36)}"

        scope.launch {
            runCatching {
                emit(GarminBridgeEvent.Status("连接 Garmin GATT 服务..."))
                val gatt = gattClient.connect(address).also { gattSession = it }

                emit(GarminBridgeEvent.Status("连接 sidecar WebSocket..."))
                openWebSocket(address, deviceName, product, gatt, scope)
            }.onFailure { e ->
                emit(GarminBridgeEvent.Error(
                    when (e) {
                        is GarminBleException -> e.message ?: "BLE 错误"
                        else                  -> "启动失败: ${e.message}"
                    }
                ))
            }
        }
    }

    fun cancel() {
        runCatching { ws?.cancel() }
        runCatching { gattSession?.close() }
        bridgeScope?.cancel()
        bridgeScope = null
        ws = null
        gattSession = null
    }

    // ── WebSocket ──────────────────────────────────────────────

    private fun openWebSocket(
        address: String,
        deviceName: String?,
        product: String,
        gatt: GarminGattSession,
        scope: CoroutineScope,
    ) {
        // OkHttp WS 超时配置：读超时过短会在等待 sidecar 响应时断开
        val wsClient = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)   // 无限读超时
            .build()

        val req = Request.Builder()
            .url(DivePlanEndpoints.GARMIN_WSS_URL)
            .build()

        var doneReceived = false
        val fitFiles = mutableListOf<File>()

        wsClient.newWebSocket(req, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                ws = webSocket
                // 发 session.open
                val msg = JSONObject().apply {
                    put("type",        "session.open")
                    put("sessionId",   sessionId)
                    put("driver",      "garmin-sidecar")
                    put("channel",     "ble-bridge")
                    put("mtu",         20)
                    put("deviceName",  deviceName ?: "")
                    put("product",     product)
                    put("transport", JSONObject().apply {
                        put("serviceId",   GarminBleGattClient.SERVICE_UUID.toString().uppercase())
                        put("notifyCharId",GarminBleGattClient.NOTIFY_UUID.toString().uppercase())
                        put("writeCharId", GarminBleGattClient.WRITE_UUID.toString().uppercase())
                    })
                }
                webSocket.send(msg.toString())
                emit(GarminBridgeEvent.Status("sidecar 会话已打开，等待手表响应..."))

                // 把 BLE notify 字节转发给 sidecar
                scope.launch {
                    gatt.notifyFlow.collect { bytes ->
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val notify = JSONObject().apply {
                            put("type",      "transport.notify")
                            put("sessionId", sessionId)
                            put("data",      b64)
                        }
                        webSocket.send(notify.toString())
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (doneReceived) return
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (obj.optString("type")) {
                    "session.opened" -> {
                        emit(GarminBridgeEvent.Status("sidecar 就绪，等待手表响应..."))
                    }
                    "transport.write" -> {
                        val data = obj.optString("data").ifBlank { null }
                            ?: obj.optString("hex").ifBlank { null }
                            ?: return
                        scope.launch {
                            runCatching {
                                val bytes = if (looksLikeHex(data)) hexToBytes(data)
                                            else Base64.decode(data, Base64.DEFAULT)
                                gatt.write(bytes)
                                // writeAck
                                val ack = JSONObject().apply {
                                    put("type",      "transport.writeAck")
                                    put("sessionId", sessionId)
                                }
                                webSocket.send(ack.toString())
                            }.onFailure { e ->
                                emit(GarminBridgeEvent.Error("BLE 写入失败: ${e.message}"))
                                webSocket.cancel()
                            }
                        }
                    }
                    "device.progress" -> {
                        val cur = obj.optInt("current", 0)
                        val max = if (obj.has("maximum")) obj.optInt("maximum") else null
                        emit(GarminBridgeEvent.Progress(cur, max))
                    }
                    "device.directory" -> {
                        val queued = obj.optInt("queued", 0)
                        val total  = obj.optInt("total", 0)
                        emit(GarminBridgeEvent.Status("设备目录：共 $total 个文件，$queued 个待下载"))
                    }
                    "device.dive" -> {
                        val b64 = obj.optString("data").ifBlank { null } ?: return
                        val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return
                        val fileName = obj.optString("fileName").ifBlank { null }
                            ?: "garmin-${System.currentTimeMillis()}.fit"
                        val file = dumpRepository.saveFit(address, deviceName, bytes, fileName)
                        fitFiles += file
                        emit(GarminBridgeEvent.FitReceived(file, fitFiles.size))
                    }
                    "device.fileSkipped" -> {
                        emit(GarminBridgeEvent.Status("跳过文件 ${obj.optString("remoteKey")}"))
                    }
                    "session.done" -> {
                        doneReceived = true
                        emit(GarminBridgeEvent.Done(fitFiles.toList()))
                        webSocket.close(1000, null)
                    }
                    "device.error", "error" -> {
                        emit(GarminBridgeEvent.Error(obj.optString("message", "Garmin sidecar 错误")))
                        webSocket.cancel()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!doneReceived)
                    emit(GarminBridgeEvent.Error("WebSocket 错误: ${t.message}"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!doneReceived && code != 1000)
                    emit(GarminBridgeEvent.Error("WebSocket 已断开 code=$code $reason".trim()))
            }
        })
    }

    private fun emit(event: GarminBridgeEvent) {
        _events.trySend(event)
    }

    // ── 工具 ────────────────────────────────────────────────────

    private fun looksLikeHex(value: String): Boolean {
        val clean = value.replace("\\s".toRegex(), "")
        return clean.isNotEmpty() && clean.length % 2 == 0 && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace("\\s".toRegex(), "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

/** GarminWssBridgeSession 向外发出的事件 */
sealed interface GarminBridgeEvent {
    data class Status(val message: String) : GarminBridgeEvent
    data class Progress(val current: Int, val maximum: Int?) : GarminBridgeEvent
    data class FitReceived(val file: File, val totalSoFar: Int) : GarminBridgeEvent
    data class Done(val files: List<File>) : GarminBridgeEvent
    data class Error(val message: String) : GarminBridgeEvent
}
