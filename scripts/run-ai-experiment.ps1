# Reproduces the baseline-versus-expanded measurement of the AI-assisted testing experiment
# (docs/ai-testing/). Both suites run against the same production code with the same JaCoCo and
# PIT settings; the only difference is whether tests tagged "adversarial" are included.
#
# Usage:  .\scripts\run-ai-experiment.ps1
# Output: docs\evidence\ai-experiment\  (raw CSV/XML reports and summary.md)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not $env:JAVA_HOME) {
    $localJdk = Join-Path $env:USERPROFILE '.jdks\temurin-21'
    if (Test-Path (Join-Path $localJdk 'bin\java.exe')) { $env:JAVA_HOME = $localJdk }
}
if (-not $env:JAVA_HOME) { Write-Error 'JAVA_HOME is not set. Install JDK 21 and set JAVA_HOME.' }

# Record the source state now, before this script writes any report file into the project.
$commit = (& git rev-parse --short HEAD 2>$null)
$dirty = if ((& git status --porcelain 2>$null)) { ' (with uncommitted changes)' } else { ' (clean working tree)' }

$out = Join-Path $projectRoot 'docs\evidence\ai-experiment'
New-Item -ItemType Directory -Force $out | Out-Null

function Invoke-Maven([string]$label, [string[]]$arguments) {
    Write-Host "== $label :: mvnw $($arguments -join ' ')"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    & .\mvnw.cmd -B -ntp @arguments | Out-File -Encoding utf8 (Join-Path $out "$label.log")
    $exit = $LASTEXITCODE
    $watch.Stop()
    if ($exit -ne 0) { Write-Error "$label failed with exit code $exit. See $out\$label.log" }
    return [Math]::Round($watch.Elapsed.TotalSeconds)
}

function Get-TestCount([string]$label) {
    # Sums the "Tests run: N" totals that Surefire and Failsafe print after "Results:".
    $total = 0
    foreach ($line in (Select-String -Path (Join-Path $out "$label.log") -Pattern '^\[(INFO|WARNING)\] Tests run: (\d+), Failures: \d+, Errors: \d+, Skipped: \d+\s*$')) {
        $total += [int]$line.Matches[0].Groups[2].Value
    }
    return $total
}

function Get-Coverage([string]$csvPath, [string]$packageFilter) {
    $rows = Import-Csv $csvPath | Where-Object { $_.PACKAGE -like $packageFilter }
    $lineCovered = ($rows | Measure-Object LINE_COVERED -Sum).Sum
    $lineMissed = ($rows | Measure-Object LINE_MISSED -Sum).Sum
    $branchCovered = ($rows | Measure-Object BRANCH_COVERED -Sum).Sum
    $branchMissed = ($rows | Measure-Object BRANCH_MISSED -Sum).Sum
    $linePercent = if (($lineCovered + $lineMissed) -gt 0) { [Math]::Round(100.0 * $lineCovered / ($lineCovered + $lineMissed), 1) } else { 0 }
    $branchPercent = if (($branchCovered + $branchMissed) -gt 0) { [Math]::Round(100.0 * $branchCovered / ($branchCovered + $branchMissed), 1) } else { 0 }
    return "$lineCovered/$($lineCovered + $lineMissed) lines ($linePercent%), $branchCovered/$($branchCovered + $branchMissed) branches ($branchPercent%)"
}

function Get-Mutations([string]$csvPath) {
    $counts = @{ KILLED = 0; SURVIVED = 0; NO_COVERAGE = 0; TIMED_OUT = 0 }
    $total = 0
    foreach ($line in Get-Content $csvPath) {
        $status = ($line -split ',')[5]
        if (-not $counts.ContainsKey($status)) { $counts[$status] = 0 }
        $counts[$status]++
        $total++
    }
    $killedPercent = [Math]::Round(100.0 * $counts.KILLED / $total, 1)
    return "$total mutants: $($counts.KILLED) killed ($killedPercent%), $($counts.TIMED_OUT) timed out, $($counts.SURVIVED) survived, $($counts.NO_COVERAGE) no coverage"
}

$results = [ordered]@{}
foreach ($suite in @('baseline', 'expanded')) {
    $profiles = if ($suite -eq 'baseline') { @('-Pbaseline') } else { @() }
    $pitProfiles = if ($suite -eq 'baseline') { '-Pmutation,baseline' } else { '-Pmutation' }

    $verifySeconds = Invoke-Maven "$suite-verify" (@($profiles) + @('clean', 'verify'))
    Copy-Item 'target\site\jacoco\jacoco.csv' (Join-Path $out "$suite-jacoco.csv") -Force
    Copy-Item 'target\site\jacoco\jacoco.xml' (Join-Path $out "$suite-jacoco.xml") -Force

    $pitSeconds = Invoke-Maven "$suite-pit" @($pitProfiles, 'test-compile', 'org.pitest:pitest-maven:mutationCoverage')
    Copy-Item "target\pit-reports\$suite\mutations.csv" (Join-Path $out "$suite-mutations.csv") -Force
    Copy-Item "target\pit-reports\$suite\mutations.xml" (Join-Path $out "$suite-mutations.xml") -Force
    # Keep the browsable PIT HTML report too; the next "clean" would delete it.
    $htmlCopy = Join-Path $out "pit-$suite-html"
    if (Test-Path $htmlCopy) { Remove-Item -Recurse -Force $htmlCopy }
    Copy-Item "target\pit-reports\$suite" $htmlCopy -Recurse -Force

    $results[$suite] = [ordered]@{
        Tests          = Get-TestCount "$suite-verify"
        VerifySeconds  = $verifySeconds
        CoverageAll    = Get-Coverage (Join-Path $out "$suite-jacoco.csv") '*'
        CoveragePolicy = Get-Coverage (Join-Path $out "$suite-jacoco.csv") 'edu.comp5100.election.policy'
        CoverageService = Get-Coverage (Join-Path $out "$suite-jacoco.csv") 'edu.comp5100.election.service'
        PitSeconds     = $pitSeconds
        Mutations      = Get-Mutations (Join-Path $out "$suite-mutations.csv")
    }
}

# Read the version from the JDK's "release" file. Calling "java -version" writes to stderr, which
# Windows PowerShell 5.1 turns into an error when it is redirected.
$javaVersion = ((Get-Content (Join-Path $env:JAVA_HOME 'release') | Where-Object { $_ -like 'JAVA_VERSION=*' }) -replace 'JAVA_VERSION=', '') -replace '"', ''
$lines = @(
    '# AI-assisted testing experiment: measured summary',
    '',
    "Generated by ``scripts/run-ai-experiment.ps1`` on $(Get-Date -Format 'yyyy-MM-dd HH:mm zzz').",
    "Source: commit $commit$dirty. Java: $javaVersion. OS: $([Environment]::OSVersion.VersionString).",
    '',
    'Both columns were measured on the same production code with the same JaCoCo and PIT settings.',
    'PIT scope: `edu.comp5100.election.policy.*`, mutator group DEFAULTS, unit-level JUnit Jupiter tests only.',
    '',
    '| Measure | Baseline suite | Expanded suite (baseline + adversarial) |',
    '|---|---|---|',
    "| Tests executed by ``verify`` (Surefire + Failsafe) | $($results.baseline.Tests) | $($results.expanded.Tests) |",
    "| ``verify`` wall-clock time | $($results.baseline.VerifySeconds) s | $($results.expanded.VerifySeconds) s |",
    "| JaCoCo, whole application | $($results.baseline.CoverageAll) | $($results.expanded.CoverageAll) |",
    "| JaCoCo, ``policy`` package | $($results.baseline.CoveragePolicy) | $($results.expanded.CoveragePolicy) |",
    "| JaCoCo, ``service`` package | $($results.baseline.CoverageService) | $($results.expanded.CoverageService) |",
    "| PIT, ``policy`` package | $($results.baseline.Mutations) | $($results.expanded.Mutations) |",
    "| PIT wall-clock time | $($results.baseline.PitSeconds) s | $($results.expanded.PitSeconds) s |",
    '',
    'Timed-out mutants are listed separately and are not counted as killed here, although PIT itself',
    'adds them to its "killed" percentage. Raw reports are next to this file.'
)
$lines | Out-File -Encoding utf8 (Join-Path $out 'summary.md')
Get-Content (Join-Path $out 'summary.md')
