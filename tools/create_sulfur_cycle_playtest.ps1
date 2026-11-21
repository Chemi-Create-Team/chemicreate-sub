# Builds the hybrid sulfur cycle (sulfur dioxide -> absorption tower -> sulfurous acid -> electrolysis -> sulfuric acid) piped end to end
# in a new flat creative world, runs it until sulfuric acid collects, checks the lines and saves the world. Writes run/sulfur-cycle-playtest-result.json.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
$report = Join-Path $projectRoot 'run/sulfur-cycle-playtest-result.json'
$startedAt = Get-Date
Push-Location $projectRoot
try {
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Dcreateplant.sulfurCyclePlaytest=true".Trim()
    # Java reports JAVA_TOOL_OPTIONS on stderr; Windows PowerShell would stop on it under 'Stop'.
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat runClient
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'Minecraft world generation failed.' }
    if (-not (Test-Path -LiteralPath $report) -or (Get-Item -LiteralPath $report).LastWriteTime -lt $startedAt) {
        throw 'The sulfur cycle did not pass the runtime checks. See run/logs/latest.log.'
    }
    Get-Content -LiteralPath $report -Raw
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    Pop-Location
}
