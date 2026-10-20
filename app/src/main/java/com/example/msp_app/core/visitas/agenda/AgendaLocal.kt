package com.example.msp_app.core.visitas.agenda

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.common.visitas.domain.CitaCompromiso
import com.example.msp_app.core.common.visitas.domain.CompromisoVigente
import com.example.msp_app.core.common.visitas.domain.CuentaCompromiso
import com.example.msp_app.core.common.visitas.domain.PagoCompromiso
import com.example.msp_app.core.common.visitas.domain.PromesaCompromiso
import com.example.msp_app.core.common.visitas.domain.VisitaCompromiso
import com.example.msp_app.core.common.visitas.domain.derivarCompromisos
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.dao.visit.VisitaZonaDao
import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import com.example.msp_app.features.sales.SaleIdSpaces
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lo que la agenda local lee. Todo es LECTURA y con métodos que ya existían
 * (plan visitas P2, Tarea 17): aquí no se escribe ninguna tabla, y las de dinero
 * (`Payment`, `sales`) solo se leen con `getAllPayments` / `getAll`.
 */
interface FuenteDeAgenda {
    suspend fun cuentasDeZona(zona: Int): List<CuentaCompromiso>

    /** Las visitas propias (pendientes o no) con `FECHA` desde aproximadamente [desdeAproximado]. */
    suspend fun visitasPropias(desdeAproximado: String): List<VisitEntity>

    suspend fun visitasDeZona(zona: Int): List<VisitaZonaEntity>

    suspend fun pagos(): List<PaymentEntity>
}

/** [FuenteDeAgenda] sobre Room, solo lectura. */
class RoomFuenteDeAgenda(
    private val saleDao: SaleDao,
    private val visitDao: VisitDao,
    private val visitaZonaDao: VisitaZonaDao,
    private val paymentDao: PaymentDao
) : FuenteDeAgenda {

    override suspend fun cuentasDeZona(zona: Int): List<CuentaCompromiso> = saleDao.getAll()
        .filter { it.ZONA_CLIENTE_ID == zona }
        .map { CuentaCompromiso(ventaId = SaleIdSpaces.forSaleRow(it), clienteId = it.CLIENTE_ID) }

    override suspend fun visitasPropias(desdeAproximado: String): List<VisitEntity> =
        visitDao.getVisitsByDate(desdeAproximado, HASTA_SIEMPRE)

    override suspend fun visitasDeZona(zona: Int): List<VisitaZonaEntity> = visitaZonaDao.deZona(
        zona
    )

    override suspend fun pagos(): List<PaymentEntity> = paymentDao.getAllPayments()

    private companion object {
        /** Mayor que cualquier `FECHA` (`2026-…`): la consulta existente pide un tope. */
        const val HASTA_SIEMPRE = "9999-12-31T23:59:59Z"
    }
}

/**
 * La agenda del teléfono (plan visitas P2, Tarea 17; spec §5.3, §5.4.8):
 * deriva el compromiso de cada venta de la zona con la MISMA regla que el
 * servidor ([derivarCompromisos], amarrada por los casos compartidos, I5) sobre
 *
 *  - las visitas `Visit` (las propias, pendientes o no) ∪ `visita_zona` (lo
 *    bajado). **Ante el mismo id gana `Visit`**: es la versión local, que puede
 *    traer un compromiso que el servidor todavía no tiene (I8);
 *  - los pagos `Payment` de esas ventas, contando la captura local pendiente
 *    (`GUARDADO_EN_MICROSIP = false`) salvo la rechazada por el servidor
 *    (`DOCTO_CC_ID = -1`, DA-8 / E-COB-031).
 *
 * El "hoy" y la ventana se calculan en la zona del negocio, nunca en UTC ni en
 * la del aparato (Review Focus 2, E-APP-106). La ventana es la de la bajada y
 * la agenda del servidor: desde = hoy − [VENTANA_DIAS] a las 00:00 del negocio
 * (`DesdeBajadaPorOmision`); una visita más vieja no existe para la agenda en
 * ningún lado.
 *
 * Nada se guarda (I9) y nada se escribe.
 */
class AgendaLocal(
    private val fuente: FuenteDeAgenda,
    private val zonaNegocio: ZoneId = BUSINESS_ZONE
) {

    suspend fun derivar(zona: Int, hoy: LocalDate): Map<Int, CompromisoVigente> {
        val desde = desdeDe(hoy, zonaNegocio)
        val cuentas = fuente.cuentasDeZona(zona)
        val ventas = cuentas.map { it.ventaId }.toSet()
        // Un día antes como cota de texto: la comparación de `FECHA` en SQL es
        // de cadenas; el corte exacto es por instante, abajo.
        val propias = fuente.visitasPropias(
            desde.atZone(zonaNegocio).toLocalDate().minusDays(1).toString()
        )
        val visitas = unir(
            propias,
            fuente.visitasDeZona(zona)
        ).filter { !it.instante.isBefore(desde) }
        val pagos = fuente.pagos().filter {
            SaleIdSpaces.forSalePayments(it) in ventas
        }.mapNotNull { pagoDe(it, zonaNegocio) }
        return derivarCompromisos(cuentas, visitas, pagos, hoy, zonaNegocio)
    }

    /** [derivar] con el "hoy" del negocio para [ahora]. */
    suspend fun derivarAhora(zona: Int, ahora: Instant): Map<Int, CompromisoVigente> =
        derivar(zona, hoyDe(ahora, zonaNegocio))

    companion object {
        /** V de la bajada (DA-3): la misma constante que el servidor; si cambia allá, sube el epoch. */
        const val VENTANA_DIAS: Long = 92

        private val HORA_CIVIL: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val FECHA_SIN_HORA = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")

        /** El día civil de [ahora] en la zona del negocio (E-APP-106). */
        fun hoyDe(ahora: Instant, zonaNegocio: ZoneId = BUSINESS_ZONE): LocalDate =
            ahora.atZone(zonaNegocio).toLocalDate()

        /** hoy − V a las 00:00 del negocio, como `DesdeBajadaPorOmision`. */
        fun desdeDe(hoy: LocalDate, zonaNegocio: ZoneId = BUSINESS_ZONE): Instant =
            hoy.minusDays(VENTANA_DIAS).atStartOfDay(zonaNegocio).toInstant()

        /**
         * `Visit` ∪ `visita_zona`; ante el mismo id gana `Visit` (E-APP-104).
         * Una fila con `FECHA` ilegible no entra (no se le inventa instante).
         */
        fun unir(
            propias: List<VisitEntity>,
            deZona: List<VisitaZonaEntity>
        ): List<VisitaCompromiso> {
            val porId = LinkedHashMap<String, VisitaCompromiso>()
            deZona.forEach { v -> v.aCompromiso()?.let { porId[it.id] = it } }
            propias.forEach { v -> v.aCompromiso()?.let { porId[it.id] = it } }
            return porId.values.toList()
        }

        /**
         * Un `Payment` como pago de la derivación. Rechazado = `DOCTO_CC_ID = -1`;
         * la captura local pendiente cuenta como cualquier otro. `FECHA_HORA_PAGO`
         * con solo el día es un pago de oficina sin hora (DA-7). Ilegible → no entra.
         */
        fun pagoDe(p: PaymentEntity, zonaNegocio: ZoneId = BUSINESS_ZONE): PagoCompromiso? {
            val rechazado = p.DOCTO_CC_ID == DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
            if (FECHA_SIN_HORA.matches(p.FECHA_HORA_PAGO)) {
                val dia = AppTime.parseWireDateOrNull(p.FECHA_HORA_PAGO) ?: return null
                return PagoCompromiso(SaleIdSpaces.forSalePayments(p), null, dia, rechazado)
            }
            val instante = AppTime.parseWireFormatOrNull(p.FECHA_HORA_PAGO) ?: return null
            return PagoCompromiso(
                SaleIdSpaces.forSalePayments(p),
                instante,
                instante.atZone(zonaNegocio).toLocalDate(),
                rechazado
            )
        }

        private fun VisitEntity.aCompromiso(): VisitaCompromiso? = compromisoDeFila(
            id = ID,
            clienteId = CLIENTE_ID,
            ventaId = IMPTE_DOCTO_CC_ID,
            tipo = TIPO_VISITA,
            fecha = FECHA,
            promesa = Triple(PROMESA_VENTA_ID, PROMESA_FECHA, PROMESA_MONTO_CENTAVOS),
            cita = CITA_FECHA to CITA_HORA
        )

        private fun VisitaZonaEntity.aCompromiso(): VisitaCompromiso? = compromisoDeFila(
            id = ID,
            clienteId = CLIENTE_ID,
            ventaId = IMPTE_DOCTO_CC_ID,
            tipo = TIPO_VISITA,
            fecha = FECHA,
            promesa = Triple(PROMESA_VENTA_ID, PROMESA_FECHA, PROMESA_MONTO_CENTAVOS),
            cita = CITA_FECHA to CITA_HORA
        )

        @Suppress("LongParameterList")
        private fun compromisoDeFila(
            id: String,
            clienteId: Int,
            ventaId: Int?,
            tipo: String,
            fecha: String,
            promesa: Triple<Int?, String?, Long?>,
            cita: Pair<String?, String?>
        ): VisitaCompromiso? {
            val instante = AppTime.parseWireFormatOrNull(fecha) ?: return null
            // DA-16: una promesa sin fecha no es compromiso.
            val promesaFecha = fechaCivil(promesa.second)
            val citaFecha = fechaCivil(cita.first)
            return VisitaCompromiso(
                id = id,
                clienteId = clienteId,
                ventaId = ventaId?.takeIf { it != 0 },
                tipoVisita = tipo,
                instante = instante,
                promesa = promesaFecha?.let { PromesaCompromiso(promesa.first, it, promesa.third) },
                cita = citaFecha?.let { CitaCompromiso(it, horaCivil(cita.second)) }
            )
        }

        private fun fechaCivil(s: String?): LocalDate? =
            AppTime.parseWireDateOrNull(s)?.takeIf { it.toString() == s }

        private fun horaCivil(s: String?): LocalTime? {
            if (s.isNullOrBlank()) return null
            return runCatching {
                LocalTime.parse(s, HORA_CIVIL)
            }.getOrNull()?.takeIf { it.format(HORA_CIVIL) == s }
        }
    }
}
