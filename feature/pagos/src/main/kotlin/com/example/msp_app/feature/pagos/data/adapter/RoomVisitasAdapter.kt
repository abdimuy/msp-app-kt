package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import com.example.msp_app.feature.pagos.domain.port.VisitasPort
import java.math.BigDecimal
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Adaptador Room de [VisitasPort] sobre [VisitDao.getVisitsByClienteId].
 *
 * Cruza los tres campos estructurados que agregó la migración aditiva de la
 * Task 26 (`PROMESA_FECHA`, `PROMESA_MONTO_CENTAVOS`, `CITA_HORA`). El monto
 * viaja en **centavos enteros** (`Long`) y se convierte con
 * `BigDecimal.valueOf(centavos, 2)` — escala exacta, sin pasar jamás por
 * `Double`.
 *
 * `IMPTE_DOCTO_CC_ID` en 0 significa "sin venta ligada" y se traduce a `null`,
 * que es lo que el dominio de cobranza espera (KDoc de `VisitaEnVentana`): una
 * visita de alcance VENTA sin venta no se colapsa a una venta arbitraria del
 * cliente —ese fue el bug que arregló la Task 13— sino que el deriver la
 * reporta como incidencia.
 *
 * La fecha/hora libres NO se reconstruyen desde `NOTA`. Ese parseo es
 * exactamente el defecto que este plan vino a arreglar.
 */
class RoomVisitasAdapter(
    private val visitDao: VisitDao,
    private val telemetry: Telemetry
) : VisitasPort {

    override suspend fun visitasDelCliente(clienteId: Int): List<VisitaDelCliente> =
        visitDao.getVisitsByClienteId(clienteId)
            .mapNotNull { it.aVisitaDelCliente(::horaDe) }
            .sortedByDescending { it.fecha }

    /**
     * `HH:mm` de la zona de negocio. Una hora impresentable se degrada a `null`
     * —la visita se conserva, solo pierde la hora de la cita— pero **no en
     * silencio**: se emite [PagosTelemetria.CODE_CITA_HORA_INVALIDA]. "Es
     * esperado" no autoriza el silencio, autoriza un código de error propio
     * (NORMA DE ERRORES). Anti-PII: se emite el nombre de la clase de la
     * excepción, nunca el texto crudo — una hora tecleada por el cobrador es
     * entrada de usuario.
     */
    private fun horaDe(crudo: String?): LocalTime? {
        val texto = crudo?.takeIf { it.isNotBlank() } ?: return null
        return try {
            LocalTime.parse(texto, HORA_DE_CITA)
        } catch (invalida: DateTimeParseException) {
            telemetry.error(
                code = PagosTelemetria.CODE_CITA_HORA_INVALIDA,
                message = "CITA_HORA no coincide con HH:mm; la visita se conserva sin hora",
                props = mapOf(PagosTelemetria.PROP_EXCEPCION to invalida.javaClass.simpleName)
            )
            null
        }
    }
}

private val HORA_DE_CITA: DateTimeFormatter = DateTimeFormatter.ofPattern(AppTime.Formats.TIME_24H)

private fun VisitEntity.aVisitaDelCliente(horaDe: (String?) -> LocalTime?): VisitaDelCliente? {
    val fecha = AppTime.parseWireFormatOrNull(FECHA) ?: return null
    return VisitaDelCliente(
        visitaId = ID,
        clienteId = CLIENTE_ID,
        ventaId = IMPTE_DOCTO_CC_ID.takeIf { it != 0 },
        fecha = fecha,
        tipoVisita = TIPO_VISITA,
        nota = NOTA?.takeIf { it.isNotBlank() },
        fechaPromesa = AppTime.parseWireDateOrNull(PROMESA_FECHA),
        montoPrometido = PROMESA_MONTO_CENTAVOS?.let { Money.of(BigDecimal.valueOf(it, CENTAVOS)) },
        horaCita = horaDe(CITA_HORA)
    )
}

private const val CENTAVOS = 2
