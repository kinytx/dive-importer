package cn.diveplan.importer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.diveplan.importer.data.ApiKeyStore
import cn.diveplan.importer.data.DumpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val dumpRepository: DumpRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val usage  = dumpRepository.totalDiskUsage()
            val prefix = apiKeyStore.prefix.value
            _uiState.value = SettingsUiState(
                diskUsageBytes  = usage,
                apiKeyPrefix    = prefix,
                showUnbindDialog = false,
            )
        }
    }

    fun showUnbindConfirm() {
        _uiState.value = _uiState.value.copy(showUnbindDialog = true)
    }

    fun dismissUnbindConfirm() {
        _uiState.value = _uiState.value.copy(showUnbindDialog = false)
    }

    /** 解绑：清除 ApiKey，MainActivity 观察到 apiKey == null 后自动跳回 BindScreen。 */
    fun unbind() {
        apiKeyStore.clear()
    }
}

data class SettingsUiState(
    val diskUsageBytes: Long  = 0,
    val apiKeyPrefix: String? = null,
    val showUnbindDialog: Boolean = false,
)
