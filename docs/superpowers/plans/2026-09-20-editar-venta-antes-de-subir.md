# Corregir una venta antes de que suba (msp-app-kt)

Rama `feat/editar-venta-local`, worktree `/Volumes/M2-1TB/Developer/.wt-editar-venta`, sacada de `main` en
`c38c7e36`. APK de prueba para el teléfono del dueño; **no se publica a la flota**.

El caso, y es el único: el dueño captura una venta sin señal, se da cuenta de que se equivocó, la corrige, y
cuando vuelve la señal sube **corregida** y **una sola vez**.

> Ejecución por subagentes, una tarea por agente, en orden. Reglas de despacho compartidas:
> `docs/superpowers/plans/DISPATCH-CONVENTIONS.md`.

---

## Dos correcciones del orquestador al plan (tienen precedencia, no se relitigan)

1. **El texto de usuario va con MAYÚSCULA INICIAL.** El plan original detectó el choque y asumió minúsculas
   citando `CLAUDE.md` §3. Se resuelve al revés y por dos razones medidas: el dueño lo pidió explícitamente
   el 2026-09-20 ("porque está efectivo y transferencia con letra minúscula"), y la rama
   `feat/pagos-y-visitas` ya cerró exactamente esta discusión el 18-sep (commit `62a25715`), donde consta que
   *la regla escrita en el repo estaba invertida* y que ésa fue la causa de que el defecto llegara al teléfono.
   Cadenas exactas: **`Corregir venta`**, **`Ya se envió`**, **`La revisa la oficina`**,
   **`Corrección guardada`**. Siguen valiendo: 2 a 4 palabras, español, sin punto final, nunca "ciclo".

2. **El número de esquema de Room va a chocar, y se sabe desde ahora.** Esta rama sale de `main` (v29) y sube
   a **v30**. La rama `feat/pagos-y-visitas` **ya tiene su propia v30** (commit `39b811c6`, migración aditiva
   para promesa, citas, fotos y ficha). Las dos son correctas en su rama y ninguna está mal hecha. La que
   **se mergee en segundo lugar renumera** su migración a v31 y regenera su `schemas/*.json`. Es mecánico, pero
   si nadie lo anota se descubre en el peor momento: al mergear. Queda anotado aquí.

---

## Global Constraints (atan a TODAS las tareas)

- **`JAVA_HOME` en cada comando gradle**: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
  Un solo comando gradle a la vez (lock de build).
- **Compuerta**: `./gradlew prePushCheck --rerun-tasks`. Con el árbol caliente sale en segundos y **no prueba
  nada** (`CLAUDE.md` §2). Nunca `--no-verify`, nunca subir umbrales.
- **Fakes-only, sin MockK** (`CLAUDE.md` §2). Room in-memory vía `RoomTestBase`
  (`core/testing/src/main/kotlin/com/example/msp_app/core/testing/RoomTestBase.kt`).
- **Nada de esperas por reloj**: prohibido `Thread.sleep`, `delay` como sincronizador, timeouts o
  `System.currentTimeMillis()` en aserciones. El orden se controla con el despachador (`runTest` +
  `StandardTestDispatcher`) y con el **seam de red** (ver "Estrategia de pruebas" §3). El tiempo se inyecta:
  `FakeClock` (`core/testing/.../time/FakeClock.kt`) y el parámetro `nowEpochMillis` que el worker ya expone
  (`PendingLocalSalesWorker.kt:78-79`).
- **Cada arreglo con su rojo sembrado**, comprobado de verdad: revertir, correr, confirmar el rojo, restaurar,
  y verificar que no quedaron restos (`CLAUDE.md` §2 — ya pasó que un marcador de mutación se quedó puesto).
- **Lo nuevo va en `:core:*` / `:feature:*`.** `app/.../features/*` sólo **pierde** código en este plan, nunca
  gana lógica nueva. Se defiende con una tarea de build (Task 5) del mismo tipo que `checkNoLegacyDateApi`
  (`build.gradle.kts:211-245`).
- **La `Idempotency-Key` NO se rota jamás en este flujo.** Queda en `LOCAL_SALE_ID`
  (`PendingLocalSalesWorker.kt:256-261`, `RoomUploadFailureRepository.kt:39-42`). El nuevo guardado **no llama**
  a `resetForEditAndRetry` (`RoomUploadFailureRepository.kt:48-53`). Razón: si el servidor sí recibió el POST
  original y se perdió el 2xx, una llave nueva crea una **segunda venta**; con la misma llave el peor caso es un
  rechazo que la verificación por GET ya existente (`PendingLocalSalesWorker.kt:314-344`) resuelve como
  `RECONCILED_VIA_GET`, soltando la venta sin duplicar. Sí se limpia el registro de fallo (`clearFailure`).
- **El UUID de la venta nunca cambia.** Corregir no es recrear: nada de borrar y reinsertar con identidad nueva
  (`docs/module-standards/ENTREGA_GARANTIZADA.md`).
- **El cierre del reclamo va en la MISMA transacción que la escritura que lo cierra** — misma regla que el
  colapso del gemelo de cobranza (`CLAUDE.md` §1): los `Flow` de Room de la UI no pasan por ningún mutex, así
  que sin transacción hay una ventana observable.
- **Una captura nunca se retiene para siempre.** El reclamo tiene arrendamiento (30 min). Vencido, el subidor
  avanza. Perder una corrección es un mal día; perder una venta es dinero.
- **Ninguna venta ya subida se edita.** Predicado de editabilidad, único y compartido:
  `ENVIADO = 0 AND (LAST_UPLOAD_PERMANENT IS NULL OR LAST_UPLOAD_PERMANENT = 0)`. El segundo término porque un
  fallo permanente significa que el servidor **sí** tiene el intento resguardado (`X-Intent-Captured`,
  `core/upload/.../UploadDecision.kt:66-78`) aunque la fila local siga en `ENVIADO=0`
  (`PendingLocalSalesWorker.kt:371-383` no llama a `changeSaleStatus`).
- **Texto de usuario**: ver la corrección 1 del orquestador, arriba. Cadenas exactas: `Corregir venta`,
  `Ya se envió`, `La revisa la oficina`, `Corrección guardada`.
- **Schema Room**: v29 hoy (`core/database/.../AppDatabase.kt:64`). Este plan lo sube a **v30** con una
  migración aditiva (`ALTER TABLE ADD COLUMN`), sin recrear ninguna tabla — las ventas pendientes del dueño
  sobreviven. Patrón: `Migration28To29.kt:32-41`. Ver la corrección 2 del orquestador sobre el choque de
  numeración con la otra rama.
- **Fechas**: `AppClock`/`AppTime` de `:core:common` es la única fuente. `checkNoLegacyDateApi`
  (`build.gradle.kts:211-245`) ya falla el build ante un `Instant.now()` nuevo en `:app`.
- **Cada módulo nuevo entra a `prePushCheck` el día que nace** (`build.gradle.kts:392-480`). El hueco de
  `:feature:configuracion` — en `settings.gradle.kts` pero fuera de la compuerta durante meses — está
  documentado ahí mismo; no se repite.

---

## Fuera de alcance (decidido, no relitigar)

1. **Editar una venta que ya subió**, aunque siga en borrador en el servidor. Decisión del dueño.
2. **Editar una venta ya aplicada en Microsip.** Idem.
3. **`PUT /v2/ventas/{id}/lineas`** y cualquier edición contra el servidor. La app no lo consume y no lo va a
   consumir aquí.
4. **El camino legado `ventas-locales`** (`LocalSalesApi.kt`, `LocalSaleSyncHandler.kt`,
   `enqueueLocalSaleUpdate`). No se revive; se cierra con una guarda de build.
5. **Ediciones concurrentes entre dos teléfonos / ETag / `updated_at`.** Un teléfono, una venta que nunca salió.
6. **Agregar o quitar fotos durante la corrección.** *Corte consciente por testabilidad* (regla del dueño: si no
   se puede probar bien, se saca): el guardado de imágenes de hoy escribe filas en Room **antes** del commit
   (`EditLocalSaleViewModel.kt:165-192`), lo que filtraría estado a medio editar al cuerpo del subidor, y cubrir
   compresión + archivos físicos + borrado diferido bien probado no cabe. En la corrección las fotos se ven,
   **no se tocan**. La venta sin imágenes ya es fallo permanente del worker (`PendingLocalSalesWorker.kt:133-143`),
   así que no tocarlas también protege ese invariante.
7. **Borrar una venta local.** (`NewLocalSaleViewModel.deleteSale` borra productos e imágenes pero **no** la fila
   de la venta — defecto preexistente, medido, fuera de alcance.)
8. **Publicar a la flota.** APK de prueba en el teléfono del dueño, con `devlocal` (`CLAUDE.md` §4: `devserver`
   está RETIRADO y apunta a producción).
9. **Mover los 6 componentes legados del formulario** (`ProductSelectionBottomSheet`, `CitySelector`,
   `ZoneSelectorSimple`, `CreateComboDialog`, `ImageViewerDialog`, `ProductSaleSummary`) a un módulo nuevo. Ver
   "Qué pasa con la pantalla muerta".

---

## El mecanismo de la carrera (decidido aquí, no lo decide el implementador)

### Qué hay hoy, medido

- La venta nace en Room con UUID del teléfono y **se encola el subidor en el mismo flujo**
  (`NewLocalSaleViewModel.kt:356` → `WorkManagerUtils.kt:98-123`, `enqueueUniqueWork(..., KEEP)`).
- Además hay un **barrido**: `LocalSalesPendingSynchronizer.kt:28-31` reencola **con `replace = true`** toda
  venta con `ENVIADO = 0` (`PendingWorkSyncFactory.kt:38-41` → `LocalSaleDataSource.kt:59-61` →
  `LocalSaleDao.getSalesByStatus(false)`), y corre **en cada apertura de sesión** (`AppNavigation.kt:237`).
- `LocalSaleEntity.kt:13-46` no tiene ninguna columna de estado de edición, versión ni `updated_at`.

### La decisión: candado único con arrendamiento, autoridad en la fila

> **Actualizado en la ronda 3 de revisión de Task 1** (implementación real, `LocalSaleDao.kt`). La versión
> original de esta sección (reclamo de solo-edición, `EDIT_CLAIM_ID`/`EDIT_CLAIMED_AT`) se quedó corta: la
> revisión encontró una carrera adicional entre editar y SUBIR que ese diseño no cerraba. Lo que sigue es lo
> que el código realmente implementa.

Cinco columnas nuevas en `local_sale`:

| columna | tipo | significado |
|---|---|---|
| `CLAIM_ID` | `TEXT` nullable | UUID del candado vivo. `NULL` = nadie tiene la fila. Es UN SOLO candado: lo puede tomar la edición o la subida, nunca las dos — mutua exclusión **por construcción** (una sola columna), no por dos predicados que alguien tenga que mantener sincronizados. |
| `CLAIM_KIND` | `TEXT` nullable | `'EDIT'` o `'UPLOAD'`. Dice qué arrendamiento aplica para decidir si `CLAIM_ID` venció (cada tipo tiene el suyo). |
| `CLAIMED_AT` | `INTEGER` nullable | epoch ms en que se acuñó el candado. |
| `REVISION` | `INTEGER NOT NULL DEFAULT 0` | correcciones commiteadas. Sólo sube. |
| `CORRECCION_NO_ENVIADA` | `INTEGER NOT NULL DEFAULT 0` | ver "Qué pasa si la subida ya empezó" abajo — marca la divergencia cuando el 2xx de una subida vuelve con el cuerpo viejo porque una corrección se commiteó mientras el POST seguía en vuelo. |

Los predicados de expiración necesitan **los dos arrendamientos** (`editLeaseMs`, `uploadLeaseMs`) en las tres
sentencias que los usan: el candado vigente en la fila puede ser de cualquiera de los dos tipos, así que
decidir si venció exige mirar `CLAIM_KIND` y aplicar el que corresponde. Defensa en profundidad en los tres:
`CLAIMED_AT IS NULL` y `CLAIM_KIND` nulo o con un valor que no es `'EDIT'`/`'UPLOAD'` también cuentan como
vencido — "una captura nunca se retiene para siempre" cubre también un estado corrupto, no sólo el vencimiento
normal.

Nueve sentencias, cada una **un solo UPDATE/SELECT** (SQLite las serializa; ahí está la atomicidad, no en
Kotlin):

1. **Reclamar para EDICIÓN** (al abrir el editor):
   ```sql
   UPDATE local_sale SET CLAIM_ID = :claimId, CLAIM_KIND = 'EDIT', CLAIMED_AT = :now
   WHERE LOCAL_SALE_ID = :id
     AND ENVIADO = 0
     AND (LAST_UPLOAD_PERMANENT IS NULL OR LAST_UPLOAD_PERMANENT = 0)
     AND (
       CLAIM_ID IS NULL
       OR CLAIMED_AT IS NULL
       OR COALESCE(CLAIM_KIND, '') NOT IN ('EDIT', 'UPLOAD')
       OR CLAIM_KIND = 'EDIT'
       OR (CLAIM_KIND = 'UPLOAD' AND CLAIMED_AT <= :now - :uploadLeaseMs)
     )
   ```
   Devuelve filas afectadas. **0 = no se puede corregir** (venta enviada, fallo permanente, o candado de
   SUBIDA vigente) → el editor ni se abre, se muestra el aviso.

   **Actualizado en Task 3, decisión del orquestador (no relitigar):** un candado `EDIT` VIVO ya **no** bloquea
   — es REENTRANTE: se toma de nuevo, acuñando un `CLAIM_ID` fresco (no hay `editLeaseMs` en esta sentencia; sí
   sigue habiendo `uploadLeaseMs`, sin cambio). El dominio (`evaluarCorregibilidad`,
   `:feature:ventaCorreccion`, Task 2) ya trataba un `EDIT` vivo como `Corregible`; el DAO no lo seguía, así
   que si la app moría con el editor abierto el dueño quedaba 30 min sin poder corregir su propia venta. En el
   alcance de este plan (un teléfono, una venta que nunca salió) un `EDIT` vivo sólo puede ser una sesión
   anterior del editor en el MISMO teléfono. Consecuencia, probada con nombre propio en
   `LocalSaleClaimDaoTest`: la sesión vieja pierde su `claimId` — su `commitEditGuard` posterior devuelve 0
   filas y no escribe nada. `claimForUpload` NO cambia: sigue rechazando cualquier candado `EDIT` vigente (la
   corrección gana sobre el subidor, sin condición).

2. **Reclamar para SUBIDA** (`claimForUpload`, simétrico): lo toma el subidor justo AL ENTRAR, ANTES de leer
   nada de la venta (paso 4 abajo — el orden importa, ver ahí el porqué). Mismo predicado de expiración que
   (1), invertido: rechaza si hay CUALQUIER candado vigente — un candado de EDICIÓN (la corrección gana, el
   subidor se frena con `Result.retry()`) o un candado de SUBIDA de otro intento en vuelo (evita que dos
   intentos del mismo worker, o un reintento superpuesto, pisen la misma venta); acepta si el candado vigente
   venció, sea del tipo que sea.

3. **Frenar al subidor por edición**: `PendingLocalSalesWorker.doWork()`, justo después de cargar la venta
   (`PendingLocalSalesWorker.kt:110-119`), si hay candado de EDICIÓN vivo → `Result.retry()` **sin tocar la
   red**. `retry` y no `failure`: WorkManager conserva el trabajo y su backoff; la venta nunca se suelta.

4. **El orden correcto: candado ANTES de leer el cuerpo.** `claimForUpload` → snapshot (`REVISION`) → leer
   productos/combos/imágenes y armar el cuerpo → `POST` → `markSentAndCloseEdit(revisionAtClaim)`. Si
   `claimForUpload` devuelve 0, `Result.retry()` **sin tocar la red** — ni siquiera se leen productos/combos.
   Este orden reemplaza al de la versión original del plan (que leía el cuerpo primero y reclamaba justo
   antes del `POST`, después de armarlo): con ese orden viejo el editor podía commitear MIENTRAS el worker
   arma el cuerpo, y el `POST` salía con un cuerpo viejo o mezclado — una divergencia evitable. Con el
   candado tomado ANTES de leer, el editor no puede commitear mientras el cuerpo se arma (su propio
   `claimForEdit` lo rechazaría), así que la única divergencia posible que queda es la del arrendamiento de
   subida venciendo con el `POST` YA en vuelo — la que `CORRECCION_NO_ENVIADA` hace visible (paso 6, y "Qué
   pasa si la subida ya empezó" abajo). El snapshot `(CLAIM_ID, REVISION, ENVIADO)` que el worker toma justo
   después de `claimForUpload` es el que se pasará a `markSentAndCloseEdit` cuando vuelva el 2xx.

   **Pendiente de la Task 4, no de Task 1**: el subidor debe RENOVAR el arrendamiento de subida (latido
   periódico desde el worker mientras el `POST` sigue en vuelo) — sin esto, los 180 s del arrendamiento
   vencen en cualquier subida que tarde más que eso, y toda subida lenta (no sólo una colgada) dispara la
   carrera que `CORRECCION_NO_ENVIADA` detecta. `CORRECCION_NO_ENVIADA` es la red de seguridad para cuando,
   aun con el latido, algo se cuela; no es el reemplazo del latido.

5. **Guardar** (guardia va **primero**, en la misma transacción):
   ```sql
   UPDATE local_sale SET CLAIM_ID = NULL, CLAIM_KIND = NULL, CLAIMED_AT = NULL, REVISION = REVISION + 1
   WHERE LOCAL_SALE_ID = :id AND CLAIM_ID = :claimId AND ENVIADO = 0
   ```
   Si devuelve 0 filas se lanza excepción → Room revierte la transacción entera → **no se escribe nada** y la UI
   dice `Ya se envió`. Si devuelve 1, en la misma transacción van los campos (`LocalSaleDao.kt`), el merge de
   productos/combos (que conserva `SERVER_UUID` — LA BASE MANDA, no lo que traiga el formulario) y
   `clearUploadFailure`.

6. **Marcar enviada, con la divergencia si la hay** (`markSentAndCloseEdit`, un solo `UPDATE`):
   ```sql
   UPDATE local_sale SET
       ENVIADO = 1,
       CLAIM_ID = NULL,
       CLAIM_KIND = NULL,
       CLAIMED_AT = NULL,
       CORRECCION_NO_ENVIADA = CASE
           WHEN REVISION != :revisionAtClaim THEN 1
           ELSE CORRECCION_NO_ENVIADA
       END
   WHERE LOCAL_SALE_ID = :id
   ```
   `revisionAtClaim` es el `REVISION` del snapshot que el worker tomó en el paso 4, ANTES del POST. Siempre
   marca `ENVIADO = 1` (el 2xx prueba que el servidor tiene la venta) y siempre cierra el candado, **sea de
   quien sea** — ver "Qué pasa si la subida ya empezó" abajo para el porqué.

7. **Soltar** al cancelar: `UPDATE ... SET CLAIM_ID = NULL, CLAIM_KIND = NULL, CLAIMED_AT = NULL WHERE
   LOCAL_SALE_ID = :id AND CLAIM_ID = :claimId`. Funciona para cualquier tipo de candado.

8. **El barrido respeta el candado**: `getPendingSales()` pasa a `getUploadableSales(now, editLeaseMs,
   uploadLeaseMs)` — `ENVIADO = 0` **y sin candado vigente de ningún tipo**. Sin esto el barrido reencolaría
   con `replace=true` en cada apertura mientras el dueño escribe, o mientras una subida sigue en vuelo,
   reseteando el backoff y peleándose con el fence.

9. **Cancelar el trabajo encolado es sólo cortesía**: al reclamar se llama
   `cancelUniqueWork("sync_pending_local_sale_$id")`; al guardar o cancelar, `enqueue(..., replace = true)`.
   Ninguna de las dos es necesaria para la corrección. **La fila manda.**

### Qué pasa si la subida ya empezó cuando el usuario toca "guardar"

- Si el POST falla o no llega: el reintento ve el candado de subida y se frena, o (si ya venció) lo pierde
  ante un `claimForEdit` — la corrección gana.
- **La carrera que la ronda 2 no cerraba, y la ronda 3 sí**: subir un cuerpo multipart NO tiene un tope real
  de tiempo en el cliente hoy (`RetrofitClientFactory.kt` sólo fija `connectTimeout`/`readTimeout` de 60 s
  cada uno, ambos de INACTIVIDAD entre bytes, no un total; no hay `writeTimeout` propio ni `callTimeout` —
  una foto de ~3.4 MB a 10 KB/s tarda ~340 s sin que salte nada). El arrendamiento de subida puede vencer con
  el POST **todavía en vuelo**. Si eso pasa, el editor puede tomar el candado (`claimForEdit`) y commitear
  ANTES de que vuelva el 2xx — y el 2xx que llega después trae el cuerpo **VIEJO**.
- `markSentAndCloseEdit` (paso 6) cierra esta ventana sin perder el caso en silencio: compara `REVISION`
  contra `revisionAtClaim` (el snapshot previo al POST). Si coinciden, nada divergió — `ENVIADO=1`, candado
  cerrado, sin marca. Si NO coinciden, alguien commiteó mientras el POST volaba: se marca
  `CORRECCION_NO_ENVIADA = 1` en la MISMA sentencia. La fila queda con `ENVIADO=1` (el servidor sí tiene la
  venta — no marcarlo la subiría otra vez) **y** la divergencia visible, nunca pisada en silencio. Quién
  muestra `CORRECCION_NO_ENVIADA` en pantalla y cómo se resuelve es de tareas posteriores a Task 1.
- Si el candado lo tomó el editor pero AÚN no commiteó (`REVISION` sin cambiar cuando vuelve el 2xx): sin
  marca — el guardado posterior del editor va a fallar solo, por su propio guardia (paso 5 lee `ENVIADO=1` y
  devuelve 0 filas), y el usuario ve `Ya se envió`.
- **Cierre pendiente, de la Task 4**: la solución completa no es sólo detectar la divergencia, es evitarla —
  el subidor debe **renovar** el arrendamiento de subida mientras el POST sigue en vuelo, para que una subida
  lenta pero viva no dispare esta carrera en la práctica. `CORRECCION_NO_ENVIADA` es la red de seguridad para
  cuando, aun así, ocurra. Decisión ya tomada: **no** agregar un `callTimeout` — un tope total haría fallar
  para siempre una subida lenta que sí avanza, cambiando una carrera por una venta que nunca llega.

### Por qué no las otras opciones

- **Sólo cancelar/retener el trabajo**: `cancelUniqueWork` no detiene un `doWork()` en curso y el barrido de
  `AppNavigation.kt:237` lo reencola en la siguiente apertura. Pierde la carrera por diseño.
- **Sólo versión optimista** (comparar `REVISION` al commitear): protege el commit pero **no impide** que el
  subidor se lleve la venta sin corregir mientras el dueño escribe — que es justo el caso que pidió.
- **Candado sin arrendamiento**: si la app muere con el editor abierto, o el proceso muere a media subida, la
  venta queda **retenida para siempre**. Cambia una carrera incómoda por dinero perdido. Inaceptable.
- **Dos columnas de candado independientes** (una para edición, otra para subida) en vez de una sola
  compartida: la mutua exclusión dependería de que CADA método nuevo recuerde chequear la otra columna. Una
  sola columna la hace imposible de olvidar — no hay "el otro chequeo" que alguien pueda no escribir.
- **`callTimeout` para acotar la subida**: cambia "el candado a veces vence con el POST en vuelo" (detectado
  y marcado por `CORRECCION_NO_ENVIADA`) por "la subida falla para siempre si tarda más del tope" — peor: una
  venta que nunca llega, en vez de una corrección que a veces necesita reintentarse.
- **Borrar y recrear con UUID nuevo**: rompe la idempotencia de punta a punta y duplica si el servidor tenía la
  original. Prohibido por `ENTREGA_GARANTIZADA`.

### El merge de líneas (y por qué no se borra todo)

`EditLocalSaleViewModel.kt:320-342` borra **todos** los productos y reinserta, y
`LocalSaleComboDao.replaceCombosForSale` (`:30-36`) hace lo mismo con combos. Ese patrón es el que del lado Go
costó seis defectos. Aquí, en la misma transacción del guardado:

- Línea que **sobrevive** (misma `(LOCAL_SALE_ID, ARTICULO_ID)` / `(COMBO_ID, LOCAL_SALE_ID)`): se actualizan
  cantidad y precios y **conserva su `SERVER_UUID`** (el worker ya lo acuñó en un intento previo,
  `PendingLocalSalesWorker.kt:191-218`).
- Línea **nueva**: se inserta con `SERVER_UUID = NULL`; el worker la acuña.
- Línea **quitada**: se borra.

Invariante: *corregir una venta no reacuña la identidad de las líneas que no cambiaron*.

---

## Qué pasa con la pantalla muerta (decidido)

**Medido**: `EditSaleScreen.kt` son **1361 líneas** con punto de entrada en `:92`, y depende de seis componentes
legados (`:76-81`: `CitySelector`, `CreateComboDialog`, `ProductSaleSummary`, `ProductSelectionBottomSheet`,
`ImageViewerDialog`, `ZoneSelectorSimple`) más `WarehouseViewModel`, `SaleProductsViewModel` y
`LocalAuthViewModel`, todos en `app/features/*`. La ruta existe (`AppNavigation.kt:127-130`, `:541-548`) y nadie
navega a ella (`SaleDescriptionScreen.kt:86-93` lo dice).

**Decisión: se revive donde está, pero degradada a cáscara.** Todo el juicio sale de ahí:

- `EditLocalSaleViewModel.kt` (419 líneas) **se borra**. Su guardado apunta al backend legado
  (`:20` importa `enqueueLocalSaleUpdate`, `:368-376` lo llama), rota la llave de idempotencia (`:312`) y pone
  `ENVIADO = false` a ciegas (`:290`) — las tres cosas están prohibidas por este plan.
- Lo reemplaza `CorreccionVentaViewModel`, `@HiltViewModel` en `:feature:ventaCorreccion`. `EditSaleScreen` ya
  usa `hiltViewModel()` (`:94`), así que el precedente existe.
- `EditSaleScreen` conserva **sólo el formulario** (campos, selectores, resumen) y delega claim/guardar/cancelar.
  Neto: el archivo legado **pierde** líneas, no gana ninguna regla.
- Mover esos 6 componentes a un módulo nuevo es un refactor de semanas que además tocaría archivos de otras
  features — fuera de alcance, y dicho aquí para que no se relitigue.
- Se blinda con `checkNoLegacySaleEdit` (Task 5): el build **falla** si `app/src/main` vuelve a nombrar
  `enqueueLocalSaleUpdate`.
- **Dónde sí va código nuevo**: `:core:database` (columnas, migración, DAO atómico) y `:feature:ventaCorreccion`
  (dominio, casos de uso, avisos con goldens). Bonus medido: `:app` **no aplica Roborazzi**
  (`app/build.gradle.kts:3-16`), así que los goldens sólo pueden vivir en el módulo nuevo.

---

## Estrategia de pruebas

Herramientas **verificadas en el repo** (no se agrega ninguna dependencia nueva):

| capa | herramienta, medida | dónde |
|---|---|---|
| 1. dominio puro | JUnit4 + `kotlinx-coroutines-test` (`libs.versions.toml:167`) | `:feature:ventaCorreccion:testDebugUnitTest` |
| 2. persistencia Room | `RoomTestBase` (in-memory) | `:core:database`, `:app` |
| 3. carrera | `TestListenableWorkerBuilder` (`work-testing`, `libs.versions.toml:89`, ya en uso en `PendingLocalSalesWorkerV2Test.kt:6,103-141`) + `runTest` + **seam de red** | `:app:testDevlocalDebugUnitTest` |
| 4. cuerpo real | fake `VentasApi` + `RequestBody.writeTo(okio.Buffer())` + parseo JSON | `:app` |
| 5. idempotencia | mismo harness; asserts sobre header y sobre la columna `IDEMPOTENCY_KEY` | `:app` |
| 6. UI | Roborazzi (`libs.versions.toml:141-143`) vía `MspScreenshotTest.capture(name, dark, fontScale)` (`core/designsystem/.../MspScreenshotTest.kt:84-110`) + `androidx-ui-test-junit4` | `:feature:ventaCorreccion` |
| 7. mutación | **no hay aparato automático** — no existe `pitest`/`stryker` en el repo. Protocolo **manual** de mutaciones fijas (Task 7) | — |
| 8. gates manuales | teléfono real + `devlocal` (`CLAUDE.md` §4) | — |

**El seam que hace determinista la carrera, y es la clave de todo**: el worker recibe `ventasApi` por
constructor (`PendingLocalSalesWorker.kt:64-65`) y los tests ya le inyectan un objeto fake
(`PendingLocalSalesWorkerV2Test.kt:143-176`). Entonces **el momento "la subida ya empezó" es el cuerpo del fake
`crearVenta`**: dentro de esa función suspend el test ejecuta el reclamo o el guardado del usuario, y después
devuelve 2xx o lanza. Un solo hilo, cero relojes, cero `sleep`, interleaving exacto y repetible.

**Control positivo (regla del dueño)**: cada regla nueva lleva una prueba que sólo pasa con la regla puesta. En
Task 7 se recorre la lista completa de mutaciones sembradas y se confirma el rojo de cada una, una por una.

**Una prueba que no puede fallar no es una prueba**: en cada tarea, la columna *rojo sembrado* dice la mutación
exacta y qué prueba debe ponerse roja.

---

## Task 1 — `:core:database`: columnas del reclamo, migración 29→30 y DAO atómico

**Entrega**: la capacidad de reclamar (edición o subida), commitear y soltar una corrección de forma atómica,
y de listar sólo las ventas realmente subibles.

> **DONE, en tres rondas** (implementación real, no el diseño original). Ronda 1: reclamo de solo-edición
> (`EDIT_CLAIM_ID`/`EDIT_CLAIMED_AT`/`REVISION`). Ronda 2: la revisión encontró una carrera edición-vs-subida
> que ese diseño dejaba abierta; se renombra a un candado único (`CLAIM_ID`/`CLAIM_KIND`/`CLAIMED_AT`) con
> mutua exclusión por construcción y nace `claimForUpload`. Ronda 3: la revisión encontró que el arrendamiento
> de subida no tenía tope real (subir un cuerpo multipart no tiene timeout de OkHttp que lo acote); se agrega
> `CORRECCION_NO_ENVIADA` y `markSentAndCloseEdit` recibe la `REVISION` del snapshot previo al POST para
> detectar y marcar la divergencia en vez de perderla en silencio. Ver "El mecanismo de la carrera" arriba
> para el diseño completo y actualizado; abajo queda sólo lo que cambió de forma respecto al original.

**Archivos (exclusivos de esta tarea)**
- `core/database/src/main/kotlin/.../entities/LocalSaleEntity.kt` — `CLAIM_ID: String? = null`,
  `CLAIM_KIND: String? = null`, `CLAIMED_AT: Long? = null`, `REVISION: Int = 0`
  (`@ColumnInfo(defaultValue = "0")`), `CORRECCION_NO_ENVIADA: Boolean = false`
  (`@ColumnInfo(defaultValue = "0")`).
- `core/database/src/main/kotlin/.../migrations/Migration29to30.kt` — cinco `ALTER TABLE ... ADD COLUMN`
  (`REVISION` y `CORRECCION_NO_ENVIADA` con `INTEGER NOT NULL DEFAULT 0`). Patrón y tono de comentario:
  `Migration28to29.kt`.
- `core/database/src/main/kotlin/.../AppDatabase.kt` — `version = 30` y la migración en `addMigrations(...)`.
- `core/database/schemas/com.example.msp_app.core.database.AppDatabase/30.json` — **generado por el build**,
  commiteado.
- `core/database/src/main/kotlin/.../entities/SaleClaimSnapshot.kt` — proyección `(CLAIM_ID, REVISION,
  ENVIADO)`; vive en `entities/` (no en `dao/`) porque `detekt.ConstructorParameterNaming` sólo excluye ese
  paquete.
- `core/database/src/main/kotlin/.../dao/localsale/LocalSaleDao.kt` — `claimForEdit(saleId, claimId, now,
  editLeaseMs, uploadLeaseMs)`, `claimForUpload(...)` (mismos parámetros, simétrico), `releaseClaim`,
  `commitEditGuard`, `markSentAndCloseEdit(saleId, revisionAtClaim)` (marca `CORRECCION_NO_ENVIADA` si
  `REVISION` ya no coincide con `revisionAtClaim`), `getUploadableSales(now, editLeaseMs, uploadLeaseMs)`,
  `getSaleClaimSnapshot(saleId)`.
- `core/database/src/main/kotlin/.../dao/localsale/LocalSaleProductDao.kt` y `LocalSaleComboDao.kt` — merge
  que conserva `SERVER_UUID` (LA BASE MANDA, no lo que traiga la entrada) y rechaza `ARTICULO_ID`/`COMBO_ID`
  duplicado con `require(...)`.
- `core/database/src/test/kotlin/.../migration/Migration29to30Test.kt`, `SchemaIntegrityTest.kt`,
  `.../AppDatabaseTest.kt`, `.../dao/localsale/LocalSaleClaimDaoTest.kt`, `.../dao/localsale/LineMergeDaoTest.kt`.

**Invariante que defiende**: el candado y su cierre son atómicos, es mutuamente excluyente entre edición y
subida por construcción, una línea que sobrevive a la corrección conserva su `SERVER_UUID`, y una divergencia
entre lo que el servidor recibió y lo que el teléfono cree que se corrigió queda siempre visible, nunca
pisada en silencio.

**Capas**: 1 (predicados como SQL), 2.

**Cómo se prueba** (resumen; el detalle completo vive en el DAO y sus pruebas)
- Migración: base v29 con una venta pendiente → migrar → las cinco columnas existen, `REVISION=0`,
  `CORRECCION_NO_ENVIADA=0`, `CLAIM_ID IS NULL`, **y la venta y sus hijos siguen ahí** (patrón
  `PaymentSurvivalMigrationTest.kt`). Además: la migración está REGISTRADA de verdad en
  `AppDatabase.buildDatabase` (no sólo pasada a mano a `MigrationTestHelper`), y una instalación NUEVA declara
  el mismo `DEFAULT 0` que una migrada (`AppDatabaseTest`).
- `claimForEdit`/`claimForUpload`: 1 sobre venta libre; 0 si `ENVIADO=1`; 0 (sólo `claimForEdit`) si
  `LAST_UPLOAD_PERMANENT` es `true` o `false` (los dos, no sólo uno); 0 si ya hay candado vigente de
  CUALQUIER tipo (mutua exclusión); 1 si el candado vigente venció, en su propio milisegundo exacto (con
  `FakeClock`, nunca reloj real) — y también si `CLAIMED_AT` o `CLAIM_KIND` son nulos o desconocidos (defensa
  en profundidad, "nunca se retiene para siempre").
- `commitEditGuard` con otro `claimId` → 0 filas y la fila intacta.
- `markSentAndCloseEdit(saleId, revisionAtClaim)`: siempre `ENVIADO=1` y candado cerrado (sea de quien sea);
  `CORRECCION_NO_ENVIADA=1` sólo si `REVISION` ya no coincide con `revisionAtClaim` — leídos en la misma
  lectura.
- Merge: 3 productos, se corrige a 2 (uno con cantidad distinta, uno nuevo, uno quitado) → el que sobrevive
  conserva su `SERVER_UUID` DE LA BASE (aunque la entrada traiga otro valor), el nuevo lo tiene `NULL`, el
  quitado no está. Mismo caso para combos. `ARTICULO_ID`/`COMBO_ID` duplicado en la entrada → excepción.
- **Corregir dos veces seguidas** (el caso que más preocupa al dueño): claim → commit → claim → commit;
  `REVISION` termina en 2 y las líneas son las de la segunda corrección, sin filas huérfanas.

**Rojo sembrado** (lista no exhaustiva; cada método tiene el suyo — ver `LocalSaleClaimDaoTest.kt` y
`LineMergeDaoTest.kt` para el catálogo completo)
- Quitar `AND ENVIADO = 0` de `claimForEdit` → rojo en `claim rechaza venta ya enviada`.
- Cambiar `<=` por `<` en la rama EDIT o UPLOAD de cualquiera de las tres sentencias de expiración → rojo en
  la prueba de frontera exacta correspondiente.
- Quitar la rama `CLAIM_KIND = 'UPLOAD'` de `claimForEdit` (o `'EDIT'` de `claimForUpload`) → rojo en la
  prueba de mutua exclusión correspondiente.
- Quitar `COALESCE(CLAIM_KIND, '') NOT IN (...)` de cualquiera de las tres sentencias → rojo en la prueba de
  `CLAIM_KIND` nulo/desconocido de esa sentencia.
- Hacer que `markSentAndCloseEdit` marque `CORRECCION_NO_ENVIADA` sin comparar `REVISION` (o nunca la marque)
  → rojo en `marcar enviada con REVISION igual/distinta`.
- Volver el merge a borrar-y-reinsertar, o dejar que la entrada pise el `SERVER_UUID` de la base → rojo en
  `linea que sobrevive conserva...`.
- Quitar `@ColumnInfo(defaultValue = "0")` de `REVISION` o `CORRECCION_NO_ENVIADA` → rojo en la prueba de
  instalación nueva de `AppDatabaseTest`.
- Quitar `MIGRATION_29_30` de `addMigrations(...)` → rojo en la prueba de registro real de
  `Migration29to30Test`.

**Medido, no asumido**: Room 2.6.1 SÍ devuelve `Int` (filas afectadas) de un `@Query` UPDATE `suspend fun` —
`claimForEdit`/`claimForUpload`/`commitEditGuard`/`releaseClaim` lo usan directo, sin el plan B de
`@Transaction fun` que releyera la fila.

**Duda dejada abierta para quien implemente Task 4**: `markSentAndCloseEdit` no recibe `CLAIM_ID` — sólo
`REVISION`, porque cierra el candado "sea de quien sea" (sin filtrar por dueño) y Room exige que todo
parámetro de un `@Query` aparezca en la sentencia. Si el diseño real necesita `CLAIM_ID` para algo (auditoría,
log), es una decisión pendiente de esa tarea.

---

## Task 2 — Nace `:feature:ventaCorreccion` (esqueleto verde + dominio puro)

**Entrega**: el módulo existe, está en la compuerta, y contiene la **decisión** de si una venta se puede
corregir, expresada como función pura.

**Archivos (exclusivos)**
- `settings.gradle.kts` — `include(":feature:ventaCorreccion")`.
- `feature/ventaCorreccion/build.gradle.kts` — plantilla exacta de `feature/configuracion/build.gradle.kts`
  (`msp.android.library`, `msp.android.compose`, `msp.hilt`, `msp.detekt`, `msp.kover`, ktlint,
  `alias(libs.plugins.roborazzi)`), namespace `com.example.msp_app.feature.ventacorreccion`, deps
  `:core:database`, `:core:common`, `:core:designsystem`; `testImplementation(project(":core:testing"))`.
- `build.gradle.kts` (raíz) — `:feature:ventaCorreccion:{ktlintCheck,testDebugUnitTest,detekt}` en
  `prePushCheck` (`:392-480`). `verifyRoborazziDebug` y `koverVerifyDebug` **no** entran todavía: sin goldens
  grabados el gate fallaría.
- `.../domain/EstadoCorreccion.kt` — `Corregible` | `YaSeEnvio` | `LaRevisaLaOficina`, y
  `evaluarCorregibilidad(enviado, permanente, claimId, claimedAt, ahora, lease): EstadoCorreccion`.
- `.../domain/ReclamoDeEdicion.kt` — arrendamiento (`LEASE_MS = 30 min`), `estaVivo(ahora)`.
- `.../domain/MergeDeLineas.kt` — dados actuales y deseadas, produce `(aActualizar, aInsertar, aBorrar)`
  conservando `SERVER_UUID`. Pura, sin Room.
- `.../domain/TextosCorreccion.kt` — las cuatro cadenas, en un solo lugar.
- Tests espejo en `src/test/kotlin/...`.
- Fixture propio mínimo del módulo (**`TestDataFactory` vive en `app/src/test/.../test-fixtures/TestDataFactory.kt:15`
  y no es visible desde aquí** — medido).

**Invariante**: la regla de editabilidad es **una sola** y no depende de Android ni de Room.

**Capas**: 1 (con control positivo), y prepara la 6.

**Cómo se prueba**
- Tabla exhaustiva de `evaluarCorregibilidad`: las 3 dimensiones (`enviado`, `permanente`, reclamo
  vivo/vencido/ausente), todas las combinaciones, con `FakeClock`.
- **Control positivo**: `evaluar_devuelve_YaSeEnvio_solo_si_enviado` construye el caso que **únicamente** pasa
  con la rama puesta; borrar la rama lo pone rojo (se verifica en Task 7).
- Frontera del arrendamiento: exactamente `lease` → vencido; `lease - 1ms` → vivo. Sin reloj real.
- `MergeDeLineas`: sobrevive / nueva / quitada / lista vacía / la misma lista dos veces (idempotente).
- Textos: cada cadena tiene entre 2 y 4 palabras, **arranca con mayúscula**, sin punto final, y **no contiene
  "ciclo"** — como prueba, no como comentario.

**Rojo sembrado**: cambiar `LEASE_MS` a `Long.MAX_VALUE` → rojo en la frontera del arrendamiento; meter la
palabra "ciclo" en una cadena → rojo en el test de textos; bajar una cadena a minúscula inicial → rojo también.

---

## Task 3 — Casos de uso y adaptadores de la corrección

**Entrega**: reclamar, guardar y cancelar funcionando de punta a punta sobre Room, sin UI.

**Archivos (exclusivos)**
- `.../domain/port/VentaLocalCorreccionPort.kt`, `.../domain/port/RelojPort.kt`,
  `.../domain/port/ReencolarSubidaPort.kt`.
- `.../domain/usecase/ReclamarCorreccion.kt`, `GuardarCorreccion.kt`, `CancelarCorreccion.kt`.
- `.../data/RoomVentaLocalCorreccionAdapter.kt` (sobre el DAO de Task 1).
- `.../ui/CorreccionVentaViewModel.kt` (`@HiltViewModel`) + `CorreccionUiState.kt`.
- `.../di/VentaCorreccionModule.kt`.
- Tests + fakes (`FakeReencolar` que **cuenta** llamadas, `FakeReloj`).

**Invariante**: guardar sin reclamo vigente **no escribe nada**, y el reencolado es una optimización, nunca un
requisito de correctitud.

**Capas**: 1, 2, y el primer escenario de la 3.

**Cómo se prueba** (Room in-memory + fakes)
- Feliz: reclamar → guardar → campos, productos y combos correctos, `REVISION=1`, reclamo cerrado, fallo previo
  limpiado, `IDEMPOTENCY_KEY` **sin cambios**.
- **Corregir dos veces seguidas**, a nivel caso de uso, incluyendo productos y combos distintos cada vez.
- Guardar con `claimId` ajeno → excepción, **nada escrito** (se compara la fila y los hijos contra el snapshot
  previo, campo por campo).
- Guardar sobre venta con `ENVIADO=1` → `YaSeEnvio`, nada escrito.
- Cancelar suelta el reclamo y deja los datos originales intactos.
- **Muerte de la app entre guardar y reencolar**: se ejecuta `GuardarCorreccion` con un `ReencolarSubidaPort`
  que lanza excepción (simula el proceso muerto justo después del commit); la transacción ya cerró, así que la
  corrección persiste; acto seguido se corre `LocalSalesPendingSynchronizer` con un enqueuer que registra, y la
  venta **aparece** en la lista. Demuestra que el commit basta.

**Rojo sembrado**: mover el `UPDATE` guardián al final de la transacción (en vez del principio) → rojo en
`guardar_con_reclamo_ajeno_no_escribe_nada`; quitar el `clearFailure` → rojo en el feliz.

---

## Task 4 — La compuerta del subidor y del barrido (la carrera, adversarial)

**Entrega**: el subidor respeta el reclamo, revalida antes del POST y cierra el reclamo al marcar enviado; el
barrido deja de recoger ventas reclamadas.

**Archivos (exclusivos)**
- `app/src/main/java/com/example/msp_app/workers/PendingLocalSalesWorker.kt` — fence tras `:110-119`;
  snapshot + revalidación justo antes de `:263`; `markSentAndCloseEdit` en lugar de `changeSaleStatus` en `:269`
  y `:332`; parámetro `leaseMs` con default, inyectable (como `nowEpochMillis`, `:78-79`).
- `app/src/main/java/com/example/msp_app/data/local/datasource/sale/LocalSaleDataSource.kt` — `getPendingSales`
  (`:59-61`) → `getUploadableSales(now, leaseMs)`; `changeSaleStatus` → `markSentAndCloseEdit`.
- `app/src/main/java/com/example/msp_app/core/sync/pendingwork/di/PendingWorkSyncFactory.kt:38-41` — pasa el
  reloj.
- `app/src/test/java/com/example/msp_app/workers/PendingLocalSalesWorkerV2Test.kt` — se **amplía** (no se
  reescribe): las 26 pruebas existentes siguen verdes.
- `app/src/test/java/com/example/msp_app/workers/CorreccionCarreraTest.kt` — **nuevo**, los escenarios
  adversariales.
- `app/src/test/java/com/example/msp_app/core/sync/pendingwork/data/synchronizers/LocalSalesPendingSynchronizerTest.kt`
  — caso "venta reclamada no se reencola".

**Invariante**: **nunca se sube un cuerpo a medio corregir, y nunca sobrevive una corrección local de una venta
que el servidor ya tiene.**

**Capas**: 3 entera, más 2.

**Cómo se prueba** — los cinco escenarios del dueño, cada uno determinista por el seam de red, **sin relojes**:

1. *El trabajador arranca a mitad de la edición*: reclamar → correr el worker → `Result.retry()` y el fake de
   `VentasApi` registra **cero** llamadas a `crearVenta`.
2. *El usuario guarda mientras la subida ya empezó*: el fake `crearVenta`, **en su cuerpo**, ejecuta
   `GuardarCorreccion` y luego devuelve 2xx → el worker marca enviada y cierra el reclamo → el guardado observa
   0 filas → **nada escrito**, estado `YaSeEnvio`, y en la base hay **una** venta con los datos originales.
   Variante: el fake lanza `IOException` en vez de 2xx → el guardado **sí** commitea y la siguiente corrida sube
   lo corregido.
3. *La app muere entre guardar y reencolar*: commit sin reencolar; correr el barrido; la venta aparece; correr el
   worker; sube corregida.
4. *Se corrige una venta que ya subió — imposible, demostrado*: `ENVIADO=1` → `claimForEdit` devuelve 0 → el
   caso de uso devuelve `YaSeEnvio` → se compara la fila **y sus hijos** contra el snapshot: idénticos. Y el
   camino por detrás: reclamo obtenido legítimamente, luego el worker triunfa, luego el guardado → rollback.
5. *Se corrige, se pierde la señal, vuelve la señal*: corrida 1 → `IOException` → `retry`; corregir; corrida 2 →
   2xx. Asertar: `crearVenta` se llamó **exactamente dos veces**, sólo la segunda devolvió 2xx, `ENVIADO=1` una
   sola vez, y el registro de fallo quedó limpio.
6. *Cuerpo mezclado*: el fake de `VentasApi` no se usa; en su lugar un `ComboLocalDataSource` de prueba que, al
   ser leído, dispara el reclamo → el worker revalida antes del POST → `retry`, cero POST. **Si no se puede
   inyectar ese datasource sin cambiar la forma del worker, se cubre en su lugar con una prueba directa de la
   revalidación** (cambiar `REVISION` entre snapshot y POST) y se anota la limitación en el commit. No inventar
   un refactor grande aquí.
7. Barrido: venta con reclamo vivo → `NothingPending`; con reclamo vencido → se reencola.

**Rojo sembrado**
- Quitar el fence del inicio → rojo en el escenario 1.
- Quitar la revalidación pre-POST → rojo en el 6.
- Volver `markSentAndCloseEdit` a `changeSaleStatus` → rojo en el 2 (queda un reclamo huérfano sobre una venta
  enviada).
- Devolver `getPendingSales` al `getSalesByStatus(false)` viejo → rojo en el del barrido.

---

## Task 5 — UI: entrada, cáscara, avisos y guarda contra el legado

**Entrega**: el dueño ve "Corregir venta" en una venta pendiente, corrige, y ve un aviso claro cuando ya no se
puede.

**Archivos (exclusivos)**
- `app/src/main/java/com/example/msp_app/features/sales/screens/SaleDescriptionScreen.kt:86-93` — el comentario
  se va, entra el botón, visible **sólo** si el estado es `Corregible`; si no, el aviso.
- `app/src/main/java/com/example/msp_app/features/sales/screens/EditSaleScreen.kt` — pasa a
  `CorreccionVentaViewModel`; se borran su guardado, su rotación de llave y su borra-y-reinserta. Reclama al
  entrar (`LaunchedEffect`), suelta al salir, guarda por el caso de uso. **No se toca el layout del formulario**
  (así no hay goldens que regrabar de él).
- `app/src/main/java/com/example/msp_app/features/sales/viewmodels/EditLocalSaleViewModel.kt` — **BORRADO**.
- `app/src/main/java/com/example/msp_app/navigation/AppNavigation.kt:541-548` — la ruta se conserva y ahora sí
  se navega hacia ella.
- `feature/ventaCorreccion/src/main/.../ui/components/AvisoNoCorregible.kt`, `BotonCorregir.kt`.
- `feature/ventaCorreccion/src/test/.../screenshot/CorreccionScreenshotTest.kt` + goldens en
  `src/test/screenshots/`.
- `feature/ventaCorreccion/src/test/.../ui/AvisoNoCorregibleTest.kt` (Compose UI test).
- `build.gradle.kts` (raíz) — `checkNoLegacySaleEdit` (calcado de `checkNoLegacyDateApi`, `:211-245`): falla si
  `app/src/main/**/*.kt` nombra `enqueueLocalSaleUpdate`; se suma a `prePushCheck`, junto con
  `:feature:ventaCorreccion:verifyRoborazziDebug` (ya hay goldens grabados).

**Invariante**: la edición nunca puede volver al backend legado, y **nunca** se ofrece corregir lo que no se
puede corregir.

**Capas**: 6, más la guarda de arquitectura.

**Cómo se prueba**
- Goldens: `BotonCorregir` y `AvisoNoCorregible` (dos textos) en **claro y oscuro** × **las tres escalas de
  letra** que ya usa el catálogo de `:core:designsystem` — el implementador **lee las escalas de los goldens
  `catalog_*` existentes y usa exactamente esas**. Grabar con `recordRoborazziDebug`, **mirar las imágenes**,
  prohibido subir el umbral.
- Compose UI test: con estado `YaSeEnvio` el botón **no existe** (`assertDoesNotExist`) y se ve `Ya se envió`;
  con `LaRevisaLaOficina`, `La revisa la oficina`; con `Corregible`, el botón existe y es clicable.
- `checkNoLegacySaleEdit`: se comprueba a mano introduciendo temporalmente la llamada prohibida y confirmando
  que el build falla (se documenta la salida en el commit).

**Rojo sembrado**: dejar el botón siempre visible → rojo en el UI test de `YaSeEnvio`; bajar una cadena a una
palabra → rojo en el test de textos de Task 2.

---

## Task 6 — Que suba CORREGIDA, una sola vez, con la misma llave

**Entrega**: la prueba de que lo que sale al cable lleva los valores nuevos y no duplica.

**Archivos (exclusivos, todos nuevos — no toca nada de Task 4)**
- `app/src/test/java/com/example/msp_app/integration/CorreccionSubeCorregidaTest.kt`
- `app/src/test/java/com/example/msp_app/integration/CorreccionIdempotenciaTest.kt`

**Invariante**: el cuerpo multipart real refleja la corrección, y corregir **no** crea una segunda venta.

**Capas**: 4 y 5.

**Cómo se prueba**
- El fake `VentasApi` captura `datos: RequestBody`; el test lo materializa con `writeTo(okio.Buffer())` y parsea
  el JSON. **Medido sobre el cuerpo, no sobre el objeto de dominio** (regla del dueño). Asertar: nombre, teléfono,
  dirección, parcialidad, total, y las líneas (artículo, cantidad, precios) con los valores **corregidos**; y
  que el artículo quitado **no aparece**.
- El `SERVER_UUID` de una línea que sobrevivió es el **mismo** en el cuerpo de la corrida 1 y en el de la 2.
- `Idempotency-Key` idéntico en ambas corridas **e igual a `LOCAL_SALE_ID`**; la columna `IDEMPOTENCY_KEY` sigue
  `NULL` tras la corrección.
- **El 2xx perdido**: corrida 1 sube de verdad pero el fake lanza `IOException` después de "responder";
  corregir; corrida 2 responde 409 y `obtenerVenta` la encuentra → `RECONCILED_VIA_GET` → `ENVIADO=1`,
  **una sola venta**, la corrección descartada, sin bucle de reintentos.
- Contador de invocaciones: en todos los escenarios, exactamente los POST esperados. "Sube una sola vez" es una
  aserción numérica, no una impresión.

**Rojo sembrado** (el más importante del plan): **reintroducir `resetForEditAndRetry` en el guardado** →
`CorreccionIdempotenciaTest` debe ponerse rojo por llave distinta. Y quitar el merge de `SERVER_UUID` → rojo en
la aserción de UUID estable.

---

## Task 7 — Verificación (la compuerta, la mutación manual y el teléfono)

**Entrega**: evidencia, no afirmaciones.

**Archivos**: ninguno de producción. Sólo el registro del resultado.

### 7.1 Compuerta automática

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew prePushCheck --rerun-tasks
```

Sin `--rerun-tasks` no prueba nada. Debe incluir, nuevas:
`:feature:ventaCorreccion:{ktlintCheck,detekt,testDebugUnitTest,verifyRoborazziDebug}` y `checkNoLegacySaleEdit`.
Fijar el piso de kover del módulo nuevo con el valor **medido** (`koverXmlReportDebug`), unos 3 puntos por debajo
— trinquete, no meta — y sumar `:feature:ventaCorreccion:koverVerifyDebug`.

Recordatorio medido: ninguna compuerta compila ni corre `androidTest`. Los E2E instrumentados **no** cubren nada
de este plan.

### 7.2 Mutación manual (no hay aparato automático — verificado)

Una por una: aplicar, correr **sólo** la prueba nombrada, confirmar el rojo, **revertir**, y confirmar con
`git status` + `git diff` que **no quedó ningún resto**.

| # | mutación | prueba que debe ponerse roja |
|---|---|---|
| 1 | quitar `AND ENVIADO = 0` de `claimForEdit` | `claim_rechaza_venta_ya_enviada` |
| 2 | quitar el cierre del reclamo de `markSentAndCloseEdit` | `marcar_enviada_cierra_el_reclamo` |
| 3 | quitar el fence del inicio del worker | carrera, escenario 1 |
| 4 | quitar la revalidación pre-POST | carrera, escenario 6 |
| 5 | mover el `UPDATE` guardián al final de la transacción | `guardar_con_reclamo_ajeno_no_escribe_nada` |
| 6 | reintroducir `resetForEditAndRetry` | `CorreccionIdempotenciaTest` |
| 7 | volver el merge a borrar-y-reinsertar | `linea_que_sobrevive_conserva_server_uuid` |
| 8 | `LEASE_MS = Long.MAX_VALUE` | frontera del arrendamiento |
| 9 | `getPendingSales` vuelve a `getSalesByStatus(false)` | barrido con reclamo vivo |
| 10 | botón "Corregir venta" siempre visible | UI test de `YaSeEnvio` |

### 7.3 Gates manuales en el teléfono (no automatizables)

APK **devlocal** (`LOCAL_API_HOST` en `local.properties`; `127.0.0.1` + `adb reverse tcp:3001 tcp:3001`).
**`devserver` está RETIRADO: su túnel es producción — un APK apuntando ahí escribiría en la base real.**

1. Instalar el APK de prueba en el teléfono del dueño. Confirmar que **no** se distribuye a la flota.
2. Modo avión.
3. Capturar una venta completa (cliente, dirección, plan, al menos 2 productos y 1 combo, fotos).
4. Verificar que aparece como pendiente y que **sí** ofrece `Corregir venta`.
5. Corregir: cambiar nombre, cambiar la cantidad de un producto, **quitar** un producto, **agregar** otro.
   Guardar. Ver `Corrección guardada`.
6. **Matar la app** (deslizar de recientes). Reabrir. Verificar que la corrección **sigue** ahí.
7. Corregir **otra vez** (el caso de las dos correcciones seguidas). Guardar.
8. Quitar el modo avión. Esperar a que suba.
9. En el servidor: **existe una sola venta** con ese UUID, con los valores de la **segunda** corrección, con las
   líneas correctas y sin la línea quitada.
10. Con señal ya activa: abrir la venta ya subida y verificar que **no** ofrece corregir, y que dice
    `Ya se envió`.
11. Adversarial: capturar otra venta sin señal, abrir el editor, **con el editor abierto** quitar el modo avión,
    esperar 30 s, guardar. Anotar qué pasó: o bien guarda y sube corregida, o bien dice `Ya se envió` y en el
    servidor está la versión sin corregir. **Cualquier otra cosa es un defecto** y hay que reportarlo.
12. Matar la app con el editor abierto y sin guardar, dejar señal, esperar >30 min (arrendamiento) y confirmar
    que la venta **sube** (no se queda retenida para siempre).

Nada se declara terminado sin la salida real de los comandos y el resultado de los 12 pasos escritos.

---

## Dudas declaradas (no medidas al planear)

1. **Las tres escalas de letra** de la matriz de goldens: Task 5 las lee de los goldens `catalog_*` existentes.
2. **El 422 `idempotency_key_mismatch`**: lo afirma un comentario del cliente (`LocalSaleDao.kt:101-107`); no se
   cruzó contra el repo Go. El plan no depende de ello, pero conviene confirmarlo antes de Task 6.
3. **`@Query` UPDATE suspend devolviendo `Int` en Room**: no se encontró ejemplo en este repo. Task 1 lo mide y
   aplica el plan B si hace falta.
4. **Defectos preexistentes, fuera de alcance, anotados**: un fallo permanente deja `ENVIADO=0` y el barrido lo
   reencola igual; y `NewLocalSaleViewModel.deleteSale` no borra la fila de la venta.
5. **`EditSaleE2ETest.kt` (1069 líneas) y `EditSaleStressTest.kt` (751)** ya ejercitan el ida y vuelta de
   edición a nivel Room aunque la pantalla esté desconectada. Task 1 y Task 3 deben **correrlas y mantenerlas
   verdes**; si el merge de líneas las pone rojas, eso **es información**, no ruido: hay que leer qué
   comportamiento asumían antes de cambiarlas.
