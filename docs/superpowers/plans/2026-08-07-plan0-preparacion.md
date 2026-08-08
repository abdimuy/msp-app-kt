# Plan 0 — Preparación (rama + andamiaje mínimo)

Parte del plan maestro `2026-08-07-plan-maestro-multimodulo.md`. Objetivo: dejar el andamiaje Gradle
listo (version catalog completo + `build-logic` con convention plugins) **sin cambiar comportamiento ni
versiones resueltas**. Al final, `./gradlew help` y la compilación de `:app` deben funcionar igual que antes.

## Global Constraints (vinculan a TODA tarea de este plan)
- Toolchain FIJA, no cambiar: AGP 8.10.1, Kotlin 2.0.21, KSP 2.0.21-1.0.27, compileSdk 35, minSdk 24,
  targetSdk 35, Java 11, Compose BOM 2024.09.00. Gradle wrapper 8.11.1.
- `JAVA_HOME` en cada comando gradle: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Variante de gate para compilar/probar: **devlocalDebug**.
- `applicationId`/namespace `com.example.msp_app` NO se toca.
- Commits por tarea, conventional commits, en español el subject, SIN atribución de Claude, SIN `--no-verify`.
- Rama: `feat/multimodulo-cimiento`. Sin push.
- **No cambiar versiones resueltas de dependencias existentes.** Solo mover strings hardcodeados al catálogo
  y AÑADIR entradas nuevas para tooling futuro (Hilt/Roborazzi/Turbine/Kover/detekt) que aún NO se aplican.

---

## Task 1 — Version catalog completo (hoist de todas las deps + tooling futuro)

**Meta:** `gradle/libs.versions.toml` pasa a ser la única fuente de versiones. Todas las dependencias y
plugins hoy escritos como strings literales en `app/build.gradle.kts` y en `build.gradle.kts` raíz se
declaran en el catálogo, y esos build scripts pasan a referenciarlas (`libs.*`). Las versiones RESUELTAS
no cambian. Además se AÑADEN (sin aplicar aún) las entradas del tooling que usarán los planes siguientes.

**Deps existentes a mover al catálogo** (tomar versión EXACTA del `app/build.gradle.kts` actual):
- Compose/AndroidX ya presentes en el catálogo se conservan.
- `com.composables:icons-lucide:1.0.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3`, `...-coroutines-android:1.7.3`
- `org.robolectric:robolectric:4.14.1`
- `com.squareup.okhttp3:mockwebserver:4.12.0`, `okhttp:4.12.0`, `okhttp-sse:4.12.0`
- `androidx.room:room-runtime:2.6.1`, `room-compiler:2.6.1`, `room-ktx:2.6.1`, `room-testing:2.6.1`
- `androidx.test:core:1.5.0`, `runner:1.5.2`, `rules:1.5.0`
- `androidx.arch.core:core-testing:2.2.0`
- `androidx.work:work-runtime-ktx:2.10.2`, `work-testing:2.10.2`
- `com.google.firebase:firebase-bom:33.12.0` (+ auth-ktx, firestore-ktx, storage-ktx sin versión, del BOM)
- `androidx.navigation:navigation-compose:2.7.7`
- `com.squareup.retrofit2:retrofit:2.9.0`, `converter-gson:2.9.0`
- `androidx.datastore:datastore-preferences:1.1.7`
- `com.android.tools:desugar_jdk_libs:2.0.4`
- `androidx.compose.foundation:foundation:1.7.8`, `androidx.compose.material:material-icons-core:1.7.8`
- `com.google.maps.android:maps-compose:4.2.0`, `com.google.android.gms:play-services-maps:18.2.0`,
  `play-services-location:21.0.1`
- `com.google.accompanist:accompanist-systemuicontroller:0.36.0`, `accompanist-permissions:0.37.3`
- `com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.4.0`
- `io.coil-kt:coil-compose:2.4.0`
- `org.apache.commons:commons-math3:3.6.1`
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2`
- `com.google.code.gson:gson:2.8.9`
- `androidx.biometric:biometric:1.1.0`
- Plugins hoy con versión literal en `build.gradle.kts` raíz: `com.google.gms.google-services:4.4.2`,
  `com.google.devtools.ksp:2.0.21-1.0.27` (ya en catálogo como `ksp`), `org.jlleitschuh.gradle.ktlint:12.1.2`.

**Tooling futuro a AÑADIR al catálogo (NO aplicar todavía; solo entradas disponibles):**
- Hilt: `com.google.dagger:hilt-android` + `hilt-compiler` (versión `2.52`), plugin `com.google.dagger.hilt.android:2.52`.
- AndroidX Hilt: `androidx.hilt:hilt-work:1.2.0`, `androidx.hilt:hilt-compiler:1.2.0`,
  `androidx.hilt:hilt-navigation-compose:1.2.0`.
- Turbine: `app.cash.turbine:turbine:1.1.0`.
- Roborazzi: `io.github.takahirom.roborazzi:roborazzi:1.26.0`, `roborazzi-compose:1.26.0`,
  `roborazzi-junit-rule:1.26.0`, plugin `io.github.takahirom.roborazzi:1.26.0`.
- Kover: plugin `org.jetbrains.kotlinx.kover:0.8.3`.
- Detekt: plugin `io.gitlab.arturbosch.detekt:1.23.7`, lib `io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7`.

Nota de compatibilidad: si alguna versión de tooling nueva es incompatible con la toolchain fija
(Kotlin 2.0.21 / KSP 2.0.21-1.0.27 / AGP 8.10.1 / Compose BOM 2024.09.00), elegí la versión estable más
cercana que SÍ compile y documentá el cambio en el reporte. NO cambies versiones de deps existentes.

**Cómo:** usar `[versions]`, `[libraries]`, `[bundles]` (agrupá lo que tenga sentido: `bundle` de compose,
de room, de test) y `[plugins]`. Reescribir `app/build.gradle.kts` y `build.gradle.kts` raíz para usar
`libs.*` / `alias(libs.plugins.*)`. Mantener el bloque `androidComponents.beforeVariants`, flavors,
signing, `MAPS_API_KEY`, y todo lo demás sin cambios de comportamiento.

**Verificación (correr y pegar salida en el reporte):**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew help
./gradlew :app:compileDevlocalDebugKotlin
```
Ambos deben terminar `BUILD SUCCESSFUL`. Además confirmá que no cambió ninguna versión resuelta de una dep
existente (por ejemplo `./gradlew :app:dependencies --configuration devlocalDebugRuntimeClasspath` antes y
después, o razonar que solo se movieron strings). No hay tests unitarios nuevos en esta tarea (es andamiaje
de build); la "prueba" es que el build resuelve idéntico.

**Commit:** `build: hoist dependencies to version catalog`

---

## Task 2 — `build-logic` con convention plugins

**Meta:** crear el included build `build-logic/` con cinco convention plugins reutilizables, resolvables
desde el proyecto, SIN romper el build de `:app`. En este plan NO se aplican a `:app` (eso es Plan 1+);
solo deben existir, compilar y estar registrados.

**Convention plugins a crear** (id → responsabilidad mínima y real, no vacía):
- `msp.android.library` — aplica `com.android.library` + `org.jetbrains.kotlin.android`; fija compileSdk 35,
  minSdk 24, Java 11 (`compileOptions` + `jvmTarget=11`, `isCoreLibraryDesugaringEnabled=true` + añade
  `coreLibraryDesugaring` desugar_jdk_libs), `buildFeatures.buildConfig` según haga falta.
- `msp.android.compose` — presupone `msp.android.library`; habilita `buildFeatures.compose`, aplica
  `org.jetbrains.kotlin.plugin.compose`, añade el BOM de compose + deps base de compose (ui, graphics,
  tooling-preview, material3) y las de test de compose.
- `msp.hilt` — aplica `com.google.dagger.hilt.android` + `com.google.devtools.ksp`; añade `hilt-android`
  y `ksp(hilt-compiler)`.
- `msp.test` — configura `testOptions.unitTests.isIncludeAndroidResources=true` + heap (`maxHeapSize=2g`,
  `-XX:MaxMetaspaceSize=1g` como en `:app`); añade deps de test compartidas (junit, coroutines-test,
  robolectric, turbine, room-testing, arch-core-testing). (Los fakes de `:core:testing` se cablean en Plan 1.)
- `msp.kover` — aplica el plugin Kover con umbrales por defecto placeholder (se ajustan por módulo en planes
  siguientes). Que exista y aplique sin fallar.

**Cómo (estructura estándar de Now-in-Android):**
- `build-logic/settings.gradle.kts` con `dependencyResolutionManagement` que expone el version catalog raíz
  (`versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }`).
- `build-logic/build.gradle.kts` con `plugins { \`kotlin-dsl\` }` y dependencias a los Gradle plugins
  (android-gradle-plugin, kotlin-gradle-plugin, ksp-gradle-plugin, hilt-gradle-plugin, kover, etc.) vía
  `compileOnly`/`implementation` sobre coordenadas del catálogo (`libs.plugins.*.get().pluginId`... o
  artefactos `*-gradle-plugin`). Registrar cada plugin con `gradlePlugin { plugins { register(...) } }`.
- `settings.gradle.kts` raíz: `pluginManagement { includeBuild("build-logic") }`.

**Verificación (correr y pegar salida):**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew help
./gradlew :app:compileDevlocalDebugKotlin
```
`./gradlew help` debe compilar `build-logic` y terminar `BUILD SUCCESSFUL`; `:app` sigue compilando.
Para probar que los plugins son aplicables sin crear un módulo nuevo, incluí en el reporte la salida de
`./gradlew help` (que fuerza a compilar los convention plugins) y confirmá que los `register(...)` no dan
error de id duplicado ni de clase faltante.

**Commit:** `build: add build-logic convention plugins`

---

## Cierre de Plan 0 (auditoría de conformidad)
- [ ] `./gradlew help` verde.
- [ ] `:app` compila la variante devlocalDebug igual que antes; versiones existentes intactas.
- [ ] Catálogo contiene TODAS las deps (existentes movidas + tooling futuro añadido).
- [ ] `build-logic` con los 5 convention plugins registrados y compilando.
- [ ] Nada aplicado aún a `:app` que cambie su comportamiento. App corre idéntica.
- [ ] Commits por tarea, sin push, sin bypass del gate.
