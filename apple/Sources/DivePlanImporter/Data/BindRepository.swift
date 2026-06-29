//
//  BindRepository.swift
//
//  绑定流程总入口 —— ViewModel 只跟这个类对话。
//
//    1. consume 把 6 位码送给服务端
//    2. 服务端返回 ApiKey + Prefix + ExpiresAt
//    3. 立刻塞进 [ApiKeyStore] 持久化（**只有这一次能拿到 ApiKey 明文**）
//    4. 抛错由 ViewModel 翻译成 BindError UI 态
//
//  解绑（[unbind]）只清本地 Keychain。
//

import Foundation
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

@MainActor
final class BindRepository {
    static let shared = BindRepository()

    private let api: BindApi
    private let apiKeyStore: ApiKeyStore

    init(api: BindApi = .shared, apiKeyStore: ApiKeyStore = .shared) {
        self.api = api
        self.apiKeyStore = apiKeyStore
    }

    var isBound: Bool { apiKeyStore.isBound }

    /// 用 6 位绑定码换 ApiKey。成功后立即存 Keychain。
    /// - Throws: [BindError]
    @discardableResult
    func consumeBindCode(_ code: String) async throws -> BindResponse {
        let resp = try await api.consumeBindCode(
            code: code,
            deviceLabel: buildDeviceLabel(),
            probeVersion: Self.appVersion,
        )
        apiKeyStore.save(
            apiKey: resp.apiKey,
            prefix: resp.prefix,
            expiresAt: resp.expiresAt,
        )
        return resp
    }

    func unbind() {
        apiKeyStore.clear()
    }

    // MARK: - Device labeling

    /// 设备标签：「iPhone 15 Pro · iOS 17.5」让用户在小程序 / web 端看「我的绑定设备」时认得出
    private func buildDeviceLabel() -> String {
        #if canImport(UIKit)
        let device = UIDevice.current
        let model = device.model      // "iPhone" / "iPad"
        let osVer = device.systemVersion
        // 设备 marketing name 在 iOS 没有公共 API；用机型 + OS 版本即可
        return "\(model) · iOS \(osVer)".prefix(64).description
        #elseif canImport(AppKit)
        let info = ProcessInfo.processInfo
        let osVer = info.operatingSystemVersionString  // "Version 14.5 (Build 23F79)"
        // 简化为 "macOS 14.5"
        let simple: String
        if let v = osVer.range(of: #"\d+\.\d+(\.\d+)?"#, options: .regularExpression) {
            simple = "macOS \(osVer[v])"
        } else {
            simple = "macOS"
        }
        let host = Host.current().localizedName ?? "Mac"
        return "\(host) · \(simple)".prefix(64).description
        #else
        return "Apple Device"
        #endif
    }

    private static var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
    }
}
