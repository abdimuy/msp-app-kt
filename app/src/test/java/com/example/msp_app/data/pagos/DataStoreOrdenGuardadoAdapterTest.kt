package com.example.msp_app.data.pagos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.port.OrdenGuardado
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [DataStoreOrdenGuardadoAdapter] contra un DataStore de archivo temporal —
 * JVM puro, mismo criterio que `DataStoreSettingsRepositoryTest`: no necesita
 * Robolectric ni `Context` Android, porque el adaptador recibe el
 * [DataStore] ya resuelto.
 *
 * Las claves crudas (`"orden_lista_clientes_orden"`/`"orden_lista_clientes_dia"`)
 * se repiten tal cual las declara `OrdenListaClientesKeys` — DataStore
 * Preferences identifica una clave por su STRING, no por referencia, así que
 * esta prueba puede fabricar un valor corrupto sin ver ese objeto `internal`,
 * igual que `DataStoreSettingsRepositoryTest` hace con `"font_size_level"`.
 */
class DataStoreOrdenGuardadoAdapterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val clock = FakeClock(Instant.parse("2026-10-05T12:00:00Z"))
    private val telemetria = RecordingTelemetry(clock)

    private fun newDataStore(): DataStore<Preferences> {
        val file = File(tempFolder.newFolder(), "test_orden_lista_clientes.preferences_pb")
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun adaptador(dataStore: DataStore<Preferences> = newDataStore()) =
        DataStoreOrdenGuardadoAdapter(dataStore = dataStore, telemetry = telemetria)

    private fun errores(code: String) =
        telemetria.recorded.filter { it.type == TelemetryEventType.ERROR && it.name == code }

    @Test
    fun `nunca se guardo nada devuelve null`() = runTest {
        assertNull(adaptador().leer())
    }

    @Test
    fun `guardar y leer hacen ida y vuelta`() = runTest {
        val dataStore = newDataStore()
        val adaptador = adaptador(dataStore)
        val dia = LocalDate.parse("2026-10-05")

        adaptador.guardar(OrdenDeLaLista.MAS_CERCA, dia)

        assertEquals(OrdenGuardado(OrdenDeLaLista.MAS_CERCA, dia), adaptador.leer())
    }

    /**
     * La regla de "cae a Cobranza si es de otro día" vive en `:feature:pagos`
     * y ya está probada ahí (ver el KDoc de `OrdenGuardadoPort`): este
     * adaptador sólo tiene que devolver la fecha TAL CUAL se guardó, sin
     * decidir si todavía aplica.
     */
    @Test
    fun `una fecha distinta a hoy se devuelve tal cual, sin decidir vigencia`() = runTest {
        val dataStore = newDataStore()
        val adaptador = adaptador(dataStore)
        val diaViejo = LocalDate.parse("2026-01-01")

        adaptador.guardar(OrdenDeLaLista.MAYOR_SALDO, diaViejo)

        assertEquals(OrdenGuardado(OrdenDeLaLista.MAYOR_SALDO, diaViejo), adaptador.leer())
    }

    @Test
    fun `un literal de enum que este build no conoce degrada a null y se reporta`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("orden_lista_clientes_orden")] = "ORDEN_RETIRADO_DEL_CATALOGO"
            prefs[stringPreferencesKey("orden_lista_clientes_dia")] = "2026-10-05"
        }

        assertNull(adaptador(dataStore).leer())
        assertEquals(1, errores(PagosTelemetria.CODE_ORDEN_GUARDADO_ILEGIBLE).size)
    }

    @Test
    fun `una fecha ilegible degrada a null y se reporta`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("orden_lista_clientes_orden")] = OrdenDeLaLista.NOMBRE.name
            prefs[stringPreferencesKey("orden_lista_clientes_dia")] = "no-es-una-fecha"
        }

        assertNull(adaptador(dataStore).leer())
        assertEquals(1, errores(PagosTelemetria.CODE_ORDEN_GUARDADO_ILEGIBLE).size)
    }

    /**
     * **Hallazgo 7 de la revisión final** (`.superpowers/sdd/2026-10-06-menu-lateral/final-fix-report.md`):
     * `DataStore.edit` puede lanzar [IOException] (disco lleno, archivo
     * dañado, permiso retirado) y `guardar` no la atrapaba — un disco lleno
     * mientras el cobrador elegía un orden podía tronar la app entera. Debe
     * reportarse y NO relanzarse: la elección sigue aplicada en memoria esta
     * sesión aunque no se haya podido persistir.
     *
     * [DataStoreQueTruena] es un fake —no un mock— que implementa
     * [DataStore] directo, como exige la regla "fakes-only, sin MockK" del
     * repo.
     */
    @Test
    fun `IOException al guardar se reporta y no se relanza`() = runTest {
        val adaptador = adaptador(DataStoreQueTruena())

        // No debe lanzar: si todavía no está atrapada, esta línea tumba la prueba.
        adaptador.guardar(OrdenDeLaLista.MAYOR_SALDO, LocalDate.parse("2026-10-05"))

        assertEquals(1, errores(PagosTelemetria.CODE_ORDEN_GUARDADO_NO_SE_PUDO_ESCRIBIR).size)
    }

    /** Un [DataStore] fingido cuya escritura siempre falla con [IOException]. */
    private class DataStoreQueTruena : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            throw IOException("disco lleno")
        }
    }
}
