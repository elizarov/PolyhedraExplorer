param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Configuration,

    [Parameter(Position = 1)]
    [string] $Output = "build/rendered/configuration.png",

    [int] $Width = 1600,

    [int] $Height = 1200
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectRoot "gradlew.bat"
$configurationBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Configuration))
$arguments = @(
    ":renderer:renderConfig",
    "-PrenderConfigurationBase64=$configurationBase64",
    "-PrenderOutput=$Output",
    "-PrenderWidth=$Width",
    "-PrenderHeight=$Height"
)

Push-Location $projectRoot
try {
    & $gradle @arguments
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
