package cn.diveplan.importer.ui.bind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.diveplan.importer.data.BindRepository
import cn.diveplan.importer.data.BindResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 绑定流程的 UI 状态机：
 *
 *   Idle ─(用户输入 6 位)──→ Submitting ─(成功)──→ Bound
 *      ↑                                ↓ (失败)
 *      └────────── ErrorVisible ←───────┘  (用户重新输入会回到 Idle)
 *
 * 既驱动手动输入 Tab，也驱动二维码扫描 Tab：
 *   - QR scanner 识别到形如 `diveplan://ble-probe/bind?code=123456` 或纯 6 位数字时
 *     直接调 [submitCode]，UI 切到 Submitting
 */
@HiltViewModel
class BindViewModel @Inject constructor(
    private val bindRepository: BindRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BindUiState())
    val uiState: StateFlow<BindUiState> = _uiState.asStateFlow()

    /** 用户在 OTP 输入框里改动一位时调用；自动归一化（去非数字、限 6 位）+ 清错 */
    fun onCodeChange(raw: String) {
        val normalized = raw.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(code = normalized, error = null) }
    }

    /**
     * 提交绑定码。
     *
     * - 长度不足 6 立刻拒绝，不打后端
     * - 同时 [Submitting] 中第二次调用被忽略
     * - 入口包括：UI「确认绑定」按钮、QR scanner 识别成功、外部 deep link
     */
    fun submitCode(codeOverride: String? = null) {
        val code = (codeOverride ?: _uiState.value.code).filter { it.isDigit() }.take(6)
        if (code.length != 6) {
            _uiState.update { it.copy(error = BindError.LengthMismatch) }
            return
        }
        if (_uiState.value.phase == BindPhase.Submitting) return

        _uiState.update { it.copy(code = code, phase = BindPhase.Submitting, error = null) }
        viewModelScope.launch {
            when (val r = bindRepository.consumeBindCode(code)) {
                is BindResult.Success -> {
                    _uiState.update {
                        it.copy(
                            phase = BindPhase.Bound,
                            successPrefix = r.prefix,
                            successExpiresAt = r.expiresAt,
                            error = null,
                        )
                    }
                }
                is BindResult.Failure -> {
                    val err = when (r) {
                        is BindResult.Failure.InvalidCode -> BindError.InvalidCode
                        is BindResult.Failure.Network     -> BindError.Network(r.detail)
                        is BindResult.Failure.Server      -> BindError.Server(r.status, r.detail)
                    }
                    _uiState.update { it.copy(phase = BindPhase.Idle, error = err) }
                }
            }
        }
    }

    /** 用户在 Bound 态点「完成」/「去扫描设备」时调；让 UI 离开 BindScreen */
    fun acknowledgeBound() {
        _uiState.update { it.copy(phase = BindPhase.Idle, code = "") }
    }
}

data class BindUiState(
    val code: String = "",
    val phase: BindPhase = BindPhase.Idle,
    val error: BindError? = null,
    /** Bound 态：ApiKey 前缀（让用户在小程序端识别"我刚绑的是这台手机"） */
    val successPrefix: String? = null,
    val successExpiresAt: String? = null,
)

enum class BindPhase { Idle, Submitting, Bound }

sealed interface BindError {
    object LengthMismatch : BindError
    object InvalidCode : BindError
    data class Network(val detail: String) : BindError
    data class Server(val status: Int, val detail: String) : BindError
}
