# Zernyth Java Gameplay Framework 0.2.0

Baseline: **Java 25 LTS**, bevorzugt Eclipse Temurin 25; verbindlich ist der
[Java-25-Standard](zernyth-java-25.md). Endstone 0.11.15 / API 0.11. Die vorhandenen Java-0.1-
Lifecycle-Plugins bleiben quellkompatibel. API und Runtime müssen gemeinsam
auf die 0.2.0-JARs aktualisiert werden. Kein zusätzlicher Server oder Plugin-Manager.

## Implementierte API

| Bereich | Native Verbindung |
|---|---|
| `Server.getOnlinePlayers()` | `Server::getOnlinePlayers`, UUID-basierte Player-Handles |
| Player Name, UUID, online | `Player::getName/getUniqueId`, erneutes `Server::getPlayer(UUID)` |
| Nachrichten, Kick, Permissions | `sendMessage`, `kick`, `hasPermission` |
| Location, Rotation, Teleport | `getLocation`, Level-/Dimension-Lookup, `teleport(Location)` |
| GameMode, Health | `getGameMode`, `setGameMode`, `getHealth` |
| Events | Native HandlerList / EndstonePluginManager, ein nativer Handler pro Java-Methode |
| Commands | Registrierung und Ausführung in EndstoneCommandMap; owned callback und Permission-Prüfung |
| Scheduler | Alle sechs nativen sync/async Task-Varianten, Einzel-/Plugin-Abbruch |

Unterstützte Events im Paket `dev.zernyth.api.event`: `PlayerJoinEvent`,
`PlayerQuitEvent`, `PlayerChatEvent`, `PlayerMoveEvent`, `PlayerInteractEvent`,
`BlockBreakEvent`, `BlockPlaceEvent`. Alle stellen den Player bereit. Chat bietet
native Nachrichtenänderung, Move unveränderliche From-/To-Snapshots, Interact
die native Action, Block-Events die Blockposition. Die fünf nativ abbrechbaren
Events implementieren `Cancellable`; Join/Quit bieten keine erfundene Cancellation.

`@EventHandler` verlangt eine öffentliche, nichtstatische void-Methode mit genau
einem unterstützten Eventparameter. Prioritäten entsprechen Endstones
LOWEST → LOW → NORMAL → HIGH → HIGHEST → MONITOR. `ignoreCancelled` wird vom
nativen Dispatcher geprüft. MONITOR-Schreibzugriffe werden abgewiesen.
Cancellation und Chatänderungen erfolgen unmittelbar am laufenden nativen Event,
auch wenn derselbe Listener anschließend wirft. Fehler eines Listeners verhindern
die Ausführung der übrigen nativen Listener nicht. Es gibt keinen zweiten Dispatcher.

## Beispiel

```java
import dev.zernyth.api.plugin.JavaPlugin;
import dev.zernyth.api.event.*;

public final class WelcomePlugin extends JavaPlugin implements Listener {
    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().registerCommand("welcome", "Show a welcome message", "welcome.use",
            (sender, command, args) -> {
                sender.sendMessage("Hello from Zernyth!");
                return true;
            });
        getServer().getScheduler().runTaskTimer(
            () -> getLogger().info("Server tick task"), 20, 200);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage("Welcome to Zernyth!");
    }
}
```

Plugin-JAR benötigt die bisherigen `name`, `version`, `main`-Schlüssel in
`plugin.yml`. Registrierung erfolgt in 0.2.0 **über Java-Code**; eine YAML-
Commandsektion wird nicht unterstützt. `registerCommand` nimmt Name, Beschreibung,
eine Permission (leerer String für keine) und `CommandExecutor` entgegen.
Ein belegter Name wird ausdrücklich abgewiesen. `Command.close()` entfernt nur
diesen Command einschließlich seiner nativen Registrierung. Argumente werden
unverändert vom nativen Executor als String-Array weitergegeben. PlayerSender,
ConsoleSender und andere native Sender werden unterschieden.

Endstones API besitzt keine Java-artige TabCompleter-Schnittstelle. Commandnamen
und die native Usage-Registrierung nutzen das bestehende BDS-Commandsystem;
eigene dynamische Java-Vorschläge, Aliases und strukturierte Argumentdefinitionen
werden in dieser Version nicht angeboten. Die anfängliche Usage ist ein optionales
`message`-Argument; im echten BDS bestimmen dessen Parserregeln die Argumentgrenzen.
Bereits verbundene Clients und die Aktualisierung ihrer Commandlisten wurden
in Auftrag 006 nicht getestet.

## Fehler, Threading und Lebensdauer

* Native Gameplay-Zugriffe, Registrierung, Abmeldung, Task-Submit und Task-Abbruch
  sind auf Endstones aktuellen Serverthread beschränkt. Ein Zugriff vom Worker
  wirft `IllegalStateException`, bevor ein BDS-Objekt dereferenziert wird.
* BDS kann zwischen onLoad und onEnable den Thread wechseln. Der Kontext wird
  beim Enable auf den aktuellen erlaubten Thread übertragen; alte Worker erhalten
  dadurch keine Berechtigung für Gameplay-Zugriffe.
* `runTask[Later/Timer]Async` führt Java-Runnables über den bestehenden Scheduler
  auf dessen Workern aus. Die JVM-Anbindung erfolgt per Daemon-Attachment und
  wird nach dem Callback wieder gelöst. Async-Code darf Java-Daten verarbeiten
  und den thread-sicheren Plugin-Logger verwenden. Er darf keine Player-, Event-,
  Sender-, Command- oder Scheduler-Operationen ausführen.
* Native Runnables und Event-/Commandcallbacks halten ihre Globalrefs nur für ihre
  definierte Lebensdauer. Bereits laufende Runnables werden durch Cancellation
  nicht unterbrochen; sie halten ihre eigene Callback-Referenz bis zur Rückkehr.
  Nach Disable werden neue native API-Aufrufe zurückgewiesen.
* Player-Handles enthalten ausschließlich eine UUID in der nativen Registry.
  Jeder Zugriff löst den aktuellen Player neu auf. `isOnline` liefert für eine
  bekannte, gerade offline befindliche UUID false; andere native Zugriffe werfen.
  Eine Player-View bezeichnet die Spieleridentität und kann nach einem Reconnect
  wieder den aktuellen Player derselben UUID auflösen. Sie hält keine alte Session
  und keinen nativen Player-Pointer am Leben.
* Event- und CommandSender-Handles laufen am Ende des individuellen Callbacks ab.
  Cancellation, Chat-Lesezugriffe und Senderoperationen über gespeicherte Views
  werfen danach. Immutable Location-Snapshots dürfen behalten werden. Das im
  Event enthaltene Player-Objekt folgt dem separaten UUID-Vertrag.
* Alle Gameplay-Handles laufen beim Disable ab. IDs werden monoton vergeben und
  nicht wiederverwendet. Ein erneutes Enable verwendet dieselbe JVM, aber einen
  neuen Ressourcenkontext; alte Player-/Task-Handles sind dann ungültig.
* `registerEvents` gibt eine `Registration` zurück. `close` oder
  `unregisterEvents(listener, owner)` entfernt native Handler und Java-Referenzen.
  Abmeldung aus einem Callback ist sicher: der Dispatcher hält einen Snapshot
  der aktuell ausgeführten Handler am Leben. Neue Registrierungen gelten für den
  nächsten Dispatch; abgemeldete Java-Handler werden sofort inaktiv.
* Beim Disable werden Java-Listener, Commands und Tasks freigegeben, auch nach
  teilweise fehlgeschlagenem Enable oder einer Exception aus onDisable. Zusätzlich
  bleibt Endstones normale Plugin-Ressourcenbereinigung aktiv.
* Task-Verwaltungsdatensätze und bekannte Player-UUIDs leben bis zum Disable.
  Java-Callback-Globalrefs fertiger Einmal-Tasks werden schon nach Ausführung
  freigegeben; dies ist keine dauerhafte Bindung an den Runnable/ClassLoader.
* Nullargumente erzeugen `NullPointerException`, ungültige Java-Argumente
  `IllegalArgumentException`; native Fehlzustände, unbekannte Dimensionen, fremde
  Threads und abgelaufene Handles erzeugen `IllegalStateException`.
  Exceptions aus Java-Callbacks werden mit Stacktrace protokolliert und isoliert.
  Die Logger-Anbindung bleibt ein einfacher Textadapter ohne vollständige JUL-
  Parameter-/Throwable-Formatierung.

## Bauen und Tests

Keine zusätzlichen Bibliotheken. Gradle lokal installiert; Offline-Build aus `java/`:

```text
gradle --offline --no-daemon :api:jar :runtime:jar :example:jar :runtime:integrationJar :runtime:failureIntegrationJar :runtime:selfTest
```

Native Konfiguration/Build wie in Auftrag 003, weiterhin `ENDSTONE_ENABLE_JAVA=ON`,
`JAVA_HOME` auf JDK 25. `ENDSTONE_GENERATE_STUBS=OFF` war beim lokalen Build gesetzt.
Java-Framework-Classpath verwendet jetzt `api-0.2.0.jar` und `runtime-0.2.0.jar`.

Für die optionalen produktiven JNI-GTests zusätzlich zu `ZERNYTH_JVM_LIBRARY`:

```bat
set "ZERNYTH_JAVA_CLASSPATH=%CD%\java\api\build\libs\api-0.2.0.jar;%CD%\java\runtime\build\libs\runtime-0.2.0.jar"
set "ZERNYTH_TEST_PLUGIN_JAR=%CD%\java\example\build\libs\example-0.2.0.jar"
set "ZERNYTH_GAMEPLAY_TEST_JAR=%CD%\java\runtime\build\libs\runtime-0.2.0-integration.jar"
set "ZERNYTH_GAMEPLAY_FAILURE_TEST_JAR=%CD%\java\runtime\build\libs\runtime-0.2.0-failure-integration.jar"
set "JAVA_TOOL_OPTIONS=-Xcheck:jni"
ctest --preset conan-relwithdebinfo --output-on-failure --parallel 4
```

Ohne diese Variablen werden die betreffenden optionalen Tests ausdrücklich
übersprungen. Die Fixture-JARs gehören nicht in einen normalen Serverbetrieb.
Der ursprüngliche HelloZernyth-Lifecycle bleibt als Beispiel erhalten; die neue
Gameplay-Fixture demonstriert und prüft die zusätzlichen Funktionen.

Die tatsächlichen Ergebnisse und Grenzen stehen im [Bericht zu Auftrag 006](validation.md).
