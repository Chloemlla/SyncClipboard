# Download a Lumen Crash SDK GitHub Release into a local Maven-style repo tree.
# Uses public release assets; no GitHub Packages auth required.
#
# Usage:
#   pwsh ./scripts/Sync-LumenCrashReleaseMaven.ps1 [-Version <ver>] [-OutDir .m2-lumen-crash]

param(
    [string]$OwnerRepo = "Chloemlla/Project-Lumen",
    [string]$Version = "",
    [string]$OutDir = ".m2-lumen-crash"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not $Version) {
    $resolver = Join-Path $scriptDir "Resolve-LumenCrashLatest.ps1"
    if (Test-Path $resolver) {
        $Version = & $resolver -OwnerRepo $OwnerRepo
    } else {
        throw "Version is required when Resolve-LumenCrashLatest.ps1 is unavailable."
    }
}

function Get-Release {
    param([string]$Token)
    $headers = @{
        Accept = "application/vnd.github+json"
        "User-Agent" = "lumen-crash-release-sync"
    }
    if ($Token) {
        $headers.Authorization = "Bearer $Token"
    }
    $tag = "lumen-crash-v$Version"
    return Invoke-RestMethod `
        -Uri "https://api.github.com/repos/$OwnerRepo/releases/tags/$tag" `
        -Headers $headers
}

function Save-Asset {
    param(
        [string]$Url,
        [string]$Target
    )
    $dir = Split-Path -Parent $Target
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Invoke-WebRequest -Uri $Url -OutFile $Target
}

$token = $env:GH_TOKEN
if (-not $token) { $token = $env:GITHUB_TOKEN }

$release = $null
if ($token) {
    try { $release = Get-Release -Token $token } catch { $release = $null }
}
if (-not $release) {
    try { $release = Get-Release -Token "" } catch { $release = $null }
}

$bundleDir = Join-Path $OutDir "com/chloemlla/lumen/lumen-crash/$Version"
$coreDir = Join-Path $OutDir "com/chloemlla/lumen/lumen-crash-core/$Version"
New-Item -ItemType Directory -Force -Path $bundleDir | Out-Null
New-Item -ItemType Directory -Force -Path $coreDir | Out-Null

$downloaded = 0
if ($release) {
    $assets = @($release.assets | Where-Object { $_.name -match '\.(aar|pom|module|jar)$' })
    foreach ($asset in $assets) {
        $targetDir = if ($asset.name -like "lumen-crash-core-*") { $coreDir } else { $bundleDir }
        $target = Join-Path $targetDir $asset.name
        Write-Host "Downloading $($asset.name)"
        Save-Asset -Url $asset.browser_download_url -Target $target
        $downloaded++
    }
}

if ($downloaded -eq 0) {
    # Fallback without Releases API: conventional Maven publish asset names on the release tag.
    $tag = "lumen-crash-v$Version"
    $base = "https://github.com/$OwnerRepo/releases/download/$tag"
    $names = @(
        "lumen-crash-$Version.aar",
        "lumen-crash-$Version.pom",
        "lumen-crash-$Version.module",
        "lumen-crash-$Version-sources.jar",
        "lumen-crash-core-$Version.aar",
        "lumen-crash-core-$Version.pom",
        "lumen-crash-core-$Version.module",
        "lumen-crash-core-$Version-sources.jar"
    )
    foreach ($name in $names) {
        $url = "$base/$name"
        $targetDir = if ($name -like "lumen-crash-core-*") { $coreDir } else { $bundleDir }
        $target = Join-Path $targetDir $name
        try {
            Write-Host "Downloading $name (fallback)"
            Save-Asset -Url $url -Target $target
            $downloaded++
        } catch {
            Write-Host "Skip missing asset $name"
        }
    }
}

if ($downloaded -eq 0) {
    throw "No Maven assets found for tag lumen-crash-v$Version"
}

Write-Host "Synced release $Version into $OutDir ($downloaded files)"
Write-Host "Gradle repo example:"
Write-Host "  maven { url = uri(`"file://$([System.IO.Path]::GetFullPath($OutDir).Replace('\','/'))`") }"
Write-Host "  implementation(`"com.chloemlla.lumen:lumen-crash:$Version`")"