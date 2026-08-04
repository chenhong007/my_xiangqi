# 一键安装 Python 依赖并运行古谱下载器
# 在 PowerShell 中执行：.\install_and_run.ps1

param(
    [string]$Manual = "",
    [switch]$List,
    [switch]$Validate,
    [switch]$Scan
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

# 查找 Python
$PythonCmd = $null
foreach ($cmd in @("python", "python3", "py")) {
    try {
        $ver = & $cmd --version 2>&1
        if ($ver -match "Python 3") {
            $PythonCmd = $cmd
            Write-Host "找到 Python: $ver" -ForegroundColor Green
            break
        }
    } catch { }
}

if (-not $PythonCmd) {
    # 尝试常见安装路径
    $paths = @(
        "C:\Python311\python.exe",
        "C:\Python310\python.exe",
        "C:\Python39\python.exe",
        "$env:LOCALAPPDATA\Programs\Python\Python311\python.exe",
        "$env:LOCALAPPDATA\Programs\Python\Python310\python.exe",
    )
    foreach ($p in $paths) {
        if (Test-Path $p) {
            $PythonCmd = $p
            Write-Host "找到 Python: $p" -ForegroundColor Green
            break
        }
    }
}

if (-not $PythonCmd) {
    Write-Host "未找到 Python 3，请先安装 Python 3.9+" -ForegroundColor Red
    Write-Host "下载地址：https://www.python.org/downloads/"
    exit 1
}

# 安装依赖
Write-Host "`n安装 Python 依赖..." -ForegroundColor Cyan
& $PythonCmd -m pip install -r requirements.txt -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "依赖安装失败，尝试继续..." -ForegroundColor Yellow
}

# 执行操作
if ($List) {
    & $PythonCmd scraper.py --list
} elseif ($Validate) {
    & $PythonCmd validate.py
} elseif ($Manual) {
    if ($Scan) {
        & $PythonCmd scraper.py --manual $Manual --scan --verbose
    } else {
        & $PythonCmd scraper.py --manual $Manual --verbose
    }
} else {
    Write-Host "`n开始下载所有古谱..." -ForegroundColor Cyan
    & $PythonCmd scraper.py --verbose
}
