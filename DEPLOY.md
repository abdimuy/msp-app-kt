# Despliegue a Producción

## 0. Correr la suite

```bash
./gradlew test detekt
```

`test` corre los unit tests de **todos** los módulos sobre sus variants debug.
Los variants release no tienen tarea de test a propósito: la suite es
Robolectric y los tests de Compose necesitan
`androidx.compose.ui:ui-test-manifest`, que es debug-only por diseño (declara
`ComponentActivity` y no debe viajar en el APK que instalan los cobradores).
El source set `src/test` es uno solo, así que la debug corre exactamente los
mismos tests. No busques `testProdReleaseUnitTest`: no existe.

## 0.1 Compuerta manual: **impresión térmica con impresora real**

> **Obligatoria antes de soltar cualquier build que toque tickets.**
> La impresión térmica **no se puede probar en emulador** y ninguna prueba
> automatizada la cubre: no hay nada que simule una impresora en este repo, a
> propósito. Lo que sí está automatizado —la regla del día del cobro y el
> registro de reimpresiones— corre en `prePushCheck`; esto es lo que queda.

**Preparación:** una impresora térmica de 58 mm emparejada por Bluetooth (probado
con PT-210), un **abono registrado hoy** y una **visita registrada hoy** en el
teléfono.

| # | Qué hacer | Qué tiene que pasar |
|---|---|---|
| 1 | Abrir el ticket de pago del abono de hoy y tocar **imprimir ticket** sin impresora recordada | Aparece la lista de emparejadas; al elegir una, sale el papel |
| 2 | Mirar el papel | 32 columnas, **sin acentos rotos ni cuadritos**; los importes pegados al borde derecho; ninguna línea partida |
| 3 | Comparar el papel con la vista previa de la pantalla | **Idéntico renglón por renglón** |
| 4 | Volver a tocar **imprimir ticket** | La banda dice `copia ya impresa`; el segundo papel trae `*** REIMPRESION ***` con `copia 2 - primera <hora>` |
| 5 | Tocar **cambiar impresora** con una impresión ya hecha | El picker abre y permite cambiar sin salir de la pantalla |
| 6 | Tocar **cerrar** con el picker abierto y con el aviso de fallo | Los dos se cierran y la pantalla vuelve al ticket |
| 7 | Apagar el Bluetooth y tocar imprimir | Banda roja `no se imprimió` + `activa el bluetooth`; **el conteo NO sube** (el papel siguiente no se marca como copia extra) |
| 8 | Alejar o apagar la impresora a media impresión | Mensaje `no se envió el ticket`, sin congelamiento (el adapter tiene su timeout de 8 s) |
| 9 | **Cerrar la app por completo y reabrir** el mismo ticket | Sigue diciendo `copia ya impresa` — el registro sobrevivió al reinicio |
| 10 | **Cambiar la fecha del teléfono al día siguiente** y abrir el mismo ticket | Banda `fuera del día · solo se imprime hoy`, CTA **gris y sin sombra**, y tocarlo no imprime nada |
| 11 | Con el ticket ABIERTO, cambiar la fecha del teléfono al día siguiente y **entonces** tocar imprimir | No sale papel; la pantalla pasa sola a `fuera del día`. Es el caso de las 23:58/00:01 |
| 12 | Repetir 1-11 con el **ticket de visita** | Mismo comportamiento; además el texto del papel corresponde al desenlace registrado (una promesa **no** imprime la carta de cobranza dura) |
| 13 | Poner el tamaño de letra en **muy grande** y abrir los dos tickets | Las cifras del resumen se leen completas; el facsímil no se sale de la pantalla |
| 14 | Papel a punto de acabarse | El ticket sale hasta donde alcanza; la app no se cae |

**Límite conocido del registro de reimpresiones:** vive en `SharedPreferences`
(mismo directorio privado y mismo borrado que la base de Room). Si se **borran
los datos de la app o se reinstala** el mismo día y el pago vuelve del servidor,
ese ticket imprimiría como primera copia. Cerrarlo requiere una tabla nueva.

## 0.2 Compuerta manual: **la visita sube sola, sin señal**

> **Obligatoria antes de soltar cualquier build que toque el sync de visitas.**
> Es el criterio de que los cuatro disparadores automáticos (apertura, periódico
> de 15 min, conectividad restaurada, push SSE) funcionan **en campo** y no solo
> en Robolectric. Y es **lo único que autoriza a quitar el botón "enviar
> pendientes"**: hoy una visita pendiente bloquea "INICIALIZAR SEMANA"
> (`AuthViewModel.kt:118-131`), así que quitar el botón antes de que esta
> compuerta pase deja al cobrador trabado sin salida manual.

**Preparación:** un teléfono real (no emulador — hace falta apagar la señal de
verdad) con la app instalada, sesión iniciada y ventas cargadas. Confirmá que
hay **cero** visitas pendientes antes de empezar: si "INICIALIZAR SEMANA" no se
queja, no hay pendientes.

| # | Qué hacer | Qué tiene que pasar |
|---|---|---|
| 1 | **Modo avión ON** — Wi-Fi y datos apagados los dos | El teléfono queda sin ninguna ruta a la red |
| 2 | Registrar una visita completa desde la pantalla nueva (tipo, y promesa o cita si aplica) | Guarda sin error. Si tomás foto, **la foto no bloquea el guardado** |
| 3 | Mirar el botón **"enviar pendientes"**. **No lo toques** — es la regla de esta compuerta | — |
| 4 | **Matar la app por completo** (recientes → deslizar). No "atrás": matarla | — |
| 5 | Esperar 30 s con el modo avión todavía puesto | — |
| 6 | **Modo avión OFF**; esperar a que vuelva la señal | — |
| 7 | **Reabrir la app** y llegar a Home, **sin tocar "enviar pendientes"** | — |
| 8 | Esperar hasta 2 minutos sin tocar nada | La visita **desaparece de pendientes sola** |
| 9 | Tocar **"INICIALIZAR SEMANA"** | **No se queja de visitas pendientes** — es la señal dura de que subió |
| 10 | Verificar en el servidor que la visita está, con su id | Existe **una sola vez** |

**Si falla el paso 8 pero el 9 no se queja:** subió y el marcado local no se
actualizó. Mirar la telemetría de `by-ids`.
**Si falla el 8 y el 9 SÍ se queja:** no subió. **El botón manual se queda, sin
discusión.** Revisar si el disparador de conectividad llegó a registrarse.
**Si sube pero aparece dos veces en el servidor:** es lo contrario de lo
diseñado (una sola ruta de escritura hacia "sincronizada"). Parar y reportar.

## 0.3 Compuerta manual: **mirar los goldens como imágenes**

> Roborazzi verde significa *"idéntico al golden"*, **no** *"se ve bien"*. Un
> golden feo commiteado se queda verde para siempre. Los PNG viven en
> `<módulo>/src/test/screenshots/` y se abren con cualquier visor: no hay que
> correr nada.

Mirar, como mínimo, en el tamaño de letra más grande (`*_2_0`): la hoja de
confirmación del abono (que el nombre del cliente se lea completo y que "saldo
anterior" y "saldo nuevo" se distingan), la captura del abono, el detalle de
cliente (que la primera venta siga visible) y la pantalla de registrar visita.

Si algo se ve mal, el golden está mal: se corrige el componente y se regraba.
**Nunca** se sube `RoborazziConfig.CHANGE_THRESHOLD` para que un desajuste pase.

## 0.4 Compuerta manual: **las 7 pantallas de cobranza, compuestas en el aparato**

> **Obligatoria antes de soltar cualquier build que toque las pantallas de
> cobranza, `MspTheme` o la provisión de `LocalAppDarkTheme` en `MainActivity`.**
> Lo que `prePushCheck` mide de esas siete pantallas es JVM: Robolectric, con el
> `NavHost` y el `hiltViewModel()` **reales**
> (`CadaDestinoDeCobranzaSeMontaTest`), que es lo más cerca del teléfono que
> llega la compuerta automática. Lo que falta es vidrio, y **sí hay forma**: la
> tarea `connectedDevlocalDebugAndroidTest` existe y está cableada — solo está
> **fuera** del gate a propósito (`prePushCheck` no toma nada de la familia
> `connected*`: exige un aparato conectado).
>
> Esto **no es una imposibilidad**: es alcance. La receta de abajo es ejecutable
> tal cual.

**Preparación:** un emulador o teléfono con `adb devices` respondiendo `device`,
y el flavor `devlocal` (Firebase dev `msp-dev-96ff5`).

⚠️ **Esto cierra la sesión del que esté usando el aparato.** Los tests
instrumentados firman con las credenciales dev en `@Before` y hacen
`FirebaseAuth.signOut()` en `@After`: al terminar, el dueño del teléfono tiene
que volver a iniciar sesión. **No se corre sobre el teléfono de un cobrador en
jornada.** No toca `theme_prefs.xml` ni borra datos de la app.

### Paso 1 — escribir el test (una vez)

En `app/src/androidTest/java/com/example/msp_app/e2e/`, calcado de
`CollectionReportDeviceSmokeTest` para las tres piezas que ese ya resuelve —y
que durante meses se dieron por imposibles—:

| Pieza | Cómo se resuelve | Dónde ya está |
|---|---|---|
| biometría | `MainActivity.isAuthenticated = true` (campo público del companion) | `CollectionReportDeviceSmokeTest.signIn()` |
| sesión | `FirebaseAuth.signInWithEmailAndPassword("gabriel.roque@msp.com", …)` | ídem — **no** hace falta la sesión de nadie |
| host de Hilt | `createAndroidComposeRule<MainActivity>()` (`MainActivity` es `@AndroidEntryPoint`) | ídem |

El cuerpo es la sonda de la JVM transplantada — `composeTestRule.activity` es una
Activity `@AndroidEntryPoint` viva, así que sirve para lo mismo que
`Robolectric.buildActivity(DestinosDeCobranzaTestActivity)`:

```kotlin
@Test
fun lasSieteDeCobranzaSeComponenEnVidrio() {
    val activity = composeTestRule.activity
    val errores = rutasDeCobranza().mapNotNull { ruta ->
        runCatching {
            composeTestRule.runOnUiThread {
                val nav = TestNavHostController(activity)
                nav.navigatorProvider.addNavigator(ComposeNavigator())
                nav.setLifecycleOwner(activity)
                nav.setViewModelStore(activity.viewModelStore)
                val grafo = nav.createGraph(startDestination = RAIZ) {
                    composable(RAIZ) {}
                    destinosDeCobranza(nav)
                }
                nav.graph = grafo
                nav.navigate(ruta)              // ANTES de componer, o no compone nada
                activity.setContent { NavHost(navController = nav, graph = grafo) }
            }
            composeTestRule.waitForIdle()
        }.exceptionOrNull()?.let { "$ruta → ${it.message}" }
    }
    assertEquals(emptyList<String>(), errores)
}
```

`rutasDeCobranza()` y las tres decisiones del `montar()` (un `NavHost` por
destino, navegar **antes** de componer, `navigate()` y no `startDestination`) se
copian tal cual de `CadaDestinoDeCobranzaSeMontaTest` — cada una costó un falso
verde medido, están documentadas ahí.

**Falta una dependencia y es una línea.** `TestNavHostController` viene de
`androidx.navigation:navigation-testing`, que hoy está en `app/build.gradle.kts`
como `testImplementation` **y no** en `androidTest`:

```kotlin
androidTestImplementation(libs.androidx.navigation.testing)
```

**No hacen falta datos de cobranza.** El crash de `MspTheme` ocurre en el
modificador más externo, antes de que ningún dato importe: la captura de
"clientes" con la base vacía es la prueba.

### Paso 2 — correrlo

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:connectedDevlocalDebugAndroidTest
```

| Qué mirar | Qué tiene que pasar |
|---|---|
| El test de arriba | Verde. En rojo, el mensaje **nombra la ruta** de cada pantalla que revienta |
| `CollectionReportDeviceSmokeTest`, que va en la misma corrida | Verde — es el único test que recorre la app REAL desde el drawer, y el que atrapó este mismo defecto la primera vez |
| El informe | `app/build/reports/androidTests/connected/` |

### Paso 3 — el tema oscuro, a ojo (solo si se tocó `MspTheme` o `ThemeController`)

Con la app abierta en una pantalla de cobranza, mover el toggle de tema de la
app (cajón o Configuración → Apariencia) y confirmar dos cosas: la pantalla
cambia **con la app**, y la barra de estado sigue legible en los dos temas
(reloj oscuro sobre claro, claro sobre oscuro). Poner el **SO** en oscuro con la
app en claro **no** debe cambiar nada de la pantalla: manda la app.

⚠️ Esto **persiste la preferencia del dueño del aparato** (`applyThemeMode`
escribe `theme_prefs.xml`). Dejarlo como estaba al terminar.

## 1. Compilar release

```bash
./gradlew :app:assembleProdRelease
```

El APK queda en: `app/build/outputs/apk/prod/release/app-prod-release.apk`

`prod` es el flavor de producción (Firebase `msp-db-1c2ce`, API Go en
`apidev.loclx.io`). `assembleRelease` a secas construye también los flavors de
desarrollo — no subas ésos.

## 2. Subir a GitHub Releases

```bash
gh release create v{VERSION} app/build/outputs/apk/prod/release/app-prod-release.apk --title "v{VERSION}" --notes "Descripción de cambios"
```

## 3. Actualizar Firestore

En `config/api_settings` actualizar:

| Campo | Valor |
|---|---|
| `LATEST_VERSION` | La nueva versión (ej. `2.9.5`) |
| `APK_URL` | `https://github.com/abdimuy/msp-app-kt/releases/download/v{VERSION}/app-prod-release.apk` |

No toques `baseURL` en ese mismo documento: es el kill-switch en vivo del API
que consumen los teléfonos ya instalados.

Los usuarios verán el banner de actualización automáticamente.

## Archivos de versión

Al crear una nueva versión, actualizar estos archivos:

- `app/build.gradle.kts` → `versionCode` y `versionName`
- `app/src/main/java/com/example/msp_app/core/utils/Constants.kt` → `APP_VERSION`

---

## Runbook: Push-channel (SSE + by-ids) — secuencia de despliegue

El push-channel (commits 12–17) requiere que el servidor esté actualizado
**antes** de entregar la app, porque el cliente detecta el endpoint `/by-ids`
con el flag `byIdsAvailable`: si el servidor responde 404, el flag se apaga
para esa sesión y se cae al cursor-sync. Invertir el orden no rompe nada, pero
los cobradores no obtendrán el beneficio de latencia hasta el siguiente reinicio.

### Paso 1 — Servidor (msp-api, Windows Server)

1. Construir el binario para Windows:
   ```bash
   GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -o msp-api.exe ./cmd/api
   ```
2. Copiar `msp-api.exe` al servidor de producción y reiniciar el servicio NSSM.
3. Verificar que los endpoints nuevos responden:
   ```
   GET /v2/cobranza/sync/pagos/by-ids?zona_id=21&ids=1,2,3  → 200 []
   GET /v2/cobranza/sync/saldos/by-ids?zona_id=21&ids=1,2,3 → 200 []
   GET /v2/cobranza/sync/pagos/zona/21/stream               → text/event-stream
   ```
4. Verificar en logcat del servidor que el FbEvent listener está activo
   (`COBRANZA_SSE_ENABLED=true`).

### Paso 2 — Validación en staging (Android)

1. Instalar el APK de release en un dispositivo de prueba.
2. Iniciar sesión con un cobrador de la zona de staging.
3. Confirmar en logcat:
   - `SSE pagos conectando zona=X` / `SSE saldos conectando zona=X`
   - Tras un evento: `SSE pagos sync done: ids=N sync=...ms`
   - Sin `byIdsAvailable=false` en los logs.
4. Crear un pago de prueba en Microsip y verificar que aparece en la app
   en menos de 5 segundos (tolerancia de 2s SSE + 100ms debounce + red).

### Paso 3 — Producción (Android)

1. Subir el APK a GitHub Releases (paso 2 de este doc).
2. Actualizar `LATEST_VERSION` y `APK_URL` en Firestore.
3. Los cobradores verán el banner de actualización automáticamente.

### Rollback

- Si el servidor necesita rollback: revertir al binario anterior. Los clientes
  new detectarán la ausencia del `/by-ids` (404) y caerán al cursor-sync en
  esa sesión. No se pierde consistencia — solo aumenta la latencia.
- Si el APK necesita rollback: publicar un APK anterior en GitHub Releases y
  actualizar `LATEST_VERSION`. El feature SSE estaba disponible desde v2.11.0;
  el rollback a cualquier versión >= 2.11.0 mantiene el polling+reconcile.
