package cn.diveplan.importer.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.diveplan.importer.ble.VendorMatch

/**
 * P3 经典蓝牙连接 + dump 抓取屏。
 *
 * 状态机对应 UI：
 *   Idle / Connecting → 转圈 + 提示「请确认设备已配对」
 *   Capturing         → 线性进度 + 已接收字节数 + 提示「请在表上操作传输」
 *   Done              → ✓ 大图标 + 已接收字节 + 「返回扫描」
 *   Failed            → ✗ 图标 + 错误文本 + 排查步骤 + 「重试」「返回」
 *
 * @param address      目标设备 MAC
 * @param deviceName   设备广播名（可能为 null）
 * @param vendorMatch  Vendor 识别结果（重试时透传回 VM）
 * @param onBack       返回扫描列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    address: String,
    deviceName: String?,
    vendorMatch: VendorMatch,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 进入屏幕自动开始（LaunchedEffect key=address，切换设备会重新触发）
    LaunchedEffect(address) {
        viewModel.startCapture(address, deviceName, vendorMatch)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName ?: address, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.reset(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (val s = state) {
                    is ConnectUiState.Idle,
                    is ConnectUiState.Connecting -> ConnectingContent(
                        label = (s as? ConnectUiState.Connecting)?.deviceName ?: deviceName ?: address
                    )

                    is ConnectUiState.Capturing -> CapturingContent(s.bytesReceived)

                    is ConnectUiState.Done -> DoneContent(
                        totalBytes = s.totalBytes,
                        onBack = { viewModel.reset(); onBack() },
                    )

                    is ConnectUiState.Failed -> FailedContent(
                        message = s.errorMessage,
                        onRetry = { viewModel.retry(s.retryAddress, s.retryName, s.retryVendor) },
                        onBack  = { viewModel.reset(); onBack() },
                    )
                }
            }
        }
    }
}

// ── 各状态子 Composable ──────────────────────────────────────

@Composable
private fun ConnectingContent(label: String) {
    CircularProgressIndicator(modifier = Modifier.size(56.dp))
    Text(
        text = "正在连接 $label…",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    HintCard(
        "准备事项：\n" +
        "• 确认设备已在 Android「系统蓝牙」中完成配对（设置 → 已连接的设备）\n" +
        "• 确认电脑表已开机且蓝牙未关闭\n" +
        "• 与设备保持 2 米内"
    )
}

@Composable
private fun CapturingContent(bytesReceived: Int) {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text(
        text = "正在接收数据 · ${formatBytes(bytesReceived)}",
        style = MaterialTheme.typography.titleMedium,
    )
    HintCard(
        "• 请勿关闭蓝牙或移动设备\n" +
        "• 如果数据未自动开始传输，请在电脑表上找「蓝牙 → 传输日志」并操作\n" +
        "• 传输完成后 App 会自动结束"
    )
}

@Composable
private fun DoneContent(totalBytes: Int, onBack: () -> Unit) {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(72.dp),
    )
    Text(
        text = "导入完成",
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = "已接收 ${formatBytes(totalBytes)}，数据已保存到本地。\n联网时将自动上传到 DivePlan。",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("返回扫描列表")
    }
}

@Composable
private fun FailedContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Icon(
        imageVector = Icons.Default.ErrorOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(64.dp),
    )
    Text(
        text = "连接失败",
        style = MaterialTheme.typography.headlineSmall,
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    HintCard(
        "排查步骤：\n" +
        "① 在手机「系统蓝牙设置」中确认设备已配对（不是本应用扫描，是系统设置）\n" +
        "② 确认电脑表已开机、蓝牙未禁用\n" +
        "③ 距离保持 2 米以内\n" +
        "④ 若多次失败：在表上操作「传输日志」之后点「重试」"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("返回") }
        Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("重试") }
    }
}

@Composable
private fun HintCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f,
        )
    }
}

// ── 工具 ─────────────────────────────────────────────────────

private fun formatBytes(bytes: Int): String = when {
    bytes < 1_024             -> "$bytes B"
    bytes < 1_024 * 1_024     -> "${"%.1f".format(bytes / 1_024.0)} KB"
    else                      -> "${"%.2f".format(bytes / (1_024.0 * 1_024.0))} MB"
}
