package cn.diveplan.importer.ui.bind

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 账号绑定主屏（首次启动 / 用户主动解绑后落到这）。
 *
 * 支持三种绑定入口：
 *   1. 手动输入 6 位码（Tab 0）
 *   2. 扫二维码（Tab 1）
 *   3. 剪贴板自动检测（Android 前台可主动读；12+ 系统自动显示 "App pasted from clipboard" toast）
 *
 * 平台差异说明：
 *   - Android：[LaunchedEffect] 在屏幕出现时主动读剪贴板，显示 banner 让用户确认
 *   - iOS（未来）：iOS 16+ 限制后台读取，应改为用户点击「粘贴」按钮触发（UIPasteControl）
 *   - 小程序：wx.getClipboardData 需用户授权，也应改为主动触发
 *
 * @param onBound 绑定成功后 ViewModel 进 Bound 态，用户点「开始扫描设备」时调
 */
@Composable
fun BindScreen(
    onBound: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BindViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tabIndex by remember { mutableIntStateOf(0) }

    // ── 剪贴板自动检测（Android 专属路径）────────────────────────
    // 前台读取是合规的；Android 12+ 系统 toast 由系统负责，无需额外提示。
    // iOS 移植时：删除此 LaunchedEffect，改为 PasteButton 触发。
    val clipboardManager = LocalClipboardManager.current
    var clipboardCode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val extracted = extractBindCode(clipboardManager.getText()?.text)
        if (extracted != null) clipboardCode = extracted
    }

    // 如果父层已经把 deepLinkCode 推给 ViewModel 并进 Submitting/Bound 状态，UI 自动反映
    LaunchedEffect(state.phase) {
        if (state.phase == BindPhase.Bound) {
            // 让父层有机会埋点 / 跳转；这里不主动跳，等用户点按钮
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "绑定账号",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "在小程序「我的 → 潜水电脑」生成绑定码后，扫描或输入",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            if (state.phase == BindPhase.Bound) {
                BoundSuccessCard(
                    prefix = state.successPrefix.orEmpty(),
                    onContinue = {
                        viewModel.acknowledgeBound()
                        onBound()
                    },
                )
                return@Column
            }

            // ── 剪贴板检测 banner ──────────────────────────────────
            clipboardCode?.let { code ->
                ClipboardBanner(
                    code = code,
                    onUse = {
                        viewModel.onCodeChange(code)
                        tabIndex = 0
                        clipboardCode = null
                        // 直接提交，体验更顺滑；如需用户二次确认可改为 onCodeChange only
                        viewModel.submitCode(code)
                    },
                    onDismiss = { clipboardCode = null },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("输入码") },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("扫码") },
                )
            }
            Spacer(Modifier.height(24.dp))

            when (tabIndex) {
                0 -> InputCodePane(
                    state = state,
                    onCodeChange = viewModel::onCodeChange,
                    onSubmit = { viewModel.submitCode() },
                    // 手动粘贴按钮：兜底 Android 12+ 用户手动触发（系统 toast 后已知有内容）
                    onPasteFromClipboard = {
                        val extracted = extractBindCode(clipboardManager.getText()?.text)
                        if (extracted != null) {
                            viewModel.onCodeChange(extracted)
                            viewModel.submitCode(extracted)
                        }
                    },
                )
                1 -> QrScanScreen(
                    onCodeDetected = { code -> viewModel.submitCode(code) },
                    onCancel = { tabIndex = 0 },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun InputCodePane(
    state: BindUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPasteFromClipboard: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CodeInputField(
            value = state.code,
            onValueChange = onCodeChange,
            onSubmit = onSubmit,
            enabled = state.phase != BindPhase.Submitting,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "6 位数字码 · 有效期 10 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            // 手动粘贴按钮：适用于用户已知剪贴板里有码，但 banner 被关掉的场景
            // Android 12+ 点击后系统会再次显示 "pasted from clipboard" toast，属正常行为
            IconButton(onClick = onPasteFromClipboard) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "从剪贴板粘贴绑定码",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = friendlyError(err),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = state.code.length == 6 && state.phase != BindPhase.Submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (state.phase) {
                    BindPhase.Submitting -> "绑定中…"
                    else                  -> "确认绑定"
                }
            )
        }
    }
}

/**
 * 剪贴板内容检测 banner。
 *
 * 仅在 Android 端主动弹出（LaunchedEffect 读取剪贴板）。
 * iOS 移植时不使用此 composable，改为 PasteButton (UIPasteControl)。
 */
@Composable
private fun ClipboardBanner(
    code: String,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = 8.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "检测到剪贴板绑定码",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = code,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            OutlinedButton(
                onClick = onUse,
                modifier = Modifier.padding(end = 4.dp),
            ) { Text("使用") }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "忽略",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun BoundSuccessCard(prefix: String, onContinue: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "✅ 已绑定",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            if (prefix.isNotEmpty()) {
                Text(
                    text = "凭证前缀: $prefix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("开始扫描潜水电脑 →")
            }
            TextButton(onClick = onContinue) {
                Text("稍后再说")
            }
        }
    }
}

private fun friendlyError(err: BindError): String = when (err) {
    BindError.LengthMismatch -> "请输入完整的 6 位数字码"
    BindError.InvalidCode     -> "绑定码无效或已过期，请在小程序重新生成"
    is BindError.Network      -> "网络异常，请检查连接后重试（${err.detail}）"
    is BindError.Server       -> "服务器异常（${err.status}），请稍后重试"
}
