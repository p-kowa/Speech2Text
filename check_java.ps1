# Skript zum Vergleichen der Java-Versionen beider Projekte

Write-Host "=== Java-Version Vergleich ===" -ForegroundColor Cyan
Write-Host ""

# Prüfe Zulu Java
Write-Host "--- Zulu Java Installation ---" -ForegroundColor Yellow
if (Test-Path "C:\Program Files\Zulu\zulu-17") {
    Write-Host "✅ Zulu Java 17 gefunden" -ForegroundColor Green
    if (Test-Path "C:\Program Files\Zulu\zulu-17\lib\security\cacerts") {
        $zuluCacerts = Get-Item "C:\Program Files\Zulu\zulu-17\lib\security\cacerts"
        Write-Host "   cacerts: $($zuluCacerts.Length / 1KB) KB" -ForegroundColor Green
        Write-Host "   Last Modified: $($zuluCacerts.LastWriteTime)" -ForegroundColor Green
    }
} else {
    Write-Host "❌ Zulu Java nicht gefunden" -ForegroundColor Red
}

Write-Host ""

# Prüfe Android Studio JBR
Write-Host "--- Android Studio JBR ---" -ForegroundColor Yellow
if (Test-Path "C:\Program Files\Android\Android Studio\jbr") {
    Write-Host "✅ Android Studio JBR gefunden" -ForegroundColor Green
    if (Test-Path "C:\Program Files\Android\Android Studio\jbr\lib\security\cacerts") {
        $jbrCacerts = Get-Item "C:\Program Files\Android\Android Studio\jbr\lib\security\cacerts"
        Write-Host "   cacerts: $($jbrCacerts.Length / 1KB) KB" -ForegroundColor Green
        Write-Host "   Last Modified: $($jbrCacerts.LastWriteTime)" -ForegroundColor Green
    }
} else {
    Write-Host "❌ Android Studio JBR nicht gefunden" -ForegroundColor Red
}

Write-Host ""
Write-Host "--- AbfrageHelfer Projekt ---" -ForegroundColor Green

# Prüfe gradle.properties des funktionierenden Projekts
if (Test-Path "C:\Daten\Android\AbfrageHelfer\gradle.properties") {
    Write-Host "gradle.properties Inhalt:" -ForegroundColor Cyan
    Get-Content "C:\Daten\Android\AbfrageHelfer\gradle.properties" | ForEach-Object {
        if ($_ -match "java|jvm|ssl|trust") {
            Write-Host "  → $_" -ForegroundColor Yellow
        }
    }
}

Write-Host ""

# Führe gradlew --version aus
Write-Host "Gradle Version (AbfrageHelfer):" -ForegroundColor Cyan
try {
    Push-Location "C:\Daten\Android\AbfrageHelfer"
    $gradleOutput = & .\gradlew.bat --version 2>&1 | Out-String
    Write-Host $gradleOutput
    Pop-Location
} catch {
    Write-Host "Fehler beim Ausführen von gradlew" -ForegroundColor Red
    Pop-Location
}

Write-Host ""
Write-Host "--- Speach2Text Projekt ---" -ForegroundColor Yellow

# Prüfe gradle.properties des aktuellen Projekts
if (Test-Path "C:\Daten\Android\Speach2Text\gradle.properties") {
    Write-Host "gradle.properties Inhalt:" -ForegroundColor Cyan
    Get-Content "C:\Daten\Android\Speach2Text\gradle.properties" | ForEach-Object {
        if ($_ -match "java|jvm|ssl|trust") {
            Write-Host "  → $_" -ForegroundColor Yellow
        }
    }
}

Write-Host ""

# Führe gradlew --version aus
Write-Host "Gradle Version (Speach2Text):" -ForegroundColor Cyan
try {
    Push-Location "C:\Daten\Android\Speach2Text"
    $gradleOutput = & .\gradlew.bat --version 2>&1 | Out-String
    Write-Host $gradleOutput
    Pop-Location
} catch {
    Write-Host "Fehler beim Ausführen von gradlew" -ForegroundColor Red
    Pop-Location
}

Write-Host ""
Write-Host "=== JAVA_HOME Umgebungsvariable ===" -ForegroundColor Cyan
if ($env:JAVA_HOME) {
    Write-Host "JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green
} else {
    Write-Host "JAVA_HOME nicht gesetzt" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Systemweites Java ===" -ForegroundColor Cyan
try {
    $javaVersion = java -version 2>&1 | Out-String
    Write-Host $javaVersion
} catch {
    Write-Host "Kein systemweites Java gefunden" -ForegroundColor Yellow
}
