param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Configuration
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectRoot "gradlew.bat"
$configurationBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Configuration))
$arguments = @(
    ":core:inspectConfiguration",
    "-PinspectConfigurationBase64=$configurationBase64"
)

Push-Location $projectRoot
try {
    [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
    $OutputEncoding = [Text.UTF8Encoding]::new($false)
    & $gradle @arguments
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
