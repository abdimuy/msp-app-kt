package com.example.msp_app.core.visitas.agenda

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.common.visitas.domain.CitaCompromiso
import com.example.msp_app.core.common.visitas.domain.CompromisoVigente
import com.example.msp_app.core.common.visitas.domain.CuentaCompromiso
import com.example.msp_app.core.common.visitas.domain.EstadoTarea
import com.example.msp_app.core.common.visitas.domain.PagoCompromiso
import com.example.msp_app.core.common.visitas.domain.PromesaCompromiso
import com.example.msp_app.core.common.visitas.domain.TareaCompromiso
import com.example.msp_app.core.common.visitas.domain.VisitaCompromiso
import com.example.msp_app.core.common.visitas.domain.derivarCompromisos
import com.example.msp_app.core.common.visitas.domain.derivarEstadoTarea
import com.example.msp_app.core.common.visitas.domain.momentoDePago
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.dao.visit.VisitaZonaDao
import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.TareaZonaEntity
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
 * (`Payment`, `sales`) solo se leen con `VisitDao.pagosParaLaAgenda` / `getAll`.
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
    private val visitaZonaDao: VisitaZonaDao
) : FuenteDeAgenda {

    override suspend fun cuentasDeZona(zona: Int): List<CuentaCompromiso> = saleDao.getAll()
        .filter { it.ZONA_CLIENTE_ID == zona }
        .map { CuentaCompromiso(ventaId = SaleIdSpaces.forSaleRow(it), clienteId = it.CLIENTE_ID) }

    override suspend fun visitasPropias(desdeAproximado: String): List<VisitEntity> =
        visitDao.getVisitsByDate(desdeAproximado, HASTA_SIEMPRE)

    override suspend fun visitasDeZona(zona: Int): List<VisitaZonaEntity> = visitaZonaDao.deZona(
        zona
    )

    override suspend fun pagos(): List<PaymentEntity> = visitDao.pagosParaLaAgenda()

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

    /**
     * El estado de cada tarea de [tareas] (plan visitas P3, Tarea 12; E-APP-123),
     * con la MISMA derivación que el servidor ([derivarEstadoTarea]) sobre las
     * visitas (`Visit` ∪ `visita_zona`) y los pagos locales, la captura pendiente
     * incluida. Solo lectura; el "hoy" es el del negocio. Una tarea que ya
     * atendió el cobrador sale `ATENDIDA` y no se avisa.
     */
    suspend fun estadosDeTareas(
        tareas: List<TareaZonaEntity>,
        hoy: LocalDate
    ): Map<String, EstadoTarea> {
        if (tareas.isEmpty()) return emptyMap()
        val creadas = tareas.mapNotNull { AppTime.parseWireFormatOrNull(it.CREATED_AT) }
        val desde = (creadas.minOrNull() ?: desdeDe(hoy, zonaNegocio))
            .atZone(zonaNegocio).toLocalDate().minusDays(1)
        val zonas = tareas.map { it.ZONA_CLIENTE_ID }.toSet()
        val propias = fuente.visitasPropias(desde.toString())
        val deZona = zonas.flatMap { fuente.visitasDeZona(it) }
        val visitas = unir(propias, deZona)
        val ventas = tareas.map { it.VENTA_ID }.toSet()
        val pagos = fuente.pagos().filter { SaleIdSpaces.forSalePayments(it) in ventas }
            .mapNotNull { pagoDe(it, zonaNegocio) }
        return tareas.associate { t ->
            val creada = AppTime.parseWireFormatOrNull(t.CREATED_AT)
            val fecha = AppTime.parseWireDateOrNull(t.FECHA)
            if (creada == null || fecha == null) {
                t.ID to EstadoTarea.NO_ATENDIDA
            } else {
                val compromiso = TareaCompromiso(
                    id = t.ID,
                    ventaId = t.VENTA_ID,
                    fecha = fecha,
                    hora = horaCivil(t.HORA),
                    createdAt = creada,
                    canceladaAt = t.CANCELADA_AT?.let { AppTime.parseWireFormatOrNull(it) }
                )
                t.ID to derivarEstadoTarea(
                    compromiso,
                    visitas,
                    pagos,
                    CuentaCompromiso(t.VENTA_ID, t.CLIENTE_ID),
                    hoy,
                    zonaNegocio
                ).estado
            }
        }
    }

    companion object {
        /** V de la bajada (DA-3): la misma constante que el servidor; si cambia allá, sube el epoch. */
        const val VENTANA_DIAS: Long = 92

        private val HORA_CIVIL: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
         * la captura local pendiente cuenta como cualquier otro, y el abono no
         * aplicado (`-2`) también: cumple la promesa en el teléfono aunque el servidor
         * no lo vea (límite conocido E-APP-125). La hora se lee con
         * [momentoDePago], que interpreta el formato real del sync: el pago de
         * oficina llega como el instante de su medianoche local y es pago sin hora
         * del día (DA-7, E-APP-112), como en el servidor. Ilegible → no entra.
         */
        fun pagoDe(p: PaymentEntity, zonaNegocio: ZoneId = BUSINESS_ZONE): PagoCompromiso? {
            val momento =
                momentoDePago(p.ID, p.FECHA_HORA_PAGO, p.PAGO_RECIBIDO_ID, zonaNegocio)
                    ?: return null
            return PagoCompromiso(
                SaleIdSpaces.forSalePayments(p),
                momento.instante,
                momento.fecha,
                p.DOCTO_CC_ID == DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
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
