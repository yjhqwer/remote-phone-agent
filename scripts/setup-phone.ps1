# ==============================================================================
# Remote Phone Agent - Phone Port 5555 Setup (PowerShell)
# ==============================================================================
param (
    [string]$PhoneIp = "192.168.1.2",
    [int]$PairPort = 5555
)

$adb = "adb.exe"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    if (Test-Path "$env:USERPROFILE\platform-tools\adb.exe") {
        $adb = "$env:USERPROFILE\platform-tools\adb.exe"
    } else {
        Write-Error "adb.exe not found! Please install platform-tools."
        exit 1
    }
}

Write-Host "==> Connecting to phone at ${PhoneIp}:${PairPort}..."
& $adb connect "${PhoneIp}:${PairPort}"

Write-Host "==> Switching adb daemon to persistent TCP port 5555..."
& $adb -s "${PhoneIp}:${PairPort}" tcpip 5555

Start-Sleep -Seconds 2
Write-Host "==> Verifying connection on port 5555..."
& $adb connect "${PhoneIp}:5555"
& $adb -s "${PhoneIp}:5555" get-state
