# Resolve the latest Lumen Crash SDK main auto-release.
# Prefers public GitHub Releases API; falls back to git ls-remote tags.
# Bad/expired tokens are ignored so public resolution still works.
# Prefers auto tags like 0.1.0-<shortSha> over frozen pure X.Y.Z tags.

param(
    [string]$OwnerRepo = "Chloemlla/Project-Lumen"
)

$ErrorActionPreference = "Stop"

function Get-VersionFromTagName([string]$tagName) {
    return ($tagName -replace '^lumen-crash-v', '')
}

function Test-AutoReleaseVersion([string]$version) {
    return $version -match '^[0-9]+\.[0-9]+\.[0-9]+-[0-9a-fA-F]{7,}$'
}

function Get-LatestFromApi {
    param([string]$Token)

    $headers = @{
        Accept = "application/vnd.github+json"
        "User-Agent" = "lumen-crash-version-resolver"
    }
    if ($Token) {
        $headers.Authorization = "Bearer $Token"
    }

    $releases = Invoke-RestMethod `
        -Uri "https://api.github.com/repos/$OwnerRepo/releases?per_page=100" `
        -Headers $headers

    $candidates = @()
    foreach ($release in $releases) {
        if ($release.draft) { continue }
        if ($release.tag_name -notlike "lumen-crash-v*") { continue }
        $version = Get-VersionFromTagName $release.tag_name
        $published = if ($release.published_at) { [datetime]$release.published_at } else { [datetime]$release.created_at }
        $candidates += [pscustomobject]@{
            Version = $version
            Published = $published
            IsAuto = Test-AutoReleaseVersion $version
        }
    }

    if ($candidates.Count -eq 0) {
        return $null
    }

    $auto = $candidates | Where-Object { $_.IsAuto } | Sort-Object Published -Descending | Select-Object -First 1
    if ($auto) {
        return $auto.Version
    }

    return ($candidates | Sort-Object Published -Descending | Select-Object -First 1).Version
}

function Get-LatestFromGitTags {
    $lines = & git ls-remote --tags "https://github.com/$OwnerRepo.git" "refs/tags/lumen-crash-v*" 2>$null
    if (-not $lines) {
        return $null
    }

    $versions = New-Object System.Collections.Generic.List[string]
    foreach ($line in $lines) {
        if ($line -match 'refs/tags/lumen-crash-v([^\^\s]+)') {
            $versions.Add($Matches[1]) | Out-Null
        }
    }

    $unique = $versions | Sort-Object -Unique
    if (-not $unique) {
        return $null
    }

    $auto = @($unique | Where-Object { Test-AutoReleaseVersion $_ })
    if ($auto.Count -gt 0) {
        return ($auto | Sort-Object -Descending | Select-Object -First 1)
    }

    return ($unique | Sort-Object -Descending | Select-Object -First 1)
}

$token = $env:GH_TOKEN
if (-not $token) { $token = $env:GITHUB_TOKEN }

$version = $null
if ($token) {
    try { $version = Get-LatestFromApi -Token $token } catch { $version = $null }
}
if (-not $version) {
    try { $version = Get-LatestFromApi -Token "" } catch { $version = $null }
}
if (-not $version) {
    try { $version = Get-LatestFromGitTags } catch { $version = $null }
}

if (-not $version) {
    throw "No lumen-crash release found"
}

Write-Output $version