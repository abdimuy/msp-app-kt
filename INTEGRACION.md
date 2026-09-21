# Integración de `feat/pagos-y-visitas` + `main` + `feat/editar-venta-local`

Rama `feat/integracion`, worktree `/Volumes/M2-1TB/Developer/.wt-integracion`.
Base: `feat/pagos-y-visitas` en `646ad773` (120 commits: pagos, visitas,
detalle de cliente y de venta, bitácora, rediseño de la fila de contactos).

Fecha: 2026-09-21. Sin push. `main` sin tocar.

## Qué se fusionó, y en qué orden

| # | Commit | Qué entró |
|---|--------|-----------|
| 1 | `73351b4e` | `merge(main)`: el commit `c38c7e36`, arreglo del sync de cobranza (el saldo volvía al del servidor y borraba el pago recién cobrado). **Sin conflictos.** |
| 2 | `9883bdd6` | `merge(ventas)`: los 14 commits de `feat/editar-venta-local`. **Nueve archivos en conflicto.** |
| 3 | `<siguiente>` | `docs`: corrige tres KDocs que la fusión dejó diciendo algo falso + este informe. |

El commit `c38c7e36` también estaba en `feat/editar-venta-local`, así que la
segunda fusión no lo volvió a traer.

## La cadena de migraciones resultante

```
v29  ──MIGRATION_29_30──▶  v30  ──MIGRATION_30_31──▶  v31
      (pagos y visitas)          (corrección de venta)
```

- **`MIGRATION_29_30` (sin cambios)** — la de `feat/pagos-y-visitas`: cinco
  columnas nullable sobre `Visit` (promesa y cita) y cinco tablas nuevas
  (`visita_imagenes`, `pago_imagenes`, `visita_recomendaciones`,
  `cliente_ficha`, `cliente_ficha_senales`). `30.json` es **byte por byte** el
  de esa rama (verificado con `diff` contra `feat/pagos-y-visitas`).
- **`MIGRATION_30_31` (renumerada)** — la que `feat/editar-venta-local` había
  escrito como 29→30. Se movió a
  `core/database/src/main/kotlin/.../migrations/Migration30to31.kt`. **El SQL
  no cambió**: las mismas seis `ALTER TABLE local_sale ADD COLUMN`
  (`CLAIM_ID`, `CLAIM_KIND`, `CLAIMED_AT` nullable; `REVISION` y
  `CORRECCION_NO_ENVIADA` `NOT NULL DEFAULT 0`; `REVISION_POSTEADA` nullable
  **sin** default). Los `@ColumnInfo(defaultValue = …)` de `LocalSaleEntity`
  quedaron como estaban.
- **`AppDatabase`**: `version = 31`, `addMigrations(… , MIGRATION_29_30,
  MIGRATION_30_31)`.
- **`31.json`**: generado por Room (`:core:database:kspDebugKotlin`), no
  escrito a mano. Verificado: `version 31`, las seis columnas con el tipo,
  nulabilidad y default correctos, y las cinco tablas del primer tramo.

### Ninguna de las dos v30 existe en la flota

Es el hecho que hace legítima la renumeración: ningún teléfono tiene una v30,
así que **nadie queda a medias**. El único salto que la actualización de hoy va
a recorrer es **v29 → v31**, y por eso se probó justamente ése.

### Pruebas de la cadena

| Prueba | Qué clava |
|--------|-----------|
| `Migration29a31Test` (**nueva**, 3 casos) | Una base v29 con una venta local + producto + combo + foto + una visita migra hasta v31 por las **dos** migraciones: todo sobrevive campo por campo, existen las columnas de **ambos** tramos, y el salto funciona también por el camino REAL de producción (`AppDatabase.buildDatabase`). |
| `Migration30to31Test` (renombrada de `Migration29to30Test` de la otra rama, 5 casos) | El tramo 30→31 contra `30.json` → `31.json`: el candado nace libre, `REVISION` es `NOT NULL DEFAULT 0`, `REVISION_POSTEADA` nace sin ancla y sin default (también en instalación nueva), la venta y sus hijos sobreviven, y la migración **está registrada** en `buildDatabase`. |
| `Migration29to30Test` (la de pagos y visitas, intacta, 11 casos) | Sin cambios. |
| `SchemaIntegrityTest` | Apunta a la versión nueva: `LATEST_SCHEMA_VERSION = 31`. |
| `AppDatabaseTest` | `version 31`; conserva los dos casos de la otra rama que miran el `PRAGMA` real de una instalación **nueva** (`REVISION` y `CORRECCION_NO_ENVIADA` con `DEFAULT 0`). |

**Control positivo, medido, no razonado.** Se borró `MIGRATION_30_31` de
`addMigrations` y se corrió `Migration29a31Test`: rojo en
`abrir por el camino de produccion lleva una base v29 hasta v31`
(`IllegalStateException`) — que es exactamente la falla que vería un teléfono
con ventas pendientes adentro. Restaurado y verde otra vez, con el árbol
limpio (`git status` vacío).

## Los nueve conflictos, uno por uno

### 1. `settings.gradle.kts`

Las dos ramas agregaban módulos al final de la lista. **Se unieron las tres**,
en orden alfabético: `:feature:pagos`, `:feature:ventaCorreccion`,
`:feature:visitas`.

### 2. `app/build.gradle.kts`

Mismo caso: las tres `implementation(project(...))` con su comentario, ninguna
perdida.

### 3. `build.gradle.kts` (raíz) — el conflicto grande

Dos choques.

**(a) `legacyDateApiContentAllowlist`.** La rama de pagos agregó la entrada de
`AppClock.kt`; la de corrección **borró** la de `EditLocalSaleViewModel.kt`
porque su Task 5 borró ese archivo. Resolución: se queda la de `AppClock.kt` y
se va la de `EditLocalSaleViewModel.kt` — verificado que el archivo no existe
en el árbol fusionado.

**(b) `prePushCheck`.** Aquí las dos ramas hicieron cosas incompatibles de
forma:

- `feat/editar-venta-local` **agregó seis entradas a la lista escrita a mano**
  del `dependsOn` (`checkNoLegacySaleEdit` + las cinco tareas de
  `:feature:ventaCorreccion`).
- `feat/pagos-y-visitas` **borró la lista entera** y la reemplazó por un
  descubrimiento desde el modelo de objetos de Gradle (Arreglo B): los módulos
  salen de `subprojects` filtrados por tener `build.gradle.kts`, las tareas por
  familias, y las compuertas propias por **tipo**
  (`buildlogic.CompuertaDelRepo`), con una base congelada de 68 entradas como
  red de no-regresión.

Se tomó el descubrimiento. Consecuencias, verificadas en la corrida real:

- Las cinco tareas de `:feature:ventaCorreccion` entran **solas**, por tener
  script de build. No hacía falta escribirlas.
- **`checkNoLegacySaleEdit` NO entraba**, y ese sí era un hueco real: venía
  registrada como `tasks.register("checkNoLegacySaleEdit")`, o sea un
  `DefaultTask` pelado sin la marca de tipo. El descubrimiento no la habría
  corrido **y encima `verificarMarcaDeCompuertas` habría hecho fallar el gate**
  por verla en el grupo `verification` sin marca. Se cambió a
  `tasks.register<CompuertaDelRepoTask>("checkNoLegacySaleEdit")` (el tipo ya
  fija `group = "verification"`, así que la línea del grupo se fue).
  Confirmado en el log del gate: aparece en la raíz y entre las 27 tareas
  ganadas.

La base congelada quedó **intacta** (68/68 cubiertas contra el grafo real, 95
dependencias). No se agregó nada a ella: el contrato es "lo descubierto debe
CONTENER la base", y ganar tareas es el objetivo.

### 4. `WorkManagerUtils.kt`

- De `feat/editar-venta-local`: `localSaleUniqueWorkName(localSaleId)`, la
  **fuente única** del nombre del trabajo (antes el literal estaba duplicado en
  `WorkManagerReencolarSubidaAdapter`). **Conservada**, y sigue siendo el único
  sitio donde se construye ese nombre.
- De `feat/pagos-y-visitas`: el borrado del parámetro `replace` de las cinco
  funciones de trabajo pendiente y de `enqueueCobranzaReconcileNowWorker`, más
  los dos encoladores nuevos de reconciliación de visitas. **Conservado.**

Se buscó el otro encargo —aplicar `localSaleUniqueWorkName` a los usos que
trajera la otra rama— y **no hay ninguno**: fuera de su definición, el literal
`sync_pending_local_sale_` sólo aparece en dos pruebas, que lo escriben a
propósito para que el nombre no se verifique contra sí mismo.

### 5–9. Los cinco de `:core:database`

`30.json`, `Migration29to30.kt`, `Migration29to30Test.kt` → se quedó la versión
de `feat/pagos-y-visitas`, tal cual. `AppDatabaseTest.kt` y
`SchemaIntegrityTest.kt` → se tomó la de la rama de corrección (la que trae los
dos casos del `PRAGMA`) y se subió a v31. Detalle en la tabla de la sección de
migraciones.

## La decisión de comportamiento que hubo que tomar

**Es la única, y está en el camino del dinero, así que va con su razón entera.**

`WorkManagerReencolarSubidaAdapter.reencolar` llamaba a
`enqueuePendingLocalSalesWorker(context, saleId, userEmail, replace = true)`.
La rama de pagos y visitas **borró ese parámetro** y clavó
`ExistingWorkPolicy.KEEP` dentro de las cinco funciones del camino del dinero.
O sea: el código no compilaba de las dos maneras — había que elegir.

**Se eligió `KEEP`.** No es un empate resuelto a moneda; las dos ramas ya
tenían escrita la razón, cada una por su lado:

1. **El contrato del propio puerto lo permite.** `ReencolarSubidaPort` dice, en
   su KDoc y en el de `GuardarCorreccion`, que reencolar es *"SIEMPRE una
   optimización, NUNCA un requisito de correctitud — la fila manda"*: si la
   llamada falla o lanza, la corrección ya está commiteada en Room y el barrido
   (`getUploadableSales`) la recoge en la siguiente apertura de sesión. La
   llamada está envuelta en `runCatching` justamente por eso.
2. **En el camino real las dos políticas hacen lo mismo.** Al RECLAMAR ya se
   llamó a `cancelarTrabajoEncolado`, así que el trabajo previo está en estado
   terminal (`CANCELLED`) y `KEEP` encola igual que `REPLACE`. La única
   diferencia aparece si hay un trabajo VIVO bajo el mismo nombre — y ése sube
   la fila **ya corregida**, porque `PendingLocalSalesWorker` lee la venta de
   Room al ejecutarse, no al encolarse.
3. **La alternativa estaba cerrada por una compuerta.** `WorkEnqueuePolicyGuardTest`
   (de la rama de pagos) afirma `KEEP` por comportamiento, barre todos los
   módulos buscando constantes de política prohibidas, y además barre los call
   sites buscando un booleano `… = true` que pida otra. Reintroducir `replace`
   habría requerido allowlistear la línea y revertir un arreglo deliberado.

Lo que se escribió, para que nadie lo redescubra: un KDoc en `reencolar`
explicando el cambio y por qué no toca la correctitud, y la corrección del KDoc
de `ReencolarSubidaPort`, que decía *"reencola la subida con reemplazo"* —
ahora ya no es verdad.

## Cosas que hubo que decidir y no estaban dichas arriba

1. **`checkNoLegacySaleEdit` pasó a `CompuertaDelRepoTask`.** Sin esto el gate
   fallaba (por `verificarMarcaDeCompuertas`) y, si no fallara, la compuerta se
   habría quedado **fuera** del pre-push en silencio. Ver §3(b).
2. **Dos fakes de prueba no compilaban.** `CorreccionCarreraTest.EncoladorGrabador`
   y `CorreccionMuerteEntreGuardarYReencolarTest.RecordingEnqueuer` implementan
   `LocalSalesWorkEnqueuer`, cuya firma perdió el `replace: Boolean`. Se les
   quitó el parámetro; ninguna de las dos lo usaba para nada.
3. **Tres KDocs quedaron afirmando algo falso.** `PendingWorkSyncFactory`,
   `LocalSaleDataSource.getUploadableSales` y `LocalSaleDao.getUploadableSales`
   justificaban su existencia diciendo *"el barrido reencola con `replace = true`
   en cada apertura de sesión"*. Tras la fusión eso ya no ocurre. **La decisión
   sigue siendo correcta por otra razón** —encolar una venta reclamada sólo
   sirve para chocar contra el fence del candado, quemar un reintento y pelearse
   con el subidor—, así que se corrigió la razón en vez de borrar el código.
   Este repo es explícito en que una razón falsa que sostiene una decisión
   correcta es peor que ninguna.
4. **Una prueba nueva, `Migration29a31Test`.** No estaba en ninguna de las dos
   ramas porque ninguna podía escribirla: el salto de dos tramos nace de la
   renumeración. Va con control positivo medido.
5. **`local.properties`.** El worktree nuevo no lo traía (está en `.gitignore`)
   y sin `MAPS_API_KEY` el build ni configura. Se copió el del repo principal.
   **No se commiteó** — sigue ignorado.

## El resultado real de la compuerta

```
$ export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
$ ./gradlew prePushCheck --rerun-tasks

prePushCheck: base congelada 68/68 cubierta contra el grafo real (95 dependencias),
              27 tareas ganadas: [:checkNoLegacySaleEdit, …, :feature:ventaCorreccion:detekt,
              :feature:ventaCorreccion:koverVerify, :feature:ventaCorreccion:koverVerifyDebug,
              :feature:ventaCorreccion:ktlintCheck, :feature:ventaCorreccion:testDebugUnitTest,
              :feature:ventaCorreccion:verifyRoborazziDebug, …, :verificarMarcaDeCompuertas]
prePushCheck: 18 módulos + la raíz, 94 tareas descubiertas (+ build-logic:ktlintCheck)

BUILD SUCCESSFUL in 4m 57s
1130 actionable tasks: 1130 executed
```

**Verde a la primera, sin `clean`, sin reintentos, y sin tropezar con los
intermitentes catalogados** (`CitiesViewModelTest`,
`AdjustedPaymentPercentageReactiveTest`).

Corrieron las compuertas de **las dos** ramas. Verificado tarea por tarea en el
log y en los XML de resultados:

| Compuerta | Estado |
|---|---|
| `:feature:pagos:verifyRoborazziDebug` | ✅ |
| `:feature:visitas:verifyRoborazziDebug` | ✅ |
| `:feature:ventaCorreccion:verifyRoborazziDebug` | ✅ |
| `:checkNoLegacySaleEdit` | ✅ (y descubierta, no escrita a mano) |
| `:checkNoLegacyDateApi` | ✅ |
| `:verificarMarcaDeCompuertas` | ✅ |
| `:app:testDevlocalDebugUnitTest` (carreras) | ✅ |
| `:app:assembleDevlocalDebug` | ✅ |
| `:core:database:koverVerifyMigrations` (100%) | ✅ |

Clases de prueba que importaban, contadas una por una (0 fallos, 0 errores):

```
Migration29a31Test                        3 pruebas
Migration30to31Test                       5
Migration29to30Test                      11
SchemaIntegrityTest                       1
AppDatabaseTest                           3
CorreccionCarreraTest                    14
CorreccionDivergenciaTest                 8
CorreccionIdempotenciaTest                2
CorreccionSubeCorregidaTest               2
CorreccionMuerteEntreGuardarYReencolarTest 1
WorkEnqueuePolicyGuardTest                3
WorkManagerReencolarSubidaAdapterTest     1
```

## El APK

```
/Volumes/M2-1TB/Developer/.wt-integracion/app/build/outputs/apk/devlocal/debug/app-devlocal-debug.apk
```

29.6 MB, generado por `./gradlew :app:assembleDevlocalDebug` (y otra vez dentro
del gate). Sabor **`devlocal`**, que es el que corresponde: `devserver` está
retirado y su túnel es hoy el de producción — un APK apuntando ahí escribiría
pagos en la base real. Para instalarlo en un teléfono físico hace falta
`LOCAL_API_HOST` en `local.properties` + `adb reverse tcp:3001 tcp:3001`.

## Lo que NO se hizo

- **No se hizo push**, ni a `feat/integracion` ni a nada.
- **No se tocó `main`** ni ningún otro worktree `.wt-*`.
- **No se corrieron los `androidTest`**: ninguna compuerta de este repo los
  compila ni los ejecuta (`build.gradle.kts` excluye las tareas `connected*`).
  Es el estado normal del repo, no algo que esta integración cambie.
