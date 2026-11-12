package com.example.msp_app.data.diadecobro

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.api.services.diadecobro.DiaDeCobroApi
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/** Qué pasó al intentar bajar el día de cobro. */
sealed interface ResultadoDeDiaDeCobro {
    /** Se bajó y se guardó. */
    data class Descargado(val ventas: Int) : ResultadoDeDiaDeCobro

    /** Ya se bajó hoy para esta zona; no se pidió nada. */
    data object YaAlDia : ResultadoDeDiaDeCobro

    /** Falló; **lo último guardado se queda intacto**. */
    data class Fallo(val causa: Throwable) : ResultadoDeDiaDeCobro
}

/**
 * Baja el día de cobro de la zona y lo guarda, **una vez al día**.
 *
 * Es el ÚNICO camino de escritura y solo toca [DiaDeCobroAlmacen]: ni una tabla
 * de ventas o pagos, ni el cursor ni el estado del sync de cobranza. Un fallo
 * (sin red, 5xx, respuesta rota) deja intacto lo guardado y devuelve
 * [ResultadoDeDiaDeCobro.Fallo]; el detalle de venta sigue enseñando lo anterior.
 *
 * El `DiaDeCobroApi` entra ya resuelto por quien lo construye **en cada
 * ejecución** (`V2ApiProvider.create`), nunca guardado aquí: así no se congela
 * la baseURL y el kill-switch sigue mandando.
 */
class DescargarDiaDeCobro(
    private val api: DiaDeCobroApi,
    private val almacen: DiaDeCobroAlmacen,
    private val clock: AppClock = AppClock.System,
    private val telemetry: Telemetry? = null
) {

    @Suppress("TooGenericExceptionCaught") // cualquier fallo conserva lo guardado
    suspend operator fun invoke(zona: Int, forzar: Boolean = false): ResultadoDeDiaDeCobro {
        val ahora = clock.now()
        if (!forzar && !vencido(almacen.leer(), zona, ahora)) return ResultadoDeDiaDeCobro.YaAlDia
        return try {
            val respuesta = api.diaDeCobro(zona)
            // Una respuesta sin la lista de ventas no es "zona vacía": es una
            // respuesta que no se entiende, y NO pisa lo guardado.
            val ventas = respuesta.ventas ?: error("respuesta sin ventas")
            almacen.guardar(DiaDeCobroGuardado(zona, ahora, respuesta))
            ResultadoDeDiaDeCobro.Descargado(ventas.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportar(e)
            ResultadoDeDiaDeCobro.Fallo(e)
        }
    }

    private fun reportar(e: Exception) {
        val props = if (e is HttpException) {
            mapOf(DiaDeCobroTelemetria.PROP_HTTP to e.code().toString())
        } else {
            mapOf(DiaDeCobroTelemetria.PROP_EXCEPCION to e.javaClass.simpleName)
        }
        telemetry?.error(
            code = DiaDeCobroTelemetria.CODE_DESCARGA_FALLO,
            message = "no se pudo bajar el día de cobro",
            props = props
        )
    }

    internal companion object {
        /**
         * Toca bajar cuando no hay nada guardado, cuando lo guardado es de otra
         * zona o cuando no se bajó **hoy** (día de la oficina).
         */
        fun vencido(guardado: DiaDeCobroGuardado?, zona: Int, ahora: java.time.Instant): Boolean =
            guardado == null ||
                guardado.zona != zona ||
                AppTime.toBusinessDate(guardado.descargadoEn) != AppTime.toBusinessDate(ahora)
    }
}

/**
 * Qué zona baja el worker: la ACTUAL del cobrador si ya se conoce (manda sobre la
 * que viajó en el trabajo, que pudo encolarse antes de un cambio de zona), si no
 * la del trabajo, y al final la última guardada. `null` = no hay zona que bajar.
 */
fun zonaDelDiaDeCobro(actual: Int?, delTrabajo: Int?, guardada: Int?): Int? =
    listOf(actual, delTrabajo, guardada).firstOrNull { it != null && it > 0 }
