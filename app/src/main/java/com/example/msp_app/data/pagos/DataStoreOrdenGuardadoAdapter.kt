package com.example.msp_app.data.pagos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.port.OrdenGuardado
import com.example.msp_app.feature.pagos.domain.port.OrdenGuardadoPort
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Claves DataStore de [DataStoreOrdenGuardadoAdapter] — nombradas con el
 * prefijo del archivo (`PagosPortsModule.provideOrdenGuardadoPort` lo abre
 * como `"orden_lista_clientes"`) porque DataStore Preferences identifica una
 * clave por su STRING, no por referencia: un test puede escribir un valor
 * crudo con `stringPreferencesKey("orden_lista_clientes_orden")` sin ver este
 * objeto, igual que `DataStoreSettingsRepositoryTest` hace con
 * `"font_size_level"`.
 */
internal object OrdenListaClientesKeys {
    val ORDEN = stringPreferencesKey("orden_lista_clientes_orden")
    val DIA = stringPreferencesKey("orden_lista_clientes_dia")
}

/**
 * Implementación real de [OrdenGuardadoPort] — ver su KDoc para por qué un
 * puerto con adaptador en `:app` y no `SavedStateHandle`.
 *
 * Recibe el [DataStore] ya resuelto (no un `Context`) — mismo reparto que
 * [com.example.msp_app.core.settings.DataStoreSettingsRepository]: quien
 * decide EN QUÉ ARCHIVO vive esto es `PagosPortsModule`
 * (`"orden_lista_clientes"`, archivo propio y NO una clave más de
 * `SettingsRepository` — esa interfaz documenta que persiste "las 3
 * preferencias que la pantalla de Configuración expone", y el orden de la
 * lista de clientes no es una de ellas—); este adaptador sólo sabe leer y
 * escribir sobre el `DataStore` que le dan, lo que lo deja probar con uno de
 * archivo temporal, sin Robolectric ni `Context` Android.
 *
 * Guarda el `name` del enum (no su índice ordinal, igual que
 * [com.example.msp_app.core.settings.DataStoreSettingsRepository.setFontSizeLevel])
 * junto con el día en formato ISO (`LocalDate.toString()`/`LocalDate.parse`).
 *
 * **Robusto ante datos corruptos, igual que `DataStoreSettingsRepository`**:
 * un archivo que no se puede leer ([IOException]), un literal de enum que este
 * build ya no conoce, o una fecha ilegible —todos caminos de "versión vieja de
 * la app" o "corrupción parcial"— degradan a `null` en vez de lanzar. [leer]
 * nunca truena: quien lo llama solo sabe manejar "lo último guardado" o "nunca
 * se eligió nada", nunca una excepción.
 */
class DataStoreOrdenGuardadoAdapter(
    private val dataStore: DataStore<Preferences>,
    private val telemetry: Telemetry
) : OrdenGuardadoPort {

    override suspend fun leer(): OrdenGuardado? {
        val prefs = dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .first()

        val ordenCrudo = prefs[OrdenListaClientesKeys.ORDEN] ?: return null
        val diaCrudo = prefs[OrdenListaClientesKeys.DIA] ?: return null

        val orden = try {
            OrdenDeLaLista.valueOf(ordenCrudo)
        } catch (ilegible: IllegalArgumentException) {
            reportarIlegible(ilegible)
            return null
        }
        val dia = try {
            LocalDate.parse(diaCrudo)
        } catch (ilegible: DateTimeParseException) {
            reportarIlegible(ilegible)
            return null
        }

        return OrdenGuardado(orden = orden, dia = dia)
    }

    /**
     * **No relanza [IOException]** (hallazgo de la revisión final de
     * `feat/lista-venta-nueva-orden`): `DataStore.edit` puede fallar por disco
     * lleno, archivo dañado o permiso retirado, y antes de este arreglo eso
     * tumbaba la pantalla entera por el simple hecho de elegir un orden. El
     * orden elegido YA quedó aplicado en memoria —[ControladorDeOrden.orden]
     * se asigna antes de llamar a [guardar]— así que degradar aquí no deja al
     * cobrador sin lo que acaba de elegir, sólo sin que sobreviva "durante el
     * día" si el teléfono se reinicia.
     */
    override suspend fun guardar(orden: OrdenDeLaLista, dia: LocalDate) {
        try {
            dataStore.edit { prefs ->
                prefs[OrdenListaClientesKeys.ORDEN] = orden.name
                prefs[OrdenListaClientesKeys.DIA] = dia.toString()
            }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: IOException) {
            reportarFalloDeEscritura(fallo)
        }
    }

    /**
     * Un `name` de enum o una fecha que este build no puede interpretar. No es
     * dinero y [leer] ya degradó a `null` —la lista cae al orden de cobranza
     * por default—, pero un valor guardado que deja de leerse tiene que quedar
     * grepeable. Ningún dato de cliente viaja: solo el nombre de la excepción.
     */
    private fun reportarIlegible(fallo: Throwable) {
        telemetry.error(
            code = PagosTelemetria.CODE_ORDEN_GUARDADO_ILEGIBLE,
            message = "el orden guardado de la lista de clientes no se pudo interpretar",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
    }

    /**
     * `DataStore.edit` falló al escribir (disco lleno, archivo dañado, permiso
     * retirado). No es dinero y el orden sigue aplicado en memoria esta
     * sesión —ver el KDoc de [guardar]—, pero una escritura que deja de
     * persistirse tiene que quedar grepeable.
     */
    private fun reportarFalloDeEscritura(fallo: Throwable) {
        telemetry.error(
            code = PagosTelemetria.CODE_ORDEN_GUARDADO_NO_SE_PUDO_ESCRIBIR,
            message = "el orden de la lista de clientes no se pudo guardar",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
    }
}
