# Zernyth Engine 0.4.0 – native Packet-Metadaten

Java 25 LTS, bevorzugt Eclipse Temurin 25. Die Erweiterung verwendet denselben
PluginManager, dieselbe JVM und die bereits vorhandenen Endstone-Packet-Events.
Sie beobachtet **Metadaten serialisierter Bedrock-Pakete**, keine dekodierten
Gameplay-Felder und keine WebRTC-Interna. Sie implementiert keinen Netzwerktransport.
Testbefunde: [Validierungsbericht 008](validation.md).

## Tatsächliche native Integrationspunkte

| Ebene | Vorhandene Dateien/Schnittstellen | Verwendung in 0.4 |
|---|---|---|
| NetherNet | `src/bedrock/deps/nethernet/nethernet_transport.h`, `http_server.h`, `http_signaling_server.h`, `simple_network_interface_impl.h` | Untersuchung vorhandener HTTP-Signaling-, WebRTC-Socket- und UDP-Konfiguration; kein Java-Zugriff auf Transportgeheimnisse |
| Transport-Hooks | `src/endstone/runtime/bedrock_hooks/nethernet_transport.cpp`, `simple_network_interface_impl.cpp` | Bestehende Implementierung bleibt unverändert; keine neuen ICE/STUN/TURN/DTLS- oder Socket-Funktionen |
| BDS-Verbindung | `runtime/bedrock_hooks/network_system.cpp`, `NetworkSystem::onNewIncomingConnection` | Bereits vorhandener Hook ruft BDS-Original auf und setzt Endstones Peers in die vorhandene Peer-Kette; nicht durch Java ersetzt |
| Paketpfad | `core/network/batched_network_peer.cpp` | Bestehende PacketReceive-/PacketSend-Events werden benutzt; keine zweiten Hooks/Events |
| Eingehend | `EndstoneBatchedNetworkPeer::_receivePacket` | Nach Entpacken/Lesen des Headers, vor normaler nativer Packet-Deserialisierung: nur Header-ID, Subclient und Payload-Länge |
| Ausgehend | `EndstoneBatchedNetworkPeer::sendPacket` | Bereits serialisiert, nach vorhandenen Endstone-Patches und vor Batch-Schreiben; Zustand am jeweiligen Listener-Prioritätspunkt |
| Dekodierte Pakete | `runtime/bedrock_hooks/packet.cpp`, `MinecraftPackets::createPacket` und `PlayerPacketHandler` | Intern selektierte native Gameplay-Hooks, kein allgemeines öffentliches dekodiertes Packet-Event; daher keine Java-Felddecoder |
| Version | `Server::getMinecraftVersion/getProtocolVersion`, `src/bedrock/shared_constants.h` | Native Meldung `26.60` / `2225`; versionierte Metadaten-Capability |
| Spielerlebensdauer | bestehende `PlayerJoinEvent`/`PlayerQuitEvent`, UUID, Actor-/Runtime-ID | Invalidierung pluginbezogener Connection-Ansichten; keine duplizierten Join-/Disconnect-Events |

Im vorhandenen Quellcode stehen HTTP-Signaling-Konfiguration, WebRTC-Sockets und
UDP-Portkonfiguration für NetherNet. Daneben existieren ältere RakNet-Typen.
Aus dem Vorhandensein dieser Klassen folgt keine Aussage über den tatsächlich
verwendeten Transport jeder Verbindung. Die Java-API liegt darüber und meldet
keinen erfundenen Transportstatus. Es wurden keine Transportparameter geändert.

Der native öffentliche Server meldet nur `26.60`, nicht den vollständigen Build.
Die vom Auftrag genannte Grundlage **1.26.60.6** ist außerdem im historischen
Bericht 004 festgehalten, wurde in Auftrag 008 aber nicht neu gestartet/geprüft.
Der Metadatenadapter benötigt gemeldete Release `26.60`, Protokoll `2225` und
die dazu passenden Compile-Konstanten. Andere Werte deaktivieren die Capability
und verhindern Registrierung sowie Callbacks bestehender Listener. Das beweist
keine Kompatibilität beliebiger Hotfix-Builds oder dekodierter Paketstrukturen.

## Öffentliche API

Unter `dev.zernyth.api.network`:

* `NetworkManager`: Protokoll, Capabilities, benannte native PacketTypes, Listener,
  Player-Connection-Ansichten und Diagnostik.
* `NetworkProtocol`: gemeldete Release und Protokoll-ID als Werte.
* `Packet`, `PacketSnapshot`: immutable Typ, Richtung, Protokoll, Kontext und Payload-Länge.
* `PacketType`: Name, native ID und zugehörige Protokollversion.
* `PacketDirection`: INBOUND/OUTBOUND.
* `PacketFilter`: optionale Typmenge und nichtleere Richtungsmenge.
* `PacketContext`: optionale Spieler-UUID und pluginbezogene Connection-Kennung,
  Beobachtungszeitpunkt, Phase, Subclient und beobachteter Cancellation-Zustand.
* `PacketListener`: synchroner Callback ohne Rückgabewert oder Mutation.
* `NetworkConnection`, `connection.ConnectionInfo/ConnectionState`: begrenzte
  Player-Zuordnung und Aktivitätsabfrage, keine native Transport-Session.
* `NetworkMetrics`: Zustellungs-/Fehlerzähler und optionale Laufzeitmessungen.
* `exception.UnsupportedPacketException`: Registrierung mit inkompatiblem
  Protokoll oder Filter.

`EventPriority` und `Registration` werden aus der vorhandenen Event-API übernommen.
Keine zusätzlichen PacketPriority-, CancellableEvent- oder Dispatcher-Abstraktionen.
Native Fehler, fremde/abgelaufene Owner-Kontexte und falsche Threads werfen
`IllegalStateException`. Fehlerhafte Java-Werte werfen IllegalArgumentException/
NullPointerException. Typfilter anderer Protokolle werden vor Registrierung abgewiesen.

### Pakettypen und Operationen

Die native ID wird aus `MinecraftPacketIds` geliefert, nicht in Java festgelegt:

| Benannter Typ | ID im aktuell untersuchten Header | Beobachtung |
|---|---|---|
| Text | 9 | Metadaten |
| MovePlayer | 19 | Metadaten |
| ShowModalForm | 100 | Metadaten |
| ModalFormResponse | 101 | Metadaten |

Diese Tabelle beschreibt nur den vorhandenen Header, keinen versionsunabhängigen
ID-Vertrag. `getPacketTypes()` liefert die jeweils nativ bestätigten Namen/IDs.
Andere Header-IDs werden als `UNKNOWN` mit ihrer tatsächlichen ID beobachtet.
Unbekannte Payloads werden niemals interpretiert. Native Header haben 10 Bit
Packet-ID und 2 Bit Subclient; ungültige Event-Metadaten werden verworfen.
Authentifizierungs-/Handshake-Payloads gelangen auch bei einem All-Filter nicht nach Java.

| Capability | Verfügbar |
|---|---|
| PACKET_METADATA | Ja, im oben definierten Release-/Protokollvertrag |
| PLAYER_CONNECTION_LIFETIME | Ja, als Player-Ansicht innerhalb desselben Vertrags |
| DECODED_FIELDS | Nein: vorhandene öffentliche Packet-Events liefern Bytes |
| PACKET_SENDING | Nein: `Player::sendPacket` bietet nur generische Raw-Bytes |
| PACKET_CANCELLATION / PACKET_MODIFICATION | Nein: native C++-Events erlauben dies zwar, aber kein typsicher geprüfter Java-Vertrag für die jeweiligen Protokollzustände |
| TRANSPORT_CONNECTION_EVENTS | Nein: keine geeigneten öffentlichen NetherNet-/WebRTC-Lifecycle-Events gefunden |
| ASYNC_OBSERVATION | Nein: diese Version implementiert ausschließlich synchrone Listener |

Nicht unterstützte Operationen haben keine funktionslosen Methoden. Es gibt keine
Raw-Send-, Payload-Zugriffs-, Cancel- oder Modify-API. `alreadyCancelled` ist nur
der Zustand am aktuellen Prioritätspunkt, keine Zusicherung der endgültigen Zustellung.

## Nutzung

```java
NetworkManager network = getServer().getNetworkManager();
if (network.getCapabilities().contains(NetworkCapability.PACKET_METADATA)) {
    Registration subscription = network.listen(
        new PacketFilter(Set.of(), Set.of(PacketDirection.INBOUND)),
        EventPriority.MONITOR,
        packet -> {
            int id = packet.type().identifier();
            // Nur kurze Arbeit auf dem Serverthread; keine Payloads protokollieren.
            counter.incrementAndGet();
        });
    // Auf dem Serverthread vorzeitig entfernen:
    subscription.close();
}
```

Eine leere Typmenge bedeutet alle IDs. Listener werden nativ nach ID und Richtung
gefiltert, bevor ein Snapshot und ein JNI-Aufruf entstehen. Jeder registrierte
Listener bekommt den ursprünglichen nativen Event genau einmal an seiner Priorität;
die Java-Bridge erzeugt kein zusätzliches Endstone-Event. Exceptions eines Listeners
verhindern spätere Listener und die native Verarbeitung nicht.

Connection-Ansichten über `network.getConnections()` sind **keine** ICE-/WebRTC-
Verbindungen und erfassen keine noch nicht angemeldeten Sessions. Kennungen sind
pluginbezogene, nicht wiederverwendete Tokens. PlayerQuit invalidiert sie; Runtime-ID-
Wechsel oder fehlender Spieler tun dies ebenfalls. Wiederkehr desselben Spielers
reaktiviert keinen alten Token. `isActive()` ist dann false, `getInfo()` meldet CLOSED.
Nach vollständigem Plugin-Disable sind Live-Zugriffe ungültig. Snapshots bleiben Werte.
Für Join/Disconnect weiterhin die bestehenden Java-PlayerJoin-/PlayerQuit-Events verwenden;
es gibt keine künstlichen NetworkConnection-/NetworkDisconnect-Events.

## Threads, Lebensdauer, Sicherheit

Listener, Registrierung/Abmeldung, Connection-Abfragen und Metrics sind an den
Serverthread und einen aktiven Plugin-Kontext gebunden. Endstones bestehender
EventManager weist synchrone Packet-Events auf Network-Workern zurück. Die Bridge
prüft den Thread zusätzlich vor jedem Playerzugriff. Network-Threads verarbeiten
weiterhin BDS-Transport/Batches; sie führen keine Java-Packet-Listener aus.

Snapshots besitzen keine nativen Packet-/Player-Pointer und dürfen nach dem Callback
aufbewahrt oder auf Java-/Scheduler-Workern verarbeitet werden. Dort sind Live-
Gameplay-/Connection-Zugriffe weiterhin verboten. Es wird **keine Async-Queue** angelegt,
es existieren keine verdeckten Netzwerk-Worker und kein unbegrenzter Event-Rückstau.
Eine eingebaute Async-Zustellung ist verschoben: Sie benötigt eine explizite begrenzte
Queue-/Overflow-/Disable-Semantik für laufende Callbacks, die hier nicht als fertig
ausgegeben wird. ASYNC_OBSERVATION fehlt entsprechend in den Capabilities.

Maximal 64 Packet-Registrierungen je Plugin, 128 Filtertypen je Registrierung und
4096 gleichzeitig verwaltete Player-Connection-Ansichten. Eine Registrierung kann
beide Richtungen besitzen. Entfernte Listener verlieren ihre GlobalRefs; lokale
JNI-Frames werden am Callback-Ende freigegeben. Die Callback-Referenz wird für einen
laufenden synchronen Aufruf gepinnt. Reentrant entfernte Listener werden deaktiviert,
damit ein verbliebener nativer Handler-Snapshot keine weiteren Java-Aufrufe ausführt.
Disable entfernt Listener und interne Join-/Quit-Beobachter; keine zweite JVM.

Eingebettete Java-Plugins sind vertrauenswürdiger In-Process-Code. Die JVM ist
keine vollständige Sandbox gegen bösartige Plugins, Reflection oder endlose Schleifen.
Ein synchroner Listener kann den Server blockieren; Diagnostik kann dies melden,
aber den Java-Code nicht sicher präemptiv abbrechen.

## Diagnose und Performance

`setDiagnosticsEnabled(true)` aktiviert native Callback- und Java-Listener-Zeitmessung.
`getMetrics()` liefert gefilterte Eventzustellungen, gestartete Callbacks, Exceptions,
zusätzliche Thread-Guard-Drops, langsame Aufrufe, Summen und Maxima der Laufzeiten.
Ab 10 ms wird eine Warnung ausgegeben, zunächst einmal und anschließend alle 100
langsamen Aufrufe. Java-Exception-Stacktraces/Fehlerlogs werden beim ersten und jedem
1000. Fehler ausgegeben, damit ein fehlerhafter Listener nicht jedes Paket ins Log schreibt.
Zähler bleiben bei deaktivierter Zeitmessung verfügbar; Zeiten sind kumulative Werte
der zuvor eingeschalteten Messperioden, sie werden beim Abschalten nicht zurückgesetzt.

`bridgeNanos` misst diesen nativen Beobachtungs-Callback einschließlich JNI, Java-
Snapshot-Erstellung und Listener. `listenerNanos` misst Java-Listener-Aufrufe.
Die Differenz ist **keine isolierte JNI-Mikrobenchmark**. Die komplette BDS-/NetherNet-
Hook-/Transportlatenz wird nicht gemessen. Zustellungen pro Zeitintervall lassen sich
aus Zählerdifferenzen berechnen, sind bei mehreren Listenern aber keine eindeutigen
Netzwerkpakete pro Sekunde. Queue-Auslastung ist mangels Queue nicht anwendbar.
Es gibt keine zugesicherte Durchsatzsteigerung oder reale BDS-Performance-Messung.

Das Beispiel `/zernythnet start|stop` mit Permission `zernyth.network.diagnostics`
schaltet Zählen ein/aus; ohne Argument zeigt es Protokoll, Capabilities und Zähler.
Es loggt keine Payloads, Authentifizierungsdaten oder Adressen und verändert keine Pakete.
Beim Enable wird kein Packet-Listener automatisch registriert.

## Build/Test

Bestehende Gradle-/CMake-Einrichtung nach [Java-25-Standard](zernyth-java-25.md).
Artefakte heißen `api-0.4.0.jar`, `runtime-0.4.0.jar`, `example-0.4.0.jar`.
Zusätzliche Fixture-Aufgaben `:runtime:networkIntegrationJar` und
`:runtime:networkPeerIntegrationJar` ergänzen die vier vorhandenen Fixture-Aufgaben.
JNI-Tests benötigen zusätzlich `ZERNYTH_NETWORK_TEST_JAR` und
`ZERNYTH_NETWORK_PEER_TEST_JAR`; bei fehlenden Pfaden erfolgt Skip statt vorgetäuschtem Test.
Mit `JAVA_TOOL_OPTIONS=-Xcheck:jni` prüfen. Keine Serverstarts durch Build/Test.

Für 0.5 empfohlen: separat autorisierte BDS-/Client-Validierung der echten Event-
Threadzuordnung, Protokoll-/Build-Erkennung, Disconnects und Hook-Kosten. Anschließend
gegebenenfalls begrenzte Async-Snapshot-Zustellung und gezielte dekodierte native
Adapter mit exakten Versionsnachweisen; erst dann typsichere Sendeoperationen prüfen.
