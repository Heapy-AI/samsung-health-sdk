<#
.SYNOPSIS
    Pulls every JSON file exported by the PoC app from a connected Android device
    into the repository's data/ folder.

    File names are discovered on the device, so all 24 data types are covered
    without the script knowing them in advance.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\pull-data.ps1
    powershell -ExecutionPolicy Bypass -File scripts\pull-data.ps1 -Serial R3CN30XXXXX
    powershell -ExecutionPolicy Bypass -File scripts\pull-data.ps1 -Clean
#>
param(
    [string]$PackageName = "com.example.shealthpoc",
    [string]$Serial = "",
    [switch]$Clean
)

# NOTE: must stay "Continue". In Windows PowerShell 5.1 a native exe's stderr becomes a
# NativeCommandError, which under "Stop" aborts the script even when adb exits 0.
# Every adb call below is checked explicitly via $LASTEXITCODE / file size instead.
$ErrorActionPreference = "Continue"

# --- locate adb ------------------------------------------------------------
$adb = $null
$cmd = Get-Command adb -ErrorAction SilentlyContinue
if ($null -ne $cmd) {
    $adb = $cmd.Source
} else {
    # Join-Path throws on a null root, so only build paths for vars that are set.
    $candidates = @()
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe") }
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) { $candidates += (Join-Path $root "platform-tools\adb.exe") }
    }
    foreach ($c in $candidates) {
        if (Test-Path $c) { $adb = $c; break }
    }
}
if (-not $adb) {
    Write-Error "adb not found. Add platform-tools to PATH or set ANDROID_HOME."
    exit 1
}

$deviceArgs = @()
if ($Serial -ne "") { $deviceArgs = @("-s", $Serial) }

$projectRoot = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $projectRoot "data"
if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest | Out-Null }

$remoteDir = "/sdcard/Android/data/$PackageName/files/data"
$runAsDir = "files/data"

Write-Host "adb        : $adb"
Write-Host "device dir : $remoteDir"
Write-Host "PC dir     : $dest"
Write-Host ""

if ($Clean) {
    Get-ChildItem $dest -Filter *.json -ErrorAction SilentlyContinue | Remove-Item -Force
    Write-Host "cleaned existing *.json in data/"
    Write-Host ""
}

# --- discover the file list on the device ---------------------------------
function Get-RemoteJsonNames {
    # 1) plain shell ls on the app-specific external dir
    $out = & $adb @deviceArgs shell "ls $remoteDir" 2>$null
    $names = @($out | ForEach-Object { $_.Trim() } | Where-Object { $_ -like "*.json" })
    if ($names.Count -gt 0) { return $names }

    # 2) fallback: internal-storage mirror through run-as (debuggable build)
    $out = & $adb @deviceArgs exec-out run-as $PackageName ls $runAsDir 2>$null
    $names = @($out | ForEach-Object { $_.Trim() } | Where-Object { $_ -like "*.json" })
    return $names
}

$files = Get-RemoteJsonNames
if ($files.Count -eq 0) {
    Write-Warning "No JSON found on the device. Run the app first and grant the Samsung Health consents."
    Write-Warning "Checked: $remoteDir  and  run-as $PackageName $runAsDir"
    exit 1
}
Write-Host "found $($files.Count) file(s) on device"
Write-Host ""

# --- fetch each file: adb pull first, run-as as fallback -------------------
$pulled = 0
foreach ($f in $files) {
    $local = Join-Path $dest $f

    & $adb @deviceArgs pull "$remoteDir/$f" "$local" 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0 -and (Test-Path $local) -and (Get-Item $local).Length -gt 0) {
        Write-Host ("  [pull  ] {0}" -f $f)
        $pulled++
        continue
    }

    $content = & $adb @deviceArgs exec-out run-as $PackageName cat "$runAsDir/$f"
    if ($LASTEXITCODE -eq 0 -and $content) {
        $text = ($content -join "`n")
        [System.IO.File]::WriteAllText($local, $text, (New-Object System.Text.UTF8Encoding($false)))
        Write-Host ("  [run-as] {0}" -f $f)
        $pulled++
    } else {
        if (Test-Path $local) { Remove-Item $local -Force }
        Write-Host ("  [MISS  ] {0}" -f $f)
    }
}

Write-Host ""
if ($pulled -eq 0) {
    Write-Warning "Nothing could be pulled."
    exit 1
}

Get-ChildItem $dest -Filter *.json | Sort-Object Name | ForEach-Object {
    Write-Host ("{0,-36} {1,9} bytes" -f $_.Name, $_.Length)
}
Write-Host ""
Write-Host "$pulled/$($files.Count) file(s) -> $dest"
