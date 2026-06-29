# DivePlan importers

潜水日志导入助手的统一工作区。

## 目录

- `android/`：Android 原生 importer，Kotlin + Jetpack Compose。
- `apple/`：iOS + macOS importer，SwiftUI Multiplatform + CoreBluetooth。
- `windows/`：Windows importer，WPF + Windows BLE API。
- `shared/`：三端共享规则，目前包含 `device-rules.json`。

## 共享规则

设备识别规则统一维护在：

```text
S:\GMP\dive-importers\shared\device-rules.json
```

Apple 和 Windows 已经直接引用这份 JSON。Android 当前仍是 hardcode
`VendorDetector.kt`，后续等 Gradle 环境可构建时再切到 assets 读取，避免在无法验证的状态下牵动初始化路径。

## 构建

Windows：

```powershell
cd S:\GMP\dive-importers\windows\DivePlanImporter.Windows
dotnet build
dotnet publish -c Release -r win-x64 --self-contained false -o ..\artifacts\win-x64
```

Apple：

```bash
cd /path/to/dive-importers/apple
xcodegen generate
open DivePlanImporter.xcodeproj
```

Android：

```powershell
cd S:\GMP\dive-importers\android
.\gradlew.bat assembleDebug
```
