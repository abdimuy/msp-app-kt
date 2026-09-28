package com.example.msp_app.core.sync.cobranza

import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import java.io.IOException
import retrofit2.HttpException

/** Cómo terminó una corrida de la reparación. */
enum class ResultadoDeLaReparacion {
    /** Todo lo que había que revisar quedó revisado. */
    COMPLETA,

    /** Falló algo transitorio (red, 5xx, sesión): hay que volver a correrla. */
    REINTENTAR
}

/**
 * **Repara, en el teléfono, lo que dejó el defecto de la condonación**: las
 * condonaciones "fantasma" y los saldos negativos. El servidor nunca tuvo el
 * error (no permite saldos negativos); sólo el teléfono.
 *
 * ## Qué hace, en orden
 *
 * Una fantasma o una venta que falla por algo transitorio **no frena al
 * resto**: se salta, se recuerda y la corrida termina en
 * [ResultadoDeLaReparacion.REINTENTAR] sólo si quedó algo pendiente.
 *
 * 1. **Fantasmas** (`PaymentDao.condonacionesFantasma`): condonaciones soltadas
 *    sin documento. Por cada una pregunta al servidor por su id con el canal que
 *    ya existe, `GET /v2/cobranza/pagos/{id}` (el de `RECONCILED_VIA_GET` en
 *    `PendingPaymentsWorker`):
 *    - **404 del propio API** (`problem+json`, `ErrPagoNoEncontrado`) → nunca
 *      se aplicó: el servidor valida el saldo ANTES de guardar el pago
 *      (`crear_pago.go`, `validateCargo`), así que un rechazo por saldo no deja
 *      fila. Se marca rechazada (-1) sin tocar el saldo.
 *    - **200 con documento** → se aplicó: se anota su `DOCTO_CC_ID`.
 *    - **200 sin documento** (pendiente del lado del servidor) o un 4xx
 *      permanente → se deja como está: no hay evidencia para cambiarla.
 *    - Red, 5xx, un 404 que no es del API (túnel), 401/408/409/425/429 →
 *      queda pendiente y se reintenta después, sin frenar a las demás.
 * 2. **Ventas a refrescar**: las de esas condonaciones y toda venta con saldo
 *    negativo. Cada una pasa por [RefrescoDelSaldoDeLaVenta] —bajo el mutex del
 *    sync, `by-ids` y la fórmula de `mergeVentas`— y, si el servidor no la trae
 *    y su saldo es negativo, se sube a cero. **Nunca se sube un saldo sin dato
 *    del servidor**: uno `>= 0` sin dato se queda como está.
 *
 * No borra filas, no toca abonos ni visitas y no escribe en el servidor.
 *
 * ## Idempotente
 *
 * Todo lo que hace se puede repetir: una fantasma marcada o anotada deja de ser
 * fantasma, el refresco escribe un valor absoluto y la nivelación sólo toca
 * negativos. Si el proceso muere a mitad, la siguiente corrida retoma lo que
 * falta sin deshacer lo hecho.
 */
class ReparacionDeCondonaciones(
    private val pagosApi: V2PaymentsApi,
    private val refresco: RefrescoDelSaldoDeLaVenta,
    private val db: AppDatabase
) {

    suspend fun reparar(): ResultadoDeLaReparacion {
        val paymentDao = db.paymentDao()
        // cargo (`sales.DOCTO_CC_ID` = `Payment.DOCTO_CC_ACR_ID`) → zona.
        val porRefrescar = linkedMapOf<Int, Int>()
        // Lo que no se pudo resolver en esta corrida. Una fantasma que falla NO
        // frena a las demás ni a la nivelación: se salta, se recuerda, y la
        // corrida pide reintento sólo al final.
        val pendientes = mutableListOf<String>()
        for (fantasma in paymentDao.condonacionesFantasma()) {
            when (val veredicto = veredictoDe(fantasma)) {
                Veredicto.NoExiste -> paymentDao.marcarCondonacionFantasma(fantasma.ID)
                is Veredicto.Aplicada -> paymentDao.updateDoctoCcId(
                    fantasma.ID,
                    veredicto.documento
                )
                Veredicto.SinEvidencia -> continue
                Veredicto.Reintentar -> {
                    pendientes += "condonacion ${fantasma.ID}"
                    continue
                }
            }
            val cargo = fantasma.DOCTO_CC_ACR_ID
            porRefrescar.putIfAbsent(cargo, fantasma.ZONA_CLIENTE_ID)
        }
        for (negativa in db.saleDao().ventasConSaldoNegativo()) {
            val cargo = negativa.DOCTO_CC_ID
            porRefrescar.putIfAbsent(cargo, negativa.ZONA_CLIENTE_ID)
        }
        for ((cargo, zona) in porRefrescar) {
            try {
                refresco.refrescar(zona = zona, cargo = cargo, nivelarNegativoSinDato = true)
            } catch (e: IOException) {
                pendientes += "venta $cargo"
            } catch (e: HttpException) {
                // Un 4xx permanente para ESTA venta: se deja como está y se sigue.
                if (esTransitorio(e.code())) pendientes += "venta $cargo"
            }
        }
        ultimosPendientes = pendientes.toList()
        return if (pendientes.isEmpty()) {
            ResultadoDeLaReparacion.COMPLETA
        } else {
            ResultadoDeLaReparacion.REINTENTAR
        }
    }

    /**
     * Lo que la última corrida no pudo resolver (sin datos personales: ids de
     * captura y de venta). El trabajo lo deja en el log si se rinde por tope.
     */
    var ultimosPendientes: List<String> = emptyList()
        private set

    private suspend fun veredictoDe(fantasma: PaymentEntity): Veredicto = try {
        val documento = pagosApi.obtenerPago(fantasma.ID).docto_cc_id
        if (documento != null && documento > 0) {
            Veredicto.Aplicada(
                documento
            )
        } else {
            Veredicto.SinEvidencia
        }
    } catch (e: IOException) {
        Veredicto.Reintentar
    } catch (e: HttpException) {
        when {
            e.code() == HTTP_NOT_FOUND && esDelApi(e) -> Veredicto.NoExiste
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

    private fun esTransitorio(code: Int): Boolean =
        code >= HTTP_5XX || code in HTTP_4XX_DE_REINTENTO

    private sealed interface Veredicto {
        data object NoExiste : Veredicto
        data class Aplicada(val documento: Int) : Veredicto
        data object SinEvidencia : Veredicto
        data object Reintentar : Veredicto
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_5XX = 500
        val HTTP_4XX_DE_REINTENTO = setOf(401, 408, 409, 425, 429)
    }
}
