//
//  DivePlanColors.swift
//
//  配色 token —— 对齐小程序 shared/app.wxss 的 dark / light 两套主题。
//
//    dark   = 深海蓝 + 青绿（默认；夜潜 / 主流偏好）
//    light  = 米白沙 + 礁湖青绿（白天 / 度假村感）
//
//  设计原则（跟小程序、Android importer 一致）：
//    - 安全色（warn）跨主题永远是饱和红 —— PPO2 越限、减压违例等致命提示必须一眼可见
//    - 氦气色（accentHe）跨主题永远琥珀 / 珊瑚橙 —— 潜水行业色标
//    - 主色（cyan / 礁湖青绿）按主题切换
//
//  用法：业务代码用 DivePlanColor.primary（自适应 colorScheme），
//  不直接引用 ._dark 或 ._light。
//

import SwiftUI

/// 跨平台命名空间。配色按 colorScheme 自动切换。
enum DivePlanColor {
    // 主色（cyan / 礁湖青绿）
    static let primary       = adaptive(dark: _dark.accentCyan,   light: _light.accentCyan)
    static let primaryTint   = adaptive(dark: _dark.accentTint,   light: _light.accentTint)
    static let onPrimary     = adaptive(dark: _dark.textOnAccent, light: _light.textOnAccent)

    // 次色（teal）
    static let secondary     = adaptive(dark: _dark.accentTeal,   light: _light.accentTeal)

    // 氦气琥珀
    static let helium        = adaptive(dark: _dark.accentHe,     light: _light.accentHe)

    // 安全色
    static let success       = adaptive(dark: _dark.accentSuccess, light: _light.accentSuccess)
    static let warn          = adaptive(dark: _dark.accentWarn,    light: _light.accentWarn)

    // 背景层
    static let background    = adaptive(dark: _dark.bgDeep,  light: _light.bgDeep)
    static let surface       = adaptive(dark: _dark.bgCard,  light: _light.bgCard)
    static let surfaceInput  = adaptive(dark: _dark.bgInput, light: _light.bgInput)

    // 文字层
    static let textPrimary   = adaptive(dark: _dark.textPrimary,   light: _light.textPrimary)
    static let textSecondary = adaptive(dark: _dark.textSecondary, light: _light.textSecondary)
    static let textMuted     = adaptive(dark: _dark.textMuted,     light: _light.textMuted)

    // 边框
    static let border        = adaptive(dark: _dark.border,        light: _light.border)
    static let borderActive  = adaptive(dark: _dark.borderActive,  light: _light.borderActive)
}

// MARK: - Palette tables

private enum _dark {
    static let bgDeep        = Color(hex: 0x0A1628)
    static let bgCard        = Color(hex: 0x0F2040)
    static let bgInput       = Color(hex: 0x0D1F3A)

    static let accentCyan    = Color(hex: 0x00D4FF)
    static let accentTeal    = Color(hex: 0x00B8A9)
    static let accentHe      = Color(hex: 0xF0A030)
    static let accentSuccess = Color(hex: 0x30D158)
    static let accentWarn    = Color(hex: 0xFF3B30)

    static let textPrimary   = Color(hex: 0xE8F4FD)
    static let textSecondary = Color(hex: 0x8AB4D4)
    static let textMuted     = Color(hex: 0x6B8DB0)
    static let textOnAccent  = Color(hex: 0x0A1628)

    static let border        = Color(hex: 0x00D4FF, opacity: 0.15)
    static let borderActive  = Color(hex: 0x00D4FF, opacity: 0.50)
    static let accentTint    = Color(hex: 0x00D4FF, opacity: 0.10)
}

private enum _light {
    static let bgDeep        = Color(hex: 0xFFFAEF)
    static let bgCard        = Color(hex: 0xFFFFFF)
    static let bgInput       = Color(hex: 0xEFFAF5)

    static let accentCyan    = Color(hex: 0x4CB8A8)
    static let accentTeal    = Color(hex: 0xF5A623)
    static let accentHe      = Color(hex: 0xFF7E5F)
    static let accentSuccess = Color(hex: 0x7CC676)
    static let accentWarn    = Color(hex: 0xE74C3C)

    static let textPrimary   = Color(hex: 0x2A4A4D)
    static let textSecondary = Color(hex: 0x5A7A7D)
    static let textMuted     = Color(hex: 0x9AB0B0)
    static let textOnAccent  = Color(hex: 0xFFFFFF)

    static let border        = Color(hex: 0x4CB8A8, opacity: 0.24)
    static let borderActive  = Color(hex: 0x4CB8A8, opacity: 0.55)
    static let accentTint    = Color(hex: 0x4CB8A8, opacity: 0.14)
}

/// 按 colorScheme 自适应：dark → first, light → second
private func adaptive(dark: Color, light: Color) -> Color {
    Color(uiColorDynamic(dark: dark, light: light))
}

// MARK: - Color hex helper

extension Color {
    /// 0xRRGGBB 整数初始化（跨 iOS / macOS）
    init(hex: UInt32, opacity: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >>  8) & 0xFF) / 255.0
        let b = Double( hex        & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: opacity)
    }
}

// MARK: - 跨平台 adaptive Color

#if canImport(UIKit)
import UIKit
private func uiColorDynamic(dark: Color, light: Color) -> UIColor {
    UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(dark)
            : UIColor(light)
    }
}
#elseif canImport(AppKit)
import AppKit
private func uiColorDynamic(dark: Color, light: Color) -> NSColor {
    NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .vibrantDark]) != nil
        return isDark ? NSColor(dark) : NSColor(light)
    }
}
#else
private func uiColorDynamic(dark: Color, light _: Color) -> Color {
    // 极端 fallback（应该不会命中）
    dark
}
#endif
