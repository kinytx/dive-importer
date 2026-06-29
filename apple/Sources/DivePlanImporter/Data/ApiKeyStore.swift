//
//  ApiKeyStore.swift
//
//  把 ApiKey 安全存到 Keychain（Security framework）。
//
//  选 Keychain 而非 UserDefaults 的原因：
//    - Keychain 由 Secure Enclave 加密保护，root 设备 / iCloud Backup 都看不到明文
//    - kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly：开机首次解锁后才能读，
//      避免后台早期阶段被读；ThisDeviceOnly 让 iCloud Keychain 不同步到别的设备
//    - kSecAttrAccessGroup 配合 entitlements 里的 keychain-access-groups
//      让同一 Team 下 iOS / macOS 两个 app 跨平台共享 ApiKey（虽然现在我们就一个 app）
//
//  暴露 @Observable @MainActor isBound 状态供 SwiftUI 观察 —— 用来切 BindScreen ↔ Home。
//
//  线程安全：所有 Keychain 操作都在 actor 内或 @MainActor 上，避免并发访问。
//

import Foundation
import Security

@Observable
@MainActor
final class ApiKeyStore {
    static let shared = ApiKeyStore()

    /// 当前 ApiKey；空 = 未绑定。SwiftUI 直接观察这个属性。
    private(set) var apiKey: String?
    private(set) var prefix: String?
    private(set) var expiresAt: String?

    var isBound: Bool { !(apiKey?.isEmpty ?? true) }

    private init() {
        // 启动时同步从 Keychain 读一次，让 SwiftUI 首屏不闪
        let snap = Self.readFromKeychainSync()
        self.apiKey = snap.apiKey
        self.prefix = snap.prefix
        self.expiresAt = snap.expiresAt
    }

    func save(apiKey: String, prefix: String, expiresAt: String?) {
        self.apiKey = apiKey
        self.prefix = prefix
        self.expiresAt = expiresAt
        Self.writeKeychain(apiKey: apiKey, prefix: prefix, expiresAt: expiresAt)
    }

    /// 用户主动解绑 / 切换账号时调；只清本地，**不** revoke 服务端 ApiKey
    /// （在小程序 / web 端用户可主动删；后端也支持过期）
    func clear() {
        apiKey = nil
        prefix = nil
        expiresAt = nil
        Self.deleteKeychain()
    }

    // MARK: - Keychain primitive

    private static let service = "cn.diveplan.importer.apikey"
    private static let accountApiKey   = "api_key"
    private static let accountPrefix    = "api_key_prefix"
    private static let accountExpiresAt = "api_key_expires_at"

    private static func writeKeychain(apiKey: String, prefix: String, expiresAt: String?) {
        write(account: accountApiKey,    value: apiKey)
        write(account: accountPrefix,    value: prefix)
        write(account: accountExpiresAt, value: expiresAt ?? "")
    }

    private static func deleteKeychain() {
        delete(account: accountApiKey)
        delete(account: accountPrefix)
        delete(account: accountExpiresAt)
    }

    private static func readFromKeychainSync() -> (apiKey: String?, prefix: String?, expiresAt: String?) {
        (
            apiKey:    read(account: accountApiKey),
            prefix:    read(account: accountPrefix),
            expiresAt: read(account: accountExpiresAt),
        )
    }

    private static func write(account: String, value: String) {
        // upsert：先 SecItemUpdate，找不到再 SecItemAdd
        let data = Data(value.utf8)
        let baseQuery: [String: Any] = [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let updateAttrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let updateStatus = SecItemUpdate(baseQuery as CFDictionary, updateAttrs as CFDictionary)
        if updateStatus == errSecSuccess { return }

        var addQuery = baseQuery.merging(updateAttrs) { _, new in new }
        addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        _ = SecItemAdd(addQuery as CFDictionary, nil)
    }

    private static func read(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String:  true,
            kSecMatchLimit as String:  kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess,
              let data = item as? Data,
              let s = String(data: data, encoding: .utf8),
              !s.isEmpty
        else { return nil }
        return s
    }

    private static func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        _ = SecItemDelete(query as CFDictionary)
    }
}
