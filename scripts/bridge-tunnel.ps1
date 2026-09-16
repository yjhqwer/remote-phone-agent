# ==============================================================================
# Remote Phone Agent - SSH Reverse Tunnel Bridge (PowerShell)
# ==============================================================================
param (
    [string]$VpsHost = "yjh-vps",
    [string]$PhoneIp = "192.168.1.2",
    [int]$LocalPort = 5555,
    [int]$RemotePort = 15555
)

Write-Host "==> Starting SSH reverse tunnel from $VpsHost:$RemotePort to $PhoneIp:$LocalPort..."
Write-Host "==> Keep this window open or run with -IsDaemon to maintain connection."

ssh -N -o ExitOnForwardFailure=yes -R "${RemotePort}:${PhoneIp}:${LocalPort}" $VpsHost
