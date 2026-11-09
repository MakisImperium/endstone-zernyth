# Zernyth Engine 0.1.0 – experimentelle Java-Integration

**Aktuelle Entwicklung:** [Plugin Ecosystem und SDK 0.5.0](zernyth-ecosystem-0.5.md),
[Networking API 0.4.0](zernyth-network-0.4.md),
[Advanced Gameplay API 0.3.0](zernyth-advanced-0.3.md)
mit [Validierungsbericht 007](validation.md). [Gameplay Framework 0.2.0](zernyth-gameplay-0.2.md) ergänzt
Player, Events, Commands und Scheduler. Die folgende Beschreibung dokumentiert
die ursprüngliche 0.1.0-Integration; insbesondere sind die dortigen Aussagen über
noch fehlende Gameplay-APIs historisch.

Verbindlicher [Java-Standard](zernyth-java-25.md): **Java 25 LTS** als dauerhafte
Mindestversion und Entwicklungs-/Laufzeitbasis, bevorzugt **Eclipse Temurin 25**.
Gradle >= 9.1, Toolchain 25 und `options.release = 25` gelten für alle Module.
Die Invocation API verwendet `JNI_VERSION_24`, die höchste im JDK-25-Header
definierte JNI-Revision. Es gibt dort kein `JNI_VERSION_25`.
Ältere Java-Laufzeiten sind nicht mehr Ziel dieser Integration.

## Architektur

`EndstoneServer::loadPlugins` registriert bei Build- und Runtime-Opt-in den
`JavaPluginLoader` neben C++ und Python. Er liefert native `Plugin`-Adapter;
`EndstonePluginManager` übernimmt unverändert Namensprüfung, Abhängigkeiten
(auch zwischen Sprachen), Lade-Reihenfolge, Lifecycle und Ressourcenbereinigung.
Java-Konstruktoren werden erst in `onLoad` nach der Abhängigkeitsprüfung ausgeführt.
Pro Plugin gibt es einen URLClassLoader mit gemeinsamem API-Parent.
Abhängigkeiten bestimmen die Reihenfolge, erlauben derzeit aber keinen direkten
Klassenimport zwischen Plugin-JARs.

Die vorhandenen Erweiterungspunkte liegen in `plugin/plugin_manager.cpp`,
`plugin_loader.h`, `plugin.h`, `server.cpp`, `scheduler/scheduler.cpp` und
`command/command_map.cpp`. Events werden über `registerEvent`/`callEvent`, Tasks
über den bestehenden Scheduler verwaltet. Native BDS-Zugriffe bleiben im Core
(beispielsweise `player.cpp`) und den Runtime-Hooks. Diese erste Version exportiert
keine Player-, Command-, Event- oder Scheduler-API: dafür sind noch typisierte,
lebensdauergeprüfte Adapter nötig. Es gibt keine als fertig ausgegebenen Stubs.

## Bauen und aktivieren (nur nach ausdrücklicher Freigabe ausführen)

Voraussetzungen: bestehende Endstone-Buildumgebung, bevorzugt Eclipse Temurin 25 inklusive JNI-Headers,
lokal installiertes Gradle mit Java-25-Unterstützung. Kein Wrapper-Download.
`JAVA_HOME` muss auf das JDK zeigen. Die Gradle-Toolchain lädt kein JDK herunter.
Ohne Temurin ist eine bewusst dokumentierte lokale JDK-25-Prüfung mit
`-PzernythJavaVendor=any` möglich; die Java-Mindestversion bleibt 25.

* Native CMake-Konfiguration um `-DENDSTONE_ENABLE_JAVA=ON` ergänzen.
  Standard ist OFF. `FindJNI` bestimmt die Header anhand des JDK;
  die JVM-Bibliothek wird absichtlich nicht an den Core gelinkt.
* In `java/`: `gradle --offline --no-daemon :api:jar :runtime:jar :example:jar`.
  Alternativ explizites CMake-Ziel `zernyth_java`, falls Gradle gefunden wurde.
* `ZERNYTH_JAVA_ENABLED=1` setzen.
* `ZERNYTH_JVM_LIBRARY` auf den absoluten Pfad zu `jvm.dll`
  (`JAVA_HOME/bin/server/jvm.dll`) bzw. `libjvm.so`
  (`JAVA_HOME/lib/server/libjvm.so`) setzen. Architektur muss zum BDS passen.
* `ZERNYTH_JAVA_CLASSPATH` auf API- und Runtime-JAR setzen, getrennt mit `;`
  unter Windows bzw. `:` unter Linux. Beispiel: absolute Pfade zu
  `java/api/build/libs/api-0.1.0.jar` und `java/runtime/build/libs/runtime-0.1.0.jar`.
* `java/example/build/libs/example-0.1.0.jar` in den bestehenden `plugins`-Ordner kopieren.

Ohne Runtime-Opt-in wird kein Java-Loader registriert und keine JVM geladen.
Bei fehlerhafter Konfiguration werden Java-Kandidaten mit Diagnose abgewiesen;
die übrigen Loader bleiben aktiv. Ein fehlgeschlagener JVM-Start wird innerhalb
des Prozesses nicht wiederholt. Zur Konfigurationskorrektur Prozess neu starten.

## Metadaten und API-Vertrag

`plugin.yml` im JAR-Root: `name`, `version`, `main` sind erforderlich.
Optional: `depend`, `softdepend`, `loadbefore` als Inline-Listen (`[foo, bar]`).
Unterstützt sind flache YAML-Skalare mit optional einfachen/doppelten Anführungszeichen;
keine YAML-Escapes, Blocklisten, Anker oder Inline-Kommentare. Unbekannte Schlüssel,
Duplikate und Dateien über 64 KiB werden abgewiesen. Versionen sind nichtleer und
auf Buchstaben/Ziffern sowie `._+-` beschränkt. Endstones Namensregeln bleiben bestehen.

Öffentliche API: `JavaPlugin` mit Lifecycle, Logger und unveränderlichen Metadaten.
API-JAR ist ohne Runtime-JAR kompilierbar und über `maven-publish` veröffentlichbar;
Gradle-/Maven-Nutzer verwenden es als compileOnly/provided-Abhängigkeit.
`initialize` ist ein internes, einmaliges Runtime-Einfallstor der Basisklasse.

JNI: `describe`, Konstruktion und Lifecycle werden aus C++ aufgerufen;
`Bootstrap.log(long,String)` ruft Endstones Plugin-Logger auf. Handles sind
prozessweit monoton, keine Pointer. Sie werden beim Disable und bei Zerstörung
entzogen und beim erneuten Enable reaktiviert. Logger-Aufrufe und Lifecycle sind
nur auf dem Serverthread erlaubt; Threads werden bei Bedarf per RAII angebunden
und wieder getrennt. Java-Fehler erscheinen mit Stacktrace auf JVM-stderr und
Plugin-Kontext im nativen Log. Native Exceptions werden an der JNI-Grenze gefangen.

Lokale JNI-Referenzen liegen in RAII-Frames; Plugin-Globalrefs werden beim
Loaderabbau freigegeben und URLClassLoader geschlossen. JVM, Bibliotheksmodul
und Bootstrap-Globalref leben absichtlich bis zum Prozessende. Kein DestroyJavaVM
beim Plugin-Disable: Java-Threads könnten dessen Rückkehr unbegrenzt blockieren.
Plugins müssen eigene Threads selbst stoppen; keine Sandbox und kein erzwungenes
Thread-Unloading. Plugin-Logger ist derzeit ein einfacher Textadapter; Throwable
und JUL-Parameterformatierung sind noch nicht vollständig abgebildet.

## Validierung und nächster Meilenstein

Der ursprüngliche Implementierungsauftrag führte keine Builds oder Tests aus.
Den aktuellen Stand mit erfolgreichem vollständigem Windows-Build und 237
bestandenen nativen Tests dokumentiert [Validierung 003](validation.md).
Dieser Lauf umfasst auch die produktive JNI-Bridge mit Mock-Server.
Kein BDS-Prozess wurde gestartet.
Vorbereitet: `gradle --offline :runtime:selfTest` prüft eingeschränkte Metadaten
und einen isolierten Lifecycle ohne native Logger-Aufrufe. Das ist ausdrücklich
kein JNI- oder BDS-Test und testet keinen Klassenexport aus einem Plugin-JAR.

Nach Freigabe notwendig: native Builds auf Windows/Linux mit JDK 25; Beispiel-JAR
im echten BDS laden; Reihenfolge mit Python/C++ prüfen; Enable-/Disable-Exceptions,
fehlende/zyklische Dependencies, wiederholtes Enable, ungültige Handles,
fremde Threads, Reload und Shutdown prüfen. Den vom BDS verwendeten Allocator,
Signalhandler und JVM-Signalhandler auf Linux gemeinsam erproben.
Danach folgen typisierte Server-/Player-Handles, Commands, Event-Abonnements,
Scheduler, breitere YAML-Unterstützung und native Integrationstests.
