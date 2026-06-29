//
//  DivePlanEndpoints.swift
//
//  ECS 后端 endpoint —— 全部相对 [baseURL]。
//  跟 gas-dive-server `BleProbeBindController` / `BleProbeCapturesController` 对齐。
//  P3/P4 加 `/api/me/dives/parse` 等。
//

import Foundation

enum DivePlanEndpoints {
    static let baseURL = URL(string: "https://api.diveplan.cn")!

    /// 6 位码换 ApiKey（匿名 POST，不带 Authorization）
    static let consumeBindCode = "/api/ble-probe/bind-codes/consume"

    /// 上传嗅探样本（要带 X-Api-Key）
    static let probeCaptures = "/api/me/ble-probe-captures"

    /// P3+：dump 文件解析
    static let divesParse = "/api/me/dives/parse"

    /// 不需要 ApiKey 的 endpoint 前缀白名单 —— [ApiKeyInjector] 用
    static let anonymousPaths: [String] = [
        consumeBindCode,
    ]
}
