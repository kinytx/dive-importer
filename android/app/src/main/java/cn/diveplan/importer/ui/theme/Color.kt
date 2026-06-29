package cn.diveplan.importer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 配色 token —— 对齐小程序 shared/app.wxss 的 dark / light 两套主题。
 *
 *   dark   = 深海蓝 + 青绿（默认；夜潜 / 主流偏好）
 *   light  = 米白沙 + 礁湖青绿（白天 / 度假村感）
 *
 * 用法：DivePlanImporterTheme 把这些塞进 Material3 ColorScheme。
 * 业务代码统一从 MaterialTheme.colorScheme.* 取，不要直接 import 本文件。
 */

// ─────────────────────────────────────────────────────────────
//  Dark （默认）—— 跟小程序 .theme-dark 一一对应
// ─────────────────────────────────────────────────────────────
internal object DarkPalette {
    val BgDeep        = Color(0xFF0A1628)  // page 背景
    val BgCard        = Color(0xFF0F2040)  // 卡片
    val BgInput       = Color(0xFF0D1F3A)  // 输入框 / 凹陷

    val AccentCyan    = Color(0xFF00D4FF)  // primary
    val AccentTeal    = Color(0xFF00B8A9)  // secondary
    val AccentHe      = Color(0xFFF0A030)  // tertiary（氦气琥珀色，跨主题保留）
    val AccentSuccess = Color(0xFF30D158)
    val AccentWarn    = Color(0xFFFF3B30)  // 安全红线，跨主题保留可识别

    val TextPrimary   = Color(0xFFE8F4FD)
    val TextSecondary = Color(0xFF8AB4D4)
    val TextMuted     = Color(0xFF6B8DB0)
    val TextOnAccent  = Color(0xFF0A1628)  // CTA 按钮上的反色字（深底）

    val Border        = Color(0x2600D4FF)   // 0.15 alpha cyan
    val BorderActive  = Color(0x8000D4FF)   // 0.50 alpha cyan
}

// ─────────────────────────────────────────────────────────────
//  Light —— 跟小程序 .theme-light 一一对应（暖底 + 冷主色）
// ─────────────────────────────────────────────────────────────
internal object LightPalette {
    val BgDeep        = Color(0xFFFFFAEF)
    val BgCard        = Color(0xFFFFFFFF)
    val BgInput       = Color(0xFFEFFAF5)

    val AccentCyan    = Color(0xFF4CB8A8)
    val AccentTeal    = Color(0xFFF5A623)
    val AccentHe      = Color(0xFFFF7E5F)
    val AccentSuccess = Color(0xFF7CC676)
    val AccentWarn    = Color(0xFFE74C3C)

    val TextPrimary   = Color(0xFF2A4A4D)
    val TextSecondary = Color(0xFF5A7A7D)
    val TextMuted     = Color(0xFF9AB0B0)
    val TextOnAccent  = Color(0xFFFFFFFF)

    val Border        = Color(0x3D4CB8A8)   // 0.24 alpha
    val BorderActive  = Color(0x8C4CB8A8)   // 0.55 alpha
}
