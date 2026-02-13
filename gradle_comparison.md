# Häufige Gradle-Konfigurationen für SSL-Probleme

## Option 1: Leere gradle.properties (Standard)
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```
Funktioniert, wenn keine Firewall/Proxy/Antivirus SSL abfängt.

## Option 2: Mit explizitem Truststore
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -Djavax.net.ssl.trustStore="C:\\Program Files\\Android\\Android Studio\\jbr\\lib\\security\\cacerts" -Djavax.net.ssl.trustStorePassword=changeit
```

## Option 3: Proxy-Konfiguration (falls Firmen-Proxy vorhanden)
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
systemProp.http.proxyHost=proxy.company.com
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=proxy.company.com
systemProp.https.proxyPort=8080
systemProp.http.nonProxyHosts=localhost|127.0.0.1
```

## Option 4: Ohne SSL-Verifizierung (NICHT EMPFOHLEN für Produktion)
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
systemProp.javax.net.ssl.trustStore=
systemProp.javax.net.ssl.trustStorePassword=
```

## Option 5: Mit Windows Certificate Store
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
systemProp.javax.net.ssl.trustStoreType=Windows-ROOT
```
Hinweis: Funktioniert nur mit bestimmten Java-Versionen.
