# Zernyth Engine

Java 25 LTS ist die dauerhafte offizielle Mindestversion und Entwicklungs-/Laufzeitbasis.
Eclipse Temurin 25 ist die bevorzugte Distribution. Alle Java-Module und Beispielplugins
verwenden Gradle-Toolchain 25 und `options.release = 25`; keine Vorschaufeatures.
Java-Versionen unter 25 werden nicht unterstützt. Die JNI-Revision 24 ist die höchste
im JDK-25-Header definierte Revision und senkt die Java-Mindestversion nicht.
Gradle benötigt mindestens Version 9.1. Fehlende JDKs niemals automatisch herunterladen.
Ohne lokales Temurin ist ein ausdrücklich dokumentierter Build mit vorhandenem JDK 25
und `-PzernythJavaVendor=any` erlaubt; keine Temurin-Validierung daraus ableiten.
Siehe [Java-Standard](docs/zernyth-java-25.md).

Netzwerk-Erweiterungen benutzen die vorhandenen Endstone-Packet-Events über dem
originalen BDS-Transport. Kein eigener NetherNet-/RakNet-/WebRTC-Stack. Der aktuelle
Java-Netzwerkvertrag ist read-only und auf Metadaten beschränkt; neue Decoder oder
Sende-/Änderungsoperationen benötigen geprüfte native Versions-, Threading- und
Ownership-Verträge. Capabilities müssen die tatsächlichen Grenzen wiedergeben.

Plugin-Abhängigkeiten und Aktivierung bleiben im nativen EndstonePluginManager.
Java-Imports/Exports steuern nur Klassensichtbarkeit. Services verwenden den nativen
ServiceManager mit widerrufbaren Proxys; lokale Libraries werden nie heruntergeladen.
Re-Enable verwendet eine neue Plugininstanz/ClassLoader-/Owner-Generation in derselben
JVM. Dateikonfigurationen werden nur explizit gespeichert, niemals automatisch beim Disable.

Managed CPU-Tasks und Tick-Pulse verwenden Endstones Scheduler. Java-I/O darf nur
mit begrenzten Virtual Threads laufen; native Gameplay-Threadregeln bleiben bestehen.
Cancellation ist kooperativ: laufende Tasks bleiben bis zum wirklichen Ende sichtbar.
Disable darf nicht auf Worker warten. Performance-Werte als Wall-Clock, Queue-Zeit
oder gemessene CPU-Zeit kennzeichnen; Optimierungen brauchen eine Ausgangsmessung.
JVM-weite JNI-Caches dürfen keine Plugin-ClassLoader oder Plugin-Klassen festhalten.
Siehe [Runtime-Vertrag 0.6](docs/zernyth-runtime-0.6.md).

Security-Regressionen verwenden synthetische Eingaben, feste Seeds und begrenzte
Ressourcen. Parser-Fuzzing führt keine Plugin-JARs aus. Sanitizer-Kompilierung ist
kein bestandener Sanitizer-Lauf; OS-Sicherheitsrichtlinien niemals umgehen.
Handle-Zähler dürfen nach Erschöpfung nicht umlaufen. Konfigurationsfehler dürfen
keinen teilweise veränderten Zustand hinterlassen. Siehe
[Security-Vertrag 0.8](docs/zernyth-security-0.8.md).

Release Candidates verwenden `zernyth-version.txt` als gemeinsame native/Java-Version.
API-, Runtime- und native Artefakte werden als passende Einheit installiert.
Quality Gates bleiben explizit PASS, FAIL, BLOCKED oder NOT TESTED; Mock-/JNI-Tests
ersetzen keine BDS-/Client-Tests. BDS darf nur bei nachgewiesener Netzwerkisolation
starten; Loopback-Signaling allein genügt nicht. Keine proprietären BDS-Dateien in
Distributionen. RC-CI bleibt manuell, ohne Release/Deployment und mit vorab lokal
bereitgestellten Offline-Abhängigkeiten. Siehe [RC-Leitfaden](docs/zernyth-release-0.9.md).

Zernyth-Formatierung verwendet ausschließlich `config/zernyth-format-files.json` und
`scripts/zernyth_quality.py`. Die dort festgehaltene Endstone-Baseline schützt originale
Dateien auch nach späteren Zernyth-Commits. Keine repositoryweite Formatierung;
bereits veränderte Upstream-Dateien nur nach Prüfung klar abgegrenzter eigener Hunks
bearbeiten. Für Auftrag 014 bleiben alle zuvor getrackten Dateien unverändert.
Öffentliche API-Javadoc wird mit Doclint und Werror geprüft. Performancevergleiche
müssen JVM-Parameter, Messbedingungen und Unsicherheit nennen; keine Überlegenheit
gegenüber anderen Servern aus Mock-Mikrobenchmarks ableiten.

Verbindlicher Java-Stil (Ergänzung zu Auftrag 014):
- Nur nachgewiesen Zernyth-eigene Dateien bzw. klar abgegrenzte eigene Erweiterungen
  bearbeiten. Originalen Endstone-Upstream weder formatieren noch refaktorieren.
- Package-Deklaration, Leerzeile, explizite Imports, Leerzeile, dokumentierte Typen.
  Normale Imports statt vollständig qualifizierter Typen in Signaturen/Implementierungen;
  notwendige Namenskonflikte, reflektive Klassennamen und Javadoc-Links sind Ausnahmen.
- Vier Leerzeichen, keine Tabs, sinnvolle Umbrüche bei 110 Zeichen, Leerzeilen zwischen
  Methoden und logischen Gruppen. Keine komprimierten Einzeilenmethoden. Öffentliche
  verschachtelte API-Typen nicht allein aus Stilgründen verschieben (Binärkompatibilität).
- IntelliJ formatiert ausschließlich die Java-Dateien des expliziten Manifests anhand
  der Zernyth-lokalen `.editorconfig`-Dateien. clang-format wird nur für eigene C++-
  Dateien benutzt; kein Java-Layout mehr durch clang-format überschreiben.
- Javadocs stehen direkt an der Deklaration und erklären Verhalten, Parameter,
  Rückgaben, relevante Fehler sowie Threading/Ownership/Lebensdauer. Keine generischen
  Platzhaltertexte oder bloße Wiederholung des Methodennamens. Überschriebene Methoden
  dürfen einen bereits dokumentierten API-Vertrag erben.
- Default-Methoden sind keine fehlenden produktiven Implementierungen. Vor einer
  Umstellung auf abstrakt alte Implementierungen/JARs prüfen. Mögliche API-/ABI-Brüche
  zuerst als konkreten Änderungsvorschlag dokumentieren und zur Freigabe vorlegen.
  Kompatibilitäts-Defaults klar dokumentieren und echte Produktions-Overrides testen.
- Verständliche lokale Namen, kleine Verantwortlichkeiten, keine künstlichen Wrapper,
  doppelten Implementierungen oder spekulativen Mikrooptimierungen. JNI-Komponenten
  dokumentieren Referenzbesitz, thread-lokale JNIEnv-Nutzung und Exception-Grenzen.
- Vor Abschluss Java-/C++-/JNI-Tests, Compiler-/Javadoc-Warnungen, Signaturen,
  Formatierung, geschützte Upstream-Hashes und vergleichbare Benchmarks prüfen.
  Blockierte oder nicht durchgeführte Prüfungen ausdrücklich ausweisen.

1. Java ist die bevorzugte Sprache für neue Plugin-APIs und übergeordnete Framework-Logik.
2. C++ bleibt für Endstone-Core-Integration, JNI und native BDS-Funktionen zuständig.
3. Bestehende Endstone-Funktionen werden erweitert und nicht unnötig neu implementiert.
4. Python- und C++-Kompatibilität bleibt erhalten.
5. Keine vollständigen Repository-Neuanalysen bei jeder Entwicklungsaufgabe.
6. Keine unnötigen Abhängigkeiten.
7. Keine automatischen Builds, Downloads, Serverstarts oder Deployments ohne ausdrückliche Freigabe.
8. Keine unkontrollierten Veränderungen am Hauptbranch oder an fremden Repositories.
9. Keine vorgetäuschten Testergebnisse.
10. Jede neue API benötigt definierte Fehlerbehandlung und Threading-Regeln.
11. Vorhandene Sicherheitsprüfungen und Schutzmechanismen bleiben bestehen.
12. Änderungen müssen modular, nachvollziehbar und wartbar sein.
