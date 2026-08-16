# CLAUDE.md — msp-app-kt

Este archivo lo lee Claude Code (y cualquier agente) al abrir el repo. Contiene
reglas duras verificadas en campo, no preferencias de estilo.

---

## 1. ANTES de tocar el sync de cobranza

> **El contrato completo vive en el repo hermano:
> `msp-api/docs/COBRANZA-SYNC.md`. Léalo entero antes de cambiar nada de
> `core/sync/cobranza/`, `PendingPaymentsWorker` o `PaymentDao`.**

El sync mueve **pagos**. Un defecto no produce una pantalla fea: produce que un
cobrador **no vea un pago, crea que no se registró y vuelva a cobrar**. El
2026-08-15 se encontraron **siete defectos simultáneos en producción con toda la
suite en verde**.

### Los invariantes del cliente, en corto

**El cursor es un PAR, no una fecha.** El servidor pagina por
`(UPDATED_AT, PK)`. `cobranza_sync_state` guarda `CURSOR` **y** `AFTER_ID`.

> Donde se escribe uno se escribe el otro, y **todo camino que deje `CURSOR` en
> `NULL` debe dejar `AFTER_ID` en 0 en la misma operación**.

Persistir sólo el cursor produjo **2,057 pagos re-descargados cada 76 segundos,
indefinidamente**, porque 1.8 de 2.2 millones de filas comparten un único
`UPDATED_AT`.

**Una página vacía NO es posición cero.** El servidor devuelve el cursor
recibido cuando no hay filas, así que la posición sigue siendo válida.
Traducir "vacía" a `0` reintroduce el bucle **un tick después**. Por eso
`SyncPage.afterId` es `Int?`.

**El epoch se persiste SOLO cuando el replay terminó.** Si el proceso muere a
media descarga, la generación guardada sigue siendo la vieja y el próximo
arranque replica otra vez. El costo de equivocarse por ese lado es ancho de
banda; por el otro sería un replay a medias **congelado para siempre**.

**El colapso del gemelo va en la MISMA transacción que el insert**, en todos
los caminos que insertan pagos (`mergePagos` **y** `reconcilePagosViaIds`). Los
`Flow` de Room de la UI no pasan por el `cobranzaWriteMutex`, así que sin
transacción hay una ventana en la que se ven las dos filas. Ya ocurrió: el
duplicado vivió **más de tres minutos** en un teléfono real.

**No borrar nunca una captura que el servidor no haya nombrado.** El UUID lo
genera el teléfono; el servidor no puede nombrar uno que nunca recibió.
`PAGO_RECIBIDO_ID` la escribe **un solo lugar** en producción:
`PagoDto.toEntity()`.

**Trocear todo `IN (...)` sobre conjuntos sin cota.** En Android ≤ 11 el tope de
SQLite es **999** parámetros. `by-ids` no está acotado. Sin trocear lanza *"too
many SQL variables"*, lo atrapa el `try/catch` externo y se vuelve **error
silencioso en cada tick**.

---

## 2. Compuertas

```
./gradlew test detekt ktlintCheck
./gradlew prePushCheck
```

- **`prePushCheck` con el árbol caliente sale en segundos y NO prueba nada.**
  Corra `--rerun-tasks` al menos una vez (~3m40s, 716 tareas) antes de afirmar
  que algo pasa.
- **Ninguna compuerta compila ni corre `androidTest`** (`build.gradle.kts`
  excluye las tareas `connected*`). Los E2E existen pero **nadie los ejecuta**
  salvo el workflow de emulador.
- Nunca use `--no-verify` ni suba umbrales para que algo pase.

### Pruebas

- **Fakes-only, sin MockK.**
- Sin datos de prueba persistentes: Room in-memory o transacciones con rollback.
- **Cada arreglo con una prueba que se ponga ROJA al revertirlo.** Compruébelo
  de verdad: revierta, corra, confirme el rojo, restaure, **y verifique que no
  quedaron restos**. Ya ocurrió que un marcador de mutación se quedara puesto y
  el reporte dijera "verde".

---

## 3. UI

- Texto de usuario en **español**, minúsculas, sin punto final.
- **Minimalista: 2-4 palabras.** Nada de oraciones en banners.
- **NUNCA diga "ciclo" en la UI** — se dice **"semana"**. El nombre interno sí
  puede ser ciclo.
- Roborazzi: si cambia UI, **regrabe los goldens** por el camino del proyecto y
  **revise las imágenes**. Prohibido subir umbrales de comparación.
- Listas que crecen con los datos van **perezosas**. `RangeCalculator.cycleDays`
  no tiene tope: con una fecha de carga vieja son cientos de filas.

---

## 4. Entorno de pruebas en dispositivo

- **`devserver` está RETIRADO**: su túnel pasó a ser el de **producción**. Un
  APK apuntando ahí **escribiría pagos en la base de producción**. Use
  **`devlocal`** (`LOCAL_API_HOST` en `local.properties`).
- Para un teléfono físico: `LOCAL_API_HOST=127.0.0.1` + `adb reverse tcp:3001
  tcp:3001`. `127.0.0.1` ya está en la lista blanca de cleartext del config de
  debug; una IP de LAN **no**.
- **`adb reverse` no derriba conexiones ya establecidas.** OkHttp mantiene viva
  la del pool y el sync cada 30 s impide que expire. Para cortar de verdad:
  **`adb kill-server`**.
- El **depurador remoto de Room** no arranca tras el login (`init()` corre antes
  de que haya sesión y sólo re-evalúa al cambiar el documento de config). Para
  activarlo: tocar `config/db_debug` en Firestore, y que `allowedDevices`
  contenga el correo.
- **No toque `config/api_settings`**: ese `baseURL` es el kill-switch de la
  flota entera.

---

## 5. Riesgos conocidos

- **`msp-app-release.keystore` existe sólo en una laptop.** Si se pierde, la app
  instalada **no se puede volver a actualizar**; la única salida sería
  desinstalar y reinstalar en cada teléfono, **borrando los pagos pendientes**.
- Hay credenciales en texto plano en `CollectionReportDeviceSmokeTest.kt` y este
  repositorio es **público**.
- `RemoteDbDebugger` se enciende sólo con un documento de Firestore, sin
  compuerta por flavor, y en producción `blockDangerousQueries` está en `false`.
