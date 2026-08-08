package com.example.msp_app

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural regression guard for Task 6 (Plan 1 — cimiento): [MspApplication]
 * must expose WorkManager's on-demand [Configuration] backed by Hilt's
 * [HiltWorkerFactory], and it must NOT be constructed until injected —
 * i.e. it has to arrive via `@Inject`, not a manual `new`.
 *
 * This does not build the real Dagger graph (no Hilt test infrastructure
 * exists in this module yet); it verifies, via plain reflection over the
 * compiled class, that the wiring described in the brief is actually in
 * place: `Configuration.Provider` implemented + an `@Inject` field of type
 * `HiltWorkerFactory`. The runtime behavior (WorkManager actually falling
 * back to the default constructor for our un-annotated Workers) is covered
 * end-to-end in [com.example.msp_app.workmanager.HiltWorkerFactoryFallbackTest].
 */
class MspApplicationWorkManagerConfigTest {

    @Test
    fun `MspApplication implements Configuration Provider`() {
        assertTrue(
            "MspApplication debe implementar Configuration.Provider para la " +
                "inicialización on-demand de WorkManager",
            Configuration.Provider::class.java.isAssignableFrom(MspApplication::class.java)
        )
    }

    @Test
    fun `MspApplication injects a HiltWorkerFactory field`() {
        val field = MspApplication::class.java.declaredFields
            .firstOrNull { it.type == HiltWorkerFactory::class.java }
            ?: error("MspApplication no declara ningún campo HiltWorkerFactory")

        assertTrue(
            "el campo HiltWorkerFactory debe estar anotado @Inject",
            field.isAnnotationPresent(Inject::class.java)
        )
    }
}
