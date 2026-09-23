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
    /**
     * El candado se tomó — [claimId] identifica esta sesión de edición.
     *
     * [yaEnviada] dice si el servidor YA tiene esta venta, porque no todos los campos se pueden
     * corregir igual en los dos casos. Una venta sin enviar viaja entera en su `POST`, así que
     * todo es editable. Una ya enviada se corrige con tres peticiones (header, cliente, líneas)
     * y **el tipo de venta no tiene endpoint en ninguna de las tres**: dejar que se cambie sería
     * prometer algo que no se puede cumplir, y el cambio se quedaría en el teléfono sin que
     * nadie avise. Ver `EditSaleScreen`, que lo usa para poner ese campo de sólo lectura.
     */
    data class Reclamada(
        val claimId: String,
        val venta: VentaLocalParaCorregir,
        val yaEnviada: Boolean
    ) : ResultadoReclamo

    /**
     * No se pudo tomar el candado. [estado] explica por qué — casi siempre
     * [EstadoCorreccion.LaRevisaLaOficina], [EstadoCorreccion.SeEstaEnviando],
     * [EstadoCorreccion.CorreccionEnCamino] o [EstadoCorreccion.LaOficinaYaLaAplico].
     *
     * Nunca los DOS estados que sí dejan corregir ([EstadoCorreccion.Corregible] y, desde el
     * nivel 2, [EstadoCorreccion.CorregibleEnviada]): si el candado se pudo tomar, [Reclamada] es
     * el resultado; esta rama sólo se alcanza cuando NO se pudo.
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
                    correccionRemotaPendiente = estadoActual.correccionRemotaPendiente,
                    correccionRemotaEstado = estadoActual.correccionRemotaEstado,
                    claimKind = estadoActual.claimKind,
                    claimedAt = estadoActual.claimedAt,
                    ahora = ahora
                )
            )
        }

        // Cortesía, nunca un requisito: si esto lanza, el candado ya se tomó igual.
        runCatching { reencolar.cancelarTrabajoEncolado(saleId) }

        // Important #1 de la ronda 1 de arreglo: si leerVenta LANZA o devuelve
        // null, el candado ya está puesto — sin este try/finally quedaría
        // huérfano (nadie más lo tiene, pero tampoco nadie lo soltó) hasta que
        // su propio arrendamiento venza, 30 minutos en los que la venta no
        // sube (`claimForUpload`/`getUploadableSales` la excluyen mientras el
        // candado siga vigente). Se recupera solo gracias a la reentrancia de
        // `claimForEdit`, pero eso no es excusa para dejarlo huérfano.
        var venta: VentaLocalParaCorregir? = null
        try {
            venta = port.leerVenta(saleId)
        } finally {
            if (venta == null) {
                runCatching { port.soltar(saleId, claimId) }
            }
        }

        val ventaLeida = venta ?: return ResultadoReclamo.NoExiste

        // `ENVIADO` se lee DESPUÉS de tener el candado, y da igual que sea después: ninguno de
        // los dos guardias de commit lo toca, y el subidor no puede cambiarlo mientras este
        // candado de edición siga puesto. Si la fila desapareciera entre medias —no puede, el
        // candado la sostiene— el `false` es el lado conservador: deja el formulario como el
        // nivel 1, que es lo que había antes de todo esto.
        val yaEnviada = port.leerEstado(saleId)?.enviado ?: false

        return ResultadoReclamo.Reclamada(claimId, ventaLeida, yaEnviada)
    }
}
