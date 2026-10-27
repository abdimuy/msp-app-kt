package com.example.msp_app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-124 · Permisos de los avisos (plan P3, Tarea 13). Lee el manifiesto del módulo. */
class ManifiestoDeAvisosTest {

    private val manifiesto: String by lazy {
        File("src/main/AndroidManifest.xml").readText()
    }

    private fun declara(permiso: String) =
        manifiesto.contains("""<uses-permission android:name="android.permission.$permiso" />""")

    @Test
    fun `el control positivo encuentra un permiso que si esta - E-APP-124`() {
        assertTrue(declara("ACCESS_FINE_LOCATION"))
    }

    @Test
    fun `declara las alarmas exactas por permiso y nunca USE_EXACT_ALARM - E-APP-124`() {
        assertTrue(declara("SCHEDULE_EXACT_ALARM"))
        assertTrue(declara("POST_NOTIFICATIONS"))
        assertTrue(declara("RECEIVE_BOOT_COMPLETED"))
        assertFalse(manifiesto.contains("USE_EXACT_ALARM"))
    }

    @Test
    fun `los receptores estan declarados con sus acciones - E-APP-124`() {
        assertTrue(manifiesto.contains(".core.avisos.AvisoAlarmReceiver"))
        assertTrue(manifiesto.contains(".core.avisos.ReprogramarAlarmasReceiver"))
        listOf(
            "BOOT_COMPLETED",
            "MY_PACKAGE_REPLACED",
            "TIME_SET",
            "TIMEZONE_CHANGED",
            "SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        ).forEach { assertTrue(it, manifiesto.contains("action.$it")) }
    }

    @Test
    fun `no declara el permiso restringido de bateria sino que abre la lista del sistema - E-APP-134`() {
        assertFalse(manifiesto.contains("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
    }
}
