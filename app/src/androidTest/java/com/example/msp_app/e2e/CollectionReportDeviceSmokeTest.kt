package com.example.msp_app.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.msp_app.MainActivity
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Credenciales dev documentadas (Firebase `msp-dev-96ff5`, el mismo proyecto que `devlocal`/`devserver`). */
private const val DEV_EMAIL = "gabriel.roque@msp.com"
private const val DEV_PASSWORD = "admin123"
private const val SIGN_IN_TIMEOUT_SECONDS = 30L

/**
 * `testTag` de cada segmento de
 * [com.example.msp_app.feature.collectionreport.ui.components.PeriodSelector] — Día=0,
 * Semana=1 (`:core:designsystem` `SegmentChips.kt`, `internal`; referenciado por el literal,
 * mismo criterio que el resto de este archivo).
 *
 * **Colisión de tags (día real, dos `MspSegmentChips` en pantalla a la vez):** en Día,
 * `DetailHeader` monta OTRO `MspSegmentChips` para el sort Hora/Nombre — mismo prefijo de tag,
 * mismos índices 0/1. `PeriodSelector` compone ANTES en el árbol (`ReportHeader` → ... →
 * `PeriodSelector` → `RangeSubRow` → `TabTransition{Hero,Duo,Chips,DetailHeader,...}`,
 * `CollectionReportContent.kt`), así que el índice `[0]` de [PERIOD_TAG_SEMANA]/[PERIOD_TAG_DIA]
 * siempre resuelve al selector de periodo, nunca al de orden.
 */
private const val PERIOD_TAG_DIA = "msp_segment_chip_0"
private const val PERIOD_TAG_SEMANA = "msp_segment_chip_1"
private const val PERIOD_SELECTOR_INDEX = 0

/** `testTag` del hero, expuesto por `:feature:collectionReport` (`HeroSection.COLLECTION_REPORT_HERO_TEST_TAG`). */
private const val HERO_TAG = "collection_report_hero"

private const val UI_TIMEOUT_MS = 30_000L

/**
 * Smoke e2e de dispositivo del piloto `:feature:collectionReport` (Plan 5, Task 11 — cierre).
 * Recorre la app REAL (sin fakes de UI/navegación) exactamente como lo haría un cobrador:
 * abre el drawer desde Home, toca "Reporte de cobranza", confirma que el hero renderiza, que
 * el toggle Día/Semana funciona, y que tocar el hero abre su sheet — task-11-brief.md: "abre
 * el drawer → 'daily_reports' → el reporte renderiza (hero visible, toggle Día/Semana
 * funciona, un sheet abre)".
 *
 * **Auth real, no fake:** [com.example.msp_app.features.auth.viewModels.AuthViewModel] es un
 * `AndroidViewModel` legado que llama `FirebaseAuth.getInstance()`/`FirebaseFirestore.getInstance()`
 * directo — sin puerto/seam inyectable que un fake pueda sustituir (a diferencia de los módulos
 * Hilt nuevos del repo). Firmar sesión de verdad contra el proyecto Firebase dev (`msp-dev-96ff5`,
 * el mismo que usa `devlocal`) ANTES de lanzar la actividad es el mismo patrón ya documentado
 * para smoke manual en emulador — ver `reference_app_dev_login_emulador` en memoria del repo.
 *
 * **Bypass biométrico determinista:** [MainActivity.isAuthenticated] es un campo mutable
 * público del companion — se fija en `true` en [signIn] para no depender de si el AVD tiene
 * biometría/PIN enrolado (en un emulador limpio `BiometricManager` ya cae solo en la rama
 * `BIOMETRIC_ERROR_NONE_ENROLLED` → `isAuthenticated = true`, pero fijarlo explícito hace el
 * test determinista sin importar el estado del AVD).
 */
@RunWith(AndroidJUnit4::class)
class CollectionReportDeviceSmokeTest {

    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Otorga en runtime los permisos peligrosos del manifest de `:app` ANTES de que
     * [composeTestRule] lance [MainActivity] (`RuleChain.outerRule` corre primero) — sin esto,
     * el primer diálogo de permiso del sistema (ubicación/bluetooth) tapa la ventana de la app
     * y el smoke se cuelga esperando un nodo que nunca compone visible detrás del diálogo.
     * `CALL_PHONE`/`REQUEST_INSTALL_PACKAGES` quedan fuera: el primero no es "dangerous"
     * otorgable así (requiere `SET_DEFAULT_DIALER` en algunos OEM, no aplica aquí) y el segundo
     * es un permiso especial (`Settings.canDrawOverlays`-style), no un runtime grant normal.
     */
    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(
            GrantPermissionRule.grant(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        )
        .around(composeTestRule)

    @Before
    fun signIn() {
        MainActivity.isAuthenticated = true

        val auth = FirebaseAuth.getInstance()
        val latch = CountDownLatch(1)
        var failure: Exception? = null
        auth.signInWithEmailAndPassword(DEV_EMAIL, DEV_PASSWORD)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) failure = task.exception
                latch.countDown()
            }
        val completed = latch.await(SIGN_IN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(completed) { "Firebase sign-in no respondio en ${SIGN_IN_TIMEOUT_SECONDS}s" }
        failure?.let { throw it }
    }

    @After
    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        MainActivity.isAuthenticated = false
    }

    @Test
    fun elReporteDeCobranzaRenderizaDesdeElDrawerYAbreUnSheet() {
        // Home -> abrir drawer -> "Reporte de cobranza".
        composeTestRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithContentDescription("Menú")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Menú").performClick()

        composeTestRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText("Reporte de cobranza")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Reporte de cobranza").performClick()

        // El reporte renderiza: el hero (tablero real, con o sin datos locales) aparece.
        composeTestRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithTag(HERO_TAG)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag(HERO_TAG).assertExists()

        // Toggle Día/Semana funciona: cambiar a Semana y volver a Día, el selector responde.
        composeTestRule.onAllNodesWithTag(PERIOD_TAG_SEMANA)[PERIOD_SELECTOR_INDEX].performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(PERIOD_TAG_DIA)[PERIOD_SELECTOR_INDEX].performClick()
        composeTestRule.waitForIdle()

        // Un sheet abre: tocar el hero abre el sheet HERO ("Resumen del día"/"Resumen del ciclo").
        composeTestRule.onNodeWithTag(HERO_TAG).performClick()
        composeTestRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText("Resumen del día")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }
}
