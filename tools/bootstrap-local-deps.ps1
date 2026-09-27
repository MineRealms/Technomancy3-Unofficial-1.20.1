[CmdletBinding()]
param(
    [string]$SourceRepository = (Join-Path $PSScriptRoot '..\..\Pollution-Unofficial-1.20.1\local-repo')
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceRoot = (Resolve-Path -LiteralPath $SourceRepository).Path
$targetRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'local-repo'))
$manifest = Get-Content -LiteralPath (Join-Path $targetRoot 'DEPENDENCIES.json') -Raw | ConvertFrom-Json
$copies = @()

# Verify every source before copying any artifact.
foreach ($artifact in $manifest.artifacts) {
    $sourceFile = [IO.Path]::GetFullPath((Join-Path $sourceRoot $artifact.path))
    $targetFile = [IO.Path]::GetFullPath((Join-Path $targetRoot $artifact.path))
    if (-not $sourceFile.StartsWith($sourceRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        -not $targetFile.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Artifact path is outside the expected repository: $($artifact.path)"
    }
    $sourceHash = (Get-FileHash -LiteralPath $sourceFile -Algorithm SHA256).Hash
    if ($sourceHash -ne $artifact.sha256) { throw "Source checksum mismatch: $sourceFile" }
    if (Test-Path -LiteralPath $targetFile) {
        if ((Get-FileHash -LiteralPath $targetFile -Algorithm SHA256).Hash -ne $artifact.sha256) {
            throw "A different artifact already exists: $targetFile"
        }
    } else {
        $copies += [PSCustomObject]@{ Source = $sourceFile; Target = $targetFile; Hash = $artifact.sha256 }
    }
}

foreach ($copy in $copies) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $copy.Target) -Force | Out-Null
    Copy-Item -LiteralPath $copy.Source -Destination $copy.Target
    if ((Get-FileHash -LiteralPath $copy.Target -Algorithm SHA256).Hash -ne $copy.Hash) {
        throw "Copied artifact checksum mismatch: $($copy.Target)"
    }
}
Write-Output "Verified $($manifest.artifacts.Count) TC4R artifacts; copied $($copies.Count) into $targetRoot"
