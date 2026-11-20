# IndexedDB und Offline-Fähigkeit: funktionale Datenbank-Boundary

**2026-11-20 · Architekturplan; noch kein IndexedDB-Modul implementiert.**

Die Browser-Datenbank ist kein großer `localStorage`-Wrapper. IndexedDB besitzt **asynchrone Requests, Transaktionen, Object Stores, Indexes, Versionswechsel und Upgrade-Blockaden**. Diese Semantik muss die Abstraktion respektieren. Siehe [Browser-Persistenz](BROWSER-PERSISTENCE.md) für den kleineren Vorbau.

## 1. Ein schmaler, semantisch korrekter Port

Ein möglicher zweiter, optionaler Adapter `mathcat-indexeddb` verwendet ein MPP-Daten-/Query-Modell und einen JS-Interpreter. Andere Targets können reine Query-Planer und Tests verwenden, ohne einen Native-IndexedDB-Treiber vorzutäuschen.

```kotlin
// Skizze, keine vorhandene API.
sealed interface DbFailure {
    data class Unavailable(val detail: String) : DbFailure
    data class InvalidSchema(val version: Int) : DbFailure
    data class Conflict(val key: String) : DbFailure
    data class Aborted(val detail: String) : DbFailure
    data class QuotaExceeded(val detail: String) : DbFailure
    data class Decode(val key: String) : DbFailure
}

data class DbSchema(val name: String, val version: Int)

interface Repository<K, V> {
    suspend fun read(key: K): Sum2<DbFailure, V?>
    suspend fun write(key: K, value: V): Sum2<DbFailure, Unit>
    suspend fun remove(key: K): Sum2<DbFailure, Unit>
}
```

Die API ist absichtlich zunächst **schmaler als IndexedDB**; sie deckt konkrete Anwendungsfälle ab und wächst erst, wenn Index-Lookups, atomare Mehrfachupdates oder Query-Prädikate erforderlich sind. Verschiedene Index- und Cursor-Semantiken nicht hinter einer scheinbar universellen `Storage<S>`-Lens verstecken.

## 2. Transaktionen dürfen nicht durch beliebige Suspension zerbrechen

IndexedDB-Transaktionen können automatisch inaktiv werden bzw. committen, wenn keine Requests mehr in ihrem zulässigen Task-Kontext anstehen. Eine API wie

```kotlin
database.transaction {
    val value = suspendNetworkCall() // problematische implizite Garantie
    put("drafts", value)
}
```

soll deshalb **nicht** behaupten, beliebige suspendierende Kotlin-Blöcke seien atomar. Besser: eine **reine Operationsbeschreibung** erstellen, deren Requests der Interpreter im gültigen Transaktionskontext ausführt. Async-Resultate werden erst nach `complete` als erfolgreich bestätigt; `abort` und `error` ergeben explizite Fehler.

```text
QueryPlan (pure) -> IDB Transaction (effect) -> complete | abort | error
```

Die Klammerung eines solchen Effekts entspricht konzeptionell `acquire/use/release`. Das bedeutet nicht, dass die JS-Transaktion ein Kotlin-`Resource` mit beliebiger Cancellation-Semantik wäre. Cancellation muss als Abbruchversuch modelliert und getestet werden.

## 3. Schema-Migration ist eine Versionskomposition

Reine Beschreibung einer Kette von Migrationsschritten:

```text
Schema(1) --m1--> Schema(2) --m2--> Schema(3)
```

Die Reihenfolge ist zwingend, und keine Migration darf eine fehlende Zwischenversion übergehen. Ein Upgrade läuft im `versionchange`-Kontext des Browsers. Andere offene Tabs können Upgrades blockieren; deshalb `blocked` und `versionchange` explizit behandeln und Handles des Host-Runtimes kontrolliert schließen.

Das ist ein guter Ort für typisierte **Schema-Deskriptoren** und später eventuell KSP-generierte Stores/Indexdefinitionen – aber nur, wenn ein reales Migrationsproblem existiert. Keine allgemeine Annotation-DSL vor dem ersten Anwendungsfall.

## 4. Offline-Daten sind nicht automatisch fachliche Wahrheit

Das lokale Datenmodell ist ein **Cache oder eine Arbeitskopie**; der Backend-Datenbestand bleibt für Solyton zunächst maßgeblich. `Repository` und `Synchronize` haben getrennte Rollen.

Eine mögliche spätere Outbox:

```text
LocalDraft -> PendingCommand -> Sent -> Confirmed | Conflict | Rejected
```

Jeder ausgehende Command benötigt eine stabile ID (für Idempotenz), das Domänenprotokoll eine Versions-/Konfliktregel (z. B. Server-Revision oder `ETag`). Network-Ausfall, Auth-Verlust, Quota, Tab-Wechsel, Logout und Browser-Eviction dürfen nicht stillschweigend als erfolgreiche Synchronisation zählen.

**Nicht jedes Fachobjekt darf offline gespeichert werden.** Besonders sensible persönliche oder Bankdaten benötigen eine eigene Datenschutz-/Threat-Model-Bewertung inklusive Löschung bei Logout und Account-Wechsel. Verschlüsselung mit einem Schlüssel, der zugleich ungeschützt im gleichen JS-Origin liegt, ist keine belastbare Lösung gegen XSS.

## 5. Browser-Effekte in den äußeren Application-Flow

Der **Flow** besitzt `OpenDatabase`, `Migrate`, `LoadDraft`, `Commit`, `Sync`, `Close` und Cancellation. Ergebnisse gehen als typisierte Events zurück. Compose beobachtet den Zustand, der Store bleibt eine bestehende Capability.

Bei Route-/Plugin-Wechsel: offene Zugriffe beenden, relevante Handles schließen, keine dauerhaft globalen DB-Connections oder unbegrenzten Background-Jobs. Bei Mehrtab-Nutzung ein gezieltes Synchronisationsprotokoll entwerfen, nicht beliebige DB-Writes als automatisch konfliktfrei ansehen.

## 6. Umsetzungsreihenfolge

**I1 — Use case:** einen nicht sensitiven Solyton-Anwendungsfall auswählen, z. B. einen lokalen Formularentwurf. Entscheiden, wann Schreiben/Verwerfen/Synchronisieren erlaubt ist.

**I2 — Memory-Spezifikation:** modellbasierte Repository-Tests, Codec-Roundtrips, Fehlerfälle, Query/Update-Beschreibung; keine IndexedDB-API im Kern.

**I3 — echtes IndexedDB-Backend:** Open/Close, Version Change, read/write/abort/commit und browserbasierte Tests einschließlich blocked Upgrades.

**I4 — Lifecycle/Offline:** Host-Flow besitzt DB-Handle; Tests für Reload, Quota, zwei Tabs, Logout, stale Requests und mehrfaches Stop.

**I5 — erst dann Outbox-Synchronisation:** Idempotenz, Konfliktpolitik und Backend-Test. Nicht einfach `localStorage`-Status mit `IndexedDB` vertauschen.

**I6 — KSP prüfen:** optionale Generierung wiederholter Typ-/Index- und Codec-Bindings, falls der Boilerplate-Gewinn nachweisbar ist. Die Algebra und Transaktionsgesetze müssen weiterhin sichtbar bleiben.

### Nachlese

- MDN: https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API/Using_IndexedDB
- MDN: https://developer.mozilla.org/en-US/docs/Web/API/IDBTransaction
