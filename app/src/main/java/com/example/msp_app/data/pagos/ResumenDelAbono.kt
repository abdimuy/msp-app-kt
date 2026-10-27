package com.example.msp_app.data.pagos

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.captura.CapturaEnCursoDao
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.google.gson.Gson
import java.math.BigDecimal
import java.time.Instant

/**
 * El `RESUMEN` del marcador de un abono (E-APP-084/085/086): lo justo para
 * guardarlo tarde con el MISMO UUID, la MISMA hora de captura y los MISMOS
 * comprobantes. Gson ignora los defaults de Kotlin, así que todo campo se lee
 * como anulable y se valida al decodificar.
 */
internal data class ResumenDelAbono(
    val ventaId: Int?,
    val importe: String?,
    val metodo: String?,
    val capturadoEn: String?,
    val comprobantes: List<ComprobanteResumido>?
) {
    internal data class ComprobanteResumido(
        val id: String?,
        val archivo: String?,
        val mime: String?
    )

    fun aAbono(abonoId: String): AbonoARegistrar? {
        val venta = ventaId ?: return null
        val pesos = importe?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: return null
        val forma = metodo?.let { m -> MetodoDeCobro.entries.firstOrNull { it.name == m } } ?: return null
        val hora = AppTime.parseWireFormatOrNull(capturadoEn) ?: return null
        return AbonoARegistrar(
            abonoId = abonoId,
            ventaId = venta,
            importe = Money.of(pesos),
            metodo = forma,
            comprobantes = comprobantesDominio(),
            capturadoEn = hora
        )
    }

    fun comprobantesDominio(): List<ComprobanteDelAbono> = comprobantes.orEmpty().mapNotNull { c ->
        val id = c.id ?: return@mapNotNull null
        val archivo = c.archivo ?: return@mapNotNull null
        ComprobanteDelAbono(id = id, archivo = archivo, mime = c.mime ?: "image/jpeg")
    }

    fun json(): String = GSON.toJson(this)

    companion object {
        private val GSON = Gson()

        fun de(abono: AbonoARegistrar, capturadoEn: Instant) = ResumenDelAbono(
            ventaId = abono.ventaId,
            importe = abono.importe.amount.toPlainString(),
            metodo = abono.metodo.name,
            capturadoEn = AppTime.toWireFormat(capturadoEn),
            comprobantes = abono.comprobantes.map {
                ComprobanteResumido(
                    it.id,
                    it.archivo,
                    it.mime
                )
            }
        )

        fun leer(json: String): ResumenDelAbono? =
            runCatching { GSON.fromJson(json, ResumenDelAbono::class.java) }.getOrNull()
    }
}

/**
 * Los archivos de comprobante que cita algún marcador ABONO abierto
 * (E-APP-086): el barrido de huérfanos no debe tocarlos.
 */
suspend fun archivosCitadosPorMarcadores(capturas: CapturaEnCursoDao): Set<String> =
    capturas.todasLasAbiertas()
        .filter { it.TIPO == CapturaEnCursoEntity.TIPO_ABONO }
        .flatMap { ResumenDelAbono.leer(it.RESUMEN)?.comprobantesDominio().orEmpty() }
        .mapTo(mutableSetOf()) { it.archivo }
