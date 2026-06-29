//
//  BindApi.swift
//
//  账号绑定接口（POST /api/ble-probe/bind-codes/consume）。
//
//  服务端 `BleProbeBindController.Consume`：
//    - 接受 6 位数字 Code（10 分钟 TTL）
//    - 接受 deviceLabel / probeVersion（可选）
//    - 成功返回 ApiKey + Prefix + ExpiresAt（**只返回这一次，必须立刻持久化**）
//
//  用裸 URLSession + Codable（不引第三方），跟 Swift Concurrency async/await 配合最自然。
//

import Foundation

// MARK: - Wire types

struct BindRequest: Encodable {
    let code: String
    let deviceLabel: String?
    let probeVersion: String?
}

struct BindResponse: Decodable {
    let bound: Bool
    let apiKey: String
    let prefix: String
    let expiresAt: String
}

private struct BindErrorBody: Decodable {
    let error: String?
}

// MARK: - Error model

enum BindError: Error, LocalizedError {
    /// 6 位码不存在 / 过期 / 已被消费 —— 用户级错误
    case invalidOrExpired
    /// 网络问题 —— 可重试
    case network(underlying: Error)
    /// 后端 5xx / 其它
    case server(status: Int, detail: String)
    /// 客户端层校验失败（长度等）
    case clientPrecondition(String)

    var errorDescription: String? {
        switch self {
        case .invalidOrExpired:               return "绑定码无效或已过期，请在小程序重新生成"
        case .network(let e):                 return "网络异常：\(e.localizedDescription)"
        case .server(let status, let detail): return "服务器异常（\(status)）：\(detail)"
        case .clientPrecondition(let msg):    return msg
        }
    }
}

// MARK: - Client

/// 绑定接口的 actor —— 用 actor 隔离 URLSession 调用，避免主线程阻塞。
actor BindApi {
    static let shared = BindApi(session: .shared)

    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    init(session: URLSession) {
        self.session = session
        let dec = JSONDecoder()
        dec.keyDecodingStrategy = .useDefaultKeys
        self.decoder = dec
        let enc = JSONEncoder()
        enc.keyEncodingStrategy = .useDefaultKeys
        self.encoder = enc
    }

    /// 用 6 位码换 ApiKey。
    /// - Throws: [BindError] —— ViewModel 负责翻译成用户文案
    func consumeBindCode(
        code: String,
        deviceLabel: String? = nil,
        probeVersion: String? = nil,
    ) async throws -> BindResponse {
        let normalized = code.filter(\.isASCII).filter(\.isNumber).prefix(6)
        guard normalized.count == 6 else {
            throw BindError.clientPrecondition("请输入完整的 6 位数字码")
        }

        let url = DivePlanEndpoints.baseURL.appendingPathComponent(DivePlanEndpoints.consumeBindCode)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 15
        let body = BindRequest(
            code: String(normalized),
            deviceLabel: deviceLabel,
            probeVersion: probeVersion,
        )
        request.httpBody = try encoder.encode(body)

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw BindError.network(underlying: error)
        }

        guard let http = response as? HTTPURLResponse else {
            throw BindError.server(status: 0, detail: "non-HTTP response")
        }

        guard (200...299).contains(http.statusCode) else {
            // 后端 invalid_or_expired_ble_probe_bind_code 返回 400
            let err = try? decoder.decode(BindErrorBody.self, from: data)
            if err?.error?.contains("invalid_or_expired") == true {
                throw BindError.invalidOrExpired
            }
            let detail = err?.error ?? String(data: data, encoding: .utf8)?.prefix(200).description ?? ""
            throw BindError.server(status: http.statusCode, detail: detail)
        }

        do {
            return try decoder.decode(BindResponse.self, from: data)
        } catch {
            throw BindError.server(status: http.statusCode, detail: "decode failed")
        }
    }
}
