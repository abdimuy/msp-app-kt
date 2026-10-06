# Menú lateral nuevo (opción A) — diseño

- **Fecha:** 2026-10-05
- **App:** msp-app-kt
- **Mock:** opción A — https://claude.ai/artifact/V2UVAaUMzhCjM2m1NxYxs1
- **Estado:** decisiones cerradas por el dueño (2026-10-05) + propuestas de adopción pendientes de confirmar

## 1. Objetivo y alcance

Reemplazar el cajón actual (`DrawerContainer`,
`app/src/main/java/com/example/msp_app/components/DrawerContainer.kt`) por un
menú lateral con cabecera de identidad + estado de envío, secciones plegables
con contadores en vivo, y Configuración/Cerrar sesión unificados en el pie.

**Incluye:**
- El componente visual del menú nuevo (arquitectura nueva, módulo
  `:core:*` — ver §3).
- Los puertos de datos que el menú necesita y su adaptador en `:app`.
- Cómo lo adopta cada pantalla existente y el plan para retirar
  `DrawerContainer`.
- Las fuentes reales de cada contador (o la constancia de que no existen).

**No incluye:**
- Navegación por voz (rechazada por el dueño).
- Reordenar las opciones del menú según uso (rechazada por el dueño).
- Implementar los contadores que hoy no tienen fuente (se deja declarado qué
  falta, no se inventa el dato).
- El trabajo de "hamburguesa en Clientes con `onAbrirMenu`", que es de **otra
  tarea** ya en curso — aquí sólo se registra el estado actual verificado en
  código (§4 de abajo) para que esta spec no lo contradiga.

## 2. Decisiones y Por confirmar

### 2.1 Decisiones (cerradas por el dueño, 2026-10-05)

1. **Cabecera fija:** avatar con iniciales, nombre, rol y ruta; más una
   tarjeta de estado de envío: "Todo enviado · hace N min" (punto verde) o
   "N pagos esperando señal" (punto ámbar).
2. **Cuerpo con scroll:** secciones por módulo (COBRO, VENTAS, ALMACÉN,
   GARANTÍAS) plegables; la app recuerda qué secciones quedaron plegadas; la
   sección de la pantalla actual se abre sola. Cada opción lleva ícono, texto
   y un contador opcional en vivo (ej. Clientes "8 por volver", Reporte
   "$4,350" cobrado hoy, Ventas "1 sin subir", Traspasos "3"). Desvanecido
   (fade) abajo cuando hay más contenido por desplazar.
3. **"Buscar en el menú"** sólo aparece con 10 opciones o más (contando todas
   las secciones visibles para ese usuario, plegadas o no).
4. **Pie fijo:** Configuración y versión. "Cerrar sesión" se mueve dentro de
   Configuración, con confirmación (el diálogo que hoy vive en
   `DrawerContainer.kt:362-381` se reubica, no se reescribe).
5. **Resaltado de la opción activa:** fondo `MspTheme.colors.brandTint`, texto
   `MspTheme.colors.brand` — tokens ya existentes
   (`core/designsystem/src/main/kotlin/com/example/msp_app/core/designsystem/theme/MspColors.kt:35,79,82`),
   sin tokens nuevos.
6. **Altura mínima de los ítems:** el mock pide 48 dp. **Esto contradice la
   regla dura del repo** — "Toques ≥ 50 dp — regla del repo, más estricta que
   los 48 de Material. No se baja nunca"
   (`docs/superpowers/plans/2026-09-17-principios-cobranza-2026.md:71-72`),
   cobrada por `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` sobre **todo**
   control tocable del grafo. El design system ya tiene el token para esto:
   `MspTheme.spacing.touchTarget = 56.dp`
   (`core/designsystem/src/main/kotlin/com/example/msp_app/core/designsystem/theme/MspSpacing.kt:29`).
   **Esta spec resuelve el conflicto a favor de la regla del repo:** los
   ítems del menú usan `MspTheme.spacing.touchTarget` (56 dp), no los 48 dp
   del mock. No es una propuesta por confirmar — es una regla que "no se baja
   nunca", y bajarla rompería la compuerta automática existente.
7. **Movimiento:** entrada con resorte corto (spring); "atrás predictivo" de
   Android 14+; con "Quitar animaciones" del sistema (`LocalReduceMotion`,
   ya usado en el repo — ver `rememberMspReducedMotion()` en
   `core/designsystem/src/main/kotlin/com/example/msp_app/core/designsystem/theme/MspMotion.kt:104-105`),
   sin animación.
8. **Rechazado:** navegación por voz; reordenar opciones según uso.

### 2.2 Decididas el 2026-10-06 (antes "por confirmar")

1. **Decidido (2026-10-06):** en pantallas con flecha atrás (detalle de cliente, detalle de venta,
   tickets) el menú se abre deslizando hacia la derecha desde la barra
   superior, no desde el borde — con navegación por gestos el borde izquierdo
   de la pantalla ya significa "atrás" y un menú ahí competiría con ese
   gesto.
2. **Decidido (2026-10-06):** Registrar abono y Registrar visita **no** abren el menú: son formularios a
   medio capturar y un gesto accidental no debe desviar al cobrador de una
   captura en curso.

## 3. Arquitectura

### 3.1 Dónde vive

Módulo nuevo **`:core:navegacion`**, agregado a `settings.gradle.kts` (hoy
sólo existen `:core:appgate`, `:core:common`, `:core:database`,
`:core:designsystem`, `:core:geo`, `:core:network`, `:core:printing`,
`:core:settings`, `:core:speech`, `:core:telemetry`, `:core:upload`,
`:core:testing` — ningún módulo de navegación existe todavía).

Por qué no en `:core:designsystem`: el menú no es un componente visual puro
— necesita datos que cruzan módulo (módulos del usuario, contadores de
`:feature:pagos`, `:feature:collectionReport`, ventas locales, sync), y
`:core:designsystem` no depende de ningún `:feature:*` ni de
`:core:database`/`:core:network` (regla de capas: un design system puro no
jala datos). Un módulo de navegación dedicado sí puede declarar los puertos
que necesita sin ensuciar el design system — cumple el criterio de Ruling BF
("un puerto se justifica con ≥2 implementaciones, cruza módulo, o el
contrato de capas lo exige",
`docs/superpowers/plans/2026-09-17-principios-cobranza-2026.md:81-83`): aquí
el motivo es "cruza módulo".

Capas, mismo patrón que `:feature:pagos` / `:feature:visitas`
(`feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/{domain/model,domain/port,application,ui,ui/components,di}`):

```
core/navegacion/
  domain/model/   ElementoDelMenu, SeccionDelMenu, EstadoDeEnvio, CabeceraDelMenu
  domain/port/    ModulosDelUsuarioPort, ContadoresDelMenuPort,
                  EstadoDeEnvioPort, SeccionesPlegadasPort
  application/    EstadoDelMenu (combina los puertos en un solo StateFlow para la UI)
  ui/             MenuLateral (el composable) + MenuLateralViewModel
  ui/components/  CabeceraDelMenu, SeccionPlegable, FilaDelMenu, BuscadorDelMenu
```

Sin `data/adapter/` dentro del módulo: por la regla 17 del repo ("cuando el
adaptador necesita algo que sólo vive en `:app`, el puerto se queda en el
módulo y el adaptador se va a `:app`",
`docs/superpowers/plans/2026-09-17-principios-cobranza-2026.md:76-77`, mismo
patrón que `TemaDeLaAppPort`/`LiquidacionPort`) — los cuatro puertos de
arriba sólo se pueden implementar leyendo `AuthViewModel` (`:app`), Room DAOs
de varios módulos y `BuildConfig` (`:app`), así que sus adaptadores viven en
`:app`, no en `:core:navegacion`.

### 3.2 Modelo de datos (firma tentativa)

```kotlin
// core/navegacion/domain/model

data class ElementoDelMenu(
    val id: String,                 // "clientes", "reporte_cobranza", "traspasos", ...
    val etiqueta: String,
    val icono: ImageVector,         // Lucide, igual que UpdateBanner.kt y el resto del design system
    val contador: String? = null,   // YA formateado ("8 por volver", "$4,350") — el menú sólo lo pinta
    val resaltarContador: Boolean = false,
    val onClick: () -> Unit
)

data class SeccionDelMenu(
    val id: String,                 // "COBRO" | "VENTAS" | "ALMACEN" | "GARANTIAS"
    val titulo: String,
    val items: List<ElementoDelMenu>
)

data class EstadoDeEnvio(
    val estado: Estado,
    val pendientes: Int,            // 0 cuando TODO_ENVIADO
    val ultimoEnvioHaceMinutos: Long? // null cuando ESPERANDO_SENAL (no aplica "hace N min")
) {
    enum class Estado { TODO_ENVIADO, ESPERANDO_SENAL }
}

data class CabeceraDelMenu(
    val iniciales: String,
    val nombre: String,
    val rol: String,
    val ruta: String,
    val envio: EstadoDeEnvio
)
```

### 3.3 Composable (firma tentativa)

```kotlin
// core/navegacion/ui/MenuLateral.kt

@Composable
fun MenuLateral(
    cabecera: CabeceraDelMenu,
    secciones: List<SeccionDelMenu>,
    seccionActivaId: String?,         // la de la pantalla actual -> se abre sola, sin tocar la preferencia guardada
    itemActivoId: String?,            // resaltado brandTint/brand
    seccionesPlegadas: Set<String>,   // lo que el usuario dejó plegado (persistido)
    onTogglePlegado: (String) -> Unit,
    onBuscar: ((String) -> Unit)?,    // null si el total de opciones < 10 (regla 2.1.3)
    pie: @Composable () -> Unit,      // Configuración + "Versión x.y.z" — BuildConfig vive en :app, por eso se inyecta
    modifier: Modifier = Modifier
)
```

El pie se inyecta como slot (`@Composable () -> Unit`) en vez de resolverse
dentro del módulo porque `BuildConfig.VERSION_NAME`
(`DrawerContainer.kt:347`) sólo existe en `:app` — mismo motivo por el que
`:core:designsystem` nunca importa `BuildConfig`.

### 3.4 Puertos (domain/port)

```kotlin
interface ModulosDelUsuarioPort {
    fun modulos(): Flow<List<String>>   // hoy: User.MODULOS, vía AuthViewModel.userData
}

interface ContadoresDelMenuPort {
    fun contadores(): Flow<Map<String, String>>  // id de ElementoDelMenu -> texto ya formateado
}

interface EstadoDeEnvioPort {
    fun estado(): Flow<EstadoDeEnvio>
}

interface SeccionesPlegadasPort {
    fun plegadas(): Flow<Set<String>>
    suspend fun setPlegada(seccionId: String, plegada: Boolean)
}
```

### 3.5 Adaptador en `:app`

Un único módulo Hilt (`MenuLateralAdapterModule`, en `:app`) implementa los
cuatro puertos:

- `ModulosDelUsuarioPort` → envuelve `AuthViewModel.userData`
  (`DrawerContainer.kt:59-63`, misma fuente que ya decide COBRO/VENTAS/
  ALMACÉN/GARANTÍAS).
- `ContadoresDelMenuPort` → combina los Flows descritos en §4. Donde §4 dice
  "sin fuente identificada", ese `id` simplemente no aparece en el mapa (el
  ítem se pinta sin contador, no con un placeholder inventado).
- `EstadoDeEnvioPort` → combina conteos de pendientes (`LocalSaleEntity
  .ENVIADO = 0`, `PaymentEntity.GUARDADO_EN_MICROSIP = 0`) con
  `CobranzaSyncStateEntity.LAST_SYNCED_AT` para el "hace N min" (ver §4).
- `SeccionesPlegadasPort` → extiende `:core:settings` (el mismo
  `SettingsRepository` sobre DataStore que ya persiste `font_size_level` /
  `privacy_masked` /
  `reduce_motion`,
  `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md:43`),
  con una clave nueva `menu_secciones_plegadas: Set<String>`. No se crea un
  `:core:settings` paralelo.

### 3.6 Adopción por pantalla

| Pantalla | Hoy | Plan |
|---|---|---|
| Home (`features/home/screens/Home.kt:316-333`) | `DrawerContainer` + hamburguesa | Hamburguesa → `MenuLateral` |
| Clientes (`ListaDeClientesScreen`, `feature/pagos/.../ListaDeClientesScreen.kt:204-260`) | **Sin menú ni hamburguesa** — comentario explícito en el código: "Sin botón de volver: es pantalla de NIVEL SUPERIOR — se llega desde el cajón" (línea 232-235), pero el `Row` del encabezado (líneas 239-260) sólo tiene título + ojo de privacidad + tema, **ningún botón de menú** | Hamburguesa nueva. La tarea paralela ya en curso conecta un `onAbrirMenu` al `DrawerContainer` viejo mientras este menú no exista; cuando `MenuLateral` esté listo, ese mismo callback apunta para allá — un solo punto de cableado, sin tocar la pantalla dos veces |
| Reporte de cobranza (`AppNavigation.kt:542-552`, destino `daily_reports`), Mapa de rutas (`features/routemap/RouteMapScreen.kt:138-150`, destino `route_map`) — ambos entran desde `DrawerContainer.kt:169-193` | `DrawerContainer` | Hamburguesa → `MenuLateral` |
| Ventas: `SaleHomeScreen` (`features/productsInventory/screens/BlankScreen.kt:108-118`), `SaleDetailsListScreen` (`features/sales/screens/SalesListScreen.kt:76-90`), `UnifiedSalesScreen.kt:212-223`, `NewSaleScreen.kt:650-660`; Mi Camioneta (`CartScreen.kt:235-272`); Catálogo (`ProductsCatalogScreen.kt:115-128`) y detalle de producto ya listado abajo; Traspasos (`TransfersListScreen.kt:112-118`); Asignación de camionetas (`CamionetaAssignmentScreen.kt:103-114`); Garantías (`GuaranteeListScreen.kt:45-51`, `CreateGuaranteeScreen.kt:66-72`) | `DrawerContainer` + hamburguesa en `TopAppBar`/encabezado | Hamburguesa → `MenuLateral`, mismo lugar |
| Detalle de venta, descripción de venta, ticket de pago, ticket de visita, detalle de producto (`SaleDetailsScreen.kt:89-184`, `SaleDescriptionScreen.kt:76-88`, `PaymentTicketScreen.kt:167-329`, `VisitTicketScreen.kt:140-188`, `ProductDetailsScreen.kt:218-230`) | `DrawerContainer` detrás de una flecha atrás que ya reemplaza al botón de menú en el lugar visual | **Decidido (§2.2.1):** deslizar desde la barra superior en vez de hamburguesa |
| Detalle de cliente (`destinoDeDetalleCliente`, sin `DrawerContainer` — usa `onAtras = popBackStack()`, `DestinosDeCobranzaGraph.kt:73-74`) | Sin menú (flecha atrás) | **Decidido (§2.2.1):** mismo deslizamiento |
| Registrar abono, Registrar visita (`destinoDeRegistrarAbono`, `destinoDeRegistrarVisita`, `DestinosDeCobranzaGraph.kt:242-309`) | Sin menú | **Decidido (§2.2.2):** se quedan sin menú a propósito |
| Sin módulos (`NoModulesScreen.kt:38-50`) | `DrawerContainer` | Hamburguesa → `MenuLateral` (sólo pie: Configuración/salir, sin secciones porque no hay módulos) |

### 3.7 Retiro de `DrawerContainer`

Fase final, después de que **las 19 pantallas** que hoy llaman
`DrawerContainer(` (lista verificada con
`grep -rn "DrawerContainer(" app/src/main` — 19 sitios de uso fuera de la
propia definición) adopten `MenuLateral`. El orden sugerido: primero las de
nivel superior (Home, Clientes, Reporte, Mapa — las que hoy abren por
hamburguesa desde cero o la reemplazan), después el resto por módulo
(VENTAS, ALMACÉN, GARANTÍAS), dejando al final las de §2.2 (deslizar desde la barra / sin menú). Cuando el
último llamador se mueva, se borra
`app/src/main/java/com/example/msp_app/components/DrawerContainer.kt`
completo (incluye el diálogo de "Cerrar sesión", que para entonces ya vive en
Configuración).

## 4. Contadores: de dónde sale cada uno

| Contador | Fuente verificada en código | Estado |
|---|---|---|
| Clientes "N por volver" | `ListaDeClientesUiState.conteos: Map<SegmentoDeCobranza, Int>` (`feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/ui/ListaDeClientesViewModel.kt:34`), con la llave `SegmentoDeCobranza.VOLVER_A_VISITAR` (`feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/ui/SegmentoDeCobranza.kt:63`) | Fuente existe, pero sólo dentro del `ViewModel` de la pantalla. Falta exponerla como un puerto liviano (sin cargar toda la lista) para que `ContadoresDelMenuPort` la consuma sin duplicar la consulta |
| Reporte "$X cobrado hoy" | `ReportAggregator` (`feature/collectionReport/src/main/kotlin/com/example/msp_app/feature/collectionreport/domain/ReportAggregator.kt`), que produce `DayTrend.total`/`TimelineBucket.total` (`Money`) por `ReportPeriod`. El mismo cálculo que ya pinta el reporte para "hoy" | Fuente existe; falta una llamada específica "total de hoy" sin construir todo el reporte |
| Ventas "N sin subir" | `LocalSaleEntity.ENVIADO: Boolean` (`core/database/src/main/kotlin/com/example/msp_app/core/database/entities/LocalSaleEntity.kt:32`). Ya hay predicados `WHERE ENVIADO = :enviado` en `LocalSaleDao` (p. ej. línea 114, 302-303, 696), pero **no existe** una consulta `COUNT(*)` sobre ese filtro — se verificó con `grep` sobre el DAO completo y no aparece ningún `countByEnviado` | Columna existe, consulta de conteo no — hay que agregarla |
| Traspasos "N" | Se revisó `TransfersListScreen.kt` y su `ViewModel`: la pantalla es un dashboard de almacenes (listar/crear traspasos), no expone ningún conteo de "traspasos pendientes de algo". `grep` sobre `features/transfers/**` no encontró un campo de conteo | **Sin fuente identificada** — no se inventa el número; el ítem del mock queda sin contador hasta que alguien defina qué cuenta "3" |
| Estado de envío (cabecera) | Pendientes: combinación de `LocalSaleEntity.ENVIADO = 0` y `PaymentEntity.GUARDADO_EN_MICROSIP: Boolean` (`core/database/src/main/kotlin/com/example/msp_app/core/database/entities/PaymentEntity.kt:29`) en `false`. "hace N min": `CobranzaSyncStateEntity.LAST_SYNCED_AT` (`core/database/src/main/kotlin/com/example/msp_app/core/database/entities/CobranzaSyncStateEntity.kt:16-17,28`), que ya guarda cuándo llegó la última página exitosa, por recurso (`ventas`/`pagos`) | Fuentes existen por separado; falta la agregación (mínimo de los dos `LAST_SYNCED_AT`, suma de los dos conteos de pendientes) en un solo puerto |

Ningún contador de esta tabla se implementa en esta spec — es un documento de
diseño, no de código. Lo que sí es exigible: ningún contador se muestra con
un número que no salga de una de estas fuentes, y "Traspasos" no se pinta
con un número inventado.

## 5. Accesibilidad y letra grande

- El menú usa `MspTheme.type.*` (Manrope) y `MspTheme.spacing.*`, nunca un
  `TextStyle`/`dp` suelto — misma regla que el resto del design system
  (`MspColors.kt:11-15`, `MspType.kt:16-20`).
- Escala de letra: el menú no implementa su propia lógica de tamaño. Hereda
  el mecanismo ya decidido en
  `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`
  (override de `LocalDensity` en la raíz = máx(nivel elegido en la app,
  `fontScale` del OS), niveles Normal/Grande/Muy grande = 1.0/1.5/2.0). A
  1.0-2.0, el texto del ítem puede envolver a dos líneas; la altura mínima
  (`touchTarget`, 56 dp) es un **mínimo**, no un techo — el ítem crece si el
  texto lo pide, nunca se trunca con `maxLines` agresivo.
- Tema claro/oscuro: todos los colores salen de `MspTheme.colors` (sección
  2.1.5); no hay valores de color nuevos que decidir — `mspLightColors()` /
  `mspDarkColors()` ya cubren `brand`, `brandTint`, `surface`, `onSurface`,
  `statusPaid` (verde del punto "Todo enviado") y `statusPartial` (ámbar de
  "esperando señal") en los dos temas
  (`MspColors.kt:78-110`, `125-157`).
- El punto de color del estado de envío **nunca es el único portador de
  significado** — va acompañado del texto ("Todo enviado" / "N pagos
  esperando señal"), siguiendo la regla ya escrita para `MspStatusChip`
  (`MspColors.kt:25-28`).

## 6. Pruebas

- **Unitarias (`application/`, `domain/`):** `EstadoDelMenu` combina los
  cuatro puertos correctamente (fakes, sin MockK — regla del repo,
  `CLAUDE.md` §"Fakes-only, sin MockK"); `SeccionesPlegadasPort` persiste y
  lee; el cálculo de "qué sección se abre sola" a partir de la ruta activa
  (casos: ruta de una sección con módulo, ruta sin módulo visible, ruta que
  no pertenece a ninguna sección).
- **Compose:** `MenuLateral` resalta el ítem activo; plegar/desplegar una
  sección llama `onTogglePlegado` con el id correcto; el buscador aparece
  sólo con ≥10 opciones y desaparece por debajo; el desvanecido inferior se
  pinta cuando hay contenido fuera de vista.
- **Goldens Roborazzi:** claro y oscuro, en los tres niveles de letra
  (Normal/Grande/Muy grande — `FontSizeLevel`, no el 1.3 crudo que usa otro
  módulo, regla 24 de
  `docs/superpowers/plans/2026-09-17-principios-cobranza-2026.md:95-97`), con
  al menos una sección plegada y otra desplegada, y con el estado de envío en
  sus dos variantes (verde/ámbar). Se miran como imagen, no sólo "test
  verde" (`docs/superpowers/plans/2026-09-17-principios-cobranza-2026.md`
  §24, y `reference_roborazzi_verde_no_prueba_nada.md`).
- **Geometría:** extender
  `app/src/test/java/com/example/msp_app/navigation/CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest.kt`
  (o su equivalente) para que cubra también los ítems del menú nuevo —
  ninguno por debajo de 56 dp (`touchTarget`), igual que el resto del grafo.
- **Prueba en aparato:** abrir el menú desde cada pantalla de la tabla §3.6,
  confirmar que la sección correcta llega abierta, que los contadores
  disponibles (los de §4 con fuente) muestran un número real (no un
  placeholder), que "atrás predictivo" funciona en Android 14+, y que
  "Quitar animaciones" del sistema corta el resorte de entrada.

## 7. Riesgos y orden de entrega

### Riesgos

- **Conflicto 48dp vs 50dp (§2.1.6):** ya resuelto a favor del repo; el
  riesgo es que una implementación futura copie el número del mock sin leer
  esta nota.
- **Contadores sin fuente o sin consulta de conteo (§4):** Traspasos no
  tiene fuente; Ventas y Reporte necesitan consultas nuevas que no existen
  hoy. Riesgo de que se construyan como un conteo en memoria sobre datos ya
  cargados en lugar de una consulta a la base — más lento y más fácil de
  desincronizar del contador real que ve la propia pantalla.
- **Atrás predictivo:** no hay precedente en el repo (`grep` sobre
  `BackHandler`/`PredictiveBack` sólo encuentra el `BackHandler` simple de
  Android, no la API de predictivo con progreso); hay que verificar si
  `ModalNavigationDrawer` de Material 3 ya lo soporta o si hay que envolverlo
  a mano.
- **Doble escritura de "sección plegada" vs "sección abierta por pantalla
  actual":** si la preferencia persistida y el auto-abrir por ruta activa no
  se mezclan con cuidado, una sección que el usuario plegó a propósito podría
  reabrirse sola cada vez que esa pantalla es la actual, y viceversa. El plan
  de pruebas (§6) cubre los casos, pero el diseño de `EstadoDelMenu` debe
  tratarlo como dos señales independientes (preferencia vs. "forzado abierto
  por ruta"), no una sola bandera.
- **Migración de 19 llamadores + 2 pantallas nuevas sin menú hoy:** cualquier
  pantalla que se quede a medio migrar deja al cobrador sin manera de volver
  al menú. La tabla §3.6 y la fase de retiro (§3.7) existen para que no se
  borre `DrawerContainer` antes de que el último llamador se mueva.

### Orden de entrega (fases)

1. **Infra:** `:core:navegacion` (modelo + puertos + `MenuLateral` puro,
   sin datos reales — fakes en los tests), extender `:core:settings` con
   `menu_secciones_plegadas`.
2. **Adaptador en `:app`:** los cuatro puertos con las fuentes de §4 que ya
   existen (módulos del usuario, estado de envío combinado). Los contadores
   sin consulta de conteo (Ventas, Reporte) o sin fuente (Traspasos) entran
   sin contador en esta fase — no bloquean el lanzamiento del menú.
3. **Adopción, pantalla por pantalla**, en el orden de §3.7 — empezando por
   Home y Clientes (la de la tarea paralela del `onAbrirMenu`).
4. **Contadores pendientes:** agregar las consultas de conteo que faltan
   (Ventas "sin subir", Reporte "cobrado hoy") conforme se prioricen; definir
   qué cuenta "Traspasos" antes de pintarle un número.
5. **Decisión de §2.2** (deslizar desde la barra / sin menú en captura) y su
   implementación en las pantallas correspondientes.
6. **Retiro de `DrawerContainer`** una vez las 19+2 pantallas migraron.
