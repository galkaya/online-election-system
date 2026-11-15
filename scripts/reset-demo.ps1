# Deletes the demo database so that the next demo start seeds fresh data.
# It only ever touches <project>\data\demo\election-demo.* and refuses to run without -ConfirmReset.
# Usage:  .\scripts\reset-demo.ps1 -ConfirmReset
param(
    [switch]$ConfirmReset
)
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$demoDir = Join-Path $projectRoot 'data\demo'
$expectedSuffix = [IO.Path]::Combine('data', 'demo')

if (-not (Test-Path (Join-Path $projectRoot 'pom.xml'))) {
    Write-Error "Refusing to run: $projectRoot does not look like the project folder (no pom.xml)."
}
if (-not $demoDir.StartsWith($projectRoot, [StringComparison]::OrdinalIgnoreCase) -or -not $demoDir.EndsWith($expectedSuffix)) {
    Write-Error "Refusing to run: the target folder $demoDir is not the demo database folder of this project."
}
if (-not $ConfirmReset) {
    Write-Host "This deletes the demo database in $demoDir (all demo votes, elections and accounts)."
    Write-Host 'Nothing was deleted. Run again with -ConfirmReset to proceed.'
    exit 2
}
if (-not (Test-Path $demoDir)) {
    Write-Host 'There is no demo database yet. Nothing to reset.'
    exit 0
}

$files = Get-ChildItem -Path $demoDir -File | Where-Object { $_.Name -like 'election-demo.*' }
foreach ($file in $files) {
    try {
        # H2 keeps the database file open while the application runs.
        $stream = [IO.File]::Open($file.FullName, 'Open', 'ReadWrite', 'None')
        $stream.Close()
    } catch {
        Write-Error "The demo database is in use ($($file.Name)). Stop the application first, then run the reset again."
    }
}
foreach ($file in $files) {
    Remove-Item -LiteralPath $file.FullName -Force
    Write-Host "Deleted $($file.FullName)"
}
Write-Host 'Demo database reset. Start the demo again to seed fresh data.'
