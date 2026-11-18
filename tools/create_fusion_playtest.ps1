# -Mode world: builds a formed tokamak with its desk, M-G, start motor bank and creative supplies in a new flat creative world for manual play.
# -Mode verify: builds the same plant in a separate world and commissions it from the desk to a held burn. Both write run/fusion-playtest-result.json.
param([ValidateSet('world', 'verify')][string]$Mode = 'world')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
$report = Join-Path $projectRoot 'run/fusion-playtest-result.json'
$startedAt = Get-Date
Push-Location $projectRoot
try {
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Dcreateplant.fusionPlaytest=$Mode".Trim()
    # Java reports JAVA_TOOL_OPTIONS on stderr; Windows PowerShell would stop on it under 'Stop'.
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat runClient
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'Minecraft world generation failed.' }
    if (-not (Test-Path -LiteralPath $report) -or (Get-Item -LiteralPath $report).LastWriteTime -lt $startedAt) {
        throw 'The fusion plant did not pass the runtime checks. See run/logs/latest.log.'
    }
    Get-Content -LiteralPath $report -Raw
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    Pop-Location
}
