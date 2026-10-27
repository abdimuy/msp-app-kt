package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.port.CitaEstructurada
import com.example.msp_app.feature.visitas.domain.port.PromesaEstructurada
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
import com.google.gson.Gson
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime

/**
 * El `RESUMEN` del marcador de una visita (E-APP-119, D15): las N cuentas de la
 * captura tal cual, con sus ids deterministas y la hora de captura, para que una
 * visita CAPTURADA con 0 escrituras se pueda **reanudar** al volver a la app
 * con la MISMA semilla y los MISMOS ids (el reintento es idempotente por el
 * marcador, E-APP-087).
 *
 * Gson ignora los defaults de Kotlin, así que todo campo se lee como anulable y
 * se valida al decodificar: un resumen incompleto no se reanuda (queda visible
 * en el aviso, nunca se inventa una visita).
 */
internal data class ResumenDeLaVisita(
    val capturadaEn: String?,
    val visitas: List<VisitaResumida>?
) {
    internal data class VisitaResumida(
        val visitaId: String?,
        val clienteId: Int?,
        val ventaId: Int?,
        val tipoVisita: String?,
        val nota: String?,
        val promesaVentaId: Int?,
        val promesaFecha: String?,
        val promesaMonto: String?,
        val citaFecha: String?,
        val citaHora: String?,
        val lat: Double?,
        val lng: Double?,
        val recomendacionId: String?,
        val comprobantes: List<ComprobanteResumido>?
    )

    internal data class ComprobanteResumido(
        val id: String?,
        val archivo: String?,
        val mime: String?
    )

    /** La hora de captura; `null` si el resumen no la trae. */
    fun hora(): Instant? = AppTime.parseWireFormatOrNull(capturadaEn)

    /** Las N cuentas, o `null` si falta cualquier dato obligatorio. */
    fun aLote(): List<VisitaARegistrar>? {
        val lista = visitas?.takeIf { it.isNotEmpty() } ?: return null
        val lote = lista.map { it.aVisita() ?: return null }
        return lote
    }

    fun json(): String = GSON.toJson(this)

    companion object {
        private val GSON = Gson()

        fun de(lote: List<VisitaARegistrar>, capturadaEn: Instant) = ResumenDeLaVisita(
            capturadaEn = AppTime.toWireFormat(capturadaEn),
            visitas = lote.map { it.resumida() }
        )

        fun leer(json: String): ResumenDeLaVisita? =
            runCatching { GSON.fromJson(json, ResumenDeLaVisita::class.java) }.getOrNull()
    }
}

private fun VisitaARegistrar.resumida() = ResumenDeLaVisita.VisitaResumida(
    visitaId = visitaId,
    clienteId = clienteId,
    ventaId = ventaId,
    tipoVisita = tipoVisita,
    nota = nota,
    promesaVentaId = promesa?.ventaId,
    promesaFecha = promesa?.fecha?.let(AppTime::toWireDate),
    promesaMonto = promesa?.monto?.amount?.toPlainString(),
    citaFecha = cita?.fecha?.let(AppTime::toWireDate),
    citaHora = cita?.hora?.toString(),
    lat = ubicacion?.lat,
    lng = ubicacion?.lng,
    recomendacionId = recomendacionId,
    comprobantes = comprobantes.map {
        ResumenDeLaVisita.ComprobanteResumido(it.id, it.archivo, it.mime)
    }
)

@Suppress("ReturnCount") // un dato obligatorio ausente corta: no se inventa la visita.
private fun ResumenDeLaVisita.VisitaResumida.aVisita(): VisitaARegistrar? {
    val id = visitaId ?: return null
    val cliente = clienteId ?: return null
    val tipo = tipoVisita ?: return null
    val promesa = promesaFecha?.let { texto ->
        val fecha = AppTime.parseWireDateOrNull(texto) ?: return null
        val monto = promesaMonto?.let { m ->
            Money.of(runCatching { BigDecimal(m) }.getOrNull() ?: return null)
        }
        PromesaEstructurada(ventaId = promesaVentaId, fecha = fecha, monto = monto)
    }
    val cita = citaFecha?.let { texto ->
        val fecha = AppTime.parseWireDateOrNull(texto) ?: return null
        val hora = citaHora?.let { h -> runCatching { LocalTime.parse(h) }.getOrNull() ?: return null }
        CitaEstructurada(fecha = fecha, hora = hora)
    }
    val donde = if (lat != null && lng != null) UbicacionDeLaVisita(lat, lng) else null
    return VisitaARegistrar(
        visitaId = id,
        clienteId = cliente,
        ventaId = ventaId,
        tipoVisita = tipo,
        nota = nota,
        promesa = promesa,
        cita = cita,
        ubicacion = donde,
        recomendacionId = recomendacionId,
        comprobantes = comprobantes.orEmpty().mapNotNull { c ->
            val cid = c.id ?: return@mapNotNull null
            val archivo = c.archivo ?: return@mapNotNull null
            ComprobanteDeVisita(id = cid, archivo = archivo, mime = c.mime ?: "image/jpeg")
        }
    )
}
