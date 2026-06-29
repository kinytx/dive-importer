package cn.diveplan.importer.ui.garmin

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.diveplan.importer.ble.VendorMatch
import cn.diveplan.importer.data.DumpUploadWorker
import cn.diveplan.importer.data.GarminBridgeEvent
import cn.diveplan.importer.data.GarminWssBridgeSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * P5 Garmin BLE → WSS sidecar → FIT 下载 ViewModel。
 *
 * 状态机：
 *
 *   Idle ──start()──▶ CheckingNetwork ──有网──▶ ConnectingGatt ──GATT 就绪──▶
 *     OpeningSession ──sidecar 握手──▶ Syncing(progress) ──done──▶ Done(files)
 *
 *   任意步骤 ──错误──▶ Failed
 *   无网     ──▶ NoNetwork
 *
 * - [start] 幂等：已有正在进行的会话时忽略
 * - [retry]：取消当前会话，重新开始
 * - [reset]：取消并回 Idle（导航返回时调）
 */
@HiltViewModel
class GarminConnectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: GarminWssBridgeSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GarminConnectUiState>(GarminConnectUiState.Idle)
    val uiState: StateFlow<GarminConnectUiState> = _uiState.asStateFlow()

    fun start(address: String, deviceName: String?, vendorMatch: VendorMatch) {
        if (_uiState.value.isActive) return

        val product = (vendorMatch as? VendorMatch.Hit)?.product ?: "Descent"

        // 检查网络
        if (!hasNetwork()) {
            _uiState.value = GarminConnectUiState.NoNetwork(address, deviceName, vendorMatch)
            return
        }

        _uiState.value = GarminConnectUiState.ConnectingGatt(deviceName, address)

        // 收集桥接事件
        viewModelScope.launch {
            bridge.events.collect { event ->
                when (event) {
                    is GarminBridgeEvent.Status   -> updateStatus(event.message)
                    is GarminBridgeEvent.Progress -> {
                        val cur = _uiState.value
                        val fitCount = (cur as? GarminConnectUiState.Syncing)?.fitCount ?: 0
                        _uiState.value = GarminConnectUiState.Syncing(
                            deviceName   = deviceName,
                            statusText   = "下载中…",
                            progress     = event.current,
                            maxProgress  = event.maximum,
                            fitCount     = fitCount,
                        )
                    }
                    is GarminBridgeEvent.FitReceived -> {
                        val cur = _uiState.value
                        val progress = (cur as? GarminConnectUiState.Syncing)?.progress ?: 0
                        val max = (cur as? GarminConnectUiState.Syncing)?.maxProgress
                        _uiState.value = GarminConnectUiState.Syncing(
                            deviceName  = deviceName,
                            statusText  = "已收到 ${event.totalSoFar} 个 FIT 文件",
                            progress    = progress,
                            maxProgress = max,
                            fitCount    = event.totalSoFar,
                        )
                    }
                    is GarminBridgeEvent.Done  -> {
                        // 触发上传
                        DumpUploadWorker.enqueue(context)
                        _uiState.value = GarminConnectUiState.Done(deviceName, event.files)
                    }
                    is GarminBridgeEvent.Error -> {
                        _uiState.value = GarminConnectUiState.Failed(
                            deviceName   = deviceName,
                            errorMessage = event.message,
                            retryAddress = address,
                            retryName    = deviceName,
                            retryVendor  = vendorMatch,
                        )
                    }
                }
            }
        }

        // 启动桥接（内部会发 Status 事件更新 UI）
        bridge.start(address, deviceName, product)
    }

    fun retry(address: String, deviceName: String?, vendorMatch: VendorMatch) {
        bridge.cancel()
        _uiState.value = GarminConnectUiState.Idle
        start(address, deviceName, vendorMatch)
    }

    fun reset() {
        bridge.cancel()
        _uiState.value = GarminConnectUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        bridge.cancel()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun updateStatus(msg: String) {
        val cur = _uiState.value
        _uiState.value = when (cur) {
            is GarminConnectUiState.ConnectingGatt -> cur.copy(statusText = msg)
            is GarminConnectUiState.OpeningSession -> cur.copy(statusText = msg)
            is GarminConnectUiState.Syncing        -> cur.copy(statusText = msg)
            else -> GarminConnectUiState.OpeningSession(
                deviceName = (cur as? GarminConnectUiState.ConnectingGatt)?.deviceName,
                address    = (cur as? GarminConnectUiState.ConnectingGatt)?.address ?: "",
                statusText = msg,
            )
        }
    }

    private fun hasNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** UI 状态 */
sealed interface GarminConnectUiState {
    /** 是否有正在进行的会话（用于幂等保护） */
    val isActive: Boolean get() = false

    object Idle : GarminConnectUiState

    data class ConnectingGatt(
        val deviceName: String?,
        val address: String,
        val statusText: String = "连接 Garmin GATT 服务...",
    ) : GarminConnectUiState { override val isActive = true }

    data class OpeningSession(
        val deviceName: String?,
        val address: String,
        val statusText: String = "连接 sidecar...",
    ) : GarminConnectUiState { override val isActive = true }

    data class Syncing(
        val deviceName: String?,
        val statusText: String,
        val progress: Int,
        val maxProgress: Int?,
        val fitCount: Int,
    ) : GarminConnectUiState { override val isActive = true }

    data class NoNetwork(
        val address: String,
        val deviceName: String?,
        val vendorMatch: VendorMatch,
    ) : GarminConnectUiState

    data class Done(
        val deviceName: String?,
        val files: List<File>,
    ) : GarminConnectUiState

    data class Failed(
        val deviceName: String?,
        val errorMessage: String,
        val retryAddress: String,
        val retryName: String?,
        val retryVendor: VendorMatch,
    ) : GarminConnectUiState
}
