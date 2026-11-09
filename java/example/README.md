# HelloZernyth – Java 25 LTS

0.6: `/zernythasync start|cancel|status` (Permission `zernyth.diagnostics`) startet
eine begrenzte, kooperativ abbrechbare CPU-Aufgabe über Endstones Worker. Das Ergebnis
wird über `returnToServer` auf dem Serverthread protokolliert. Status zeigt Task-,
Queue- und JVM-Speicherwerte; keine nativen Gameplay-Aufrufe auf dem Worker.
Siehe [Async Runtime und Profiler](../../docs/zernyth-runtime-0.6.md).

Das Beispielplugin gehört zum vorhandenen Gradle-Multiprojekt. Es benötigt
**Java 25 LTS**, bevorzugt **Eclipse Temurin 25**; ältere Java-Versionen werden
nicht unterstützt. Toolchain und `options.release = 25` werden zentral für API,
Runtime, Beispiel und Test-Fixtures festgelegt.

Mit bereits installiertem Temurin 25 und Gradle >= 9.1 im Verzeichnis `java/`:

```text
gradle --offline --no-daemon :api:jar :runtime:jar :example:jar
```

Wenn ausschließlich eine andere JDK-25-Distribution lokal vorhanden ist, bewusst
`-PzernythJavaVendor=any` ergänzen. Es werden keine JDKs automatisch heruntergeladen.
Das ist kein Test mit Temurin. Java 25 bleibt auch bei dieser Auswahl verpflichtend.

Artefakt: `example/build/libs/example-0.5.0.jar`. API und Runtime gehören in den
JVM-Classpath, das Beispiel-JAR wird vom vorhandenen EndstonePluginManager geladen.
Die Server-Laufzeit benötigt Java >= 25; Temurin 25 ist die Standardempfehlung.

`onEnable/onDisable` zeigen den Lifecycle. Der explizite Spielercommand
`/zernythdemo` mit Permission `zernyth.demo` demonstriert Inventar, Itemvergabe,
Welt/Block/Entity und ActionForm. Beim Aktivieren werden keine Items vergeben.
Serverstarts und Änderungen produktiver Konfigurationen sind kein Bestandteil des Builds.

`/zernythnet start|stop` mit Permission `zernyth.network.diagnostics` schaltet
lesendes Paketzählen ein/aus; ohne Argument werden Protokoll, Capabilities und Zähler
angezeigt. Kein automatischer Listener beim Enable, keine Payload-Logs, keine
Paketänderungen. Details: [Network API](../../docs/zernyth-network-0.4.md).

`/zernythdiag` mit Permission `zernyth.diagnostics` zeigt eigene Lifecycle-Zeiten
und Ressourcenzähler ohne Konfigurationswerte. Für neue Plugins siehe
[SDK/Services/Config](../../docs/zernyth-ecosystem-0.5.md) und die
[Vorlage](../../zernyth-plugin-template/README.md).

Für eigene Plugins in Gradle:

```kotlin
plugins { `java-library` }
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}
tasks.withType<JavaCompile>().configureEach { options.release.set(25) }
```

API als `compileOnly` einbinden, nicht in das Plugin schattieren. Für Maven ist
entsprechend `maven.compiler.release=25` und ein JDK 25 zu verwenden.
Threading-/Fehlerregeln: [Gameplay-Dokumentation](../../docs/zernyth-advanced-0.3.md).
