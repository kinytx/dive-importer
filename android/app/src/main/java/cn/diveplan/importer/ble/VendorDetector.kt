package cn.diveplan.importer.ble

import java.util.UUID

/**
 * 潜水电脑表 vendor / product 识别。
 *
 * **数据来源 / 单一真相**：
 *   gas-dive-plan/pages/dc-import/dc-import.ts > VENDOR_BLE_SERVICE_HINTS + DEVICE_NAME_HINTS
 *   小程序里那份是 SOT，本文件 hardcode 同一份。**任何 vendor 数据变更需要同步三处**：
 *     1. plan 仓 dc-import.ts
 *     2. android-dive-importer (本文件)
 *     3. apple-dive-importer  Sources/.../BLE/VendorDetector.swift
 *
 * 设计：
 *  - service UUID 优先（强信号；广播里就能拿到不需要连 GATT）
 *  - 设备名 regex fallback（按特异度从高到低 —— 「Petrel 3」必须排在「Petrel」前）
 *  - 不匹配时返回 [VendorMatch.Unknown]，UI 显示「未识别 · 点击手动选」
 */

/** 服务 UUID hint：vendor → 它的潜水电脑表广播的 service UUID */
private val SERVICE_UUID_HINTS: Map<String, List<UUID>> = mapOf(
    "Shearwater" to listOf(UUID.fromString("FE25C237-0ECE-443C-B0AA-E02033E7029D")),
    "Garmin"     to listOf(UUID.fromString("6A4E2800-667B-11E3-949A-0800200C9A66")),
)

/** 反向索引：service UUID → vendor */
private val SERVICE_TO_VENDOR: Map<UUID, String> by lazy {
    SERVICE_UUID_HINTS.flatMap { (vendor, ids) -> ids.map { it to vendor } }.toMap()
}

/** 单条设备名 → vendor + product 映射。order matters：更具体的 pattern 排前 */
private data class DeviceNamePattern(
    val regex: Regex,
    val vendor: String,
    val product: String,
    val ambiguous: Boolean = false,
    /** weak = 只识别到品牌/系列，不能直接走读取协议（如 Garmin Descent 需进 Garmin sidecar） */
    val weak: Boolean = false,
    val hint: String? = null,
)

private val DEVICE_NAME_PATTERNS: List<DeviceNamePattern> = listOf(
    // ── Garmin Descent（weak：走 Garmin sidecar 通道）──
    DeviceNamePattern(Regex("^(?:Garmin\\s*)?Descent\\s*Mk\\d+(?:i)?\\s*(?:\\d+mm)?\\b", RegexOption.IGNORE_CASE),
        "Garmin", "Descent Mk", weak = true,
        hint = "Garmin Descent Mk 系列走 Garmin Sidecar 通道"),
    DeviceNamePattern(Regex("^(?:Garmin\\s*)?Descent\\s*G[12]\\b", RegexOption.IGNORE_CASE),
        "Garmin", "Descent", weak = true,
        hint = "Garmin Descent G 系列走 Garmin Sidecar 通道"),
    DeviceNamePattern(Regex("^(?:Garmin\\s*)?(?:Descent\\s*)?X50i\\b", RegexOption.IGNORE_CASE),
        "Garmin", "X50i", weak = true, hint = "Garmin X50i 走 Garmin Sidecar 通道"),
    DeviceNamePattern(Regex("^(?:Garmin\\s*)?(?:Descent\\s*)?X30\\b", RegexOption.IGNORE_CASE),
        "Garmin", "X30",  weak = true, hint = "Garmin X30 走 Garmin Sidecar 通道"),
    DeviceNamePattern(Regex("^(?:Garmin\\s*)?Descent\\b", RegexOption.IGNORE_CASE),
        "Garmin", "Descent", ambiguous = true, weak = true,
        hint = "仅识别到 Garmin Descent 系列，需确认型号"),

    // ── Shearwater ──
    DeviceNamePattern(Regex("^Petrel\\s*3\\b", RegexOption.IGNORE_CASE), "Shearwater", "Petrel 3"),
    DeviceNamePattern(Regex("^Petrel\\s*2\\b", RegexOption.IGNORE_CASE), "Shearwater", "Petrel 2"),
    DeviceNamePattern(Regex("^Petrel\\b",      RegexOption.IGNORE_CASE), "Shearwater", "Petrel 2",
        ambiguous = true, hint = "Petrel 系列默认按 Petrel 2 协议"),
    DeviceNamePattern(Regex("^Perdix\\s*2\\b", RegexOption.IGNORE_CASE), "Shearwater", "Perdix 2"),
    DeviceNamePattern(Regex("^Perdix\\s*AI\\b",RegexOption.IGNORE_CASE), "Shearwater", "Perdix AI"),
    DeviceNamePattern(Regex("^Perdix\\b",      RegexOption.IGNORE_CASE), "Shearwater", "Perdix",
        ambiguous = true, hint = "Perdix 系列默认按 Perdix 协议"),
    DeviceNamePattern(Regex("^Teric\\b",       RegexOption.IGNORE_CASE), "Shearwater", "Teric"),
    DeviceNamePattern(Regex("^Peregrine\\b",   RegexOption.IGNORE_CASE), "Shearwater", "Peregrine"),
    DeviceNamePattern(Regex("^Nerd\\s*2\\b",   RegexOption.IGNORE_CASE), "Shearwater", "Nerd 2"),
    DeviceNamePattern(Regex("^Nerd\\b",        RegexOption.IGNORE_CASE), "Shearwater", "Nerd"),

    // ── Suunto BLE ──
    DeviceNamePattern(Regex("^EON\\s*Steel\\s*Black\\b", RegexOption.IGNORE_CASE), "Suunto", "EON Steel Black"),
    DeviceNamePattern(Regex("^EON\\s*Steel\\b",         RegexOption.IGNORE_CASE), "Suunto", "EON Steel"),
    DeviceNamePattern(Regex("^EON\\s*Core\\b",          RegexOption.IGNORE_CASE), "Suunto", "EON Core"),
    DeviceNamePattern(Regex("^(?:Suunto\\s*)?D5\\b",    RegexOption.IGNORE_CASE), "Suunto", "D5"),

    // ── Scubapro ──
    DeviceNamePattern(Regex("^G2\\b",          RegexOption.IGNORE_CASE), "Scubapro", "G2"),
    DeviceNamePattern(Regex("^Aladin\\s*A1\\b",RegexOption.IGNORE_CASE), "Scubapro", "Aladin A1"),
    DeviceNamePattern(Regex("^Aladin\\s*A2\\b",RegexOption.IGNORE_CASE), "Scubapro", "Aladin A2"),

    // ── Mares ──
    DeviceNamePattern(Regex("^Genius\\b",      RegexOption.IGNORE_CASE), "Mares", "Genius"),
    DeviceNamePattern(Regex("^Quad\\s*Air\\b", RegexOption.IGNORE_CASE), "Mares", "Quad Air"),
    DeviceNamePattern(Regex("^Quad\\b",        RegexOption.IGNORE_CASE), "Mares", "Quad"),
    DeviceNamePattern(Regex("^Puck\\s*Pro\\b", RegexOption.IGNORE_CASE), "Mares", "Puck Pro"),
    DeviceNamePattern(Regex("^Smart\\b",       RegexOption.IGNORE_CASE), "Mares", "Smart",
        ambiguous = true, hint = "Smart / Smart Air / Smart Apnea 共用广播名"),

    // ── Aqualung ──
    DeviceNamePattern(Regex("^i770R\\b",       RegexOption.IGNORE_CASE), "Aqualung", "i770R"),
    DeviceNamePattern(Regex("^i750TC\\b",      RegexOption.IGNORE_CASE), "Aqualung", "i750TC"),
    DeviceNamePattern(Regex("^i550C\\b",       RegexOption.IGNORE_CASE), "Aqualung", "i550C"),
    DeviceNamePattern(Regex("^i470TC\\b",      RegexOption.IGNORE_CASE), "Aqualung", "i470TC"),
    DeviceNamePattern(Regex("^i300C\\b",       RegexOption.IGNORE_CASE), "Aqualung", "i300C"),

    // ── Heinrichs Weikamp OSTC ──
    DeviceNamePattern(Regex("^OSTC\\s*Plus\\b", RegexOption.IGNORE_CASE), "Heinrichs Weikamp", "OSTC Plus"),
    DeviceNamePattern(Regex("^OSTC\\s*4\\b",    RegexOption.IGNORE_CASE), "Heinrichs Weikamp", "OSTC 4"),
    DeviceNamePattern(Regex("^OSTC\\s*Sport\\b",RegexOption.IGNORE_CASE), "Heinrichs Weikamp", "OSTC Sport"),
    DeviceNamePattern(Regex("^OSTC\\s*2\\b",    RegexOption.IGNORE_CASE), "Heinrichs Weikamp", "OSTC 2"),
)

/** 识别结果 */
sealed interface VendorMatch {
    object Unknown : VendorMatch
    data class Hit(
        val vendor: String,
        val product: String,
        /** 设备名 ambiguous（多个型号共用广播名）→ UI 提示用户确认 */
        val ambiguous: Boolean = false,
        /** weak = 只识别到品牌系列，需要走特殊通道（如 Garmin sidecar） */
        val weak: Boolean = false,
        val hint: String? = null,
        /** 'service-uuid' / 'device-name'：debug 用，了解为什么命中 */
        val source: String,
    ) : VendorMatch
}

object VendorDetector {
    /**
     * 综合 service UUIDs（来自 BLE advertisement）+ 广播 name 来识别。
     * service UUID 优先 —— 如果广播包含已知 service，直接命中 vendor，不依赖 name。
     */
    fun detect(advertisedName: String?, serviceUuids: List<UUID>): VendorMatch {
        for (uuid in serviceUuids) {
            SERVICE_TO_VENDOR[uuid]?.let { vendor ->
                // 拿到 vendor 后还可以尝试用 name 进一步定位 product
                val productHit = (advertisedName?.let(::detectByName) as? VendorMatch.Hit)
                if (productHit != null && productHit.vendor.equals(vendor, ignoreCase = true)) {
                    return productHit.copy(source = "service-uuid+name")
                }
                return VendorMatch.Hit(
                    vendor = vendor,
                    product = "(未确定型号)",
                    ambiguous = true,
                    weak = vendor.equals("Garmin", ignoreCase = true),
                    source = "service-uuid",
                )
            }
        }
        return advertisedName?.let(::detectByName) ?: VendorMatch.Unknown
    }

    private fun detectByName(name: String): VendorMatch {
        if (name.isBlank()) return VendorMatch.Unknown
        for (p in DEVICE_NAME_PATTERNS) {
            if (p.regex.containsMatchIn(name)) {
                return VendorMatch.Hit(
                    vendor = p.vendor,
                    product = p.product,
                    ambiguous = p.ambiguous,
                    weak = p.weak,
                    hint = p.hint,
                    source = "device-name",
                )
            }
        }
        return VendorMatch.Unknown
    }
}
