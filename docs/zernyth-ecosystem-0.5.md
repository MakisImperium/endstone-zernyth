# Zernyth Engine 0.5.0 – Java Plugin Ecosystem

Java 25 LTS ist die Mindestversion, Eclipse Temurin 25 die bevorzugte Distribution.
API, Runtime und Beispiel tragen Version 0.5.0. EndstonePluginManager und die
vorhandene JVM/JNI-Bridge bleiben die Lifecycle- und Dependency-Autorität.
Die [Validierung 009](validation.md) dokumentiert die tatsächlich
ausgeführten Builds und Tests. Kein BDS wird durch die hier beschriebenen Builds gestartet.

## Manifest und Abhängigkeiten

```yaml
name: economy
version: 1.0.0
main: dev.example.economy.EconomyPlugin
depend:
  - core_plugin
softdepend:
  - permissions
loadbefore: [minigames]
imports: [core_plugin]
exports: [dev.example.economy.api]
libraries: [database-driver-1.0.jar]
commands:
  balance:
    description: Show balance
    permission: economy.balance
    aliases: [bal]
    player-only: true
```

Es gelten weiterhin Endstones Namen: Kleinbuchstaben, Ziffern, Unterstrich;
keine reservierten Namen. Beispiele mit `ZernythEconomy` müssen entsprechend
`zernyth_economy` verwenden. Listen dürfen jetzt YAML-Blocklisten oder Inline-Listen
sein. Unbekannte Schlüssel, doppelte Schlüssel/Listeneinträge und ungültige Werte
werden abgewiesen. `plugin.yml` maximal 64 KiB. Numerisch wirkende Versionswerte
am besten zitieren, damit YAML-Typauflösung ihre Schreibweise nicht verändert.
Nicht unterstützte YAML-Objekttags und Collection-Aliase sind gesperrt.

`depend`, `softdepend`, `loadbefore` gehen weiterhin als Metadaten an den originalen
Endstone-Resolver. Keine Java-Graph-Engine. Kandidaten und Auswahl werden nach
Pluginname deterministisch verarbeitet. Doppelte Batch-Kandidaten werden abgewiesen;
fehlende Pflichtabhängigkeiten und harte Zyklen führen zu Diagnose und Ablehnung,
ohne unabhängige Plugins zu stoppen. Soft-Zyklen werden deterministisch aufgelöst.
Eine Pflichtabhängigkeit muss beim Enable bereits erfolgreich aktiviert sein.
Disable eines Providers deaktiviert zuerst aktive harte Dependents, auch bei
einzeln ausgelöstem Disable. Optional abhängige Plugins bleiben aktiv.

Versionen sind weiterhin opake Endstone-Versionsstrings. Es gibt kein verlässliches
SemVer-Modell für alle Java-/Python-/C++-Plugins; daher keine erfundenen Versionsranges.
`>=1.0` oder Maven-Koordinaten sind keine gültigen Dependency-Einträge.

## ClassLoader und lokale Libraries

Ein Plugin besitzt einen eigenen schließbaren URLClassLoader. Delegation:

1. Geschützte JDK-/Zernyth-API-/Runtime-Pakete an den stabilen Parent.
2. Eigene Klassen aus Plugin-JAR und expliziten lokalen Libraries.
3. Klassen ausdrücklich importierter, vom Provider exportierter Pakete.
4. Sonstige JDK-Plattformklassen; keine beliebige Suche in fremden Plugins.

Geschützt sind `java`, `javax`, `jdk`, `sun`, `com.sun`, `dev.zernyth.api` und
`dev.zernyth.runtime`. Mitgebrachte Klassen dieser Pakete werden zurückgewiesen.
`dev.zernyth.example` und bestehende Plugin-Pakete bleiben verwendbar. Die Main-Klasse
muss aus der Plugin-JAR selbst stammen. API niemals in ein Plugin schattieren.

`exports` benennt Paketpräfixe einschließlich ihrer Unterpakete. Nur diese Klassen
dürfen explizite `imports` anderer Plugins sehen. Ein Import muss zusätzlich als
hard/soft Dependency deklariert sein; Imports beeinflussen den nativen Graphen nicht.
Fehlt ein optionaler Provider, wird sein Import ausgelassen. Plugin-Code muss den
fehlenden optionalen Typ selbst korrekt behandeln. Harte Importe auf Nicht-Java-
Plugins funktionieren nicht; sprachübergreifende Lifecycle-Abhängigkeiten bleiben möglich.

Gemeinsame Service-Interfaces und zugehörige DTOs werden aus dem exportierenden
Loader geladen. Doppelte Klassen zwischen Haupt-JAR/Libraries, zwischen Importen
oder zwischen eigenen und importierten Klassen werden diagnostiziert. Mitgebrachte
Kopien zentraler API-Klassen werden vor Main-Instanziierung abgewiesen.

Libraries liegen ausschließlich in `<nativer Plugin-Datenordner>/libraries/`.
Die Manifestliste enthält lokale Dateinamen, keine URLs, Pfade oder Maven-Koordinaten.
Reihenfolge: Haupt-JAR, dann deklarierte Libraries. Doppelte Klassen werden nicht
durch eine zufällige Reihenfolge verdeckt. Diagnose zeigt Dateiname und SHA-256 als
eindeutigen Inhaltsnachweis; eine Maven-Version wird nicht aus Dateinamen geraten.
Kein Auto-Download, kein globaler Library-Classpath und kein Nachinstallieren.

Prüfungen: vorhandene reguläre JAR-Dateien, keine Link-/Reparse-Pfade, kein impliziter
Manifest-`Class-Path` oder JAR-Index, keine doppelten ZIP-Einträge/Traversaleinträge,
Classfile-Header bis Major 69 ohne Preview, keine Core-Kopien. Grenzen: 16 Libraries,
128 MiB komprimierte JAR-Gesamtgröße, 128 MiB deklarierte Expansion je JAR, 50000
Einträge je JAR, 10000 unterschiedliche Klassen je Plugin. Der JVM-Class-Verifier
prüft die tatsächlich geladenen Klassen zusätzlich. JAR-Validierung ist keine Signatur-/Supply-Chain-Prüfung.

Disable schließt JAR-Handles und entfernt Loader-Bindings sowie native GlobalRefs.
Re-Enable erzeugt eine **neue Plugininstanz, einen neuen ClassLoader und eine neue
native Owner-Generation**; die JVM bleibt dieselbe. `onLoad` wird für diese neue
Instanz erneut ausgeführt. Alte native Sichten und Service-Proxys werden nicht reaktiviert.
Keine In-Place-JAR-Hot-Reload-Garantie: bei Manifest-/Dependency-Änderungen neu starten
und neu entdecken lassen. URLClassLoader.close ist kein erzwungenes JVM-Unload;
weiter lebende Plugin-Threads, importierte Klassen oder eigene Referenzen können
Loader weiter im Heap halten. Solche Threads muss das Plugin selbst beenden.

ClassLoader-Isolation ist **keine vollständige Sicherheits-Sandbox**. In-Process-
Plugins können den JVM-Prozess beeinträchtigen; Reflection, endlose Schleifen,
System.exit oder externe Java-Dateizugriffe werden nicht durch diese API sandboxiert.

## Services

```java
// Interface in einem exportierten API-Paket des Providers:
public interface EconomyService { int balance(String account); }

// Provider, auf dem Serverthread in onEnable:
Registration registration = getServer().getServicesManager().register(
    EconomyService.class, account -> 0, ServicePriority.NORMAL);

// Consumer mit depend/softdepend + imports auf den Provider:
EconomyService economy = getServer().getServicesManager().load(EconomyService.class);
if (economy != null) {
    int balance = economy.balance("test");
}
```

`find` liefert Optional, `load` null bei fehlendem Provider, `getProviders` alle
passenden Provider in nativer Prioritätsreihenfolge. Die native EndstoneServiceManager-
Registry übernimmt Priorität/Ownership; kein zweites Java-Providerverzeichnis.
LOWEST bis HIGHEST entsprechen dem nativen Enum. Bei gleicher Priorität gilt
die bestehende native Reihenfolge, aktuell zuletzt registrierter Provider zuerst.
Bis zu 256 eigene Registrierungen pro Plugin. Registration.close entfernt die
eigene Registrierung, Disable entfernt alle automatisch.

Nur öffentliche Interfaces und kompatible gemeinsam geladene Klassen verwenden.
Eine gleichnamige Interface-Kopie aus einem anderen Loader führt zu einem klaren
ClassLoader-Mismatch-Fehler statt einer ungeprüften Rückgabe mit ClassCastException.

Consumer bekommen widerrufbare Java-Proxys. Vor jedem Service-Methodenaufruf prüft
die Produktionsbridge Caller, Provider, Aktivierung, Serverzuordnung und Thread.
Nach Deregistrierung/Provider-Disable wirft ein bereits gespeicherter Proxy
IllegalStateException; er wechselt nicht stillschweigend zu einem anderen Provider.
Neu suchen, um einen anderen Provider zu erhalten. Object.toString/hashCode/equals
des Proxys bleiben reine lokale Identitätsoperationen.

Registry-Zugriffe und Service-Aufrufe sind serverthreadgebunden. Auch ein Service,
der nur Datenbanken verwendet, erhält hier keine ungeprüfte Async-Ausnahme. Für
asynchrone Arbeit einen expliziten Java-Wertauftrag über den vorhandenen Scheduler
entwerfen; auf Workern keine Live-Services oder BDS-Objekte benutzen.
Fehler des Providers werden als Java-Exception an den Consumer weitergegeben.

## Konfiguration

```java
saveDefaultConfig();                         // config.yml nur anlegen, wenn sie fehlt
Configuration config = getConfig();          // cached; ohne Datei zunächst leer
String name = config.getString("server.name", "Zernyth Server");
int limit = config.getInt("server.max-players", 100);
config.set("server.max-players", 80);
saveConfig();                               // explizit schreiben
reloadConfig();                             // vollständig neu parsen, dann Werte ersetzen

Configuration database = getStorage().load("database.json");
getStorage().save("database.json");
```

YAML über SafeConstructor von SnakeYAML 2.4, JSON über Gson 2.14.0 im strikten Modus.
Beide bereits lokal vorhandenen Parser sind in der Runtime-JAR gebündelt, nicht im
API-Artefakt oder Plugin. Es gibt keinen neuen eigenen YAML-/JSON-Parser. Unterstützt
sind verschachtelte String-Key-Objekte, Listen, Strings, Boolean, finite Zahlen und null.
Benutzerdefinierte Objekttypen/Datumsobjekte werden nicht deserialisiert. Getter
prüfen Typen; getInt prüft Ganzzahligkeit und int-Bereich. Fehlende Werte verwenden
den angegebenen Default; getList(path, type) liefert bei fehlendem Wert eine leere Liste.

Punktnotation verwendet Segmente aus Buchstaben/Ziffern/`_`/`-`; literale Punkte
in YAML-Schlüsseln sind nicht über diese Pfadnotation adressierbar. Configuration
enthält nur Java-Werte: synchronisierte Änderungen, abgetrennte Rückgaben, keine
nativen Handles und kein implizites Speichern. Kopierte Werte dürfen auf Workern
verarbeitet werden; Dateizugriffe bleiben an Lifecycle-/Serverthread gebunden.

Dateien sind UTF-8, maximal 1 MiB, Verschachtelung maximal 50, Objekt-/Listenlängen
maximal 10000; YAML-Collection-Aliase sind gesperrt. Relative Unterverzeichnisse sind
erlaubt. Absolute Pfade, `..`, Backslash, NTFS-Streams/Colon, Geräte-Dateinamen und
Link-/Reparse-Pfade werden abgewiesen. Nur `.yml`, `.yaml`, `.json` sind Konfigurationen.
Nicht vorhandene Datenverzeichnisse werden erst bei explizitem Schreiben angelegt.

Speichern schreibt in eine temporäre Datei im Zielverzeichnis, danach atomarer
Rename mit Replace. Wenn das Dateisystem ATOMIC_MOVE nicht unterstützt, erfolgt
ein dokumentierter Replace-Fallback; kein Versprechen von Crash-/fsync-Durabilität.
Bei fehlgeschlagenem Reload bleiben bisherige Configuration-Werte unverändert,
bei Erfolg bleibt die Configuration-Instanz erhalten und ihre Werte werden ersetzt.
Keine automatische Überwachung und **kein Autosave beim Disable**. Explizites Speichern
im eigenen onDisable ist möglich, solange der Lifecycle läuft. Danach sind Dateizugriffe geschlossen.
Parserfehlermeldungen enthalten keine Konfigurations-Snippets; öffentliche Diagnostik
zeigt keine Konfigurationswerte. Dies ist keine OS-Sandbox und kein vollständiger
Schutz gegen Dateisystemrennen durch fremde Prozesse.

## Commands

Manifest-Commands werden vor onEnable über dieselbe native CommandMap registriert
und an `JavaPlugin.onCommand(sender, command, args)` weitergereicht. Aliase und
Root-Permission greifen ebenfalls nativ; `player-only` wird kontrolliert geprüft.
Ohne Override informiert der Default über den fehlenden Executor. Unbekannte Command-
Metadaten führen zu Enable-Fehler mit Ressourcen-Rollback.

Zusätzlich `Commands.register(server, CommandDefinition)` für immutable Bäume:
Subcommands/Unteraliase, Permission pro Knoten, Player-only, automatisch erzeugte
Hilfe, feste Argumentanzahl und STRING/INTEGER/DOUBLE/BOOLEAN-Validierung.
`CommandContext` liefert typed Getter und kopierte Argumente. Der Sender ist nur
während des nativen Command-Callbacks gültig. Fehler beim Aufbauen mehrerer Root-
Aliase schließen bereits registrierte Aliase wieder. Leaf-Knoten brauchen einen Executor.
Jeder Root/Alias wird in der bestehenden nativen CommandMap registriert; Disable
entfernt alle native Callbacks. Kein zusätzlicher Command-Dispatcher für BDS.

Die Java-Baum-Auswertung arbeitet innerhalb dieses registrierten Command-Callbacks.
Es gibt keine dynamische Java-Tab-Completion: Der bestehende native Command-Vertrag
bietet keine zuverlässige dynamische Completer-Funktion. BDS sieht weiterhin die
vorhandene generische Java-Command-Argumentschnittstelle, keine erfundene typed
Client-Completion oder vollständige Brigadier-/Paper-Kompatibilität.

## Diagnostik, SDK und IDE

`getDiagnostics()` liefert einen eigenen PluginDiagnostics-Wert: Status, Load-/Enable-/
Disable-Zeiten, gescannte Klassen, Library-Dateiname/Hash, Pflichtabhängigkeiten,
Event-/Packet-Listener, Commands/Aliase, aktive Tasks, Services und letzten Fehlertyp.
Load umfasst Java-Konstruktion/ClassLoader-Scan und onLoad; Zeiten sind Java-seitige
Messpunkte, keine vollständige native Profilierung/JVM-Startzeit. Fehler enthalten
den Exception-Typ, keine beliebigen Fehlermeldungen oder Config-Inhalte. Snapshot
auf Workern lesbar; Abruf erfolgt auf Lifecycle-/Serverthread. Klassenzahl ist die
gescannte Definitionenmenge, keine JVM-Heap-Zählung. Native Ressourcen werden nur
bei Abfrage gezählt, ohne neue Hintergrundthreads.

Das Beispiel bietet `/zernythdiag` mit Permission `zernyth.diagnostics` neben den
bisherigen Gameplay-/Netzwerkcommands. Die gesonderte
[Vorlage](../zernyth-plugin-template/README.md) demonstriert Default-Config und typed Command.

API-Artefakt: **`dev.zernyth:api:0.5.0`**, ohne Drittanbieter-Abhängigkeiten.
In `java/` nach lokaler Buildfreigabe:

```text
gradle --offline --no-daemon :api:publishApiPublicationToSdkRepository
```

Erzeugt nur `java/build/sdk-repository` im vorhandenen Fork: API-JAR, Sources-JAR,
Javadoc-JAR, POM und Gradle-Metadaten. Keine öffentliche Veröffentlichung.
Temurin 25 ist Standard; vorhandenes anderes JDK 25 mit `-PzernythJavaVendor=any`.
Die API-Javadoc-HTML liegt zusätzlich unter `java/api/build/docs/javadoc`.

Gradle-Consumer im vorhandenen Template:

```kotlin
repositories { maven { url = uri("../java/build/sdk-repository") } }
dependencies { compileOnly("dev.zernyth:api:0.5.0") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(25) }
```

Maven: `maven.compiler.release=25`, API-Scope `provided`, lokales file-Repository;
vollständiges Beispiel in `zernyth-plugin-template/pom.xml`. Keine Maven-Installation
oder Plugin-Downloads automatisch ausführen. Maven-Build ist separat zu validieren.

IntelliJ: Template als Gradle-Projekt öffnen, Projekt-SDK und Gradle-JVM auf Java 25
setzen, lokales API-Artefakt vorher erzeugen, Gradle offline ausführen. Bei Bedarf
SDK-JAR samt Sources/Javadocs im IDE-Dependency-Modell anzeigen. Das Plugin-JAR aus
`build/libs` erst in einer separat autorisierten Testumgebung installieren. Runtime
und API gehören in den JVM-Classpath, nicht zusätzlich geschattet ins Plugin.

## Lifecycle und Migration

`onLoad`: Java-Werte/Dateikonfiguration vorbereiten, keine aktiven Services/Tasks voraussetzen.
`onEnable`: Commands, Listener, Services, Tasks registrieren. `onDisable`: eigene Threads/
externen Verbindungen schließen, optional explizit Config speichern. Native Bereinigung
umfasst anschließend auch Forms, Packet-Listener, Services und Callbacks.
Ein Fehler in onLoad/onEnable/onDisable lässt keine aktiven nativen Registrierungen
des fehlgeschlagenen Plugins zurück. Fehler beim Neuaufbau eines geschlossenen
Loaders setzen den Pluginstatus ebenfalls wieder auf deaktiviert.

Binary-APIs aus 0.2–0.4 bleiben vorhanden; PluginDescription behält seinen
Sechs-Felder-Konstruktor. Neue Servermethoden besitzen kompatible Defaults für
fremde Implementierungen, die produktive Runtime überschreibt sie nativ.
**Bewusste Verhaltensänderung:** Re-Enable erhält keine alten Plugininstanz-Felder mehr.
Zustand bei Bedarf explizit speichern; keine statischen Plugininstanzen oder Server-
Wrapper nach Disable verwenden. Ältere mitgebündelte Core-API-Kopien werden jetzt
abgewiesen: API auf compileOnly/provided umstellen. Native harte Dependents werden
beim Provider-Disable automatisch zuerst deaktiviert. Netzwerk bleibt weiterhin
read-only wie 0.4; kein neues Packet Sending/Modification oder Async-Queue.
