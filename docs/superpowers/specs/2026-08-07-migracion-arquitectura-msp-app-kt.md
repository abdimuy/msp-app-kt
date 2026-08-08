# Spec: Migración progresiva de arquitectura — msp-app-kt

> **Fecha:** 2026-08-07
> **Estado:** diseño aprobado (brainstorm). Este es el spec del **cimiento + piloto**; el sistema de diseño (detalle de tokens) y el backend de observabilidad tienen specs-compañeros.
> **Repo:** `msp-app-kt` (un solo repo git; multi-módulo Gradle). NO es un sub-repo/submódulo.

## 1. Contexto y objetivo

`msp-app-kt` (app Android de cobradores, Kotlin + Jetpack Compose, offline-first) es hoy **un solo módulo Gradle `:app`** con 19 features (~37k LOC), **sin framework de DI** (todo `ApiProvider` manual + ViewModels auto-cableados), `NavHost` plano, y una `AppDatabase` Room monolítica. Funciona, pero: el look es Material default (sin identidad), depurar en campo ha sido el mayor dolor histórico (no había forma de saber qué build trae un teléfono ni de reconstruir escenarios), y el DI manual + la falta de fronteras dificultan iterar.

**Objetivo:** migrar **progresivamente, en el mismo repo** (patrón *strangler-fig*), a una arquitectura **hexagonal multi-módulo** — el patrón que el equipo ya domina (com.bonanza.campo, msp-api, sistema-cobro-web) pero **mejor calibrado** contra sus dolores — donde **cada módulo nuevo nace con identidad visual propia (azul, estilo kollect-app) y observabilidad incorporada**. Nada se reescribe de golpe; se migra pantalla por pantalla sin romper lo existente.

Este trabajo es independiente del sistema Firestore/deploy y del fix de pagos ya desplegado; construye el cimiento sobre el que el resto de features se migran una por una.

## 2. Principios (las mejoras sobre el hexagonal anterior)

1. **Menos ceremonia, YAGNI agresivo.** Un `interface`/puerto **solo** cuando hay ≥2 implementaciones reales o se cruza un borde de módulo — nada de "port por si acaso" con una sola impl. Mappers dto↔domain **solo** cuando el shape difiere de verdad; nada de triple-map ritual. El mejor precedente interno ya existente es `core/sync/pendingwork/` — esa es la plantilla, no el hexagonal recargado.
2. **DI real (Hilt).** Se elimina el `ApiProvider` manual y los ViewModels que se auto-cablean. KSP ya está en el proyecto.
3. **Fronteras por módulo Gradle**, no por convención. Cada feature aislada: no puede alcanzar el interior de otra; comparte solo vía `:core:*`.
4. **Observabilidad por construcción.** Cada módulo nace instrumentado (puerto `Telemetry` + helpers del UI-kit); la PII nunca entra en las etiquetas (LFPDPPP).
5. **Identidad visual por construcción.** Cada módulo consume `:core:designsystem` (tema azul); sus pantallas nunca hardcodean colores.
6. **Reversible por feature.** El `NavHost` enruta viejo↔nuevo; migrar una feature es un cambio acotado y reversible.

## 3. Topología de módulos (target)

```
msp-app-kt/                     (mismo repo git)
├── app/                        shell legacy (se encoge) + composition root (Hilt) + NavHost (puente viejo↔nuevo)
├── core/
│   ├── designsystem/           tema azul (tokens Color/Type/Shapes/Spacing/Motion sobre M3) + UI-kit + reveal oscuro/claro
│   ├── database/               AppDatabase (hoisteada) + DAOs compartidos
│   ├── telemetry/              puerto Telemetry + Modifier.trackClick + ScreenScope/LocalScreenName
│   ├── network/                Retrofit/OkHttp base + interceptores (auth, versión)
│   └── common/                 utilidades verdaderamente compartidas
└── feature/
    ├── camionetaAssignment/    PILOTO (módulo hexagonal completo)
    └── <feature>/              cada feature migrada = su módulo (domain/ · data/ · ui/)
```

- **`:app`** deja de contener features poco a poco; retiene el composition root (Hilt `@HiltAndroidApp`), el `NavHost`, y las pantallas legacy aún no migradas.
- Cada **`:feature:<name>`**: `domain/` (entidades + casos de uso, cero framework) · `data/` (repos + adapters Room/Retrofit; puerto solo si cruza módulo) · `ui/` (Compose + `@HiltViewModel`). Sin capas que solo reenvían.
- **`settings.gradle.kts`** incluye cada módulo. La `libs.versions.toml` (hoy delgada) se completa con las ~30 deps hoy hardcodeadas en `app/build.gradle.kts`, y se crean *convention plugins* (`buildSrc` o `build-logic`) para no repetir configuración por módulo.

## 4. DI con Hilt (migración gradual)

- `:app` → `@HiltAndroidApp`; `@AndroidEntryPoint` en la Activity.
- Envolver lo existente **sin reescribir su interior**: `@Provides @Singleton` para el Retrofit de `ApiProvider`/`V2ApiProvider`, la `AppDatabase`, `ConnectivityMonitor`, etc. — módulos Hilt en `:core:network`/`:core:database`.
- Convertir ViewModels a `@HiltViewModel` con `@Inject constructor(...)` **feature por feature**, retirando cada `ApiProvider.create()` a medida que se migra. Workers → `@HiltWorker` (requiere `androidx.hilt:hilt-work`).
- Regla: mientras una feature siga en `:app` legacy, puede seguir usando el DI viejo; al migrarla a su módulo, pasa a Hilt. No hay "big bang" de DI.

## 5. Estrategia de migración (strangler-fig)

- **Seam de navegación:** el `NavHost` en `:app` enruta cada ruta a la pantalla vieja (en `:app`) o a la nueva (en `:feature:*`). Migrar = crear el módulo, mover la feature, apuntar su `composable(route){}` a la versión nueva, borrar la vieja. Un bloque por ruta.
- **Orden (de menor a mayor riesgo):**
  1. **Cimiento** (este spec): `:core:designsystem`, `:core:database`, `:core:telemetry`, `:core:network`, Hilt en `:app`, convention plugins + version catalog.
  2. **Piloto: `:feature:camionetaAssignment`** — cero acoplamiento con otras features, ya tiene `domain/data/presentation`, ruta única sin params, ViewModel ya construido en el callsite. Prueba TODO el template end-to-end (módulo + Hilt + tema azul + telemetría + Room/Retrofit reales) con mínimo blast radius.
  3. **Piloto 2: `:feature:dailyReport`** — ya está ~80% en la forma objetivo; valida "repo pasa a interface" y "use case con `@Inject`".
  4. Features hoja (warehouses, zones, guarantees, visit, …).
  5. **`payments`/cobranza** — alto valor, God-ViewModel sin repo; se migra cuando el patrón esté probado.
  6. **`sales`** (16k LOC, hub de 11 features) — el último; probablemente se descompone.
- **`:core:database` primero** porque la `AppDatabase` monolítica compartida es el mayor obstáculo físico para partir módulos (se hoistea a un módulo compartido, no se parte por feature).

## 6. Sistema de diseño — identidad azul (`:core:designsystem`)

Basado 1:1 en el design system de kollect-app (tokens `Colors/Type/Shapes/Spacing/Motion` sobre M3, leídos vía `AppTheme.colors.*`; fuente **Manrope**; formas 20/16dp; dos springs `standard`/`emphasized`).

- **Verde→azul = exactamente 6 valores hex** (el resto — estados, promise, danger, info — es semántico y NO cambia):
  | token | kollect (verde) | propuesta azul |
  |---|---|---|
  | light `brand` | `#0D4A45` | `#0D3B66` |
  | light `brand2` | `#0A3B37` | `#0A2E52` |
  | light `brandTint` | `#EEF5F4` | `#EAF1F9` |
  | dark `brand` | `#1E9E86` | `#3B82F6` |
  | dark `brand2` | `#14705C` | `#1D5FB0` |
  | dark `brandTint` | `#123029` | `#0E2440` |
  (Propuesta, ajustable en implementación o vía mockup visual antes de fijar.)
- **Animación oscuro/claro:** el *reveal circular estilo Telegram* de kollect-app (graphics-layer snapshot del tema viejo + `Animatable` de radio con `tween(380ms, FastOutSlowInEasing)` desde el punto tocado, revelando el tema nuevo; crossfade de 300ms como respaldo). Se porta tal cual.
- Componentes firma (CampoCard/hairline, PrimaryFieldButton con haptic, StatusChip nunca-solo-color, micro-interacciones ≤300ms, el único `emphasized` reservado a "pago confirmado") se replican en el UI-kit.
- (El detalle exhaustivo de tokens/componentes va en el **spec-compañero de design system**; la referencia completa está en `.superpowers/research/kollect-app-designsystem.md`.)

## 7. Observabilidad self-hosted (`:core:telemetry` + backend)

Recomendación híbrida (ver `.superpowers/research/observability-self-hosted.md`), todo en infra propia:

- **App-side (en este spec, agnóstico del backend):**
  - Puerto `Telemetry` en `:core:telemetry` (`screenView` / `tap` / `event` / `error`); el adapter concreto (GlitchTip SDK + cola propia) se cablea SOLO en el composition root — las features nunca importan un SDK de vendor.
  - **Cola offline durable en Room, espejo del outbox de pagos** (estados pending/uploading/sent/failed, batch por N eventos o T segundos o al reconectar, backoff, tope con "nunca tirar errores, best-effort tirar el resto", sampling de eventos ruidosos).
  - Captura Compose: `screenView` automático vía `navController.currentBackStackEntryFlow`; `Modifier.trackClick("nombre-estático")` + `ScreenScope`/`LocalScreenName` en el UI-kit → cada módulo observable por construcción. **Etiquetas siempre estáticas del desarrollador, jamás derivadas de texto/contentDescription** (LFPDPPP).
  - **Crashes/errores:** SDK de Sentry (`io.sentry:sentry-android`) con DSN apuntando a GlitchTip (offline cache incluido).
- **Backend (spec-compañero, cuando se defina el box):** **VM Linux chica** con **GlitchTip** (errores; ~1GB RAM, 4 contenedores, mismo protocolo Sentry) + **Postgres** (tabla `telemetry_events` particionada por día; ingest vía endpoint Go; retención corta LFPDPPP + rollups) + **Metabase** (dashboards/embudos/"qué no se usa"). **SQLite NO** para el store servidor (es solo para la cola en el teléfono).
- **"Recrear cualquier escenario" — expectativa honesta:** en Android nativo no hay video-replay tipo web sin consentimiento por sesión + riesgo LFPDPPP. Lo realista y mejor para depurar es una **línea de tiempo estructurada por sesión** (pantalla→acción→estado→red→error, en orden) — grepeable/diffeable y captura el estado del dominio (saldo mostrado, pendientes de sync) que ninguna herramienta genérica capturaría. Opcional: grabación de pantalla **bajo demanda con consentimiento** (soporte en vivo), nunca siempre-encendida.

## 8. Versionado / trazabilidad (ya implementado, se conserva)

`versionName` en `build.gradle` es la única fuente; los flavors de test estampan git-SHA (`2.12.2-dev+<sha>`) visible en footer/drawer y en el `appVersion` de los logs. `Constants.APP_VERSION` se deriva de `BuildConfig.VERSION_NAME.substringBefore("-")` (base de release, para el update-check). Cada módulo/feature reporta a `Telemetry` con ese `appVersion`, así cualquier evento/error es atribuible al commit exacto.

## 9. Alcance del PRIMER plan (cimiento + piloto)

1. Version catalog completo + convention plugins (`build-logic`) para módulos Android/Compose/Hilt.
2. `:core:designsystem` con el tema azul (6 hex) + el reveal oscuro/claro + UI-kit base (Card, Button, StatusChip, ProgressBar, Money) — con tests de screenshot/preview donde aplique.
3. `:core:database` (hoist de `AppDatabase` + DAOs) sin cambiar el esquema.
4. `:core:telemetry` (puerto + cola Room offline + `Modifier.trackClick`/`ScreenScope`) apuntando a un adapter stub/GlitchTip (backend real = spec aparte).
5. Hilt en `:app` (`@HiltAndroidApp`, `@Provides` envolviendo lo existente).
6. **Migrar `:feature:camionetaAssignment`** end-to-end como prueba del template: módulo Gradle, hexagonal (domain/data/ui), `@HiltViewModel`, tema azul, `trackClick`, ruta re-apuntada en el `NavHost`, feature vieja borrada. Tests por capa.
7. Gate: `./gradlew :app:testDevlocalDebugUnitTest` + `connectedDevlocalDebugAndroidTest` (piloto) + `ktlintCheck` + `assembleDevserverRelease` verdes; la app corre idéntica salvo la pantalla piloto (ahora en su módulo, con tema azul).

## 10. Specs-compañeros (después del cimiento)
- **Design system (detalle):** tokens completos, todos los componentes firma, dark/light, guía de uso.
- **Backend de observabilidad:** VM Linux, docker-compose (GlitchTip + Postgres + Metabase), esquema `telemetry_events`, endpoint de ingest Go, retención/rollups LFPDPPP, dashboards.
- **Migración de features restantes:** orden, y el plan específico para `payments` y la descomposición de `sales`.

## 11. Riesgos y mitigaciones
- **`AppDatabase` monolítica:** se hoistea a `:core:database` (módulo compartido), no se parte por feature — evita romper 10 features a la vez.
- **`sales` (hub de 11 features):** intocable hasta el final; se descompone en su propio esfuerzo.
- **Regresiones al migrar:** cada feature migrada mantiene paridad de comportamiento; el `NavHost` permite volver a la versión vieja si algo falla.
- **Sobre-ingeniería:** el principio YAGNI (§2) es explícito y se verifica en review (nada de puertos de una sola impl).
- **Churn de Compose/Hilt entre versiones:** convention plugins centralizan versiones; se evita instrumentación por reflexión frágil (se prefiere `trackClick` explícito).
