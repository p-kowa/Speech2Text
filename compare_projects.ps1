# PowerShell-Skript zum Vergleichen der Gradle-Konfigurationen

$workingProject = "C:\Daten\Android\AbfrageHelfer"
$currentProject = "C:\Daten\Android\Speach2Text"

Write-Host "=== Vergleich der Gradle-Konfigurationen ===" -ForegroundColor Cyan
Write-Host ""

# Vergleiche gradle.properties
Write-Host "--- gradle.properties vom funktionierenden Projekt (AbfrageHelfer) ---" -ForegroundColor Green
if (Test-Path "$workingProject\gradle.properties") {
    Get-Content "$workingProject\gradle.properties"
} else {
    Write-Host "Datei nicht gefunden" -ForegroundColor Red
}

Write-Host ""
Write-Host "--- gradle.properties vom aktuellen Projekt (Speach2Text) ---" -ForegroundColor Yellow
if (Test-Path "$currentProject\gradle.properties") {
    Get-Content "$currentProject\gradle.properties"
} else {
    Write-Host "Datei nicht gefunden" -ForegroundColor Red
}

Write-Host ""
Write-Host "--- settings.gradle.kts vom funktionierenden Projekt ---" -ForegroundColor Green
if (Test-Path "$workingProject\settings.gradle.kts") {
    Get-Content "$workingProject\settings.gradle.kts"
} elseif (Test-Path "$workingProject\settings.gradle") {
    Get-Content "$workingProject\settings.gradle"
} else {
    Write-Host "Datei nicht gefunden" -ForegroundColor Red
}

Write-Host ""
Write-Host "--- settings.gradle.kts vom aktuellen Projekt ---" -ForegroundColor Yellow
if (Test-Path "$currentProject\settings.gradle.kts") {
    Get-Content "$currentProject\settings.gradle.kts"
} else {
    Write-Host "Datei nicht gefunden" -ForegroundColor Red
}

Write-Host ""
Write-Host "--- gradle-wrapper.properties Vergleich ---" -ForegroundColor Cyan
Write-Host "Funktionierendes Projekt:"
if (Test-Path "$workingProject\gradle\wrapper\gradle-wrapper.properties") {
    Get-Content "$workingProject\gradle\wrapper\gradle-wrapper.properties" | Select-String "distributionUrl"
}
Write-Host "Aktuelles Projekt:"
if (Test-Path "$currentProject\gradle\wrapper\gradle-wrapper.properties") {
    Get-Content "$currentProject\gradle\wrapper\gradle-wrapper.properties" | Select-String "distributionUrl"
}
