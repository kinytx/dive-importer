# dive-importers/android/build_importer_android.ps1
# DivePlan 潜水日志导入 · Android APK 构建脚本
#
# 用法（在 dive-importers/android/ 执行，或由总控脚本调用）：
#   .\build_importer_android.ps1              # Release APK（有 key.properties 时签名）
#   .\build_importer_android.ps1 -Debug       # Debug APK
#
# 产物：
#   app\build\outputs\apk\release\app-release.apk
#   app\build\outputs\apk\debug\app-debug.apk  （Debug 模式）
#
# 前置条件：
#   - gradlew.bat（由 Android Studio 首次 Sync 生成，见 BUILD.md 路径 A）
#   - Java 17+ in PATH（Android Studio 自带）
#   - ANDROID_HOME 指向 Android SDK

param(
    [switch]$Debug
)

$ErrorActionPreference = 'Stop'
$ScriptDir = $PSScriptRoot

Write-Host '=== DivePlan 潜水日志导入 · Android Build ===' -ForegroundColor Cyan

# ── 检查 gradlew.bat ──────────────────────────────────────────────────────────
$Gradlew = Join-Path $ScriptDir 'gradlew.bat'
if (-not (Test-Path $Gradlew)) {
    Write-Error @"
gradlew.bat 不存在。
请先用 Android Studio 打开 $ScriptDir 并完成 Gradle Sync（见 BUILD.md 路径 A），
Sync 完成后 gradlew.bat 会自动生成在项目根目录。
"@
    exit 1
}

# ── 检查 ANDROID_HOME ─────────────────────────────────────────────────────────
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
    Write-Host "  ANDROID_HOME 未设置，默认使用 $env:ANDROID_HOME"
}

# ── 使用 Android Studio 自带 JDK，避免系统 Java 过新导致 Gradle/Kotlin 崩溃 ───────
$StudioJbr = 'C:\Program Files\Android\Android Studio\jbr'
if (Test-Path (Join-Path $StudioJbr 'bin\java.exe')) {
    $env:JAVA_HOME = $StudioJbr
    $env:Path = "$StudioJbr\bin;$env:Path"
    Write-Host "  JAVA_HOME 使用 Android Studio JBR: $env:JAVA_HOME"
}

# ── 构建 ──────────────────────────────────────────────────────────────────────
$Task   = if ($Debug) { 'assembleDebug' }   else { 'assembleRelease' }
$Config = if ($Debug) { 'debug' }           else { 'release' }
$ApkOut = if ($Debug) {
    Join-Path $ScriptDir "app\build\outputs\apk\debug\app-debug.apk"
} else {
    Join-Path $ScriptDir "app\build\outputs\apk\release\app-release.apk"
}

Write-Host "[1/2] $Gradlew $Task ..." -ForegroundColor Yellow
Push-Location $ScriptDir
try {
    & $Gradlew $Task
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Gradle $Task 失败（exitcode=$LASTEXITCODE）"
        exit 1
    }
} finally {
    Pop-Location
}

# ── 验证产物 ──────────────────────────────────────────────────────────────────
Write-Host "`n[2/2] 验证产物 ..." -ForegroundColor Yellow
if (Test-Path $ApkOut) {
    $SizeMB = (Get-Item $ApkOut).Length / 1MB
    Write-Host ("  ✓ APK : $ApkOut  ({0:F1} MB)" -f $SizeMB) -ForegroundColor Green
} else {
    Write-Warning "APK 未找到：$ApkOut"
}

Write-Host "`n✓ 完成！配置: $Config" -ForegroundColor Cyan
