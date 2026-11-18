# Builds the 5, 7, 9, 11 and 13 m RBMK variants in one world, checks them and writes run/rbmk-variants-result.json.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
$report = Join-Path $projectRoot 'run/rbmk-variants-result.json'
$startedAt = Get-Date
Push-Location $projectRoot
try {
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Dcreateplant.rbmkVariantsPlaytest=visual".Trim()
    # Java reports JAVA_TOOL_OPTIONS on stderr; Windows PowerShell would stop on it under 'Stop'.
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat runClient
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'Minecraft RBMK variant generation failed.' }
    if (-not (Test-Path -LiteralPath $report) -or (Get-Item -LiteralPath $report).LastWriteTime -lt $startedAt) {
        throw 'The RBMK variants did not pass the runtime checks. See run/logs/latest.log.'
    }
    Get-Content -LiteralPath $report -Raw
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    Pop-Location
}
