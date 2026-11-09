# Zernyth 0.9.0-rc.1 — Build, Installation und Quality Gates

Dies ist ein Release Candidate, keine Stable- oder Produktionsfreigabe. BDS-Start
und Gameplay sind getrennte Gates. Ein bestandener Build, Import oder JNI-Mock-Test
beweist keinen Betrieb im echten Server.

## Versionen und Voraussetzungen

`zernyth-version.txt` ist die zentrale RC-Version. Gradle und der native Build lesen
dieselbe Datei. Native JVM-Initialisierung weist abweichende API-/Runtime-JAR-Versionen
ab, bevor Plugin-Code geladen wird. Bestehende Plugin-JARs müssen dagegen nicht die
Engine-Version tragen. Endstone-Basis/API bleiben 0.11.15/0.11; BDS-Testgrundlage ist
1.26.60.6, kein pauschaler Support anderer BDS-Versionen.

Vorhandene lokale Werkzeuge erforderlich: Clang 23 mit MSVC-Frontend und kompatibler
VS-/Windows-SDK-Umgebung auf Windows; Clang mit libc++ auf Linux; Ninja, CMake >=3.29,
Conan 2, Gradle >=9.1, JDK 25 und Python >=3.11 mit bestehenden Endstone-Abhängigkeiten.
Der lokale Windows-Nachweis verwendet Clang 23.1.4, MSVC-Header 14.51, CMake 4.3.1,
Gradle 9.8.2, Python 3.15.2 und Oracle JDK 25.0.5.0.3. Andere Kombinationen müssen
eigens getestet werden. Das Python-Modul benötigt dieselbe Major/Minor-ABI wie der Build.

Temurin 25 ist bevorzugt. Ohne installiertes Temurin kann vorhandenes Oracle JDK 25
mit `--vendor any` ausdrücklich gewählt werden. Dies validiert Temurin nicht.
JDK, Python, Microsoft-Runtime und BDS werden nicht automatisch installiert oder gebündelt.

## Offline-Build

Auf Windows aus einer bestehenden VS-Developer-Konsole mit clang-cl, Conan, CMake,
Ninja und Python auf PATH arbeiten. JAVA_HOME zeigt auf den gewählten JDK 25.
Auf Linux denselben Ablauf mit vorhandenen Clang-/libc++-/Python-Entwicklungspaketen
verwenden. Die vorhandenen Conan-Profile fordern x86_64, C++20 und auf Linux libc++.
Linux ist ohne dort ausgeführten Build weiterhin NOT TESTED.

```text
python scripts/zernyth_release.py build --gradle <vorhandener-gradle-Pfad>
python scripts/zernyth_release.py test
python scripts/zernyth_release.py stability --profile lifecycle
python scripts/zernyth_release.py regression
python -m unittest discover -s tests -p test_zernyth_release.py
python scripts/zernyth_release.py reproduce-java --gradle <vorhandener-gradle-Pfad>
python scripts/zernyth_release.py package
```

Für Oracle beim Build/Reproduzieren `--vendor any`, bei Bedarf überall
`--java-home <JDK25>` hinzufügen. Werkzeuge lassen sich mit `--cmake`, `--ctest`,
`--conan` angeben. Es sind Operatorparameter, keine eingebauten Entwicklerpfade.
`--jobs` begrenzt native Parallelität auf 1..8, Standard 4.

Conan verwendet `--no-remote --build=never`; fehlende Cache-Pakete führen zum Fehler,
nicht zu Downloads oder externen Builds. Der erste Lauf erzeugt einen Lockfile
unter `build/release-013/conan.lock`; Folgeläufe verwenden ihn. Er enthält konkrete
Recipe-Revisions. Zusätzlich wird ein lokaler Dependency-Graph erfasst. CMake-
FetchContent ist disconnected, Stub-Generator-Downloads sind deaktiviert.
Gradle läuft offline mit deaktiviertem Toolchain-Download. Versionsabgleich,
Java-25-Bytecode und Python-ABI werden geprüft.

Build- und Testberichte liegen lokal in `build/release-013/`. Sie können lokale
Dateipfade enthalten und werden nicht in öffentliche Releasearchive aufgenommen.
Dependency-Cache, Compiler, JDK und Lockfile müssen vor einem Fremdmaschinen-Build
gezielt bereitgestellt und geprüft werden. Kein Lockfile macht native Binärdateien
automatisch bitweise reproduzierbar.

## Paket und Integrität

Das ZIP enthält Native-/Python-Komponenten im vorhandenen Endstone-Package-Layout
unter `lib/python/endstone`, API/Runtime unter `java`, ein explizites Beispiel unter
`examples`, ein lokales Maven-SDK, eine Plugin-Vorlage, Dokumentation, Lizenzmaterial,
VERSION, BUILD.json, QUALITY-GATES.json und SHA256SUMS.

Keine proprietären BDS-Binärdateien, Welten, Credentials, Logs, JDKs oder PDB-Dateien
werden aufgenommen. Java-Parser- und vorhandene native Dependency-Lizenzen werden
mit Provenienz inventarisiert. Eine vollständige juristische Drittanbieter-Prüfung
bleibt ein eigenes offenes Release-Gate. BDS wird nur aus einer separat rechtmäßig
bereitgestellten Installation verwendet; dieses Paket erteilt dafür keine Lizenz.

```text
python scripts/zernyth_release.py verify --output build/<RC-Archiv>.zip
```

Der Verifier extrahiert/führt nichts aus. Er prüft Pfade, Symlinks, Duplikate,
Größenlimits, Pflichtdateien, Hashinventar, Build-/JAR-Versionen und API-Bytecode.
Ein externer `.zip.sha256`-Wert wird beim Erzeugen geschrieben. Mit einer separat
vertrauenswürdig bezogenen Prüfsumme vergleichen: Hashes sind keine Signaturen oder
Herkunftsgarantie. Das Archiv wird bei gleichem Stage-Inhalt zweimal erzeugt und
bytegleich verglichen. Native Cross-Machine-Reproduzierbarkeit ist ein anderes Gate;
RelWithDebInfo kann weiterhin Buildpfade in binären Debugreferenzen enthalten.

Der Packager importiert die gestagte Python-Extension in einem isolierten Python-
Kindprozess. Das bestätigt Import/ABI am Buildhost, keinen BDS-Start. Fehlende Dateien,
falsche ABI, gemischte JARs und instrumentierte Native-Builds werden abgewiesen.
Vorhandene Archive werden nicht überschrieben. ZIPs werden ausschließlich unter
dem Workspace-Buildverzeichnis erzeugt. Kein Release/Upload/Deployment wird ausgelöst.

## Manuelle Installation und Rollback

1. Archiv prüfen und in ein neues, versioniertes Verzeichnis entpacken.
2. Passende Python-Version, bereits vorhandene Python-Abhängigkeiten und JDK 25
   prüfen. Kein automatisches pip/Java/BDS-Setup ist Bestandteil der Installation.
3. Eine separat vorhandene, kompatible BDS-Installation verwenden. Vor Tests eine
   getrennte Serverkopie/neue Welt außerhalb produktiver Daten bereitstellen.
4. Konfigurationen und Plugin-Daten sichern. Endstone-Konfigurationen nicht blind
   ersetzen. Native-Komponenten und API/Runtime immer als Einheit wechseln.
5. Zunächst den Offline-Launcher nur inspizieren lassen:

```text
python <RC>/bin/run_existing.py --server <vorhandener-BDS-Ordner> --java-home <JDK25>
```

Er prüft Python-ABI, BDS-Dateiversion und JDK, startet standardmäßig nichts.
Windows-BDS-Versionsressourcen können gelesen werden; Linux-BDS-Erkennung/Start ist
hier noch nicht validiert und wird ohne nachgewiesene Version nicht freigegeben.
Ein späterer manueller `--run`-Aufruf nutzt ausschließlich Endstones vorhandenen
Launch-Backend und ruft weder Install/Update noch Config-Migration auf. Er ist keine
Isolationsprüfung: **auf diesem Entwicklungsrechner nicht als BDS-Test ausführen**, solange
die unten genannten Netzwerk-Blocker bestehen. Der Bediener muss seine Netzwerkrichtlinie
vor einem bewussten Start eigenständig sicherstellen. Dieser Launcher wurde nicht mit
echtem BDS-Start validiert.

HelloZernyth nur ausdrücklich in die `plugins`-Ablage der freigegebenen Testinstanz
kopieren. Nichts wird automatisch aktiviert. Prüfen: Core/JVM, JAR-Erkennung,
onLoad/onEnable/Logging und kontrolliertes stop/onDisable/Exitcode. Reale Python-
und C++-Plugins benötigen einen eigenen Nachweis. Erst danach Bedrock-Testclient
verbinden und jede Player-/Gameplay-/Forms-/Packet-Funktion einzeln protokollieren.

Update: Server kontrolliert stoppen, Konfiguration/Daten sichern, neuen RC daneben
entpacken, geprüfte Plugins/Konfiguration bewusst übernehmen, Versionseinheit prüfen.
Rollback: stoppen, vorherige komplette Distribution und gegebenenfalls gesicherte
Daten verwenden. Keine automatischen Welt-/Plugin-Datenänderungen, kein Hintergrund-Updater.

## BDS-Isolation: aktueller Blocker

```text
python scripts/zernyth_bds.py <vorhandener-BDS-Ordner> --prepare build/<neues-Testverzeichnis>
```

Dies liest Version/SHA256 und schreibt ausschließlich neue Testtemplates. Kein BDS,
keine Welten und keine produktiven Konfigurationen werden kopiert oder gestartet.
Historisch wurden trotz Loopback-Signaling zusätzliche Wildcard-UDP-Bindings auf 7551
und ausgehende Verbindungen beobachtet. Der bestehende Transport-Hook ändert UDP-
Port-/Mapping-Konfiguration, garantiert aber keine Bindung aller Sockets an Loopback.
STUN abschalten, Allowlist oder `server-ip=127.0.0.1` beweisen keine Isolation.

Ohne nachgewiesene isolierte VM/Netzwerkumgebung bleibt BDS BLOCKED. Keine Firewall-
Änderungen, Device-Guard-Änderungen, öffentlichen Freigaben oder neuen Netzwerk-Hooks.
Ein verbundenes kompatibles Bedrock-Testgerät ist zusätzlich nötig, um Gameplay als
PASS zu markieren. Ein Serverstart allein genügt dafür nicht.

## Stabilität, Performance und Sanitizer

Profile in `config/zernyth-validation-profiles.json`: smoke, lifecycle, bounded-stress,
observation. Maximal 50 zusätzliche Enable/Disable-Zyklen pro JVM, 600 Sekunden,
512 MiB Java-Heap und vier CPU-Affinitätskerne. Das CLI setzt Java-ProcessorCount,
CPU-Affinität und überwacht RSS alle 100 ms. Überschreitung beendet nur den eigenen
Testprozess. RSS-Monitoring ist kein kernelharter Speichergrenzwert. Observation
fügt begrenzte Pausen hinzu, ist aber kein stundenlanger BDS-Soak-Test.

Der vorhandene Benchmark-Harness misst Mock-JNI/Event-/Scheduler-/Lifecycle-Pfade.
Rohwerte sind Batch-Mittelwerte, keine BDS-TPS-/Spielerzahlgarantie. Nur gleiche JVM-
Parameter, Hardware und Workloads vergleichen. Reale Mainthread-/Packet-Kosten und
GC unter BDS-Last bleiben offen, solange kein realer Lasttest stattgefunden hat.

Linux-Instrumentierung ist mit `build --sanitizers address,undefined` vorbereitet.
Native Mock-Tests ohne JVM separat mit ASan/UBSan/Leak-Detection ausführen; JVM-
Prozesslebensdauerallokationen nicht als pauschalen Leak-Beweis interpretieren.
Der begrenzte Handle-Harness ist zusätzlich verfügbar. Instrumentierte Builds
werden nicht als RC gepackt. Windows-Device-Guard-Blockaden niemals umgehen.

## CI und Kompatibilität

`.github/workflows/zernyth-rc.yml` ist ausschließlich manuell auslösbar und wurde
für diesen Auftrag nicht gestartet. Dedizierte selbst gehostete Runner müssen alle
Tools/Conan-/Gradle-Caches, Oracle/Temurin und Windows-VC-Umgebung bereits besitzen.
Nicht die persönliche Arbeitskopie als CI-Checkout verwenden. Runnerlabels sind
Provisionierungsverträge, kein Nachweis vorhandener Runner.

Vorbereitete Jobs: Java/Fuzz/Unit, Windows/Linux jeweils Oracle/Temurin, JNI/native
Tests, Lifecycle, Packaging, Syntax-/Integritätschecks und Linux-Sanitizer. Minimale
Repository-Berechtigung `contents: read`, keine Secrets im YAML, keine Uploads,
Releases oder Deployments. Source-Checkout über GitHub benötigt bei einer später
freigegebenen Ausführung Netzwerk sowie die referenzierte Checkout-Action v6.
Build-Abhängigkeiten bleiben offline. Immutable Action-SHA-/Runner-Image-Prüfung
und tatsächliche CI-Ausführung bleiben vor produktivem CI-Einsatz zu erledigen.

| Kombination/Gate | Kennzeichnung |
|---|---|
| Windows + Oracle 25, lokale Build-/JNI-Prüfung | Nach tatsächlich ausgeführtem Bericht bewerten |
| Windows + Temurin 25 | NOT TESTED, lokal nicht installiert |
| Linux + Oracle/Temurin 25 | NOT TESTED, keine lokale Linux-Umgebung nachgewiesen |
| BDS 1.26.60.6 Datei | Version geprüft; Runtime BLOCKED durch Isolation |
| Andere BDS-Versionen | Unsupported durch diesen RC-Vertrag |
| Echte Gameplay-/Client-Interaktion | NOT TESTED |
| CI-Ausführung und vollständige Sanitizer-Validierung | NOT TESTED/BLOCKED |
| Stable-/Produktionsfreigabe | BLOCKED |

Keine vollständige Bukkit-/Paper-Kompatibilität, keine generische Packet-Manipulation,
keine native dynamische Client-Tab-Completion und keine Prozess-Sandbox für Plugins.
Die vorhandenen API-, Threading- und Security-Leitfäden gelten weiter.

## SDK aus der Distribution

Die Plugin-Vorlage unter `examples/plugin-template` verwendet API `0.9.0-rc.1`.
In diesem Ordner in IntelliJ/Gradle JDK 25 wählen und den mitgelieferten Dateirepository
angeben: `gradle --offline -PzernythSdkRepository=../../sdk-repository build`.
Ohne Temurin ist der bekannte `-PzernythJavaVendor=any`-Override möglich. Maven kann
entsprechend `-Dzernyth.sdk.repository=file:///.../sdk-repository` nutzen, aber nur
mit bereits vorhandenen Maven-Plugins; eine Maven-Ausführung wurde nicht nachgewiesen.
API als compileOnly/provided verwenden, nicht mit ins Plugin schattieren.

CI muss CONAN_HOME und GRADLE_USER_HOME außerhalb des von Checkout bereinigten
Quellverzeichnisses vorprovisionieren. Der Buildtreiber weist ein fehlendes oder
innerhalb der Arbeitskopie liegendes CONAN_HOME im CI-Modus ab. Keine Cache-Downloads.
Die CI-Konfiguration selbst wurde nicht ausgelöst.
Für Regressionen müssen die vertrauenswürdig vorprovisionierten eigenen Beispiel-
JARs 0.2..0.8 verfügbar sein. Optional `ZERNYTH_LEGACY_JARS` auf ein eigenes Archiv
außerhalb des Checkouts setzen. Fehlende Archive sind BLOCKED, kein stiller Skip;
keine fremden Plugins oder automatischen Artefakt-Downloads einsetzen.

In der aktuellen lokalen Prüfung konnten die vorhandenen instrumentierten Handle-
Helper unter ASan/UBSan ausgeführt werden. Das ist keine Voll-Core-Instrumentierung.
Die installierte Windows-ASan-Runtime meldet Leak-Detection als nicht unterstützt;
Address-/UB-Prüfung ohne diesen nicht verfügbaren Leak-Modus ist separat zu bewerten.
Linux-Leak-/Core-Prüfung bleibt NOT TESTED. Kein Abschalten von Sicherheitsrichtlinien.
