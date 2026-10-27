package com.example.msp_app.core.visitas.agenda

import com.example.msp_app.core.common.visitas.domain.CuentaCompromiso
import com.example.msp_app.core.common.visitas.domain.EstadoCompromiso
import com.example.msp_app.core.common.visitas.domain.TipoCompromiso
import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La agenda local (plan visitas P2, Tarea 17; E-APP-104): unión
 * `Visit ∪ visita_zona` con `Visit` ganando ante el mismo id, y los pagos de
 * `Payment` con la captura local pendiente contando salvo la rechazada.
 */
class AgendaLocalTest {

    private val cdmx: ZoneId = ZoneId.of("America/Mexico_City")
    private val hoy: LocalDate = LocalDate.parse("2026-10-16")
    private val zona = 7

    private class FuenteFalsa(
        val cuentas: List<CuentaCompromiso> = listOf(CuentaCompromiso(100, 10)),
        val propias: List<VisitEntity> = emptyList(),
        val deZona: List<VisitaZonaEntity> = emptyList(),
        val pagos: List<PaymentEntity> = emptyList()
    ) : FuenteDeAgenda {
        var desdePedido: String? = null
        override suspend fun cuentasDeZona(zona: Int) = cuentas
        override suspend fun visitasPropias(desdeAproximado: String): List<VisitEntity> {
            desdePedido = desdeAproximado
            return propias
        }
        override suspend fun visitasDeZona(zona: Int) = deZona
        override suspend fun pagos() = pagos
    }

    private fun propia(
        id: String = ID_1,
        fecha: String = "2026-10-15T17:00:00Z",
        promesaFecha: String? = "2026-10-20",
        guardada: Int = 0,
        venta: Int = 100
    ) = VisitEntity(
        ID = id,
        CLIENTE_ID = 10,
        COBRADOR_ID = 1,
        FECHA = fecha,
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        TIPO_VISITA = "Pidió que regrese otro día",
        ZONA_CLIENTE_ID = zona,
        IMPTE_DOCTO_CC_ID = venta,
        GUARDADO_EN_MICROSIP = guardada,
        PROMESA_VENTA_ID = promesaFecha?.let { venta },
        PROMESA_FECHA = promesaFecha,
        PROMESA_MONTO_CENTAVOS = promesaFecha?.let { 15000L }
    )

    private fun deZona(
        id: String = ID_1,
        fecha: String = "2026-10-15T17:00:00Z",
        tipo: String = "Tiene dinero pero no quiso pagar",
        promesaFecha: String? = null,
        citaFecha: String? = null,
        citaHora: String? = null
    ) = VisitaZonaEntity(
        ID = id,
        CLIENTE_ID = 10,
        COBRADOR_ID = 2,
        COBRADOR = "otro",
        FECHA = fecha,
        TIPO_VISITA = tipo,
        ZONA_CLIENTE_ID = zona,
        IMPTE_DOCTO_CC_ID = 100,
        NOTA = null,
        PROMESA_VENTA_ID = promesaFecha?.let { 100 },
        PROMESA_FECHA = promesaFecha,
        PROMESA_MONTO_CENTAVOS = null,
        CITA_FECHA = citaFecha,
        CITA_HORA = citaHora,
        UPDATED_AT = "2026-10-15T17:00:01Z"
    )

    private fun pago(
        fechaHora: String = "2026-10-16T15:00:00Z",
        guardado: Boolean = false,
        doctoCcId: Int = 0,
        venta: Int = 100
    ) = PaymentEntity(
        ID = "p-$fechaHora-$doctoCcId",
        COBRADOR = "yo",
        DOCTO_CC_ACR_ID = venta,
        DOCTO_CC_ID = doctoCcId,
        FECHA_HORA_PAGO = fechaHora,
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = 150.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 10,
        COBRADOR_ID = 1,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = zona,
        NOMBRE_CLIENTE = "Rosa Hernández"
    )

    private suspend fun derivar(f: FuenteFalsa) = AgendaLocal(f, cdmx).derivar(zona, hoy)

    @Test
    fun unionGanaVisitAnteElMismoId() = runTest {
        // El servidor todavía tiene la versión sin promesa; la local la trae.
        val r = derivar(FuenteFalsa(propias = listOf(propia()), deZona = listOf(deZona())))
        val cv = r.getValue(100)
        assertEquals(ID_1, cv.visitaId)
        assertEquals(TipoCompromiso.PROMESA, cv.tipo)
        assertEquals(EstadoCompromiso.VIGENTE_FUTURA, cv.estado)
        // La unión no duplica el id.
        assertEquals(1, AgendaLocal.unir(listOf(propia()), listOf(deZona())).size)
        // Control positivo: solo con lo bajado no hay compromiso.
        assertTrue(derivar(FuenteFalsa(deZona = listOf(deZona()))).isEmpty())
    }

    @Test
    fun visitaDeZonaAjenaEntraALaUnion() = runTest {
        val otra = deZona(id = ID_2, fecha = "2026-10-15T18:00:00Z", promesaFecha = "2026-10-22")
        val r = derivar(FuenteFalsa(propias = listOf(propia()), deZona = listOf(otra)))
        assertEquals(ID_2, r.getValue(100).visitaId)
        assertEquals(LocalDate.parse("2026-10-22"), r.getValue(100).dia)
    }

    @Test
    fun capturaLocalPendienteCierraLaPromesa() = runTest {
        val pendiente = pago(guardado = false, doctoCcId = 0)
        val r = derivar(FuenteFalsa(propias = listOf(propia()), pagos = listOf(pendiente)))
        assertEquals(EstadoCompromiso.CUMPLIDA, r.getValue(100).estado)
    }

    @Test
    fun capturaRechazadaNoCierra() = runTest {
        val rechazada = pago(guardado = false, doctoCcId = DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR)
        val r = derivar(FuenteFalsa(propias = listOf(propia()), pagos = listOf(rechazada)))
        assertEquals(EstadoCompromiso.VIGENTE_FUTURA, r.getValue(100).estado)
        // Control positivo: el mismo pago sin la marca -1 sí cierra.
        val aceptada = rechazada.copy(DOCTO_CC_ID = 0)
        assertEquals(
            EstadoCompromiso.CUMPLIDA,
            derivar(
                FuenteFalsa(propias = listOf(propia()), pagos = listOf(aceptada))
            ).getValue(100).estado
        )
    }

    @Test
    fun pagoDeOtraVentaNoCierra() = runTest {
        val r = derivar(FuenteFalsa(propias = listOf(propia()), pagos = listOf(pago(venta = 999))))
        assertEquals(EstadoCompromiso.VIGENTE_FUTURA, r.getValue(100).estado)
    }

    /**
     * C1 de la revisión de T17 (E-APP-112): el sync manda el pago de oficina
     * como el instante de su medianoche local (`2026-08-24T06:00:00Z`, medido en
     * dev). El servidor lo trata como pago sin hora del día y cierra la promesa
     * de una visita del mismo día (DA-7, caso 023); el teléfono también.
     */
    @Test
    fun `pago de oficina del sync cierra la promesa del mismo dia - E-APP-112`() = runTest {
        // Visita con promesa el 16 a las 11:00 locales.
        val visita = propia(fecha = "2026-10-16T17:00:00Z")
        val oficina = pago(
            fechaHora = "2026-10-16T06:00:00Z",
            guardado = true,
            doctoCcId = 14998987
        ).copy(ID = "14998988")
        assertEquals(EstadoCompromiso.CUMPLIDA, estadoCon(visita, oficina))
        // Control positivo: con pago_recibido_id es un pago con hora, anterior a la visita: no cierra.
        val conHora = oficina.copy(PAGO_RECIBIDO_ID = "6b0c1f2e-3d4a-4b5c-8d6e-7f8091a2b3c4")
        assertEquals(EstadoCompromiso.VIGENTE_FUTURA, estadoCon(visita, conHora))
        // El pago de oficina del día anterior no cierra (caso 024).
        val ayer = oficina.copy(FECHA_HORA_PAGO = "2026-10-15T06:00:00Z")
        assertEquals(EstadoCompromiso.VIGENTE_FUTURA, estadoCon(visita, ayer))
    }

    private suspend fun estadoCon(visita: VisitEntity, pago: PaymentEntity) =
        derivar(FuenteFalsa(propias = listOf(visita), pagos = listOf(pago))).getValue(100).estado

    @Test
    fun pagoConSoloElDiaEsDeOficinaSinHora() {
        val p = requireNotNull(AgendaLocal.pagoDe(pago(fechaHora = "2026-10-16"), cdmx))
        assertNull(p.instante)
        assertEquals(LocalDate.parse("2026-10-16"), p.fecha)
        val conHora =
            requireNotNull(AgendaLocal.pagoDe(pago(fechaHora = "2026-10-17T05:30:00Z"), cdmx))
        assertEquals(Instant.parse("2026-10-17T05:30:00Z"), conHora.instante)
        // 23:30 del 16 en CDMX: su día civil es el 16, no el 17 de UTC.
        assertEquals(LocalDate.parse("2026-10-16"), conHora.fecha)
        assertNull(AgendaLocal.pagoDe(pago(fechaHora = "basura"), cdmx))
    }

    @Test
    fun hoyALas2330LocalEsElDiaDelNegocio() = runTest {
        // 23:45 del 16 en CDMX = 05:45Z del 17 (caso compartido 022).
        val ahora = Instant.parse("2026-10-17T05:45:00Z")
        assertEquals(LocalDate.parse("2026-10-16"), AgendaLocal.hoyDe(ahora, cdmx))
        val f =
            FuenteFalsa(
                propias = listOf(
                    propia(fecha = "2026-10-17T05:30:00Z", promesaFecha = "2026-10-16")
                )
            )
        val cv = AgendaLocal(f, cdmx).derivarAhora(zona, ahora).getValue(100)
        assertEquals(EstadoCompromiso.VIGENTE_HOY, cv.estado)
    }

    @Test
    fun ventanaEsLaDeLaBajadaEnLaZonaDelNegocio() = runTest {
        val desde = AgendaLocal.desdeDe(hoy, cdmx)
        // 2026-07-16 00:00 CDMX = 06:00Z.
        assertEquals(Instant.parse("2026-07-16T06:00:00Z"), desde)
        // Un segundo antes del corte no existe para la agenda; en el corte, sí.
        val vieja = FuenteFalsa(propias = listOf(propia(fecha = "2026-07-16T05:59:59Z")))
        assertTrue(derivar(vieja).isEmpty())
        assertEquals("2026-07-15", vieja.desdePedido)
        val justa = FuenteFalsa(propias = listOf(propia(fecha = "2026-07-16T06:00:00Z")))
        assertEquals(ID_1, derivar(justa).getValue(100).visitaId)
    }

    @Test
    fun citaDeZonaConHoraYPromesaSinFechaNoEsPromesa() {
        val v = AgendaLocal.unir(
            emptyList(),
            listOf(
                deZona(citaFecha = "2026-10-18", citaHora = "16:00"),
                deZona(id = ID_2, citaHora = "16:00")
            )
        )
        val conCita = v.first { it.id == ID_1 }
        assertEquals(LocalDate.parse("2026-10-18"), conCita.cita?.fecha)
        assertEquals("16:00", conCita.cita?.hora.toString())
        // Hora sin día no es cita (Task 26 / TipoVisitaCatalogo).
        assertNull(v.first { it.id == ID_2 }.cita)
        // Monto sin fecha no es promesa (DA-16).
        val soloMonto = propia(promesaFecha = null).copy(PROMESA_MONTO_CENTAVOS = 15000L)
        assertNull(AgendaLocal.unir(listOf(soloMonto), emptyList()).single().promesa)
    }

    private companion object {
        const val ID_1 = "00000000-0000-3000-8000-000000000001"
        const val ID_2 = "00000000-0000-3000-8000-000000000002"
    }
}
