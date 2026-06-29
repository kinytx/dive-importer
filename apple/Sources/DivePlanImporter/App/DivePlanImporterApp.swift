//
//  DivePlanImporterApp.swift
//  DivePlan Importer
//
//  iOS + macOS 共用入口（SwiftUI App protocol）。
//
//  路由策略（P1）：
//    - 启动时根据 [ApiKeyStore.isBound] 决定首屏：
//        - 有 key → 占位 Home（P2 替换成 ScanScreen）
//        - 无 key → BindScreen
//    - 处理 deep link `diveplan://ble-probe/bind?code=123456`：
//        - onOpenURL 提取出 6 位码后塞进 [BindViewModel.submit]，UI 自动跳到「绑定中」
//
//  P2 时这里加 NavigationStack；P1 阶段两屏切换够用了。
//

import SwiftUI

@main
struct DivePlanImporterApp: App {
    @State private var apiKeyStore = ApiKeyStore.shared
    /// 跟 deep link 共享同一个 VM 实例：onOpenURL → submit → BindScreen 自动反映 Submitting
    @State private var bindViewModel = BindViewModel()

    /// 选中设备的临时 alert state（P3 真接入抓 dump 后删）
    @State private var selectedAlertText: String?

    var body: some Scene {
        WindowGroup {
            rootScreen
                .onOpenURL(perform: handleIncomingURL)
                .alert("选中设备",
                       isPresented: Binding(
                           get: { selectedAlertText != nil },
                           set: { if !$0 { selectedAlertText = nil } }),
                       actions: { Button("好") { selectedAlertText = nil } },
                       message: { Text(selectedAlertText ?? "") })
        }
        #if os(macOS)
        .windowResizability(.contentSize)
        #endif
    }

    @ViewBuilder
    private var rootScreen: some View {
        if apiKeyStore.isBound {
            // 已绑定 → ScanScreen（P3 加点击设备进入抓 dump；现在仅弹 alert 验证选中态）
            ScanScreen(onDeviceSelected: { device in
                let info: String = {
                    if case let .hit(vendor, product, ambiguous, weak, _, _) = device.vendorMatch {
                        var s = "\(vendor) \(product)"
                        if weak { s += " · 走特殊通道" }
                        if ambiguous { s += " · 待确认" }
                        return s
                    }
                    return "未识别"
                }()
                selectedAlertText = "\(info)\n\(device.name ?? device.id.uuidString.prefix(8).description)"
            })
        } else {
            // 未绑定 → BindScreen
            BindScreen(
                viewModel: bindViewModel,
                onBound: {
                    // ApiKeyStore.isBound 变化会自动触发 rootScreen 重新计算并切到 Home
                },
            )
        }
    }

    private func handleIncomingURL(_ url: URL) {
        guard let code = extractBindCode(from: url.absoluteString) else { return }
        bindViewModel.submit(codeOverride: code)
    }
}
