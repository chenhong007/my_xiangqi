<#
.SYNOPSIS
    弈古 App 一键构建脚本

.DESCRIPTION
    1. 将 data/ 目录的原始棋谱数据转换为 App 格式 (data/app/)
    2. 同步到 Android 工程的 assets/manuals/ 目录
    3. 编译 Android App（默认 Debug，可选 Release）

.PARAMETER Release
    加上此开关编译 Release 包

.PARAMETER SkipExport
    跳过数据导出步骤（仅重新编译）

.PARAMETER InstallOnly
    编译后自动安装到连接的设备

.PARAMETER DataOnly
    仅导出和同步数据，不编译

.EXAMPLE
    .\build.ps1                      # 导出数据 + 编译 Debug
    .\build.ps1 -Release             # 导出数据 + 编译 Release
    .\build.ps1 -SkipExport          # 跳过数据导出，仅编译
    .\build.ps1 -InstallOnly         # 编译并安装到设备
    .\build.ps1 -DataOnly            # 仅更新数据，不编译
#>

param(
    [switch]$Release,
    [switch]$SkipExport,
    [switch]$InstallOnly,
    [switch]$DataOnly
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$DataDir = Join-Path $ProjectRoot "data"
$AppDataDir = Join-Path $DataDir "app"
$AndroidDir = Join-Path $ProjectRoot "android"
$AssetsDir = Join-Path $AndroidDir "app\src\main\assets\manuals"

# ─────────────── 辅助函数 ───────────────

function Write-Step($msg) {
    Write-Host ""
    Write-Host "===> $msg" -ForegroundColor Cyan
}

function Write-Ok($msg) {
    Write-Host "  [OK] $msg" -ForegroundColor Green
}

function Write-Err($msg) {
    Write-Host "  [ERROR] $msg" -ForegroundColor Red
}

function Find-Python {
    $candidates = @(
        "C:\Users\Administrator\AppData\Local\Programs\Python\Python311\python.exe",
        "C:\Users\Administrator\AppData\Local\Programs\Python\Python312\python.exe",
        "C:\Python311\python.exe",
        "C:\Python312\python.exe"
    )
    foreach ($p in $candidates) {
        if (Test-Path $p) { return $p }
    }
    $cmd = Get-Command python -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $cmd = Get-Command python3 -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        return $env:JAVA_HOME
    }

    $candidates = @(
        # Android Studio bundled JBR
        "C:\Program Files\Android\Android Studio\jbr",
        "D:\Program Files\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        # Common JDK locations
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Eclipse Adoptium\jdk-17*",
        "C:\Program Files\Microsoft\jdk-17*",
        "D:\Java\jdk-17",
        "D:\jdk-17"
    )

    foreach ($pattern in $candidates) {
        $resolved = Resolve-Path $pattern -ErrorAction SilentlyContinue
        if ($resolved) {
            foreach ($dir in $resolved) {
                if (Test-Path "$dir\bin\java.exe") {
                    return $dir.Path
                }
            }
        }
    }
    return $null
}

# ─────────────── 环境检查 ───────────────

Write-Step "环境检查"

$Python = Find-Python
if (-not $Python) {
    Write-Err "未找到 Python，请安装 Python 3.11+ 或将其加入 PATH"
    exit 1
}
Write-Ok "Python: $Python"

$Gradlew = Join-Path $AndroidDir "gradlew.bat"
if (-not $DataOnly) {
    if (-not (Test-Path $Gradlew)) {
        Write-Err "未找到 gradlew.bat，请在 android/ 目录运行: gradle wrapper --gradle-version 8.7"
        exit 1
    }
    Write-Ok "Gradle Wrapper: $Gradlew"

    $JavaHome = Find-JavaHome
    if (-not $JavaHome) {
        Write-Err "未找到 JDK。请执行以下任一操作:"
        Write-Host "  1. 安装 JDK 17+ 并设置 JAVA_HOME 环境变量" -ForegroundColor Yellow
        Write-Host "  2. 安装 Android Studio (自带 JBR)" -ForegroundColor Yellow
        Write-Host "  3. 临时设置: `$env:JAVA_HOME = 'C:\path\to\jdk'" -ForegroundColor Yellow
        exit 1
    }
    $env:JAVA_HOME = $JavaHome
    Write-Ok "JAVA_HOME: $JavaHome"
}

# ─────────────── 步骤 1: 数据导出 ───────────────

if (-not $SkipExport) {
    Write-Step "导出棋谱数据 (data/ -> data/app/)"

    $ExportScript = Join-Path $ProjectRoot "scraper\export_app.py"
    if (-not (Test-Path $ExportScript)) {
        Write-Err "未找到 $ExportScript"
        exit 1
    }

    & $Python $ExportScript
    if ($LASTEXITCODE -ne 0) {
        Write-Err "数据导出失败 (exit code: $LASTEXITCODE)"
        exit 1
    }

    $exportedCount = (Get-ChildItem $AppDataDir -Filter "*.json" | Measure-Object).Count
    Write-Ok "已导出 $exportedCount 个古谱文件到 data/app/"

    # ─────────────── 步骤 2: 同步到 assets ───────────────

    Write-Step "同步数据到 Android assets"

    if (-not (Test-Path $AssetsDir)) {
        New-Item -ItemType Directory -Path $AssetsDir -Force | Out-Null
    }

    $sourceFiles = Get-ChildItem $AppDataDir -Filter "*.json"
    $copied = 0
    $updated = 0

    foreach ($file in $sourceFiles) {
        $dest = Join-Path $AssetsDir $file.Name
        if (Test-Path $dest) {
            $srcHash = (Get-FileHash $file.FullName -Algorithm MD5).Hash
            $dstHash = (Get-FileHash $dest -Algorithm MD5).Hash
            if ($srcHash -eq $dstHash) {
                continue
            }
            $updated++
        } else {
            $copied++
        }
        Copy-Item $file.FullName $dest -Force
    }

    # 删除 assets 中多余的文件
    $removed = 0
    $assetFiles = Get-ChildItem $AssetsDir -Filter "*.json"
    foreach ($file in $assetFiles) {
        $src = Join-Path $AppDataDir $file.Name
        if (-not (Test-Path $src)) {
            Remove-Item $file.FullName -Force
            $removed++
        }
    }

    Write-Ok "新增 $copied / 更新 $updated / 删除 $removed 个文件"
} else {
    Write-Step "跳过数据导出 (-SkipExport)"
}

# ─────────────── 步骤 3: 编译 App ───────────────

if ($DataOnly) {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "  数据更新完成！" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host ""
    exit 0
}

if ($Release) {
    $task = "assembleRelease"
    $apkPath = "app\build\outputs\apk\release\app-release.apk"
} elseif ($InstallOnly) {
    $task = "installDebug"
    $apkPath = $null
} else {
    $task = "assembleDebug"
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
}

Write-Step "编译 Android App (task: $task)"

Push-Location $AndroidDir
try {
    & .\gradlew.bat $task --warning-mode=summary
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Gradle 编译失败 (exit code: $LASTEXITCODE)"
        exit 1
    }
} finally {
    Pop-Location
}

# ─────────────── 完成 ───────────────

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  构建成功！" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green

if ($apkPath) {
    $fullApk = Join-Path $AndroidDir $apkPath
    if (Test-Path $fullApk) {
        $size = [math]::Round((Get-Item $fullApk).Length / 1MB, 2)
        Write-Host "  APK: $fullApk" -ForegroundColor White
        Write-Host "  大小: ${size} MB" -ForegroundColor White
    }
}

if ($InstallOnly) {
    Write-Host "  已安装到设备" -ForegroundColor White
}

Write-Host ""
