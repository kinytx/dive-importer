package cn.diveplan.importer.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.diveplan.importer.data.DumpEntry
import cn.diveplan.importer.data.DumpRepository
import cn.diveplan.importer.data.network.DiveImportJobApi
import cn.diveplan.importer.data.network.DiveImportJobSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * P6 历史页 ViewModel。
 *
 * 展示：
 *  - 本地文件（.bin RFCOMM dump + .fit Garmin FIT）及其上传状态
 *  - 服务端 dive-import-jobs（解析状态 + 计数）
 *
 * 有 pending/running job 时每 5 秒自动轮询一次，其余情况只在 [refresh] 时更新。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dumpRepository: DumpRepository,
    private val jobApi: DiveImportJobApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
        startPolling()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val bins  = dumpRepository.allDumps()
            val fits  = dumpRepository.allFits()
            val jobs  = jobApi.listJobs()
            val usage = dumpRepository.totalDiskUsage()
            _uiState.value = HistoryUiState(
                loading      = false,
                localFiles   = (bins + fits).sortedByDescending { it.modifiedAt },
                serverJobs   = jobs,
                diskUsageBytes = usage,
            )
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                val hasActive = _uiState.value.serverJobs.any {
                    it.status == "pending" || it.status == "running"
                }
                if (hasActive) refresh()
            }
        }
    }
}

data class HistoryUiState(
    val loading: Boolean = true,
    val localFiles: List<DumpEntry> = emptyList(),
    val serverJobs: List<DiveImportJobSummary> = emptyList(),
    val diskUsageBytes: Long = 0,
)
