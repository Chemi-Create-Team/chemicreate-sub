# -Scenario plant: builds the largest reactor with its fuel pool and gantry crane in a new overworld, refuels, runs and shuts it down.
# -Scenario cycle: runs the largest reactor through start, criticality, danger, AZ-5 shutdown and restart twice while checking the control room console and display link.
# -Scenario quarry: builds a quarry of -Dcreateplant.quarryChunks (default 2) chunks square from a marker and digs it. Both write run/rbmk-plant-result.json.
param([ValidateSet('plant', 'quarry', 'cycle', 'loop')][string]$Scenario = 'plant')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
$report = Join-Path $projectRoot 'run/rbmk-plant-result.json'
$startedAt = Get-Date
Push-Location $projectRoot
try {
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Dcreateplant.rbmkPlaytest=$Scenario".Trim()
    # Java reports JAVA_TOOL_OPTIONS on stderr; Windows PowerShell would stop on it under 'Stop'.
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat runClient
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'Minecraft world generation failed.' }
    if (-not (Test-Path -LiteralPath $report) -or (Get-Item -LiteralPath $report).LastWriteTime -lt $startedAt) {
        throw 'The plant did not pass the runtime checks. See run/logs/latest.log.'
    }
    Get-Content -LiteralPath $report -Raw
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    Pop-Location
}
