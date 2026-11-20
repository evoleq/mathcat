# Browser-Persistenz: Cookies, Web Storage und typisierte Boundaries

**2026-11-20 · Architektur- und Migrationsplan; noch keine Implementierung.**

Ziel ist **eine kleine funktionale Schnittstelle für Browser-Persistenz**, keine neue Familie von Monaden. Der Store bleibt in-memory State/Dispatcher-Komposition; Browser-Speicher sind *externe Effekte*. Der Application-Flow besitzt Laden, Synchronisieren, Löschen und Lifecycle.

## 1. Semantik zuerst, nicht die API vereinheitlichen um jeden Preis

| Technik | Geeignet für | Grenzen |
| --- | --- | --- |
| `localStorage` | Kleine, nicht geheime, persistente Einstellungen | Synchron und damit potenziell blockierend; quota/permission/eviction; über JS zugreifbar |
| `sessionStorage` | Tab-bezogene, kleine Einstellungen | Ebenfalls synchron und JS-zugänglich; eigene Lebensdauer |
| Nicht-`HttpOnly`-Cookie | Sprache, nicht geheime Browser-Preferences | Server-Übertragung je nach Attributen; Path/Domain/SameSite/Secure/Laufzeit sorgfältig definieren |
| `HttpOnly`-Session-Cookie | Vom **Server** verwaltete Authentifizierung | Für Browser-JS absichtlich **unsichtbar**, daher **kein** KeyValuePort |
| IndexedDB | Größere strukturierte Daten, Offline-Entwürfe | Asynchrone Transaktionen/Migrationen; eigene Schnittstelle statt Web-Storage-Wrapper |

Wichtig: MathCats synchrones `Storage<S>` ist **nicht** die identische Abstraktion wie persistente Browser-Speicherung. Die erste hat `read/write`, die zweite kann fehlschlagen und besitzt einen externen Lebenszyklus.

## 2. Konkrete Beobachtungen in Solyton

- [`module/localstorage/api/local-storage.kt`](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-frontend/src/jsMain/kotlin/org/solyton/solawi/bid/module/localstorage/api/local-storage.kt) kapselt lediglich `localStorage.getItem/setItem` in String-Funktionen. Typisierte Werte, Fehler und Löschsemantik fehlen dort.
- [`application/storage/event/login.kt`](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-frontend/src/jsMain/kotlin/org/solyton/solawi/bid/application/storage/event/login.kt) persistiert **Access- und Refresh-Tokens in localStorage**. Das ist ein *konkreter* Anlass für eine separate Security-/Auth-Migrationsentscheidung, keine unkritisch zu übernehmende Bibliothekskonvention.
- [`module/cookie/api/cookie.kt`](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-frontend/src/jsMain/kotlin/org/solyton/solawi/bid/module/cookie/api/cookie.kt) verarbeitet Sprache und Consent über `document.cookie` und einen selbstgeschriebenen Parser.
- [`application/storage/event/context.kt`](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-frontend/src/jsMain/kotlin/org/solyton/solawi/bid/application/storage/event/context.kt) liest `context_id`, schreibt aber `<env>_context_id` und verwaltet einen auswechselbaren globalen `pagehide`-Listener. Das muss mit Tests geklärt werden, bevor wir es migrieren.
- [`application/storage/event/cookie.kt`](https://github.com/doctor-smith/solawi-bid/blob/main/solawi-bid-frontend/src/jsMain/kotlin/org/solyton/solawi/bid/application/storage/event/cookie.kt) erstellt einen separaten `CoroutineScope(Job())`, nur um ein Cookie zu schreiben.

Diese Beobachtungen sind **kein Nachweis eines konkreten Vorfalls**, aber gute Regressionstest-Kandidaten.

## 3. Kleiner gemeinsamer Vertrag

Ein Vorschlag für eine **optionale** `mathcat-browser-storage`-Bibliothek (nicht Bestandteil von `mathcat-core`):

```kotlin
// Beispielentwurf; noch keine öffentliche MathCat-API.
sealed interface PersistenceError {
    data object Unavailable : PersistenceError
    data object Denied : PersistenceError
    data object QuotaExceeded : PersistenceError
    data class Decode(val key: String) : PersistenceError
    data class Unexpected(val reason: String) : PersistenceError
}

typealias PersistResult<A> = Sum2<PersistenceError, A>

interface KeyValuePort {
    suspend fun get(key: String): PersistResult<String?>
    suspend fun put(key: String, value: String): PersistResult<Unit>
    suspend fun remove(key: String): PersistResult<Unit>
}

data class TypedKey<A>(
    val namespace: String,
    val name: String,
    val version: Int,
    val encode: (A) -> String,
    val decode: (String) -> PersistResult<A>
)
```

**Nur ein Vertrag, mehrere Interpreter:** `MemoryKeyValuePort` für Tests; `LocalStoragePort` und `SessionStoragePort` in JS. Rückgabewerte unterscheiden fehlende Schlüssel, ungültige Kodierung und unerwartete Browserfehler. Kein heimlicher Fallback auf gültige Domain-Daten. `suspend` ist hier eine **Kompositionsoberfläche**, macht aber die Web-Storage-API selbst **nicht asynchron**.

Ein typisierter Key ist **kein Sicherheitsmechanismus**; die Host-Boundary muss Plugin-Zugriffe zusätzlich auf explizit freigegebene Schlüssel beschränken. Keine freie API zum Lesen sämtlicher Origin-Schlüssel für nachgeladene Microfrontends.

**Mathematische Struktur:** `A -> suspend (E + B)` als checked Kleisli-Pfeil; `*` für tatsächliche Effektkomposition. Decoder/Encoder erfüllen auf dem gültigen Typbereich:

```text
decode(encode(a)) == Right(a)
```

Beim isolierten Memory-Interpreter testen wir Beobachtungsgesetze:

```text
get(k) nach put(k, x)        == Right(x)
get(k) nach remove(k)        == Right(null)
get(j) nach put(k, x), k!=j  == vorheriger get(j)
```

Diese Regeln gelten **nicht bedingungslos** gegenüber anderen Browser-Tabs, Speicher-Eviction oder Zugriffsbeschränkungen. Deshalb sind Browser-Integrationstests zusätzlich nötig.

## 4. Cookies sind eine eigene Boundary

Für **Client-Preferences** einen expliziten Cookie-Port mit `Path`, `SameSite`, `Secure`, Ablauf und Löschung entwickeln. Kein generischer `Cookie = localStorage`-Adapter: Browser-Cookies haben andere Domain- und Übertragungssemantik.

**Authentifizierung:** `HttpOnly`-Cookies dürfen durch JS weder gelesen noch gesetzt werden. Deshalb erfolgt Session-Management über HTTP/Backend-Verträge, inklusive CSRF-Schutz, Cookie-Lifetime, Session-Rotation und Logout. Das Umstellen von Solytons Token-Speicherung darf **keine Nebenwirkung** des generischen Speicher-Refactors sein. OWASP rät ausdrücklich davon ab, Session-IDs/Secrets in JavaScript-zugänglichen Web-Storage-Objekten abzulegen.

**Consent** ist eine Anwendungspolitik, keine Eigenschaft einer FP-Struktur. MathCat darf keine globale Einwilligungsentscheidung vorgeben.

## 5. Zuständigkeit: außen der Application-Flow

```text
Event(ChangeLanguage, SelectContext, Logout, Startup)
                |
                v
       pure App-Transition
                |
        PersistenceCommand
                |
                v
       platform interpreter
                |
                v
        ResultEvent -> Flow -> Store observation
```

Der Application-Flow besitzt das **Hydrating, Schreiben, Fehlerereignisse und eventuelle Subscriptions**. `@Composable Store` hat keinen Seiten-Effekt beim Rendern.

- `storage`-Listener registrieren und bei Stop deregistrieren; Änderungen im selben Document lösen normalerweise **kein** `storage`-Event dort aus.
- `pagehide` ist kein verlässlicher Commit-Mechanismus. Persistenz am fachlichen Übergang bevorzugen; Lebenszyklus-Ereignisse nur als Fallback.
- Keine Nebenwirkungen im `Writer` einer Domain-Lens verstecken: ein Writer beschreibt Zustandsänderung, Persistieren ist ein **separater Command**.
- Bei mehreren Tabs Ereignisse/Versionen oder eine passende Synchronisationsstrategie vorsehen, bevor wir Konsistenz behaupten.

## 6. Umsetzung in kleinen Schritten

**P1 — Charakterisierung:** Solytons Cookie, Context, Sprache und Token-Zugriffe dokumentieren; Verhalten für fehlende/geblockte Speicher, bereits geöffnete Tabs und Logout testen. Festlegen, welche Schlüssel tatsächlich persistiert werden müssen.

**P2 — kleines MPP-Port:** TypedKey, Codec, Fehler-Sum, Memory-Interpreter und Gesetze. Keine IndexedDB-Transaktionsabstraktion hineinziehen.

**P3 — Browser-Adapter:** `localStorage` und `sessionStorage`; ein expliziter Cookie-Preference-Adapter. Tests auf JS inklusive Quota/Permission-Fehlern, Encoding, Löschen und Listener-Cleanup.

**P4 — Application-Flow:** Startup-Hydration, Sprachwechsel, Kontextwechsel und Logout als Events/Commands; Regressionstests gegen doppelte Listener oder globale Coroutines.

**P5 — separate Auth-Entscheidung:** Token-/Session-Sicherheitsmigration gemeinsam mit Backend und E2E-Tests planen, niemals implizit umstellen.

**P6 — größere Daten:** Erst dann [IndexedDB/offline](INDEXEDDB-OFFLINE.md), wenn ein realer Solyton-Anwendungsfall vorliegt.

### Quellen zum API-Verhalten

- MDN: https://developer.mozilla.org/en-US/docs/Web/API/Web_Storage_API
- MDN: https://developer.mozilla.org/en-US/docs/Web/API/Window/storage_event
- MDN: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Cookies
- OWASP: https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html
