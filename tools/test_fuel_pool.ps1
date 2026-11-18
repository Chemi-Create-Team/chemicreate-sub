param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$classesRoot = Join-Path $projectRoot 'build/classes/java/main/net/createplant/gametest'
Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & .\gradlew.bat build --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    }
    $resolvedClasses = (Resolve-Path -LiteralPath $classesRoot).Path
    $expectedClasses = [IO.Path]::GetFullPath($classesRoot)
    if ($resolvedClasses -ne $expectedClasses) { throw 'Unexpected GameTest output directory.' }
    if (Get-ChildItem -LiteralPath $resolvedClasses -Filter '*.pool-disabled' -File) {
        throw 'A previous interrupted check left disabled class files. Restore them before retrying.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $resolvedClasses 'FuelPoolGameTests.class')) -or
        -not (Test-Path -LiteralPath (Join-Path $resolvedClasses 'CraneGameTests.class'))) {
        throw 'Required fuel pool or crane tests are missing from the build output.'
    }
    $disabledClasses = @()
    try {
        # Restrict discovery in build output; keep source annotations and templates unchanged.
        Get-ChildItem -LiteralPath $resolvedClasses -Filter '*.class' -File |
            Where-Object { $_.Name -notlike 'FuelPoolGameTests*' -and $_.Name -notlike 'CraneGameTests*' } |
            ForEach-Object {
                Rename-Item -LiteralPath $_.FullName -NewName ($_.Name + '.pool-disabled')
                $disabledClasses += $_.FullName
            }
        & .\gradlew.bat runGameTestServer -x compileJava -x processResources --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Fuel pool or crane GameTests failed.' }
    } finally {
        foreach ($original in $disabledClasses) {
            $disabled = $original + '.pool-disabled'
            if (Test-Path -LiteralPath $disabled) {
                Rename-Item -LiteralPath $disabled -NewName ([IO.Path]::GetFileName($original))
            }
        }
    }
} finally {
    Pop-Location
}
