package com.example.msp_app.feature.visitas.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.domain.model.VisitaRegistrada
import com.example.msp_app.feature.visitas.domain.port.VisitaImpresaPort
import java.math.BigDecimal
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** `HH:mm` — el mismo formato con el que la Task 19 escribe `CITA_HORA`. */
private val HORA_DE_CITA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Adaptador Room de [VisitaImpresaPort] sobre [VisitDao.findVisitById] — la
 * consulta **anulable** que esta tarea agregó al DAO, porque "esa visita ya no
 * está" es un estado normal del ticket y no una excepción.
 *
 * Cruza los tres campos estructurados de la Task 26 con el MISMO criterio que
 * `RoomVisitasAdapter` de `:feature:pagos`:
 *  - `PROMESA_MONTO_CENTAVOS` es `Long` y se convierte con
 *    `BigDecimal.valueOf(centavos, 2)` — escala exacta, sin pasar jamás por
 *    `Double`.
 *  - `IMPTE_DOCTO_CC_ID` en 0 significa "sin venta ligada" y se traduce a `null`.
 *  - La fecha/hora **no** se reconstruyen desde `NOTA`. Ese parseo es el defecto
 *    que este plan vino a arreglar.
 *
 * Una `FECHA` impresentable devuelve `null` en vez de una visita con fecha
 * inventada: la fecha es lo que decide si el ticket puede imprimirse hoy, y una
 * fecha falsa ahí abriría o cerraría la impresión por accidente. No se calla —
 * emite [VisitasTelemetria.CODE_TICKET_VISITA_SIN_FECHA].
 */
class RoomVisitaImpresaAdapter(
    private val visitDao: VisitDao,
    private val telemetry: Telemetry
) : VisitaImpresaPort {

    override suspend fun visita(visitaId: String): VisitaRegistrada? {
        val fila = visitDao.findVisitById(visitaId) ?: return null
        val registradaEn = AppTime.parseWireFormatOrNull(fila.FECHA)
        if (registradaEn == null) {
            telemetry.error(
                code = VisitasTelemetria.CODE_TICKET_VISITA_SIN_FECHA,
                message = "Visit.FECHA no se pudo leer; sin ella la regla del dia no puede decidir",
                props = emptyMap()
            )
            return null
        }
        return VisitaRegistrada(
            visitaId = fila.ID,
            clienteId = fila.CLIENTE_ID,
            ventaId = fila.IMPTE_DOCTO_CC_ID.takeIf { it != SIN_VENTA },
            registradaEn = registradaEn,
            tipoVisita = fila.TIPO_VISITA,
            cobrador = fila.COBRADOR,
            nota = fila.NOTA?.takeIf { it.isNotBlank() },
            fechaPromesa = AppTime.parseWireDateOrNull(fila.PROMESA_FECHA),
            montoPrometido = fila.PROMESA_MONTO_CENTAVOS?.let {
                Money.of(BigDecimal.valueOf(it, CENTAVOS))
            },
            fechaCita = AppTime.parseWireDateOrNull(fila.CITA_FECHA),
            horaCita = horaDe(fila)
        )
    }

    /**
     * `HH:mm` de la zona de negocio. Una hora impresentable degrada a `null` —la
     * cita conserva su día— pero **no en silencio**: emite
     * [VisitasTelemetria.CODE_TICKET_VISITA_HORA_INVALIDA]. Anti-PII: viaja el
     * nombre de la clase de la excepción, nunca el texto crudo.
     */
    private fun horaDe(fila: VisitEntity): LocalTime? {
        val texto = fila.CITA_HORA?.takeIf { it.isNotBlank() } ?: return null
        return try {
            LocalTime.parse(texto, HORA_DE_CITA)
        } catch (invalida: DateTimeParseException) {
            telemetry.error(
                code = VisitasTelemetria.CODE_TICKET_VISITA_HORA_INVALIDA,
                message = "CITA_HORA no coincide con HH:mm; el ticket se imprime sin hora",
                props = mapOf(VisitasTelemetria.PROP_EXCEPCION to invalida.javaClass.simpleName)
            )
            null
        }
    }

    private companion object {
        /** El centinela que ya usa el schema para "sin venta ligada". */
        const val SIN_VENTA = 0
        const val CENTAVOS = 2
    }
}
