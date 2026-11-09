# Zernyth 0.7 Developer API

Java 25, bevorzugt Temurin, bleibt verbindlich. Bestehende APIs sind additiv erweitert.
`dev.zernyth:api:0.7.0` als Gradle `compileOnly` oder Maven `provided` verwenden.
API niemals in Plugin-JARs schattieren. Das bestehende Template demonstriert die neuen
Commands und Komponenten; lokale Veröffentlichung bleibt auf `java/build/sdk-repository`
begrenzt (`:api:publishApiPublicationToSdkRepository`). Keine neuen Dependencies.

## Text

```java
sender.sendMessage(Component.text("Welcome"));
player.sendMessage(Component.translatable("chat.type.announcement", "Server", "Welcome"));
```

Import: `dev.zernyth.api.text.Component`. Werte sind immutable. Translations verwenden
Endstones `Message`/`Translatable` und dessen nativen Empfängerpfad; Client-Schlüssel
und Parameter müssen zur Bedrock-Version passen. Unbekannte Schlüssel werden nicht
vom Framework übersetzt. Keine Hover-/Click-/RGB-/Adventure-Kompatibilität behauptet.
Eigene externe CommandSender-Implementierungen erben Plain-Text-Unterstützung und
lehnen Übersetzungen explizit ab, bis sie diese selbst unterstützen.
Native Empfängerzugriffe bleiben serverthreadgebunden; CommandSender sind nach dem
Command-Callback ungültig. Textwerte selbst können auf Workern erstellt werden.

## Commands

```java
var count = TypedCommand.Argument.integer("count", 1, 100).optional(3);
var give = new TypedCommand("give", "Example", "example.give", false,
    List.of("g"), List.of(count), List.of(), context -> {
        context.sender().sendMessage(Component.text("Count: " + context.get(count)));
        return true;
    });
new TypedCommand("example", "Example", "", false,
    List.of("ex"), List.of(), List.of(give), null).register(getServer());
```

Import `dev.zernyth.api.command.TypedCommand`. Nodes sind immutable. Branches haben
keine positionalen Argumente, Leaves einen Executor. Pflichtargumente stehen vor
optionalen. `Argument.choice` bietet validierte Auswahlwerte; öffentliche
`Argument<T>` unterstützen eigene Parser mit `Class<T>` und statischen Vorschlägen.
`Context.get(argument)` liefert den geprüften Typ. Parser sollten erwartete Eingabefehler
als IllegalArgumentException melden; unerwartete Programmierfehler bleiben Fehler
für die vorhandene native Callback-Isolation. Keinen blockierenden Parser verwenden.

Aliase, verschachtelte Permissions, Player-only und vollständige Usage-Pfade werden
ausgewertet. Argumentfehler benennen das betroffene Argument. Registriert wird über
`Server.registerCommand`, somit weiterhin im ursprünglichen Endstone-Command-System.
Disable räumt dessen native Owner-Ressourcen auf. Die alte CommandDefinition-API
und ihre Binärsignaturen bleiben erhalten.

`suggest(sender, tokens)` berechnet lokale Vorschläge. Das letzte Token ist der
aktuelle Präfix; für eine neue leere Argumentposition ein leeres Token anhängen.
Permissions werden auf dem gesamten besuchten Pfad geprüft. Diese Funktion sendet
keine Completion-Daten an BDS-Clients: native dynamische Tab-Completion ist nicht
implementiert und wird nicht als Capability ausgegeben.

## Events und Portierung

`EventView` ist ein additiver allgemeiner View-Vertrag. Bestehende Player-Events
implementieren ihn, behalten aber ihre Hierarchie, `getPlayer()` und synchrone
Cancellation. Es entsteht kein neuer Dispatcher und kein erfundenes Serverevent.
Native Views bleiben nur während des jeweiligen Callbacks gültig.

`dev.zernyth.api.compat.PlayerAdapter.adapt(player)` ist eine funktionierende
Source-Portierungsfassade für Name, UUID, Online-Status, Komponenten-Nachrichten,
Permissions, Location und Teleport. Sie delegiert an denselben nativen Player und
übernimmt dessen Threading-, Owner- und Lebensdauerfehler unverändert.

| Konzept | Unterstützung |
|---|---|
| Player-Operationen der Fassade | Delegation an vorhandene Zernyth-API |
| Event-/Scheduler-/Service-Integration | Zernyth-native APIs; keine fremden Interface-Adapter |
| Unveränderte Bukkit-/Paper-JARs | nicht unterstützt |
| org.bukkit-/Paper-Klassen, NMS, Java-Edition-Pakete | nicht bereitgestellt |
| Java-Inventar-GUI- oder Paper-Threadsemantik | nicht emuliert |

Dies ist keine allgemeine Bukkit-/Paper-Binärkompatibilität. Es werden keine
Fremdklassen vorgetäuscht und keine zusätzlichen Plugin-Manager/ClassLoader erzeugt.

## IntelliJ und Debugging

`java/` als bestehendes Gradle-Projekt öffnen, Projekt-SDK und Gradle JVM auf JDK 25
setzen, Sources und generierte API-Javadocs verwenden. Ohne Temurin den vorhandenen
Oracle-Override `-PzernythJavaVendor=any` explizit in Gradle-Argumenten setzen.
`build` führt die ausführbare Offline-Java-Testsuite über `runtime:selfTest` aus.

Für eine ausdrücklich gewünschte isolierte Java-Debug-Sitzung ist
`:runtime:selfTest --debug-jvm` verfügbar. JavaExec-Debugging bindet in dieser
Konfiguration an 127.0.0.1:5005 und wartet auf den Debugger. Ohne das Flag ist es aus.
In IntelliJ eine Remote-JVM-Attach-Konfiguration für diesen lokalen Port verwenden;
Breakpoints in Parsern, Bootstrap und ClassLoader setzen. Dieser Harness ist kein BDS.
Keine Firewallöffnung oder BDS-JDWP-Konfiguration wird automatisch vorgenommen.

Bei Fehlern zuerst Ebene unterscheiden: Parserfehler, Java-Callback-Exception,
abgelaufener Owner/Handle oder Serverthread-Verletzung. Native Stacks und JNI-Checker
sind getrennt von Java-Logs zu bewerten. Ein Worker darf einen alten CommandSender
nicht speichern; Ergebnisse über AsyncRuntime.returnToServer und neu aufgelöste,
gültige Empfänger weiterverarbeiten. Plugin-TCCL und neue Generationen beachten.
