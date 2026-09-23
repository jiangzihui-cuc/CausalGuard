# capture-uid-attribution.ps1
# A1-8：抓取 TrackerControl 底座的 UID 归属日志并统计成功率（Windows / PowerShell）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File .\capture-uid-attribution.ps1 -Seconds 120
#
# 说明：
#   - 需要已安装 adb（platform-tools），且手机已连接并授权 USB 调试
#   - 抓取期间请正常使用手机（微信 / 浏览器 / 购物类 App 等），制造 TCP/UDP 流量
#   - 只统计底座调用 getConnectionOwnerUid 的 TCP/UDP 连接；ICMP 不进入此调用，天然无法归属

param(
    [int]$Seconds = 120,
    [string]$OutFile = "uid-attribution.log"
)

$ErrorActionPreference = "Stop"

# 定位 adb：优先 PATH，其次脚本目录 / 当前目录
$adbCmd = (Get-Command adb -ErrorAction SilentlyContinue)
$adb = $null
if ($adbCmd) { $adb = $adbCmd.Source }
if (-not $adb) {
    foreach ($cand in @("$PSScriptRoot\adb.exe", "$PWD\adb.exe", ".\adb.exe")) {
        if (Test-Path $cand) { $adb = (Resolve-Path $cand).Path; break }
    }
}
if (-not $adb) {
    Write-Host "找不到 adb。请先安装 Android platform-tools 并把其目录加入 PATH，或把本脚本放到 platform-tools 目录下运行。" -ForegroundColor Red
    exit 1
}
Write-Host "使用 adb: $adb" -ForegroundColor DarkGray

Write-Host "已连接设备：" -ForegroundColor Cyan
& $adb devices

Write-Host "清空 logcat 缓冲..." -ForegroundColor Cyan
& $adb logcat -c

Write-Host "开始抓取 $Seconds 秒，请在此期间正常使用手机..." -ForegroundColor Green
$proc = Start-Process -FilePath $adb `
    -ArgumentList @("logcat", "-v", "time", "-s", "TrackerControl.VPN") `
    -RedirectStandardOutput $OutFile -PassThru -NoNewWindow

Start-Sleep -Seconds $Seconds
if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
Start-Sleep -Seconds 1

if (-not (Test-Path $OutFile)) {
    Write-Host "抓取失败，未生成日志文件。" -ForegroundColor Red
    exit 1
}

$lines = Get-Content $OutFile
$attempts = ($lines | Select-String -SimpleMatch "Get uid local=").Count
$uidMatches = $lines | Select-String -Pattern "Get uid=(-?\d+)"
$uids = @()
foreach ($m in $uidMatches) { $uids += [int]$m.Matches[0].Groups[1].Value }

$total = $uids.Count
$valid = ($uids | Where-Object { $_ -ge 0 }).Count
$invalid = ($uids | Where-Object { $_ -lt 0 }).Count
$rate = if ($total -gt 0) { [math]::Round(100.0 * $valid / $total, 1) } else { 0 }

Write-Host ""
Write-Host "===== A1-8 UID 归属统计 =====" -ForegroundColor Yellow
Write-Host ("getUid 调用（local 行） : {0}" -f $attempts)
Write-Host ("Get uid= 结果行          : {0}" -f $total)
Write-Host ("成功(uid >= 0)          : {0}" -f $valid)
Write-Host ("失败(uid = -1)          : {0}" -f $invalid)
Write-Host ("成功率                  : {0}%" -f $rate)
Write-Host ("原始日志                : {0}" -f (Resolve-Path $OutFile))
Write-Host ""
Write-Host "把以上五行数字发给成员 A 即可回填 spike-results。" -ForegroundColor Green
