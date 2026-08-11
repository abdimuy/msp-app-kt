# Configuración + tamaño de letra accesible — diseño

- **Fecha:** 2026-08-10
- **App:** msp-app-kt · rama base `feat/multimodulo-cimiento`
- **Estado:** aprobado en brainstorming, pendiente de plan de implementación

## Contexto y objetivo

Muchos cobradores son mayores y no ven bien. Hoy el tamaño de letra depende **solo del `fontScale` del sistema operativo** (accesibilidad de Android): la decisión de layout Tier 1 / Tier 2 del reporte de cobranza (`ReportTier.rememberReportTier`, lee `LocalDensity.current.fontScale`) y los "layouts grandes" de venta/combos (`fontScale > 1.3f`) se cuelgan de ahí. No existe control in-app, y entrar a Ajustes de Android es justo lo difícil para ese usuario.

Objetivo: una **pantalla de Configuración** que centralice **tamaño de letra** (el core, no está en ningún otro lado), **tema** y **privacidad de cifras**, con un tamaño de letra controlable desde la app y una tipografía que se lee cómodo en los niveles grandes.

Todo el trabajo va en **arquitectura nueva** (módulos `:feature:*` / `:core:*`), nunca extendiendo legacy `app/src/main/.../features/*` (salvo el glue mínimo de composición). Ver la convención del repo.

## Decisiones (cerradas en brainstorming)

1. **Pantalla nueva** en un módulo hexagonal `:feature:configuracion`. Entrada desde el drawer.
2. **Cuatro ajustes:** tamaño de letra, tema (reusa `ThemeController`), privacidad de cifras (preferencia global nueva), y **deshabilitar animaciones** (reduce-motion, preferencia global nueva).
3. **Tamaño de letra = 3 niveles discretos:** Normal / Grande / Muy grande.
4. **Override = Opción C:** el tamaño efectivo = **máximo(nivel elegido en la app, `fontScale` del OS)**. La app controla, pero **nunca** achica por debajo de lo que el teléfono ya pide (accesibilidad-safe). Con nivel por defecto "Normal", out-of-the-box respeta el OS.
5. **Compresión de jerarquía:** en los niveles grandes, la diferencia entre el texto más grande y el más chico se **comprime** (piso alto para el texto chico, techo para el grande) para que todo sea legible y nada se desborde. Se aplica **progresivamente, solo en pantallas ya migradas** (empezando por el reporte de cobranza / sistema de tiers); el resto de la app recibe el escalado lineal global mientras se migra.

## Alcance

**Incluye:**
- Módulo `:feature:configuracion` con la pantalla y su ViewModel/estado.
- `:core:settings` (nuevo): `SettingsRepository` sobre DataStore para persistir tamaño de letra y privacidad.
- `:core:designsystem`: modelo `FontSizeLevel` (nivel → fontScale nominal) + rampa tipográfica comprimida (tabla rol×nivel→tamaño).
- Aplicación del override en la raíz de composición (`app/`): `CompositionLocalProvider(LocalDensity …)` con el efectivo = máx(app, OS).
- Aplicar la rampa comprimida a la **primera pantalla migrada** (reporte de cobranza, Tier 1/2) como piloto del rollout progresivo.
- Entrada "Configuración" en el drawer + navegación.
- Tests unit + compose + goldens.

**No incluye (fuera de alcance):**
- Migrar la compresión a todas las pantallas legacy de golpe (es progresivo, pantalla por pantalla, conforme se migran).
- Rehacer la persistencia del tema si `ThemeController` ya persiste (ver open items).
- Ajustes adicionales futuros (idioma, etc.).

## Arquitectura

### `:core:settings` (nuevo) — persistencia cross-cutting
- `SettingsRepository` sobre **DataStore Preferences** (mismo stack que `SaleDraftManager`, ya en la app).
- Claves: `font_size_level` (enum `NORMAL|GRANDE|MUY_GRANDE`), `privacy_masked` (bool), `reduce_motion` (bool). El tema se mantiene en `ThemeController` (ver Tema/Privacidad).
- Expone `StateFlow`/`Flow` de cada ajuste, y setters suspend. Cross-cutting: lo consumen tanto la raíz de `app/` (para el override) como cualquier pantalla que enmascare cifras.
- Vive en `:core:settings` (no en `:feature:configuracion`) para que app-root y otros features dependan de él sin depender de la pantalla.

### `:core:designsystem` — tamaño de letra
- `enum FontSizeLevel(nominalScale: Float)`: `NORMAL(1.0f)`, `GRANDE(1.5f)`, `MUY_GRANDE(2.0f)`. El `nominalScale` alimenta la decisión de tier (umbral existente ~1.65 → Muy grande = Tier 2).
- **Rampa tipográfica comprimida:** una función pura `rampedSize(role, level): TextUnit` (o una `Typography` por nivel) que define el tamaño por rol (display / title / body / label / caption) con compresión: al subir de nivel, el texto chico sube más rápido (piso alto) y el grande se topa (techo). Es la pieza que las pantallas migradas consumen en vez del escalado lineal.
- Puro y testeable: monotonicidad (a mayor nivel, ningún rol decrece), pisos/techos, y que el ratio grande/chico decrece con el nivel.

### `:feature:configuracion` (nuevo) — la pantalla
- Hexagonal, mismo estándar que los demás `:feature:*`. `ConfiguracionScreen` + `ConfiguracionViewModel` + estado observable.
- Consume `:core:settings` (tamaño, privacidad) y `ThemeController` (tema).
- Tres ajustes con **preview en vivo** del tamaño (un bloque de muestra que se re-dibuja al tocar cada nivel).

### `app/` (composición root) — glue mínimo, inevitable
- En el arranque (Theme/`MainActivity`), leer `SettingsRepository.fontSizeLevel` + el `fontScale` del OS y proveer `LocalDensity` con **efectivo = máx(nivel.nominalScale, os.fontScale)**. Así **todas** las pantallas que ya leen `fontScale` (tiers, layouts grandes) responden sin tocarlas una por una.
- Ítem "Configuración" en `DrawerContainer` + ruta de navegación a `:feature:configuracion`.

## Mecánica del tamaño de letra (dos capas)

1. **Escalado global (Opción C) — beneficia a toda la app ya:** el override de `LocalDensity` en la raíz agranda linealmente todo el texto según máx(app, OS). Es el piso de mejora inmediato en cada pantalla, y hace que los tiers/layouts-grandes reaccionen.
2. **Compresión (progresiva) — solo pantallas migradas:** una pantalla migrada envuelve su contenido para usar la **rampa comprimida** en vez del escalado lineal, tamando la jerarquía en los niveles grandes. Las no migradas se quedan con la capa 1 (lineal) hasta que se migren.

**Cuidado de implementación (double-scaling):** una pantalla que use la rampa produce tamaños ya resueltos para el nivel efectivo; dentro de su subárbol hay que **neutralizar** el `fontScale` del `LocalDensity` (proveer densidad con `fontScale = 1f` o usar unidades no-escalables) para que la rampa no se multiplique dos veces. El plan debe cubrir este punto explícitamente y testearlo.

## Deshabilitar animaciones (reduce-motion)

- Preferencia global `reduce_motion` en `SettingsRepository`. Por defecto `false` (animaciones activas).
- Las pantallas migradas la leen y **cortan/omiten** sus animaciones cuando está activa. El reporte de cobranza es el caso denso: theme reveal, `StaggeredEntrance`, `TabTransition` (swap Día↔Semana), crecimiento de la `Sparkline`, y las micro-animaciones de las tarjetas — todas deben degradar a un estado estático inmediato cuando `reduce_motion` es `true`.
- Se expone un `LocalReduceMotion` (CompositionLocal) provisto en la raíz desde la preferencia, para que los composables lo consulten sin acoplarse a `:core:settings`. Rollout progresivo, igual que la compresión: cada pantalla migrada respeta la bandera.

## Tema y privacidad

- **Tema:** la pantalla togglea `ThemeController` (ya global, `isDarkMode`). Verificar si `ThemeController` persiste entre reinicios; si no, persistir vía `SettingsRepository` (`theme_mode`). Los toggles existentes en otras pantallas siguen funcionando (misma fuente).
- **Privacidad de cifras:** hoy es un parámetro `masked` que cada pantalla pasa a sus componentes (`MoneyText`, etc.); no hay estado global. Se crea la preferencia global `privacy_masked` en `SettingsRepository`; las pantallas migradas leen de ahí (el reporte de cobranza ya tiene su propio `masked` — se conecta a la preferencia global). Los toggles in-screen escriben a la misma preferencia.

## Testing

- `SettingsRepository`: lectura/escritura DataStore (in-memory), defaults.
- `FontSizeLevel` + rampa: nominalScale por nivel; `resolveTier` con el efectivo; rampa monotónica + pisos/techos + ratio decreciente.
- Override efectivo = máx(app, OS): pruebas de casos (OS grande + app Normal → OS; OS normal + app Muy grande → app).
- `ConfiguracionScreen`: compose test (seleccionar nivel actualiza el preview; togglear tema/privacidad).
- Goldens Roborazzi: pantalla de Configuración y el reporte de cobranza en los 3 niveles (lineal vs comprimido).

## Riesgos / open items

- **Alcance de la compresión:** la rampa comprimida es un cambio de design-system; se contiene haciéndolo progresivo (piloto = cobranza). Riesgo de romper layouts se limita a la pantalla migrada a la vez.
- **Double-scaling** (arriba): hay que neutralizar el fontScale dentro del subárbol con rampa.
- **Persistencia de `ThemeController`:** verificar; posible trabajo extra si no persiste.
- **Glue en `app/`:** tocar la raíz de composición y el drawer es inevitable (composición), pero mínimo; la lógica vive en los módulos nuevos.
- **`:core:settings` vs meter la persistencia en el feature:** se elige `:core:settings` para no acoplar app-root a la pantalla.

## Rollout progresivo

1. Infra: `:core:settings`, `FontSizeLevel` + rampa, override en la raíz, pantalla de Configuración. Con esto, TODA la app ya escala (capa 1) y el reporte de cobranza estrena la compresión (capa 2).
2. Conforme se migran más pantallas, cada una opta por la rampa comprimida — sin re-tocar la infra.
