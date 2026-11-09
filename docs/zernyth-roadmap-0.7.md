# Zernyth 0.7.0 — Advanced Developer API & Compatibility

Historisches Planungsdokument. Den tatsächlich implementierten Umfang und die
Validierung beschreibt [der 0.7-Bericht](validation.md).

Status: konkretisierter Entwicklungsumfang, keine implementierte oder validierte
0.7-Version. Grundlage sind die vier vom Nutzer vorgeschlagenen Schwerpunkte und
eine gezielte Prüfung der vorhandenen Command-, Event- und Message-Schnittstellen.
0.6 bleibt die aktuelle implementierte API-Version.

## 1. Einheitliche Java-API

Bestehende Gameplay-APIs vereinheitlichen: konsistente Namen, Null-/Optional-Regeln,
Argumentvalidierung, Fehler und dokumentierte Thread-/Lebensdauerverträge.
Bestehende Methoden und Binärsignaturen soweit möglich erhalten; neue Komfortmethoden
delegieren an tatsächlich vorhandene Endstone-Funktionen.

Text zunächst auf Endstones `Message` abbilden: String oder `Translatable`.
Strukturierte Textwerte und deren Bedrock-Rendering benötigen eine explizite
Unterstützungsmatrix. Hover-, Click- und RGB-Funktionen nicht als verfügbar ausgeben,
wenn der native Empfängerpfad sie nicht transportiert. Adventure erst nach Prüfung
von Nutzen, lokaler Verfügbarkeit und Dependency-Regeln entscheiden; keine Downloads.

`Event` setzt gegenwärtig einen Player voraus. Allgemeine Event-Verträge additiv
einführen; die existierende Hierarchie und `getPlayer()` nicht kommentarlos ändern.
Cancellation weiterhin synchron im vorhandenen nativen Dispatcher; keine zweite
Event-Verwaltung. Registrierungen und unveränderliche Datenkopien klar unterscheiden.

Abnahme: bestehende Beispiel-JARs laden weiter; Text wird über echte native
Nachrichtenpfade zugestellt; Event-Lebensdauer und Cancellation sind getestet.

## 2. Commands

`CommandDefinition`, `Commands` und `CommandContext` erweitern. Vorhanden sind
Aliase, verschachtelte Subcommands, Permissions, Player-only und vier Argumenttypen.
Ergänzen: typsichere Argument-Schlüssel/Parser, Wertebereiche, optionale Argumente,
vollständige Usage-Pfade und konkrete Fehler mit betroffenem Argument.
Ausführung bleibt beim ursprünglichen Endstone-Command-System.

Vorschläge zweistufig prüfen: reine Java-Vorschlagsberechnung für definierte
Eingaben und tatsächliche Anbindung an den nativen BDS-Completion-Pfad. Eine
Vorschlagsfunktion allein nicht als funktionierende Client-Tab-Completion ausgeben.
Permissions vor Hilfe und Vorschlägen beachten. Commands und Callbacks beim Disable
weiterhin vollständig bereinigen.

Abnahme: verschachtelte Aliase, falsche/fehlende Argumente, Bereiche, Permissions,
Rollback und Disable sind automatisiert geprüft. Native Completion nur bei
nachgewiesenem Erweiterungspunkt als unterstützt markieren.

## 3. Compatibility-Grundlage

Zunächst eine technische Zuordnung ausgewählter Bukkit-/Paper-Konzepte zu realen
Zernyth-Funktionen erstellen: unterstützt, abweichende Semantik, nicht unterstützt.
Versions- und Capability-Verträge festlegen, danach einen kleinen funktionierenden
Adapterumfang mit Tests wählen. Adapter verwenden dieselben Plugin-Owner,
Generationen, Services, Scheduler und Dispatcher.

Keine beliebigen Paper-JARs als ladbar deklarieren. Keine leeren `org.bukkit`-
Klassen oder zweite Plugin-Verwaltung einführen. Weder Serverimplementierung noch
NMS, Java-Edition-Protokoll, Inventar-GUI-Semantik oder Paper-Threadmodell versprechen.
Eine Mapping-Tabelle allein ist noch kein funktionsfähiger Compatibility Layer.
Öffentliche Fremd-API-Signaturen und Lizenzen vor einer Übernahme separat prüfen.

Abnahme: für jeden tatsächlich implementierten Adapter gibt es eine dokumentierte
Semantik, native Zuordnung und positiven/negativen Test. Fehlende Funktionen schlagen
explizit fehl. Zernyth-native Plugins bleiben der primäre Entwicklungsweg.

## 4. Developer Experience

SDK/Javadocs und bestehende Gradle-/Maven-Vorlage fortführen. Beispiele für Text,
typisierte Subcommands, Events und sichere Async-Rückkehr ergänzen. IntelliJ-
Projektimport, Sources, Breakpoints, Plugin-TCCL und Exception-Diagnose dokumentieren.

Remote-Debugging nur als explizite lokale Opt-in-Konfiguration beschreiben;
keinen Debug-Port automatisch öffnen. Eine spätere BDS-Debug-Sitzung benötigt
eigene Freigabe. Fehlerbeispiele müssen Lifecycle-, Generationen- und Threadfehler
voneinander unterscheiden und dürfen keine sensiblen Daten protokollieren.

## Umsetzung und Validierung

Reihenfolge: API-/Kompatibilitätsverträge, Text und additive Events, Command-Erweiterungen,
kleiner belegter Compatibility-Adapter, SDK/Beispiele, Regressionen und Bericht.
Versionsnummer erst zusammen mit real implementierter 0.7-Funktionalität erhöhen.

Geplante Prüfungen: Java-Tests, produktive JNI-Bridge mit Mocks und `-Xcheck:jni`,
native Regressionen, bestehende 0.2–0.6-JARs sowie Windows-Build. Mess- und
Threading-Verträge aus 0.6 bleiben verbindlich. Linux, Temurin und echte BDS-Laufzeit
nur nach tatsächlicher Ausführung als validiert bezeichnen.

Keine BDS-Starts, Downloads, Deployments, Firewalländerungen oder öffentlichen
Veröffentlichungen durch diese Planung. Builds und Tests dieses Dokuments wurden
nicht ausgeführt; es ändert keine Laufzeitimplementierung.
