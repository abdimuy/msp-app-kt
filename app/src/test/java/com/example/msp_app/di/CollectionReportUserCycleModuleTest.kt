package com.example.msp_app.di

import com.example.msp_app.data.collectionreport.FirebaseUserCycleAdapter
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan 5, Task 10: [CollectionReportUserCycleModule] debe exponer la impl real de `UserCyclePort`
 * ([FirebaseUserCycleAdapter], que lee `userData` de Firestore), NO el fake del feature. Y NO debe
 * cachear una instancia (el módulo no es `@Singleton` — kill-switch de sesión): cada resolución
 * produce un adapter nuevo, sin congelar la fuente de red/sesión.
 */
class CollectionReportUserCycleModuleTest {

    @Test
    fun `provideUserCyclePort es la impl Firebase (userData de Firestore)`() {
        val port = CollectionReportUserCycleModule.provideUserCyclePort()

        assertTrue(port is FirebaseUserCycleAdapter)
    }

    @Test
    fun `provideUserCyclePort no congela una instancia (sin scope singleton)`() {
        val first = CollectionReportUserCycleModule.provideUserCyclePort()
        val second = CollectionReportUserCycleModule.provideUserCyclePort()

        assertNotSame(
            "UserCyclePort NO debe ser @Singleton: congelaría la sesión y rompería el kill-switch",
            first,
            second
        )
    }
}
