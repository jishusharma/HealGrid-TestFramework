param(
    [string]$ProjectDir = "D:\IdeaProjects\HealGrid",
    [string]$SuiteXml = "testNgXmls\healenium-healing-proof.xml",
    [string]$BuildId = "",
    [ValidateSet("true", "false")]
    [string]$HealEnabled = "true",
    [ValidateSet("grid", "local", "browserstack")]
    [string]$Execution = "grid",
    [string]$GridUrl = "http://localhost:4444",
    [string]$HealeniumHost = "localhost"
)

$ErrorActionPreference = "Stop"

# IntelliJ usage:
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\run-local-allure-report.ps1"
# Purpose:
#   Run a TestNG suite locally, collect Allure results, and generate a local Allure HTML report.
#   UI suites also attach a compact Healenium healing summary to each Allure test result.

function Assert-DockerReady {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    docker ps --format "{{.Names}}" | Out-Null
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $oldPreference

    if ($exitCode -ne 0) {
        throw "Docker is not ready. Start Docker Desktop, then rerun this script from IntelliJ Terminal."
    }
}

if ([string]::IsNullOrWhiteSpace($BuildId)) {
    $BuildId = "local-allure-" + (Get-Date -Format "yyyyMMdd-HHmmss")
}

Set-Location -LiteralPath $ProjectDir

$allureResults = Join-Path $ProjectDir "target\allure-results"
$allureReport = Join-Path $ProjectDir "target\allure-report"

Remove-Item -LiteralPath $allureResults -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $allureReport -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $allureResults | Out-Null

if ($Execution -eq "grid" -or $HealEnabled -eq "true") {
    Assert-DockerReady
    docker-compose up -d postgres-db healenium selector-imitator selenium-hub chrome
}

$env:LOCAL_BUILD_ID = $BuildId
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "healenium"
$env:DB_USER = "healenium_user"
$env:DB_PASSWORD = "healenium_password"

Write-Host ""
Write-Host "STEP 1 - Run TestNG suite and collect Allure results"
Write-Host "Suite: $SuiteXml"
Write-Host "Build: $BuildId"
Write-Host "Healing enabled: $HealEnabled"

$testArgs = @(
    "-q",
    "test",
    "-Dheadless=true",
    "-Dhealenium.host=$HealeniumHost",
    "-Dexecution=$Execution",
    "-Dgrid.url=$GridUrl",
    "-Dheal.enabled=$HealEnabled",
    "-Dsurefire.suiteXmlFiles=$SuiteXml",
    "-Dmaven.test.failure.ignore=true"
)

& mvn @testArgs
if ($LASTEXITCODE -ne 0) {
    throw "Maven test execution failed before Allure report generation. Exit code: $LASTEXITCODE"
}

Write-Host ""
Write-Host "STEP 2 - Generate local Allure HTML report"

$allureCommand = Get-Command allure -ErrorAction SilentlyContinue
if ($allureCommand) {
    & allure generate $allureResults --clean -o $allureReport
    if ($LASTEXITCODE -ne 0) {
        throw "Allure CLI report generation failed. Exit code: $LASTEXITCODE"
    }
} else {
    Write-Host "Allure CLI not found on PATH. Falling back to Maven allure:report."
    & mvn -q allure:report
    if ($LASTEXITCODE -ne 0) {
        throw "Maven allure:report generation failed. Exit code: $LASTEXITCODE"
    }
}

Write-Host ""
Write-Host "DONE"
Write-Host "Allure results: $allureResults"
Write-Host "Allure report:  $allureReport\index.html"
Write-Host "Open from IntelliJ or browser after the script finishes."
