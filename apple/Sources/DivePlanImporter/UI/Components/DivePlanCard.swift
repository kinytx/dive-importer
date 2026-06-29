//
//  DivePlanCard.swift
//
//  跟小程序 .card 类对应的卡片容器。
//  统一 padding / corner / border / shadow，让所有页面卡片一眼是同一个产品。
//

import SwiftUI

struct DivePlanCard<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(DivePlanSpacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(DivePlanColor.surface)
            .overlay(
                RoundedRectangle(cornerRadius: DivePlanRadius.card)
                    .stroke(DivePlanColor.border, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: DivePlanRadius.card))
            .shadow(color: .black.opacity(0.18), radius: 12, x: 0, y: 4)
    }
}

#Preview {
    DivePlanCard {
        Text("Hello DivePlan").foregroundStyle(DivePlanColor.textPrimary)
    }
    .padding()
    .background(DivePlanColor.background)
}
