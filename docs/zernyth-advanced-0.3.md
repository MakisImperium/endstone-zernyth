# Zernyth Engine 0.3.0 – Advanced Gameplay API

Die Erweiterung verwendet den vorhandenen JavaPluginLoader im EndstonePluginManager,
dieselbe JVM, dieselben Plugin-Kontexte und Endstones native öffentliche Schnittstellen.
Python- und C++-Loader bleiben erhalten. Es gibt keinen zweiten Plugin-Manager,
Event-Dispatcher oder Scheduler. Der [Bericht 007](validation.md) trennt
Mock-/JNI-Tests von einer tatsächlichen BDS-Ausführung.

## Implementierte API und native Zuordnung

| Java | Bestehende native Endstone-Schnittstellen | Verhalten |
|---|---|---|
| `Player.getInventory/getEnderChest` → `Inventory` | `Player::getInventory/getEnderChest`, `Inventory` | Größe, Stacklimit, einzelne Slots, Inhalte, setzen, hinzufügen/entfernen mit Restmengen nach Eingabeindex, `contains`, leeren |
| `Server.createItemStack` → `ItemStack` | `Registry<ItemType>`, `ItemType::createItemStack`, `ItemStack`, `ItemMeta`, `Enchantment` | Namespaced Typ, Menge, native Stack-/Haltbarkeitslimits, Data-Wert, Name, Lore, Damage, Unbreakable, Enchantments; unveränderliche Kopien und Vergleiche |
| `Server.getWorlds` → `World` | `Server::getLevel`, `Level::getName/getDimensions/getActors` | Aktueller Level als Welt, Kennung, Dimensionen und Entity-Snapshots |
| `Dimension` | `Dimension::getLoadedChunks/getBlockAt/spawnActor` | Geladene Chunkpositionen, Loaded-Prüfung, Blockansicht und natives Entity-Spawning |
| `Block/BlockData` | `Block::getData/setData/getState`, `Server::createBlockData` | Typ und String/Integer/Boolean-Zustände, Änderung mit explizitem Physics-Flag, Nachbarn |
| `BlockState` | `BlockState::getData/setData/update` | Native Zustandsaufnahme, Bearbeitung, Update mit force/physics, explizites `close` |
| `Entity` | `Actor::getId/getRuntimeId/getType/isValid/isDead/getLocation/teleport/setRotation/getVelocity/getNameTag/setNameTag/isOnGround/isInWater/isInLava/remove/asMob/asPlayer`; `Mob` Health | Lebensdauergeprüfte Ansichten, Rotation, Teleport, Name, Zustände, Health soweit Mob, Spielerzuordnung, Entfernung ohne Player-Remove |
| `ActionForm/MessageForm/ModalForm` | `endstone::ActionForm/MessageForm/ModalForm`, `Player::sendForm` | Buttons mit optionalem Bild, zwei Entscheidungen, TextField/Toggle/Slider/Dropdown/StepSlider; validierte asynchrone Antwort |

Inventare entsprechen den vorhandenen nativen Slots. Unterstützt sind Spielerinventar
und Ender Chest, keine frei konstruierten Container oder zusätzliche Rüstungs-/Offhand-
Slotbelegung. `null` bedeutet leerer Slot; `setContents` übernimmt native Semantik,
und Mengen sind strikt 1 bis zum nativen Itemlimit. AIR wird als Item abgewiesen.
Alle Elemente eines Schreib-Batches werden vor der ersten Mutation validiert.
Eine atomare Transaktion bei einem späteren nativen Fehler wird nicht zugesichert.

`ItemStack` und `ItemMeta` sind immutable Java-Werte ohne native Handles. `copy`,
`withAmount`, `withItemMeta`, `equals` und `isSimilar` erhalten unbekanntes natives
User-NBT. `equals` berücksichtigt die Menge; `isSimilar` vergleicht Typ, Data und
Metadaten einschließlich unbekanntem NBT. Namen/Lore/Damage/Enchantments werden aus
dem opaken NBT normalisiert, damit native Roundtrips dem Java-Vergleich entsprechen.
NBT-Typen einschließlich Long, Float, Double, Listen und Arrays bleiben erhalten.
Das interne `ItemStack.read/write`-Transportformat ist kein Persistenzvertrag;
beliebige NBT-Manipulation wird nicht als öffentliche Gameplay-Funktion angeboten.
Beim Anwenden erfolgt erneut native Registry-, Mengen- und Metadatenvalidierung.

## Lebensdauer, Fehler und Threads

Alle nativen Gameplay-Zugriffe benötigen den Serverthread und den lebenden
Plugin-Kontext. Auch reine native Lesezugriffe sind auf Workern verboten.
Value-Objekte und bereits kopierte Antworten dürfen auf Workern verarbeitet werden.
Das Logging und der vorhandene Scheduler behalten ihre bisherigen Regeln.
Native Fehler, ungültige Handles, falscher Thread und entladene Referenzen werden
als `IllegalStateException` zurückgegeben. Java-Wertvalidierung verwendet
`IllegalArgumentException`/`NullPointerException`, Positionsüberlauf
`ArithmeticException`. Auch Slotgrenzen werden vor dem nativen Containerzugriff geprüft.
Die Package-Javadocs definieren diese Verträge direkt an der API.

Player bleiben UUID-basiert. Weltansichten prüfen aktuellen Level, Identität und Namen
vor jedem nativen Zugriff. Eine beobachtete Entladung macht den alten Token dauerhaft
ungültig. Entity-Ansichten speichern Actor-ID plus Runtime-ID, niemals Actor-Pointer;
der Actor wird jedes Mal über den lebenden Level aufgelöst. Native ActorRemove- und
PlayerQuit-Events invalidieren vorhandene Ansichten. Tokens werden nicht wiederverwendet;
inaktive Entity-Buchführung wird bei nachfolgender Auflösung neuer Ansichten entfernt.
`Entity.isValid()` liefert bei veralteter Entity false, weitere Zugriffe schlagen fehl.
Alle Ressourcen gehören einem Plugin und werden bei Disable freigegeben.

`Dimension.getBlock` liefert bei ungeladenem Chunk `Optional.empty`. Ein späterer
Blockzugriff prüft den Chunk erneut und schlägt bei inzwischen entladenem Chunk fehl.
Lesen, Schreiben, State-Update, Entity-Teleport und Spawning lösen kein implizites
Chunk-Laden aus. Negative Koordinaten werden mit Floor-Division behandelt.
Die Prüfung enumeriert aktuell die native Liste geladener Chunks, also O(n).
`BlockState` mit try-with-resources schließen; Disable bereinigt verbleibende Snapshots.

## Forms

```java
var form = new ActionForm("Zernyth", "Choose an option",
    List.of(new ActionForm.Button("Hello")));
FormRequest request = player.showForm(form);
request.response().thenAccept(answer -> {
    if (answer.status() == FormResponse.Status.SUBMITTED) {
        player.sendMessage("Selected: " + answer.values().getFirst());
    }
});
```

`response()` liefert eine `CompletionStage`, keinen blockierenden Ablauf. Niemals
auf dem Serverthread `join/get` auf eine ausstehende Antwort anwenden. Callbacks
laufen auf dem Serverthread; `thenAcceptAsync` darf nur Werte verarbeiten oder
über den bestehenden Scheduler eine synchrone Fortsetzung planen.

Action/Message liefern einen nullbasierten Integer-Index. Modal liefert die Werte
in Control-Reihenfolge: String, Boolean, Double, Integer, Integer. Anzahl, Typ,
Buttonindex, Sliderbereich und Schritt werden validiert, einschließlich Integer-
Überläufen. Status sind `SUBMITTED`, `CLOSED`, `DISCONNECTED`, `PLUGIN_DISABLED`,
`INVALID_RESPONSE`, `CANCELLED`. Falscher Spieler, falscher Callback-Thread,
doppelte und verspätete Antworten werden ignoriert. Beim Disconnect sind neue
Formulare bis zum nächsten Join gesperrt, auch innerhalb des Completion-Callbacks.

`cancel` entfernt nur den eigenen Callback und schließt nicht alle Spieler-Forms.
Endstone bietet keine Entfernung genau einer bereits gesendeten nativen Form-Anfrage.
Eine schwache native Closure kann deshalb bis Antwort/Schließen/Disconnect bestehen
bleiben; die Java-GlobalRef und die aktive Bridge-Anfrage werden sofort entfernt.
Disable eines Plugins beeinträchtigt offene Anfragen anderer Plugins nicht.
Es werden keine eigenen Netzwerkpakete implementiert.

Grenzen: 1024 offene Anfragen je Plugin, 32 je Spieler/Plugin, 256 Controls/Buttons,
8 MiB Bridge-Payload, 4 MiB je Textfeld im Transport, 1 MiB Form-Antwort,
65536 Einträge je generischer Transportliste, NBT-Verschachtelung höchstens 64.
Ungültige Eingaben werden abgewiesen; diese Limits sind keine BDS-Protokollgarantie.

## Build und Verwendung

Offizielle Mindestversion und Entwicklungs-/Laufzeitbasis ist dauerhaft **Java 25 LTS**,
bevorzugt **Eclipse Temurin 25**. Toolchain und Release sind 25, Bytecode-Major 69;
`JNI_VERSION_24` bleibt die im JDK 25 verfügbare Invocation-Revision.
Ältere Java-Versionen werden nicht unterstützt. Verbindliche Einrichtung und
Distributionsauswahl: [Java-25-Standard](zernyth-java-25.md).

Vorhandenes CMake-Preset `conan-relwithdebinfo`, `ENDSTONE_ENABLE_JAVA=ON`,
JNI-Headers aus `JAVA_HOME`; `jvm.dll`/`libjvm.so` wird weiterhin dynamisch geladen.
Gradle baut die API ohne native Runtime-Abhängigkeit, Runtime und Plugin als getrennte JARs.
Es wurden keine zusätzlichen Bibliotheken oder Downloads eingeführt.
Gradle >= 9.1 und ein lokal installiertes Temurin 25 verwenden. Ohne Temurin ist
`-PzernythJavaVendor=any` ein ausdrücklich zu dokumentierender lokaler JDK-25-Fallback.

```text
gradle --offline --no-daemon :api:jar :runtime:jar :example:jar
gradle --offline --no-daemon :runtime:integrationJar :runtime:failureIntegrationJar :runtime:advancedIntegrationJar :runtime:advancedPeerIntegrationJar :runtime:selfTest
cmake --build --preset conan-relwithdebinfo --parallel 4
ctest --preset conan-relwithdebinfo --output-on-failure --parallel 4
```

Die Tests benötigen `ZERNYTH_JVM_LIBRARY`, API/Runtime im
`ZERNYTH_JAVA_CLASSPATH` sowie die Fixture-Pfade `ZERNYTH_TEST_PLUGIN_JAR`,
`ZERNYTH_GAMEPLAY_TEST_JAR`, `ZERNYTH_GAMEPLAY_FAILURE_TEST_JAR`,
`ZERNYTH_ADVANCED_TEST_JAR`, `ZERNYTH_ADVANCED_PEER_TEST_JAR`.
Fehlende Testvoraussetzungen führen zum Skip; daher immer die tatsächlichen Ergebnisse prüfen.
Für JNI-Tests `JAVA_TOOL_OPTIONS=-Xcheck:jni` setzen. JVM-Tests laufen separat/seriell.

Für eine später separat freigegebene Servervalidierung verwenden:
`api-0.3.0.jar`, `runtime-0.3.0.jar` im Runtime-Classpath und
`example-0.3.0.jar` im bestehenden Plugins-Verzeichnis; übrige JVM-Konfiguration
wie in [zernyth-java.md](zernyth-java.md). **In Auftrag 007 wird kein Server gestartet.**
Alte Smoke-Skripte und historische 0.1/0.2-Pfade sind keine aktuelle 0.3-Testfreigabe.

Das Beispiel registriert `/zernythdemo` mit Permission `zernyth.demo`.
Nur der explizite Spielercommand liest Inventar/Welt/Block/Entity, gibt einen
Diamanten über das native Inventar und zeigt eine ActionForm. Enable verändert
keine Welt und vergibt keine Items. Der Command darf deshalb nur bewusst in
einer Testumgebung verwendet werden.

## Grenzen und nächster Meilenstein

Kein künstliches Multiworld-System: Endstone stellt aktuell einen Level mit
Dimensionen bereit. Keine Java-Welterzeugung/-entladung, kein Chunk-Laden,
keine Entity-UUID-Erfindung, kein Velocity-Setter ohne native Schnittstelle,
keine beliebigen Container oder Netzwerkpakete. Health ist auf Mob beschränkt.
Die neuen Server-/Player-Methoden sind kompatible Interface-Defaults für fremde
Implementierungen; die produktive Zernyth-Runtime überschreibt alle mit nativen
Adaptern. Fremde Implementierungen ohne Erweiterung werfen ausdrücklich UnsupportedOperationException.

Nächster Meilenstein: separat freigegebener isolierter BDS-/Bedrock-Client-Test
für Inventarsynchronisierung, reale Items/Enchantments/NBT, Dimensionsgrenzen,
Entity-Lebensdauer und alle drei Forms. Anschließend Linux-CI mit Temurin 25.
Keine dieser Prüfungen wird durch
den erfolgreichen Mock-/JNI-Test ersetzt.
