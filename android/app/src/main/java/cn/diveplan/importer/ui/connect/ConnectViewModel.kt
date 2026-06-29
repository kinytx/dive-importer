package cn.diveplan.importer.ui.connect

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.diveplan.importer.ble.ClassicBtConnector
import cn.diveplan.importer.ble.ClassicBtException
import cn.diveplan.importer.ble.VendorMatch
import cn.diveplan.importer.data.DumpRepository
import cn.diveplan.importer.data.DumpUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * P3 连接 + dump 抓取 ViewModel。
 *
 * 状态机：
 *   Idle ──startCapture──▶ Connecting ──socket.connect()──▶ Capturing ──EOF/timeout──▶ Done
 *                │                          │                    │
 *                └──────────────────────────┴────────────────────┴──exception──▶ Failed
 *
 * - [startCapture] 幂等：若已有 job 在跑则忽略
 * - [retry] 取消当前 job，重置状态，重新开始
 * - [reset] 取消 job 并退回 Idle（返回扫描列表时调）
 */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connector: ClassicBtConnector,
    private val dumpRepository: DumpRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConnectUiState>(ConnectUiState.Idle)
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    private var captureJob: Job? = null

    fun startCapture(address: String, deviceName: String?, vendorMatch: VendorMatch) {
        if (captureJob?.isActive == true) return
        captureJob = viewModelScope.launch {
            _uiState.value = ConnectUiState.Connecting(deviceName, address)
            runCatching {
                _uiState.value = ConnectUiState.Capturing(deviceName, 0)
                connector.captureRawDump(address) { received ->
                    _uiState.value = ConnectUiState.Capturing(deviceName, received)
                }
            }.onSuccess { bytes ->
                val file = dumpRepository.saveDump(address, deviceName, bytes)
                // P4：dump 落盘后立刻触发后台上传；有网直接传，无网等联网时 WorkManager 重试
                DumpUploadWorker.enqueue(context)
                _uiState.value = ConnectUiState.Done(deviceName, file, bytes.size)
            }.onFailure { e ->
                _uiState.value = ConnectUiState.Failed(
                    deviceName    = deviceName,
                    errorMessage  = when (e) {
                        is ClassicBtException -> e.message ?: e.javaClass.simpleName
                        else                  -> "未知错误: ${e.message}"
                    },
                    retryAddress  = address,
                    retryName     = deviceName,
                    retryVendor   = vendorMatch,
                )
            }
        }
    }

    fun retry(address: String, deviceName: String?, vendorMatch: VendorMatch) {
        captureJob?.cancel()
        _uiState.value = ConnectUiState.Idle
        startCapture(address, deviceName, vendorMatch)
    }

    /** 退出屏幕前调；取消进行中的连接并重置状态。 */
    fun reset() {
        captureJob?.cancel()
        _uiState.value = ConnectUiState.Idle
    }
}

sealed interface ConnectUiState {
    object Idle : ConnectUiState

    data class Connecting(
        val deviceName: String?,
        val address: String,
    ) : ConnectUiState

    data class Capturing(
        val deviceName: String?,
        val bytesReceived: Int,
    ) : ConnectUiState

    data class Done(
        val deviceName: String?,
        val file: File,
        val totalBytes: Int,
    ) : ConnectUiState

    data class Failed(
        val deviceName: String?,
        val errorMessage: String,
        val retryAddress: String,
        val retryName: String?,
        val retryVendor: VendorMatch,
    ) : ConnectUiState
}
