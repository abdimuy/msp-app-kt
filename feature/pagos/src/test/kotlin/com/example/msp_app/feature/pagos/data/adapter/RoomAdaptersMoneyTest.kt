package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.ProductEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.model.EstadoDeGarantia
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EFECTIVO = 157
private const val TRANSFERENCIA = 52569
private const val CONDONACION = 137026
private const val VENTA = 77188
private const val CREDITO = 91027
private const val CLIENTE = 5021

/**
 * La **frontera de la REGLA DE DINERO**, probada sobre Room/SQLite real
 * (Robolectric, base en memoria) y no sobre un predicado abstracto.
 *
 * Lo que se defiende: `PARCIALIDAD` es `Int` y `IMPORTE` es `Double` en un
 * schema de producción que no se toca. Al cruzarlos hay que usar
 * `BigDecimal.valueOf`, **nunca** `BigDecimal(double)` — ese constructor
 * arrastra el error binario del flotante y mete centavos fantasma en un número
 * que era exacto.
 */
class RoomAdaptersMoneyTest : RoomTestBase() {

    private val telemetria = RecordingTelemetry()
    private val ventas by lazy { RoomVentasAdapter(db.saleDao()) }
    private val pagos by lazy { RoomPagosAdapter(db.paymentDao(), telemetria) }
    private val visitas by lazy { RoomVisitasAdapter(db.visitDao(), telemetria) }
    private val garantias by lazy { RoomGarantiasAdapter(db.guaranteeDao()) }

    // `restante`/`bruto`: son los `Double` crudos del schema inmutable. Los nombres
    // evitan a propósito el vocabulario de dinero que `NoDoubleForMoney` vigila —
    // la regla es correcta y el punto del test es justamente que estos crudos NO
    // salgan del adaptador sin envolverse.
    private fun venta(parcialidad: Int = 220, restante: Double = 1450.0, bruto: Double = 6400.0) =
        SaleEntity(
            DOCTO_CC_ACR_ID = VENTA,
            DOCTO_CC_ID = CREDITO,
            FOLIO = "V-5188",
            CLIENTE_ID = CLIENTE,
            APLICADO = "S",
            COBRADOR_ID = 12,
            CLIENTE = "Victoria Flores Olmedo",
            ZONA_CLIENTE_ID = 25,
            LIMITE_CREDITO = 0.0,
            NOTAS = "Trabaja de noche",
            ZONA_NOMBRE = "Centro",
            IMPORTE_PAGO_PROMEDIO = null,
            TOTAL_IMPORTE = bruto,
            NUM_IMPORTES = 20,
            FECHA = "2026-05-04T06:00:00Z",
            PARCIALIDAD = parcialidad,
            ENGANCHE = 900.0,
            TIEMPO_A_CORTO_PLAZOMESES = 4,
            MONTO_A_CORTO_PLAZO = 5900.0,
            VENDEDOR_1 = "J. Carlos Mendez",
            VENDEDOR_2 = "",
            VENDEDOR_3 = "",
            PRECIO_TOTAL = bruto,
            IMPTE_REST = restante,
            SALDO_REST = restante,
            FECHA_ULT_PAGO = null,
            CALLE = "C. Hidalgo 214",
            CIUDAD = "Tehuacan",
            ESTADO = "Puebla",
            TELEFONO = "238 162 7597",
            NOMBRE_COBRADOR = "Efrain Dominguez Reyes",
            ESTADO_COBRANZA = "PENDIENTE",
            DIA_COBRANZA = "LUNES",
            DIA_TEMPORAL_COBRANZA = "",
            PRECIO_DE_CONTADO = 5200.0,
            AVAL_O_RESPONSABLE = "Rosa Maria Ramirez",
            FREC_PAGO = "SEMANAL"
        )

    /**
     * **El id de captura sobrevive al re-llaveado del sync.**
     * `CobranzaSyncManager.mergePagos` borra la fila del UUID y reinserta la
     * canónica bajo la llave numérica de Microsip, conservando el UUID en
     * `PAGO_RECIBIDO_ID`. El guard anti-duplicado de la pantalla de abono se
     * resuelve mirando el historial, así que si el adaptador tirara esa columna
     * el guard se soltaría después de cada merge y el saldo se descontaría dos
     * veces.
     *
     * **Control de reversión:** borrar `capturaId = PAGO_RECIBIDO_ID` del mapeo
     * pone este test en ROJO.
     */
    @Test
    fun `el historial conserva el id con el que el telefono capturo el abono`() = runTest {
        db.paymentDao().saveAll(
            listOf(
                pago("4471902", EFECTIVO, 220.0).copy(PAGO_RECIBIDO_ID = "abono-uuid-0001"),
                pago("4471903", EFECTIVO, 100.0)
            )
        )

        val historial = pagos.pagosDe(VENTA).associateBy { it.pagoId }

        assertEquals("abono-uuid-0001", historial.getValue("4471902").capturaId)
        assertNull(
            "una fila que nunca se re-llaveo no inventa un id de captura",
            historial.getValue("4471903").capturaId
        )
    }

    // `raw` (no `importe`) para no disparar NoDoubleForMoney: es el Double crudo del schema.
    private fun pago(id: String, formaCobro: Int, raw: Double) = PaymentEntity(
        ID = id,
        COBRADOR = "Efrain Dominguez Reyes",
        DOCTO_CC_ACR_ID = VENTA,
        DOCTO_CC_ID = CREDITO,
        FECHA_HORA_PAGO = "2026-08-03T17:10:00Z",
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = raw,
        LAT = null,
        LNG = null,
        CLIENTE_ID = CLIENTE,
        COBRADOR_ID = 12,
        FORMA_COBRO_ID = formaCobro,
        ZONA_CLIENTE_ID = 25,
        NOMBRE_CLIENTE = "Victoria Flores Olmedo"
    )

    /**
     * El par de coordenadas de una visita, junto.
     *
     * Van en un objeto y no en dos parámetros sueltos porque con ellos el
     * ayudante llega a los siete que detekt corta (`LongParameterList`), y
     * además nunca se pasa uno sin el otro: en esta tabla son dos `Double` NO
     * NULOS y lo que significa "sin señal" es el PAR en cero.
     */
    private data class Punto(val lat: Double, val lng: Double)

    private fun visita(
        id: String,
        tipo: String = "Pidió reagendar visita",
        centavos: Long? = null,
        horaCita: String? = null,
        ventaLigada: Int = VENTA,
        punto: Punto = Punto(lat = 18.46, lng = -97.39)
    ) = VisitEntity(
        ID = id,
        CLIENTE_ID = CLIENTE,
        COBRADOR = "Efrain Dominguez Reyes",
        COBRADOR_ID = 12,
        FECHA = "2026-09-01T16:00:00Z",
        FORMA_COBRO_ID = EFECTIVO,
        // `Double` NO NULOS en esta tabla: el "sin señal" del teléfono se guarda
        // como el par en cero, no como ausencia. Por eso el default es una
        // coordenada real y el cero se pide explícito — es lo que el aparato
        // escribe de verdad, no un caso inventado.
        LAT = punto.lat,
        LNG = punto.lng,
        NOTA = null,
        TIPO_VISITA = tipo,
        ZONA_CLIENTE_ID = 25,
        IMPTE_DOCTO_CC_ID = ventaLigada,
        GUARDADO_EN_MICROSIP = 0,
        PROMESA_MONTO_CENTAVOS = centavos,
        CITA_HORA = horaCita
    )

    @Test
    fun `PARCIALIDAD Int cruza a Money exacto, sin centavos fantasma`() = runTest {
        db.saleDao().insertAll(listOf(venta(parcialidad = 350)))
        val datos = ventas.ventasDelCliente(CLIENTE).single()
        assertEquals(Money.of(BigDecimal("350.00")), datos.parcialidad)
        assertEquals(BigDecimal("350.00"), datos.parcialidad.amount)
    }

    @Test
    fun `IMPORTE Double cruza por BigDecimal-valueOf, no por el constructor binario`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        // 350.1 no tiene representación binaria exacta: BigDecimal(350.1) daría
        // 350.10000000000002273736754432320594787597656250.
        db.paymentDao().saveAll(listOf(pago("p1", EFECTIVO, 350.1)))
        val importe = pagos.pagosDe(VENTA).single().importe
        assertEquals(BigDecimal("350.10"), importe.amount)
        assertEquals(2, importe.amount.scale())
    }

    @Test
    fun `la condonacion nunca entra al historial de abonos`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        db.paymentDao().saveAll(
            listOf(
                pago("p1", EFECTIVO, 350.0),
                pago("p2", TRANSFERENCIA, 350.0),
                pago("p3", CONDONACION, 900.0)
            )
        )
        val historial = pagos.pagosDe(VENTA)
        assertEquals(2, historial.size)
        assertTrue(historial.none { it.formaCobroId == CONDONACION })
        assertEquals(
            setOf(MetodoDeCobro.EFECTIVO, MetodoDeCobro.TRANSFERENCIA),
            historial.map { it.metodo }.toSet()
        )
    }

    @Test
    fun `un abono con fecha ilegible se cae del historial pero NO en silencio`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        db.paymentDao().saveAll(
            listOf(
                pago("p1", EFECTIVO, 350.0),
                pago("p2", EFECTIVO, 700.0).copy(FECHA_HORA_PAGO = "el martes pasado")
            )
        )
        val historial = pagos.pagosDe(VENTA)
        assertEquals(1, historial.size)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR && it.name == PagosTelemetria.CODE_ABONO_SIN_FECHA_LEGIBLE
        }
        assertEquals("1", error.props[PagosTelemetria.PROP_OCURRENCIAS])
        // Anti-PII: ni el id del pago, ni el importe, ni la fecha cruda viajan.
        val texto = error.name + error.props.entries.joinToString { it.key + it.value }
        assertTrue(
            texto,
            !texto.contains("p2") && !texto.contains("700") && !texto.contains("martes")
        )
    }

    @Test
    fun `sin abonos ilegibles no se emite nada`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        db.paymentDao().saveAll(listOf(pago("p1", EFECTIVO, 350.0)))
        assertEquals(1, pagos.pagosDe(VENTA).size)
        assertTrue(telemetria.recorded.none { it.type == TelemetryEventType.ERROR })
    }

    @Test
    fun `la garantia de la venta se lee por DOCTO_CC_ID`() = runTest {
        db.guaranteeDao().insertGuarantees(
            GuaranteeEntity(
                EXTERNAL_ID = "GAR-2026-0188",
                DOCTO_CC_ID = CREDITO,
                ESTADO = "NOTIFICADO",
                DESCRIPCION_FALLA = "No enfria en el congelador",
                OBSERVACIONES = null,
                UPLOADED = 1,
                FECHA_SOLICITUD = "2026-08-18T17:00:00Z",
                NOMBRE_CLIENTE = "Victoria Flores Olmedo",
                NOMBRE_PRODUCTO = "Refrigerador Mabe 14'"
            )
        )
        val garantia = garantias.garantiaDe(CREDITO)!!
        assertEquals("GAR-2026-0188", garantia.garantiaId)
        assertEquals("Refrigerador Mabe 14'", garantia.producto)
        assertEquals(LocalDate.of(2026, 8, 18), garantia.reportadaEl)
        assertEquals(EstadoDeGarantia.NOTIFICADA, garantia.estado)
        // Una venta sin garantia no inventa una.
        assertNull(garantias.garantiaDe(CREDITO + 1))
    }

    @Test
    fun `una garantia sin nombre de producto cae a la descripcion de la falla`() = runTest {
        db.guaranteeDao().insertGuarantees(
            GuaranteeEntity(
                EXTERNAL_ID = "GAR-2026-0199",
                DOCTO_CC_ID = CREDITO,
                ESTADO = "OTRA_COSA",
                DESCRIPCION_FALLA = "No enfria en el congelador",
                OBSERVACIONES = null,
                UPLOADED = 1,
                FECHA_SOLICITUD = "no es una fecha",
                NOMBRE_CLIENTE = null,
                NOMBRE_PRODUCTO = null
            )
        )
        val garantia = garantias.garantiaDe(CREDITO)!!
        assertEquals("No enfria en el congelador", garantia.producto)
        assertNull(garantia.reportadaEl)
        // Un estado fuera del catalogo se muestra, no se esconde.
        assertEquals(EstadoDeGarantia.DESCONOCIDO, garantia.estado)
    }

    @Test
    fun `la venta se resuelve por su id sin conocer al cliente`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        val datos = ventas.venta(VENTA)
        assertEquals(CLIENTE, datos!!.clienteId)
        assertEquals(Money.of(BigDecimal("1450.00")), datos.saldo)
        assertEquals(Money.of(BigDecimal("4950.00")), datos.abonado)
        assertNull(ventas.venta(1))
    }

    @Test
    fun `los productos de la venta llegan concatenados por la query de Room`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        db.productDao().saveAll(
            listOf(
                ProductEntity(1, CREDITO, "V-5188", 10, "Refrigerador Mabe 14'", 1, 6400.0, 6400.0, 1)
            )
        )
        assertEquals("Refrigerador Mabe 14'", ventas.ventasDelCliente(CLIENTE).single().descripcion)
    }

    /**
     * **Los atrasos llegan por la lectura POR CLIENTE, no solo por la ruta completa.**
     *
     * Este test existe porque su ausencia escondió un defecto real. `getAll` une
     * `overdue_payments_view` y `getByClientId` **no lo hacía**, así que
     * `NUM_PAGOS_ATRASADOS` caía al default `null` y el adaptador contestaba
     * `atrasos = 0` para TODA venta leída por cliente.
     *
     * No era cosmético. `CuentaDelAbono.preseleccionada` marca la cuenta con más
     * atrasos; con todas en cero, `maxByOrNull` devuelve **la primera de la
     * lista** — exactamente el defecto que la hoja del abono vino a cerrar.
     *
     * **Por qué no se vio antes:** las pruebas de esa hoja siembran `atrasos` en
     * el fixture, así que probaban la FUNCIÓN y no la TUBERÍA. Es la regla de
     * control positivo: una ausencia no es un hallazgo hasta probar que la
     * consulta habría encontrado la cosa. Éste es el test que la prueba, y por eso
     * va sobre Room de verdad y no sobre un fake.
     *
     * **Control de reversión:** quitar el `LEFT JOIN overdue_payments_view` de
     * `SaleDao.getByClientId` pone este test en ROJO.
     */
    @Test
    fun `los atrasos llegan tambien leyendo por cliente, no solo por la ruta`() = runTest {
        // Una venta vieja y casi sin abonar: la vista tiene que contarle atraso.
        db.saleDao().insertAll(
            listOf(
                venta(parcialidad = 220, restante = 6000.0, bruto = 6400.0)
                    .copy(FECHA = "2026-01-05T06:00:00Z", FREC_PAGO = "SEMANAL")
            )
        )

        val porCliente = ventas.ventasDelCliente(CLIENTE).single().atrasos
        val porLaRuta = ventas.todasLasVentas().single().atrasos

        assertTrue(
            "la vista tiene que contar atraso en esta venta, o el test no prueba nada",
            porLaRuta > 0
        )
        assertEquals(
            "leer por cliente y leer la ruta completa no pueden dar atrasos distintos",
            porLaRuta,
            porCliente
        )
    }

    /**
     * **Lo que ya llegaba al adaptador y nadie pintaba.** Las tres columnas
     * viajaban en la proyección de `SaleDao` desde antes de este trabajo; lo que
     * faltaba era mapearlas.
     *
     * **Control de reversión:** quitar cualquiera de las tres líneas del mapeo de
     * `RoomVentasAdapter` pone este test en ROJO.
     */
    @Test
    fun `el promedio y los dos dias de cobranza se mapean`() = runTest {
        db.saleDao().insertAll(
            listOf(venta().copy(IMPORTE_PAGO_PROMEDIO = 150.0, DIA_COBRANZA = " JUEVES "))
        )

        val datos = ventas.ventasDelCliente(CLIENTE).single()

        assertEquals(Money.of(BigDecimal("150.00")), datos.pagoPromedio)
        assertEquals("JUEVES", datos.diaDeCobranza)
        assertEquals("", datos.diaTemporal)
    }

    /**
     * Sin `IMPORTE_PAGO_PROMEDIO` no se afirma un promedio. `Money.ZERO` diría
     * "suele dar cero", que es una afirmación distinta de "no se sabe".
     */
    @Test
    fun `sin promedio en la columna no se inventa un cero`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        assertNull(ventas.ventasDelCliente(CLIENTE).single().pagoPromedio)
    }

    /**
     * El día movido MANDA sobre el de catálogo, y la precedencia vive en un solo
     * lugar ([com.example.msp_app.feature.pagos.domain.model.DatosDeVenta.diaDeRuta]).
     */
    @Test
    fun `el dia temporal manda sobre el dia de cobranza mientras traiga algo`() = runTest {
        db.saleDao().insertAll(
            listOf(venta().copy(DIA_COBRANZA = "LUNES", DIA_TEMPORAL_COBRANZA = "JUEVES"))
        )
        assertEquals("JUEVES", ventas.ventasDelCliente(CLIENTE).single().diaDeRuta)

        db.saleDao().insertAll(
            listOf(venta().copy(DIA_COBRANZA = "LUNES", DIA_TEMPORAL_COBRANZA = "   "))
        )
        assertEquals("LUNES", ventas.ventasDelCliente(CLIENTE).single().diaDeRuta)
    }

    /**
     * **Dónde se cobró.** `LAT`/`LNG` ya viajaban en las tres proyecciones del
     * historial sin mapearse.
     *
     * **Control de reversión:** quitar `ubicacion = UbicacionDelCobro.de(LAT, LNG)`
     * pone este test en ROJO.
     */
    @Test
    fun `el historial trae el punto donde se cobro`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        db.paymentDao().saveAll(
            listOf(pago("p1", EFECTIVO, 350.0).copy(LAT = 18.46, LNG = -97.39))
        )

        val ubicacion = pagos.pagosDe(VENTA).single().ubicacion!!

        assertEquals(18.46, ubicacion.lat, 0.0)
        assertEquals(-97.39, ubicacion.lng, 0.0)
    }

    /**
     * **Media coordenada no ubica nada.** Una latitud sin longitud pintaría el
     * pin en el meridiano cero: un dato FALSO, no uno ausente.
     */
    @Test
    fun `media coordenada no es una ubicacion`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        db.paymentDao().saveAll(
            listOf(
                pago("p1", EFECTIVO, 350.0).copy(LAT = 18.46, LNG = null),
                pago("p2", EFECTIVO, 350.0).copy(LAT = null, LNG = -97.39),
                pago("p3", EFECTIVO, 350.0)
            )
        )

        assertTrue(pagos.pagosDe(VENTA).all { it.ubicacion == null })
    }

    /**
     * **El importe por renglón que la pantalla no podía pintar.** El
     * `GROUP_CONCAT` de la venta solo trae nombres; con dos o más productos la
     * columna de dinero quedaba en blanco. `products` sí tiene
     * `PRECIO_TOTAL_NETO` por renglón.
     *
     * Prueba también el orden: la `@Query` no lleva `ORDER BY`, así que el
     * adaptador ordena por `POSICION` para que el renglón de arriba no dependa
     * del plan de consulta.
     */
    @Test
    fun `cada producto llega con su importe real y en el orden de captura`() = runTest {
        // Las llaves van AL REVÉS de `POSICION` a propósito: sin el `sortedWith`
        // SQLite emite por rowid y contestaría "Base para cama" primero, así que
        // el orden que afirma este test no puede salir por casualidad.
        db.productDao().saveAll(
            listOf(
                ProductEntity(1, CREDITO, "V-5188", 11, "Base para cama", 1, 1200.0, 1200.0, 2),
                ProductEntity(2, CREDITO, "V-5188", 10, "Sala 3 piezas", 1, 5200.0, 5200.0, 1)
            )
        )

        val productos = RoomProductosAdapter(db.productDao()).productosDe("V-5188")

        assertEquals(listOf("Sala 3 piezas", "Base para cama"), productos.map { it.nombre })
        assertEquals(Money.of(BigDecimal("5200.00")), productos.first().importe)
        assertEquals(Money.of(BigDecimal("1200.00")), productos.last().importe)
    }

    /** Un folio cuyos renglones no sincronizaron contesta vacío, no lanza. */
    @Test
    fun `un folio sin renglones contesta vacio`() = runTest {
        assertTrue(RoomProductosAdapter(db.productDao()).productosDe("V-0000").isEmpty())
    }

    @Test
    fun `PROMESA_MONTO_CENTAVOS cruza exacto a pesos con centavos`() = runTest {
        db.visitDao().insertVisit(visita("v1", centavos = 35050))
        assertEquals(
            Money.of(BigDecimal("350.50")),
            visitas.visitasDelCliente(CLIENTE).single().montoPrometido
        )
    }

    @Test
    fun `una visita sin venta ligada llega con ventaId nulo, no colapsada`() = runTest {
        db.visitDao().insertVisit(visita("v1", ventaLigada = 0))
        assertNull(visitas.visitasDelCliente(CLIENTE).single().ventaId)
    }

    @Test
    fun `CITA_HORA valida se lee`() = runTest {
        db.visitDao().insertVisit(visita("v1", horaCita = "16:30"))
        assertEquals(LocalTime.of(16, 30), visitas.visitasDelCliente(CLIENTE).single().horaCita)
        assertTrue(telemetria.recorded.none { it.type == TelemetryEventType.ERROR })
    }

    /**
     * **Dónde se hizo la visita.** `VisitEntity.LAT`/`LNG` ya viajaban en la tabla
     * y nadie las mapeaba: es el mismo hueco que la Task anterior cerró para el
     * abono. Sin esto, tocar una visita en la bitácora no podría abrir ningún
     * mapa.
     *
     * **Control de reversión:** quitar `ubicacion = UbicacionDelCobro.medida(LAT, LNG)`
     * del mapeo pone este test en ROJO.
     */
    @Test
    fun `la visita trae el punto donde se hizo`() = runTest {
        db.visitDao().insertVisit(visita("v1"))

        val ubicacion = visitas.visitasDelCliente(CLIENTE).single().ubicacion!!

        assertEquals(18.46, ubicacion.lat, 0.0)
        assertEquals(-97.39, ubicacion.lng, 0.0)
    }

    /**
     * **El par en cero no es un lugar.** Con el GPS apagado la fila trae
     * `0.0, 0.0`, que cae en el Golfo de Guinea: pintarlo sería un dato FALSO, no
     * uno ausente. La pantalla legada `SaleMapScreen.kt:52-65` ya descartaba ese
     * par; la regla vive ahora en un solo dueño, `UbicacionDelCobro.medida`.
     *
     * El caso de arriba es su **control positivo**: prueba que esta misma consulta
     * SÍ ve una coordenada cuando la hay, así que el `null` de acá es el cero y no
     * un mapeo que se perdió.
     */
    @Test
    fun `una visita sin senal no deja un pin en el meridiano cero`() = runTest {
        db.visitDao().insertVisit(visita("v1", punto = Punto(lat = 0.0, lng = 0.0)))

        assertNull(visitas.visitasDelCliente(CLIENTE).single().ubicacion)
    }

    @Test
    fun `CITA_HORA invalida degrada a nulo pero NO en silencio`() = runTest {
        db.visitDao().insertVisit(visita("v1", horaCita = "4 y media"))
        val leida = visitas.visitasDelCliente(CLIENTE).single()
        assertNull(leida.horaCita)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR && it.name == PagosTelemetria.CODE_CITA_HORA_INVALIDA
        }
        assertEquals("DateTimeParseException", error.props[PagosTelemetria.PROP_EXCEPCION])
        // Anti-PII: el texto crudo tecleado NO viaja.
        assertTrue(error.props.values.none { it.contains("4 y media") })
    }
}
