//
//  BindScreen.swift
//
//  账号绑定主屏（首次启动 / 用户主动解绑后落到这）。
//
//  设计：顶部 Picker「输入码 / 扫码」二选一；底部按钮 / 错误 / Bound 反馈。
//

import SwiftUI

struct BindScreen: View {
    @Bindable var viewModel: BindViewModel
    var onBound: () -> Void

    @State private var tab: Tab = .inputCode
    enum Tab: String, CaseIterable, Identifiable {
        case inputCode = "输入码"
        case scanQr    = "扫码"
        var id: String { rawValue }
    }

    var body: some View {
        ZStack {
            DivePlanColor.background.ignoresSafeArea()

            VStack(alignment: .center, spacing: DivePlanSpacing.lg) {
                header

                if viewModel.phase == .bound {
                    BoundSuccessCard(
                        prefix: viewModel.successPrefix ?? "",
                        onContinue: {
                            viewModel.acknowledgeBound()
                            onBound()
                        },
                    )
                    .padding(.horizontal, DivePlanSpacing.lg)
                    Spacer()
                } else {
                    Picker("绑定方式", selection: $tab) {
                        ForEach(Tab.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, DivePlanSpacing.lg)

                    switch tab {
                    case .inputCode:
                        inputCodePane
                            .padding(.horizontal, DivePlanSpacing.lg)
                    case .scanQr:
                        QrScanView(
                            onCodeDetected: { code in viewModel.submit(codeOverride: code) },
                            onCancel: { tab = .inputCode },
                        )
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    Spacer(minLength: 0)
                }
            }
            .padding(.top, DivePlanSpacing.xl)
        }
    }

    private var header: some View {
        VStack(spacing: DivePlanSpacing.xs) {
            Text("绑定账号")
                .font(DivePlanFont.hero)
                .foregroundStyle(DivePlanColor.textPrimary)
            Text("在小程序「我的 → 潜水电脑」生成绑定码后，扫描或输入")
                .font(DivePlanFont.body)
                .foregroundStyle(DivePlanColor.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, DivePlanSpacing.xl)
        }
    }

    private var inputCodePane: some View {
        VStack(alignment: .leading, spacing: DivePlanSpacing.md) {
            CodeInputField(
                value: Binding(
                    get: { viewModel.code },
                    set: { viewModel.onCodeChange($0) },
                ),
                enabled: viewModel.phase != .submitting,
                onSubmit: { viewModel.submit() },
            )

            Text("6 位数字码 · 有效期 10 分钟")
                .font(DivePlanFont.bodySmall)
                .foregroundStyle(DivePlanColor.textSecondary)

            if let err = viewModel.error {
                Text(err.message)
                    .font(DivePlanFont.body)
                    .foregroundStyle(DivePlanColor.warn)
            }

            Button {
                viewModel.submit()
            } label: {
                Text(viewModel.phase == .submitting ? "绑定中…" : "确认绑定")
                    .font(DivePlanFont.cardTitle)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, DivePlanSpacing.sm)
            }
            .buttonStyle(.borderedProminent)
            .tint(DivePlanColor.primary)
            .disabled(viewModel.code.count != 6 || viewModel.phase == .submitting)
            .padding(.top, DivePlanSpacing.sm)
        }
    }
}

// MARK: - Bound 成功反馈卡

private struct BoundSuccessCard: View {
    let prefix: String
    let onContinue: () -> Void

    var body: some View {
        DivePlanCard {
            VStack(alignment: .center, spacing: DivePlanSpacing.sm) {
                Text("✅ 已绑定")
                    .font(DivePlanFont.cardTitle)
                    .foregroundStyle(DivePlanColor.textPrimary)
                if !prefix.isEmpty {
                    Text("凭证前缀：\(prefix)")
                        .font(DivePlanFont.body)
                        .foregroundStyle(DivePlanColor.textSecondary)
                }
                Button {
                    onContinue()
                } label: {
                    Text("开始扫描潜水电脑 →")
                        .font(DivePlanFont.cardTitle)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, DivePlanSpacing.sm)
                }
                .buttonStyle(.borderedProminent)
                .tint(DivePlanColor.primary)
                .padding(.top, DivePlanSpacing.sm)
            }
        }
    }
}
