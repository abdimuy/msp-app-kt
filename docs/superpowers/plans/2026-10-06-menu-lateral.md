# Menú lateral nuevo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar el cajón legado `DrawerContainer` por el menú lateral de la opción A (cabecera con identidad y estado de envío, secciones plegables recordadas con contadores reales, buscador con ≥10 opciones, pie con Configuración y versión) en las 20 llamadas que hoy lo usan, y retirar `DrawerContainer`.

**Architecture:** Módulo nuevo `:core:navegacion` con `domain/` puro (modelo + puertos), `application/` (reglas puras, textos, estado combinado), `ui/` (hoja `MenuLateral`, contenedor `MenuLateralContenedor` sobre `ModalNavigationDrawer` de Material 3 1.3.0, `MenuLateralViewModel`). Los adaptadores que leen Room, DataStore, `ReunirCartera` y `BuildConfig` viven en `:app` (principio 17), igual que el pegamento `MenuDeLaApp`, que tiene la MISMA forma que `DrawerContainer(navController) { abrir -> … }` para que la adopción sea un cambio mecánico pantalla por pantalla.

**Tech Stack:** Kotlin 2.0.21, Compose BOM 2024.09.00 (material3 1.3.0, foundation 1.7.8), Hilt 2.52 (`msp.hilt`), DataStore Preferences 1.1.7, Room 2.6.1, íconos `com.composables:icons-lucide:1.0.0`, pruebas JUnit4 + Robolectric 4.14.1 + Turbine + Roborazzi 1.26.0 (fakes, sin MockK).

**Spec:** docs/superpowers/specs/2026-10-05-menu-lateral-design.md

## Global Constraints

- Altura mínima de TODO ítem tocable del menú: `MspTheme.spacing.touchTarget` (56.dp). Los 48 dp del mock NO se usan (spec §2.1.6). La compuerta del repo (`CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`) cobra ≥ 50 dp sobre todo control; si una prueba de altura sale roja, sube la implementación, nunca el número.
- Texto de UI en español, mayúscula inicial (se mide la primera LETRA), sin punto final, 2–4 palabras. Nunca "ciclo" (se dice "semana"). Las versalitas sólo por el sumidero `.uppercase(BUSINESS_LOCALE)`.
- Colores sólo de `MspTheme.colors`: opción activa fondo `brandTint` + texto `brand`; punto "Todo enviado" `statusPaid`; punto "esperando señal" `statusPartial`. Tipografía sólo `MspTheme.type.*`, espacios sólo `MspTheme.spacing.*`, formas `MspTheme.shapes.*`. Cero tokens nuevos.
- El punto de color nunca es el único portador de significado: siempre va con su texto.
- Movimiento reducido: toda animación nueva consulta `rememberMspReducedMotion()`; con él, el menú abre y cierra con `snapTo` (sin animación).
- Contadores: ningún número que no salga de una fuente de la spec §4. Traspasos y Reporte van SIN contador en este plan. Un contador en 0 no se pinta.
- Contrato hexagonal: `domain/` sin `android.*` ni Compose; adaptador que necesita algo de `:app` vive en `:app`; `ui/` nunca importa adaptadores.
- Pruebas: fakes-only, sin MockK/Mockito; `StandardTestDispatcher` + `advanceUntilIdle()`, nunca `UnconfinedTestDispatcher` en pruebas nuevas; nombres mexicanos realistas en datos de prueba.
- Errores en `:core:*`: ningún `Throwable` capturado se traga; cada `catch` emite `Telemetry.error(code, message, props)` con `code` constante; sin PII (nunca nombres, montos ni `e.message`; sólo `falla::class.java.simpleName`).
- Room: schema v27/v30 inmutable. Sólo se agregan `@Query` a DAOs existentes; ninguna entidad cambia.
- Gradle: SIEMPRE `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` antes del comando y SIEMPRE `./gradlew --no-parallel --no-daemon …`. Nunca dos Gradle a la vez en este checkout.
- Si Gradle sale con `Wrong plugin option format: null` o falla una tarea `ksp…Kotlin`, reintentar el MISMO comando hasta 3 veces antes de tratarlo como defecto.
- Las pruebas se cuentan en los XML, no en la consola: `grep -ho 'tests="[0-9]*"' <mod>/build/test-results/<tarea>/TEST-*.xml`.
- Antes de `verifyRoborazziDebug`: `rm -rf <mod>/build/outputs/roborazzi`. Los goldens se graban con `:<mod>:recordRoborazziDebug` y se MIRAN como imagen (claro y oscuro, 1.0/1.5/2.0). Prohibido subir `RoborazziConfig.CHANGE_THRESHOLD`. Ningún golden ajeno (`:core:designsystem`, `:feature:collectionReport`, `:feature:pagos`, `:feature:configuracion`) se mueve.
- Antes de `prePushCheck`: `rm -rf app/build`, y correrlo con `--rerun-tasks` al menos una vez (el árbol caliente sale verde sin probar nada).
- Commits: conventional commits, asunto en español, SIN atribución de Claude ni `Co-Authored-By`. Nunca `--no-verify`. El pre-commit corre ktlint + `:build-logic` + `testDevlocalDebugUnitTest` (varios minutos): se espera a que termine.
- Nada de publicar, subir APK ni `git push`.
- Cada cambio de comportamiento lleva una prueba que se vio ROJA antes de la implementación (paso "correr y ver que falla") y que se pone roja al revertir.

## Review Focus

1. **Atrás con el menú abierto.** El botón o gesto atrás del sistema con el menú abierto debe cerrar el menú y dejar al cobrador en la misma pantalla; en Inicio, nunca sacarlo de la app. Hoy `DrawerContainer` no maneja atrás. Prueba: Task 4, `atras con el menu abierto lo cierra y no deja la pantalla`.
2. **Tocar la opción de la pantalla donde ya estás, o tocar dos veces rápido.** Debe sólo cerrar el menú; nunca apilar otra copia ni recargar la pantalla (hoy "Inicio" recrea Home con `popUpTo(home) { inclusive = true }`). Prueba: Task 10, `elegir la opcion de la pantalla actual no navega` y `dos toques rapidos dejan una sola copia`.
3. **Reabrir el menú después de buscar o de plegar la sección activa.** Al reabrir: buscador vacío; la sección de la pantalla actual vuelve a abrirse sola aunque el usuario la haya plegado (forzada por ruta) y esa apertura forzada NUNCA se escribe en la preferencia. Prueba: Task 4, `cerrar el menu limpia la busqueda y las secciones tocadas`; Task 3, `la seccion activa se abre aunque este plegada y no escribe la preferencia`.
4. **Sesión ausente o a medias** (sin `LocalAuthViewModel` en las redes de prueba, `userData` en `Loading`/`Error`, `MODULOS` vacío, `NOMBRE` vacío). El menú pinta avatar "?", sin nombre, sin secciones y con el pie (Configuración + versión); nunca truena. Prueba: Task 10, `sin usuario el menu queda con solo el pie`; Task 1, `iniciales de un nombre vacio es signo de interrogacion`.
5. **Marca de tiempo del sync rara** (`LAST_SYNCED_AT` ilegible, en el futuro por reloj adelantado, falta uno de los dos recursos, o el último intento falló: `recordError` TAMBIÉN escribe `LAST_SYNCED_AT`). Nunca "hace -3 min" ni "hace N min" después de un error: se muestra "Todo enviado" sin tiempo. Prueba: Task 7, los cinco casos de `minutosDesdeElUltimoContacto`.

---

## Decisiones de este plan frente a la spec (con su evidencia)

La spec marca sus firmas como "tentativas" (§3.2–§3.4). Donde este plan se aparta, aquí está el porqué y el nivel de evidencia.

| # | Spec dice | Este plan hace | Evidencia |
|---|---|---|---|
| D1 | `ModulosDelUsuarioPort` envuelve `AuthViewModel.userData` | No hay puerto: `MenuDeLaApp` lee `LocalAuthViewModel` (igual que `DrawerContainer.kt:62-71`) y pasa `MODULOS` al catálogo | LEÍDO `AuthViewModel.kt:38` es `AndroidViewModel` creado con `viewModel()` en `AppNavigation.kt:251`, no está en el grafo de Hilt; un puerto Hilt obligaría a un segundo listener de Firestore (lecturas dobles y dos verdades). Ruling BF: un puerto sin implementación Hilt es defecto |
| D2 | `ContadoresDelMenuPort.contadores(): Flow` | `suspend fun contadores(ids: Set<String>)` y se refresca al abrir el menú, sólo para las opciones visibles | LEÍDO `ReunirCartera.kt:60-73`: el conteo "por volver" exige tres lecturas completas de Room; correrlo en cada recomposición de cada pantalla sería caro. Al abrir es lo que el cobrador ve |
| D3 | `SeccionesPlegadasPort` extiende `SettingsRepository` | DataStore propio `menu_lateral` en `:app` (adaptador + calificador) | LEÍDO `SettingsRepository.kt:7` documenta que persiste "las 3 preferencias que la pantalla de Configuración expone"; precedente exacto `DataStoreOrdenGuardadoAdapter` + `PagosPortsModule.provideOrdenListaClientesDataStore` |
| D4 | `onBuscar: ((String) -> Unit)?` | La consulta vive en `AperturaDelMenu` (estado de la apertura) y el umbral lo decide `ReglasDelMenu.muestraBuscador` | Para que cerrar el menú limpie la búsqueda (Review Focus 3) el estado tiene que ser del contenedor |
| D5 | `ElementoDelMenu` con `icono: ImageVector`, `onClick`, `resaltarContador` | `icono: IconoDelMenu` (enum), sin `onClick` (el menú emite `onElegir(elemento)`), sin `resaltarContador` | Principio 15: `domain/` sin Compose. Ninguna fuente de §4 decide "resaltar": YAGNI |
| D6 | `EstadoDeEnvio(estado, pendientes, ultimoEnvioHaceMinutos)` | `EstadoDeEnvio(pagosPendientes, ventasPendientes, ultimoEnvioHaceMinutos)` con `estado` derivado | La spec suma pagos y ventas pero el texto decidido dice "N pagos esperando señal": con las dos cuentas separadas el texto no miente (ver `TextoDelEnvio`) |
| D7 | `LAST_SYNCED_AT` = "última página exitosa" | Se ignora el tiempo si algún recurso tiene `LAST_ERROR` | LEÍDO `CobranzaSyncManager.kt:286-295` + `CobranzaSyncStateDao.recordError`: en error también se escribe `LAST_SYNCED_AT`. La afirmación de la spec §4 está REFUTADA por lectura |
| D8 | Ventas pendientes = `ENVIADO = 0` | `ENVIADO = 0 AND (LAST_UPLOAD_PERMANENT IS NULL OR LAST_UPLOAD_PERMANENT = 0)` | LEÍDO `LocalSaleDao.kt:255-258`: con fallo permanente "el servidor ya resguardó el intento" (`LaRevisaLaOficina`); contarla como "esperando señal" sería falso para siempre |
| D9 | Detalle de venta legado, descripción, tickets legados y detalle de producto tienen "flecha atrás" | Conservan su hamburguesa y sólo cambian de cajón (Task 15). El deslizar desde la barra va a las pantallas que SÍ tienen flecha: detalle de cliente y detalle de venta de `:feature:pagos` (Task 16) | LEÍDO `SaleDetailsScreen.kt:182-193`, `SaleDescriptionScreen.kt:88-90`, `PaymentTicketScreen.kt:328-333`, `VisitTicketScreen.kt:187-192`, `ProductDetailsScreen.kt:229-234`: las cinco pintan `Icons.Default.Menu`; `Marco.kt:232-248` (`BarraDeDetalle`) es la flecha real. Los tickets de `:feature` están estacionados sin entrada (`DestinosDeCobranzaGraph.kt:280-286, 322-329`) |
| D10 | Atrás predictivo "sin precedente, verificar" | Se usa `ModalDrawerSheet(drawerState, …)` desde la Task 4; la Task 17 enciende `enableOnBackInvokedCallback` y el resorte | MEDIDO 2026-10-06 (dev, `javap -public` sobre `material3-android-1.3.0` del caché de Gradle): existen `ModalDrawerSheet-Snr_uVM(DrawerState, …)`, `DrawerState.snapTo` y `DrawerState.animateTo(DrawerValue, AnimationSpec)` (este último `@Deprecated`). Control positivo: el mismo `javap` sí lista `open`/`close`. LEÍDO `AndroidManifest.xml:28-39` sin `enableOnBackInvokedCallback`; `grep "override fun onBackPressed\|KEYCODE_BACK\|onKeyDown" app/src/main` sin resultados (sin control positivo: INFERIDO que no hay overrides) |
| D11 | Cabecera "nombre, rol y ruta" | `rol = null` (no se pinta); `ruta = "Zona N"` desde `ZONA_CLIENTE_ID` | LEÍDO `User.kt:7-23`: no hay campo de rol ni nombre de ruta. No se inventa |
| D12 | Pie "Configuración y versión" | Además conserva `UpdateBanner(compact = true)` como hoy | LEÍDO `DrawerContainer.kt:338`: quitarlo apagaría el aviso de actualización sin que nadie lo pidiera. El botón de tema del encabezado viejo (`DrawerContainer.kt:101-126`) NO pasa: Configuración ya cambia el tema |
| D13 | Contadores pendientes en "fase 4" | Ventas "N sin subir" SÍ entra (sale de la misma consulta que la cabecera necesita); Reporte NO | La consulta de conteo de ventas se crea en la Task 6 de todos modos; Reporte necesitaría una llamada nueva a `ReportAggregator` |

## Mapa de archivos

**Crear — `:core:navegacion`** (`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/`)

| Archivo | Responsabilidad |
|---|---|
| `core/navegacion/build.gradle.kts` | Módulo nuevo con `msp.android.library/compose/hilt/test/kover/detekt`, ktlint y roborazzi |
| `domain/model/ModeloDelMenu.kt` | `IconoDelMenu`, `ElementoDelMenu`, `SeccionDelMenu`, `EstadoDeEnvio`, `CabeceraDelMenu`, `ActivoEnElMenu` |
| `domain/port/PuertosDelMenu.kt` | `ContadoresDelMenuPort`, `EstadoDeEnvioPort`, `SeccionesPlegadasPort` |
| `application/ReglasDelMenu.kt` | Umbral del buscador, filtro sin acentos, qué sección va abierta, iniciales |
| `application/TextoDelEnvio.kt` | "Todo enviado · hace 3 min" / "2 pagos esperando señal" |
| `application/EstadoDelMenu.kt` | Estado combinado que consume la UI |
| `application/NavegacionTelemetria.kt` | Códigos de telemetría del módulo |
| `ui/MenuLateralViewModel.kt` | Combina puertos, refresca contadores al abrir, persiste plegado |
| `ui/AperturaDelMenu.kt` | Estado de UNA apertura: consulta y secciones tocadas |
| `ui/MenuLateral.kt` | La hoja pura (tags públicos, ancho) |
| `ui/MenuLateralContenedor.kt` | `ModalNavigationDrawer` + atrás predictivo + reinicio al cerrar + movimiento reducido + modo de gesto |
| `ui/ZonaQueAbreElMenu.kt` | Deslizar a la derecha desde la franja superior (Task 16) |
| `ui/components/IconosDelMenu.kt` | `IconoDelMenu` → vector Lucide |
| `ui/components/EncabezadoDelMenu.kt` | Avatar, nombre, zona y tarjeta de envío |
| `ui/components/SeccionPlegable.kt` | Encabezado plegable de sección + sus filas |
| `ui/components/FilaDelMenu.kt` | Fila de 56 dp con ícono, texto, contador y resaltado |
| `ui/components/BuscadorDelMenu.kt` | Campo "Buscar en el menú" |
| `ui/components/PieDelMenu.kt` | Aviso opcional + Configuración + versión |

Pruebas en `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/`: `MenuFixtures.kt`, `fake/FakesDelMenu.kt`, `application/ReglasDelMenuTest.kt`, `application/TextoDelEnvioTest.kt`, `ui/MenuLateralViewModelTest.kt`, `ui/MenuLateralTest.kt`, `ui/MenuLateralContenedorTest.kt`, `ui/ZonaQueAbreElMenuTest.kt`, `screenshot/MenuLateralScreenshotTest.kt`, `screenshot/MenuLateralMatrixScreenshotTest.kt`; goldens en `core/navegacion/src/test/screenshots/`.

**Modificar — otros módulos**

| Archivo | Cambio |
|---|---|
| `settings.gradle.kts` | `include(":core:navegacion")` |
| `core/database/.../dao/payment/PaymentDao.kt` | `observarPendientesDeSubir(): Flow<Int>` |
| `core/database/.../dao/localsale/LocalSaleDao.kt` | `observarSinSubir(): Flow<Int>` |
| `core/database/.../dao/cobranzasync/CobranzaSyncStateDao.kt` | `observarRecursos(recursos): Flow<List<CobranzaSyncStateEntity>>` |
| `feature/pagos/.../ui/CarteraEnPantalla.kt` | `clientesPorVolver(clientes, hoy): Int` (el MISMO número que el chip "Volver") |
| `feature/pagos/.../ui/PagosRutas.kt` | `envoltura` en `destinoDeDetalleCliente` y `destinosDePagos` (Task 16) |
| `feature/configuracion/.../ui/ConfiguracionScreen.kt` + `ui/components/SesionSection.kt` (nuevo) | "Cerrar sesión" con confirmación |
| `app/build.gradle.kts` | `implementation(project(":core:navegacion"))` |
| `app/src/main/AndroidManifest.xml` | `android:enableOnBackInvokedCallback="true"` (Task 17) |
| `app/.../navigation/AppNavigation.kt` | Reporte adopta el menú; Configuración recibe `onCerrarSesion` |
| `app/.../navigation/DestinosDeCobranzaGraph.kt` | `envoltura` de la lista → `MenuDeLaApp`; detalles con gesto desde la barra |
| 18 pantallas legadas (Tasks 11–15) | `DrawerContainer(` → `MenuDeLaApp(` |
| `app/.../components/DrawerContainer.kt` | Se BORRA (Task 18) |

**Crear — `:app`**

| Archivo | Responsabilidad |
|---|---|
| `app/src/main/java/com/example/msp_app/data/menu/EstadoDeEnvioAdapter.kt` | `EstadoDeEnvioPort` sobre Room + reloj + pulso de 1 min |
| `app/src/main/java/com/example/msp_app/data/menu/ContadoresDelMenuAdapter.kt` | `ContadoresDelMenuPort`: clientes por volver y ventas sin subir |
| `app/src/main/java/com/example/msp_app/data/menu/DataStoreSeccionesPlegadasAdapter.kt` | `SeccionesPlegadasPort` sobre DataStore |
| `app/src/main/java/com/example/msp_app/di/MenuLateralModule.kt` | Bindings Hilt de los tres puertos + DataStore calificado |
| `app/src/main/java/com/example/msp_app/navigation/menu/IdsDelMenu.kt` | Ids estables de las opciones |
| `app/src/main/java/com/example/msp_app/navigation/menu/CatalogoDelMenu.kt` | Opciones por módulo, ruta → activo, cabecera desde `User` |
| `app/src/main/java/com/example/msp_app/navigation/menu/NavegacionDelMenu.kt` | Navegar desde el menú sin duplicar pantallas |
| `app/src/main/java/com/example/msp_app/navigation/menu/MenuDeLaApp.kt` | Pegamento: reemplazo directo de `DrawerContainer` |

Pruebas en `app/src/test/java/com/example/msp_app/`: `data/menu/EstadoDeEnvioAdapterTest.kt`, `data/menu/ContadoresDelMenuAdapterTest.kt`, `data/menu/DataStoreSeccionesPlegadasAdapterTest.kt`, `navigation/menu/CatalogoDelMenuTest.kt`, `navigation/menu/NavegacionDelMenuTest.kt`, `navigation/AdopcionDelMenuLateralTest.kt`, `navigation/LaHamburguesaDeClientesAbreElMenuLateralTest.kt` (renombrado), `navigation/ElMenuSeAbreDesdeLaBarraTest.kt`, `navigation/ElAtrasPredictivoEstaEncendidoTest.kt`, `navigation/RetiroDelCajonViejoTest.kt`; se modifica `navigation/CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest.kt`.

**Por qué este orden de tareas.** 1–5 construyen y prueban el menú sin datos reales (fakes). 6–8 dan las fuentes reales y su cableado Hilt; 9 mueve "Cerrar sesión" ANTES de que cualquier pantalla pierda el ítem viejo; 10 arma el pegamento; 11–15 adoptan en el orden de la spec §3.7 (nivel superior primero) y cada una deja la app compilando con una prueba de adopción que crece; 16 es la decisión §2.2; 17 el atrás predictivo del sistema y el resorte; 18 retira `DrawerContainer` cuando el escáner confirma cero usos.

---

### Task 1: Módulo `:core:navegacion` con modelo y reglas puras

**Files:**
- Create: `core/navegacion/build.gradle.kts`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/domain/model/ModeloDelMenu.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/application/ReglasDelMenu.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/application/TextoDelEnvio.kt`
- Modify: `settings.gradle.kts` (después de `include(":core:geo")`)
- Test: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/application/ReglasDelMenuTest.kt`
- Test: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/application/TextoDelEnvioTest.kt`
- Test: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/MenuFixtures.kt`

**Interfaces:**
- Consumes: `BUSINESS_LOCALE` (`com.example.msp_app.core.common.time.BUSINESS_LOCALE`).
- Produces:
  - `enum class IconoDelMenu { INICIO, CLIENTES, REPORTE, MAPA, VENTAS, CAMIONETA, CATALOGO, TRASPASOS, ASIGNACION, GARANTIAS, CONFIGURACION }`
  - `data class ElementoDelMenu(val id: String, val etiqueta: String, val icono: IconoDelMenu, val contador: String? = null)`
  - `data class SeccionDelMenu(val id: String, val titulo: String, val items: List<ElementoDelMenu>)`
  - `data class EstadoDeEnvio(val pagosPendientes: Int, val ventasPendientes: Int, val ultimoEnvioHaceMinutos: Long?)` con `enum class Estado { TODO_ENVIADO, ESPERANDO_SENAL }` y `val estado: Estado`
  - `data class CabeceraDelMenu(val iniciales: String, val nombre: String, val rol: String?, val ruta: String?, val envio: EstadoDeEnvio?)`
  - `data class ActivoEnElMenu(val seccionId: String?, val itemId: String?)` con `companion object { val NINGUNO }`
  - `object ReglasDelMenu { const val MINIMO_PARA_BUSCAR: Int = 10; fun totalDeOpciones(secciones: List<SeccionDelMenu>): Int; fun muestraBuscador(secciones: List<SeccionDelMenu>): Boolean; fun estaAbierta(seccionId: String, plegadas: Set<String>, activa: String?, tocadas: Set<String>, consulta: String): Boolean; fun filtrar(secciones: List<SeccionDelMenu>, consulta: String): List<SeccionDelMenu>; fun iniciales(nombre: String): String }`
  - `object TextoDelEnvio { fun titulo(envio: EstadoDeEnvio): String; fun haceCuanto(envio: EstadoDeEnvio): String?; fun completo(envio: EstadoDeEnvio): String }`
  - `object MenuFixtures` (pruebas): `cobro`, `ventas`, `almacen`, `garantias`, `todas` (10 opciones), `nueve` (9 opciones), `fun cabecera(envio: EstadoDeEnvio? = TODO_ENVIADO_HACE_3): CabeceraDelMenu`, `val TODO_ENVIADO_HACE_3`, `val DOS_PAGOS_ESPERANDO`

- [ ] **Step 1: Registrar el módulo y su script de build**

En `settings.gradle.kts`, inmediatamente después de `include(":core:geo")`:

```kotlin
include(":core:navegacion")
```

Crear `core/navegacion/build.gradle.kts`:

```kotlin
plugins {
    id("msp.android.library")
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.test")
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint)
    // Goldens de la hoja del menú: claro x oscuro x las tres escalas (Task 5).
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.example.msp_app.core.navegacion"
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric ve res + fontScale
    }
}

// `msp.kover` deja el piso placeholder (0%). El piso real se mide y se fija en
// la Task 18 del plan docs/superpowers/plans/2026-10-06-menu-lateral.md, cuando
// el módulo ya tiene todo su código.

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common")) // BUSINESS_LOCALE (sumidero de versalitas)
    implementation(project(":core:telemetry"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.icons.lucide)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.activity.compose) // BackHandler del atrás predictivo

    testImplementation(project(":core:testing")) // fakes + Turbine + Robolectric + roborazzi (api)
    testImplementation(libs.androidx.ui.test.junit4)
}
```

- [ ] **Step 2: Escribir las pruebas que fallan**

`core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/MenuFixtures.kt`:

```kotlin
package com.example.msp_app.core.navegacion

import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.ElementoDelMenu
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu

/** El menú de un usuario con los cuatro módulos: 4 + 3 + 2 + 1 = 10 opciones. */
object MenuFixtures {

    val cobro = SeccionDelMenu(
        id = "COBRO",
        titulo = "Cobro",
        items = listOf(
            ElementoDelMenu("inicio", "Inicio", IconoDelMenu.INICIO),
            ElementoDelMenu("clientes", "Clientes", IconoDelMenu.CLIENTES, contador = "8 por volver"),
            ElementoDelMenu("reporte_cobranza", "Reporte de cobranza", IconoDelMenu.REPORTE),
            ElementoDelMenu("mapa_rutas", "Mapa de rutas", IconoDelMenu.MAPA)
        )
    )

    val ventas = SeccionDelMenu(
        id = "VENTAS",
        titulo = "Ventas",
        items = listOf(
            ElementoDelMenu("ventas", "Ventas", IconoDelMenu.VENTAS, contador = "1 sin subir"),
            ElementoDelMenu("mi_camioneta", "Mi Camioneta", IconoDelMenu.CAMIONETA),
            ElementoDelMenu("catalogo", "Catálogo de Productos", IconoDelMenu.CATALOGO)
        )
    )

    val almacen = SeccionDelMenu(
        id = "ALMACEN",
        titulo = "Almacén",
        items = listOf(
            ElementoDelMenu("traspasos", "Traspasos", IconoDelMenu.TRASPASOS),
            ElementoDelMenu("asignacion", "Asignación de Camionetas", IconoDelMenu.ASIGNACION)
        )
    )

    val garantias = SeccionDelMenu(
        id = "GARANTIAS",
        titulo = "Garantías",
        items = listOf(ElementoDelMenu("garantias", "Garantías", IconoDelMenu.GARANTIAS))
    )

    val todas: List<SeccionDelMenu> = listOf(cobro, ventas, almacen, garantias)

    val nueve: List<SeccionDelMenu> = listOf(cobro, ventas, almacen)

    val TODO_ENVIADO_HACE_3 = EstadoDeEnvio(pagosPendientes = 0, ventasPendientes = 0, ultimoEnvioHaceMinutos = 3)

    val DOS_PAGOS_ESPERANDO = EstadoDeEnvio(pagosPendientes = 2, ventasPendientes = 0, ultimoEnvioHaceMinutos = null)

    fun cabecera(envio: EstadoDeEnvio? = TODO_ENVIADO_HACE_3) = CabeceraDelMenu(
        iniciales = "ED",
        nombre = "Efraín Domínguez Reyes",
        rol = null,
        ruta = "Zona 21",
        envio = envio
    )
}
```

`core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/application/ReglasDelMenuTest.kt`:

```kotlin
package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.navegacion.MenuFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasDelMenuTest {

    @Test
    fun `el buscador aparece con diez opciones contando secciones plegadas o no`() {
        assertEquals(10, ReglasDelMenu.totalDeOpciones(MenuFixtures.todas))
        assertTrue(ReglasDelMenu.muestraBuscador(MenuFixtures.todas))
    }

    @Test
    fun `con nueve opciones no hay buscador`() {
        assertEquals(9, ReglasDelMenu.totalDeOpciones(MenuFixtures.nueve))
        assertFalse(ReglasDelMenu.muestraBuscador(MenuFixtures.nueve))
    }

    @Test
    fun `una seccion no plegada esta abierta`() {
        assertTrue(ReglasDelMenu.estaAbierta("VENTAS", emptySet(), null, emptySet(), ""))
    }

    @Test
    fun `una seccion plegada que no es la activa esta cerrada`() {
        assertFalse(ReglasDelMenu.estaAbierta("VENTAS", setOf("VENTAS"), "COBRO", emptySet(), ""))
    }

    @Test
    fun `la seccion activa se abre sola aunque el usuario la haya plegado`() {
        assertTrue(ReglasDelMenu.estaAbierta("COBRO", setOf("COBRO"), "COBRO", emptySet(), ""))
    }

    @Test
    fun `si el usuario toco la seccion activa en esta apertura manda su preferencia`() {
        assertFalse(ReglasDelMenu.estaAbierta("COBRO", setOf("COBRO"), "COBRO", setOf("COBRO"), ""))
    }

    @Test
    fun `con una busqueda escrita toda seccion con resultados va abierta`() {
        assertTrue(ReglasDelMenu.estaAbierta("VENTAS", setOf("VENTAS"), null, setOf("VENTAS"), "cat"))
    }

    @Test
    fun `una ruta sin seccion visible no abre nada que no exista`() {
        // Usuario sin VENTAS parado en una ruta de VENTAS: la activa no está entre las suyas.
        val visibles = listOf(MenuFixtures.cobro)
        val abiertas = visibles.filter {
            ReglasDelMenu.estaAbierta(it.id, setOf("COBRO"), "VENTAS", emptySet(), "")
        }
        assertEquals(emptyList<String>(), abiertas.map { it.id })
    }

    @Test
    fun `buscar sin acentos ni mayusculas encuentra garantias`() {
        val resultado = ReglasDelMenu.filtrar(MenuFixtures.todas, "  GARANTIAS ")
        assertEquals(listOf("GARANTIAS"), resultado.map { it.id })
        assertEquals(listOf("garantias"), resultado.single().items.map { it.id })
    }

    @Test
    fun `buscar deja solo las secciones con coincidencias`() {
        val resultado = ReglasDelMenu.filtrar(MenuFixtures.todas, "camioneta")
        assertEquals(listOf("VENTAS", "ALMACEN"), resultado.map { it.id })
        assertEquals(listOf("mi_camioneta"), resultado[0].items.map { it.id })
        assertEquals(listOf("asignacion"), resultado[1].items.map { it.id })
    }

    @Test
    fun `una busqueda sin coincidencias devuelve vacio`() {
        assertEquals(emptyList<Any>(), ReglasDelMenu.filtrar(MenuFixtures.todas, "nómina"))
    }

    @Test
    fun `una busqueda en blanco no filtra`() {
        assertEquals(MenuFixtures.todas, ReglasDelMenu.filtrar(MenuFixtures.todas, "   "))
    }

    @Test
    fun `iniciales de las dos primeras palabras`() {
        assertEquals("ED", ReglasDelMenu.iniciales("efraín  Domínguez Reyes"))
    }

    @Test
    fun `iniciales de un nombre vacio es signo de interrogacion`() {
        assertEquals("?", ReglasDelMenu.iniciales("   "))
    }
}
```

`core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/application/TextoDelEnvioTest.kt`:

```kotlin
package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextoDelEnvioTest {

    private fun envio(pagos: Int = 0, ventas: Int = 0, minutos: Long? = null) =
        EstadoDeEnvio(pagosPendientes = pagos, ventasPendientes = ventas, ultimoEnvioHaceMinutos = minutos)

    @Test
    fun `sin pendientes es todo enviado con su tiempo`() {
        assertEquals("Todo enviado · hace 3 min", TextoDelEnvio.completo(envio(minutos = 3)))
        assertEquals(EstadoDeEnvio.Estado.TODO_ENVIADO, envio().estado)
    }

    @Test
    fun `sin tiempo conocido dice solo todo enviado`() {
        assertEquals("Todo enviado", TextoDelEnvio.completo(envio(minutos = null)))
    }

    @Test
    fun `un pago en singular y varios en plural`() {
        assertEquals("1 pago esperando señal", TextoDelEnvio.completo(envio(pagos = 1, minutos = 5)))
        assertEquals("3 pagos esperando señal", TextoDelEnvio.completo(envio(pagos = 3)))
        assertEquals(EstadoDeEnvio.Estado.ESPERANDO_SENAL, envio(pagos = 1).estado)
    }

    @Test
    fun `solo ventas habla de ventas`() {
        assertEquals("1 venta esperando señal", TextoDelEnvio.titulo(envio(ventas = 1)))
        assertEquals("2 ventas esperando señal", TextoDelEnvio.titulo(envio(ventas = 2)))
    }

    @Test
    fun `pagos y ventas juntos se cuentan como envios`() {
        assertEquals("4 envíos esperando señal", TextoDelEnvio.titulo(envio(pagos = 3, ventas = 1)))
    }

    @Test
    fun `esperando senal nunca dice hace cuanto`() {
        assertNull(TextoDelEnvio.haceCuanto(envio(pagos = 1, minutos = 2)))
    }

    @Test
    fun `menos de un minuto es hace un momento`() {
        assertEquals("hace un momento", TextoDelEnvio.haceCuanto(envio(minutos = 0)))
    }

    @Test
    fun `minutos negativos no se pintan`() {
        assertNull(TextoDelEnvio.haceCuanto(envio(minutos = -3)))
    }

    @Test
    fun `horas y dias en vez de cientos de minutos`() {
        assertEquals("hace 59 min", TextoDelEnvio.haceCuanto(envio(minutos = 59)))
        assertEquals("hace 1 h", TextoDelEnvio.haceCuanto(envio(minutos = 60)))
        assertEquals("hace 23 h", TextoDelEnvio.haceCuanto(envio(minutos = 1_439)))
        assertEquals("hace 1 día", TextoDelEnvio.haceCuanto(envio(minutos = 1_440)))
        assertEquals("hace 3 días", TextoDelEnvio.haceCuanto(envio(minutos = 4_400)))
    }
}
```

- [ ] **Step 3: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest
```

Expected: FAIL en `compileDebugUnitTestKotlin` con `Unresolved reference: ReglasDelMenu`, `TextoDelEnvio`, `EstadoDeEnvio`.

- [ ] **Step 4: Implementación mínima**

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/domain/model/ModeloDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.domain.model

/**
 * Qué dibujo lleva una opción. El vector concreto (Lucide) lo decide `ui/`:
 * `domain/` no importa Compose (principio 15).
 */
enum class IconoDelMenu {
    INICIO,
    CLIENTES,
    REPORTE,
    MAPA,
    VENTAS,
    CAMIONETA,
    CATALOGO,
    TRASPASOS,
    ASIGNACION,
    GARANTIAS,
    CONFIGURACION
}

/**
 * Una opción del menú. [contador] llega YA formateado ("8 por volver") y sólo
 * cuando su fuente existe (spec §4): `null` es "sin contador", nunca un número
 * inventado.
 */
data class ElementoDelMenu(
    val id: String,
    val etiqueta: String,
    val icono: IconoDelMenu,
    val contador: String? = null
)

/** Una sección por módulo: `COBRO`, `VENTAS`, `ALMACEN`, `GARANTIAS`. */
data class SeccionDelMenu(
    val id: String,
    val titulo: String,
    val items: List<ElementoDelMenu>
)

/**
 * Lo que la tarjeta de la cabecera cuenta. Pagos y ventas van separados para
 * que el texto no diga "pagos" cuando lo que espera es una venta (decisión D6
 * del plan). [ultimoEnvioHaceMinutos] es `null` cuando no hay un dato honesto
 * que dar (nunca se sincronizó, falló el último intento o el reloj está raro).
 */
data class EstadoDeEnvio(
    val pagosPendientes: Int,
    val ventasPendientes: Int,
    val ultimoEnvioHaceMinutos: Long?
) {
    enum class Estado { TODO_ENVIADO, ESPERANDO_SENAL }

    val estado: Estado
        get() = if (pagosPendientes + ventasPendientes == 0) Estado.TODO_ENVIADO else Estado.ESPERANDO_SENAL
}

/**
 * La cabecera fija. [rol] y [ruta] son opcionales porque `User` no trae rol
 * (decisión D11 del plan): lo que no tiene fuente no se pinta. [envio] es
 * `null` mientras la primera lectura no llega — pintar "Todo enviado" antes de
 * saberlo sería mentir.
 */
data class CabeceraDelMenu(
    val iniciales: String,
    val nombre: String,
    val rol: String?,
    val ruta: String?,
    val envio: EstadoDeEnvio?
)

/** La sección que se abre sola y la opción que se resalta, según la pantalla actual. */
data class ActivoEnElMenu(
    val seccionId: String?,
    val itemId: String?
) {
    companion object {
        val NINGUNO = ActivoEnElMenu(seccionId = null, itemId = null)
    }
}
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/application/ReglasDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import java.text.Normalizer

/**
 * Las reglas del menú que no necesitan Compose para probarse.
 *
 * ## Dos señales, no una bandera (spec §7, "doble escritura")
 *
 * [estaAbierta] mezcla la PREFERENCIA guardada ([plegadas]) con el
 * "forzado abierto" de la pantalla actual ([activa]) sin escribir nunca la
 * segunda en la primera. [tocadas] son las secciones que el usuario tocó en
 * ESTA apertura: si toca la sección activa, manda su preferencia; al cerrar el
 * menú [tocadas] se vacía y la próxima apertura vuelve a forzarla.
 */
object ReglasDelMenu {

    /** Spec §2.1.3: el buscador aparece con 10 opciones o más. */
    const val MINIMO_PARA_BUSCAR: Int = 10

    private val MARCAS_DIACRITICAS = Regex("\\p{M}+")
    private val ESPACIOS = Regex("\\s+")

    fun totalDeOpciones(secciones: List<SeccionDelMenu>): Int = secciones.sumOf { it.items.size }

    fun muestraBuscador(secciones: List<SeccionDelMenu>): Boolean =
        totalDeOpciones(secciones) >= MINIMO_PARA_BUSCAR

    fun estaAbierta(
        seccionId: String,
        plegadas: Set<String>,
        activa: String?,
        tocadas: Set<String>,
        consulta: String
    ): Boolean = when {
        consulta.isNotBlank() -> true
        seccionId == activa && seccionId !in tocadas -> true
        else -> seccionId !in plegadas
    }

    fun filtrar(secciones: List<SeccionDelMenu>, consulta: String): List<SeccionDelMenu> {
        val buscada = normalizar(consulta)
        if (buscada.isEmpty()) return secciones
        return secciones
            .map { seccion -> seccion.copy(items = seccion.items.filter { normalizar(it.etiqueta).contains(buscada) }) }
            .filter { it.items.isNotEmpty() }
    }

    /** Las iniciales de las dos primeras palabras; "?" si no hay nombre. */
    fun iniciales(nombre: String): String = nombre
        .trim()
        .split(ESPACIOS)
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
            .replace(MARCAS_DIACRITICAS, "")
            .lowercase(BUSINESS_LOCALE)
}
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/application/TextoDelEnvio.kt`:

```kotlin
package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio

/**
 * El texto de la tarjeta de envío. "hace N min" sólo acompaña a "Todo enviado"
 * (spec §2.1.1); con pendientes no aplica. Pasados 60 minutos cambia a horas y
 * pasadas 24 h a días: "hace 600 min" no se lee.
 */
object TextoDelEnvio {

    private const val MINUTOS_POR_HORA = 60L
    private const val MINUTOS_POR_DIA = 1_440L

    fun titulo(envio: EstadoDeEnvio): String {
        val pagos = envio.pagosPendientes
        val ventas = envio.ventasPendientes
        return when {
            pagos == 0 && ventas == 0 -> "Todo enviado"
            ventas == 0 -> "$pagos ${if (pagos == 1) "pago" else "pagos"} esperando señal"
            pagos == 0 -> "$ventas ${if (ventas == 1) "venta" else "ventas"} esperando señal"
            else -> "${pagos + ventas} envíos esperando señal"
        }
    }

    fun haceCuanto(envio: EstadoDeEnvio): String? {
        if (envio.estado != EstadoDeEnvio.Estado.TODO_ENVIADO) return null
        val minutos = envio.ultimoEnvioHaceMinutos ?: return null
        return when {
            minutos < 0 -> null
            minutos < 1 -> "hace un momento"
            minutos < MINUTOS_POR_HORA -> "hace $minutos min"
            minutos < MINUTOS_POR_DIA -> "hace ${minutos / MINUTOS_POR_HORA} h"
            else -> {
                val dias = minutos / MINUTOS_POR_DIA
                if (dias == 1L) "hace 1 día" else "hace $dias días"
            }
        }
    }

    fun completo(envio: EstadoDeEnvio): String =
        listOfNotNull(titulo(envio), haceCuanto(envio)).joinToString(" · ")
}
```

- [ ] **Step 5: Correr y ver verde, y contar**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest :core:navegacion:detekt :core:navegacion:ktlintFormat :core:navegacion:ktlintCheck
grep -ho 'tests="[0-9]*"' core/navegacion/build/test-results/testDebugUnitTest/TEST-*.xml
```

Expected: BUILD SUCCESSFUL; los XML suman `tests="14"` (ReglasDelMenuTest) y `tests="9"` (TextoDelEnvioTest).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts core/navegacion
git commit -m "feat(navegacion): modulo core:navegacion con modelo y reglas del menu lateral"
```

---

### Task 2: Puertos, estado combinado y `MenuLateralViewModel`

**Files:**
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/domain/port/PuertosDelMenu.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/application/EstadoDelMenu.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/application/NavegacionTelemetria.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralViewModel.kt`
- Test: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/fake/FakesDelMenu.kt`
- Test: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralViewModelTest.kt`

**Interfaces:**
- Consumes: `EstadoDeEnvio` (Task 1); `Telemetry` (`com.example.msp_app.core.telemetry.Telemetry`, `fun error(code: String, message: String, props: Map<String, String> = emptyMap())`).
- Produces:
  - `interface ContadoresDelMenuPort { suspend fun contadores(ids: Set<String>): Map<String, String> }`
  - `interface EstadoDeEnvioPort { fun estado(): Flow<EstadoDeEnvio> }`
  - `interface SeccionesPlegadasPort { fun plegadas(): Flow<Set<String>>; suspend fun setPlegada(seccionId: String, plegada: Boolean) }`
  - `data class EstadoDelMenu(val envio: EstadoDeEnvio? = null, val plegadas: Set<String> = emptySet(), val contadores: Map<String, String> = emptyMap())`
  - `object NavegacionTelemetria { const val CODE_CONTADORES_FALLARON = "menu_contadores_fallaron"; const val CODE_ENVIO_FALLO = "menu_envio_fallo"; const val CODE_PLEGADAS_FALLARON = "menu_plegadas_fallaron" }`
  - `@HiltViewModel class MenuLateralViewModel @Inject constructor(ContadoresDelMenuPort, EstadoDeEnvioPort, SeccionesPlegadasPort, Telemetry)` con `val estado: StateFlow<EstadoDelMenu>`, `fun alAbrir(ids: Set<String>)`, `fun cambiarPlegado(seccionId: String, plegar: Boolean)`
  - Fakes (pruebas): `FakeEstadoDeEnvioPort`, `FakeContadoresDelMenuPort`, `FakeSeccionesPlegadasPort`

- [ ] **Step 1: Escribir los fakes y la prueba que falla**

`core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/fake/FakesDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.fake

import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.domain.port.ContadoresDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.EstadoDeEnvioPort
import com.example.msp_app.core.navegacion.domain.port.SeccionesPlegadasPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class FakeEstadoDeEnvioPort : EstadoDeEnvioPort {
    val flujo = MutableSharedFlow<EstadoDeEnvio>(replay = 1)
    var falla: Throwable? = null

    override fun estado(): Flow<EstadoDeEnvio> {
        val error = falla ?: return flujo
        return flow { throw error }
    }
}

class FakeContadoresDelMenuPort : ContadoresDelMenuPort {
    var respuesta: Map<String, String> = emptyMap()
    var falla: Exception? = null
    val pedidos = mutableListOf<Set<String>>()

    override suspend fun contadores(ids: Set<String>): Map<String, String> {
        pedidos += ids
        falla?.let { throw it }
        return respuesta.filterKeys { it in ids }
    }
}

class FakeSeccionesPlegadasPort : SeccionesPlegadasPort {
    val valores = MutableStateFlow<Set<String>>(emptySet())
    val escrituras = mutableListOf<Pair<String, Boolean>>()

    override fun plegadas(): Flow<Set<String>> = valores

    override suspend fun setPlegada(seccionId: String, plegada: Boolean) {
        escrituras += seccionId to plegada
        valores.value = if (plegada) valores.value + seccionId else valores.value - seccionId
    }
}
```

`core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralViewModelTest.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui

import com.example.msp_app.core.navegacion.application.EstadoDelMenu
import com.example.msp_app.core.navegacion.application.NavegacionTelemetria
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.fake.FakeContadoresDelMenuPort
import com.example.msp_app.core.navegacion.fake.FakeEstadoDeEnvioPort
import com.example.msp_app.core.navegacion.fake.FakeSeccionesPlegadasPort
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MenuLateralViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val envio = FakeEstadoDeEnvioPort()
    private val contadores = FakeContadoresDelMenuPort()
    private val plegadas = FakeSeccionesPlegadasPort()
    private val telemetria = RecordingTelemetry(FakeClock())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MenuLateralViewModel(contadores, envio, plegadas, telemetria)

    private fun errores(code: String) =
        telemetria.recorded.filter { it.type == TelemetryEventType.ERROR && it.name == code }

    @Test
    fun `antes de la primera lectura el envio es null, nunca todo enviado`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        assertNull(vm.estado.value.envio)
    }

    @Test
    fun `combina envio y plegadas en un solo estado`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        envio.flujo.emit(EstadoDeEnvio(pagosPendientes = 2, ventasPendientes = 0, ultimoEnvioHaceMinutos = null))
        plegadas.valores.value = setOf("VENTAS")
        advanceUntilIdle()

        assertEquals(
            EstadoDelMenu(
                envio = EstadoDeEnvio(pagosPendientes = 2, ventasPendientes = 0, ultimoEnvioHaceMinutos = null),
                plegadas = setOf("VENTAS"),
                contadores = emptyMap()
            ),
            vm.estado.value
        )
    }

    @Test
    fun `abrir pide los contadores de las opciones visibles y los publica`() = runTest(dispatcher) {
        contadores.respuesta = mapOf("clientes" to "8 por volver", "ventas" to "1 sin subir")
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }

        vm.alAbrir(setOf("inicio", "clientes"))
        advanceUntilIdle()

        assertEquals(listOf(setOf("inicio", "clientes")), contadores.pedidos)
        assertEquals(mapOf("clientes" to "8 por volver"), vm.estado.value.contadores)
    }

    @Test
    fun `si los contadores fallan el menu queda sin contadores y lo reporta`() = runTest(dispatcher) {
        contadores.falla = IllegalStateException("room cerrada")
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }

        vm.alAbrir(setOf("clientes"))
        advanceUntilIdle()

        assertEquals(emptyMap<String, String>(), vm.estado.value.contadores)
        val error = errores(NavegacionTelemetria.CODE_CONTADORES_FALLARON).single()
        assertEquals("IllegalStateException", error.props["excepcion"])
    }

    @Test
    fun `si la fuente de envio falla se reporta y la tarjeta no se pinta`() = runTest(dispatcher) {
        envio.falla = IOException("disco")
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        assertNull(vm.estado.value.envio)
        assertEquals(1, errores(NavegacionTelemetria.CODE_ENVIO_FALLO).size)
    }

    @Test
    fun `cambiar plegado escribe la preferencia`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }

        vm.cambiarPlegado("VENTAS", plegar = true)
        advanceUntilIdle()

        assertEquals(listOf("VENTAS" to true), plegadas.escrituras)
        assertEquals(setOf("VENTAS"), vm.estado.value.plegadas)
    }
}
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest --tests 'com.example.msp_app.core.navegacion.ui.MenuLateralViewModelTest'
```

Expected: FAIL de compilación: `Unresolved reference: ContadoresDelMenuPort`, `MenuLateralViewModel`, `EstadoDelMenu`.

- [ ] **Step 3: Implementación mínima**

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/domain/port/PuertosDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.domain.port

import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import kotlinx.coroutines.flow.Flow

/**
 * Contadores ya formateados por id de opción, sólo para [ids] (las opciones que
 * el usuario ve). Ids sin fuente simplemente no aparecen en el mapa.
 * Ruling BF: cruza módulo — las fuentes viven en `:feature:pagos` y Room, y el
 * adaptador en `:app` (decisión D2 del plan: se llama al abrir, no en cada frame).
 */
interface ContadoresDelMenuPort {
    suspend fun contadores(ids: Set<String>): Map<String, String>
}

/** Pendientes de subir + hace cuánto hubo contacto sano con el servidor (spec §4). */
interface EstadoDeEnvioPort {
    fun estado(): Flow<EstadoDeEnvio>
}

/** Qué secciones dejó plegadas el usuario. Persistido (decisión D3 del plan). */
interface SeccionesPlegadasPort {
    fun plegadas(): Flow<Set<String>>

    suspend fun setPlegada(seccionId: String, plegada: Boolean)
}
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/application/EstadoDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio

/** Lo que la hoja necesita de los puertos, en un solo valor. */
data class EstadoDelMenu(
    val envio: EstadoDeEnvio? = null,
    val plegadas: Set<String> = emptySet(),
    val contadores: Map<String, String> = emptyMap()
)
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/application/NavegacionTelemetria.kt`:

```kotlin
package com.example.msp_app.core.navegacion.application

/** Códigos de telemetría del menú lateral: constantes, únicos y grepeables (principio 20). */
object NavegacionTelemetria {
    const val CODE_CONTADORES_FALLARON: String = "menu_contadores_fallaron"
    const val CODE_ENVIO_FALLO: String = "menu_envio_fallo"
    const val CODE_PLEGADAS_FALLARON: String = "menu_plegadas_fallaron"
}
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralViewModel.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.navegacion.application.EstadoDelMenu
import com.example.msp_app.core.navegacion.application.NavegacionTelemetria
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.domain.port.ContadoresDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.EstadoDeEnvioPort
import com.example.msp_app.core.navegacion.domain.port.SeccionesPlegadasPort
import com.example.msp_app.core.telemetry.Telemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MenuLateralViewModel @Inject constructor(
    private val contadoresPort: ContadoresDelMenuPort,
    envioPort: EstadoDeEnvioPort,
    private val plegadasPort: SeccionesPlegadasPort,
    private val telemetry: Telemetry
) : ViewModel() {

    private val contadores = MutableStateFlow<Map<String, String>>(emptyMap())

    val estado: StateFlow<EstadoDelMenu> = combine(
        envioPort.estado()
            .map<EstadoDeEnvio, EstadoDeEnvio?> { it }
            // `null` de arranque: sin esto `combine` no emitiría NADA (ni plegadas ni
            // contadores) hasta la primera lectura de Room.
            .onStart { emit(null) }
            .catch { falla ->
                reportar(NavegacionTelemetria.CODE_ENVIO_FALLO, falla)
                emit(null)
            },
        plegadasPort.plegadas()
            .catch { falla ->
                reportar(NavegacionTelemetria.CODE_PLEGADAS_FALLARON, falla)
                emit(emptySet())
            },
        contadores
    ) { envio, plegadas, cuentas ->
        EstadoDelMenu(envio = envio, plegadas = plegadas, contadores = cuentas)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUSCRIPCION_MS), EstadoDelMenu())

    /** Refresca los contadores de las opciones visibles. Se llama cada vez que el menú se abre. */
    fun alAbrir(ids: Set<String>) {
        viewModelScope.launch { contadores.value = leerContadores(ids) }
    }

    fun cambiarPlegado(seccionId: String, plegar: Boolean) {
        viewModelScope.launch { escribirPlegado(seccionId, plegar) }
    }

    @Suppress("TooGenericExceptionCaught") // cualquier falla de una fuente deja la opción sin contador, nunca tumba el menú
    private suspend fun leerContadores(ids: Set<String>): Map<String, String> = try {
        contadoresPort.contadores(ids)
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (falla: Exception) {
        reportar(NavegacionTelemetria.CODE_CONTADORES_FALLARON, falla)
        emptyMap()
    }

    @Suppress("TooGenericExceptionCaught") // una preferencia que no se guarda no debe tumbar el menú
    private suspend fun escribirPlegado(seccionId: String, plegar: Boolean) {
        try {
            plegadasPort.setPlegada(seccionId, plegar)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (falla: Exception) {
            reportar(NavegacionTelemetria.CODE_PLEGADAS_FALLARON, falla)
        }
    }

    private fun reportar(code: String, falla: Throwable) {
        telemetry.error(
            code = code,
            message = "el menu lateral no pudo leer o guardar una fuente",
            props = mapOf("excepcion" to falla::class.java.simpleName)
        )
    }

    private companion object {
        const val SUSCRIPCION_MS = 5_000L
    }
}
```

- [ ] **Step 4: Correr y ver verde, y contar**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest :core:navegacion:detekt :core:navegacion:ktlintFormat :core:navegacion:ktlintCheck
grep -ho 'tests="[0-9]*"' core/navegacion/build/test-results/testDebugUnitTest/TEST-*MenuLateralViewModelTest.xml
```

Expected: BUILD SUCCESSFUL; `tests="6"`.

- [ ] **Step 5: Commit**

```bash
git add core/navegacion
git commit -m "feat(navegacion): puertos del menu lateral y su ViewModel con fakes"
```

---

### Task 3: La hoja `MenuLateral` (pura)

**Files:**
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/AperturaDelMenu.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateral.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/IconosDelMenu.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/FilaDelMenu.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/SeccionPlegable.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/EncabezadoDelMenu.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/BuscadorDelMenu.kt`
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/PieDelMenu.kt`
- Test: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralTest.kt`

**Interfaces:**
- Consumes: modelo y `ReglasDelMenu`/`TextoDelEnvio` (Task 1); `MspTheme`, `MspInitialsAvatar(initials: String, modifier: Modifier = Modifier, size: Dp = 38.dp)` de `:core:designsystem`.
- Produces:
  - `@Stable class AperturaDelMenu { var consulta: String; val tocadas: Set<String>; fun tocar(seccionId: String); fun reiniciar() }` y `@Composable fun rememberAperturaDelMenu(): AperturaDelMenu`
  - `@Composable fun MenuLateral(cabecera: CabeceraDelMenu, secciones: List<SeccionDelMenu>, activo: ActivoEnElMenu, seccionesPlegadas: Set<String>, apertura: AperturaDelMenu, onTogglePlegado: (seccionId: String, plegar: Boolean) -> Unit, onElegir: (ElementoDelMenu) -> Unit, pie: @Composable () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun FilaDelMenu(etiqueta: String, icono: IconoDelMenu, activo: Boolean, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier, contador: String? = null)`
  - `@Composable fun PieDelMenu(version: String, onConfiguracion: () -> Unit, aviso: @Composable () -> Unit = {})`
  - Constantes públicas: `MENU_LATERAL_TAG = "menu_lateral"`, `FILA_DEL_MENU_TAG = "menu_lateral_fila_"`, `SECCION_DEL_MENU_TAG = "menu_lateral_seccion_"`, `BUSCADOR_DEL_MENU_TAG = "menu_lateral_buscador"`, `DESVANECIDO_DEL_MENU_TAG = "menu_lateral_desvanecido"`, `ENVIO_DEL_MENU_TAG = "menu_lateral_envio"`, `ID_CONFIGURACION = "configuracion"`, `val ANCHO_DEL_MENU: Dp = 304.dp`

- [ ] **Step 1: Escribir la prueba que falla**

`core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralTest.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.MenuFixtures
import com.example.msp_app.core.navegacion.domain.model.ActivoEnElMenu
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.ElementoDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MenuLateralTest : RobolectricTestBase() {

    @get:Rule
    val compose = createComposeRule()

    private val toques = mutableListOf<Pair<String, Boolean>>()
    private val elegidos = mutableListOf<ElementoDelMenu>()

    private fun montar(
        secciones: List<SeccionDelMenu> = MenuFixtures.todas,
        activo: ActivoEnElMenu = ActivoEnElMenu("COBRO", "clientes"),
        plegadas: Set<String> = emptySet(),
        cabecera: CabeceraDelMenu = MenuFixtures.cabecera(),
        alto: Dp = 2_000.dp,
        apertura: AperturaDelMenu = AperturaDelMenu()
    ) {
        compose.setContent {
            MspTheme(animateColors = false) {
                Box(Modifier.width(ANCHO_DEL_MENU).height(alto)) {
                    MenuLateral(
                        cabecera = cabecera,
                        secciones = secciones,
                        activo = activo,
                        seccionesPlegadas = plegadas,
                        apertura = apertura,
                        onTogglePlegado = { id, plegar -> toques += id to plegar },
                        onElegir = { elegidos += it },
                        pie = { PieDelMenu(version = "Versión 2.19.3", onConfiguracion = {}) }
                    )
                }
            }
        }
    }

    @Test
    fun `resalta solo la opcion activa`() {
        montar()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "clientes").assertIsSelected()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "inicio").assertIsNotSelected()
    }

    @Test
    fun `tocar una opcion la entrega a onElegir`() {
        montar()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "mapa_rutas").performClick()
        assertEquals(listOf("mapa_rutas"), elegidos.map { it.id })
    }

    @Test
    fun `plegar una seccion abierta pide plegar con su id`() {
        montar()
        compose.onNodeWithTag(SECCION_DEL_MENU_TAG + "VENTAS").performClick()
        assertEquals(listOf("VENTAS" to true), toques)
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "ventas").assertDoesNotExist()
    }

    @Test
    fun `una seccion plegada no pinta sus opciones y desplegarla pide plegar false`() {
        montar(plegadas = setOf("ALMACEN"))
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "traspasos").assertDoesNotExist()
        compose.onNodeWithTag(SECCION_DEL_MENU_TAG + "ALMACEN").performClick()
        assertEquals(listOf("ALMACEN" to false), toques)
    }

    @Test
    fun `la seccion activa se abre aunque este plegada y no escribe la preferencia`() {
        montar(plegadas = setOf("COBRO"))
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "clientes").assertExists()
        assertEquals(emptyList<Pair<String, Boolean>>(), toques)
    }

    @Test
    fun `tocar la seccion activa forzada la pliega en esta apertura`() {
        montar(plegadas = setOf("COBRO"))
        compose.onNodeWithTag(SECCION_DEL_MENU_TAG + "COBRO").performClick()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "clientes").assertDoesNotExist()
        assertEquals(listOf("COBRO" to true), toques)
    }

    @Test
    fun `el buscador aparece con diez opciones`() {
        montar(secciones = MenuFixtures.todas)
        compose.onNodeWithTag(BUSCADOR_DEL_MENU_TAG).assertExists()
    }

    @Test
    fun `sin diez opciones no hay buscador`() {
        montar(secciones = MenuFixtures.nueve)
        compose.onNodeWithTag(BUSCADOR_DEL_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun `buscar filtra aunque la seccion este plegada`() {
        montar(plegadas = setOf("GARANTIAS"))
        compose.onNodeWithTag(BUSCADOR_DEL_MENU_TAG).performTextInput("garantias")
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "garantias").assertExists()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "clientes").assertDoesNotExist()
    }

    @Test
    fun `buscar algo que no existe dice sin resultados`() {
        montar()
        compose.onNodeWithTag(BUSCADOR_DEL_MENU_TAG).performTextInput("nómina")
        compose.onNodeWithText("Sin resultados").assertExists()
    }

    @Test
    fun `el desvanecido aparece solo cuando hay mas por desplazar`() {
        montar(alto = 360.dp)
        compose.onNodeWithTag(DESVANECIDO_DEL_MENU_TAG).assertExists()
    }

    @Test
    fun `sin contenido fuera de vista no hay desvanecido`() {
        montar(secciones = listOf(MenuFixtures.garantias))
        compose.onNodeWithTag(DESVANECIDO_DEL_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun `cada fila y cada encabezado de seccion miden al menos el toque del repo`() {
        montar()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "clientes").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + ID_CONFIGURACION).assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag(SECCION_DEL_MENU_TAG + "COBRO").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag(BUSCADOR_DEL_MENU_TAG).assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun `el contador se pinta junto a su opcion`() {
        montar()
        compose.onNodeWithText("8 por volver").assertExists()
    }

    @Test
    fun `la tarjeta de envio dice todo enviado con su tiempo`() {
        montar()
        compose.onNodeWithText("Todo enviado · hace 3 min").assertExists()
    }

    @Test
    fun `la tarjeta de envio dice cuantos pagos esperan`() {
        montar(cabecera = MenuFixtures.cabecera(envio = MenuFixtures.DOS_PAGOS_ESPERANDO))
        compose.onNodeWithText("2 pagos esperando señal").assertExists()
    }

    @Test
    fun `sin lectura de envio no hay tarjeta`() {
        montar(cabecera = MenuFixtures.cabecera(envio = null))
        compose.onNodeWithTag(ENVIO_DEL_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun `el pie lleva configuracion y la version`() {
        montar(secciones = emptyList())
        compose.onNodeWithText("Configuración").assertExists()
        compose.onNodeWithText("Versión 2.19.3").assertExists()
    }
}
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest --tests 'com.example.msp_app.core.navegacion.ui.MenuLateralTest'
```

Expected: FAIL de compilación: `Unresolved reference: MenuLateral`, `AperturaDelMenu`, `PieDelMenu`, `FILA_DEL_MENU_TAG`.

- [ ] **Step 3: Implementación mínima**

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/AperturaDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * El estado de UNA apertura del menú: lo escrito en el buscador y las secciones
 * que el usuario tocó. El contenedor llama [reiniciar] al cerrar, así la próxima
 * apertura llega limpia (Review Focus 3 del plan).
 */
@Stable
class AperturaDelMenu {
    var consulta: String by mutableStateOf("")

    var tocadas: Set<String> by mutableStateOf(emptySet())
        private set

    fun tocar(seccionId: String) {
        tocadas = tocadas + seccionId
    }

    fun reiniciar() {
        consulta = ""
        tocadas = emptySet()
    }
}

@Composable
fun rememberAperturaDelMenu(): AperturaDelMenu = remember { AperturaDelMenu() }
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/IconosDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.Truck
import com.composables.icons.lucide.UserCog
import com.composables.icons.lucide.Users
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu

/** Medido 2026-10-06: los doce nombres existen en `icons-lucide-1.0.0` (`unzip -l` del jar del caché). */
internal fun IconoDelMenu.vector(): ImageVector = when (this) {
    IconoDelMenu.INICIO -> Lucide.House
    IconoDelMenu.CLIENTES -> Lucide.Users
    IconoDelMenu.REPORTE -> Lucide.ChartColumn
    IconoDelMenu.MAPA -> Lucide.Map
    IconoDelMenu.VENTAS -> Lucide.ShoppingCart
    IconoDelMenu.CAMIONETA -> Lucide.Truck
    IconoDelMenu.CATALOGO -> Lucide.BookOpen
    IconoDelMenu.TRASPASOS -> Lucide.ArrowLeftRight
    IconoDelMenu.ASIGNACION -> Lucide.UserCog
    IconoDelMenu.GARANTIAS -> Lucide.ShieldCheck
    IconoDelMenu.CONFIGURACION -> Lucide.Settings
}
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/FilaDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu

/**
 * Una opción del menú. Alto MÍNIMO `touchTarget` (56 dp, spec §2.1.6): crece si
 * el texto envuelve a escala 2.0, nunca se trunca (spec §5). Activa: fondo
 * `brandTint`, texto `brand` (spec §2.1.5).
 */
@Composable
fun FilaDelMenu(
    etiqueta: String,
    icono: IconoDelMenu,
    activo: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contador: String? = null
) {
    val colores = MspTheme.colors
    val tinta = if (activo) colores.brand else colores.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MspTheme.spacing.touchTarget)
            .clip(MspTheme.shapes.control)
            .background(if (activo) colores.brandTint else colores.surface)
            .clickable(onClick = onClick)
            .semantics { selected = activo }
            .testTag(tag)
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        Icon(
            imageVector = icono.vector(),
            contentDescription = null,
            tint = if (activo) colores.brand else colores.onSurfaceMuted,
            modifier = Modifier.size(MspTheme.spacing.lg)
        )
        Text(
            text = etiqueta,
            style = if (activo) MspTheme.type.bodyStrong else MspTheme.type.body,
            color = tinta,
            modifier = Modifier.weight(1f)
        )
        if (contador != null) {
            Text(
                text = contador,
                style = MspTheme.type.caption,
                color = if (activo) colores.brand else colores.onSurfaceMuted
            )
        }
    }
}
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/SeccionPlegable.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.domain.model.ElementoDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import com.example.msp_app.core.navegacion.ui.FILA_DEL_MENU_TAG
import com.example.msp_app.core.navegacion.ui.SECCION_DEL_MENU_TAG

/**
 * Encabezado plegable + sus opciones. Plegar es instantáneo, sin animación: nada
 * salta (principio 12) y no hay nada que cortar con movimiento reducido.
 */
@Composable
internal fun SeccionPlegable(
    seccion: SeccionDelMenu,
    abierta: Boolean,
    habilitada: Boolean,
    itemActivoId: String?,
    onToggle: () -> Unit,
    onElegir: (ElementoDelMenu) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MspTheme.spacing.touchTarget)
                .clip(MspTheme.shapes.control)
                .clickable(enabled = habilitada, onClick = onToggle)
                .semantics { stateDescription = if (abierta) "Abierta" else "Plegada" }
                .testTag(SECCION_DEL_MENU_TAG + seccion.id)
                .padding(horizontal = MspTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = seccion.titulo.uppercase(BUSINESS_LOCALE),
                style = MspTheme.type.sectionLabel,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (abierta) Lucide.ChevronDown else Lucide.ChevronRight,
                contentDescription = null,
                tint = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(MspTheme.spacing.lg)
            )
        }
        if (abierta) {
            seccion.items.forEach { elemento ->
                FilaDelMenu(
                    etiqueta = elemento.etiqueta,
                    icono = elemento.icono,
                    activo = elemento.id == itemActivoId,
                    tag = FILA_DEL_MENU_TAG + elemento.id,
                    onClick = { onElegir(elemento) },
                    contador = elemento.contador
                )
            }
        }
    }
}
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/EncabezadoDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.application.TextoDelEnvio
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.ui.ENVIO_DEL_MENU_TAG

@Composable
internal fun EncabezadoDelMenu(cabecera: CabeceraDelMenu) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
        ) {
            MspInitialsAvatar(initials = cabecera.iniciales, size = MspTheme.spacing.touchTarget)
            Column(Modifier.weight(1f)) {
                if (cabecera.nombre.isNotBlank()) {
                    Text(text = cabecera.nombre, style = MspTheme.type.name, color = MspTheme.colors.onSurface)
                }
                val detalle = listOfNotNull(cabecera.rol, cabecera.ruta).joinToString(" · ")
                if (detalle.isNotEmpty()) {
                    Text(text = detalle, style = MspTheme.type.caption, color = MspTheme.colors.onSurfaceMuted)
                }
            }
        }
        cabecera.envio?.let { TarjetaDeEnvio(it) }
    }
}

/** El punto nunca va solo: siempre con su texto (spec §5, regla de `MspStatusChip`). */
@Composable
private fun TarjetaDeEnvio(envio: EstadoDeEnvio) {
    val punto = if (envio.estado == EstadoDeEnvio.Estado.TODO_ENVIADO) {
        MspTheme.colors.statusPaid
    } else {
        MspTheme.colors.statusPartial
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.tile)
            .background(MspTheme.colors.surface2)
            .testTag(ENVIO_DEL_MENU_TAG)
            .padding(MspTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Box(
            Modifier
                .size(MspTheme.spacing.sm)
                .clip(MspTheme.shapes.chip)
                .background(punto)
        )
        Text(text = TextoDelEnvio.completo(envio), style = MspTheme.type.body, color = MspTheme.colors.onSurface)
    }
}
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/BuscadorDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.ui.BUSCADOR_DEL_MENU_TAG

@Composable
internal fun BuscadorDelMenu(consulta: String, onConsulta: (String) -> Unit) {
    BasicTextField(
        value = consulta,
        onValueChange = onConsulta,
        singleLine = true,
        textStyle = MspTheme.type.body.copy(color = MspTheme.colors.onSurface),
        cursorBrush = SolidColor(MspTheme.colors.brand),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md)
            .testTag(BUSCADOR_DEL_MENU_TAG),
        decorationBox = { campo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MspTheme.spacing.touchTarget)
                    .clip(MspTheme.shapes.field)
                    .background(MspTheme.colors.surface2)
                    .padding(horizontal = MspTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
            ) {
                Icon(
                    imageVector = Lucide.Search,
                    contentDescription = null,
                    tint = MspTheme.colors.onSurfaceMuted,
                    modifier = Modifier.size(MspTheme.spacing.lg)
                )
                Box(Modifier.weight(1f)) {
                    if (consulta.isEmpty()) {
                        Text("Buscar en el menú", style = MspTheme.type.body, color = MspTheme.colors.onSurfaceMuted)
                    }
                    campo()
                }
            }
        }
    )
}
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/components/PieDelMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu
import com.example.msp_app.core.navegacion.ui.FILA_DEL_MENU_TAG
import com.example.msp_app.core.navegacion.ui.ID_CONFIGURACION

/**
 * Pie fijo (spec §2.1.4): Configuración + versión. [aviso] es la ranura del
 * banner de actualización de `:app` (decisión D12 del plan). [version] llega ya
 * armada porque `BuildConfig` sólo existe en `:app` (spec §3.3).
 */
@Composable
fun PieDelMenu(version: String, onConfiguracion: () -> Unit, aviso: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.sm)
    ) {
        aviso()
        FilaDelMenu(
            etiqueta = "Configuración",
            icono = IconoDelMenu.CONFIGURACION,
            activo = false,
            tag = FILA_DEL_MENU_TAG + ID_CONFIGURACION,
            onClick = onConfiguracion
        )
        Text(
            text = version,
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = MspTheme.spacing.sm)
        )
    }
}
```

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateral.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.application.ReglasDelMenu
import com.example.msp_app.core.navegacion.domain.model.ActivoEnElMenu
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.ElementoDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import com.example.msp_app.core.navegacion.ui.components.BuscadorDelMenu
import com.example.msp_app.core.navegacion.ui.components.EncabezadoDelMenu
import com.example.msp_app.core.navegacion.ui.components.SeccionPlegable

const val MENU_LATERAL_TAG: String = "menu_lateral"
const val FILA_DEL_MENU_TAG: String = "menu_lateral_fila_"
const val SECCION_DEL_MENU_TAG: String = "menu_lateral_seccion_"
const val BUSCADOR_DEL_MENU_TAG: String = "menu_lateral_buscador"
const val DESVANECIDO_DEL_MENU_TAG: String = "menu_lateral_desvanecido"
const val ENVIO_DEL_MENU_TAG: String = "menu_lateral_envio"
const val ID_CONFIGURACION: String = "configuracion"

/** 360 dp de teléfono menos 56 dp de velo para tocar fuera y cerrar. */
val ANCHO_DEL_MENU: Dp = 304.dp

/**
 * La hoja del menú, sin cajón ni datos: todo llega por parámetro. Se compone
 * DENTRO de `MspTheme` (lo pone `MenuLateralContenedor`).
 *
 * El cuerpo es un `Column` con scroll y no una lista perezosa a propósito: el
 * máximo son 10 opciones (spec §2.1.3) y no crece con los datos (CLAUDE.md §3).
 */
@Composable
fun MenuLateral(
    cabecera: CabeceraDelMenu,
    secciones: List<SeccionDelMenu>,
    activo: ActivoEnElMenu,
    seccionesPlegadas: Set<String>,
    apertura: AperturaDelMenu,
    onTogglePlegado: (seccionId: String, plegar: Boolean) -> Unit,
    onElegir: (ElementoDelMenu) -> Unit,
    pie: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val desplazamiento = rememberScrollState()
    val visibles = ReglasDelMenu.filtrar(secciones, apertura.consulta)
    Column(modifier = modifier.fillMaxHeight().background(MspTheme.colors.surface)) {
        EncabezadoDelMenu(cabecera)
        if (ReglasDelMenu.muestraBuscador(secciones)) {
            BuscadorDelMenu(consulta = apertura.consulta, onConsulta = { apertura.consulta = it })
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(desplazamiento)
                    .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.sm)
            ) {
                if (visibles.isEmpty() && apertura.consulta.isNotBlank()) {
                    Text(
                        text = "Sin resultados",
                        style = MspTheme.type.body,
                        color = MspTheme.colors.onSurfaceMuted,
                        modifier = Modifier.padding(MspTheme.spacing.md)
                    )
                }
                visibles.forEach { seccion ->
                    val abierta = ReglasDelMenu.estaAbierta(
                        seccionId = seccion.id,
                        plegadas = seccionesPlegadas,
                        activa = activo.seccionId,
                        tocadas = apertura.tocadas,
                        consulta = apertura.consulta
                    )
                    SeccionPlegable(
                        seccion = seccion,
                        abierta = abierta,
                        habilitada = apertura.consulta.isBlank(),
                        itemActivoId = activo.itemId,
                        onToggle = {
                            apertura.tocar(seccion.id)
                            onTogglePlegado(seccion.id, abierta)
                        },
                        onElegir = onElegir
                    )
                }
            }
            if (desplazamiento.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(MspTheme.spacing.lg + MspTheme.spacing.sm)
                        .background(
                            Brush.verticalGradient(
                                listOf(MspTheme.colors.surface.copy(alpha = 0f), MspTheme.colors.surface)
                            )
                        )
                        .testTag(DESVANECIDO_DEL_MENU_TAG)
                )
            }
        }
        HorizontalDivider(color = MspTheme.colors.outline)
        pie()
    }
}
```

- [ ] **Step 4: Correr y ver verde, y contar**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest :core:navegacion:detekt :core:navegacion:ktlintFormat :core:navegacion:ktlintCheck
grep -ho 'tests="[0-9]*"' core/navegacion/build/test-results/testDebugUnitTest/TEST-*MenuLateralTest.xml
```

Expected: BUILD SUCCESSFUL; `tests="18"`.

- [ ] **Step 5: Commit**

```bash
git add core/navegacion
git commit -m "feat(navegacion): hoja del menu lateral con secciones plegables, buscador y pie"
```

---

### Task 4: `MenuLateralContenedor` (cajón, atrás, reinicio, movimiento reducido)

**Files:**
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralContenedor.kt`
- Test: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralContenedorTest.kt`

**Interfaces:**
- Consumes: `AperturaDelMenu`, `rememberAperturaDelMenu()`, `MENU_LATERAL_TAG`, `ANCHO_DEL_MENU` (Task 3); `rememberMspReducedMotion()`, `MspTheme` de `:core:designsystem`; `ModalDrawerSheet(drawerState: DrawerState, …)` de material3 1.3.0 (decisión D10).
- Produces: `@Composable fun MenuLateralContenedor(hoja: @Composable (cerrar: () -> Unit, apertura: AperturaDelMenu) -> Unit, onAbierto: () -> Unit, contenido: @Composable (abrir: () -> Unit) -> Unit)` (la Task 16 le agrega `gesto: AperturaPorGesto` con default).

- [ ] **Step 1: Escribir la prueba que falla**

`core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralContenedorTest.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MenuLateralContenedorTest : RobolectricTestBase() {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var abiertos = 0
    private var aperturaVista: AperturaDelMenu? = null

    private fun montar(reducido: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reducido) {
                MenuLateralContenedor(
                    hoja = { cerrar, apertura ->
                        aperturaVista = apertura
                        Button(onClick = cerrar, modifier = Modifier.testTag("cerrar")) { Text("Cerrar") }
                    },
                    onAbierto = { abiertos += 1 }
                ) { abrir ->
                    Button(onClick = abrir, modifier = Modifier.testTag("abrir")) { Text("Abrir") }
                }
            }
        }
    }

    private fun abrir() {
        compose.onNodeWithTag("abrir").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `cerrado no se ve y abrir lo muestra y avisa una vez`() {
        montar()
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
        abrir()
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsDisplayed()
        assertEquals(1, abiertos)
    }

    @Test
    fun `atras con el menu abierto lo cierra y no deja la pantalla`() {
        montar()
        abrir()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
        assertFalse("atrás salió de la pantalla en vez de cerrar el menú", compose.activity.isFinishing)
    }

    @Test
    fun `cerrar el menu limpia la busqueda y las secciones tocadas`() {
        montar()
        abrir()
        compose.runOnUiThread {
            aperturaVista!!.consulta = "garantias"
            aperturaVista!!.tocar("COBRO")
        }

        compose.onNodeWithTag("cerrar").performClick()
        compose.waitForIdle()

        assertEquals("", aperturaVista!!.consulta)
        assertTrue(aperturaVista!!.tocadas.isEmpty())
    }

    @Test
    fun `con movimiento reducido abre sin animacion`() {
        montar(reducido = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("abrir").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        assertEquals(0f, compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot().left.value, 0.5f)
    }

    @Test
    fun `control positivo, sin movimiento reducido la entrada se anima`() {
        montar(reducido = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("abrir").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        assertTrue(compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot().left.value < 0f)
    }
}
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest --tests 'com.example.msp_app.core.navegacion.ui.MenuLateralContenedorTest'
```

Expected: FAIL de compilación: `Unresolved reference: MenuLateralContenedor`.

- [ ] **Step 3: Implementación mínima**

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralContenedor.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * El cajón alrededor de una pantalla. Lo que pone:
 *
 * - **MspTheme sólo dentro de la hoja**: la pantalla de `contenido` puede ser
 *   legada (MaterialTheme de `MspappTheme`) y no debe cambiar de colores.
 * - **Atrás**: `ModalDrawerSheet(drawerState, …)` de material3 1.3.0 registra el
 *   manejador de atrás (predictivo en Android 14+) que cierra el menú — medido
 *   que la sobrecarga existe (decisión D10 del plan).
 * - **Reinicio al cerrar**: [AperturaDelMenu.reiniciar] (Review Focus 3).
 * - **Movimiento reducido**: `snapTo`, sin animación (spec §2.1.7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuLateralContenedor(
    hoja: @Composable (cerrar: () -> Unit, apertura: AperturaDelMenu) -> Unit,
    onAbierto: () -> Unit,
    contenido: @Composable (abrir: () -> Unit) -> Unit
) {
    val estadoDelCajon = rememberDrawerState(DrawerValue.Closed)
    val alcance = rememberCoroutineScope()
    val apertura = rememberAperturaDelMenu()
    val movimientoReducido = rememberMspReducedMotion()
    val alAbrir by rememberUpdatedState(onAbierto)

    val abrir: () -> Unit = {
        alcance.launch {
            if (movimientoReducido) estadoDelCajon.snapTo(DrawerValue.Open) else estadoDelCajon.open()
        }
    }
    val cerrar: () -> Unit = {
        alcance.launch {
            if (movimientoReducido) estadoDelCajon.snapTo(DrawerValue.Closed) else estadoDelCajon.close()
        }
    }

    LaunchedEffect(estadoDelCajon) {
        snapshotFlow { estadoDelCajon.currentValue }
            .distinctUntilChanged()
            .collect { valor -> if (valor == DrawerValue.Open) alAbrir() else apertura.reiniciar() }
    }

    ModalNavigationDrawer(
        drawerState = estadoDelCajon,
        drawerContent = {
            MspTheme {
                ModalDrawerSheet(
                    drawerState = estadoDelCajon,
                    modifier = Modifier
                        .width(ANCHO_DEL_MENU)
                        .testTag(MENU_LATERAL_TAG),
                    drawerContainerColor = MspTheme.colors.surface,
                    drawerContentColor = MspTheme.colors.onSurface
                ) {
                    hoja(cerrar, apertura)
                }
            }
        }
    ) {
        contenido(abrir)
    }
}
```

- [ ] **Step 4: Correr y ver verde, y contar**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest :core:navegacion:detekt :core:navegacion:ktlintFormat :core:navegacion:ktlintCheck
grep -ho 'tests="[0-9]*"' core/navegacion/build/test-results/testDebugUnitTest/TEST-*MenuLateralContenedorTest.xml
```

Expected: BUILD SUCCESSFUL; `tests="5"`. Si `atras con el menu abierto…` sale rojo con la implementación puesta, el manejador de la sobrecarga no se registró en Robolectric: agregar dentro de `MenuLateralContenedor`, antes de `ModalNavigationDrawer`, `androidx.activity.compose.BackHandler(enabled = estadoDelCajon.isOpen) { cerrar() }` y volver a correr. Anotar en el reporte cuál de los dos caminos quedó.

- [ ] **Step 5: Commit**

```bash
git add core/navegacion
git commit -m "feat(navegacion): contenedor del menu lateral con atras, reinicio y movimiento reducido"
```

---

### Task 5: Goldens Roborazzi de la hoja

**Files:**
- Create: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/screenshot/MenuLateralScreenshotTest.kt`
- Create: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/screenshot/MenuLateralMatrixScreenshotTest.kt`
- Create: `core/navegacion/src/test/screenshots/menu_*.png` (12 imágenes, las graba Roborazzi)

**Interfaces:**
- Consumes: `MenuLateral`, `PieDelMenu`, `AperturaDelMenu`, `ANCHO_DEL_MENU` (Task 3); `MenuFixtures` (Task 1); `RoborazziConfig.CHANGE_THRESHOLD`; `FontSizeLevel`, `LocalFontSizeLevel`, `LocalReduceMotion`.
- Produces: 12 goldens `menu_{completo|esperando|sin_modulos}_{light|dark}_{1_0|1_5|2_0}.png`.

- [ ] **Step 1: Escribir las pruebas de captura**

`core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/screenshot/MenuLateralScreenshotTest.kt`:

```kotlin
package com.example.msp_app.core.navegacion.screenshot

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.ui.ANCHO_DEL_MENU
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.roborazzi.RoborazziConfig
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Misma receta que `ConfiguracionScreenshotTest`: escala por `LocalDensity`, tema explícito, sin animación. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", application = android.app.Application::class)
abstract class MenuLateralScreenshotTest : RobolectricTestBase() {

    @Before
    fun apagaAnimaciones() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    fun capture(name: String, dark: Boolean, nivel: FontSizeLevel, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = RoborazziConfig.CHANGE_THRESHOLD)
            )
        ) {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel,
                LocalReduceMotion provides true
            ) {
                MspTheme(darkTheme = dark, animateColors = false) {
                    Box(Modifier.width(ANCHO_DEL_MENU).fillMaxHeight()) { content() }
                }
            }
        }
    }

    fun sufijoDe(nivel: FontSizeLevel): String = nivel.nominalScale.toString().replace('.', '_')

    fun tema(dark: Boolean): String = if (dark) "dark" else "light"
}
```

`core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/screenshot/MenuLateralMatrixScreenshotTest.kt`:

```kotlin
package com.example.msp_app.core.navegacion.screenshot

import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.navegacion.MenuFixtures
import com.example.msp_app.core.navegacion.domain.model.ActivoEnElMenu
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import com.example.msp_app.core.navegacion.ui.AperturaDelMenu
import com.example.msp_app.core.navegacion.ui.MenuLateral
import com.example.msp_app.core.navegacion.ui.components.PieDelMenu
import org.junit.Test

/**
 * Qué mirar en cada imagen:
 * 1. Ninguna fila por debajo de 56 dp y ninguna etiqueta truncada a 2.0 (envuelve).
 * 2. "Clientes" resaltado en `brandTint`/`brand`; ningún otro.
 * 3. VENTAS plegada (completo) / ALMACÉN plegada (esperando) con su chevron a la derecha.
 * 4. Punto verde con "Todo enviado · hace 3 min"; punto ámbar con "2 pagos esperando señal" — nunca rojo.
 * 5. Buscador visible en "completo" y "esperando" (10 opciones), ausente en "sin módulos".
 * 6. Desvanecido abajo sólo si hay más contenido por desplazar.
 */
class MenuLateralMatrixScreenshotTest : MenuLateralScreenshotTest() {

    @Test fun `completo light normal`() = completo(false, FontSizeLevel.NORMAL)

    @Test fun `completo light grande`() = completo(false, FontSizeLevel.GRANDE)

    @Test fun `completo light muy grande`() = completo(false, FontSizeLevel.MUY_GRANDE)

    @Test fun `completo dark normal`() = completo(true, FontSizeLevel.NORMAL)

    @Test fun `completo dark grande`() = completo(true, FontSizeLevel.GRANDE)

    @Test fun `completo dark muy grande`() = completo(true, FontSizeLevel.MUY_GRANDE)

    @Test fun `esperando light normal`() = esperando(false, FontSizeLevel.NORMAL)

    @Test fun `esperando light muy grande`() = esperando(false, FontSizeLevel.MUY_GRANDE)

    @Test fun `esperando dark normal`() = esperando(true, FontSizeLevel.NORMAL)

    @Test fun `esperando dark muy grande`() = esperando(true, FontSizeLevel.MUY_GRANDE)

    @Test fun `sin modulos light normal`() = sinModulos(false)

    @Test fun `sin modulos dark normal`() = sinModulos(true)

    private fun completo(dark: Boolean, nivel: FontSizeLevel) =
        menu("completo", dark, nivel, MenuFixtures.todas, setOf("VENTAS"), MenuFixtures.cabecera())

    private fun esperando(dark: Boolean, nivel: FontSizeLevel) = menu(
        "esperando",
        dark,
        nivel,
        MenuFixtures.todas,
        setOf("ALMACEN"),
        MenuFixtures.cabecera(envio = MenuFixtures.DOS_PAGOS_ESPERANDO)
    )

    private fun sinModulos(dark: Boolean) =
        menu("sin_modulos", dark, FontSizeLevel.NORMAL, emptyList(), emptySet(), MenuFixtures.cabecera())

    private fun menu(
        caso: String,
        dark: Boolean,
        nivel: FontSizeLevel,
        secciones: List<SeccionDelMenu>,
        plegadas: Set<String>,
        cabecera: CabeceraDelMenu
    ) {
        capture("menu_${caso}_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            MenuLateral(
                cabecera = cabecera,
                secciones = secciones,
                activo = ActivoEnElMenu("COBRO", "clientes"),
                seccionesPlegadas = plegadas,
                apertura = AperturaDelMenu(),
                onTogglePlegado = { _, _ -> },
                onElegir = {},
                pie = { PieDelMenu(version = "Versión 2.19.3", onConfiguracion = {}) }
            )
        }
    }
}
```

- [ ] **Step 2: Correr la verificación y ver que falla (no hay goldens)**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
rm -rf core/navegacion/build/outputs/roborazzi
./gradlew --no-parallel --no-daemon :core:navegacion:verifyRoborazziDebug
```

Expected: FAIL — Roborazzi reporta que `src/test/screenshots/menu_completo_light_1_0.png` (y las otras 11) no existen.

- [ ] **Step 3: Grabar los goldens**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:recordRoborazziDebug
ls core/navegacion/src/test/screenshots/ | grep -c '^menu_.*\.png$'
```

Expected: BUILD SUCCESSFUL; el conteo imprime `12`.

- [ ] **Step 4: MIRAR las 12 imágenes**

Abrir cada PNG de `core/navegacion/src/test/screenshots/` con la herramienta de lectura de imágenes y recorrer la lista "Qué mirar" del KDoc de `MenuLateralMatrixScreenshotTest`. Si algo falla (texto truncado, fila baja, color equivocado), corregir la IMPLEMENTACIÓN, volver al Step 3 y mirar otra vez. Anotar en el reporte, por imagen, "visto: OK" o el defecto encontrado y su arreglo.

- [ ] **Step 5: Verificar y comprobar que ningún golden ajeno se movió**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
rm -rf core/navegacion/build/outputs/roborazzi
./gradlew --no-parallel --no-daemon :core:navegacion:verifyRoborazziDebug
git status --porcelain | grep '\.png$' | grep -v '^?? core/navegacion/src/test/screenshots/'
```

Expected: BUILD SUCCESSFUL; el último comando no imprime nada.

- [ ] **Step 6: Commit**

```bash
git add core/navegacion
git commit -m "test(navegacion): goldens del menu lateral en claro y oscuro a tres escalas"
```

---

### Task 6: Consultas de conteo en Room

**Files:**
- Modify: `core/database/src/main/kotlin/com/example/msp_app/core/database/dao/payment/PaymentDao.kt` (después de `getPendingPayments`, ~línea 321)
- Modify: `core/database/src/main/kotlin/com/example/msp_app/core/database/dao/localsale/LocalSaleDao.kt` (después de `insertSale`, ~línea 16; agregar `import kotlinx.coroutines.flow.Flow`)
- Modify: `core/database/src/main/kotlin/com/example/msp_app/core/database/dao/cobranzasync/CobranzaSyncStateDao.kt` (después de `get`; agregar `import kotlinx.coroutines.flow.Flow`)
- Test: `core/database/src/test/kotlin/com/example/msp_app/core/database/dao/ConteosDelMenuLateralDaoTest.kt`

**Interfaces:**
- Consumes: entidades existentes `PaymentEntity`, `LocalSaleEntity`, `CobranzaSyncStateEntity` (sin cambios).
- Produces:
  - `PaymentDao.observarPendientesDeSubir(): Flow<Int>`
  - `LocalSaleDao.observarSinSubir(): Flow<Int>`
  - `CobranzaSyncStateDao.observarRecursos(recursos: List<String>): Flow<List<CobranzaSyncStateEntity>>`

- [ ] **Step 1: Escribir la prueba que falla**

`core/database/src/test/kotlin/com/example/msp_app/core/database/dao/ConteosDelMenuLateralDaoTest.kt`:

```kotlin
package com.example.msp_app.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Las tres lecturas que alimentan la tarjeta de envío del menú lateral (spec §4). */
class ConteosDelMenuLateralDaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun pago(id: String, guardado: Boolean) = PaymentEntity(
        ID = id,
        COBRADOR = "Efraín Domínguez Reyes",
        DOCTO_CC_ACR_ID = 48213,
        DOCTO_CC_ID = if (guardado) 91027 else 0,
        FECHA_HORA_PAGO = "2026-10-06T16:45:00Z",
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = 350.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Araceli Jiménez Cortés"
    )

    private fun venta(id: String, enviado: Boolean, permanente: Boolean? = null) = LocalSaleEntity(
        LOCAL_SALE_ID = id,
        NOMBRE_CLIENTE = "Rosa Elena Martínez Vázquez",
        FECHA_VENTA = "2026-10-06T15:30:00Z",
        LATITUD = 19.043415,
        LONGITUD = -98.198234,
        DIRECCION = "Privada de las Rosas 45",
        PARCIALIDAD = 850.0,
        ENGANCHE = 500.0,
        TELEFONO = "2221234567",
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = null,
        NOTA = null,
        DIA_COBRANZA = "MARTES",
        PRECIO_TOTAL = 6800.0,
        TIEMPO_A_CORTO_PLAZOMESES = 8,
        MONTO_A_CORTO_PLAZO = 6300.0,
        MONTO_DE_CONTADO = 5800.0,
        ENVIADO = enviado,
        LAST_UPLOAD_PERMANENT = permanente
    )

    private fun estado(recurso: String) = CobranzaSyncStateEntity(
        RESOURCE = recurso,
        ZONA_CLIENTE_ID = 21,
        CURSOR = "2026-10-06T16:00:00Z",
        LAST_SYNCED_AT = "2026-10-06T16:00:00Z",
        LAST_ERROR = null
    )

    @Test
    fun `cuenta solo los pagos sin guardar y se actualiza al insertar`() = runTest {
        val dao = database.paymentDao()
        dao.saveAll(listOf(pago("a", guardado = false), pago("b", guardado = true)))

        dao.observarPendientesDeSubir().test {
            assertEquals(1, awaitItem())
            dao.saveAll(listOf(pago("c", guardado = false)))
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cuenta ventas sin subir y excluye las que el servidor ya resguardo`() = runTest {
        val dao = database.localSaleDao()
        dao.insertSale(venta("v1", enviado = false))
        dao.insertSale(venta("v2", enviado = false, permanente = false))
        dao.insertSale(venta("v3", enviado = false, permanente = true))
        dao.insertSale(venta("v4", enviado = true))

        assertEquals(2, dao.observarSinSubir().first())
    }

    @Test
    fun `observa solo los recursos pedidos`() = runTest {
        val dao = database.cobranzaSyncStateDao()
        dao.upsert(estado("ventas"))
        dao.upsert(estado("pagos"))
        dao.upsert(estado("migration_purge_legacy_pago_ids"))

        val vistos = dao.observarRecursos(listOf("ventas", "pagos")).first()

        assertEquals(setOf("ventas", "pagos"), vistos.map { it.RESOURCE }.toSet())
    }
}
```

Antes de correr, confirmar los nombres de los DAOs en `AppDatabase`:

```bash
grep -n "fun paymentDao\|fun localSaleDao\|fun cobranzaSyncStateDao" core/database/src/main/kotlin/com/example/msp_app/core/database/AppDatabase.kt
```

Expected: tres líneas. Si algún nombre difiere, usar el que imprime este comando en la prueba.

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:database:testDebugUnitTest --tests 'com.example.msp_app.core.database.dao.ConteosDelMenuLateralDaoTest'
```

Expected: FAIL de compilación: `Unresolved reference: observarPendientesDeSubir`, `observarSinSubir`, `observarRecursos`.

- [ ] **Step 3: Implementación mínima**

En `PaymentDao.kt`, después de `suspend fun getPendingPayments(): List<PaymentEntity>`:

```kotlin
    /**
     * Cuántas capturas esperan subir — el MISMO filtro que [getPendingPayments]
     * (`GUARDADO_EN_MICROSIP = 0`). La observa la tarjeta de envío del menú
     * lateral (plan 2026-10-06-menu-lateral, Task 6).
     */
    @Query("SELECT COUNT(*) FROM Payment WHERE GUARDADO_EN_MICROSIP = 0")
    fun observarPendientesDeSubir(): Flow<Int>
```

En `LocalSaleDao.kt`, agregar `import kotlinx.coroutines.flow.Flow` y después de `insertSale`:

```kotlin
    /**
     * Ventas que todavía esperan subir. Excluye `LAST_UPLOAD_PERMANENT = 1`: con
     * fallo permanente el servidor ya resguardó el intento (`LaRevisaLaOficina`,
     * ver el KDoc de la consulta de corrección de nivel 2 más abajo), así que no
     * está "esperando señal". La observan la tarjeta de envío y el contador
     * "N sin subir" del menú lateral.
     */
    @Query(
        """
        SELECT COUNT(*) FROM local_sale
        WHERE ENVIADO = 0
          AND (LAST_UPLOAD_PERMANENT IS NULL OR LAST_UPLOAD_PERMANENT = 0)
        """
    )
    fun observarSinSubir(): Flow<Int>
```

En `CobranzaSyncStateDao.kt`, agregar `import kotlinx.coroutines.flow.Flow` y después de `get`:

```kotlin
    /**
     * Las filas de estado de [recursos] (`ventas`, `pagos`), observadas. El menú
     * lateral saca de aquí el "hace N min" — ojo: [recordError] también escribe
     * `LAST_SYNCED_AT`, así que quien lea esto debe mirar `LAST_ERROR`.
     */
    @Query("SELECT * FROM cobranza_sync_state WHERE RESOURCE IN (:recursos)")
    fun observarRecursos(recursos: List<String>): Flow<List<CobranzaSyncStateEntity>>
```

- [ ] **Step 4: Correr y ver verde, y contar**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:database:testDebugUnitTest --tests 'com.example.msp_app.core.database.dao.ConteosDelMenuLateralDaoTest' :core:database:detekt :core:database:ktlintFormat :core:database:ktlintCheck
grep -ho 'tests="[0-9]*"' core/database/build/test-results/testDebugUnitTest/TEST-*ConteosDelMenuLateralDaoTest.xml
git diff --stat core/database/schemas
```

Expected: BUILD SUCCESSFUL; `tests="3"`; el `git diff --stat` de `schemas` no imprime nada (el schema no cambió).

- [ ] **Step 5: Commit**

```bash
git add core/database
git commit -m "feat(database): conteos observables de pagos y ventas sin subir para el menu lateral"
```

---

### Task 7: Adaptador del estado de envío en `:app`

**Files:**
- Modify: `app/build.gradle.kts` (después de `implementation(project(":core:designsystem"))`, ~línea 248)
- Create: `app/src/main/java/com/example/msp_app/data/menu/EstadoDeEnvioAdapter.kt`
- Test: `app/src/test/java/com/example/msp_app/data/menu/EstadoDeEnvioAdapterTest.kt`

**Interfaces:**
- Consumes: `EstadoDeEnvioPort`, `EstadoDeEnvio` (Tasks 1–2); DAOs de la Task 6; `AppClock` (`fun now(): Instant`); `CobranzaSyncManager.RESOURCE_VENTAS`/`RESOURCE_PAGOS`; `RoomTestBase` (`protected lateinit var db: AppDatabase`), `FakeClock`.
- Produces:
  - `class EstadoDeEnvioAdapter(paymentDao: PaymentDao, localSaleDao: LocalSaleDao, syncStateDao: CobranzaSyncStateDao, clock: AppClock, pulso: Flow<Unit> = pulsoCadaMinuto()) : EstadoDeEnvioPort` con `companion object { val RECURSOS: List<String> }`
  - `internal fun minutosDesdeElUltimoContacto(estados: List<CobranzaSyncStateEntity>, ahora: Instant): Long?`
  - `internal fun pulsoCadaMinuto(): Flow<Unit>`

- [ ] **Step 1: Agregar la dependencia del módulo**

En `app/build.gradle.kts`, después de `implementation(project(":core:designsystem"))`:

```kotlin
    // Menú lateral nuevo (plan 2026-10-06-menu-lateral): puertos, hoja y contenedor.
    // Los adaptadores viven aquí porque leen Room, DataStore y `ReunirCartera`.
    implementation(project(":core:navegacion"))
```

- [ ] **Step 2: Escribir la prueba que falla**

`app/src/test/java/com/example/msp_app/data/menu/EstadoDeEnvioAdapterTest.kt`:

```kotlin
package com.example.msp_app.data.menu

import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstadoDeEnvioAdapterTest : RoomTestBase() {

    private val ahora = Instant.parse("2026-10-06T16:10:00Z")

    private fun estado(recurso: String, sincronizado: String = "2026-10-06T16:07:00Z", error: String? = null) =
        CobranzaSyncStateEntity(
            RESOURCE = recurso,
            ZONA_CLIENTE_ID = 21,
            CURSOR = null,
            LAST_SYNCED_AT = sincronizado,
            LAST_ERROR = error
        )

    private fun pagoPendiente(id: String) = PaymentEntity(
        ID = id,
        COBRADOR = "Efraín Domínguez Reyes",
        DOCTO_CC_ACR_ID = 48213,
        DOCTO_CC_ID = 0,
        FECHA_HORA_PAGO = "2026-10-06T16:00:00Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 200.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Guadalupe Hernández Soto"
    )

    @Test
    fun `los dos recursos sanos dan los minutos desde el mas viejo`() {
        val estados = listOf(estado("ventas", "2026-10-06T16:07:00Z"), estado("pagos", "2026-10-06T16:05:00Z"))
        assertEquals(5L, minutosDesdeElUltimoContacto(estados, ahora))
    }

    @Test
    fun `si el ultimo intento fallo no hay tiempo aunque LAST_SYNCED_AT sea reciente`() {
        val estados = listOf(estado("ventas"), estado("pagos", error = "timeout"))
        assertNull(minutosDesdeElUltimoContacto(estados, ahora))
    }

    @Test
    fun `una marca ilegible no da tiempo`() {
        assertNull(minutosDesdeElUltimoContacto(listOf(estado("ventas"), estado("pagos", "ayer")), ahora))
    }

    @Test
    fun `una marca en el futuro no da minutos negativos`() {
        val estados = listOf(estado("ventas", "2026-10-06T16:30:00Z"), estado("pagos", "2026-10-06T16:30:00Z"))
        assertNull(minutosDesdeElUltimoContacto(estados, ahora))
    }

    @Test
    fun `sin uno de los dos recursos no hay tiempo`() {
        assertNull(minutosDesdeElUltimoContacto(listOf(estado("ventas")), ahora))
    }

    @Test
    fun `el adaptador junta pendientes y tiempo desde Room`() = runTest {
        db.paymentDao().saveAll(listOf(pagoPendiente("p1"), pagoPendiente("p2")))
        db.cobranzaSyncStateDao().upsert(estado("ventas"))
        db.cobranzaSyncStateDao().upsert(estado("pagos"))
        val adaptador = EstadoDeEnvioAdapter(
            paymentDao = db.paymentDao(),
            localSaleDao = db.localSaleDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            clock = FakeClock(ahora),
            pulso = flowOf(Unit)
        )

        assertEquals(
            EstadoDeEnvio(pagosPendientes = 2, ventasPendientes = 0, ultimoEnvioHaceMinutos = 3),
            adaptador.estado().first()
        )
    }
}
```

- [ ] **Step 3: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.data.menu.EstadoDeEnvioAdapterTest'
```

Expected: FAIL de compilación: `Unresolved reference: minutosDesdeElUltimoContacto`, `EstadoDeEnvioAdapter`.

- [ ] **Step 4: Implementación mínima**

`app/src/main/java/com/example/msp_app/data/menu/EstadoDeEnvioAdapter.kt`:

```kotlin
package com.example.msp_app.data.menu

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.dao.cobranzasync.CobranzaSyncStateDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.domain.port.EstadoDeEnvioPort
import com.example.msp_app.core.sync.cobranza.CobranzaSyncManager
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * La tarjeta de envío del menú lateral (spec §4): pendientes de pagos y de
 * ventas + hace cuánto hubo contacto SANO con el servidor. [pulso] re-evalúa los
 * minutos cada 60 s aunque Room no cambie.
 */
class EstadoDeEnvioAdapter(
    private val paymentDao: PaymentDao,
    private val localSaleDao: LocalSaleDao,
    private val syncStateDao: CobranzaSyncStateDao,
    private val clock: AppClock,
    private val pulso: Flow<Unit> = pulsoCadaMinuto()
) : EstadoDeEnvioPort {

    override fun estado(): Flow<EstadoDeEnvio> = combine(
        paymentDao.observarPendientesDeSubir(),
        localSaleDao.observarSinSubir(),
        syncStateDao.observarRecursos(RECURSOS),
        pulso
    ) { pagos, ventas, estados, _ ->
        EstadoDeEnvio(
            pagosPendientes = pagos,
            ventasPendientes = ventas,
            ultimoEnvioHaceMinutos = minutosDesdeElUltimoContacto(estados, clock.now())
        )
    }

    companion object {
        val RECURSOS: List<String> = listOf(CobranzaSyncManager.RESOURCE_VENTAS, CobranzaSyncManager.RESOURCE_PAGOS)
    }
}

private const val UN_MINUTO_MS = 60_000L

internal fun pulsoCadaMinuto(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(UN_MINUTO_MS)
    }
}

/**
 * Minutos desde el contacto sano MÁS VIEJO de los dos recursos, o `null` cuando
 * no hay un dato honesto. `recordError` también escribe `LAST_SYNCED_AT`
 * (`CobranzaSyncManager.kt:286-295`), por eso una fila con `LAST_ERROR` anula el
 * tiempo (decisión D7 del plan).
 */
internal fun minutosDesdeElUltimoContacto(estados: List<CobranzaSyncStateEntity>, ahora: Instant): Long? {
    if (estados.map { it.RESOURCE }.toSet() != EstadoDeEnvioAdapter.RECURSOS.toSet()) return null
    if (estados.any { it.LAST_ERROR != null }) return null
    val instantes = estados.map { estado ->
        try {
            Instant.parse(estado.LAST_SYNCED_AT)
        } catch (ilegible: DateTimeParseException) {
            return null
        }
    }
    val minutos = Duration.between(instantes.min(), ahora).toMinutes()
    return minutos.takeIf { it >= 0 }
}
```

- [ ] **Step 5: Correr y ver verde, y contar**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.data.menu.EstadoDeEnvioAdapterTest' :app:ktlintFormat :app:ktlintCheck
grep -ho 'tests="[0-9]*"' app/build/test-results/testDevlocalDebugUnitTest/TEST-*EstadoDeEnvioAdapterTest.xml
```

Expected: BUILD SUCCESSFUL; `tests="6"`.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/example/msp_app/data/menu app/src/test/java/com/example/msp_app/data/menu
git commit -m "feat(menu): adaptador del estado de envio con pendientes y ultimo contacto sano"
```

---

### Task 8: Contadores, secciones plegadas y cableado Hilt

**Files:**
- Modify: `feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/ui/CarteraEnPantalla.kt` (dentro de `object CarteraEnPantalla`, después de `proyectar`)
- Modify: `feature/pagos/src/test/kotlin/com/example/msp_app/feature/pagos/ui/CarteraEnPantallaTest.kt` (dos pruebas al final)
- Create: `app/src/main/java/com/example/msp_app/navigation/menu/IdsDelMenu.kt`
- Create: `app/src/main/java/com/example/msp_app/data/menu/ContadoresDelMenuAdapter.kt`
- Create: `app/src/main/java/com/example/msp_app/data/menu/DataStoreSeccionesPlegadasAdapter.kt`
- Create: `app/src/main/java/com/example/msp_app/di/MenuLateralModule.kt`
- Test: `app/src/test/java/com/example/msp_app/data/menu/ContadoresDelMenuAdapterTest.kt`
- Test: `app/src/test/java/com/example/msp_app/data/menu/DataStoreSeccionesPlegadasAdapterTest.kt`

**Interfaces:**
- Consumes: puertos (Task 2); `EstadoDeEnvioAdapter` (Task 7); `ReunirCartera` (`suspend operator fun invoke(): Cartera`, `Cartera(clientes: List<ClienteEnLista>, hoy: LocalDate)`); `CarteraEnPantalla.proyectar`; `SegmentoDeCobranza.VOLVER_A_VISITAR`; `Telemetry`; `RecordingTelemetry`, `FakeClock`.
- Produces:
  - `CarteraEnPantalla.clientesPorVolver(clientes: List<ClienteEnLista>, hoy: LocalDate): Int`
  - `object IdsDelMenu { const val INICIO = "inicio"; CLIENTES = "clientes"; REPORTE = "reporte_cobranza"; MAPA = "mapa_rutas"; VENTAS = "ventas"; MI_CAMIONETA = "mi_camioneta"; CATALOGO = "catalogo"; TRASPASOS = "traspasos"; ASIGNACION = "asignacion"; GARANTIAS = "garantias" }`
  - `class ContadoresDelMenuAdapter(clientesPorVolver: suspend () -> Int, ventasSinSubir: suspend () -> Int, telemetry: Telemetry) : ContadoresDelMenuPort` con `companion object { const val CODE_CONTADOR_FALLO = "menu_contador_fallo" }`
  - `class DataStoreSeccionesPlegadasAdapter(dataStore: DataStore<Preferences>) : SeccionesPlegadasPort`
  - `@Qualifier annotation class MenuLateralDataStore`; `object MenuLateralModule` (bindings de los tres puertos)

- [ ] **Step 1: Escribir las pruebas que fallan**

Al final de `CarteraEnPantallaTest` (antes de la llave de cierre de la clase):

```kotlin
    /**
     * El contador "N por volver" del menú lateral es EL MISMO número que el chip
     * "Volver" de la lista con la búsqueda vacía — si alguna vez divergen, el
     * cobrador ve dos verdades.
     */
    @Test
    fun `clientes por volver es el conteo del chip volver sin busqueda`() {
        val clientes = ListaFixtures.rutaConPromesaDeHoy()
        val delChip = proyectar(clientes = clientes, segmento = SegmentoDeCobranza.VOLVER_A_VISITAR)
            .conteos[SegmentoDeCobranza.VOLVER_A_VISITAR]

        assertEquals(delChip, CarteraEnPantalla.clientesPorVolver(clientes, hoyDePrueba))
    }

    /** Control positivo: la promesa de hoy de Esperanza cae en VOLVER, así que el número no es un cero vacío. */
    @Test
    fun `control positivo, la ruta con promesa de hoy tiene alguien por volver`() {
        assertTrue(CarteraEnPantalla.clientesPorVolver(ListaFixtures.rutaConPromesaDeHoy(), hoyDePrueba) > 0)
    }
```

`app/src/test/java/com/example/msp_app/data/menu/ContadoresDelMenuAdapterTest.kt`:

```kotlin
package com.example.msp_app.data.menu

import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.navigation.menu.IdsDelMenu
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ContadoresDelMenuAdapterTest {

    private val telemetria = RecordingTelemetry(FakeClock())
    private var consultasDeCartera = 0

    private fun adaptador(porVolver: Int = 8, sinSubir: Int = 1, fallaCartera: Boolean = false) =
        ContadoresDelMenuAdapter(
            clientesPorVolver = {
                consultasDeCartera += 1
                if (fallaCartera) error("cartera ilegible") else porVolver
            },
            ventasSinSubir = { sinSubir },
            telemetry = telemetria
        )

    @Test
    fun `formatea los dos contadores con fuente`() = runTest {
        assertEquals(
            mapOf(IdsDelMenu.CLIENTES to "8 por volver", IdsDelMenu.VENTAS to "1 sin subir"),
            adaptador().contadores(setOf(IdsDelMenu.CLIENTES, IdsDelMenu.VENTAS, IdsDelMenu.TRASPASOS))
        )
    }

    @Test
    fun `un contador en cero no se pinta`() = runTest {
        assertEquals(emptyMap<String, String>(), adaptador(porVolver = 0, sinSubir = 0).contadores(setOf(IdsDelMenu.CLIENTES, IdsDelMenu.VENTAS)))
    }

    @Test
    fun `sin la opcion visible no se calcula su contador`() = runTest {
        adaptador().contadores(setOf(IdsDelMenu.VENTAS))
        assertEquals(0, consultasDeCartera)
    }

    @Test
    fun `si una fuente falla las demas siguen y se reporta sin datos personales`() = runTest {
        val resultado = adaptador(fallaCartera = true).contadores(setOf(IdsDelMenu.CLIENTES, IdsDelMenu.VENTAS))

        assertEquals(mapOf(IdsDelMenu.VENTAS to "1 sin subir"), resultado)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR && it.name == ContadoresDelMenuAdapter.CODE_CONTADOR_FALLO
        }
        assertEquals(mapOf("contador" to IdsDelMenu.CLIENTES, "excepcion" to "IllegalStateException"), error.props)
    }

    @Test
    fun `traspasos nunca lleva contador`() = runTest {
        assertEquals(emptyMap<String, String>(), adaptador().contadores(setOf(IdsDelMenu.TRASPASOS, IdsDelMenu.REPORTE)))
    }
}
```

`app/src/test/java/com/example/msp_app/data/menu/DataStoreSeccionesPlegadasAdapterTest.kt`:

```kotlin
package com.example.msp_app.data.menu

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSeccionesPlegadasAdapterTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private fun adaptador() = DataStoreSeccionesPlegadasAdapter(
        PreferenceDataStoreFactory.create(
            produceFile = { File(carpeta.newFolder(), "test_menu_lateral.preferences_pb") }
        )
    )

    @Test
    fun `sin nada guardado no hay secciones plegadas`() = runTest {
        assertEquals(emptySet<String>(), adaptador().plegadas().first())
    }

    @Test
    fun `plegar y desplegar hacen ida y vuelta sin tocar las demas`() = runTest {
        val adaptador = adaptador()
        adaptador.setPlegada("VENTAS", plegada = true)
        adaptador.setPlegada("ALMACEN", plegada = true)
        adaptador.setPlegada("VENTAS", plegada = false)

        assertEquals(setOf("ALMACEN"), adaptador.plegadas().first())
    }
}
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :feature:pagos:testDebugUnitTest --tests 'com.example.msp_app.feature.pagos.ui.CarteraEnPantallaTest'
```

Expected: FAIL de compilación: `Unresolved reference: clientesPorVolver`.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.data.menu.*'
```

Expected: FAIL de compilación: `Unresolved reference: ContadoresDelMenuAdapter`, `IdsDelMenu`, `DataStoreSeccionesPlegadasAdapter`.

- [ ] **Step 3: Implementación mínima**

En `CarteraEnPantalla.kt`, dentro de `object CarteraEnPantalla`, después de `proyectar`:

```kotlin
    /**
     * Cuántos clientes caen en el chip "Volver" con la búsqueda vacía: el
     * contador "N por volver" del menú lateral (spec §4). Es [proyectar] mismo,
     * no una copia de la regla — así el menú y la lista no pueden diferir.
     */
    fun clientesPorVolver(clientes: List<ClienteEnLista>, hoy: LocalDate): Int =
        proyectar(clientes, SegmentoDeCobranza.VOLVER_A_VISITAR, query = "", hoy = hoy)
            .conteos[SegmentoDeCobranza.VOLVER_A_VISITAR] ?: 0
```

`app/src/main/java/com/example/msp_app/navigation/menu/IdsDelMenu.kt`:

```kotlin
package com.example.msp_app.navigation.menu

/** Ids estables de las opciones del menú: los usan el catálogo, los contadores y las pruebas. */
object IdsDelMenu {
    const val INICIO = "inicio"
    const val CLIENTES = "clientes"
    const val REPORTE = "reporte_cobranza"
    const val MAPA = "mapa_rutas"
    const val VENTAS = "ventas"
    const val MI_CAMIONETA = "mi_camioneta"
    const val CATALOGO = "catalogo"
    const val TRASPASOS = "traspasos"
    const val ASIGNACION = "asignacion"
    const val GARANTIAS = "garantias"
}
```

`app/src/main/java/com/example/msp_app/data/menu/ContadoresDelMenuAdapter.kt`:

```kotlin
package com.example.msp_app.data.menu

import com.example.msp_app.core.navegacion.domain.port.ContadoresDelMenuPort
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.navigation.menu.IdsDelMenu
import kotlinx.coroutines.CancellationException

/**
 * Los contadores con fuente verificada (spec §4): Clientes "N por volver" y
 * Ventas "N sin subir". Reporte y Traspasos no tienen fuente en este plan y no
 * aparecen. Cada fuente falla sola: si la cartera no se puede leer, Ventas
 * conserva su número.
 */
class ContadoresDelMenuAdapter(
    private val clientesPorVolver: suspend () -> Int,
    private val ventasSinSubir: suspend () -> Int,
    private val telemetry: Telemetry
) : ContadoresDelMenuPort {

    override suspend fun contadores(ids: Set<String>): Map<String, String> = buildMap {
        if (IdsDelMenu.CLIENTES in ids) {
            contar(IdsDelMenu.CLIENTES, clientesPorVolver)?.let { put(IdsDelMenu.CLIENTES, "$it por volver") }
        }
        if (IdsDelMenu.VENTAS in ids) {
            contar(IdsDelMenu.VENTAS, ventasSinSubir)?.let { put(IdsDelMenu.VENTAS, "$it sin subir") }
        }
    }

    @Suppress("TooGenericExceptionCaught") // una fuente rota deja SU opción sin contador, nunca las demás
    private suspend fun contar(id: String, fuente: suspend () -> Int): Int? = try {
        fuente().takeIf { it > 0 }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (falla: Exception) {
        telemetry.error(
            code = CODE_CONTADOR_FALLO,
            message = "el menu lateral no pudo contar una opcion",
            props = mapOf("contador" to id, "excepcion" to falla::class.java.simpleName)
        )
        null
    }

    companion object {
        const val CODE_CONTADOR_FALLO = "menu_contador_fallo"
    }
}
```

`app/src/main/java/com/example/msp_app/data/menu/DataStoreSeccionesPlegadasAdapter.kt`:

```kotlin
package com.example.msp_app.data.menu

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.msp_app.core.navegacion.domain.port.SeccionesPlegadasPort
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal object MenuLateralKeys {
    val PLEGADAS = stringSetPreferencesKey("menu_secciones_plegadas")
}

/**
 * Qué secciones dejó plegadas el usuario, en el archivo propio `menu_lateral`
 * (decisión D3 del plan, mismo molde que `DataStoreOrdenGuardadoAdapter`). Un
 * archivo ilegible degrada a "nada plegado", nunca lanza.
 */
class DataStoreSeccionesPlegadasAdapter(
    private val dataStore: DataStore<Preferences>
) : SeccionesPlegadasPort {

    override fun plegadas(): Flow<Set<String>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[MenuLateralKeys.PLEGADAS] ?: emptySet() }

    override suspend fun setPlegada(seccionId: String, plegada: Boolean) {
        dataStore.edit { prefs ->
            val actuales = prefs[MenuLateralKeys.PLEGADAS] ?: emptySet()
            prefs[MenuLateralKeys.PLEGADAS] = if (plegada) actuales + seccionId else actuales - seccionId
        }
    }
}
```

`app/src/main/java/com/example/msp_app/di/MenuLateralModule.kt`:

```kotlin
package com.example.msp_app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.dao.cobranzasync.CobranzaSyncStateDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.navegacion.domain.port.ContadoresDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.EstadoDeEnvioPort
import com.example.msp_app.core.navegacion.domain.port.SeccionesPlegadasPort
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.menu.ContadoresDelMenuAdapter
import com.example.msp_app.data.menu.DataStoreSeccionesPlegadasAdapter
import com.example.msp_app.data.menu.EstadoDeEnvioAdapter
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.ui.CarteraEnPantalla
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** DataStore propio del menú lateral — calificado por la misma razón que `OrdenListaClientesDataStore`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MenuLateralDataStore

/**
 * Los puertos de `:core:navegacion` cuyas fuentes viven en `:app` (principio 17).
 * SIN `@Singleton` en los puertos: no sostienen estado propio; el DataStore sí
 * es `@Singleton` (DataStore revienta con dos instancias sobre el mismo archivo).
 */
@Module
@InstallIn(SingletonComponent::class)
object MenuLateralModule {

    @Provides
    @Singleton
    @MenuLateralDataStore
    fun provideMenuLateralDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("menu_lateral") })

    @Provides
    fun provideSeccionesPlegadasPort(
        @MenuLateralDataStore dataStore: DataStore<Preferences>
    ): SeccionesPlegadasPort = DataStoreSeccionesPlegadasAdapter(dataStore)

    @Provides
    fun provideEstadoDeEnvioPort(
        paymentDao: PaymentDao,
        localSaleDao: LocalSaleDao,
        syncStateDao: CobranzaSyncStateDao,
        clock: AppClock
    ): EstadoDeEnvioPort = EstadoDeEnvioAdapter(paymentDao, localSaleDao, syncStateDao, clock)

    @Provides
    fun provideContadoresDelMenuPort(
        reunirCartera: ReunirCartera,
        localSaleDao: LocalSaleDao,
        telemetry: Telemetry
    ): ContadoresDelMenuPort = ContadoresDelMenuAdapter(
        clientesPorVolver = {
            val cartera = reunirCartera()
            CarteraEnPantalla.clientesPorVolver(cartera.clientes, cartera.hoy)
        },
        ventasSinSubir = { localSaleDao.observarSinSubir().first() },
        telemetry = telemetry
    )
}
```

- [ ] **Step 4: Correr y ver verde (incluye la validación del grafo Hilt de `:app`), y contar**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :feature:pagos:testDebugUnitTest --tests 'com.example.msp_app.feature.pagos.ui.CarteraEnPantallaTest' :feature:pagos:detekt :feature:pagos:ktlintFormat :feature:pagos:ktlintCheck
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.data.menu.*' :app:assembleDevlocalDebug :app:ktlintFormat :app:ktlintCheck
grep -ho 'tests="[0-9]*"' app/build/test-results/testDevlocalDebugUnitTest/TEST-*ContadoresDelMenuAdapterTest.xml app/build/test-results/testDevlocalDebugUnitTest/TEST-*DataStoreSeccionesPlegadasAdapterTest.xml
```

Expected: BUILD SUCCESSFUL en los dos; `tests="5"` y `tests="2"`. `assembleDevlocalDebug` verde prueba que Hilt resolvió los cuatro parámetros de `MenuLateralViewModel` (un binding faltante rompe `hiltJavaCompileDevlocalDebug`). Ningún golden de `:feature:pagos` cambia (`git status --porcelain feature/pagos/src/test/screenshots` vacío).

- [ ] **Step 5: Commit**

```bash
git add feature/pagos app/src/main/java/com/example/msp_app/navigation/menu app/src/main/java/com/example/msp_app/data/menu app/src/main/java/com/example/msp_app/di/MenuLateralModule.kt app/src/test/java/com/example/msp_app/data/menu
git commit -m "feat(menu): contadores con fuente real, secciones plegadas en DataStore y cableado Hilt"
```

---

### Task 9: "Cerrar sesión" pasa a Configuración

**Files:**
- Create: `feature/configuracion/src/main/kotlin/com/example/msp_app/feature/configuracion/ui/components/SesionSection.kt`
- Modify: `feature/configuracion/src/main/kotlin/com/example/msp_app/feature/configuracion/ui/ConfiguracionScreen.kt` (firmas de `ConfiguracionScreen` y `ConfiguracionContent`; la sección al final del `Column` con scroll)
- Modify: `app/src/main/java/com/example/msp_app/navigation/AppNavigation.kt:722-724`
- Test: `feature/configuracion/src/test/kotlin/com/example/msp_app/feature/configuracion/ui/ConfiguracionScreenTest.kt`

**Interfaces:**
- Consumes: el diálogo de `DrawerContainer.kt:378-397` (textos tal cual: título "Cerrar sesión", cuerpo "¿Estás seguro de que deseas cerrar sesión?", "Sí", "No"); `authViewModel.logout()` (`AppNavigation.kt:251`).
- Produces:
  - `@Composable fun SesionSection(onCerrarSesion: () -> Unit, modifier: Modifier = Modifier)` y `const val CERRAR_SESION_TAG = "configuracion_cerrar_sesion"`
  - `ConfiguracionScreen(navController: NavController, onCerrarSesion: (() -> Unit)? = null, viewModel: ConfiguracionViewModel = hiltViewModel())`
  - `ConfiguracionContent(…, onAbrirDescarga: (DescargaOpcional) -> Unit, onCerrarSesion: (() -> Unit)? = null)`

- [ ] **Step 1: Escribir la prueba que falla**

Al final de `ConfiguracionScreenTest` (antes de la llave de cierre):

```kotlin
    private fun setSesion(onCerrarSesion: (() -> Unit)?) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                ConfiguracionContent(
                    state = ConfiguracionUiState(),
                    onBack = {},
                    onSelectFontSize = {},
                    onSelectThemeMode = {},
                    onPrivacyMaskedChanged = {},
                    onReduceMotionChanged = {},
                    onAbrirDescarga = {},
                    onCerrarSesion = onCerrarSesion
                )
            }
        }
    }

    @Test
    fun `cerrar sesion pide confirmacion y si confirma la cierra`() {
        var cerradas = 0
        setSesion(onCerrarSesion = { cerradas += 1 })

        composeTestRule.onNodeWithTag(CERRAR_SESION_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText("¿Estás seguro de que deseas cerrar sesión?").assertExists()
        composeTestRule.onNodeWithText("Sí").performClick()

        assertEquals(1, cerradas)
    }

    @Test
    fun `decir no a la confirmacion no cierra la sesion`() {
        var cerradas = 0
        setSesion(onCerrarSesion = { cerradas += 1 })

        composeTestRule.onNodeWithTag(CERRAR_SESION_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText("No").performClick()

        assertEquals(0, cerradas)
        composeTestRule.onNodeWithText("¿Estás seguro de que deseas cerrar sesión?").assertDoesNotExist()
    }

    @Test
    fun `el boton de cerrar sesion mide el toque del repo`() {
        setSesion(onCerrarSesion = {})
        composeTestRule.onNodeWithTag(CERRAR_SESION_TAG).performScrollTo().assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun `sin accion de sesion la seccion no existe`() {
        setSesion(onCerrarSesion = null)
        composeTestRule.onNodeWithTag(CERRAR_SESION_TAG).assertDoesNotExist()
    }
```

Agregar a los imports de la prueba (si faltan): `androidx.compose.ui.test.performScrollTo`, `androidx.compose.ui.test.assertHeightIsAtLeast`, `androidx.compose.ui.unit.dp`, `org.junit.Assert.assertEquals`, `com.example.msp_app.feature.configuracion.ui.components.CERRAR_SESION_TAG`.

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :feature:configuracion:testDebugUnitTest --tests 'com.example.msp_app.feature.configuracion.ui.ConfiguracionScreenTest'
```

Expected: FAIL de compilación: `No parameter with name 'onCerrarSesion' found`, `Unresolved reference: CERRAR_SESION_TAG`.

- [ ] **Step 3: Implementación mínima**

`feature/configuracion/src/main/kotlin/com/example/msp_app/feature/configuracion/ui/components/SesionSection.kt`:

```kotlin
package com.example.msp_app.feature.configuracion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.example.msp_app.core.designsystem.theme.MspTheme

const val CERRAR_SESION_TAG: String = "configuracion_cerrar_sesion"

/**
 * "Cerrar sesión" con confirmación, movido desde el cajón legado (spec §2.1.4).
 * El diálogo es el de `DrawerContainer` reubicado con sus mismos textos, no
 * reescrito.
 */
@Composable
fun SesionSection(onCerrarSesion: () -> Unit, modifier: Modifier = Modifier) {
    var confirmando by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(text = "Sesión", style = MspTheme.type.sectionHeader, color = MspTheme.colors.onSurface)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MspTheme.spacing.sm)
                .heightIn(min = MspTheme.spacing.touchTarget)
                .clip(MspTheme.shapes.control)
                .background(MspTheme.colors.surface)
                .clickable { confirmando = true }
                .testTag(CERRAR_SESION_TAG)
                .padding(horizontal = MspTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Cerrar sesión", style = MspTheme.type.bodyStrong, color = MspTheme.colors.danger)
        }
    }
    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Estás seguro de que deseas cerrar sesión?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmando = false
                    onCerrarSesion()
                }) { Text("Sí") }
            },
            dismissButton = {
                TextButton(onClick = { confirmando = false }) { Text("No") }
            }
        )
    }
}
```

En `ConfiguracionScreen.kt`:

1. Firma de `ConfiguracionScreen`: agregar el parámetro entre `navController` y `viewModel`:

```kotlin
fun ConfiguracionScreen(
    navController: NavController,
    onCerrarSesion: (() -> Unit)? = null,
    viewModel: ConfiguracionViewModel = hiltViewModel()
) {
```

2. En la llamada a `ConfiguracionContent` dentro de `ConfiguracionScreen`, después del bloque `onAbrirDescarga = { … }`, agregar `onCerrarSesion = onCerrarSesion`.

3. Firma de `ConfiguracionContent`: agregar como último parámetro `onCerrarSesion: (() -> Unit)? = null`.

4. En `ConfiguracionContent`, inmediatamente antes de la última línea `Spacer(Modifier.height(MspTheme.spacing.lg))` del `Column` con scroll:

```kotlin
            if (onCerrarSesion != null) {
                Spacer(Modifier.height(SECTION_GAP))
                SesionSection(onCerrarSesion = onCerrarSesion, modifier = Modifier.fillMaxWidth())
            }
```

y el import `com.example.msp_app.feature.configuracion.ui.components.SesionSection`.

En `AppNavigation.kt`, reemplazar `ConfiguracionScreen(navController = navController)` (línea 723) por:

```kotlin
                ConfiguracionScreen(
                    navController = navController,
                    // "Cerrar sesión" vive aquí desde el menú lateral nuevo (spec §2.1.4).
                    onCerrarSesion = { authViewModel.logout() }
                )
```

- [ ] **Step 4: Correr y ver verde; goldens de Configuración intactos**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
rm -rf feature/configuracion/build/outputs/roborazzi
./gradlew --no-parallel --no-daemon :feature:configuracion:testDebugUnitTest :feature:configuracion:verifyRoborazziDebug :feature:configuracion:detekt :feature:configuracion:ktlintFormat :feature:configuracion:ktlintCheck
./gradlew --no-parallel --no-daemon :app:assembleDevlocalDebug :app:ktlintFormat :app:ktlintCheck
grep -o 'testcase name="[^"]*sesion[^"]*"' feature/configuracion/build/test-results/testDebugUnitTest/TEST-*ConfiguracionScreenTest.xml | wc -l
```

Expected: BUILD SUCCESSFUL; el `grep` cuenta `4` casos con "sesion" en el nombre; ningún golden se mueve (los goldens de este módulo sólo capturan `DescargasSection`).

- [ ] **Step 5: Commit**

```bash
git add feature/configuracion app/src/main/java/com/example/msp_app/navigation/AppNavigation.kt
git commit -m "feat(configuracion): cerrar sesion con confirmacion dentro de configuracion"
```

---

### Task 10: Catálogo, navegación y `MenuDeLaApp`

**Files:**
- Create: `app/src/main/java/com/example/msp_app/navigation/menu/CatalogoDelMenu.kt`
- Create: `app/src/main/java/com/example/msp_app/navigation/menu/NavegacionDelMenu.kt`
- Create: `app/src/main/java/com/example/msp_app/navigation/menu/MenuDeLaApp.kt`
- Test: `app/src/test/java/com/example/msp_app/navigation/menu/CatalogoDelMenuTest.kt`
- Test: `app/src/test/java/com/example/msp_app/navigation/menu/NavegacionDelMenuTest.kt`

**Interfaces:**
- Consumes: `IdsDelMenu` (Task 8); modelo, `ReglasDelMenu` (Task 1); `MenuLateral`, `PieDelMenu` (Task 3); `MenuLateralContenedor` (Task 4); `MenuLateralViewModel` (Task 2); `Screen` (`AppNavigation.kt:79`), `PagosRutas`, `VisitasRutas`; `User`; `LocalAuthViewModel`; `ResultState`; `UpdateBanner(compact: Boolean)`; `BuildConfig.VERSION_NAME`.
- Produces:
  - `internal data class EntradaDelCatalogo(val elemento: ElementoDelMenu, val ruta: String, val seccionId: String)`
  - `internal object CatalogoDelMenu { fun secciones(modulos: List<String>, contadores: Map<String, String>): List<SeccionDelMenu>; fun activoPara(ruta: String?): ActivoEnElMenu; fun entrada(itemId: String): EntradaDelCatalogo?; fun cabecera(usuario: User?, envio: EstadoDeEnvio?): CabeceraDelMenu }`
  - `internal fun navegarDesdeElMenu(navController: NavController, itemId: String)`; `internal fun navegarAConfiguracion(navController: NavController)`
  - `@Composable fun MenuDeLaApp(navController: NavController, contenido: @Composable (abrirMenu: () -> Unit) -> Unit)` — MISMA forma que `DrawerContainer(navController) { abrir -> … }`

- [ ] **Step 1: Escribir las pruebas que fallan**

`app/src/test/java/com/example/msp_app/navigation/menu/CatalogoDelMenuTest.kt`:

```kotlin
package com.example.msp_app.navigation.menu

import com.example.msp_app.core.navegacion.domain.model.ActivoEnElMenu
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogoDelMenuTest {

    @Test
    fun `los cuatro modulos dan las diez opciones en el orden de hoy`() {
        val secciones = CatalogoDelMenu.secciones(listOf("COBRO", "VENTAS", "ALMACEN", "GARANTIAS"), emptyMap())
        assertEquals(listOf("COBRO", "VENTAS", "ALMACEN", "GARANTIAS"), secciones.map { it.id })
        assertEquals(
            listOf(
                "Inicio", "Clientes", "Reporte de cobranza", "Mapa de rutas",
                "Ventas", "Mi Camioneta", "Catálogo de Productos",
                "Traspasos", "Asignación de Camionetas",
                "Garantías"
            ),
            secciones.flatMap { s -> s.items.map { it.etiqueta } }
        )
    }

    @Test
    fun `solo aparecen las secciones de los modulos del usuario`() {
        assertEquals(listOf("VENTAS"), CatalogoDelMenu.secciones(listOf("VENTAS"), emptyMap()).map { it.id })
    }

    @Test
    fun `sin modulos no hay secciones`() {
        assertEquals(emptyList<Any>(), CatalogoDelMenu.secciones(emptyList(), emptyMap()))
    }

    @Test
    fun `los contadores se pegan a su opcion`() {
        val secciones = CatalogoDelMenu.secciones(listOf("COBRO"), mapOf(IdsDelMenu.CLIENTES to "8 por volver"))
        assertEquals("8 por volver", secciones.single().items.single { it.id == IdsDelMenu.CLIENTES }.contador)
    }

    @Test
    fun `una ruta de nivel superior activa su opcion y su seccion`() {
        assertEquals(ActivoEnElMenu("COBRO", IdsDelMenu.CLIENTES), CatalogoDelMenu.activoPara(PagosRutas.LISTA_CLIENTES))
        assertEquals(ActivoEnElMenu("ALMACEN", IdsDelMenu.TRASPASOS), CatalogoDelMenu.activoPara(Screen.TransfersList.route))
    }

    @Test
    fun `una ruta hija abre su seccion sin resaltar opcion`() {
        assertEquals(ActivoEnElMenu("COBRO", null), CatalogoDelMenu.activoPara(PagosRutas.DETALLE_CLIENTE))
        assertEquals(ActivoEnElMenu("VENTAS", null), CatalogoDelMenu.activoPara(Screen.NewSale.route))
    }

    @Test
    fun `una ruta que no pertenece a ninguna seccion no activa nada`() {
        assertEquals(ActivoEnElMenu.NINGUNO, CatalogoDelMenu.activoPara(Screen.Configuracion.route))
        assertEquals(ActivoEnElMenu.NINGUNO, CatalogoDelMenu.activoPara(null))
    }

    @Test
    fun `sin usuario la cabecera queda con signo de interrogacion y sin zona`() {
        val cabecera = CatalogoDelMenu.cabecera(usuario = null, envio = null)
        assertEquals("?", cabecera.iniciales)
        assertEquals("", cabecera.nombre)
        assertNull(cabecera.ruta)
        assertNull(cabecera.rol)
    }

    @Test
    fun `con usuario la cabecera lleva iniciales, nombre y zona`() {
        val usuario = User(NOMBRE = "Efraín Domínguez Reyes", ZONA_CLIENTE_ID = 21, MODULOS = listOf("COBRO"))
        val cabecera = CatalogoDelMenu.cabecera(usuario, envio = null)
        assertEquals("ED", cabecera.iniciales)
        assertEquals("Efraín Domínguez Reyes", cabecera.nombre)
        assertEquals("Zona 21", cabecera.ruta)
    }
}
```

`app/src/test/java/com/example/msp_app/navigation/menu/NavegacionDelMenuTest.kt`:

```kotlin
package com.example.msp_app.navigation.menu

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NavegacionDelMenuTest {

    private fun nav(): TestNavHostController {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {}
            composable(PagosRutas.LISTA_CLIENTES) {}
            composable(Screen.DailyReport.route) {}
            composable(Screen.Configuracion.route) {}
        }
        return nav
    }

    private fun pila(nav: TestNavHostController) = nav.backStack.mapNotNull { it.destination.route }.filter { it != nav.graph.route }

    @Test
    fun `elegir clientes deja home abajo`() {
        val nav = nav()
        navegarDesdeElMenu(nav, IdsDelMenu.CLIENTES)
        assertEquals(listOf(Screen.Home.route, PagosRutas.LISTA_CLIENTES), pila(nav))
    }

    @Test
    fun `elegir la opcion de la pantalla actual no navega`() {
        val nav = nav()
        navegarDesdeElMenu(nav, IdsDelMenu.CLIENTES)
        val antes = nav.backStack.map { it.id }

        navegarDesdeElMenu(nav, IdsDelMenu.CLIENTES)

        assertEquals(antes, nav.backStack.map { it.id })
    }

    @Test
    fun `elegir inicio estando en inicio no recrea la pantalla`() {
        val nav = nav()
        val antes = nav.backStack.map { it.id }
        navegarDesdeElMenu(nav, IdsDelMenu.INICIO)
        assertEquals(antes, nav.backStack.map { it.id })
    }

    @Test
    fun `dos toques rapidos dejan una sola copia`() {
        val nav = nav()
        navegarDesdeElMenu(nav, IdsDelMenu.REPORTE)
        navegarDesdeElMenu(nav, IdsDelMenu.REPORTE)
        assertEquals(listOf(Screen.Home.route, Screen.DailyReport.route), pila(nav))
    }

    @Test
    fun `cambiar de opcion reemplaza la anterior sobre home`() {
        val nav = nav()
        navegarDesdeElMenu(nav, IdsDelMenu.CLIENTES)
        navegarDesdeElMenu(nav, IdsDelMenu.REPORTE)
        assertEquals(listOf(Screen.Home.route, Screen.DailyReport.route), pila(nav))
    }

    @Test
    fun `configuracion se apila una sola vez`() {
        val nav = nav()
        navegarAConfiguracion(nav)
        navegarAConfiguracion(nav)
        assertEquals(listOf(Screen.Home.route, Screen.Configuracion.route), pila(nav))
    }
}
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.menu.*'
```

Expected: FAIL de compilación: `Unresolved reference: CatalogoDelMenu`, `navegarDesdeElMenu`, `navegarAConfiguracion`.

- [ ] **Step 3: Implementación mínima**

`app/src/main/java/com/example/msp_app/navigation/menu/CatalogoDelMenu.kt`:

```kotlin
package com.example.msp_app.navigation.menu

import com.example.msp_app.core.navegacion.application.ReglasDelMenu
import com.example.msp_app.core.navegacion.domain.model.ActivoEnElMenu
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.ElementoDelMenu
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.feature.visitas.ui.VisitasRutas
import com.example.msp_app.navigation.Screen

internal data class EntradaDelCatalogo(
    val elemento: ElementoDelMenu,
    val ruta: String,
    val seccionId: String
)

/**
 * Las opciones del menú por módulo, con las MISMAS etiquetas, rutas y orden que
 * `DrawerContainer.kt:136-334` (un cobrador no debe buscar nada de nuevo).
 */
internal object CatalogoDelMenu {

    private const val COBRO = "COBRO"
    private const val VENTAS = "VENTAS"
    private const val ALMACEN = "ALMACEN"
    private const val GARANTIAS = "GARANTIAS"

    private val TITULOS = linkedMapOf(
        COBRO to "Cobro",
        VENTAS to "Ventas",
        ALMACEN to "Almacén",
        GARANTIAS to "Garantías"
    )

    private val ENTRADAS: List<EntradaDelCatalogo> = listOf(
        entrada(IdsDelMenu.INICIO, "Inicio", IconoDelMenu.INICIO, Screen.Home.route, COBRO),
        entrada(IdsDelMenu.CLIENTES, "Clientes", IconoDelMenu.CLIENTES, PagosRutas.LISTA_CLIENTES, COBRO),
        entrada(IdsDelMenu.REPORTE, "Reporte de cobranza", IconoDelMenu.REPORTE, Screen.DailyReport.route, COBRO),
        entrada(IdsDelMenu.MAPA, "Mapa de rutas", IconoDelMenu.MAPA, Screen.RouteMap.route, COBRO),
        entrada(IdsDelMenu.VENTAS, "Ventas", IconoDelMenu.VENTAS, Screen.SaleHome.route, VENTAS),
        entrada(IdsDelMenu.MI_CAMIONETA, "Mi Camioneta", IconoDelMenu.CAMIONETA, Screen.Cart.route, VENTAS),
        entrada(IdsDelMenu.CATALOGO, "Catálogo de Productos", IconoDelMenu.CATALOGO, Screen.ProductsCatalog.route, VENTAS),
        entrada(IdsDelMenu.TRASPASOS, "Traspasos", IconoDelMenu.TRASPASOS, Screen.TransfersList.route, ALMACEN),
        entrada(IdsDelMenu.ASIGNACION, "Asignación de Camionetas", IconoDelMenu.ASIGNACION, Screen.CamionetaAssignment.route, ALMACEN),
        entrada(IdsDelMenu.GARANTIAS, "Garantías", IconoDelMenu.GARANTIAS, Screen.GuaranteeList.route, GARANTIAS)
    )

    /** Rutas que no son una opción pero pertenecen a una sección: la abren sin resaltar nada. */
    private val RUTAS_HIJAS: Map<String, String> = mapOf(
        PagosRutas.DETALLE_CLIENTE to COBRO,
        PagosRutas.DETALLE_VENTA to COBRO,
        PagosRutas.BITACORA to COBRO,
        Screen.SaleDetails.route to COBRO,
        Screen.SaleMap.route to COBRO,
        Screen.PaymentTicket.route to COBRO,
        Screen.VisitTicket.route to COBRO,
        Screen.Forgiveness.route to COBRO,
        Screen.Guarantee.route to COBRO,
        PagosRutas.REGISTRAR_ABONO to COBRO,
        VisitasRutas.REGISTRAR to COBRO,
        Screen.NewSale.route to VENTAS,
        Screen.SaleDetailsList.route to VENTAS,
        Screen.SaleDescripction.route to VENTAS,
        Screen.EditSale.route to VENTAS,
        Screen.ProductDetails.route to VENTAS,
        Screen.TransferDetail.route to ALMACEN,
        Screen.NewTransfer.route to ALMACEN,
        Screen.CreateGuarantee.route to GARANTIAS,
        Screen.GuaranteeDetail.route to GARANTIAS
    )

    private fun entrada(id: String, etiqueta: String, icono: IconoDelMenu, ruta: String, seccion: String) =
        EntradaDelCatalogo(ElementoDelMenu(id, etiqueta, icono), ruta, seccion)

    fun secciones(modulos: List<String>, contadores: Map<String, String>): List<SeccionDelMenu> =
        TITULOS.filterKeys { it in modulos }.map { (id, titulo) ->
            SeccionDelMenu(
                id = id,
                titulo = titulo,
                items = ENTRADAS.filter { it.seccionId == id }.map { it.elemento.copy(contador = contadores[it.elemento.id]) }
            )
        }

    fun activoPara(ruta: String?): ActivoEnElMenu {
        if (ruta == null) return ActivoEnElMenu.NINGUNO
        ENTRADAS.firstOrNull { it.ruta == ruta }?.let { return ActivoEnElMenu(it.seccionId, it.elemento.id) }
        return RUTAS_HIJAS[ruta]?.let { ActivoEnElMenu(it, null) } ?: ActivoEnElMenu.NINGUNO
    }

    fun entrada(itemId: String): EntradaDelCatalogo? = ENTRADAS.firstOrNull { it.elemento.id == itemId }

    /** `rol` siempre `null`: `User` no trae rol (decisión D11 del plan). */
    fun cabecera(usuario: User?, envio: EstadoDeEnvio?): CabeceraDelMenu {
        val nombre = usuario?.NOMBRE.orEmpty()
        return CabeceraDelMenu(
            iniciales = ReglasDelMenu.iniciales(nombre),
            nombre = nombre,
            rol = null,
            ruta = usuario?.ZONA_CLIENTE_ID?.takeIf { it > 0 }?.let { "Zona $it" },
            envio = envio
        )
    }
}
```

`app/src/main/java/com/example/msp_app/navigation/menu/NavegacionDelMenu.kt`:

```kotlin
package com.example.msp_app.navigation.menu

import androidx.navigation.NavController
import com.example.msp_app.navigation.Screen

/**
 * Navega desde el menú con las mismas pilas que `DrawerContainer` (Home abajo)
 * más dos reglas nuevas (Review Focus 2): la opción de la pantalla actual no
 * navega, y `launchSingleTop` evita la copia doble de un toque repetido.
 */
internal fun navegarDesdeElMenu(navController: NavController, itemId: String) {
    val entrada = CatalogoDelMenu.entrada(itemId) ?: return
    if (navController.currentDestination?.route == entrada.ruta) return
    navController.navigate(entrada.ruta) {
        if (entrada.ruta == Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
        } else {
            popUpTo(Screen.Home.route)
        }
        launchSingleTop = true
    }
}

internal fun navegarAConfiguracion(navController: NavController) {
    if (navController.currentDestination?.route == Screen.Configuracion.route) return
    navController.navigate(Screen.Configuracion.route) { launchSingleTop = true }
}
```

`app/src/main/java/com/example/msp_app/navigation/menu/MenuDeLaApp.kt`:

```kotlin
package com.example.msp_app.navigation.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.msp_app.BuildConfig
import com.example.msp_app.components.UpdateBanner
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.navegacion.ui.MenuLateral
import com.example.msp_app.core.navegacion.ui.MenuLateralContenedor
import com.example.msp_app.core.navegacion.ui.MenuLateralViewModel
import com.example.msp_app.core.navegacion.ui.components.PieDelMenu
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.auth.User

/**
 * El menú lateral nuevo para cualquier pantalla de `:app`. Misma forma que
 * `DrawerContainer(navController) { abrir -> … }`: adoptarlo es cambiar el
 * nombre de la llamada.
 *
 * `runCatching` sobre `LocalAuthViewModel` por la misma razón que el cajón
 * viejo: una red de prueba compone destinos sin sesión, y sin sesión el menú se
 * pinta con sólo el pie (Review Focus 4), nunca truena.
 */
@Composable
fun MenuDeLaApp(
    navController: NavController,
    contenido: @Composable (abrirMenu: () -> Unit) -> Unit
) {
    val authViewModel = runCatching { LocalAuthViewModel.current }.getOrNull()
    val usuario: User? = if (authViewModel != null) {
        val estadoDelUsuario by authViewModel.userData.collectAsState()
        (estadoDelUsuario as? ResultState.Success<User?>)?.data
    } else {
        null
    }
    val viewModel: MenuLateralViewModel = hiltViewModel()
    val estado by viewModel.estado.collectAsState()
    val rutaActual = navController.currentBackStackEntryAsState().value?.destination?.route
    val secciones = CatalogoDelMenu.secciones(usuario?.MODULOS.orEmpty(), estado.contadores)

    MenuLateralContenedor(
        hoja = { cerrar, apertura ->
            MenuLateral(
                cabecera = CatalogoDelMenu.cabecera(usuario, estado.envio),
                secciones = secciones,
                activo = CatalogoDelMenu.activoPara(rutaActual),
                seccionesPlegadas = estado.plegadas,
                apertura = apertura,
                onTogglePlegado = viewModel::cambiarPlegado,
                onElegir = { elemento ->
                    cerrar()
                    navegarDesdeElMenu(navController, elemento.id)
                },
                pie = {
                    PieDelMenu(
                        version = "Versión ${BuildConfig.VERSION_NAME}",
                        onConfiguracion = {
                            cerrar()
                            navegarAConfiguracion(navController)
                        },
                        aviso = { UpdateBanner(compact = true) }
                    )
                }
            )
        },
        onAbierto = { viewModel.alAbrir(secciones.flatMap { seccion -> seccion.items.map { it.id } }.toSet()) },
        contenido = contenido
    )
}
```

- [ ] **Step 4: Correr y ver verde, y contar**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.menu.*' :app:assembleDevlocalDebug :app:ktlintFormat :app:ktlintCheck
grep -ho 'tests="[0-9]*"' app/build/test-results/testDevlocalDebugUnitTest/TEST-*CatalogoDelMenuTest.xml app/build/test-results/testDevlocalDebugUnitTest/TEST-*NavegacionDelMenuTest.xml
```

Expected: BUILD SUCCESSFUL; `tests="9"` y `tests="6"`. Si `TestNavHostController.backStack` no compila en navigation 2.7.7, usar `nav.currentBackStack.value` en `pila()` y en las comparaciones de ids, y anotarlo.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/msp_app/navigation/menu app/src/test/java/com/example/msp_app/navigation/menu
git commit -m "feat(menu): catalogo por modulo, navegacion sin duplicados y MenuDeLaApp"
```

---

### Task 11: Adopción — Inicio, Clientes y Sin módulos

**Files:**
- Modify: `app/src/main/java/com/example/msp_app/features/home/screens/Home.kt:317`
- Modify: `app/src/main/java/com/example/msp_app/navigation/DestinosDeCobranzaGraph.kt:7,67-76`
- Modify: `app/src/main/java/com/example/msp_app/features/common/NoModulesScreen.kt:31,38`
- Create: `app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt`
- Rename+Modify: `app/src/test/java/com/example/msp_app/navigation/LaHamburguesaDeClientesAbreElCajonTest.kt` → `LaHamburguesaDeClientesAbreElMenuLateralTest.kt`
- Modify: `app/src/test/java/com/example/msp_app/navigation/CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest.kt` (prueba nueva)

**Interfaces:**
- Consumes: `MenuDeLaApp` (Task 10); `MENU_LATERAL_TAG`, `FILA_DEL_MENU_TAG`, `ID_CONFIGURACION` (Task 3); `EscanerDeFuentes.archivosQueMencionan(simbolo: String): List<String>`; `BOTON_MENU_TAG`.
- Produces: `AdopcionDelMenuLateralTest.ADOPTADAS: List<String>` (la lista que crece en las Tasks 12–15).

- [ ] **Step 1: Escribir las pruebas que fallan**

`app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt`:

```kotlin
package com.example.msp_app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cada pantalla adoptada llama a `MenuDeLaApp` y ya no a `DrawerContainer`.
 * [ADOPTADAS] crece una tarea a la vez (plan 2026-10-06-menu-lateral, Tasks
 * 11–15); la Task 18 la reemplaza por "nadie menciona DrawerContainer".
 */
class AdopcionDelMenuLateralTest {

    private val escaner = EscanerDeFuentes()

    @Test
    fun `las pantallas adoptadas llaman al menu nuevo y no al cajon viejo`() {
        val conMenu = escaner.archivosQueMencionan("MenuDeLaApp")
        val conCajon = escaner.archivosQueMencionan("DrawerContainer")

        val sinMenu = ADOPTADAS.filterNot { ruta -> conMenu.any { it.endsWith(ruta) } }
        val conCajonTodavia = ADOPTADAS.filter { ruta -> conCajon.any { it.endsWith(ruta) } }

        assertEquals("no llaman a MenuDeLaApp", emptyList<String>(), sinMenu)
        assertEquals("siguen llamando a DrawerContainer", emptyList<String>(), conCajonTodavia)
    }

    @Test
    fun `control positivo, el escaner ve la definicion del menu y la del cajon`() {
        assertTrue(escaner.archivosQueMencionan("MenuDeLaApp").any { it.endsWith("navigation/menu/MenuDeLaApp.kt") })
        assertTrue(escaner.archivosQueMencionan("DrawerContainer").any { it.endsWith("components/DrawerContainer.kt") })
    }

    companion object {
        val ADOPTADAS: List<String> = listOf(
            "features/home/screens/Home.kt",
            "navigation/DestinosDeCobranzaGraph.kt",
            "features/common/NoModulesScreen.kt"
        )
    }
}
```

Renombrar la red de la hamburguesa y cambiar sus aserciones:

```bash
git mv app/src/test/java/com/example/msp_app/navigation/LaHamburguesaDeClientesAbreElCajonTest.kt app/src/test/java/com/example/msp_app/navigation/LaHamburguesaDeClientesAbreElMenuLateralTest.kt
```

En el archivo renombrado: la clase pasa a `class LaHamburguesaDeClientesAbreElMenuLateralTest`; el KDoc de cabecera dice "abre el menú lateral nuevo (`MenuDeLaApp`)" en vez de "`DrawerContainer`" y conserva el párrafo del fixture de Firebase cambiando la causa a "el pie del menú pinta `UpdateBanner`"; el test se renombra a `` `tocar abrir menu en la lista de clientes abre el menu lateral` `` y su bloque de aserciones queda:

```kotlin
        composeTestRule.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()

        composeTestRule.onNodeWithTag(BOTON_MENU_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MENU_LATERAL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Configuración").assertIsDisplayed()
        // "Cerrar sesión" se mudó a Configuración (Task 9): el menú ya no lo trae.
        composeTestRule.onNodeWithText("Cerrar sesión").assertDoesNotExist()
```

con el import `com.example.msp_app.core.navegacion.ui.MENU_LATERAL_TAG`.

En `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`, después de `` `todo control tocable de cobranza conserva sus 50dp` ``:

```kotlin
    /**
     * **Las opciones del menú lateral miden 56 dp (`touchTarget`), no los 48 del
     * mock** (spec §2.1.6). Se mide sobre el `NavHost` real de la lista, donde el
     * menú se compone aunque esté cerrado. Sin sesión en esta red, la única
     * opción presente es Configuración: es el control positivo.
     */
    @Test
    fun `cada opcion del menu lateral mide 56dp`() {
        val rutas = montarElGrafo()
        assertTrue(PagosRutas.LISTA_CLIENTES in rutas)
        val minimo = with(composeTestRule.density) { ALTO_DE_LA_OPCION_DEL_MENU.roundToPx() }

        val opciones = controlesDe(PagosRutas.LISTA_CLIENTES).filter { nodo ->
            nodo.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(FILA_DEL_MENU_TAG) == true
        }

        assertTrue(
            "no se encontró la opción Configuración del menú: el barrido no vería ninguna",
            opciones.any { it.config.getOrNull(SemanticsProperties.TestTag) == FILA_DEL_MENU_TAG + ID_CONFIGURACION }
        )
        val bajas = opciones
            .filter { it.boundsInRoot.height.toInt() < minimo }
            .map { "${it.config.getOrNull(SemanticsProperties.TestTag)} mide ${it.boundsInRoot.height.toInt()}px" }
        assertEquals(emptyList<String>(), bajas)
    }
```

con `val ALTO_DE_LA_OPCION_DEL_MENU = 56.dp` en el `companion object` y los imports `com.example.msp_app.core.navegacion.ui.FILA_DEL_MENU_TAG`, `com.example.msp_app.core.navegacion.ui.ID_CONFIGURACION`.

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.AdopcionDelMenuLateralTest' --tests 'com.example.msp_app.navigation.LaHamburguesaDeClientesAbreElMenuLateralTest' --tests 'com.example.msp_app.navigation.CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest'
```

Expected: FAIL — `AdopcionDelMenuLateralTest`: "no llaman a MenuDeLaApp" con las 3 rutas; `LaHamburguesa…`: `MENU_LATERAL_TAG` no existe; `cada opcion del menu lateral mide 56dp`: "no se encontró la opción Configuración del menú".

- [ ] **Step 3: Implementación mínima**

En `DestinosDeCobranzaGraph.kt`, reemplazar el import `com.example.msp_app.components.DrawerContainer` por `com.example.msp_app.navigation.menu.MenuDeLaApp`, y el bloque `envoltura` (líneas 67-76) por:

```kotlin
        // ÚNICO punto de cableado del menú de la lista: el menú lateral nuevo
        // (docs/superpowers/specs/2026-10-05-menu-lateral-design.md), ver el KDoc
        // de `destinoDeListaDeClientes.envoltura`.
        envoltura = { contenido ->
            MenuDeLaApp(navController = navController) { abrirMenu ->
                contenido(abrirMenu)
            }
        }
```

Y actualizar el comentario de la línea 60-61 a "se llega desde el menú lateral".

En `Home.kt` y `NoModulesScreen.kt`, cambio mecánico:

```bash
for f in app/src/main/java/com/example/msp_app/features/home/screens/Home.kt app/src/main/java/com/example/msp_app/features/common/NoModulesScreen.kt; do
  sed -i '' -e 's/^import com\.example\.msp_app\.components\.DrawerContainer$/import com.example.msp_app.navigation.menu.MenuDeLaApp/' -e 's/DrawerContainer(/MenuDeLaApp(/' "$f"
done
grep -n "DrawerContainer\|MenuDeLaApp" app/src/main/java/com/example/msp_app/features/home/screens/Home.kt app/src/main/java/com/example/msp_app/features/common/NoModulesScreen.kt
```

Expected del `grep`: sólo líneas con `MenuDeLaApp` (import y llamada) en cada archivo.

- [ ] **Step 4: Correr y ver verde, incluidas las redes de navegación completas**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:ktlintFormat :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.*' :app:ktlintCheck
grep -ho 'tests="[0-9]*"' app/build/test-results/testDevlocalDebugUnitTest/TEST-com.example.msp_app.navigation.*.xml
```

Expected: BUILD SUCCESSFUL. Anotar los conteos por archivo; `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` sube en 1. Si `todo control tocable de cobranza conserva sus 50dp` sale rojo con un control del menú, se SUBE la implementación del menú (nunca se agrega a `DEUDA_DE_TOQUE`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/msp_app/features/home/screens/Home.kt app/src/main/java/com/example/msp_app/navigation/DestinosDeCobranzaGraph.kt app/src/main/java/com/example/msp_app/features/common/NoModulesScreen.kt app/src/test/java/com/example/msp_app/navigation
git commit -m "feat(menu): inicio, clientes y sin modulos adoptan el menu lateral nuevo"
```

---

### Task 12: Adopción — Reporte de cobranza y Mapa de rutas

**Files:**
- Modify: `app/src/main/java/com/example/msp_app/navigation/AppNavigation.kt:542` (y su import)
- Modify: `app/src/main/java/com/example/msp_app/features/routemap/RouteMapScreen.kt:138` (y su import)
- Modify: `app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt` (`ADOPTADAS`)

**Interfaces:**
- Consumes: `MenuDeLaApp` (Task 10); `AdopcionDelMenuLateralTest.ADOPTADAS` (Task 11).
- Produces: `ADOPTADAS` con 5 rutas.

- [ ] **Step 1: Escribir la prueba que falla**

En `AdopcionDelMenuLateralTest.ADOPTADAS`, agregar al final de la lista:

```kotlin
            "navigation/AppNavigation.kt",
            "features/routemap/RouteMapScreen.kt"
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.AdopcionDelMenuLateralTest'
```

Expected: FAIL — "siguen llamando a DrawerContainer" con `navigation/AppNavigation.kt` y `features/routemap/RouteMapScreen.kt` (y "no llaman a MenuDeLaApp" con los dos).

- [ ] **Step 3: Implementación mínima**

```bash
for f in app/src/main/java/com/example/msp_app/navigation/AppNavigation.kt app/src/main/java/com/example/msp_app/features/routemap/RouteMapScreen.kt; do
  sed -i '' -e 's/^import com\.example\.msp_app\.components\.DrawerContainer$/import com.example.msp_app.navigation.menu.MenuDeLaApp/' -e 's/DrawerContainer(/MenuDeLaApp(/' "$f"
done
grep -n "DrawerContainer(\|MenuDeLaApp" app/src/main/java/com/example/msp_app/navigation/AppNavigation.kt app/src/main/java/com/example/msp_app/features/routemap/RouteMapScreen.kt
```

Expected del `grep`: import + una llamada `MenuDeLaApp(` por archivo, ninguna `DrawerContainer(`. En `AppNavigation.kt`, actualizar a mano el comentario de las líneas 522-524 ("El drawer real se cablea aquí: `DrawerContainer` provee `openDrawer`…") a "El menú lateral se cablea aquí: `MenuDeLaApp` provee `openDrawer`…".

- [ ] **Step 4: Correr y ver verde**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:ktlintFormat :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.*' :app:assembleDevlocalDebug :app:ktlintCheck
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/msp_app/navigation/AppNavigation.kt app/src/main/java/com/example/msp_app/features/routemap/RouteMapScreen.kt app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt
git commit -m "feat(menu): reporte de cobranza y mapa de rutas adoptan el menu lateral"
```

---

### Task 13: Adopción — pantallas de VENTAS

**Files:**
- Modify: `app/src/main/java/com/example/msp_app/features/productsInventory/screens/BlankScreen.kt:108`
- Modify: `app/src/main/java/com/example/msp_app/features/sales/screens/SalesListScreen.kt:76`
- Modify: `app/src/main/java/com/example/msp_app/features/sales/screens/UnifiedSalesScreen.kt:212`
- Modify: `app/src/main/java/com/example/msp_app/features/sales/screens/NewSaleScreen.kt:650`
- Modify: `app/src/main/java/com/example/msp_app/features/cart/screens/CartScreen.kt:235`
- Modify: `app/src/main/java/com/example/msp_app/features/productsInventory/screens/ProductsCatalogScreen.kt:115`
- Modify: `app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt` (`ADOPTADAS`)

**Interfaces:**
- Consumes: `MenuDeLaApp`; `ADOPTADAS`.
- Produces: `ADOPTADAS` con 11 rutas.

- [ ] **Step 1: Escribir la prueba que falla**

En `ADOPTADAS`, agregar:

```kotlin
            "features/productsInventory/screens/BlankScreen.kt",
            "features/sales/screens/SalesListScreen.kt",
            "features/sales/screens/UnifiedSalesScreen.kt",
            "features/sales/screens/NewSaleScreen.kt",
            "features/cart/screens/CartScreen.kt",
            "features/productsInventory/screens/ProductsCatalogScreen.kt"
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.AdopcionDelMenuLateralTest'
```

Expected: FAIL — las 6 rutas nuevas en "siguen llamando a DrawerContainer".

- [ ] **Step 3: Implementación mínima**

```bash
for f in \
  app/src/main/java/com/example/msp_app/features/productsInventory/screens/BlankScreen.kt \
  app/src/main/java/com/example/msp_app/features/sales/screens/SalesListScreen.kt \
  app/src/main/java/com/example/msp_app/features/sales/screens/UnifiedSalesScreen.kt \
  app/src/main/java/com/example/msp_app/features/sales/screens/NewSaleScreen.kt \
  app/src/main/java/com/example/msp_app/features/cart/screens/CartScreen.kt \
  app/src/main/java/com/example/msp_app/features/productsInventory/screens/ProductsCatalogScreen.kt; do
  sed -i '' -e 's/^import com\.example\.msp_app\.components\.DrawerContainer$/import com.example.msp_app.navigation.menu.MenuDeLaApp/' -e 's/DrawerContainer(/MenuDeLaApp(/' "$f"
  grep -c "MenuDeLaApp" "$f"
done
```

Expected: cada archivo imprime `2` (import + llamada). Un archivo que imprima otro número se revisa a mano antes de seguir.

- [ ] **Step 4: Correr y ver verde**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:ktlintFormat :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.*' :app:assembleDevlocalDebug :app:ktlintCheck
```

Expected: BUILD SUCCESSFUL. `checkNoLegacySaleEdit` no se ve afectado (no se toca ninguna llamada de subida de ventas).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/msp_app/features/productsInventory app/src/main/java/com/example/msp_app/features/sales app/src/main/java/com/example/msp_app/features/cart app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt
git commit -m "feat(menu): las pantallas de ventas adoptan el menu lateral"
```

---

### Task 14: Adopción — ALMACÉN y GARANTÍAS

**Files:**
- Modify: `app/src/main/java/com/example/msp_app/features/transfers/presentation/list/TransfersListScreen.kt:112`
- Modify: `app/src/main/java/com/example/msp_app/features/camionetaAssignment/presentation/screens/CamionetaAssignmentScreen.kt:103`
- Modify: `app/src/main/java/com/example/msp_app/features/guarantees/screens/GuaranteeListScreen.kt:45`
- Modify: `app/src/main/java/com/example/msp_app/features/guarantees/screens/CreateGuaranteeScreen.kt:66`
- Modify: `app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt` (`ADOPTADAS`)

**Interfaces:**
- Consumes: `MenuDeLaApp`; `ADOPTADAS`.
- Produces: `ADOPTADAS` con 15 rutas.

- [ ] **Step 1: Escribir la prueba que falla**

En `ADOPTADAS`, agregar:

```kotlin
            "features/transfers/presentation/list/TransfersListScreen.kt",
            "features/camionetaAssignment/presentation/screens/CamionetaAssignmentScreen.kt",
            "features/guarantees/screens/GuaranteeListScreen.kt",
            "features/guarantees/screens/CreateGuaranteeScreen.kt"
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.AdopcionDelMenuLateralTest'
```

Expected: FAIL — las 4 rutas nuevas en "siguen llamando a DrawerContainer".

- [ ] **Step 3: Implementación mínima**

```bash
for f in \
  app/src/main/java/com/example/msp_app/features/transfers/presentation/list/TransfersListScreen.kt \
  app/src/main/java/com/example/msp_app/features/camionetaAssignment/presentation/screens/CamionetaAssignmentScreen.kt \
  app/src/main/java/com/example/msp_app/features/guarantees/screens/GuaranteeListScreen.kt \
  app/src/main/java/com/example/msp_app/features/guarantees/screens/CreateGuaranteeScreen.kt; do
  sed -i '' -e 's/^import com\.example\.msp_app\.components\.DrawerContainer$/import com.example.msp_app.navigation.menu.MenuDeLaApp/' -e 's/DrawerContainer(/MenuDeLaApp(/' "$f"
  grep -c "MenuDeLaApp" "$f"
done
```

Expected: cada archivo imprime `2`.

- [ ] **Step 4: Correr y ver verde**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:ktlintFormat :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.*' :app:assembleDevlocalDebug :app:ktlintCheck
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/msp_app/features/transfers app/src/main/java/com/example/msp_app/features/camionetaAssignment app/src/main/java/com/example/msp_app/features/guarantees app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt
git commit -m "feat(menu): almacen y garantias adoptan el menu lateral"
```

---

### Task 15: Adopción — las cinco pantallas legadas de detalle (con hamburguesa)

Estas pantallas pintan hamburguesa, no flecha (decisión D9, LEÍDO). Conservan su botón y sólo cambian de cajón.

**Files:**
- Modify: `app/src/main/java/com/example/msp_app/features/sales/screens/SaleDetailsScreen.kt:89`
- Modify: `app/src/main/java/com/example/msp_app/features/sales/screens/SaleDescriptionScreen.kt:76`
- Modify: `app/src/main/java/com/example/msp_app/features/payments/screens/PaymentTicketScreen.kt:167`
- Modify: `app/src/main/java/com/example/msp_app/features/visit/screens/VisitTicketScreen.kt:140`
- Modify: `app/src/main/java/com/example/msp_app/features/productsInventory/screens/ProductDetailsScreen.kt:218`
- Modify: `app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt` (`ADOPTADAS`)

**Interfaces:**
- Consumes: `MenuDeLaApp`; `ADOPTADAS`.
- Produces: `ADOPTADAS` con 20 rutas (todas las llamadas de `DrawerContainer(` fuera de su definición).

- [ ] **Step 1: Escribir la prueba que falla**

En `ADOPTADAS`, agregar:

```kotlin
            "features/sales/screens/SaleDetailsScreen.kt",
            "features/sales/screens/SaleDescriptionScreen.kt",
            "features/payments/screens/PaymentTicketScreen.kt",
            "features/visit/screens/VisitTicketScreen.kt",
            "features/productsInventory/screens/ProductDetailsScreen.kt"
```

y una prueba de inventario al final de la clase:

```kotlin
    /** Ninguna llamada a `DrawerContainer(` queda fuera de su propia definición. */
    @Test
    fun `no queda ninguna pantalla con el cajon viejo`() {
        val conCajon = escaner.archivosQueMencionan("DrawerContainer")
            .filterNot { it.endsWith("components/DrawerContainer.kt") }
        assertEquals(emptyList<String>(), conCajon)
    }
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.AdopcionDelMenuLateralTest'
```

Expected: FAIL — las 5 rutas nuevas en "siguen llamando a DrawerContainer", y `no queda ninguna pantalla con el cajon viejo` lista los mismos 5 archivos.

- [ ] **Step 3: Implementación mínima**

```bash
for f in \
  app/src/main/java/com/example/msp_app/features/sales/screens/SaleDetailsScreen.kt \
  app/src/main/java/com/example/msp_app/features/sales/screens/SaleDescriptionScreen.kt \
  app/src/main/java/com/example/msp_app/features/payments/screens/PaymentTicketScreen.kt \
  app/src/main/java/com/example/msp_app/features/visit/screens/VisitTicketScreen.kt \
  app/src/main/java/com/example/msp_app/features/productsInventory/screens/ProductDetailsScreen.kt; do
  sed -i '' -e 's/^import com\.example\.msp_app\.components\.DrawerContainer$/import com.example.msp_app.navigation.menu.MenuDeLaApp/' -e 's/DrawerContainer(/MenuDeLaApp(/' "$f"
  grep -c "MenuDeLaApp" "$f"
done
```

Expected: cada archivo imprime `2`.

- [ ] **Step 4: Correr y ver verde**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:ktlintFormat :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.*' :app:assembleDevlocalDebug :app:ktlintCheck
```

Expected: BUILD SUCCESSFUL; `RetiroDelDialogoDePagoTest` y `LaCondonacionGuardadaVaAlTicketTest` siguen verdes (tocan `SaleDetailsScreen`/`PaymentTicketScreen`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/msp_app/features app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt
git commit -m "feat(menu): las pantallas legadas de detalle adoptan el menu lateral"
```

---

### Task 16: Deslizar desde la barra en los detalles con flecha; abono y visita sin menú

**Files:**
- Create: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/ZonaQueAbreElMenu.kt`
- Modify: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralContenedor.kt` (parámetro `gesto`)
- Modify: `app/src/main/java/com/example/msp_app/navigation/menu/MenuDeLaApp.kt` (parámetro `gesto`)
- Modify: `feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/ui/PagosRutas.kt` (`destinoDeDetalleCliente` y `destinosDePagos`)
- Modify: `app/src/main/java/com/example/msp_app/navigation/DestinosDeCobranzaGraph.kt` (llamadas de los dos destinos)
- Test: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/ui/ZonaQueAbreElMenuTest.kt`
- Test: `app/src/test/java/com/example/msp_app/navigation/ElMenuSeAbreDesdeLaBarraTest.kt`

**Interfaces:**
- Consumes: `MenuLateralContenedor` (Task 4); `MenuDeLaApp` (Task 10); `PagosRutas.detalleCliente(Int)`, `detalleVenta(Int)`, `registrarAbono(Int)`; `VisitasRutas.registrar(clienteId: Int, ventaId: Int? = null)`; `DestinosDeCobranzaTestActivity`.
- Produces:
  - `enum class AperturaPorGesto { DESLIZAR_EN_TODA_LA_PANTALLA, DESDE_LA_BARRA }`
  - `@Composable fun ZonaQueAbreElMenu(onAbrir: () -> Unit, modifier: Modifier = Modifier, bordeIzquierdoPx: Int? = null, contenido: @Composable () -> Unit)`; `val ALTO_DE_LA_FRANJA: Dp = 72.dp`; `val DISTANCIA_PARA_ABRIR: Dp = 48.dp`
  - `MenuLateralContenedor(hoja, onAbierto, gesto: AperturaPorGesto = AperturaPorGesto.DESLIZAR_EN_TODA_LA_PANTALLA, contenido)`
  - `MenuDeLaApp(navController, gesto: AperturaPorGesto = AperturaPorGesto.DESLIZAR_EN_TODA_LA_PANTALLA, contenido)`
  - `destinoDeDetalleCliente(…, envoltura: @Composable (contenido: @Composable () -> Unit) -> Unit = { contenido -> contenido() })` y el mismo parámetro en `destinosDePagos` (envuelve sólo `DETALLE_VENTA`)

- [ ] **Step 1: Escribir las pruebas que fallan**

`core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/ui/ZonaQueAbreElMenuTest.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Densidad mdpi de Robolectric: 1 px = 1 dp, y el inset de la barra de estado vale 0. */
class ZonaQueAbreElMenuTest : RobolectricTestBase() {

    @get:Rule
    val compose = createComposeRule()

    private var aperturas = 0
    private var toquesAtras = 0

    private fun montar(borde: Int = 0) {
        compose.setContent {
            Box(Modifier.size(360.dp, 640.dp)) {
                ZonaQueAbreElMenu(onAbrir = { aperturas += 1 }, bordeIzquierdoPx = borde) {
                    Box(Modifier.fillMaxSize().testTag("pantalla")) {
                        Button(onClick = { toquesAtras += 1 }, modifier = Modifier.testTag("atras").size(56.dp)) {
                            Text("Atrás")
                        }
                    }
                }
            }
        }
    }

    private fun deslizar(desdeX: Float, y: Float) {
        compose.onNodeWithTag("pantalla").performTouchInput {
            swipe(start = Offset(desdeX, y), end = Offset(desdeX + 240f, y), durationMillis = 250)
        }
        compose.waitForIdle()
    }

    @Test
    fun `deslizar a la derecha en la franja superior abre el menu`() {
        montar()
        deslizar(desdeX = 80f, y = 30f)
        assertEquals(1, aperturas)
    }

    @Test
    fun `deslizar debajo de la franja no abre`() {
        montar()
        deslizar(desdeX = 80f, y = 300f)
        assertEquals(0, aperturas)
    }

    @Test
    fun `tocar la flecha dentro de la franja sigue funcionando`() {
        montar()
        compose.onNodeWithTag("atras").performClick()
        assertEquals(1, toquesAtras)
        assertEquals(0, aperturas)
    }

    @Test
    fun `un gesto que nace en el borde del sistema es del sistema`() {
        montar(borde = 100)
        deslizar(desdeX = 50f, y = 30f)
        assertEquals(0, aperturas)
    }

    @Test
    fun `control positivo, el mismo gesto fuera del borde si abre`() {
        montar(borde = 100)
        deslizar(desdeX = 150f, y = 30f)
        assertEquals(1, aperturas)
    }
}
```

`app/src/test/java/com/example/msp_app/navigation/ElMenuSeAbreDesdeLaBarraTest.kt`:

```kotlin
package com.example.msp_app.navigation

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.navegacion.ui.MENU_LATERAL_TAG
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.feature.visitas.ui.VisitasRutas
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec §2.2 (decidido 2026-10-06): en detalle de cliente y de venta el menú se
 * abre deslizando desde la barra superior; Registrar abono y Registrar visita
 * NO tienen menú. Monta el MISMO grafo que `AppNavigation` ([destinosDeCobranza]).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class ElMenuSeAbreDesdeLaBarraTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        hiltRule.inject()
        val context: Context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:stub")
                    .setApiKey("stub-api-key")
                    .setProjectId("stub-project")
                    .build()
            )
        }
    }

    private fun montarEn(ruta: String) {
        val activity = Robolectric.buildActivity(DestinosDeCobranzaTestActivity::class.java).setup().get()
        val nav = TestNavHostController(activity)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.setLifecycleOwner(activity)
        nav.setViewModelStore(activity.viewModelStore)
        val grafo = nav.createGraph(startDestination = RAIZ) {
            composable(RAIZ) {}
            destinosDeCobranza(nav, activity)
        }
        nav.graph = grafo
        nav.navigate(ruta)
        activity.setContent { NavHost(navController = nav, graph = grafo) }
        composeTestRule.waitForIdle()
    }

    private fun deslizarEnLaBarra() {
        composeTestRule.onRoot().performTouchInput {
            swipe(start = Offset(120f, 30f), end = Offset(400f, 30f), durationMillis = 250)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `en el detalle de cliente deslizar desde la barra abre el menu`() {
        montarEn(PagosRutas.detalleCliente(1))
        deslizarEnLaBarra()
        composeTestRule.onNodeWithTag(MENU_LATERAL_TAG).assertIsDisplayed()
    }

    @Test
    fun `en el detalle de venta deslizar desde la barra abre el menu`() {
        montarEn(PagosRutas.detalleVenta(1))
        deslizarEnLaBarra()
        composeTestRule.onNodeWithTag(MENU_LATERAL_TAG).assertIsDisplayed()
    }

    @Test
    fun `registrar abono no tiene menu`() {
        montarEn(PagosRutas.registrarAbono(1))
        composeTestRule.onAllNodesWithTag(MENU_LATERAL_TAG).assertCountEquals(0)
    }

    @Test
    fun `registrar visita no tiene menu`() {
        montarEn(VisitasRutas.registrar(1, 1))
        composeTestRule.onAllNodesWithTag(MENU_LATERAL_TAG).assertCountEquals(0)
    }

    @Test
    fun `control positivo, el detalle de cliente si compone el menu`() {
        montarEn(PagosRutas.detalleCliente(1))
        composeTestRule.onAllNodesWithTag(MENU_LATERAL_TAG).assertCountEquals(1)
    }

    private companion object {
        const val RAIZ = "raiz_de_la_prueba_de_la_barra"
    }
}
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest --tests 'com.example.msp_app.core.navegacion.ui.ZonaQueAbreElMenuTest'
```

Expected: FAIL de compilación: `Unresolved reference: ZonaQueAbreElMenu`.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.ElMenuSeAbreDesdeLaBarraTest'
```

Expected: FAIL — `en el detalle de cliente…` y `…de venta…` con "could not find any node that satisfies (TestTag = 'menu_lateral')"; `control positivo…` con "Expected 1 but found 0". Las dos de "no tiene menú" pasan ya (son la guarda para el futuro).

- [ ] **Step 3: Implementación mínima**

`core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/ZonaQueAbreElMenu.kt`:

```kotlin
package com.example.msp_app.core.navegacion.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Alto de `BarraDeDetalle` (56 dp de botón + 2 × 8 dp, `Marco.kt:236-240`) bajo la barra de estado. */
val ALTO_DE_LA_FRANJA: Dp = 72.dp

/** Lo que hay que recorrer a la derecha para que cuente como "abrir el menú". */
val DISTANCIA_PARA_ABRIR: Dp = 48.dp

/**
 * Abre el menú con un deslizamiento a la derecha que NACE en la franja superior
 * (spec §2.2.1). Escucha en el pase `Initial` sin consumir nada hasta decidir:
 * un toque sobre la flecha atrás llega intacto. Un gesto que nace dentro del
 * borde de gestos del sistema ([bordeIzquierdoPx], por defecto
 * `WindowInsets.systemGestures`) es "atrás" del sistema y se ignora.
 */
@Composable
fun ZonaQueAbreElMenu(
    onAbrir: () -> Unit,
    modifier: Modifier = Modifier,
    bordeIzquierdoPx: Int? = null,
    contenido: @Composable () -> Unit
) {
    val densidad = LocalDensity.current
    val borde = bordeIzquierdoPx ?: WindowInsets.systemGestures.getLeft(densidad, LocalLayoutDirection.current)
    val franjaPx = WindowInsets.statusBars.getTop(densidad) + with(densidad) { ALTO_DE_LA_FRANJA.toPx() }
    val umbralPx = with(densidad) { DISTANCIA_PARA_ABRIR.toPx() }
    val abrir by rememberUpdatedState(onAbrir)

    Box(
        modifier = modifier.pointerInput(borde, franjaPx, umbralPx) {
            awaitEachGesture {
                val inicio = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (inicio.position.y > franjaPx || inicio.position.x < borde) return@awaitEachGesture
                var dx = 0f
                var dy = 0f
                while (true) {
                    val evento = awaitPointerEvent(PointerEventPass.Initial)
                    val cambio = evento.changes.firstOrNull { it.id == inicio.id } ?: break
                    if (!cambio.pressed) break
                    val delta = cambio.positionChange()
                    dx += delta.x
                    dy += delta.y
                    if (abs(dy) > abs(dx) && abs(dy) > viewConfiguration.touchSlop) break
                    if (dx >= umbralPx && dx > 2 * abs(dy)) {
                        cambio.consume()
                        abrir()
                        break
                    }
                }
            }
        }
    ) {
        contenido()
    }
}
```

En `MenuLateralContenedor.kt`:

1. Agregar, antes de la función, el enum:

```kotlin
/** Cómo se abre el menú con el dedo. La hamburguesa abre siempre, en los dos modos. */
enum class AperturaPorGesto {
    /** Comportamiento de hoy: `ModalNavigationDrawer` con gestos en toda la pantalla. */
    DESLIZAR_EN_TODA_LA_PANTALLA,

    /** Pantallas con flecha (spec §2.2.1): sólo desde la franja superior; el borde es "atrás". */
    DESDE_LA_BARRA
}
```

2. Firma: agregar `gesto: AperturaPorGesto = AperturaPorGesto.DESLIZAR_EN_TODA_LA_PANTALLA,` entre `onAbierto` y `contenido`.

3. En `ModalNavigationDrawer(…)`, agregar `gesturesEnabled = gesto == AperturaPorGesto.DESLIZAR_EN_TODA_LA_PANTALLA || estadoDelCajon.isOpen,` y reemplazar el cuerpo `{ contenido(abrir) }` por:

```kotlin
    ) {
        if (gesto == AperturaPorGesto.DESDE_LA_BARRA) {
            // `fillMaxSize`: la franja cubre el tope de la pantalla aunque el contenido
            // todavía esté en su estado de carga y mida menos.
            ZonaQueAbreElMenu(onAbrir = abrir, modifier = Modifier.fillMaxSize()) { contenido(abrir) }
        } else {
            contenido(abrir)
        }
    }
```

con el import `androidx.compose.foundation.layout.fillMaxSize`.

En `MenuDeLaApp.kt`: agregar el parámetro `gesto: AperturaPorGesto = AperturaPorGesto.DESLIZAR_EN_TODA_LA_PANTALLA,` entre `navController` y `contenido`, pasarlo como `gesto = gesto` a `MenuLateralContenedor`, e importar `com.example.msp_app.core.navegacion.ui.AperturaPorGesto`.

En `PagosRutas.kt`:

1. `destinoDeDetalleCliente`: agregar como último parámetro

```kotlin
    envoltura: @Composable (contenido: @Composable () -> Unit) -> Unit = { contenido -> contenido() }
```

y envolver la llamada `DetalleClienteScreen(…)` dentro del `composable { entrada -> … }`: `envoltura { DetalleClienteScreen(…) }` (la línea `val clienteId = …` queda fuera, antes de `envoltura`).

2. `destinosDePagos`: agregar el MISMO parámetro como último y envolver `DetalleVentaScreen(…)`: `envoltura { DetalleVentaScreen(…) }` (`val ventaId = …` queda fuera).

3. En los KDoc de las dos funciones, agregar al final:

```kotlin
 *
 * [envoltura] es la bisagra del menú lateral, igual que en
 * [destinoDeListaDeClientes]: `:app` pasa el menú con apertura desde la barra
 * superior (spec §2.2.1); el default no envuelve nada, para montar la ruta en
 * una prueba de este módulo sin saber que existe un menú.
```

En `DestinosDeCobranzaGraph.kt`, en la llamada a `destinoDeDetalleCliente(…)` (después de `ubicacion = …`) y en la llamada a `destinosDePagos(…)` (después de `ubicacion = …`), agregar:

```kotlin
        // Spec §2.2.1: pantalla con flecha → el menú se abre deslizando desde la
        // barra superior, nunca desde el borde (que es "atrás" del sistema).
        envoltura = { contenido ->
            MenuDeLaApp(navController = navController, gesto = AperturaPorGesto.DESDE_LA_BARRA) { contenido() }
        }
```

con el import `com.example.msp_app.core.navegacion.ui.AperturaPorGesto`. `destinoDeRegistrarAbono` y `destinoDeRegistrarVisita` NO se tocan (spec §2.2.2).

- [ ] **Step 4: Correr y ver verde, y contar**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest :core:navegacion:detekt :core:navegacion:ktlintFormat :core:navegacion:ktlintCheck
rm -rf feature/pagos/build/outputs/roborazzi
./gradlew --no-parallel --no-daemon :feature:pagos:testDebugUnitTest :feature:pagos:verifyRoborazziDebug :feature:pagos:detekt :feature:pagos:ktlintFormat :feature:pagos:ktlintCheck
./gradlew --no-parallel --no-daemon :app:ktlintFormat :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.*' :app:ktlintCheck
grep -ho 'tests="[0-9]*"' core/navegacion/build/test-results/testDebugUnitTest/TEST-*ZonaQueAbreElMenuTest.xml app/build/test-results/testDevlocalDebugUnitTest/TEST-*ElMenuSeAbreDesdeLaBarraTest.xml
```

Expected: BUILD SUCCESSFUL en los tres; `tests="5"` y `tests="5"`; ningún golden de `:feature:pagos` se mueve (el default de `envoltura` no envuelve). `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` sigue verde: los detalles ahora componen el menú, y sus opciones miden 56 dp.

- [ ] **Step 5: Commit**

```bash
git add core/navegacion feature/pagos app/src/main/java/com/example/msp_app/navigation app/src/test/java/com/example/msp_app/navigation/ElMenuSeAbreDesdeLaBarraTest.kt
git commit -m "feat(menu): el menu se abre deslizando desde la barra en los detalles con flecha"
```

---

### Task 17: Atrás predictivo del sistema y resorte de entrada

Ya MEDIDO (decisión D10): material3 1.3.0 trae el atrás predictivo en `ModalDrawerSheet(drawerState, …)` (en uso desde la Task 4) y `DrawerState.animateTo(…, AnimationSpec)` existe pero está `@Deprecated`. No hace falta spike; falta encender el atrás predictivo del SISTEMA (manifiesto) y el resorte corto.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:28-39` (atributo en `<application>`)
- Modify: `core/navegacion/src/main/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralContenedor.kt` (`abrir`)
- Test: `app/src/test/java/com/example/msp_app/navigation/ElAtrasPredictivoEstaEncendidoTest.kt`
- Test: `core/navegacion/src/test/kotlin/com/example/msp_app/core/navegacion/ui/MenuLateralContenedorTest.kt` (prueba nueva)

**Interfaces:**
- Consumes: `MenuLateralContenedor` (Tasks 4 y 16); `ANCHO_DEL_MENU`.
- Produces: `internal val RESORTE_DE_ENTRADA: SpringSpec<Float>` en `MenuLateralContenedor.kt`.

- [ ] **Step 1: Escribir las pruebas que fallan**

`app/src/test/java/com/example/msp_app/navigation/ElAtrasPredictivoEstaEncendidoTest.kt`:

```kotlin
package com.example.msp_app.navigation

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sin `android:enableOnBackInvokedCallback="true"` Android 14 no da el atrás
 * predictivo aunque el menú lo soporte (spec §2.1.7; decisión D10 del plan).
 */
class ElAtrasPredictivoEstaEncendidoTest {

    private val manifiesto: File =
        listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }

    @Test
    fun `la aplicacion declara enableOnBackInvokedCallback`() {
        val aplicacion = Regex("""<application\b[^>]*>""").find(manifiesto.readText())?.value
        assertNotNull("no se encontró <application> en ${manifiesto.path}: el test no miraría nada", aplicacion)
        assertTrue(aplicacion!!.contains("android:enableOnBackInvokedCallback=\"true\""))
    }
}
```

Al final de `MenuLateralContenedorTest`:

```kotlin
    /**
     * Resorte corto, no la `tween` de 256 ms por defecto. A 64 ms (4 cuadros) un
     * resorte críticamente amortiguado de rigidez media (≈1500) ya recorrió ~70 %
     * del ancho; la `tween` FastOutSlowIn va por ~16 %. El umbral 50 % separa los
     * dos con holgura (cálculo INFERIDO; el rojo/verde de este test lo vuelve MEDIDO).
     */
    @Test
    fun `la entrada con resorte recorre mas de la mitad en 64 ms y no se pasa`() {
        montar(reducido = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("abrir").performClick()

        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        val izquierda = compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot().left.value
        val recorrido = 1f - (-izquierda / ANCHO_DEL_MENU.value)
        assertTrue("recorrió ${recorrido * 100}% en 64 ms", recorrido > 0.5f)

        val posiciones = (1..60).map {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot().left.value
        }
        assertTrue("el menú rebotó más allá del borde: $posiciones", posiciones.all { it <= 0.5f })
        assertEquals(0f, posiciones.last(), 0.5f)
    }
```

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.ElAtrasPredictivoEstaEncendidoTest'
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest --tests 'com.example.msp_app.core.navegacion.ui.MenuLateralContenedorTest'
```

Expected: el primero FAIL en `assertTrue` (el atributo no existe); el segundo FAIL en `la entrada con resorte…` con "recorrió ~16% en 64 ms" (la `tween` de `open()`). Pegar ambos rojos en el reporte.

- [ ] **Step 3: Implementación mínima**

En `app/src/main/AndroidManifest.xml`, dentro de `<application`, después de `android:supportsRtl="true"`:

```xml
        android:enableOnBackInvokedCallback="true"
```

En `MenuLateralContenedor.kt`, agregar antes de la función:

```kotlin
/**
 * El resorte de entrada (spec §2.1.7): los mismos valores que
 * `MspMotion.standard()` —críticamente amortiguado, sin rebote—. Se copian los
 * dos números porque este contenedor vive FUERA de `MspTheme` (la pantalla puede
 * ser legada) y `MspMotion` tiene constructor `internal`.
 */
internal val RESORTE_DE_ENTRADA: SpringSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
```

y reemplazar la lambda `abrir` por:

```kotlin
    val abrir: () -> Unit = {
        alcance.launch {
            if (movimientoReducido) {
                estadoDelCajon.snapTo(DrawerValue.Open)
            } else {
                // `animateTo` está @Deprecated en material3 1.3.0 ("the animation spec is now an
                // implementation detail of ModalDrawer"), pero es la ÚNICA vía para un resorte;
                // medido 2026-10-06 con javap. Si una versión futura lo quita, este archivo no
                // compila y se decide ahí: `open()` sin resorte, o envolver el cajón a mano.
                @Suppress("DEPRECATION")
                estadoDelCajon.animateTo(DrawerValue.Open, RESORTE_DE_ENTRADA)
            }
        }
    }
```

con los imports `androidx.compose.animation.core.Spring`, `androidx.compose.animation.core.SpringSpec`, `androidx.compose.animation.core.spring`.

- [ ] **Step 4: Correr y ver verde; todo el módulo y las redes**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:testDebugUnitTest :core:navegacion:detekt :core:navegacion:ktlintFormat :core:navegacion:ktlintCheck
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest :app:assembleDevlocalDebug
```

Expected: BUILD SUCCESSFUL. Toda la suite de `:app` (no sólo navegación) porque el atributo del manifiesto cambia cómo se despacha atrás en API 33+.

- [ ] **Step 5: Prueba en aparato (manual, se reporta como MEDIDO o "no medido")**

Con un teléfono Android 14+ y `devlocal` (`LOCAL_API_HOST=127.0.0.1` + `adb reverse tcp:3001 tcp:3001`; NUNCA `devserver`, CLAUDE.md §4):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:installDevlocalDebug
```

Verificar y anotar con fecha: (a) con el menú abierto, el gesto atrás encoge el menú mientras se arrastra y lo cierra al soltar, sin salir de la pantalla; (b) en una pantalla legada con `BackHandler` (p. ej. Nueva venta) atrás sigue haciendo lo de antes; (c) con "Quitar animaciones" del sistema el menú aparece sin animación. Si no hay aparato disponible, el reporte dice "no medido en aparato" y la tarea NO se da por cerrada sin que el dueño lo acepte.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/test/java/com/example/msp_app/navigation/ElAtrasPredictivoEstaEncendidoTest.kt core/navegacion
git commit -m "feat(menu): atras predictivo del sistema y resorte corto al abrir el menu"
```

---

### Task 18: Retiro de `DrawerContainer` y piso de cobertura

**Files:**
- Delete: `app/src/main/java/com/example/msp_app/components/DrawerContainer.kt`
- Create: `app/src/test/java/com/example/msp_app/navigation/RetiroDelCajonViejoTest.kt`
- Modify: `app/src/test/java/com/example/msp_app/navigation/AdopcionDelMenuLateralTest.kt` (quitar el control positivo que exige el archivo del cajón)
- Modify: comentarios que nombran `DrawerContainer` (lista exacta en el Step 3)
- Modify: `core/navegacion/build.gradle.kts` (piso de kover)

**Interfaces:**
- Consumes: `EscanerDeFuentes.archivosQueMencionan`, `EscanerDeFuentes.existe(rutaRelativa: String): Boolean`.
- Produces: ninguna API nueva.

- [ ] **Step 1: Escribir la prueba que falla**

`app/src/test/java/com/example/msp_app/navigation/RetiroDelCajonViejoTest.kt`:

```kotlin
package com.example.msp_app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `DrawerContainer` se fue: ni el archivo ni una sola mención en código (spec §3.7). */
class RetiroDelCajonViejoTest {

    private val escaner = EscanerDeFuentes()

    @Test
    fun `nada en el repo menciona DrawerContainer`() {
        assertEquals(emptyList<String>(), escaner.archivosQueMencionan("DrawerContainer"))
        assertFalse(escaner.existe("app/src/main/java/com/example/msp_app/components/DrawerContainer.kt"))
    }

    /** Control positivo: el mismo escáner ve las 20 llamadas que lo reemplazaron más la definición. */
    @Test
    fun `control positivo, el escaner ve el menu nuevo en todas las pantallas`() {
        val conMenu = escaner.archivosQueMencionan("MenuDeLaApp")
        assertTrue("el escáner vio ${conMenu.size} archivos con MenuDeLaApp", conMenu.size >= 20)
    }
}
```

En `AdopcionDelMenuLateralTest`, en `control positivo, el escaner ve la definicion del menu y la del cajon`, borrar la línea que exige `components/DrawerContainer.kt` y renombrar el test a `` `control positivo, el escaner ve la definicion del menu` ``.

- [ ] **Step 2: Correr y ver que falla**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.RetiroDelCajonViejoTest'
```

Expected: FAIL — `nada en el repo menciona DrawerContainer` lista `…/components/DrawerContainer.kt`.

- [ ] **Step 3: Borrar el cajón y limpiar menciones**

```bash
git rm app/src/main/java/com/example/msp_app/components/DrawerContainer.kt
grep -rn "DrawerContainer" app/src feature core --include='*.kt' | grep -v "/build/"
```

Para cada línea que imprima el `grep` (son comentarios y KDoc: `PagosRutas.kt` KDoc de `destinoDeListaDeClientes`, `AppNavigation.kt:719-720`, y los fixtures de Firebase en `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest.kt`, `LaHamburguesaDeClientesAbreElMenuLateralTest.kt`, `ElMenuSeAbreDesdeLaBarraTest.kt`), reescribir la frase nombrando `MenuDeLaApp` (o "el menú lateral" en `:feature:pagos`, que no puede nombrar símbolos de `:app`) y, en los fixtures, "el pie del menú (`PieDelMenu`) pinta `UpdateBanner`". Volver a correr el `grep` hasta que no imprima nada.

- [ ] **Step 4: Correr y ver verde**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :app:ktlintFormat :app:testDevlocalDebugUnitTest --tests 'com.example.msp_app.navigation.*' :app:assembleDevlocalDebug :app:ktlintCheck :feature:pagos:ktlintCheck
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Medir la cobertura de `:core:navegacion` y fijar el piso trinquete**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:koverXmlReportDebug
python3 - <<'EOF'
import xml.etree.ElementTree as ET, glob
f = glob.glob("core/navegacion/build/reports/kover/reportDebug.xml")[0]
c = [x for x in ET.parse(f).getroot().findall("counter") if x.get("type") == "LINE"][0]
cubiertas, perdidas = int(c.get("covered")), int(c.get("missed"))
pct = 100 * cubiertas / (cubiertas + perdidas)
print(f"LINE {cubiertas}/{cubiertas + perdidas} = {pct:.2f}% -> piso {int(pct) - 3}")
EOF
```

Expected: imprime una línea `LINE a/b = p% -> piso N`. En `core/navegacion/build.gradle.kts`, reemplazar el comentario del placeholder por el bloque siguiente, escribiendo en lugar de `N` el entero que imprimió el script y en lugar de `p`/`a/b` los valores medidos:

```kotlin
// Piso de cobertura REAL (reemplaza el placeholder 0% de `msp.kover`).
// MEDIDO <fecha de hoy> con `:core:navegacion:koverXmlReportDebug`: LINE a/b = p%.
// Piso = medido - 3: trinquete, no meta.
kover {
    reports {
        verify {
            rule("core-navegacion: piso trinquete (medido p% LINE)") {
                minBound(N)
            }
        }
    }
}
```

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew --no-parallel --no-daemon :core:navegacion:koverVerifyDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: La compuerta completa, en frío**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
rm -rf app/build
for m in core/navegacion core/designsystem feature/pagos feature/configuracion feature/collectionReport; do rm -rf "$m/build/outputs/roborazzi"; done
./gradlew --no-parallel --no-daemon prePushCheck --rerun-tasks
git status --porcelain | grep '\.png$'
```

Expected: BUILD SUCCESSFUL; el `grep` de PNG no imprime nada (ningún golden se movió en la compuerta). Si `prePushCheck` sale rojo por `Wrong plugin option format: null` o `ksp…Kotlin`, reintentar hasta 3 veces.

- [ ] **Step 7: Prueba en aparato de la spec §6 (manual)**

Con `devlocal` en el teléfono (nunca `devserver`): abrir el menú desde Inicio, Clientes, Reporte, Mapa, Ventas, Mi Camioneta, Catálogo, Traspasos, Asignación, Garantías, Nueva garantía, Sin módulos (usuario sin módulos), detalle de venta legado, descripción de venta, ticket de pago, ticket de visita, detalle de producto (hamburguesa) y detalle de cliente / detalle de venta de cobranza (deslizar desde la barra). En cada una anotar: la sección correcta llega abierta, la opción correcta resaltada, "Clientes" muestra el mismo número que el chip "Volver" de la lista, "Ventas" el número de ventas sin subir, Traspasos y Reporte sin número, y que Registrar abono / Registrar visita no abren menú al deslizar. Lo no verificado se reporta como "no medido".

- [ ] **Step 8: Commit**

```bash
git add -A app/src core/navegacion/build.gradle.kts feature/pagos/src
git commit -m "refactor(menu): retira DrawerContainer y fija el piso de cobertura del menu lateral"
```

---

## Auto-revisión (hecha al escribir el plan)

**Cobertura de la spec.**
- §2.1.1 cabecera + estado de envío → Tasks 1, 3, 6, 7 (rol: D11).
- §2.1.2 secciones plegables recordadas, sección actual abierta sola, contadores en vivo, desvanecido → Tasks 1, 3, 8, 10.
- §2.1.3 buscador con ≥10 opciones contando plegadas → Tasks 1, 3.
- §2.1.4 pie Configuración + versión; "Cerrar sesión" a Configuración con el diálogo reubicado → Tasks 3, 9, 10.
- §2.1.5 resaltado `brandTint`/`brand` → Task 3 (y goldens Task 5).
- §2.1.6 56 dp → Tasks 3, 11 (geometría en el `NavHost` real), 9 (botón de sesión).
- §2.1.7 resorte, atrás predictivo, movimiento reducido → Tasks 4, 17.
- §2.2.1 deslizar desde la barra → Task 16; §2.2.2 abono/visita sin menú → Task 16.
- §3.1 módulo `:core:navegacion` con capas → Tasks 1–4; §3.5 adaptadores en `:app` → Tasks 7, 8 (D1, D3).
- §3.6 adopción por pantalla → Tasks 11–16 (20 llamadas, inventario cerrado en la Task 15).
- §3.7 retiro → Task 18.
- §4 fuentes de contadores → Tasks 6, 7, 8 (D7, D8, D13); Traspasos sin número → Task 8.
- §5 tokens, escala 1.0–2.0, punto con texto → Tasks 3, 5.
- §6 pruebas unitarias, Compose, goldens, geometría, aparato → Tasks 1–5, 11, 17, 18.

**Placeholders.** Ningún "TBD" ni "similar a la Task N". El único valor que se llena al ejecutar es MEDIDO por un comando dado en el mismo paso (el piso de kover de la Task 18). Corregido en esta revisión: el `combine` del ViewModel esperaba la primera lectura de envío antes de publicar contadores (se agregó `onStart { emit(null) }`); la franja de deslizar medía lo que el contenido en carga (se agregó `fillMaxSize`); el conteo de pruebas de Configuración dependía de un número previo no medido (ahora cuenta los 4 casos nuevos por nombre).

**Consistencia de tipos.** `ContadoresDelMenuPort.contadores(ids: Set<String>)` (Task 2) = fake (Task 2) = adaptador (Task 8) = `alAbrir(ids)` en `MenuDeLaApp` (Task 10). `MenuLateralContenedor(hoja, onAbierto, gesto, contenido)`: `gesto` entra con default en la Task 16 y las llamadas de las Tasks 4/10 siguen compilando. `envoltura` de los detalles es `@Composable (@Composable () -> Unit) -> Unit` en `PagosRutas` y en `DestinosDeCobranzaGraph`. `FILA_DEL_MENU_TAG + ID_CONFIGURACION` es el tag que usan `PieDelMenu` (Task 3) y la geometría (Task 11).

**Review Focus.** Las cinco entradas tienen su prueba en la tarea dueña: 1 → Task 4; 2 → Task 10; 3 → Tasks 3 y 4; 4 → Tasks 1 y 10; 5 → Task 7.
