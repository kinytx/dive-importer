# DivePlan 潜水日志导入 · Android Build 指南

把当前进度（P0 骨架 + P1 账号绑定 + P2 BLE 扫描）编成 debug APK 装到安卓机上测试。

## 前置环境

| 工具 | 最低版本 | 装法 |
|---|---|---|
| **Android Studio** | Hedgehog 2023.1+ / Iguana / Jellyfish / Koala | <https://developer.android.com/studio> |
| Java 17 JDK | 17+ | Android Studio 自带，不需要单独装 |
| Android SDK | API 36（compileSdk）/ API 26（minSdk） | Android Studio 首次启动会引导安装 |
| Kotlin | 2.0.21 | 由 `gradle/libs.versions.toml` 锁定 |

## 路径 A：用 Android Studio 一键 build（推荐）

```text
1. Android Studio → File → Open → 选 S:\GMP\dive-importers\android
2. 等右下角 "Gradle Sync" 跑完（首次拉依赖 ~3-5 分钟，~200MB）
   - 如果 sync 失败，看「常见问题」一节
3. 顶部 toolbar 选 device：插一根安卓手机（开发者选项 + USB 调试）或 emulator
4. ⌘R / Ctrl+R 或点绿色 ▶ Run
   - Android Studio 自动 build + 装 + 启动 app
5. 手机上看到「绑定账号」界面 → 成功
```

## 路径 B：命令行 build APK（不开 Android Studio）

```powershell
cd S:\GMP\dive-importers\android

# 1. 第一次需要让 Android Studio 给你生成 gradle wrapper（4 个文件）：
#    - gradlew
#    - gradlew.bat
#    - gradle/wrapper/gradle-wrapper.properties
#    - gradle/wrapper/gradle-wrapper.jar
#    最简单：用 Android Studio 打开项目一次，sync 完后这些文件就自动生成在仓库根
#    （不进 git，但 build 时需要）

# 2. 配 ANDROID_HOME（如果还没配）
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

# 3. 编 debug APK
.\gradlew.bat assembleDebug

# 4. APK 在：
#    app\build\outputs\apk\debug\app-debug.apk
```

把 `app-debug.apk` 用 USB 或 adb push 到手机：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 真机调试第一次

| 步骤 | 怎么做 |
|---|---|
| 1. 手机开发者选项 | 设置 → 关于手机 → 连点"版本号"7 次 |
| 2. 开 USB 调试 | 设置 → 开发者选项 → USB 调试 |
| 3. 插 USB 线 | 手机弹窗「允许此电脑调试」→ 允许 |
| 4. `adb devices` | 应该列出你的手机 serial |
| 5. 装 APK | Android Studio Run 或 `adb install -r` |

## 测试当前进度

装好后启动应用：

**1. 首屏：账号绑定 BindScreen**

- 顶部 Picker：`输入码 / 扫码`
- **测「输入码」**：
  - 在小程序里 `pages/dc-import/dc-import.wxml` 进入 → 点「我的潜水电脑」生成绑定码（6 位数字）
  - Android 上输入 → 「确认绑定」
  - 成功后短暂看到「✅ 已绑定 · 凭证前缀: dpk_xxx」→ 跳到下一屏
- **测「扫码」**：
  - 同上小程序生成绑定 URL `diveplan://ble-probe/bind?code=123456`，做成二维码（任意 QR 工具）
  - Android 切到「扫码」Tab → 授权相机 → 对着 QR → 自动识别并绑定
- **测「deep link」**：
  - 手机浏览器或文件管理器打开 URL `diveplan://ble-probe/bind?code=123456`
  - Android 弹「DivePlan 潜水日志导入打开」→ app 直接跳到「绑定中」

**2. 绑定后：BLE ScanScreen**

- 自动开始扫描 BLE 广播
- 顶部 `🔍 扫描中… · 已发现 N 台设备`
- 设备列表：
  - 已识别 vendor 的设备显示主色圆形 badge（S=Shearwater / G=Garmin（琥珀色） / U=Suunto 等）
  - 未识别 `?` 灰圈
  - 右侧 RSSI `●●●○` 强度条 + `-65 dBm`
- 点设备 → 当前是 noop（P3 才接入抓 dump）

**3. 在你身边没潜水电脑时怎么测**？

- 用任何 BLE 设备（小米手环、AirPods、电视 Smart Remote）测扫描渲染
- 用 Garmin 手表（如果你有 Descent Mk3）测「G」橙色 badge + weak hint「Garmin Descent Mk 系列走 Garmin Sidecar 通道」

## 常见问题

### Gradle sync 卡在 "Resolving dependencies"

国内网络访问 Google Maven 慢。两个办法：

```kotlin
// settings.gradle.kts 里 dependencyResolutionManagement.repositories 加阿里镜像：
maven { url = uri("https://maven.aliyun.com/repository/google") }
maven { url = uri("https://maven.aliyun.com/repository/public") }
```

或者打开梯子让 Android Studio 走代理。

### `gradlew.bat` 不存在

正常 —— 这是 Android Studio Gradle Sync 第一次生成的产物。先用路径 A 打开一次，sync 完后再切到命令行。

### APK 装上后秒退 / 报 "ActivityNotFoundException"

通常是 `MainActivity` 因为依赖注入失败崩了。看 `adb logcat | grep DivePlanImporter`，常见原因：
- `Hilt` 注解处理失败 → 重新 `Clean Project` + `Rebuild`
- `EncryptedSharedPreferences` 在某些定制 ROM 上需要 `androidx.security:security-crypto-ktx` —— 我们已经引了

### 真机 BLE 扫描结果一直为空

- 检查权限：设置 → 应用 → DivePlan 潜水日志导入 → 权限 → 蓝牙 / 附近设备 设为「允许」
- API 31+ 不再需要位置权限；API ≤30 需要开 GPS（系统层强制）
- 真机 + USB 调试模式下蓝牙偶尔会被禁用，重启手机就行

### 想看 BLE 扫描日志

```powershell
adb logcat | Select-String -Pattern "BluetoothLeScanner|BleScanner|VendorDetector"
```

## Release APK（之后上线时用）

```powershell
.\gradlew.bat assembleRelease
# 输出: app\build\outputs\apk\release\app-release-unsigned.apk
# 需要先配签名 keystore，本文档不展开（CI/CD 阶段再做）
```
