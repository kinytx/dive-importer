package cn.diveplan.importer.ui.garmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.diveplan.importer.ble.VendorMatch

/**
 * P5 Garmin BLE → sidecar → FIT 下载屏幕。
 *
 * 状态：
 *   ConnectingGatt  — 正在连 GATT
 *   OpeningSession  — 正在连 WSS sidecar
 *   Syncing         — 下载中（进度条 + FIT 计数）
 *   NoNetwork       — 无网络（Wi-Fi/移动数据）
 *   Done            — 完成，FIT 文件已排队上传
 *   Failed          — 失败（可重试）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarminConnectScreen(
    address: String,
    deviceName: String?,
    vendorMatch: VendorMatch,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarminConnectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(address) {
        viewModel.start(address, deviceName, vendorMatch)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName ?: "Garmin 设备") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val state = uiState) {
                GarminConnectUiState.Idle -> {}

                is GarminConnectUiState.ConnectingGatt,
                is GarminConnectUiState.OpeningSession -> {
                    val text = when (state) {
                        is GarminConnectUiState.ConnectingGatt -> state.statusText
                        is GarminConnectUiState.OpeningSession -> state.statusText
                        else -> ""
                    }
                    ConnectingContent(deviceName = deviceName, statusText = text)
                }

                is GarminConnectUiState.Syncing ->
                    SyncingContent(state)

                is GarminConnectUiState.NoNetwork ->
                    NoNetworkContent(
                        deviceName = state.deviceName,
                        onRetry = { viewModel.retry(state.address, state.deviceName, state.vendorMatch) },
                    )

                is GarminConnectUiState.Done ->
                    DoneContent(state = state, onBack = {
                        viewModel.reset()
                        onBack()
                    })

                is GarminConnectUiState.Failed ->
                    FailedContent(
                        state = state,
                        onRetry = { viewModel.retry(state.retryAddress, state.retryName, state.retryVendor) },
                    )
            }
        }
    }
}

// ── 子 Composable ──────────────────────────────────────────────────────────

@Composable
private fun ConnectingContent(deviceName: String?, statusText: String) {
    CircularProgressIndicator(modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(24.dp))
    Text(
        text = deviceName ?: "Garmin 设备",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SyncingContent(state: GarminConnectUiState.Syncing) {
    Text(
        text = state.deviceName ?: "Garmin 设备",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))

    if (state.maxProgress != null && state.maxProgress > 0) {
        LinearProgressIndicator(
            progress = { state.progress.toFloat() / state.maxProgress },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${state.progress} / ${state.maxProgress}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(16.dp))
    Text(
        text = state.statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (state.fitCount > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "已收到 ${state.fitCount} 个 FIT 文件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun NoNetworkContent(deviceName: String?, onRetry: () -> Unit) {
    Icon(
        Icons.Filled.SignalWifiOff,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "需要网络连接",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Garmin ${deviceName ?: "设备"} 日志下载需要通过服务器解析，请连接 Wi-Fi 或移动数据后重试。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRetry) { Text("重试") }
}

@Composable
private fun DoneContent(state: GarminConnectUiState.Done, onBack: () -> Unit) {
    Icon(
        Icons.Filled.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(56.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "下载完成",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "已收到 ${state.files.size} 个 FIT 文件，已排入后台上传队列。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (state.files.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        val totalBytes = state.files.sumOf { it.length() }
        Text(
            text = "总大小 ${formatBytes(totalBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onBack) { Text("返回设备列表") }
}

@Composable
private fun FailedContent(
    state: GarminConnectUiState.Failed,
    onRetry: () -> Unit,
) {
    Icon(
        Icons.Filled.Warning,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "连接失败",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = state.errorMessage,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRetry) { Text("重试") }
    }
}

// ── 工具 ─────────────────────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024       -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else               -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
