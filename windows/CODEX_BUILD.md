# importer Windows — Codex 构建指南

## 环境要求

| 工具 | 版本 | 获取 |
|------|------|------|
| .NET SDK | 9.0+ | https://dotnet.microsoft.com/download |

验证：
```powershell
dotnet --version   # 应显示 9.x.x
```

---

## 构建命令

### Debug（快速验证）
```powershell
cd S:\GMP\dive-importers\windows
.\build_importer_windows.ps1 -Debug
```

### Release（发布用）
```powershell
cd S:\GMP\dive-importers\windows
.\build_importer_windows.ps1
```

### 自包含（用户无需安装 .NET Runtime）
```powershell
.\build_importer_windows.ps1 -SelfContained
```

产物位置：
```
dive-importers\windows\artifacts\win-x64\DivePlanImporter.Windows.exe
```

---

## Codex 执行顺序

```bash
# 1. 构建
cd S:\GMP\dive-importers\windows
powershell -ExecutionPolicy Bypass -File .\build_importer_windows.ps1

# 2. 验证产物
dir artifacts\win-x64\DivePlanImporter.Windows.exe
```

---

## 项目结构

```
dive-importers/windows/
├── DivePlanImporter.Windows/
│   ├── DivePlanImporter.Windows.csproj  ← net9.0-windows10.0.19041.0, WPF
│   ├── App.xaml / App.xaml.cs           ← 入口
│   ├── MainWindow.xaml / .cs            ← 主窗口 UI
│   ├── BleAdvertisementScanner.cs       ← WinRT BLE 扫描
│   ├── DeviceRuleDetector.cs            ← 设备识别（port of JS 规则引擎）
│   ├── Models.cs                        ← VendorMatch / DiscoveredDevice
│   └── Rules/                           ← device-rules.json（Link 到 shared/）
├── artifacts/win-x64/                   ← 构建产物
└── build_importer_windows.ps1           ← 构建脚本
```

---

## 常见问题

### `dotnet publish` 失败：找不到 Windows SDK
项目目标 `net9.0-windows10.0.19041.0`，需要 Windows 10 SDK。确认在 Windows 机器上构建，
或安装 `Microsoft.Windows.SDK.BuildTools`。

### device-rules.json 未出现在产物目录
检查 `.csproj` 中的 Link 路径：
```xml
<Content Include="..\..\shared\device-rules.json"
         Link="Rules\device-rules.json"
         CopyToOutputDirectory="PreserveNewest" />
```
确保 `S:\GMP\dive-importers\shared\device-rules.json` 存在。
