param(
    [string]$GameDirectory = 'D:/game/.minecraft/versions/1.21.1-NeoForge_21.1.252'
)

$ErrorActionPreference = 'Stop'
$migration = Join-Path $PSScriptRoot 'integration/output-collision-migration-initial.json'
if ((Test-Path -LiteralPath $migration) -and (Get-Content -LiteralPath $migration -Raw | ConvertFrom-Json).completed) {
    throw 'Multiple formal effects now depend on this helper. This ring-only rollback refuses to remove it. Restore all projects and installed resources listed in the batch migration backup before uninstalling.'
}
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$output = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../output')).Path
$backup = Join-Path $output 'backups/须佐环斩_算法性能优化前_20261015_141539/须佐能乎_环斩冲击'
$formal = Join-Path $output '须佐能乎_环斩冲击'
$helper = Join-Path $game 'mods/photon-collision-helper-neoforge-1.21.1-0.1.4.jar'
$pack = Join-Path $backup 'susanoo-ring.fxpack'
if (-not (Test-Path -LiteralPath $pack -PathType Leaf)) { throw 'Native collision backup is missing; helper was not removed.' }
$snapshot = Join-Path $output ('backups/须佐环斩_helper回退前_' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
New-Item -ItemType Directory -Path $snapshot | Out-Null
Copy-Item -LiteralPath $formal -Destination $snapshot -Recurse
foreach ($entry in Get-ChildItem -LiteralPath $backup) {
    Copy-Item -LiteralPath $entry.FullName -Destination $formal -Recurse -Force
}
$assets = Join-Path $backup 'assets/susanoo_ring'
$gameAssets = Join-Path $game 'ldlib2/assets/susanoo_ring'
New-Item -ItemType Directory -Path $gameAssets -Force | Out-Null
foreach ($entry in Get-ChildItem -LiteralPath $assets) {
    Copy-Item -LiteralPath $entry.FullName -Destination $gameAssets -Recurse -Force
}
Copy-Item -LiteralPath $pack -Destination (Join-Path $game 'photon/fxpacks/susanoo-ring.fxpack') -Force
if (Test-Path -LiteralPath $helper -PathType Leaf) { Remove-Item -LiteralPath $helper }
Write-Output 'Native ring project, assets and pack restored. Helper removed. Restart Minecraft; Photon is unchanged.'
