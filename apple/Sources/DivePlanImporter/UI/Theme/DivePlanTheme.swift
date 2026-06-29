//
//  DivePlanTheme.swift
//
//  把 DivePlanColor 装进 SwiftUI environment，方便所有 View 用 @Environment(\.divePlanTheme) 拿。
//  P0 阶段只暴露颜色和字号；P1+ 会再加 cornerRadius / spacing token。
//

import SwiftUI

// MARK: - Spacing / Corner

enum DivePlanSpacing {
    static let xs:  CGFloat = 4
    static let sm:  CGFloat = 8
    static let md:  CGFloat = 12
    static let lg:  CGFloat = 16
    static let xl:  CGFloat = 24
    static let xxl: CGFloat = 32
}

enum DivePlanRadius {
    static let chip: CGFloat = 999
    static let card: CGFloat = 18
    static let input: CGFloat = 12
    static let badge: CGFloat = 8
}

// MARK: - Typography

enum DivePlanFont {
    static let hero         = Font.system(size: 28, weight: .bold)
    static let title        = Font.system(size: 22, weight: .bold)
    static let cardTitle    = Font.system(size: 16, weight: .semibold)
    static let body         = Font.system(size: 14, weight: .regular)
    static let bodySmall    = Font.system(size: 12, weight: .regular)
    static let label        = Font.system(size: 11, weight: .medium)
    static let monoSmall    = Font.system(size: 12, weight: .regular, design: .monospaced)
}

// MARK: - Environment key（占位，P1 用）

private struct DivePlanThemeKey: EnvironmentKey {
    static let defaultValue = DivePlanThemeValues()
}

struct DivePlanThemeValues {
    // 后续 P1 会把可主题化 token 放这里（不是 color，因为 color 用 DivePlanColor 直接读）
    var dummyForP0: Bool = true
}

extension EnvironmentValues {
    var divePlanTheme: DivePlanThemeValues {
        get { self[DivePlanThemeKey.self] }
        set { self[DivePlanThemeKey.self] = newValue }
    }
}
