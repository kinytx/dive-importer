package cn.diveplan.importer.ui.scan

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.diveplan.importer.ble.BlePermissions
import cn.diveplan.importer.ble.BleScanError
import cn.diveplan.importer.ble.BondedDiveComputer
import cn.diveplan.importer.ble.DiscoveredBleDevice
import cn.diveplan.importer.ble.VendorMatch

/**
 * 扫描设备列表屏（P3 更新版）。
 *
 * 两个分区：
 *   ① 已配对经典蓝牙潜水电脑（从系统 bondedDevices 筛选）
 *      —— Shearwater Petrel 等 GATT 为空的设备走这里；直接连 RFCOMM 抓 dump
 *   ② BLE 广播发现的设备（BleScanner 实时更新）
 *      —— 也可以点击，会到 ConnectScreen 引导走对应路径
 *
 * 点击任意设备 → [onConnectDevice](address, name, vendorMatch) → 父层跳 ConnectScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onConnectDevice: (address: String, name: String?, vendorMatch: VendorMatch) -> Unit,
    onNavigateHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermissions by remember { mutableStateOf(BlePermissions.hasAllPermissions(context)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result.values.all { it }
        if (hasPermissions) {
            viewModel.startScan()
            viewModel.refreshBonded()
        }
    }
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasPermissions) viewModel.startScan()
    }

    LaunchedEffect(Unit) {
        if (hasPermissions) {
            viewModel.startScan()
            viewModel.refreshBonded()
        } else {
            permLauncher.launch(BlePermissions.requiredPermissions())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描潜水电脑") },
                actions = {
                    IconButton(onClick = onNavigateHistory) {
                        Icon(Icons.Filled.History, contentDescription = "上传历史")
                    }
                },
            )
        },
        modifier = modifier,
    ) { scaffoldPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
        ScanStatusBar(
            scanning = state.scanning,
            bleCount = state.bleDevices.size,
            onRescan = {
                if (!hasPermissions) {
                    permLauncher.launch(BlePermissions.requiredPermissions())
                    return@ScanStatusBar
                }
                if (state.scanning) viewModel.stopScan() else viewModel.startScan()
            },
            onRefreshBonded = { viewModel.refreshBonded() },
        )

        state.error?.let { err ->
            ErrorCard(
                err = err,
                onEnableBluetooth = {
                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                },
                onRequestPermission = { permLauncher.launch(BlePermissions.requiredPermissions()) },
            )
        }

        val hasBonded = state.bondedDevices.isNotEmpty()
        val hasBle    = state.bleDevices.isNotEmpty()

        if (!hasBonded && !hasBle) {
            EmptyState(scanning = state.scanning)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ── 分区 1：已配对经典蓝牙 ───────────────────────────
                if (hasBonded) {
                    item {
                        SectionHeader(
                            text   = "已配对经典蓝牙设备",
                            detail = "点击连接并抓取日志（Shearwater Petrel 系列）",
                        )
                    }
                    items(state.bondedDevices, key = { "bonded_${it.address}" }) { dev ->
                        BondedDeviceRow(dev = dev, onClick = {
                            onConnectDevice(dev.address, dev.name, dev.vendorMatch)
                        })
                    }
                }

                // ── 分区 2：BLE 广播发现 ─────────────────────────────
                if (hasBonded && hasBle) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                if (hasBle) {
                    item {
                        SectionHeader(
                            text   = "BLE 广播发现",
                            detail = "Shearwater 表广播后引导走经典蓝牙；Garmin 走 Sidecar（即将支持）",
                        )
                    }
                    items(state.bleDevices, key = { it.address }) { device ->
                        BleDeviceRow(device = device, onClick = {
                            onConnectDevice(device.address, device.name, device.vendorMatch)
                        })
                    }
                }
            }
        }
    }
    } // end Scaffold
}

// ── 顶部状态条 ───────────────────────────────────────────────

@Composable
private fun ScanStatusBar(
    scanning: Boolean,
    bleCount: Int,
    onRescan: () -> Unit,
    onRefreshBonded: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (scanning) "🔍 BLE 扫描中…" else "BLE 扫描已停止",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "已发现 $bleCount 台 BLE 设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onRefreshBonded, modifier = Modifier.padding(end = 8.dp)) {
            Text("刷新配对")
        }
        OutlinedButton(onClick = onRescan) {
            Text(if (scanning) "停止" else "↻ 扫描")
        }
    }
}

// ── 分区标题 ─────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String, detail: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── 已配对经典蓝牙设备行 ──────────────────────────────────────

@Composable
private fun BondedDeviceRow(dev: BondedDiveComputer, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dev.vendorMatch.vendor.first().uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dev.name ?: dev.address,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "${dev.vendorMatch.vendor} ${dev.vendorMatch.product}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = dev.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.BluetoothConnected,
                contentDescription = "经典蓝牙",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── BLE 广播设备行 ────────────────────────────────────────────

@Composable
private fun BleDeviceRow(device: DiscoveredBleDevice, onClick: () -> Unit) {
    val hit = device.vendorMatch as? VendorMatch.Hit
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VendorBadge(hit = hit)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "(无广播名)",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (hit != null) {
                    Text(
                        text = "${hit.vendor} ${hit.product}".trim() +
                            if (hit.ambiguous) "（请确认型号）" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (hit.weak) {
                        Text(
                            text = hit.hint ?: "需要走特殊通道",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                } else {
                    Text(
                        text = "未识别（点击手动选）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RssiBadge(rssi = device.rssi)
        }
    }
}

// ── 共用组件 ──────────────────────────────────────────────────

@Composable
private fun VendorBadge(hit: VendorMatch.Hit?) {
    val (label, bg) = when {
        hit == null -> "?" to MaterialTheme.colorScheme.surfaceVariant
        hit.weak    -> hit.vendor.first().uppercase() to MaterialTheme.colorScheme.tertiary
        else        -> hit.vendor.first().uppercase() to MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun RssiBadge(rssi: Int) {
    val strength = when {
        rssi > -55 -> "●●●●"
        rssi > -70 -> "●●●○"
        rssi > -85 -> "●●○○"
        else       -> "●○○○"
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(strength, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text("$rssi dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(scanning: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (scanning) "等待发现潜水电脑…\n请确认电脑表已开机并进入配对/传输模式"
                   else          "未发现设备\n点「↻ 扫描」开始 BLE 扫描\n已在系统蓝牙配对的设备点「刷新配对」",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorCard(
    err: BleScanError,
    onEnableBluetooth: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (err) {
                    BleScanError.NoAdapter            -> "此设备不支持低功耗蓝牙"
                    BleScanError.BluetoothOff         -> "蓝牙未开启"
                    is BleScanError.MissingPermission -> "缺少蓝牙权限"
                    is BleScanError.Failed            -> "BLE 扫描失败（错误码 ${err.code}）"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            when (err) {
                BleScanError.BluetoothOff ->
                    Button(onClick = onEnableBluetooth) { Text("打开蓝牙") }
                is BleScanError.MissingPermission ->
                    Button(onClick = onRequestPermission) { Text("授权蓝牙权限") }
                else -> {}
            }
        }
    }
}
