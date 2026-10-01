package com.example.msp_app.core.sync.cobranza

import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.features.sales.SaleIdSpaces
import java.io.IOException
import retrofit2.HttpException

/** Qué pasó en una corrida de [ResolucionDeCapturasSueltas]. */
data class ResumenDeLaResolucion(
    /** Capturas sueltas que se le preguntaron al servidor. */
    val revisadas: Int = 0,
    /** Aplicadas con sus dos pruebas: documento anotado, gemelo colapsado. */
    val reconocidas: Int = 0,
    /** El servidor no las tiene: marcadas no aplicadas (-1). */
    val noAplicadas: Int = 0,
    /** Sin evidencia para cambiarlas: se quedan como estaban. */
    val sinEvidencia: Int = 0,
    /** Lo que falló por algo transitorio y hay que volver a intentar. */
    val pendientes: List<String> = emptyList(),
    /**
     * Para oficina, sin datos personales: `id|cargo|importe` de cada captura que
     * el servidor no tiene (el cobrador trae ese dinero) y de cada una que se
     * quedó sin evidencia (sigue restando y alguien la tiene que revisar).
     */
    val detalleNoAplicadas: List<String> = emptyList(),
    val detalleSinEvidencia: List<String> = emptyList()
) {
    val completa: Boolean get() = pendientes.isEmpty()
}

/**
 * **Resuelve las capturas sueltas: abonos que el teléfono dio por subidos
 * (`GUARDADO_EN_MICROSIP = 1`) sin documento (`DOCTO_CC_ID = 0`) y que el sync
 * nunca nombra** (E-APP-048).
 *
 * ## Por qué existe
 *
 * Desde 2.18.0 el saldo de una venta es el del servidor menos
 * `sumImporteNoReconocidoPorElServidor`, que resta exactamente esas filas. Si el
 * servidor sí aplicó el abono, se resta dos veces. Medido el 2026-09-30/10-01:
 * 2,206 capturas así en 30 de 50 teléfonos, 1,725 ventas por debajo de Microsip
 * ($359,860); 2,188 eran abonos del 8 al 13 de agosto que el API Node aplicó sin
 * dejar `MSP_PAGOS_RECIBIDOS.IMPTE_DOCTO_CC_ID`, así que el sync no puede
 * nombrarlos y ningún colapso los alcanza.
 *
 * ## Qué hace con cada una
 *
 * Le pregunta al servidor por su id (`GET /v2/cobranza/pagos/{id}`, el mismo
 * canal que [ReparacionDeCondonaciones]) y **sólo actúa con evidencia**:
 *
 * - **Aplicada**: el servidor da el documento **y** ya bajó por el sync el abono
 *   numérico con ese documento, en el mismo cargo y por el mismo importe
 *   (`CapturasSueltasDao.gemelosDelServidor`). Las dos pruebas hacen falta: para la era
 *   Node el `GET` trae cargo e importe en cero, y contesta 200 aunque el
 *   documento ya no exista en `DOCTOS_CC` (medido en dev el 2026-10-01). Se
 *   anota el documento, se colapsa la copia con el colapso legacy de siempre
 *   (`deleteLegacyTwinsByDoctoCcIds`, con sus tres cerrojos) y se fija el saldo
 *   al del servidor — **todo en una transacción, bajo el mutex del sync**, con
 *   [RefrescoDelSaldoDeLaVenta]. Si no se pudo leer el saldo, no se anota nada.
 * - **No existe**: 404 del propio API (`problem+json`) que dice
 *   `pago_no_encontrado`. Se marca -1 (deja de restarse; la fila se queda) y se
 *   fija el saldo al del servidor, en la misma transacción. Decisión del dueño
 *   del 2026-09-29: el servidor es la verdad.
 * - **Cualquier otra respuesta** —documento sin gemelo, 200 pendiente, un 404
 *   del API sin ese código, un 4xx permanente— **no se toca**.
 * - **Red, 5xx, 401/408/409/425/429 o un 404 que no es del API** (túnel) →
 *   pendiente, se reintenta; una que falla no frena a las demás.
 *
 * Nunca decide por monto ni por hora, nunca sube un saldo sin dato del
 * servidor y nunca borra una captura que el servidor no nombró: lo único que se
 * borra es la copia cuyo gemelo con el mismo documento ya está en el teléfono.
 *
 * ## Idempotente
 *
 * Una captura reconocida o marcada deja de ser suelta; una sin evidencia se
 * vuelve a preguntar en la siguiente corrida sin cambiar nada.
 */
class ResolucionDeCapturasSueltas(
    private val pagosApi: V2PaymentsApi,
    private val refresco: RefrescoDelSaldoDeLaVenta,
    private val db: AppDatabase
) {

    suspend fun resolver(): ResumenDeLaResolucion {
        val capturas = db.capturasSueltasDao()
        var revisadas = 0
        var reconocidas = 0
        var noAplicadas = 0
        var sinEvidencia = 0
        val pendientes = mutableListOf<String>()
        val detalleNoAplicadas = mutableListOf<String>()
        val detalleSinEvidencia = mutableListOf<String>()
        for (suelta in capturas.capturasSueltas()) {
            revisadas++
            val veredicto = veredictoDe(suelta)
            val resultado = try {
                when (veredicto) {
                    is Veredicto.Aplicada -> reconocer(suelta, veredicto.documento)
                    Veredicto.NoExiste -> marcarNoAplicada(suelta)
                    Veredicto.SinEvidencia -> Resultado.SIN_EVIDENCIA
                    Veredicto.Reintentar -> Resultado.PENDIENTE
                }
            } catch (e: IOException) {
                Resultado.PENDIENTE
            } catch (e: HttpException) {
                if (esTransitorio(e.code())) Resultado.PENDIENTE else Resultado.SIN_EVIDENCIA
            }
            when (resultado) {
                Resultado.RECONOCIDA -> reconocidas++
                Resultado.NO_APLICADA -> {
                    noAplicadas++
                    detalleNoAplicadas += detalle(suelta)
                }
                Resultado.SIN_EVIDENCIA -> {
                    sinEvidencia++
                    detalleSinEvidencia += detalle(suelta)
                }
                Resultado.PENDIENTE -> pendientes += suelta.ID
            }
        }
        return ResumenDeLaResolucion(
            revisadas = revisadas,
            reconocidas = reconocidas,
            noAplicadas = noAplicadas,
            sinEvidencia = sinEvidencia,
            pendientes = pendientes,
            detalleNoAplicadas = detalleNoAplicadas,
            detalleSinEvidencia = detalleSinEvidencia
        )
    }

    private fun detalle(suelta: PaymentEntity): String =
        "${suelta.ID}|${suelta.DOCTO_CC_ACR_ID}|${suelta.IMPORTE}"

    /**
     * Segunda prueba y escritura atómica. El gemelo se vuelve a contar dentro de
     * la transacción: si entre la pregunta y la escritura desapareció, no se
     * anota nada.
     */
    private suspend fun reconocer(suelta: PaymentEntity, documento: Int): Resultado {
        val capturas = db.capturasSueltasDao()
        if (!tieneGemelo(suelta, documento)) return Resultado.SIN_EVIDENCIA
        var anotada = false
        refresco.refrescar(
            zona = suelta.ZONA_CLIENTE_ID,
            cargo = SaleIdSpaces.forSalePayments(suelta)
        ) {
            if (tieneGemelo(suelta, documento) &&
                capturas.anotarDocumentoDeCapturaSuelta(suelta.ID, documento) == 1
            ) {
                db.paymentDao().deleteLegacyTwinsByDoctoCcIds(listOf(documento))
                anotada = true
            }
        }
        return if (anotada) Resultado.RECONOCIDA else Resultado.SIN_EVIDENCIA
    }

    private suspend fun tieneGemelo(suelta: PaymentEntity, documento: Int): Boolean =
        db.capturasSueltasDao().gemelosDelServidor(
            documento = documento,
            cargo = suelta.DOCTO_CC_ACR_ID,
            importe = suelta.IMPORTE
        ) > 0

    private suspend fun marcarNoAplicada(suelta: PaymentEntity): Resultado {
        var marcada = false
        refresco.refrescar(
            zona = suelta.ZONA_CLIENTE_ID,
            cargo = SaleIdSpaces.forSalePayments(suelta)
        ) {
            marcada = db.capturasSueltasDao().marcarCapturaSueltaNoAplicada(suelta.ID) == 1
        }
        return if (marcada) Resultado.NO_APLICADA else Resultado.SIN_EVIDENCIA
    }

    private suspend fun veredictoDe(suelta: PaymentEntity): Veredicto = try {
        val documento = pagosApi.obtenerPago(suelta.ID).docto_cc_id
        if (documento != null && documento > 0) {
            Veredicto.Aplicada(documento)
        } else {
            Veredicto.SinEvidencia
        }
    } catch (e: IOException) {
        Veredicto.Reintentar
    } catch (e: HttpException) {
        when {
            e.code() == HTTP_NOT_FOUND && esDelApi(e) ->
                if (diceNoEncontrado(e)) Veredicto.NoExiste else Veredicto.SinEvidencia
            e.code() == HTTP_NOT_FOUND || esTransitorio(e.code()) -> Veredicto.Reintentar
            else -> Veredicto.SinEvidencia
        }
    }

    /**
     * El 404 sólo prueba "no existe" si lo contestó el API: su `problem+json`.
     * Un 404 de túnel o proxy es que la petición no llegó.
     */
    private fun esDelApi(e: HttpException): Boolean =
        e.response()?.headers()?.get("Content-Type").orEmpty()
            .contains("problem+json", ignoreCase = true)

    /**
     * El API pone el código en `errors[].message` (`code=pago_no_encontrado`,
     * medido en dev el 2026-10-01), no en un `code` de primer nivel. Basta con
     * que el cuerpo lo nombre; sin él, un 404 del API no prueba la ausencia.
     */
    private fun diceNoEncontrado(e: HttpException): Boolean = try {
        e.response()?.errorBody()?.string().orEmpty().contains(CODIGO_NO_ENCONTRADO)
    } catch (_: IOException) {
        false
    }

    private fun esTransitorio(code: Int): Boolean =
        code >= HTTP_5XX || code in HTTP_4XX_DE_REINTENTO

    private enum class Resultado { RECONOCIDA, NO_APLICADA, SIN_EVIDENCIA, PENDIENTE }

    private sealed interface Veredicto {
        data object NoExiste : Veredicto
        data class Aplicada(val documento: Int) : Veredicto
        data object SinEvidencia : Veredicto
        data object Reintentar : Veredicto
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_5XX = 500
        const val CODIGO_NO_ENCONTRADO = "pago_no_encontrado"
        val HTTP_4XX_DE_REINTENTO = setOf(401, 408, 409, 425, 429)
    }
}
