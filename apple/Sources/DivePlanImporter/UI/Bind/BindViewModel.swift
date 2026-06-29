//
//  BindViewModel.swift
//
//  绑定流程的 UI 状态机（跟 Android `BindViewModel.kt` 一致）：
//
//    Idle ─(用户输入 6 位)──→ Submitting ─(成功)──→ Bound
//       ↑                                    ↓ (失败)
//       └────────── error 显示 ←──────────────┘  (用户重新输入会回到 Idle)
//
//  既驱动手动输入 Tab，也驱动二维码扫描 Tab：
//    - QR scanner 识别到形如 `diveplan://ble-probe/bind?code=123456` 或纯 6 位数字时
//      直接调 [submitCode]，UI 切到 Submitting
//

import Foundation
import Observation

@Observable
@MainActor
final class BindViewModel {
    enum Phase { case idle, submitting, bound }

    enum UIError: Equatable {
        case lengthMismatch
        case invalidCode
        case network(String)
        case server(status: Int, detail: String)

        var message: String {
            switch self {
            case .lengthMismatch:       return "请输入完整的 6 位数字码"
            case .invalidCode:          return "绑定码无效或已过期，请在小程序重新生成"
            case .network(let d):       return "网络异常，请检查连接后重试（\(d)）"
            case .server(let s, _):     return "服务器异常（\(s)），请稍后重试"
            }
        }
    }

    private(set) var code: String = ""
    private(set) var phase: Phase = .idle
    private(set) var error: UIError?
    /// Bound 态：ApiKey 前缀（让用户在小程序端识别"我刚绑的是这台设备"）
    private(set) var successPrefix: String?
    private(set) var successExpiresAt: String?

    private let repository: BindRepository

    init(repository: BindRepository = .shared) {
        self.repository = repository
    }

    /// 用户在 OTP 输入框里改动时调用；自动归一化（去非数字、限 6 位）+ 清错
    func onCodeChange(_ raw: String) {
        code = raw.filter(\.isASCII).filter(\.isNumber).prefix(6).description
        error = nil
    }

    /// 提交绑定码。可以传 codeOverride（扫码 / deep link 直接喂 6 位）
    func submit(codeOverride: String? = nil) {
        let final: String
        if let o = codeOverride {
            final = o.filter(\.isASCII).filter(\.isNumber).prefix(6).description
        } else {
            final = code
        }
        guard final.count == 6 else {
            error = .lengthMismatch
            return
        }
        guard phase != .submitting else { return }

        code = final
        phase = .submitting
        error = nil

        Task {
            do {
                let resp = try await repository.consumeBindCode(final)
                self.successPrefix = resp.prefix
                self.successExpiresAt = resp.expiresAt
                self.phase = .bound
                self.error = nil
            } catch let e as BindError {
                self.phase = .idle
                switch e {
                case .invalidOrExpired:
                    self.error = .invalidCode
                case .clientPrecondition:
                    self.error = .lengthMismatch
                case .network(let underlying):
                    self.error = .network(underlying.localizedDescription)
                case .server(let status, let detail):
                    self.error = .server(status: status, detail: detail)
                }
            } catch {
                self.phase = .idle
                self.error = .server(status: 0, detail: error.localizedDescription)
            }
        }
    }

    /// 用户在 Bound 态点「完成」/「去扫描设备」时调
    func acknowledgeBound() {
        phase = .idle
        code = ""
    }
}
