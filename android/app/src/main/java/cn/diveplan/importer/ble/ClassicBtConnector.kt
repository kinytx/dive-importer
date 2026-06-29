package cn.diveplan.importer.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 经典蓝牙 RFCOMM/SPP 连接 + 原始 dump 抓取。
 *
 * 背景：Shearwater Petrel / Petrel 2 等老设备 GATT service 列表为空，
 * BLE GATT 路径走不通，必须走经典蓝牙 RFCOMM/SPP。
 *
 * 打开 socket 三段式 fallback（按优先级）：
 *   1. createRfcommSocketToServiceRecord(SPP_UUID)   ← 标准 secure
 *   2. createInsecureRfcommSocketToServiceRecord(SPP_UUID)  ← 无 PIN 时更稳
 *   3. 反射 createRfcommSocket(channel=1)            ← 极少数厂商 ROM buggy 时兜底
 *
 * 数据读取：
 *   - 用 available() + 短 sleep 轮询，不在主线程阻塞，同时能响应 coroutine cancel
 *   - [INACTIVITY_TIMEOUT_MS] 内无新数据 → 认为传输结束（Shearwater 在表上操作传输后会停止发送）
 *   - 调用方负责把返回的 ByteArray 落盘（[cn.diveplan.importer.data.DumpRepository]）
 *
 * SPP UUID = 00001101-0000-1000-8000-00805F9B34FB（蓝牙 SIG 标准值；Shearwater 用这个）
 */
@Singleton
class ClassicBtConnector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** 连续无新数据超过此时间 → 认为传输完毕 */
        const val INACTIVITY_TIMEOUT_MS: Long = 8_000L

        /** 单块读缓冲（字节） */
        private const val CHUNK: Int = 4_096

        /** 整体上限 40 MB：正常 dump < 2 MB；超限说明读到异常数据 */
        private const val MAX_BYTES: Int = 40 * 1024 * 1024

        /** available() 为 0 时的轮询间隔 */
        private const val POLL_MS: Long = 80L
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /**
     * 从系统已配对设备列表中筛选能被 [VendorDetector] 识别的潜水电脑（排除 Garmin weak 设备）。
     * 返回列表按 name 排序，供 ScanScreen「已配对设备」分区显示。
     *
     * 调用前需确保 [BlePermissions.hasAllPermissions] 为 true（需要 BLUETOOTH_CONNECT）。
     */
    @SuppressLint("MissingPermission")
    fun bondedDiveComputers(): List<BondedDiveComputer> {
        val a = adapter ?: return emptyList()
        return try {
            a.bondedDevices.orEmpty().mapNotNull { dev ->
                val name = try { dev.name } catch (_: SecurityException) { null }
                val match = VendorDetector.detect(name, emptyList())
                // weak = Garmin 等走 sidecar 通道；v1 先不展示，只列 RFCOMM 可抓的 vendor
                if (match is VendorMatch.Hit && !match.weak) {
                    BondedDiveComputer(address = dev.address, name = name, vendorMatch = match)
                } else null
            }.sortedWith(compareBy(nullsLast()) { it.name })
        } catch (_: SecurityException) { emptyList() }
    }

    /**
     * 连接 + dump 抓取主入口。运行在 [Dispatchers.IO]；支持 coroutine cancel。
     *
     * @param address      目标设备 MAC（来自已配对列表 or BLE 广播）
     * @param onProgress   进度回调 (bytesReceived)；从 IO 线程调，调用方用 MutableStateFlow 转 UI
     * @return 成功时返回原始 dump ByteArray；失败抛 [ClassicBtException]
     */
    @SuppressLint("MissingPermission")
    suspend fun captureRawDump(
        address: String,
        onProgress: (bytesReceived: Int) -> Unit = {},
    ): ByteArray = withContext(Dispatchers.IO) {
        val a = adapter ?: throw ClassicBtException.NoAdapter

        val device = try {
            a.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw ClassicBtException.InvalidAddress(address)
        }

        val socket = openSocket(device)

        try {
            socket.connect()
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw ClassicBtException.ConnectFailed(e.message ?: e.javaClass.simpleName)
        }

        val out = ByteArrayOutputStream()
        try {
            val buf = ByteArray(CHUNK)
            val inputStream = socket.inputStream
            var deadlineMs = System.currentTimeMillis() + INACTIVITY_TIMEOUT_MS

            while (isActive) {
                val avail = try {
                    inputStream.available()
                } catch (e: IOException) {
                    throw ClassicBtException.ReadFailed(e.message ?: e.javaClass.simpleName)
                }

                if (avail > 0) {
                    val n = try {
                        inputStream.read(buf, 0, minOf(avail, CHUNK))
                    } catch (e: IOException) {
                        throw ClassicBtException.ReadFailed(e.message ?: e.javaClass.simpleName)
                    }
                    if (n < 0) break  // EOF
                    out.write(buf, 0, n)
                    deadlineMs = System.currentTimeMillis() + INACTIVITY_TIMEOUT_MS
                    onProgress(out.size())
                    if (out.size() > MAX_BYTES) throw ClassicBtException.DumpTooLarge(out.size())
                } else {
                    if (System.currentTimeMillis() > deadlineMs) break  // 无数据超时 → 传输结束
                    Thread.sleep(POLL_MS)
                }
            }
        } finally {
            runCatching { socket.close() }
        }

        if (out.size() == 0) throw ClassicBtException.EmptyDump
        out.toByteArray()
    }

    // ── socket 打开三段式 fallback ────────────────────────────

    @SuppressLint("MissingPermission")
    private fun openSocket(device: android.bluetooth.BluetoothDevice): BluetoothSocket {
        return try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e1: IOException) {
            try {
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e2: IOException) {
                reflectiveSocket(device) ?: throw ClassicBtException.SocketOpenFailed(
                    "secure: ${e1.message}; insecure: ${e2.message}"
                )
            }
        }
    }

    @Suppress("DiscouragedPrivateApi")
    private fun reflectiveSocket(device: android.bluetooth.BluetoothDevice): BluetoothSocket? = try {
        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
        m.invoke(device, 1) as? BluetoothSocket
    } catch (_: Exception) { null }
}

// ── 数据类 ────────────────────────────────────────────────────

/** 已配对 + VendorDetector 识别的潜水电脑（用于 ScanScreen 的「已配对设备」分区） */
data class BondedDiveComputer(
    val address: String,
    val name: String?,
    val vendorMatch: VendorMatch.Hit,
)

// ── 错误类型 ──────────────────────────────────────────────────

sealed class ClassicBtException(message: String) : Exception(message) {
    object NoAdapter : ClassicBtException("设备不支持蓝牙")
    data class InvalidAddress(val address: String) : ClassicBtException("无效地址: $address")
    data class SocketOpenFailed(val detail: String) : ClassicBtException("无法创建 RFCOMM socket: $detail")
    data class ConnectFailed(val detail: String) : ClassicBtException("连接失败: $detail")
    data class ReadFailed(val detail: String) : ClassicBtException("读取失败: $detail")
    data class DumpTooLarge(val bytes: Int) : ClassicBtException("dump 超过上限 (${bytes / 1024} KB)")
    object EmptyDump : ClassicBtException("未收到任何数据；请在电脑表上操作「传输日志」后重试")
}
