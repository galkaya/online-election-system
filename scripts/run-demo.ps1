# Starts the application with the "demo" profile (persistent H2 file database in .\data\demo).
# Usage:  .\scripts\run-demo.ps1
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not $env:JAVA_HOME) {
    $localJdk = Join-Path $env:USERPROFILE '.jdks\temurin-21'
    if (Test-Path (Join-Path $localJdk 'bin\java.exe')) {
        $env:JAVA_HOME = $localJdk
    }
}
if (-not $env:JAVA_HOME) {
    Write-Error 'JAVA_HOME is not set and no JDK was found in %USERPROFILE%\.jdks\temurin-21. Install JDK 21 and set JAVA_HOME.'
}

Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
Write-Host 'Starting the demo at http://localhost:8080 (press Ctrl+C to stop).'
& .\mvnw.cmd -B -ntp spring-boot:run "-Dspring-boot.run.profiles=demo"
