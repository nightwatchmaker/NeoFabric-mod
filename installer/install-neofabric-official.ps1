[CmdletBinding()]
param(
    [string]$MinecraftDir = "$env:APPDATA\.minecraft",
    [string]$Artifact = "",
    [string]$MinecraftVersion = "26.2",
    [string]$ProfileId = "NeoFabric-26.2-dev",
    [string]$LoaderVersion = "3.9.0-dev",
    [string]$ManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
    [string]$LauncherProfilesFile = ""
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($Artifact)) {
    $ProjectArtifact = Join-Path $Root ("build\libs\neofabric-loader-{0}.jar" -f $LoaderVersion)
    $PortableArtifact = Join-Path $ScriptDir ("neofabric-loader-{0}.jar" -f $LoaderVersion)
    if (Test-Path -LiteralPath $ProjectArtifact -PathType Leaf) {
        $Artifact = $ProjectArtifact
    } elseif (Test-Path -LiteralPath $PortableArtifact -PathType Leaf) {
        $Artifact = $PortableArtifact
    } else {
        throw "NeoFabric artifact not found. Put neofabric-loader-$LoaderVersion.jar beside this PS1 or pass -Artifact."
    }
}
if (-not (Test-Path -LiteralPath $Artifact -PathType Leaf)) {
    throw "NeoFabric artifact not found: $Artifact"
}
$Artifact = (Resolve-Path -LiteralPath $Artifact).Path
$VersionDir = Join-Path $MinecraftDir ("versions\" + $ProfileId)
$LibraryDir = Join-Path $MinecraftDir ("libraries\org\neofabric\loader\" + $LoaderVersion)
$LibraryJar = Join-Path $LibraryDir ("loader-{0}.jar" -f $LoaderVersion)
$Work = Join-Path ([System.IO.Path]::GetTempPath()) ("neofabric-official-" + [guid]::NewGuid().ToString())
try {
    New-Item -ItemType Directory -Force -Path $Work, $VersionDir, $LibraryDir | Out-Null
    $Manifest = Invoke-RestMethod -Uri $ManifestUrl -Method Get
    $Version = @($Manifest.versions | Where-Object { $_.id -eq $MinecraftVersion }) | Select-Object -First 1
    if ($null -eq $Version -or [string]::IsNullOrWhiteSpace([string]$Version.url)) {
        throw "Minecraft version $MinecraftVersion was not found in the manifest."
    }
    $BaseJson = Join-Path $Work ("{0}.json" -f $MinecraftVersion)
    Invoke-WebRequest -Uri ([string]$Version.url) -OutFile $BaseJson
    $Base = Get-Content -LiteralPath $BaseJson -Raw | ConvertFrom-Json
    foreach ($Required in @("id", "mainClass", "libraries")) {
        if ($null -eq $Base.PSObject.Properties[$Required]) {
            throw "Mojang version JSON is missing required field: $Required"
        }
    }
    $NeoFabricLibrary = [pscustomobject]@{ name = "org.neofabric:loader:$LoaderVersion" }
    $Libraries = @($NeoFabricLibrary)
    # Keep this child manifest deliberately minimal. The official launcher merges
    # inherited manifests; copying arguments/logging/custom fields can create a
    # mixed schema that is parsed as invalid (notably logging.argument/type).
    $Profile = [ordered]@{
        id = $ProfileId
        inheritsFrom = $Base.id
        type = "custom"
        mainClass = "org.neofabric.launcher.NeoFabricLauncher"
        libraries = $Libraries
    }
    $ProfileJson = Join-Path $VersionDir ("{0}.json" -f $ProfileId)
    $Profile | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ProfileJson -Encoding UTF8
    Copy-Item -LiteralPath $Artifact -Destination $LibraryJar -Force
    if ([string]::IsNullOrWhiteSpace($LauncherProfilesFile)) {
        $storeProfiles = Join-Path $MinecraftDir "launcher_profiles_microsoft_store.json"
        $legacyProfiles = Join-Path $MinecraftDir "launcher_profiles.json"
        $LauncherProfilesFile = if (Test-Path -LiteralPath $storeProfiles) { $storeProfiles } else { $legacyProfiles }
    }
    if (Test-Path -LiteralPath $LauncherProfilesFile) {
        $LauncherProfiles = Get-Content -LiteralPath $LauncherProfilesFile -Raw | ConvertFrom-Json
    } else {
        $LauncherProfiles = [pscustomobject]@{ profiles = [pscustomobject]@{} }
    }
    if ($null -eq $LauncherProfiles.PSObject.Properties["profiles"]) {
        $LauncherProfiles | Add-Member -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{})
    }
    $ProfileKey = "neofabric-" + ($ProfileId.ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
    $Now = [DateTime]::UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'")
    $Entry = [pscustomobject]@{
        name = "NeoFabric $MinecraftVersion (development)"
        type = "custom"
        created = $Now
        lastUsed = $Now
        lastVersionId = $ProfileId
        gameDir = $MinecraftDir
    }
    $LauncherProfiles.profiles | Add-Member -NotePropertyName $ProfileKey -NotePropertyValue $Entry -Force
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LauncherProfilesFile) | Out-Null
    $LauncherProfiles | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $LauncherProfilesFile -Encoding UTF8
    $Hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $LibraryJar).Hash.ToLowerInvariant()
    @(
        "NeoFabric official Minecraft Launcher development profile"
        "Profile: $ProfileId"
        "Minecraft: $MinecraftVersion"
        "Profile JSON: $ProfileJson"
        "Loader library: $LibraryJar"
        "Launcher profiles: $LauncherProfilesFile"
        "Loader SHA-256: $Hash"
        "Status: development-only"
    ) | Set-Content -LiteralPath (Join-Path $VersionDir "INSTALL-MANIFEST.txt") -Encoding UTF8
    Write-Host "Generated profile: $ProfileJson"
    Write-Host "Registered launcher installation: $LauncherProfilesFile"
    Write-Host "Installed library: $LibraryJar"
    Write-Host "SHA-256: $Hash"
} finally {
    if (Test-Path -LiteralPath $Work) { Remove-Item -LiteralPath $Work -Recurse -Force }
}
