//
//  CodeInputField.swift
//
//  6 位 OTP-style 输入框 —— 6 个可视化方框，光标永远在最后一位之后。
//
//  实现技巧：底下铺一个透明 TextField 接管系统键盘，上面显示 6 个 box 用 Text 渲染数字。
//
//  SwiftUI 在 iOS 17+ 支持 .keyboardType / .textContentType(.oneTimeCode)，
//  macOS 上 .keyboardType 被忽略但 TextField 仍工作。
//

import SwiftUI

struct CodeInputField: View {
    @Binding var value: String
    var enabled: Bool
    var onSubmit: () -> Void

    @FocusState private var focused: Bool

    var body: some View {
        ZStack {
            // 1) 6 个可视化方框
            HStack(spacing: DivePlanSpacing.sm) {
                ForEach(0..<6, id: \.self) { i in
                    box(at: i)
                }
            }

            // 2) 透明 TextField 吃键盘
            TextField("", text: Binding(
                get: { value },
                set: { new in
                    let normalized = new.filter(\.isASCII).filter(\.isNumber).prefix(6).description
                    if normalized != value { value = normalized }
                },
            ))
            .focused($focused)
            .disabled(!enabled)
            .foregroundStyle(.clear)
            .tint(.clear)            // 隐藏光标
            .keyboardTypeNumberPadCompat()
            .textContentTypeOneTimeCodeCompat()
            .onSubmit(onSubmit)
        }
        .frame(height: 56)
        .onAppear { focused = true }
    }

    @ViewBuilder
    private func box(at index: Int) -> some View {
        let chars = Array(value)
        let active = index == chars.count && enabled
        let ch = chars.indices.contains(index) ? String(chars[index]) : ""

        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(active
                      ? DivePlanColor.primary.opacity(0.10)
                      : DivePlanColor.surfaceInput)
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(
                    active ? DivePlanColor.primary : DivePlanColor.border,
                    lineWidth: active ? 2 : 1,
                )
            Text(ch)
                .font(.system(size: 26, weight: .bold, design: .monospaced))
                .foregroundStyle(DivePlanColor.textPrimary)
        }
        .frame(maxWidth: .infinity, minHeight: 56)
    }
}

// MARK: - Cross-platform modifier compat

private extension View {
    /// iOS 设数字键盘；macOS 忽略
    @ViewBuilder
    func keyboardTypeNumberPadCompat() -> some View {
        #if os(iOS)
        self.keyboardType(.numberPad)
        #else
        self
        #endif
    }

    /// iOS 17+ OTP 自动填充提示；macOS 忽略
    @ViewBuilder
    func textContentTypeOneTimeCodeCompat() -> some View {
        #if os(iOS)
        self.textContentType(.oneTimeCode)
        #else
        self
        #endif
    }
}
