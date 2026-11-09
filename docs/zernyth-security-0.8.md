# Zernyth 0.8 — Sicherheits- und Lebensdauerverträge

Java 25 bleibt Mindestversion. Eine eingebettete JVM und ClassLoader-Isolation sind
keine Sandbox: Plugins besitzen weiterhin Prozessrechte. Kontingente beschränken
Framework-Arbeit, nicht beliebige direkte Allokationen, Threads oder Betriebssystemzugriffe.

## Handles

Opaque Owner-/Objektkennungen sind keine Pointer. Context-Lookup erfolgt vor jeder
nativen Operation; Worker werden vor Server-/Plugin-Dereferenzierung abgewiesen.
Objektkennungen werden nicht recycelt, sondern bei Erschöpfung dauerhaft abgewiesen.
Player-Views prüfen UUID und Actor-Runtime-ID. Nach einer neuen Sitzung mit derselben
UUID sind alte Views ungültig; auch Abfragen können dann IllegalStateException werfen.
UUID während gültiger Sitzung speichern und neue Online-Player auflösen, statt alte
Views nach Reconnect weiterzuverwenden. Dies ist eine beabsichtigte Verschärfung
der Lebensdauersemantik ohne Änderung der Java-Binärsignaturen.

World-/Entity- und Connection-Verträge bleiben separat: Entity/Connection verwenden
native Identität und Runtime-ID; Worlds prüfen aktuelle native Identität/Namen und
Invalidierungszustand. Daraus folgt keine neue allgemeine ABA-Garantie für Welten.
Echte BDS-Reconnect-/Dimension-/Unload-Zyklen müssen noch validiert werden.

## Lock-Reihenfolge und Callbacks

- Registry-Mutex nur für Context-Lookup/Invalidierung halten, vor Gameplay und
  Java-Callbacks freigeben. Serverthread-Prüfung kommt vor Server-Dereferenzierung.
- Native Job-Mutex nur zum Pinnen/Entfernen einer Callback-Referenz halten, vor
  `CallVoidMethod` freigeben. Worker dürfen mit gehaltenem Job-Mutex nicht auf
  ManagedRuntime.gate warten.
- ManagedRuntime.gate schützt Admission, Queue und Status. User-Code, Completion-
  Fortsetzungen, Interrupts und Warnlogger laufen außerhalb dieses Locks.
  Die Pulse-Registrierung unter dem Gate reiht nur in Endstones Scheduler ein;
  dort darf keine unmittelbare synchrone Callback-Ausführung eingeführt werden.
- Native Service-Token und Gameplay-Container sind serverthreadgebunden; Worker-
  Servicezugriffe sind abgewiesen, nicht als parallele Provider-Ausführung freigegeben.
- Logging besitzt einen separaten Owner-Registry-Lock zur Lebensdauerkoordination.
  Daraus keine neuen Aufrufe in die Context-Registry oder Plugin-Callbacks einführen.

Disable invalidiert den Context, bevor native Ressourcen abgebaut werden. Aktive
Callbacks pinnen ihre Java-Referenz; neue native Zugriffe alter Generationen scheitern.
Laufende, nicht kooperative Java-Tasks bleiben bis zum tatsächlichen Ende sichtbar
und können ClassLoader halten. Keine gewaltsame Beendigung und kein JVM-Neustart.

## Konfiguration und Eingaben

YAML/JSON-Dateien bleiben auf 1 MiB begrenzt. JSON-Schlüssel werden auch verschachtelt
auf Duplikate geprüft; YAML verwendet weiterhin den SafeConstructor ohne Aliase.
Manifest-/Default-Dateien werden strikt als UTF-8 dekodiert. Ungepaarte Surrogate
werden auch nach JSON-Escape-Dekodierung zurückgewiesen.

Configuration begrenzt eine Kopie auf 100000 Wertknoten, 10000 Einträge je Container,
50 Ebenen und 1048576 UTF-16-Codeeinheiten je String. Pfade haben höchstens 4096 Zeichen
und 50 Segmente; Prüfung erfolgt vor dem Regex. Fehlgeschlagene set-Aufrufe ändern
den Zustand nicht. Diese strengeren Grenzen können zuvor akzeptierte ungültige oder
überdimensionierte Daten zurückweisen. Konfigurationsänderungen sind keine Tick-Hotpath-API.

Serialisierung wird bereits beim Schreiben in einen begrenzten Speicher-Writer
abgebrochen; anschließend wird die bestehende UTF-8-Bytegrenze geprüft. Es erfolgt
kein Schreiben einer zu großen Konfiguration auf die Zieldatei. Atomare Dateiumbenennung
und vorhandene Pfad-/Link-/Reserviertnamenprüfungen bleiben erhalten.
Es wird keine vollständige Abwehr gegen externe Dateisystem-TOCTOU-Angreifer mit
gleichen OS-Rechten versprochen.

## Tests und Reproduktion

`:runtime:selfTest` führt SecurityTest aus: gerichtete Regressionen und 300 Iterationen
mit maximal 256 MiB Heap und zwei Minuten Task-Zeitlimit. Die Iterationen arbeiten
mit Seed 1208, jeweils höchstens 512 synthetischen Bytes für JSON/YAML/JAR-Parser.
Die JARs werden ausschließlich gelesen, niemals geladen/instanziiert. Temporäre
Dateien liegen unter `java/runtime/build/security-fuzz` und werden entfernt.
Dies ist begrenztes Smoke-Fuzzing, kein coverage-guided Fuzzer und kein Beweis
vollständiger Parser-Sicherheit.

`tests/security/BeforeSecurityProbe.java` lässt sich separat mit API/Runtime 0.7
kompilieren und ausführen, um dokumentierte Altfehler nachzuweisen; nicht in den
normalen 0.8-Testlauf einbauen. `ConfigBenchmark.java` wird einmal gegen API 0.7
kompiliert und in getrennten Prozessen gegen 0.7 und 0.8 ausgeführt; Parameter:
`-Xms128m -Xmx128m`, drei Warmup- und neun Messrunden mit je 1000 set-Aufrufen.

`tests/java_handle_sanitizer.cpp` testet die produktive Handle-Allokation mit vier
Threads und insgesamt 4000 Kennungen sowie dauerhafter Erschöpfung. Unter vorhandener
Windows-VC-Umgebung ist z.B. `clang-cl /std:c++20 /EHsc -fsanitize=address,undefined
/Isrc tests/java_handle_sanitizer.cpp /Febuild/security-012/handle_sanitizer.exe`
vorbereitet. Benötigte Runtime-Bibliotheken müssen bereits installiert sein.
Der Harness instrumentiert nur diesen Produktionshelper, nicht den gesamten Core.
OS-Blockierungen sind kein bestandener Sanitizer-Test und dürfen nicht umgangen werden.

JNI-Integration mit `-Xcheck:jni` prüft u.a. ungültige Owner/Player, Null-Arrays und
weitere gültige Aufrufe nach JNI-Fehlern. Ein bestandener JNI-Checker-Lauf beweist
keine vollständige Speichersicherheit. Eine neue JVM wird nur pro isoliertem
Testprozess gestartet; im Serverprozess bleibt es bei einer eingebetteten JVM.

[Tatsächliche Ergebnisse und verbleibende Risiken](validation.md).
