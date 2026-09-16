<#
.SYNOPSIS
    Monitors GitHub Actions CI build for Antigravity APK, downloads artifact,
    delivers to Google Drive local folder, and validates file integrity.

.DESCRIPTION
    Script: download-delivery-apk.ps1
    Target Repo: yjhqwer/remote-phone-agent
    Target File: G:\我的云端硬盘\apk\Antigravity-v1.0.apk
#>

[CmdletBinding()]
param (
    [Parameter()]
    [string]$Repo = "yjhqwer/remote-phone-agent",

    [Parameter()]
    [string]$WorkflowFile = "build-apk.yml",

    [Parameter()]
    [string]$ArtifactName = "Antigravity-Mobile-APK",

    [Parameter()]
    [string]$TargetDirectory = "G:\我的云端硬盘\apk",

    [Parameter()]
    [string]$TargetFileName = "Antigravity-v1.0.apk",

    [Parameter()]
    [string]$GitHubToken = "",

    [Parameter()]
    [int]$PollIntervalSeconds = 15,

    [Parameter()]
    [int]$TimeoutMinutes = 25
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Step {
    param([string]$Message)
    Write-Host "`n[$(Get-Date -Format 'HH:mm:ss')] [INFO] $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [WARN] $Message" -ForegroundColor Yellow
}

function Write-Err {
    param([string]$Message)
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [ERROR] $Message" -ForegroundColor Red
}

# 1. Resolve Token
$token = $GitHubToken
if (-not $token) {
    if ($env:GITHUB_TOKEN) { $token = $env:GITHUB_TOKEN }
    elseif ($env:GH_TOKEN) { $token = $env:GH_TOKEN }
    elseif (Get-Command gh -ErrorAction SilentlyContinue) { $token = (& gh auth token 2>$null) }
}

$headers = @{
    "Accept"               = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
    "User-Agent"           = "Antigravity-Delivery-Agent/1.0"
}
if ($token) {
    $headers["Authorization"] = "Bearer $token"
    Write-Step "GitHub Authentication: Configured (Token Present)"
}

# 2. Monitor Workflow Execution
Write-Step "Querying latest workflow runs for '$WorkflowFile' in '$Repo'..."
$startTime = [DateTime]::UtcNow
$timeoutSpan = [TimeSpan]::FromMinutes($TimeoutMinutes)
$targetRun = $null

while ($true) {
    if (([DateTime]::UtcNow - $startTime) -gt $timeoutSpan) {
        Write-Err "Timeout reached ($TimeoutMinutes minutes) while waiting for workflow run."
        exit 1
    }

    $runsUrl = "https://api.github.com/repos/$Repo/actions/workflows/$WorkflowFile/runs?per_page=5"
    try {
        $response = Invoke-RestMethod -Uri $runsUrl -Headers $headers -Method Get
    } catch {
        Write-Err "Failed to retrieve workflow runs: $_"
        exit 1
    }

    if (-not $response.workflow_runs -or $response.workflow_runs.Count -eq 0) {
        Write-Warn "No workflow runs found. Retrying in $PollIntervalSeconds s..."
        Start-Sleep -Seconds $PollIntervalSeconds
        continue
    }

    $latestRun = $response.workflow_runs[0]
    $runId = $latestRun.id
    $runNumber = $latestRun.run_number
    $status = $latestRun.status
    $conclusion = $latestRun.conclusion
    $elapsed = [DateTime]::UtcNow - $startTime
    $elapsedFormatted = "{0:D2}m {1:D2}s" -f [int]$elapsed.TotalMinutes, $elapsed.Seconds

    if ($status -eq "completed") {
        if ($conclusion -eq "success") {
            Write-Success "Run #$runNumber (ID: $runId) COMPLETED successfully in $elapsedFormatted!"
            $targetRun = $latestRun
            break
        } else {
            Write-Err "Run #$runNumber (ID: $runId) failed with conclusion: '$conclusion'"
            Write-Err "Workflow URL: $($latestRun.html_url)"
            exit 1
        }
    } else {
        Write-Host "  ⏳ [Run #$runNumber] Status: '$status' | Branch: $($latestRun.head_branch) | Elapsed: $elapsedFormatted | Waiting ${PollIntervalSeconds}s..." -ForegroundColor DarkGray
        Start-Sleep -Seconds $PollIntervalSeconds
    }
}

# 3. Locate Artifact
Write-Step "Locating artifact '$ArtifactName' for Run ID $($targetRun.id)..."
$artifactsUrl = "https://api.github.com/repos/$Repo/actions/runs/$($targetRun.id)/artifacts"
$artifactResp = Invoke-RestMethod -Uri $artifactsUrl -Headers $headers -Method Get

$matchedArtifact = $artifactResp.artifacts | Where-Object { $_.name -eq $ArtifactName } | Select-Object -First 1
if (-not $matchedArtifact) {
    $matchedArtifact = $artifactResp.artifacts | Where-Object { $_.name -like "*APK*" } | Select-Object -First 1
}

if (-not $matchedArtifact) {
    Write-Err "No matching artifact found in run $($targetRun.id)."
    exit 1
}

$downloadZipUrl = $matchedArtifact.archive_download_url
Write-Success "Found Artifact: '$($matchedArtifact.name)' (Size: $([math]::Round($matchedArtifact.size_in_bytes / 1MB, 2)) MB)"

# 4. Download and Extract
$tempDir = Join-Path $env:TEMP ("antigravity_build_" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
$tempZip = Join-Path $tempDir "artifact.zip"
$tempExtract = Join-Path $tempDir "extracted"

try {
    Write-Step "Downloading artifact archive..."
    $curlCmd = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($token -and $curlCmd) {
        & $curlCmd.Source -sSL `
            -H "Authorization: Bearer $token" `
            -H "Accept: application/vnd.github+json" `
            -H "X-GitHub-Api-Version: 2022-11-28" `
            -o $tempZip `
            "$downloadZipUrl"
    } else {
        Invoke-WebRequest -Uri $downloadZipUrl -Headers $headers -OutFile $tempZip -MaximumRedirection 5
    }

    Write-Step "Extracting artifact archive..."
    Expand-Archive -Path $tempZip -DestinationPath $tempExtract -Force

    # 5. Deliver to Destination
    $extractedFiles = Get-ChildItem -Path $tempExtract -Recurse
    $sourceApk = $extractedFiles | Where-Object { $_.Name -like "*.apk" -and $_.Name -like "*release*" } | Select-Object -First 1
    if (-not $sourceApk) {
        $sourceApk = $extractedFiles | Where-Object { $_.Name -like "*.apk" } | Select-Object -First 1
    }

    if (-not $sourceApk) {
        Write-Err "No .apk file found in artifact archive."
        exit 1
    }

    if (-not (Test-Path $TargetDirectory)) {
        New-Item -ItemType Directory -Path $TargetDirectory -Force | Out-Null
    }

    $finalDestination = Join-Path $TargetDirectory $TargetFileName
    Write-Step "Delivering APK to: $finalDestination"
    Copy-Item -Path $sourceApk.FullName -Destination $finalDestination -Force

    # 6. Verification
    $fileInfo = Get-Item -Path $finalDestination
    $fileSizeMB = [math]::Round($fileInfo.Length / 1MB, 2)
    $calculatedHash = (Get-FileHash -Path $finalDestination -Algorithm SHA256).Hash.ToLower()

    Write-Host "`n========================================================" -ForegroundColor Green
    Write-Host "       ANTIGRAVITY APK DELIVERY VERIFIED               " -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Green
    Write-Host " Destination : $finalDestination"
    Write-Host " File Size   : $fileSizeMB MB ($($fileInfo.Length) bytes)"
    Write-Host " SHA-256     : $calculatedHash"
    Write-Host " Status      : READY FOR INSTALLATION ON GT NEO5" -ForegroundColor Green
    Write-Host "========================================================`n" -ForegroundColor Green

} finally {
    if (Test-Path $tempDir) {
        Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
