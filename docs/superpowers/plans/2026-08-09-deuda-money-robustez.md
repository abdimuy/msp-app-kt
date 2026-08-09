# Plan — Deuda money-adjacent (robustez SUPREMA)

> Cierre de los bugs pre-existentes que los audits de Plan 2 destaparon en el camino del dinero.
> Rama `feat/multimodulo-cimiento`. Cada tarea: **AUDITAR el contrato real → caracterizar (char-test old→new) → reescribir → revisar**.
> Estos bugs son PRE-EXISTENTES en `main` (no introducidos por la migración); se corrigen conscientemente como fix de bug documentado.

## Global Constraints (vinculan a TODAS las tareas)

- **Toolchain FIJO:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. AGP 8.10.1 / Kotlin 2.0.21 / KSP 2.0.21-1.0.27 / compileSdk 35 / Java 11 / Compose BOM 2024.09.00. Variante `devlocalDebug`. UN comando gradle a la vez (build lock). Paquete `com.example.msp_app` intocable.
- **Schema Room v27 INMUTABLE.** El DDL en disco (`core/database/schemas/.../27.json`) es contrato de datos de producción — NO se toca. La Tarea 4 modifica **solo la firma/tipo de retorno Kotlin** de dos `@Query` DAO (nullable) y borra un método muerto: NO cambia ninguna tabla/columna/índice ni el `identityHash`. Cualquier tarea que rompa el schema = BLOCKED.
- **Money-path:** cada cambio de comportamiento money = documentado con **test de caracterización old→new** (probar el comportamiento viejo roto y el nuevo correcto). Verificar el contrato real (DAO/API) antes de reescribir. Si no se puede verificar → BLOCKED.
- **Tests:** fakes-only (estado + spy/recording), **NUNCA MockK/Mockito**; Turbine para Flows; `kotlinx-coroutines-test`. Robustez SUPREMA: casos borde exhaustivos (excepción del DAO, lista vacía legítima vs error, fila ausente, partial-write).
- **Commits:** uno por tarea, conventional, subject en español, SIN atribución de Claude / SIN `Co-Authored-By`, SIN push, NUNCA `--no-verify`. El pre-commit (ktlint + `:build-logic` + `testDevlocalDebugUnitTest`) DEBE pasar.
- **Código inglés / strings usuario español / UI text 2-4 palabras / datos de test = nombres mexicanos realistas.** Hexagonal + YAGNI.
- **Review scaling:** Tareas 1-3 (money-output/lógica) → **2 revisores** (uno adversarial) + char-test. Tarea 4 (correctness non-money, display) → **1 revisor adversarial** + test.
- **Gate por tarea (el implementador lo corre):**
  ```
  ./gradlew ktlintCheck
  ./gradlew testDevlocalDebugUnitTest
  ./gradlew :core:database:testDebugUnitTest   # si la tarea toca DAOs (Tarea 4) o si hay tests Robolectric de DAO
  ./gradlew detekt
  ```
  NO se corre `connected*` (device) en estas tareas; el smoke e2e de dispositivo ya está cubierto por el cierre de Plan 2 (Task 9).

---

## Task 1 — [MONEY] Venta sin productos al sync: eliminar el exception-swallow + guard downstream

**Bug (pre-existente, camino de sync REAL — confirmado por el adversarial de Plan 2 T7):**
`SaleProductLocalDataSource.getProductsForSale` y `ComboLocalDataSource.getCombosForSale` envuelven la llamada al DAO en `try { ... } catch (e: Exception) { emptyList() }` — **tragan TODAS las excepciones y devuelven lista vacía**. Ese resultado alimenta directamente el camino de subida a Microsip:
- `features/sales/sync/LocalSaleSyncHandler.prepareRequest` (líneas 58-59): construye el request de la venta con `products`/`combos` obtenidos así, **sin guard** contra productos vacíos.
- `workers/PendingLocalSalesWorker` (líneas 167-168 y 200-201): mismo patrón en el worker de subida.

**Riesgo money:** si un DAO lanza (corrupción, disco lleno, constraint, migración a medias), la venta se sube a Microsip **con la lista de productos VACÍA** — una venta sin renglones = pérdida de dinero / inconsistencia con el inventario. Medium (requiere una excepción real del DAO), pero load-bearing porque el sync se va a confiar más en planes posteriores.

**Contrato real a verificar ANTES de reescribir:**
- Qué acepta el backend cuando `products`/`combos` van vacíos. Cruzar el backend Go en `/Volumes/M2-1TB/Developer/msp-api` (endpoint de creación de venta local / ventas) y el DTO del request en la app. Determinar si el server rechaza o acepta silenciosamente una venta sin renglones. Documentar el hallazgo en el reporte.
- Distinguir los DOS casos legítimos: (a) **lista vacía legítima** (una venta que de verdad no tiene ese tipo de renglón — p.ej. una venta solo-combos no tiene products, o viceversa) vs (b) **error del DAO** (la excepción). El fix NO debe convertir un vacío legítimo en fallo.

**Reescritura:**
1. `getProductsForSale` / `getCombosForSale`: **dejar de tragar la excepción**. Que la excepción se propague (o se envuelva en un tipo de error explícito), NUNCA colapsar un error a `emptyList()`. Un vacío legítimo debe venir del DAO como lista vacía real, no de un catch.
2. **Guard downstream (crux):** en `LocalSaleSyncHandler.prepareRequest` y en `PendingLocalSalesWorker` (ambos sitios), **no subir una venta cuyo conjunto de renglones quedó vacío por error**. Si tras cargar products+combos la venta no tiene NINGÚN renglón y eso no es un estado legítimo, el sync debe **fallar/reintentar** (retry del worker) en vez de subir una venta vacía. Definir la regla exacta según el contrato verificado (p.ej. "una venta pendiente siempre tiene ≥1 renglón entre products∪combos; si queda vacío ⇒ error, no subir"). Documentar la regla elegida.
3. Preservar el comportamiento de los ViewModels/lectura (EditLocalSaleViewModel, NewLocalSaleViewModel, DailyReportRepository) — ahí un fallo de lectura debe manejarse como error de UI, no como venta vacía; verificar que el cambio de propagación no rompa esos callers (envolver donde corresponda para no crashear la UI).

**Char-test old→new (obligatorio):**
- OLD roto: cuando el DAO lanza, el request se arma con productos vacíos (prueba el comportamiento actual).
- NEW correcto: cuando el DAO lanza, el sync NO sube la venta (falla/reintenta); cuando la lista es legítimamente vacía, se respeta.
- Casos borde: DAO lanza en products pero no en combos (y viceversa); ambos vacíos legítimos; un renglón de cada tipo; excepción envuelta correctamente.

**Archivos:** `SaleProductLocalDataSource.kt`, `ComboLocalDataSource.kt`, `features/sales/sync/LocalSaleSyncHandler.kt`, `workers/PendingLocalSalesWorker.kt`, y los callers de lectura que necesiten envoltura. Tests nuevos exhaustivos.

**Commit:** `fix(ventas): no subir venta a Microsip con productos vacíos por error del DAO`

---

## Task 2 — [MONEY] `PaymentsLocalDataSource.saveAll` usa `deleteAll()` (borraría pagos pendientes)

**Bug (pre-existente, dormido — 0 callers de producción del wrapper, confirmado en Plan 2 T6):**
`PaymentsLocalDataSource.saveAll(payments)` hace `paymentDao.deleteAll()` seguido de `paymentDao.saveAll(payments)`. `deleteAll()` es `DELETE FROM payment` (borra TODO, incluidos los pagos **pendientes de subir** `GUARDADO_EN_MICROSIP = 0`). Los callers reales (`CobranzaReconciler`, `CobranzaSyncManager`) usan `paymentDao.saveAll` directo y bypassean este wrapper — por eso está dormido — pero es una **mina money**: el primer caller que use el wrapper borraría dinero no sincronizado.

**Contrato real (ya verificado, confirmar):** `PaymentDao` YA tiene `deleteUploaded()` (línea 507: `DELETE FROM payment WHERE GUARDADO_EN_MICROSIP = 1`) — borra SOLO los ya confirmados por el servidor, preservando los pendientes. Es el método correcto para un refresh de caché que no debe perder dinero local.

**Reescritura:** en `PaymentsLocalDataSource.saveAll`, reemplazar `paymentDao.deleteAll()` por `paymentDao.deleteUploaded()`. Auditar que la semántica resultante (borrar solo subidos, luego `saveAll` con REPLACE del set del servidor) es coherente; documentar por qué preserva los pendientes.

**Char-test old→new (obligatorio):** sembrar 1 pago uploaded (`GUARDADO_EN_MICROSIP=1`) + 1 pago pending (`=0`); llamar `saveAll(nuevoSetDelServidor)`; **assert que el pago pending SOBREVIVE** en `getPendingPayments()` y que los uploaded se refrescan. El test OLD (con deleteAll) debe demostrar que el pending desaparecía. Usar DB Room real (Robolectric, `RoomTestBase`) o un `PaymentDao` fake fiel; preferir la DB real para probar el DELETE real.

**Archivos:** `data/local/datasource/payment/PaymentsLocalDataSource.kt` + test.

**Commit:** `fix(pagos): saveAll conserva pagos pendientes (deleteUploaded en vez de deleteAll)`

---

## Task 3 — [MONEY] Inserts de venta sin `@Transaction` (riesgo partial-write)

**Bug (pre-existente):** operaciones de escritura compuestas de venta corren sus inserts **sin envoltura transaccional** → si el proceso muere entre inserts, queda una venta a medias (partial-write) — money-adjacent.
- `LocalSaleDataSource.insertSaleWithImages(sale, images)` (líneas 48-53): hace `insertSale(sale)` y luego `images.forEach { insertSaleImage(it) }` — N+1 inserts sin transacción.
- `SalesLocalDataSource.saveAll(sales)` (línea 32): auditar si es un insert compuesto que también necesita atomicidad.

**Contrato / patrón correcto:** Room garantiza atomicidad con `@Transaction`. La forma idiomática: exponer un método `@Transaction` en el DAO (`LocalSaleDao` / `SaleDao` en `:core:database`) que haga los inserts compuestos, o envolver con `db.withTransaction { }` en la capa datasource. **NO alterar el schema** — `@Transaction` es anotación de método, no DDL; agregar un método `@Transaction` a un DAO NO cambia `27.json` ni el `identityHash`. Verificar que el schema export sigue byte-idéntico tras el cambio.

**Reescritura:** envolver `insertSaleWithImages` (y `SalesLocalDataSource.saveAll` si aplica) en una unidad atómica (`@Transaction` en DAO o `withTransaction`). Preservar el orden/comportamiento observable en el caso feliz.

**Char-test old→new (obligatorio):** probar atomicidad — simular fallo a mitad de la secuencia de inserts (p.ej. una imagen que viola constraint, o inyectar excepción en el segundo insert) y **assert que NADA quedó persistido** (ni la venta ni las imágenes previas) con la versión transaccional; el test OLD demuestra el partial-write. DB Room real (Robolectric). Verificar además que el caso feliz persiste todo.

**Archivos:** `data/local/datasource/sale/LocalSaleDataSource.kt`, posiblemente `SalesLocalDataSource.kt`, el/los DAO en `:core:database` (solo método `@Transaction`, schema intacto) + tests.

**Commit:** `fix(ventas): inserts de venta atómicos con @Transaction (evita partial-write)`

---

## Task 4 — [NON-MONEY / correctness] DAO null-lie + método muerto

**Bug (pre-existente, NPE risk):** dos `@Query` declaran retorno **non-null** pero Room puede devolver `null` cuando no hay fila → NPE en runtime.
- `core/database/dao/product/ProductDao.getProductById(id): ProductEntity` (línea 26) — `WHERE ARTICULO_ID = :id`, single-row, sin fila ⇒ NPE.
- `core/database/dao/productInventory/ProductInventoryDao.getProductInventoryById(id): ProductInventoryEntity` (línea 39) — idem.

**Método muerto:** `GuaranteesLocalDataSource.getImagesByGuaranteeId(guaranteeId): List<...>` (línea 68) — **0 callers** (verificado por `git grep`); además su nombre no casa con el DAO (`getImagenesByGuaranteesId`). Borrarlo.

**EXCEPCIÓN dura respetada:** este cambio toca DAOs en `:core:database` pero **solo la firma/tipo de retorno Kotlin** (`ProductEntity` → `ProductEntity?`) — NO el SQL, NO el schema, NO `27.json`. Confirmar que el schema export sigue byte-idéntico.

**Reescritura:**
1. `ProductDao.getProductById` → retorno `ProductEntity?`. Propagar a `ProductsLocalDataSource.getProductById` (línea 22-23, hoy `ProductEntity`) → nullable; ajustar sus callers.
2. `ProductInventoryDao.getProductInventoryById` → `ProductInventoryEntity?`. Propagar a `ProductsInventoryLocalDataSource.getProductInventoryById` → nullable; ajustar el caller real `features/productsInventory/viewmodels/ProductDetailsViewModel.kt:87` (`getProductInventoryById(id).toDomain()`) para manejar `null` (estado de error/no-encontrado en la UI, string usuario en español, 2-4 palabras).
3. Borrar `GuaranteesLocalDataSource.getImagesByGuaranteeId` (confirmar 0 callers antes de borrar).

**Test (obligatorio):** con DB Room real (Robolectric) o DAO fake fiel: `getProductById`/`getProductInventoryById` con id inexistente **devuelve null** (no crashea); con id existente devuelve la fila. Test de que el ViewModel maneja null sin crash (estado de error). Verificar schema byte-idéntico.

**Archivos:** `ProductDao.kt`, `ProductInventoryDao.kt` (`:core:database`), `ProductsLocalDataSource.kt`, `ProductsInventoryLocalDataSource.kt`, `ProductDetailsViewModel.kt`, `GuaranteesLocalDataSource.kt` + tests.

**Commit:** `fix(catalogo): DAOs by-id nullable (evita NPE) y borrar método muerto de garantías`

---

## Conformance checklist (cierre del plan)
- [ ] Task 1: exception-swallow eliminado en getProductsForSale/getCombosForSale; guard downstream en LocalSaleSyncHandler + PendingLocalSalesWorker (no sube venta con renglones vacíos por error); contrato del backend verificado y documentado; char-test old→new + casos borde; callers de lectura no crashean.
- [ ] Task 2: `PaymentsLocalDataSource.saveAll` usa `deleteUploaded()`; char-test prueba que los pending sobreviven; DB real.
- [ ] Task 3: inserts de venta atómicos (`@Transaction`/`withTransaction`); char-test prueba no-partial-write; schema byte-idéntico.
- [ ] Task 4: DAOs by-id nullable + callers ajustados + ViewModel maneja null; método muerto borrado; schema byte-idéntico.
- [ ] Gate verde por tarea (ktlint + unit + detekt [+ :core:database si aplica]); commits conventional español sin atribución; sin push; sin `--no-verify`.
- [ ] Cada bug documentado como fix consciente de bug pre-existente (no cambio accidental).
