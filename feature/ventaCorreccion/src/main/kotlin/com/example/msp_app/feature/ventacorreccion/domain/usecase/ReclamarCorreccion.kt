package com.example.msp_app.feature.ventacorreccion.domain.usecase

import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.VentaLocalParaCorregir
import com.example.msp_app.feature.ventacorreccion.domain.evaluarCorregibilidad
import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import com.example.msp_app.feature.ventacorreccion.domain.port.RelojPort
import com.example.msp_app.feature.ventacorreccion.domain.port.VentaLocalCorreccionPort
import java.util.UUID
import javax.inject.Inject

/** Resultado de intentar reclamar una venta para corregirla. */
sealed interface ResultadoReclamo {
    /** El candado se tomó — [claimId] identifica esta sesión de edición. */
    data class Reclamada(val claimId: String, val venta: VentaLocalParaCorregir) : ResultadoReclamo

    /**
     * No se pudo tomar el candado. [estado] explica por qué — casi siempre
     * [EstadoCorreccion.YaSeEnvio], [EstadoCorreccion.LaRevisaLaOficina] o
     * [EstadoCorreccion.SeEstaEnviando] (nunca [EstadoCorreccion.Corregible]: si el candado se
     * pudo tomar, [Reclamada] es el resultado; esta rama sólo se alcanza cuando NO se pudo).
     */
    data class NoCorregible(val estado: EstadoCorreccion) : ResultadoReclamo

    /** La venta no existe (borrada, o el ID no corresponde a ninguna fila). */
    data object NoExiste : ResultadoReclamo
}

/**
 * Abre el editor: intenta tomar el candado de EDICIÓN. **Reentrante** — un `EDIT` vivo del
 * MISMO teléfono se toma de nuevo con un `claimId` fresco (decisión del orquestador, Task 3:
 * ver `LocalSaleDao.claimForEdit`). Al tomarlo, cancela por cortesía cualquier trabajo de
 * subida encolado ([ReencolarSubidaPort.cancelarTrabajoEncolado]) — nunca requerido para la
 * correctitud, el fence de Task 4 es quien de verdad frena al subidor si de todas formas ya
 * arrancó.
 */
class ReclamarCorreccion @Inject constructor(
    private val port: VentaLocalCorreccionPort,
    private val reloj: RelojPort,
    private val reencolar: ReencolarSubidaPort
) {
    suspend operator fun invoke(saleId: String): ResultadoReclamo {
        val claimId = UUID.randomUUID().toString()
        val ahora = reloj.ahoraEpochMillis()

        val tomado = port.reclamarParaEditar(saleId, claimId, ahora)
        if (!tomado) {
            val estadoActual = port.leerEstado(saleId) ?: return ResultadoReclamo.NoExiste
            return ResultadoReclamo.NoCorregible(
                evaluarCorregibilidad(
                    enviado = estadoActual.enviado,
                    permanente = estadoActual.permanente,
                    correccionNoEnviada = estadoActual.correccionNoEnviada,
                    claimKind = estadoActual.claimKind,
                    claimedAt = estadoActual.claimedAt,
                    ahora = ahora
                )
            )
        }

        // Cortesía, nunca un requisito: si esto lanza, el candado ya se tomó igual.
        runCatching { reencolar.cancelarTrabajoEncolado(saleId) }

        val venta = port.leerVenta(saleId) ?: return ResultadoReclamo.NoExiste
        return ResultadoReclamo.Reclamada(claimId, venta)
    }
}
