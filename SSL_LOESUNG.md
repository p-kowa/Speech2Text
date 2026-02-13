# Lösung für SSL-Zertifikatsproblem

## Problem
Der Fehler "unable to find valid certification path to requested target" tritt auf, wenn Gradle versucht, über HTTPS auf Maven-Repositories zuzugreifen, aber die SSL-Zertifikate nicht validieren kann.

## Lösung

Die Konfiguration erfolgt in der `gradle.properties` Datei:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -Djavax.net.ssl.trustStore="C:\\Program Files\\Android\\Android Studio\\jbr\\lib\\security\\cacerts" -Djavax.net.ssl.trustStorePassword=changeit
```

## Was wurde geändert:

### 1. gradle.properties
- Fügt expliziten Pfad zum Java-Truststore hinzu
- Verwendet das cacerts-File vom Android Studio JBR (JetBrains Runtime)
- Setzt das Standard-Passwort "changeit"

### 2. settings.gradle.kts
- Standard-Konfiguration mit pluginManagement und dependencyResolutionManagement
- Repositories: google(), mavenCentral(), gradlePluginPortal()

### 3. build.gradle.kts
- Minimale Konfiguration nur mit Plugins
- Keine allprojects-Deklaration (nicht mehr nötig bei moderner Gradle-Konfiguration)

## Nach der Konfiguration:

1. Stoppe den Gradle Daemon:
   ```
   .\gradlew.bat --stop
   ```

2. Starte den Build neu:
   ```
   .\gradlew.bat build
   ```

## Alternativen (falls oben nicht funktioniert):

### Option A: Ohne expliziten Truststore
Wenn Sie keine Firewall/Proxy-Probleme haben:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

### Option B: Mit Firmen-Proxy
Falls ein Proxy vorhanden ist:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
systemProp.http.proxyHost=proxy.firma.de
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=proxy.firma.de
systemProp.https.proxyPort=8080
```

### Option C: Eigenes Zertifikat importieren
Falls ein Firmen-Zertifikat verwendet wird:
```bash
keytool -import -alias firmen-cert -file C:\path\to\cert.cer -keystore "C:\Program Files\Android\Android Studio\jbr\lib\security\cacerts" -storepass changeit
```

## Hinweis
Die Konfiguration mit dem expliziten Truststore funktioniert am besten in Firmen-Umgebungen, wo Antivirensoftware oder Firewalls HTTPS-Verbindungen abfangen.
