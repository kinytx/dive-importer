//
//  PlaceholderHomeScreen.swift
//
//  P0 占位首页 —— 仅验证主题、字号、卡片渲染。
//  P1 之后被 Navigation 替换成真正的 bind / scan / sync 流程。
//

import SwiftUI

struct PlaceholderHomeScreen: View {
    var body: some View {
        ZStack {
            DivePlanColor.background.ignoresSafeArea()

            VStack(spacing: DivePlanSpacing.lg) {
                Spacer()

                VStack(spacing: DivePlanSpacing.xs) {
                    Text("潜水日志导入助手")
                        .font(DivePlanFont.hero)
                        .foregroundStyle(DivePlanColor.textPrimary)
                    Text("DivePlan · \(platformLabel) v0.1.0")
                        .font(DivePlanFont.body)
                        .foregroundStyle(DivePlanColor.textSecondary)
                }

                DivePlanCard {
                    VStack(alignment: .leading, spacing: DivePlanSpacing.sm) {
                        Text("✅ 项目骨架已就位（P0）")
                            .font(DivePlanFont.cardTitle)
                            .foregroundStyle(DivePlanColor.textPrimary)
                        Text("下一步 P1:账号绑定（二维码 + 6 位码）")
                            .font(DivePlanFont.body)
                            .foregroundStyle(DivePlanColor.textSecondary)
                    }
                }
                .padding(.horizontal, DivePlanSpacing.lg)

                Spacer()
            }
        }
        #if os(macOS)
        .frame(minWidth: 380, idealWidth: 480, minHeight: 600, idealHeight: 720)
        #endif
    }

    private var platformLabel: String {
        #if os(iOS)
        return "iOS"
        #elseif os(macOS)
        return "macOS"
        #else
        return "Apple"
        #endif
    }
}

#Preview("Dark") {
    PlaceholderHomeScreen()
        .preferredColorScheme(.dark)
}

#Preview("Light") {
    PlaceholderHomeScreen()
        .preferredColorScheme(.light)
}
