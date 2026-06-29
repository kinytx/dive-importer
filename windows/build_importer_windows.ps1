# dive-importers/windows/build_importer_windows.ps1
# DivePlan 潜水日志导入助手 · Windows 构建脚本
#
# 用法（在 dive-importers/windows/ 目录执行）：
#   .\build_importer_windows.ps1              # Release 发布（framework-dependent）
#   .\build_importer_windows.ps1 -SelfContained  # 自包含（无需用户安装 .NET 9）
#   .\build_importer_windows.ps1 -Debug       # Debug 构建
#
# 输出：
#   artifacts\win-x64\DivePlanImporter.Windows.exe  （及依赖项）
#
# 前提：.NET 9 SDK（https://dotnet.microsoft.com/download）

param(
    [switch]$SelfContained,
    [switch]$Debug
)

$ErrorActionPreference = 'Stop'
$ScriptDir  = $PSScriptRoot
$ProjDir    = Join-Path $ScriptDir 'DivePlanImporter.Windows'
$ProjFile   = Join-Path $ProjDir  'DivePlanImporter.Windows.csproj'
$OutDir     = Join-Path $ScriptDir 'artifacts\win-x64'

Write-Host '=== DivePlan 潜水导入助手 · Windows Build ===' -ForegroundColor Cyan

if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    Write-Error '.NET SDK 未找到。请安装 .NET 9 SDK：https://dotnet.microsoft.com/download'
    exit 1
}

$DotnetVersion = dotnet --version
Write-Host "dotnet 版本: $DotnetVersion"

# ── 构建参数 ──────────────────────────────────────────────────────────────────
$Config = if ($Debug) { 'Debug' } else { 'Release' }
$SC     = if ($SelfContained) { 'true' } else { 'false' }

$PublishArgs = @(
    'publish', $ProjFile,
    '-c', $Config,
    '-r', 'win-x64',
    "--self-contained:$SC",
    '-o', $OutDir,
    '/p:PublishSingleFile=false'   # 保留 DLL 结构，便于调试和热更 device-rules.json
)

if ($SelfContained) {
    Write-Host '模式: 自包含（用户无需安装 .NET 9）'
} else {
    Write-Host '模式: framework-dependent（需用户已装 .NET 9 Desktop Runtime）'
}
Write-Host "配置: $Config  →  $OutDir`n"

# ── 执行构建 ──────────────────────────────────────────────────────────────────
Write-Host "[1/2] dotnet publish ..." -ForegroundColor Yellow
& dotnet @PublishArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "dotnet publish 失败（exitcode=$LASTEXITCODE）"
    exit 1
}

# ── 验证产物 ──────────────────────────────────────────────────────────────────
Write-Host "`n[2/2] 验证产物 ..." -ForegroundColor Yellow
$ExePath  = Join-Path $OutDir 'DivePlanImporter.Windows.exe'
$RulesDir = Join-Path $OutDir 'Rules\device-rules.json'

if (Test-Path $ExePath) {
    $SizeMB = (Get-Item $ExePath).Length / 1MB
    Write-Host ("  ✓ EXE : $ExePath  ({0:F1} MB)" -f $SizeMB) -ForegroundColor Green
} else {
    Write-Error "未找到 $ExePath"
}

if (Test-Path $RulesDir) {
    Write-Host "  ✓ 规则: $RulesDir" -ForegroundColor Green
} else {
    Write-Warning "device-rules.json 未复制到输出目录，请检查 .csproj CopyToOutputDirectory"
}

Write-Host "`n✓ 完成！" -ForegroundColor Cyan
