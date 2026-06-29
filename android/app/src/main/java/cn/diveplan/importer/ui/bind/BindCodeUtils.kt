package cn.diveplan.importer.ui.bind

/**
 * 把扫码 / deep link 拿到的字符串归一化成 6 位绑定码。
 *
 * 支持的输入形态：
 *   - 纯 6 位数字: "123456"
 *   - deep link:   "diveplan://ble-probe/bind?code=123456"
 *   - 含杂字符:    "123-456 " "Code 123456" → "123456"
 *
 * 服务端 `NormalizeCode` 也只取前 6 位数字 —— 跟它对齐。
 */
fun extractBindCode(raw: String?): String? {
    if (raw.isNullOrBlank()) return null

    // 先尝试解 URL（容错失败也无所谓）
    val text = if (raw.startsWith("diveplan://", ignoreCase = true)) {
        // 简易解析 ?code=xxx；不引 java.net.URI 避免它对 custom scheme 不识别的情况
        raw.substringAfter("?", missingDelimiterValue = "")
            .split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "code" }
            ?.get(1)
            ?: raw
    } else raw

    val digits = text.filter { it.isDigit() }
    if (digits.length < 6) return null
    return digits.take(6)
}
