//
//  BleScanner.swift
//
//  跨 iOS / macOS BLE 扫描 + vendor 自动识别（CoreBluetooth）。
//  跟 Android `BleScanner.kt` 行为一致：
//    - 单例 + @Observable 暴露 devices / scanning / error，SwiftUI 直接观察
//    - 内部按 peripheral.identifier 去重，新 RSSI 覆盖旧值但 firstSeenAt 保留
//
//  权限模型（不同于 Android）：
//    - iOS：CBCentralManager 初始化时弹「需要使用蓝牙」系统对话（Info.plist NSBluetoothAlwaysUsageDescription）
//    - macOS：sandbox entitlement `com.apple.security.device.bluetooth = true` 之外，
//      首次 powerOn 时弹系统对话
//

import Foundation
import CoreBluetooth
import Observation

@Observable
@MainActor
final class BleScanner: NSObject {
    static let shared = BleScanner()

    private(set) var devices: [DiscoveredBleDevice] = []
    private(set) var scanning: Bool = false
    private(set) var lastError: BleScanError?
    /// 暴露当前 CBManagerState 让 UI 反馈「蓝牙未开 / 未授权」
    private(set) var managerState: CBManagerState = .unknown

    /// 内部 map：UUID → DiscoveredBleDevice，便于 O(1) 更新 RSSI
    private var devMap: [UUID: DiscoveredBleDevice] = [:]

    /// CBCentralManager 在 main queue 上回调，跟 @MainActor 协作最稳
    private lazy var central: CBCentralManager = CBCentralManager(delegate: self, queue: .main)

    private override init() { super.init() }

    /// 启动扫描；如果 state 还没到 poweredOn 会先等
    func start() {
        guard !scanning else { return }
        switch central.state {
        case .poweredOn:
            beginScan()
        case .poweredOff:
            lastError = .bluetoothOff
        case .unauthorized:
            lastError = .unauthorized
        case .unsupported:
            lastError = .unsupported
        case .resetting, .unknown:
            // 等下次 centralManagerDidUpdateState 自动尝试启动
            scanning = true        // 让 UI 立刻进入「扫描中」UX
            lastError = nil
        @unknown default:
            lastError = .unknown
        }
    }

    func stop() {
        guard central.state == .poweredOn else { scanning = false; return }
        central.stopScan()
        scanning = false
    }

    private func beginScan() {
        lastError = nil
        devMap.removeAll()
        devices = []
        // 不指定 serviceUUIDs 才能扫到全部 BLE 广播（Shearwater / Suunto 等）
        // 注意：iOS 后台模式不能不指定 serviceUUIDs；本 app v1 只前台扫描
        central.scanForPeripherals(withServices: nil, options: [
            CBCentralManagerScanOptionAllowDuplicatesKey: true,   // 让 RSSI 实时更新
        ])
        scanning = true
    }
}

// MARK: - CBCentralManagerDelegate

extension BleScanner: CBCentralManagerDelegate {
    nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Task { @MainActor in
            self.managerState = central.state
            switch central.state {
            case .poweredOn:
                if self.scanning && !central.isScanning {
                    self.beginScan()
                }
            case .poweredOff:
                self.scanning = false
                self.lastError = .bluetoothOff
            case .unauthorized:
                self.scanning = false
                self.lastError = .unauthorized
            case .unsupported:
                self.scanning = false
                self.lastError = .unsupported
            default: break
            }
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager,
                                    didDiscover peripheral: CBPeripheral,
                                    advertisementData: [String : Any],
                                    rssi RSSI: NSNumber) {
        // 在 nonisolated 里只能传值给 actor —— peripheral 是 Sendable-ish 句柄，但
        // advertisementData 的解析在主 actor 内完成
        let name = (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? peripheral.name
        let serviceUUIDs = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID]) ?? []
        let rssi = RSSI.intValue
        let id = peripheral.identifier
        Task { @MainActor in
            self.ingest(id: id, name: name, serviceUUIDs: serviceUUIDs, rssi: rssi)
        }
    }

    @MainActor
    private func ingest(id: UUID, name: String?, serviceUUIDs: [CBUUID], rssi: Int) {
        let match = VendorDetector.detect(advertisedName: name, serviceUuids: serviceUUIDs)
        let now = Date()
        let prev = devMap[id]
        let device = DiscoveredBleDevice(
            id: id,
            name: name,
            rssi: rssi,
            serviceUuids: serviceUUIDs,
            vendorMatch: match,
            firstSeenAt: prev?.firstSeenAt ?? now,
            lastSeenAt: now,
        )
        devMap[id] = device
        // 同 Android 里的 LinkedHashMap.values：保留插入顺序
        devices = Array(devMap.values)
    }
}

// MARK: - Models

struct DiscoveredBleDevice: Identifiable, Equatable {
    let id: UUID
    let name: String?
    let rssi: Int
    let serviceUuids: [CBUUID]
    let vendorMatch: VendorMatch
    let firstSeenAt: Date
    let lastSeenAt: Date
}

enum BleScanError: Equatable {
    case bluetoothOff
    case unauthorized       // 用户拒绝蓝牙权限 / 系统拒绝
    case unsupported        // 设备不支持 BLE（一般 Mac mini 无蓝牙模块时）
    case unknown
}
