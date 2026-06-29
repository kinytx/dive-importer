package cn.diveplan.importer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.diveplan.importer.ble.VendorMatch
import cn.diveplan.importer.data.ApiKeyStore
import cn.diveplan.importer.ui.bind.BindScreen
import cn.diveplan.importer.ui.bind.BindViewModel
import cn.diveplan.importer.ui.bind.extractBindCode
import cn.diveplan.importer.ui.connect.ConnectScreen
import cn.diveplan.importer.ui.garmin.GarminConnectScreen
import cn.diveplan.importer.ui.history.HistoryScreen
import cn.diveplan.importer.ui.scan.ScanScreen
import cn.diveplan.importer.ui.settings.SettingsScreen
import cn.diveplan.importer.ui.theme.DivePlanImporterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 主 Activity -- 单 Activity Compose 架构。
 *
 * 路由策略（P3 版本）：
 *   - 未绑定（apiKey == null）-> [BindScreen]
 *   - 已绑定 + 无选中设备    -> [ScanScreen]（BLE + 已配对经典蓝牙两分区）
 *   - 已绑定 + 选中设备      -> [ConnectScreen]（经典蓝牙 RFCOMM dump 抓取）
 *
 * 导航状态用 [rememberSaveable] 存简单字段（旋转后不丢失）。
 * VendorMatch 是 sealed interface，用管道分隔字符串序列化存 saveable：
 *   "Hit|vendor|product|weak|ambiguous|hint"  or "Unknown"
 *
 * Deep link `diveplan://ble-probe/bind?code=xxxxxx` 由 [handleIncomingIntent] 处理。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var apiKeyStore: ApiKeyStore

    private val bindViewModel: BindViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            DivePlanImporterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val code = intent?.data?.toString()?.let(::extractBindCode) ?: return
        bindViewModel.submitCode(code)
    }

    @Composable
    private fun RootScreen() {
        val apiKey by apiKeyStore.apiKey.collectAsStateWithLifecycle()

        if (apiKey.isNullOrBlank()) {
            // 未绑定 -> 绑定屏
            BindScreen(
                viewModel = bindViewModel,
                onBound = { /* apiKey 变化触发 recompose，自动切走 */ },
            )
            return
        }

        // 已绑定 -> 扫描 / 连接 / 历史 / 设置屏
        var selectedAddress   by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedName      by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedVendorRaw by rememberSaveable { mutableStateOf<String?>(null) }
        var currentScreen     by rememberSaveable { mutableStateOf("scan") }  // "scan" | "history" | "settings"

        val vendorMatch: VendorMatch? = selectedVendorRaw?.let(::decodeVendorMatch)

        val onBack: () -> Unit = {
            selectedAddress   = null
            selectedName      = null
            selectedVendorRaw = null
        }

        when {
            // 优先：已选中设备 → ConnectScreen 或 GarminConnectScreen
            selectedAddress != null && vendorMatch != null -> {
                if (vendorMatch is VendorMatch.Hit && vendorMatch.weak) {
                    GarminConnectScreen(
                        address     = selectedAddress!!,
                        deviceName  = selectedName,
                        vendorMatch = vendorMatch,
                        onBack      = onBack,
                    )
                } else {
                    ConnectScreen(
                        address     = selectedAddress!!,
                        deviceName  = selectedName,
                        vendorMatch = vendorMatch,
                        onBack      = onBack,
                    )
                }
            }
            currentScreen == "settings" -> SettingsScreen(
                onBack = { currentScreen = "history" },
            )
            currentScreen == "history" -> HistoryScreen(
                onBack             = { currentScreen = "scan" },
                onNavigateSettings = { currentScreen = "settings" },
            )
            else -> ScanScreen(
                onConnectDevice = { address, name, match ->
                    selectedAddress   = address
                    selectedName      = name
                    selectedVendorRaw = encodeVendorMatch(match)
                },
                onNavigateHistory = { currentScreen = "history" },
            )
        }
    }

    // ---- VendorMatch 序列化（仅用于 rememberSaveable） ----------
    // 格式："Hit|vendor|product|weak|ambiguous|hint"  or "Unknown"

    private fun encodeVendorMatch(m: VendorMatch): String = when (m) {
        VendorMatch.Unknown -> "Unknown"
        is VendorMatch.Hit  -> listOf(
            "Hit",
            m.vendor,
            m.product,
            m.weak.toString(),
            m.ambiguous.toString(),
            m.hint.orEmpty(),
        ).joinToString("|")
    }

    private fun decodeVendorMatch(s: String): VendorMatch {
        if (s == "Unknown") return VendorMatch.Unknown
        val p = s.split("|")
        if (p.firstOrNull() != "Hit" || p.size < 5) return VendorMatch.Unknown
        return VendorMatch.Hit(
            vendor    = p[1],
            product   = p[2],
            weak      = p[3].toBoolean(),
            ambiguous = p[4].toBoolean(),
            hint      = p.getOrElse(5) { "" }.ifBlank { null },
            source    = "restored",
        )
    }
}
