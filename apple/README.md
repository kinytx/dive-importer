# DivePlan 潜水日志导入助手 · iOS + macOS

一个 SwiftUI Multiplatform App，把潜水电脑表上的潜水日志通过蓝牙读出来，
自动上传到 DivePlan 账号，跟小程序 / web 端看到的是同一份数据。

跟 `../android` 平行 —— 同一份后端、同一套账号绑定流程、同一个产品视觉。

## 当前进度

- [x] **P0 项目骨架** —— SwiftUI Multiplatform App，iOS 17+ / macOS 14+，主题对齐小程序 dark/light，Info.plist + entitlements 就位
- [ ] P1 账号绑定（二维码 + 6 位码 → ApiKey 存 Keychain）
- [ ] P2 BLE 扫描 + vendor 识别（CoreBluetooth）
- [ ] P3 libdc 路径 dump 抓取
- [ ] P4 离线上传队列（URLSession background tasks）
- [ ] P5 Garmin 路径 WSS（URLSessionWebSocketTask）
- [ ] P6 完成态 / 历史 / 设置
- [ ] 真机校验

## 在 Mac 上第一次准备

```bash
# 1. 装 XcodeGen（用 Homebrew）
brew install xcodegen

# 2. 生成 .xcodeproj
cd S:\GMP\dive-importers\apple
xcodegen generate

# 3. 用 Xcode 打开
open DivePlanImporter.xcodeproj

# 4. 在 Xcode 顶部 scheme 旁选 iPhone / Mac
#    选 iPhone 15 Simulator → Run → 看到「✅ 项目骨架已就位（P0）」
#    选 My Mac (Designed for iPad)/My Mac → Run → 看到 macOS Native 窗口
```

后续改动只要改 `project.yml` 或 `Sources/` 任意文件，再跑一次 `xcodegen generate` 即可。
**`.xcodeproj` 是生成产物，不进 git**（已加 `.gitignore`）。

## 项目结构

```
dive-importers/apple/
├── project.yml                      # XcodeGen 配置（项目结构 / 配置 / 平台 / 依赖）
├── Sources/
│   └── DivePlanImporter/
│       ├── App/                     # @main 入口
│       ├── UI/
│       │   ├── Theme/               # DivePlanColor / DivePlanFont
│       │   ├── Components/          # DivePlanCard 等可复用组件
│       │   └── Screens/             # 各页面
│       ├── Data/                    # P1+: ApiKey 仓 / dump 队列
│       └── BLE/                     # P2+: BLE 扫描 / GATT 协议
├── Resources/
│   ├── iOS/                         # iOS-specific Info.plist + entitlements
│   ├── macOS/                       # macOS-specific Info.plist + entitlements
│   └── Shared/Assets.xcassets/      # 图标、颜色
└── docs/
    └── xcode-setup.md               # 详细 Xcode 配置指南
```

## 跨平台策略

**单 target Multiplatform**（Xcode 14+ 推荐写法）：
- 一份 SwiftUI 代码同时编 iOS + macOS
- 用 `#if os(iOS)` / `#if os(macOS)` 处理平台差异（如 `.windowResizability`）
- BLE 用 `CoreBluetooth`（iOS / macOS 同一份 API）
- 扫码用 `AVFoundation + Vision`（iOS / Apple Silicon Mac 同一份）

## 鉴权（跟 Android importer 一致）

走 **API Key**（非 Bearer JWT）：

1. 用户在小程序 / web 端 `POST /api/me/ble-probe-bind-codes` → 6 位码 + `diveplan://ble-probe/bind?code=xxxxxx` URL
2. iOS/Mac 端扫码（custom URL scheme 自动唤起）/ 输入码 → `POST /api/ble-probe/bind-codes/consume` → 返回 `ApiKey`
3. 所有后续请求带 `X-Api-Key: dpk_xxx` header
4. `ApiKey` 存 **Keychain**（iOS）/ Keychain Sharing group（Mac，可跨 iOS / macOS 互通）

## IP / 法务边界

- ❌ **严禁** import `GMP/Gadgetbridge/` 任何源码到本工程（AGPL-3.0）
- ✅ 复用 `gas-dive-plan/shared/utils/garmin-ble-wss-bridge.ts` 的协议消息格式（DivePlan 自研）
- ✅ 通过观察行为重写实现（参见 `garmin-sidecar/CLEAN_ROOM.md`）

## 相关仓库

- `../android` —— Android 平行版（Kotlin + Compose）
- `gas-dive-server` —— ECS 后端，含 `BleProbeBindController` / `BleProbeCapturesController`
- `garmin-sidecar` —— Garmin 协议 C 核心 + WSS bridge（DivePlan clean-room 实现）
