//
//  ScanScreen.swift
//
//  扫描设备列表屏。
//  - 顶部状态条：扫描中/已停止 + 设备数 + ↻ 重新扫描
//  - 列表：按 vendor hit / RSSI 排序，每条显示「badge · 名字 · vendor product · RSSI」
//  - 点击设备 → onDeviceSelected（P3 加抓 dump）
//
//  跟 Android `ScanScreen.kt` 行为一致。
//

import SwiftUI
import CoreBluetooth

struct ScanScreen: View {
    @Bindable var scanner = BleScanner.shared
    var onDeviceSelected: (DiscoveredBleDevice) -> Void

    var body: some View {
        ZStack {
            DivePlanColor.background.ignoresSafeArea()
            VStack(spacing: 0) {
                statusBar
                if let err = scanner.lastError {
                    errorCard(err: err)
                }
                contentList
            }
        }
        .onAppear { scanner.start() }
        .onDisappear { scanner.stop() }
    }

    private var statusBar: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(scanner.scanning ? "🔍 扫描中…" : "扫描已停止")
                    .font(DivePlanFont.cardTitle)
                    .foregroundStyle(DivePlanColor.textPrimary)
                Text("已发现 \(sortedDevices.count) 台设备")
                    .font(DivePlanFont.bodySmall)
                    .foregroundStyle(DivePlanColor.textSecondary)
            }
            Spacer()
            Button {
                if scanner.scanning { scanner.stop() } else { scanner.start() }
            } label: {
                Text(scanner.scanning ? "停止" : "↻ 扫描")
            }
            .buttonStyle(.bordered)
            .tint(DivePlanColor.primary)
        }
        .padding(DivePlanSpacing.lg)
    }

    @ViewBuilder
    private func errorCard(err: BleScanError) -> some View {
        DivePlanCard {
            VStack(alignment: .leading, spacing: DivePlanSpacing.sm) {
                Text(errorTitle(err))
                    .font(DivePlanFont.cardTitle)
                    .foregroundStyle(DivePlanColor.warn)
                if let detail = errorDetail(err) {
                    Text(detail)
                        .font(DivePlanFont.body)
                        .foregroundStyle(DivePlanColor.textSecondary)
                }
            }
        }
        .padding(.horizontal, DivePlanSpacing.lg)
    }

    private func errorTitle(_ err: BleScanError) -> String {
        switch err {
        case .bluetoothOff:   return "蓝牙未开启"
        case .unauthorized:   return "未授权使用蓝牙"
        case .unsupported:    return "此设备不支持低功耗蓝牙"
        case .unknown:        return "蓝牙暂时不可用"
        }
    }

    private func errorDetail(_ err: BleScanError) -> String? {
        switch err {
        case .bluetoothOff: return "请在控制中心 / 系统设置中打开蓝牙"
        case .unauthorized: return "请在 设置 → 隐私 → 蓝牙 里允许本 App"
        default: return nil
        }
    }

    @ViewBuilder
    private var contentList: some View {
        if sortedDevices.isEmpty {
            VStack(spacing: DivePlanSpacing.sm) {
                Spacer()
                Text(scanner.scanning ? "等待发现潜水电脑…" : "未发现设备")
                    .font(DivePlanFont.cardTitle)
                    .foregroundStyle(DivePlanColor.textPrimary)
                Text(scanner.scanning
                     ? "请确认电脑表已开机并进入配对模式"
                     : "点击右上「扫描」开始")
                    .font(DivePlanFont.body)
                    .foregroundStyle(DivePlanColor.textSecondary)
                Spacer()
            }
            .padding(.horizontal, DivePlanSpacing.xl)
        } else {
            ScrollView {
                LazyVStack(spacing: DivePlanSpacing.sm) {
                    ForEach(sortedDevices) { device in
                        DeviceRow(device: device)
                            .onTapGesture { onDeviceSelected(device) }
                    }
                }
                .padding(.horizontal, DivePlanSpacing.lg)
                .padding(.bottom, DivePlanSpacing.xl)
            }
        }
    }

    /// 排序：已识别 vendor 优先，其次 RSSI 大优先，相等时 firstSeenAt 早优先
    private var sortedDevices: [DiscoveredBleDevice] {
        scanner.devices.sorted { a, b in
            if a.vendorMatch.isHit != b.vendorMatch.isHit {
                return a.vendorMatch.isHit
            }
            if a.rssi != b.rssi { return a.rssi > b.rssi }
            return a.firstSeenAt < b.firstSeenAt
        }
    }
}

private struct DeviceRow: View {
    let device: DiscoveredBleDevice

    var body: some View {
        DivePlanCard {
            HStack(spacing: DivePlanSpacing.md) {
                vendorBadge
                VStack(alignment: .leading, spacing: 2) {
                    Text(device.name ?? "(无广播名)")
                        .font(DivePlanFont.cardTitle)
                        .foregroundStyle(DivePlanColor.textPrimary)
                    if case let .hit(vendor, product, ambiguous, weak, hint, _) = device.vendorMatch {
                        Text("\(vendor) \(product)\(ambiguous ? "（请确认型号）" : "")")
                            .font(DivePlanFont.body)
                            .foregroundStyle(DivePlanColor.primary)
                        if weak, let hint {
                            Text(hint)
                                .font(DivePlanFont.bodySmall)
                                .foregroundStyle(DivePlanColor.helium)
                        }
                    } else {
                        Text("未识别（点击手动选）")
                            .font(DivePlanFont.bodySmall)
                            .foregroundStyle(DivePlanColor.textSecondary)
                    }
                    Text(device.id.uuidString.prefix(8) + "…")
                        .font(DivePlanFont.monoSmall)
                        .foregroundStyle(DivePlanColor.textMuted)
                }
                Spacer()
                rssiBadge
            }
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var vendorBadge: some View {
        let (label, bg): (String, Color) = {
            if case let .hit(vendor, _, _, weak, _, _) = device.vendorMatch {
                return (String(vendor.prefix(1)).uppercased(),
                        weak ? DivePlanColor.helium : DivePlanColor.primary)
            }
            return ("?", DivePlanColor.surfaceInput)
        }()
        ZStack {
            Circle().fill(bg).frame(width: 40, height: 40)
            Text(label)
                .font(DivePlanFont.cardTitle)
                .foregroundStyle(DivePlanColor.onPrimary)
        }
    }

    private var rssiBadge: some View {
        let strength: String = switch device.rssi {
            case (-55)...:    "●●●●"
            case (-70)...(-56): "●●●○"
            case (-85)...(-71): "●●○○"
            default:           "●○○○"
        }
        return VStack(alignment: .trailing, spacing: 2) {
            Text(strength)
                .font(DivePlanFont.bodySmall)
                .foregroundStyle(DivePlanColor.primary)
            Text("\(device.rssi) dBm")
                .font(DivePlanFont.bodySmall)
                .foregroundStyle(DivePlanColor.textMuted)
        }
    }
}
