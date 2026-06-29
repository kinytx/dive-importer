//
//  VendorDetector.swift
//
//  潜水电脑表 vendor / product 识别。
//
//  规则来源：打包进 app bundle 的 device-rules.json。Android / Apple / Windows
//  三端应使用 dive-import-shared 中的同一份 JSON，避免 vendor 表散落在各平台代码里。
//

import Foundation
import CoreBluetooth

// MARK: - Rule file

private struct DeviceRuleSet: Decodable {
    let schemaVersion: Int
    let serviceUuidHints: [ServiceUuidHintRule]
    let deviceNamePatterns: [DeviceNamePatternRule]
}

private struct ServiceUuidHintRule: Decodable {
    let vendor: String
    let uuids: [String]
    let weak: Bool?
}

private struct DeviceNamePatternRule: Decodable {
    let pattern: String
    let vendor: String
    let product: String
    let ambiguous: Bool?
    let weak: Bool?
    let hint: String?
}

private struct ServiceHint {
    let vendor: String
    let weak: Bool
}

private struct DeviceNamePattern {
    let regex: NSRegularExpression
    let vendor: String
    let product: String
    let ambiguous: Bool
    /// weak = 只识别到品牌/系列，不能直接走读取协议（如 Garmin Descent 需进 Garmin sidecar）
    let weak: Bool
    let hint: String?

    init(rule: DeviceNamePatternRule) throws {
        self.regex = try NSRegularExpression(pattern: rule.pattern, options: [.caseInsensitive])
        self.vendor = rule.vendor
        self.product = rule.product
        self.ambiguous = rule.ambiguous ?? false
        self.weak = rule.weak ?? false
        self.hint = rule.hint
    }

    func matches(_ name: String) -> Bool {
        let range = NSRange(name.startIndex..<name.endIndex, in: name)
        return regex.firstMatch(in: name, options: [], range: range) != nil
    }
}

private struct DeviceRules {
    let serviceToHint: [CBUUID: ServiceHint]
    let deviceNamePatterns: [DeviceNamePattern]

    static let shared: DeviceRules = load()

    private static func load() -> DeviceRules {
        guard let url = Bundle.main.url(forResource: "device-rules", withExtension: "json") else {
            assertionFailure("Missing device-rules.json in app bundle")
            return DeviceRules(serviceToHint: [:], deviceNamePatterns: [])
        }

        do {
            let data = try Data(contentsOf: url)
            let raw = try JSONDecoder().decode(DeviceRuleSet.self, from: data)

            var serviceToHint: [CBUUID: ServiceHint] = [:]
            for hint in raw.serviceUuidHints {
                for uuid in hint.uuids {
                    serviceToHint[CBUUID(string: uuid)] = ServiceHint(
                        vendor: hint.vendor,
                        weak: hint.weak ?? false
                    )
                }
            }

            let patterns = raw.deviceNamePatterns.compactMap { try? DeviceNamePattern(rule: $0) }
            return DeviceRules(serviceToHint: serviceToHint, deviceNamePatterns: patterns)
        } catch {
            assertionFailure("Invalid device-rules.json: \(error)")
            return DeviceRules(serviceToHint: [:], deviceNamePatterns: [])
        }
    }
}

// MARK: - Public model

enum VendorMatch: Equatable {
    case unknown
    case hit(
        vendor: String,
        product: String,
        ambiguous: Bool,
        weak: Bool,
        hint: String?,
        /// 'service-uuid' / 'device-name' / 'service-uuid+name'：debug 用
        source: String
    )
}

enum VendorDetector {
    /// 综合 service UUIDs（来自 BLE advertisement）+ 广播 name 来识别。
    /// service UUID 优先 —— 如果广播包含已知 service，直接命中 vendor，不依赖 name。
    static func detect(advertisedName: String?, serviceUuids: [CBUUID]) -> VendorMatch {
        let rules = DeviceRules.shared

        for uuid in serviceUuids {
            if let serviceHint = rules.serviceToHint[uuid] {
                if let name = advertisedName, !name.isEmpty,
                   case let .hit(v, p, amb, weak, hint, _) = detect(name: name, rules: rules),
                   v.compare(serviceHint.vendor, options: .caseInsensitive) == .orderedSame {
                    return .hit(vendor: v, product: p, ambiguous: amb, weak: weak,
                                hint: hint, source: "service-uuid+name")
                }
                return .hit(
                    vendor: serviceHint.vendor,
                    product: "(未确定型号)",
                    ambiguous: true,
                    weak: serviceHint.weak,
                    hint: nil,
                    source: "service-uuid"
                )
            }
        }

        if let name = advertisedName, !name.isEmpty {
            return detect(name: name, rules: rules)
        }
        return .unknown
    }

    private static func detect(name: String, rules: DeviceRules) -> VendorMatch {
        for pattern in rules.deviceNamePatterns {
            if pattern.matches(name) {
                return .hit(
                    vendor: pattern.vendor,
                    product: pattern.product,
                    ambiguous: pattern.ambiguous,
                    weak: pattern.weak,
                    hint: pattern.hint,
                    source: "device-name"
                )
            }
        }
        return .unknown
    }
}

extension VendorMatch {
    var isHit: Bool {
        if case .hit = self { true } else { false }
    }
}
