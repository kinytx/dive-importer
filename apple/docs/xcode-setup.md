# Xcode 配置详细指南

## 一次性环境

| 软件 | 版本 | 装法 |
|---|---|---|
| Xcode | 15.4+ | App Store / developer.apple.com |
| Command Line Tools | 跟 Xcode 同版本 | `xcode-select --install` |
| Homebrew | 最新 | <https://brew.sh> |
| XcodeGen | 任意 | `brew install xcodegen` |

## 生成 / 重新生成 `.xcodeproj`

任何对 `project.yml` 或 `Sources/` 结构变更后：

```bash
cd S:\GMP\dive-importers\apple
xcodegen generate
```

`.xcodeproj` 是生成产物 —— **不进 git**，只 commit `project.yml`。

## 第一次运行

1. 打开 Xcode：`open DivePlanImporter.xcodeproj`
2. 顶部 toolbar：
   - **Scheme** = DivePlanImporter
   - **Destination** = 你想跑的设备：
     - iPhone 15 Simulator —— 跑 iOS 模拟器（最常用）
     - My Mac —— 跑 macOS Native 窗口
     - 物理设备（需 Apple Developer 账号 + 真机插 USB）
3. 按 ⌘R 运行

## 设置 Code Signing（真机调试需要）

1. 在 Xcode 左侧导航选项目（顶部蓝色图标）
2. 中间面板选 **DivePlanImporter** target
3. **Signing & Capabilities** tab：
   - 勾 **Automatically manage signing**
   - **Team** 选你的 Apple Developer 账号
4. Bundle Identifier `cn.diveplan.importer` 必须 unique，如果冲突改成 `cn.diveplan.importer.<你名字>`
5. ⌘R 跑真机

## 改 BLE / Camera 提示文案

| 文案 | 位置 |
|---|---|
| iOS 蓝牙提示 | `Resources/iOS/Info.plist` → `NSBluetoothAlwaysUsageDescription` |
| iOS 相机提示 | `Resources/iOS/Info.plist` → `NSCameraUsageDescription` |
| macOS 蓝牙提示 | `Resources/macOS/Info.plist` → `NSBluetoothAlwaysUsageDescription` |
| macOS 相机提示 | `Resources/macOS/Info.plist` → `NSCameraUsageDescription` |

改完跑 `xcodegen generate` 重新生成 .xcodeproj。

## 常见问题

### `xcodegen: command not found`

```bash
brew install xcodegen
# 或
mint install yonaskolb/xcodegen   # 如果用 Mint
```

### Xcode 报「Could not find Info.plist」

`xcodegen` 不会自动建文件，只声明路径。如果 `Resources/iOS/Info.plist` 不存在，
检查这个仓库 clone 是否完整。重跑 `xcodegen generate`。

### 模拟器跑不起来 BLE

Simulator **没有蓝牙模拟**，必须用真机调试 BLE 相关代码。
iOS 模拟器只能验证 UI / 网络 / 扫码（用电脑摄像头）。

### macOS App Sandbox 报权限拒绝

`Resources/macOS/DivePlanImporter.entitlements` 已开 `bluetooth / camera / network.client`。
如果还是被拒，在 Xcode → target → Signing & Capabilities 里手动确认 capability 开了。

## 调试 deep link `diveplan://ble-probe/bind?code=123456`

iOS Simulator：

```bash
xcrun simctl openurl booted "diveplan://ble-probe/bind?code=123456"
```

macOS：

```bash
open "diveplan://ble-probe/bind?code=123456"
```
