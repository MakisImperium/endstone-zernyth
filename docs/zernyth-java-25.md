# Offizieller Java-Standard: Java 25 LTS

Java 25 LTS ist die dauerhafte offizielle Mindestversion und die Entwicklungs-
und Laufzeitbasis von Zernyth Engine. Bevorzugte Distribution ist Eclipse Temurin 25
(Gradle `JvmVendorSpec.ADOPTIUM`). Ältere Laufzeiten sind nicht unterstützt.
Eine andere Distribution darf für eine ausdrücklich dokumentierte lokale Prüfung
verwendet werden; sie senkt die Mindestversion nicht und ersetzt keinen Temurin-Test.

## Gradle und Beispielplugins

Gradle mindestens 9.1 verwenden: Die offizielle
[Kompatibilitätsmatrix](https://docs.gradle.org/current/userguide/compatibility.html)
führt ab 9.1 Java 25 sowohl für Toolchains als auch zum Ausführen von Gradle auf.
Das vorhandene lokale Gradle 9.8.2 erfüllt diese Voraussetzung. Es wird kein neuer
Wrapper und kein Download eingeführt. `settings.gradle.kts` prüft die Mindestversion.

Alle Module `api`, `runtime`, `example` sowie deren Testklassen erben zentral:

```kotlin
toolchain.languageVersion.set(JavaLanguageVersion.of(25))
toolchain.vendor.set(JvmVendorSpec.ADOPTIUM)
tasks.withType<JavaCompile>().configureEach { options.release.set(25) }
```

JavaExec-Aufgaben einschließlich `:runtime:selfTest` verwenden ebenfalls die
gewählte Toolchain. Keine Preview-Features. Classfile-Major ist 69. Die
[Gradle-Toolchain-Dokumentation](https://docs.gradle.org/current/userguide/toolchains.html)
erläutert Vendor-Auswahl und Release-Option.

`JAVA_HOME` auf das lokal installierte Temurin 25 setzen; bei Bedarf kann
`org.gradle.java.installations.paths` eine bereits vorhandene Installation angeben.
`org.gradle.java.installations.auto-download=false` bleibt gesetzt.

Aus `java/`:

```text
gradle --offline --no-daemon :api:jar :runtime:jar :example:jar
gradle --offline --no-daemon :runtime:integrationJar :runtime:failureIntegrationJar :runtime:advancedIntegrationJar :runtime:advancedPeerIntegrationJar :runtime:selfTest
```

Ohne Temurin schlägt die Toolchain-Auswahl mit Diagnose fehl, ohne JDK-Download.
Für eine vorhandene andere Distribution **Java 25** ausdrücklich
`-PzernythJavaVendor=any` ergänzen. Andere Property-Werte werden abgewiesen.
Compiler und Release bleiben 25, auch wenn Gradle selbst mit einem neueren JDK läuft.
Eigene Plugins: [Beispielanleitung](../java/example/README.md).

## CMake und JNI unter Windows/Linux

Die bestehende native Buildumgebung und vorhandene Presets bleiben maßgeblich.
`ENDSTONE_ENABLE_JAVA=ON` verwendet `find_package(Java 25 REQUIRED COMPONENTS Development)`
und `find_package(JNI REQUIRED COMPONENTS JVM)` mit Headers aus dem gewählten JDK.
`JAVA_HOME` vor dem Konfigurieren setzen; bei JDK-Wechsel auch alte CMake-Cachepfade
prüfen. Alle Java-Module und der JNI-Core müssen dieselbe Prozessarchitektur verwenden.

Die JVM wird weiterhin über die Invocation API dynamisch aus `jvm.dll` unter Windows
beziehungsweise `libjvm.so` unter Linux geladen; keine feste neue JVM-Linkabhängigkeit.
Die Bridge verwendet **JNI_VERSION_24**, die höchste im JDK-25-Header definierte
JNI-Revision. Eine Konstante `JNI_VERSION_25` existiert dort nicht. JNI-Revisionsnummer
und Java-Produktversion unterscheiden sich: Die Mindestlaufzeit bleibt Java 25.

Das explizite CMake-Ziel `zernyth_java` übergibt die bevorzugte Distribution an
Gradle. Standard `ZERNYTH_JAVA_VENDOR=temurin`; für einen dokumentierten lokalen
Fallback beim Konfigurieren `-DZERNYTH_JAVA_VENDOR=any`. Reguläre native Builds
starten Gradle nicht automatisch. Unbekannte Vendor-Werte werden abgewiesen.

Runtime-Konfiguration für eine später separat autorisierte Serverumgebung:
`ZERNYTH_JVM_LIBRARY` zeigt auf das vorhandene JDK-25-JVM-Modul,
`ZERNYTH_JAVA_CLASSPATH` enthält API-/Runtime-JAR 0.5.0. Die Runtime bündelt ihre
YAML-/JSON-Parser; die API bleibt ein separates Java-Artefakt ohne Drittanbieter-Abhängigkeiten. Java bleibt optional;
ohne Java-Opt-in benötigt der native Serverstart keine JVM. Bestehende Python-
und C++-Plugins bleiben Teil desselben Plugin-Managers. Diese Standardisierung
ändert keinerlei produktive Serverkonfiguration.

## Validierung und historische Dokumente

Lokale Build-/Testergebnisse stehen im
[Standardisierungsbericht](validation.md). Keine Behauptung einer
Temurin-Prüfung ohne tatsächlich vorhandenes Temurin; Linux und BDS sind eigene
Validierungen. JNI-Tests verwenden `JAVA_TOOL_OPTIONS=-Xcheck:jni`.

Alte Validierungsberichte sind historische Messprotokolle. Darin enthaltene
frühere Mindestversionen oder Rückport-Überlegungen sind keine aktuellen
Entwicklungsanforderungen. Für neue Arbeit gilt ausschließlich dieser Java-25-Standard.
