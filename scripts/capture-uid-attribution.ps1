# capture-uid-attribution.ps1
# A1-8: capture TrackerControl UID attribution logs and compute success rate (Windows / PowerShell)
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\capture-uid-attribution.ps1 -Seconds 120
#
# Notes:
#   - Requires adb (platform-tools) and a phone connected with USB debugging authorized
#   - Use the phone normally during capture (WeChat / browser / shopping apps) to create TCP/UDP traffic
#   - Only TCP/UDP calls to getConnectionOwnerUid are counted; ICMP never enters this call

param(
    [int]$Seconds = 120,
    [string]$OutFile = "uid-attribution.log"
)

$ErrorActionPreference = "Stop"

# Locate adb: PATH first, then script directory / current directory
$adbCmd = (Get-Command adb -ErrorAction SilentlyContinue)
$adb = $null
if ($adbCmd) { $adb = $adbCmd.Source }
if (-not $adb) {
    foreach ($cand in @("$PSScriptRoot\adb.exe", "$PWD\adb.exe", ".\adb.exe")) {
        if (Test-Path $cand) { $adb = (Resolve-Path $cand).Path; break }
    }
}
if (-not $adb) {
    Write-Host "ERROR: adb not found. Install Android platform-tools and add it to PATH, or run this script from the platform-tools folder." -ForegroundColor Red
    exit 1
}
Write-Host "Using adb: $adb" -ForegroundColor DarkGray

Write-Host "Connected devices:" -ForegroundColor Cyan
& $adb devices

Write-Host "Clearing logcat buffer..." -ForegroundColor Cyan
& $adb logcat -c

Write-Host "Capturing for $Seconds seconds. Please use the phone now..." -ForegroundColor Green
$proc = Start-Process -FilePath $adb `
    -ArgumentList @("logcat", "-v", "time", "-s", "TrackerControl.VPN") `
    -RedirectStandardOutput $OutFile -PassThru -NoNewWindow

Start-Sleep -Seconds $Seconds
if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
Start-Sleep -Seconds 1

if (-not (Test-Path $OutFile)) {
    Write-Host "ERROR: log file was not created." -ForegroundColor Red
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
Write-Host "===== A1-8 UID attribution stats =====" -ForegroundColor Yellow
Write-Host ("getUid calls (local lines) : {0}" -f $attempts)
Write-Host ("Get uid= result lines      : {0}" -f $total)
Write-Host ("success (uid >= 0)         : {0}" -f $valid)
Write-Host ("failure (uid = -1)         : {0}" -f $invalid)
Write-Host ("success rate               : {0}%" -f $rate)
Write-Host ("raw log                    : {0}" -f (Resolve-Path $OutFile))
Write-Host ""
Write-Host "Send these numbers to member A to fill in spike-results." -ForegroundColor Green
