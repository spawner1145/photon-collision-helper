param(
    [string]$GameDirectory = 'D:/game/.minecraft/versions/1.21.1-NeoForge_21.1.252'
)

$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot 'build/libs/photon-collision-helper-neoforge-1.21.1-0.1.4.jar'
$mods = Join-Path (Resolve-Path -LiteralPath $GameDirectory).Path 'mods'
$photon = Join-Path $mods 'photon-neoforge-1.21.1-2.2.8-all.jar'
$ldlib = Join-Path $mods 'ldlib2-neoforge-1.21.1-2.2.42-all.jar'
foreach ($required in @($source, $photon, $ldlib)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing dependency or build: $required" }
}
$destination = Join-Path $mods (Split-Path -Leaf $source)
$others = @(Get-ChildItem -LiteralPath $mods -Filter '*photon-collision-helper*.jar' | Where-Object { $_.FullName -ne $destination })
if ($others.Count -gt 0) { throw 'Another helper version exists; remove it manually before installing to avoid duplicate mod IDs.' }
$originalHash = (Get-FileHash -LiteralPath $photon -Algorithm SHA256).Hash
Copy-Item -LiteralPath $source -Destination $destination -Force
if ((Get-FileHash -LiteralPath $photon -Algorithm SHA256).Hash -ne $originalHash) { throw 'Photon hash changed unexpectedly.' }
$record = [ordered]@{
    installedAt = (Get-Date).ToString('o')
    gameDirectory = $GameDirectory
    helper = $destination
    helperSha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
    originalPhotonSha256 = $originalHash
    photonUnchanged = $true
    restartRequired = $true
    startupRegistrationVerified = $false
}
$record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'installation.json') -Encoding utf8
Write-Output 'Helper installed. Photon is unchanged. Restart Minecraft for permanent loading and automatic registration.'
