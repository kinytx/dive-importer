package cn.diveplan.importer.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * P6 设置屏幕。
 *
 * 包含：
 *  - 本地存储用量
 *  - 当前绑定账号（ApiKey prefix）
 *  - 解绑按钮（二次确认）→ 清空 ApiKey → 自动跳回绑定页
 *  - 应用版本
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (state.showUnbindDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUnbindConfirm() },
            title   = { Text("确认解绑？") },
            text    = { Text("解绑后本 App 将停止与 DivePlan 账号同步。本地已保存的 dump/FIT 文件不会删除，但无法继续上传，直到重新绑定。") },
            confirmButton = {
                Button(
                    onClick = { viewModel.unbind() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    )
                ) { Text("确认解绑") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUnbindConfirm() }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(horizontal = 20.dp),
        ) {
            // ── 存储 ─────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            SettingsSectionTitle("存储")
            SettingsRow(label = "本地占用", value = formatBytes(state.diskUsageBytes))
            HorizontalDivider()

            // ── 账号 ─────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            SettingsSectionTitle("账号")
            SettingsRow(
                label = "绑定 ApiKey",
                value = state.apiKeyPrefix?.let { "$it…" } ?: "（未知）"
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.showUnbindConfirm() },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("解绑账号") }
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            // ── 关于 ─────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            SettingsSectionTitle("关于")
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrDefault("--")
            SettingsRow(label = "版本", value = version ?: "--")
            SettingsRow(label = "上传端点", value = "api.diveplan.cn")
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0          -> "0 B"
    bytes < 1024        -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else                -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
