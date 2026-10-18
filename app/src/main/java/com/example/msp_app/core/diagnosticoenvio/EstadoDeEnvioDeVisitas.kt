package com.example.msp_app.core.diagnosticoenvio

import android.content.Context
import com.example.msp_app.BuildConfig
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.enviodiagnostico.EnvioDiagnosticoDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.entities.EnvioDiagnosticoEntity
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import java.time.Duration
import kotlinx.coroutines.CancellationException

/**
 * Arma y manda el evento `visitas_estado` (plan visitas P1, Tarea 14; H14):
 * conteos de `Visit` y fallos vigentes de visitas en `envio_diagnostico`.
 *
 * Vive en este paquete porque es la telemetría, uno de los pocos lectores
 * permitidos de `envio_diagnostico` (`EnvioDiagnosticoAislamientoTest`). Solo
 * LEE: nada de aquí decide un envío. [emitir] nunca lanza (salvo cancelación).
 */
class EstadoDeEnvioDeVisitas(
    private val visitDao: VisitDao,
    private val diagnosticoDao: EnvioDiagnosticoDao,
    private val clock: AppClock = AppClock.System,
    private val version: String = BuildConfig.VERSION_NAME
) {

    suspend fun datos(): Map<String, Any> {
        val ahora = clock.now()
        val conteos = visitDao.conteosDeEnvio(AppTime.toWireFormat(ahora.minus(VENTANA)))
        val fallos = diagnosticoDao.fallosVigentesPorCodigo(EnvioDiagnosticoEntity.TIPO_VISITA)
        return TelemetriaDeVisitas.estado(conteos, ahora, version, fallos)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun emitir() {
        val datos = try {
            datos()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return // Telemetría: una lectura fallida no tumba la corrida.
        }
        TelemetriaDeVisitas.emitir(TelemetriaDeVisitas.EVENTO_ESTADO, datos)
    }

    companion object {
        private val VENTANA: Duration = Duration.ofHours(24)

        fun de(context: Context): EstadoDeEnvioDeVisitas {
            val db = AppDatabase.getInstance(context)
            return EstadoDeEnvioDeVisitas(db.visitDao(), db.envioDiagnosticoDao())
        }
    }
}
