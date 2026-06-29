# importer Android — Codex 构建指南

## 环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 17 或 21 | 推荐 Android Studio 自带 JBR；不要使用 Java 25 |
| Android SDK | compileSdk 36 | ANDROID_HOME 需设置 |
| Gradle Wrapper | 8.13 | 由 gradlew.bat 自动下载 |

`ANDROID_HOME` 默认路径（未设置时脚本自动回落）：
```
%LOCALAPPDATA%\Android\Sdk
```

---

## 第一步：安装 gradle-wrapper.jar（一次性）

`gradle-wrapper.jar` 无法通过 FUSE 挂载写入，需在 Windows 本地执行：

```powershell
# 在 S:\GMP 目录下运行
cd S:\GMP
.\install_gradle_wrapper.ps1
```

该脚本会：
- 将 `gradle-wrapper.jar`（43 KB，base64 内嵌）解码写入 `dive-importers\android\gradle\wrapper\`
- 写入 `gradle-wrapper.properties`（指向 Gradle 8.13 官方 CDN）

运行后验证：
```powershell
Test-Path S:\GMP\dive-importers\android\gradle\wrapper\gradle-wrapper.jar
# 输出 True 则成功
```

---

## 第二步：构建 APK

### Debug APK（推荐先跑）
```powershell
cd S:\GMP\dive-importers\android
.\gradlew.bat assembleDebug
```

产物：
```
app\build\outputs\apk\debug\app-debug.apk
```

### Release APK（无签名，可安装测试）
```powershell
.\gradlew.bat assembleRelease
```

产物：
```
app\build\outputs\apk\release\app-release-unsigned.apk
```

### 通过总控脚本构建
```powershell
cd S:\GMP
.\build_release.ps1 -SkipMixer -SkipWindows
# 只跑 importer Android，跳过 mixer 和 Windows 产物
```

---

## 常见问题

### Gradle 下载慢 / 超时
首次运行 `gradlew.bat` 会下载 Gradle 8.13（约 130 MB）。如网络差，可预先：
```powershell
# 下载到本地后手动放入缓存
$dest = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin"
```
或在 `gradle-wrapper.properties` 改用镜像（腾讯/阿里云）：
```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.13-bin.zip
```

### `Configuration cache` 报错（Permission denied）
`gradle.properties` 已配置 `org.gradle.configuration-cache=true`，
若 Codex 沙盒写权限受限，临时禁用：
```powershell
.\gradlew.bat assembleDebug --no-configuration-cache
```

### `ANDROID_HOME not found`
```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
```

### Gradle 报 `IllegalArgumentException: 25.0.1`
当前系统 Java 版本太新，Kotlin/Gradle 无法解析。使用 Android Studio 自带 JBR：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

`build_importer_android.ps1` 已自动优先使用这个 JBR。

### Hilt kapt 报错
Hilt 需要 kapt annotation processor，build.gradle.kts 已配置。
若报 `kapt` 找不到，检查：
```
gradle\libs.versions.toml → kotlin-kapt = "..."
app\build.gradle.kts → id("kotlin-kapt")
```

---

## 项目结构速查

```
dive-importers/android/
├── gradle/wrapper/
│   ├── gradle-wrapper.jar          ← 第一步安装
│   └── gradle-wrapper.properties   ← Gradle 8.13
├── gradle/libs.versions.toml       ← 所有依赖版本
├── app/
│   ├── build.gradle.kts            ← minSdk=26, compileSdk=36
│   ├── proguard-rules.pro          ← Moshi/Tink/MLKit/Hilt 规则
│   └── src/main/
│       ├── AndroidManifest.xml     ← BLE + 相机权限 + deeplink
│       ├── assets/device-rules.json ← 35条设备识别规则
│       └── java/cn/diveplan/importer/   ← Kotlin 源码目录名沿用 java；Gradle 只编译这套
│           ├── DivePlanImporterApp.kt    ← @HiltAndroidApp
│           ├── MainActivity.kt           ← Compose 入口
│           ├── navigation/NavGraph.kt    ← BIND → SCAN 路由
│           ├── model/Models.kt           ← VendorMatch / DiscoveredDevice
│           ├── ble/
│           │   ├── DeviceRuleDetector.kt ← 35条规则匹配引擎
│           │   └── BleScanner.kt         ← BLE LE 扫描 StateFlow
│           ├── data/CredentialRepository.kt ← EncryptedSharedPreferences
│           ├── di/AppModule.kt           ← Hilt: Moshi + OkHttp
│           ├── ui/
│           │   ├── theme/Theme.kt        ← Ocean dark (#0A1628 + #00D4FF)
│           │   ├── bind/
│           │   │   ├── BindViewModel.kt  ← POST /v1/ble-probe/devices/bind
│           │   │   └── BindScreen.kt     ← 输入码 + QR 扫码 Tab
│           │   └── scan/
│           │       ├── ScanViewModel.kt  ← 权限管理 + toggleScan
│           │       └── ScanScreen.kt     ← 设备列表 + RSSI + Badge
│           └── ...
│
└── app/src/main/kotlin/                 ← 未完成实验骨架，当前不参与 Gradle 编译
├── gradlew.bat                     ← Windows 构建入口
├── gradlew                         ← Linux/Mac 构建入口
└── build_importer_android.ps1      ← 独立构建脚本（含签名检查）
```

---

## Codex 执行顺序（完整流程）

```bash
# Codex 在 Windows 环境中执行：

# 1. 安装 wrapper jar
powershell -ExecutionPolicy Bypass -File S:\GMP\install_gradle_wrapper.ps1

# 2. 构建 debug APK
cd S:\GMP\dive-importers\android
.\gradlew.bat assembleDebug --no-configuration-cache

# 3. 验证产物
dir app\build\outputs\apk\debug\app-debug.apk
```
