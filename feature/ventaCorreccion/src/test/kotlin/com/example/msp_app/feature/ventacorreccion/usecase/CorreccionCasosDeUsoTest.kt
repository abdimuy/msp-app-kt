package com.example.msp_app.feature.ventacorreccion.usecase

import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.ventacorreccion.data.RoomVentaLocalCorreccionAdapter
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeReencolar
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeReloj
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.CorreccionRemotaTerminal
import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.VentaLocalParaCorregir
import com.example.msp_app.feature.ventacorreccion.domain.port.VentaLocalCorreccionPort
import com.example.msp_app.feature.ventacorreccion.domain.usecase.CancelarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardadoRechazadoException
import com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ReclamarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ResultadoReclamo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SALE_ID = "sale-correccion-001"
private const val EMAIL = "cobrador.pruebas@muebleriamsp.mx"

/**
 * `ReclamarCorreccion`/`GuardarCorreccion`/`CancelarCorreccion` de punta a punta sobre Room
 * (Task 3 del plan "Corregir una venta antes de que suba"): el guardia del guardado va primero
 * en la transacción, el reencolado es una optimización que nunca bloquea el commit, y corregir
 * dos veces seguidas no deja filas huérfanas ni pierde `SERVER_UUID`.
 */
class CorreccionCasosDeUsoTest : RoomTestBase() {

    private val clock = FakeClock.at("2026-09-20T12:00:00Z")
    private lateinit var port: VentaLocalCorreccionPort
    private lateinit var reencolar: FakeReencolar
    private lateinit var reclamar: ReclamarCorreccion
    private lateinit var guardar: GuardarCorreccion
    private lateinit var cancelar: CancelarCorreccion

    @Before
    fun setUpCasosDeUso() {
        port = RoomVentaLocalCorreccionAdapter(
            db,
            db.localSaleDao(),
            db.localSaleProduct(),
            db.localSaleComboDao()
        )
        val reloj = FakeReloj(clock)
        reencolar = FakeReencolar()
        reclamar = ReclamarCorreccion(port, reloj, reencolar)
        guardar = GuardarCorreccion(port, reloj, reencolar)
        cancelar = CancelarCorreccion(port, reencolar)
    }

    // ─── Fixtures propios (duplicados a propósito — convención del repo) ──

    @Suppress("LongParameterList")
    private fun freeSale(
        saleId: String = SALE_ID,
        nombreCliente: String = "Rosa Elena Martinez Vazquez",
        idempotencyKey: String? = "idem-original-sin-tocar",
        enviado: Boolean = false,
        correccionRemotaPendiente: Boolean = false,
        correccionRemotaEstado: String? = null
    ) = LocalSaleEntity(
        LOCAL_SALE_ID = saleId,
        NOMBRE_CLIENTE = nombreCliente,
        FECHA_VENTA = "2026-09-18T15:30:00Z",
        LATITUD = 19.043415,
        LONGITUD = -98.198234,
        DIRECCION = "Privada de las Rosas 45",
        PARCIALIDAD = 850.0,
        ENGANCHE = 500.0,
        TELEFONO = "2221234567",
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = "Juan Martinez Vazquez",
        NOTA = null,
        DIA_COBRANZA = "MARTES",
        PRECIO_TOTAL = 6800.0,
        TIEMPO_A_CORTO_PLAZOMESES = 8,
        MONTO_A_CORTO_PLAZO = 6300.0,
        MONTO_DE_CONTADO = 5800.0,
        ENVIADO = enviado,
        IDEMPOTENCY_KEY = idempotencyKey,
        CORRECCION_REMOTA_PENDIENTE = correccionRemotaPendiente,
        CORRECCION_REMOTA_ESTADO = correccionRemotaEstado
    )

    private fun producto(
        saleId: String = SALE_ID,
        articuloId: Int,
        cantidad: Int = 1,
        serverUuid: String? = null
    ) = LocalSaleProductEntity(
        LOCAL_SALE_ID = saleId,
        ARTICULO_ID = articuloId,
        ARTICULO = "Recamara matrimonial $articuloId",
        CANTIDAD = cantidad,
        PRECIO_LISTA = 1000.0,
        PRECIO_CORTO_PLAZO = 1100.0,
        PRECIO_CONTADO = 900.0,
        COMBO_ID = null,
        SERVER_UUID = serverUuid
    )

    private fun combo(saleId: String = SALE_ID, comboId: String, serverUuid: String? = null) =
        LocalSaleComboEntity(
            COMBO_ID = comboId,
            LOCAL_SALE_ID = saleId,
            NOMBRE_COMBO = "Combo sala $comboId",
            PRECIO_LISTA = 5000.0,
            PRECIO_CORTO_PLAZO = 5500.0,
            PRECIO_CONTADO = 4500.0,
            SERVER_UUID = serverUuid
        )

    private fun campos(nombreCliente: String = "Rosa Elena Martinez Vazquez Corregido") =
        CamposVentaCorregidos(
            nombreCliente = nombreCliente,
            fechaVenta = "2026-09-18T15:30:00Z",
            latitud = 19.043415,
            longitud = -98.198234,
            direccion = "Privada de las Rosas 45",
            parcialidad = 900.0,
            enganche = 500.0,
            telefono = "2221234567",
            frecPago = "SEMANAL",
            avalOResponsable = "Juan Martinez Vazquez",
            nota = null,
            diaCobranza = "MARTES",
            precioTotal = 7200.0,
            tiempoACortoPlazoMeses = 8,
            montoACortoPlazo = 6600.0,
            montoDeContado = 6000.0
        )

    private suspend fun insertSale(sale: LocalSaleEntity) = db.localSaleDao().insertSale(sale)

    /**
     * La cola de ALTA, tal cual la ve el barrido de sesión — con los arrendamientos de
     * PRODUCCIÓN ([LocalSaleClaimLeases]), no con cifras inventadas aquí. Es la mitad de la
     * medición del invariante del nivel 2: una venta ya enviada no puede volver a aparecer en
     * esta lista NUNCA.
     */
    private suspend fun ventasParaSubir(): List<LocalSaleEntity> =
        db.localSaleDao().getUploadableSales(
            now = clock.now().toEpochMilli(),
            editLeaseMs = LocalSaleClaimLeases.EDIT_LEASE_MS,
            uploadLeaseMs = LocalSaleClaimLeases.UPLOAD_LEASE_MS,
            remoteLeaseMs = LocalSaleClaimLeases.REMOTE_LEASE_MS
        )

    private suspend fun insertProducts(products: List<LocalSaleProductEntity>) =
        db.localSaleProduct().insertAllSaleProducts(products)

    private suspend fun insertCombos(combos: List<LocalSaleComboEntity>) =
        db.localSaleComboDao().insertAllCombos(combos)

    /** Compara TODOS los campos de `local_sale` — `LocalSaleEntity` no es `data class`. */
    @Suppress("LongMethod")
    private fun assertSaleUnchanged(esperada: LocalSaleEntity, actual: LocalSaleEntity) {
        assertEquals(esperada.LOCAL_SALE_ID, actual.LOCAL_SALE_ID)
        assertEquals(esperada.NOMBRE_CLIENTE, actual.NOMBRE_CLIENTE)
        assertEquals(esperada.FECHA_VENTA, actual.FECHA_VENTA)
        assertEquals(esperada.LATITUD, actual.LATITUD, 0.0)
        assertEquals(esperada.LONGITUD, actual.LONGITUD, 0.0)
        assertEquals(esperada.DIRECCION, actual.DIRECCION)
        assertEquals(esperada.PARCIALIDAD, actual.PARCIALIDAD, 0.0)
        assertEquals(esperada.ENGANCHE, actual.ENGANCHE)
        assertEquals(esperada.TELEFONO, actual.TELEFONO)
        assertEquals(esperada.FREC_PAGO, actual.FREC_PAGO)
        assertEquals(esperada.AVAL_O_RESPONSABLE, actual.AVAL_O_RESPONSABLE)
        assertEquals(esperada.NOTA, actual.NOTA)
        assertEquals(esperada.DIA_COBRANZA, actual.DIA_COBRANZA)
        assertEquals(esperada.PRECIO_TOTAL, actual.PRECIO_TOTAL, 0.0)
        assertEquals(esperada.TIEMPO_A_CORTO_PLAZOMESES, actual.TIEMPO_A_CORTO_PLAZOMESES)
        assertEquals(esperada.MONTO_A_CORTO_PLAZO, actual.MONTO_A_CORTO_PLAZO, 0.0)
        assertEquals(esperada.MONTO_DE_CONTADO, actual.MONTO_DE_CONTADO, 0.0)
        assertEquals(esperada.ENVIADO, actual.ENVIADO)
        assertEquals(esperada.NUMERO, actual.NUMERO)
        assertEquals(esperada.COLONIA, actual.COLONIA)
        assertEquals(esperada.POBLACION, actual.POBLACION)
        assertEquals(esperada.CIUDAD, actual.CIUDAD)
        assertEquals(esperada.TIPO_VENTA, actual.TIPO_VENTA)
        assertEquals(esperada.ZONA_CLIENTE_ID, actual.ZONA_CLIENTE_ID)
        assertEquals(esperada.ZONA_CLIENTE, actual.ZONA_CLIENTE)
        assertEquals(esperada.CLIENTE_ID, actual.CLIENTE_ID)
        assertEquals(esperada.LAST_UPLOAD_HTTP_CODE, actual.LAST_UPLOAD_HTTP_CODE)
        assertEquals(esperada.LAST_UPLOAD_ERROR_CODE, actual.LAST_UPLOAD_ERROR_CODE)
        assertEquals(esperada.LAST_UPLOAD_ERROR_MESSAGE, actual.LAST_UPLOAD_ERROR_MESSAGE)
        assertEquals(esperada.LAST_UPLOAD_AT, actual.LAST_UPLOAD_AT)
        assertEquals(esperada.LAST_UPLOAD_PERMANENT, actual.LAST_UPLOAD_PERMANENT)
        assertEquals(esperada.IDEMPOTENCY_KEY, actual.IDEMPOTENCY_KEY)
        assertEquals(esperada.CLAIM_ID, actual.CLAIM_ID)
        assertEquals(esperada.CLAIM_KIND, actual.CLAIM_KIND)
        assertEquals(esperada.CLAIMED_AT, actual.CLAIMED_AT)
        assertEquals(esperada.REVISION, actual.REVISION)
        assertEquals(esperada.CORRECCION_NO_ENVIADA, actual.CORRECCION_NO_ENVIADA)
        // Las dos columnas del nivel 2: sin ellas aquí, un rechazo que además levantara la cola
        // de correcciones remotas pasaría por "nada se escribió".
        assertEquals(esperada.CORRECCION_REMOTA_PENDIENTE, actual.CORRECCION_REMOTA_PENDIENTE)
        assertEquals(esperada.CORRECCION_REMOTA_ESTADO, actual.CORRECCION_REMOTA_ESTADO)
    }

    // ─── Feliz ──────────────────────────────────────────────────────────

    /**
     * Siembra la venta feliz (con un fallo previo transitorio y una IDEMPOTENCY_KEY original),
     * reclama y guarda una corrección que quita un artículo, cambia la cantidad de otro, agrega
     * uno nuevo y conserva el combo. Split en dos `@Test` (campos de la venta / líneas) por
     * `detekt.LongMethod` — un solo método superaba las 60 líneas.
     */
    private suspend fun sembrarYCorregirVentaFeliz() {
        insertSale(freeSale(idempotencyKey = "idem-original-sin-tocar"))
        insertProducts(
            listOf(
                producto(articuloId = 1, cantidad = 1, serverUuid = "uuid-articulo-1"),
                producto(articuloId = 2, cantidad = 2, serverUuid = "uuid-articulo-2")
            )
        )
        insertCombos(listOf(combo(comboId = "combo-1", serverUuid = "uuid-combo-1")))
        db.localSaleDao().updateUploadFailure(
            saleId = SALE_ID,
            httpCode = 500,
            errorCode = "server_error",
            errorMessage = "boom",
            at = clock.now().toEpochMilli(),
            permanent = false
        )

        val reclamo = reclamar(SALE_ID)
        check(reclamo is ResultadoReclamo.Reclamada)

        // Corrige: articulo 1 sobrevive con cantidad nueva, articulo 2 se quita, articulo 3
        // es nuevo; el combo sobrevive.
        guardar(
            SALE_ID,
            reclamo.claimId,
            campos(nombreCliente = "Rosa Elena Martinez Vazquez Corregido"),
            listOf(
                producto(articuloId = 1, cantidad = 5, serverUuid = "basura-del-formulario"),
                producto(articuloId = 3, cantidad = 1)
            ),
            listOf(combo(comboId = "combo-1", serverUuid = "basura-del-formulario")),
            EMAIL
        )
    }

    @Test
    fun `feliz - guardar deja los campos de la venta correctos, REVISION en 1, candado cerrado, fallo limpiado, IDEMPOTENCY_KEY intacta`() =
        runTest {
            sembrarYCorregirVentaFeliz()

            val sale = db.localSaleDao().getSaleById(SALE_ID)
            assertNotNull(sale)
            assertEquals("Rosa Elena Martinez Vazquez Corregido", sale?.NOMBRE_CLIENTE)
            assertEquals(7200.0, sale?.PRECIO_TOTAL)
            assertEquals(1, sale?.REVISION)
            assertNull("el candado debe cerrarse", sale?.CLAIM_ID)
            assertNull(sale?.CLAIM_KIND)
            assertNull(sale?.CLAIMED_AT)
            assertEquals(
                "la Idempotency-Key NUNCA se rota en este flujo",
                "idem-original-sin-tocar",
                sale?.IDEMPOTENCY_KEY
            )
            assertNull("el fallo previo debe limpiarse", sale?.LAST_UPLOAD_HTTP_CODE)
            assertNull(sale?.LAST_UPLOAD_ERROR_CODE)
            assertNull(sale?.LAST_UPLOAD_ERROR_MESSAGE)
            assertNull(sale?.LAST_UPLOAD_AT)
            assertNull(sale?.LAST_UPLOAD_PERMANENT)
        }

    @Test
    fun `feliz - guardar deja los productos y combos correctos (sobrevive, nuevo, quitado)`() =
        runTest {
            sembrarYCorregirVentaFeliz()

            val productos = db.localSaleProduct().getProductsForSale(SALE_ID)
            assertEquals(2, productos.size)
            val sobreviviente = productos.single { it.ARTICULO_ID == 1 }
            assertEquals(
                "LA BASE MANDA sobre el SERVER_UUID",
                "uuid-articulo-1",
                sobreviviente.SERVER_UUID
            )
            assertEquals(5, sobreviviente.CANTIDAD)
            val nuevo = productos.single { it.ARTICULO_ID == 3 }
            assertNull("linea nueva sin SERVER_UUID", nuevo.SERVER_UUID)
            assertTrue("articulo 2 se quito", productos.none { it.ARTICULO_ID == 2 })

            val combos = db.localSaleComboDao().getCombosForSale(SALE_ID)
            assertEquals(1, combos.size)
            assertEquals("uuid-combo-1", combos.single().SERVER_UUID)
        }

    // ─── Corregir dos veces seguidas ────────────────────────────────────

    @Test
    fun `corregir dos veces seguidas, con productos y combos distintos cada vez, sube REVISION a 2 sin filas huerfanas`() =
        runTest {
            insertSale(freeSale())
            insertProducts(listOf(producto(articuloId = 1, cantidad = 2, serverUuid = "uuid-1")))

            val primerReclamo = reclamar(SALE_ID)
            check(primerReclamo is ResultadoReclamo.Reclamada)
            guardar(
                SALE_ID,
                primerReclamo.claimId,
                campos(nombreCliente = "Correccion Uno"),
                listOf(
                    producto(articuloId = 1, cantidad = 3),
                    producto(articuloId = 2, cantidad = 1)
                ),
                listOf(combo(comboId = "combo-de-la-primera")),
                EMAIL
            )
            assertEquals(1, db.localSaleDao().getSaleById(SALE_ID)?.REVISION)

            val segundoReclamo = reclamar(SALE_ID)
            check(segundoReclamo is ResultadoReclamo.Reclamada)
            assertNotEquals(primerReclamo.claimId, segundoReclamo.claimId)
            guardar(
                SALE_ID,
                segundoReclamo.claimId,
                campos(nombreCliente = "Correccion Dos"),
                listOf(
                    producto(articuloId = 1, cantidad = 4),
                    producto(articuloId = 3, cantidad = 1)
                ),
                listOf(combo(comboId = "combo-de-la-segunda")),
                EMAIL
            )

            val sale = db.localSaleDao().getSaleById(SALE_ID)
            assertEquals(2, sale?.REVISION)
            assertEquals("Correccion Dos", sale?.NOMBRE_CLIENTE)
            assertNull(sale?.CLAIM_ID)

            val productos = db.localSaleProduct().getProductsForSale(SALE_ID)
            assertEquals(2, productos.size)
            val sobreviviente = productos.single { it.ARTICULO_ID == 1 }
            assertEquals(
                "el SERVER_UUID original sobrevive a AMBAS correcciones",
                "uuid-1",
                sobreviviente.SERVER_UUID
            )
            assertEquals(4, sobreviviente.CANTIDAD)
            assertNull(productos.single { it.ARTICULO_ID == 3 }.SERVER_UUID)
            assertTrue(
                "articulo 2 (de la primera correccion) no debe quedar huerfano",
                productos.none { it.ARTICULO_ID == 2 }
            )

            val combos = db.localSaleComboDao().getCombosForSale(SALE_ID)
            assertEquals(1, combos.size)
            assertEquals(
                "el combo de la primera correccion no debe quedar huerfano",
                "combo-de-la-segunda",
                combos.single().COMBO_ID
            )
        }

    // ─── Guardar con reclamo ajeno ──────────────────────────────────────

    @Test
    fun `guardar_con_reclamo_ajeno_no_escribe_nada`() = runTest {
        insertSale(freeSale())
        insertProducts(listOf(producto(articuloId = 1, cantidad = 1, serverUuid = "uuid-1")))
        // Minor #3 de la ronda 1 de arreglo: la prueba original no sembraba combos, así que un
        // mutante que sólo protegiera productos y dejara escapar el merge de combos hubiera
        // pasado igual.
        insertCombos(listOf(combo(comboId = "combo-1", serverUuid = "uuid-combo-1")))
        val reclamo = reclamar(SALE_ID)
        check(reclamo is ResultadoReclamo.Reclamada)

        val saleAntes = db.localSaleDao().getSaleById(SALE_ID)
        assertNotNull(saleAntes)
        val productosAntes = db.localSaleProduct().getProductsForSale(SALE_ID)
        val combosAntes = db.localSaleComboDao().getCombosForSale(SALE_ID)

        var excepcion: GuardadoRechazadoException? = null
        try {
            guardar(
                SALE_ID,
                "claim-ajeno-que-nunca-se-tomo",
                campos(nombreCliente = "Este nombre NUNCA debe quedar"),
                listOf(producto(articuloId = 1, cantidad = 999)),
                listOf(combo(comboId = "combo-nuevo-que-nunca-debe-quedar")),
                EMAIL
            )
        } catch (rechazo: GuardadoRechazadoException) {
            excepcion = rechazo
        }

        assertNotNull("debe lanzar GuardadoRechazadoException", excepcion)

        val saleDespues = db.localSaleDao().getSaleById(SALE_ID)
        assertNotNull(saleDespues)
        assertSaleUnchanged(saleAntes!!, saleDespues!!)
        assertEquals(productosAntes, db.localSaleProduct().getProductsForSale(SALE_ID))
        assertEquals(combosAntes, db.localSaleComboDao().getCombosForSale(SALE_ID))
    }

    // ─── Guardar sobre venta ya enviada (el nivel 2) ────────────────────

    /**
     * **El invariante más caro de todo este trabajo.** Corregir una venta que YA subió deja la
     * fila con `ENVIADO = 1` **y** `CORRECCION_REMOTA_PENDIENTE = 1`, en la misma transacción.
     *
     * Si `ENVIADO` bajara a 0 — que es lo que hace el guardado de una venta SIN enviar, y lo que
     * hacía este mismo adaptador antes del nivel 2 — la venta volvería a la cola de ALTA con su
     * `Idempotency-Key` original: el servidor contestaría con la respuesta que ya tenía
     * guardada, no actualizaría nada, y el teléfono se quedaría creyendo que reenvió. La
     * corrección se perdería en silencio, que es el modo de falla exacto que la cola remota
     * existe para evitar.
     *
     * Por eso el invariante no se mide sólo leyendo las dos columnas: se mide preguntándole a
     * las DOS colas quién reclama esta venta. `getUploadableSales` (la de alta) no debe verla
     * nunca más, y `getVentasConCorreccionRemotaPendiente` (la de correcciones) debe verla
     * exactamente a ella. Un mutante que baje `ENVIADO` pone rojas las dos aserciones.
     */
    @Test
    fun `corregir una venta YA ENVIADA la deja con ENVIADO en 1 y en la cola de correcciones, nunca en la de alta`() =
        runTest {
            insertSale(freeSale(enviado = true, idempotencyKey = "idem-original-sin-tocar"))
            insertProducts(listOf(producto(articuloId = 1, cantidad = 1, serverUuid = "uuid-1")))

            val reclamo = reclamar(SALE_ID)
            check(reclamo is ResultadoReclamo.Reclamada)
            guardar(
                SALE_ID,
                reclamo.claimId,
                campos(nombreCliente = "Corregida DESPUES de subir"),
                listOf(producto(articuloId = 1, cantidad = 7)),
                emptyList(),
                EMAIL
            )

            val sale = db.localSaleDao().getSaleById(SALE_ID)!!
            assertTrue(
                "ENVIADO NUNCA puede bajar: la venta volveria a la cola de ALTA con su " +
                    "Idempotency-Key y el servidor contestaria con la respuesta vieja",
                sale.ENVIADO
            )
            assertTrue(
                "sin la bandera de la cola remota, nadie entrega la correccion",
                sale.CORRECCION_REMOTA_PENDIENTE
            )
            assertNull(
                "y sin marca terminal: el servidor todavia no ha dicho nada",
                sale.CORRECCION_REMOTA_ESTADO
            )
            assertEquals("Corregida DESPUES de subir", sale.NOMBRE_CLIENTE)
            assertEquals(1, sale.REVISION)
            assertNull("el candado se cierra igual que en el nivel 1", sale.CLAIM_ID)
            assertEquals(
                "la Idempotency-Key NUNCA se rota en este flujo",
                "idem-original-sin-tocar",
                sale.IDEMPOTENCY_KEY
            )
            assertEquals(7, db.localSaleProduct().getProductsForSale(SALE_ID).single().CANTIDAD)

            assertTrue(
                "la cola de ALTA no puede volver a verla NUNCA",
                ventasParaSubir().none { it.LOCAL_SALE_ID == SALE_ID }
            )
            assertEquals(
                "y la cola de CORRECCIONES tiene que verla exactamente a ella",
                listOf(SALE_ID),
                db.localSaleDao().getVentasConCorreccionRemotaPendiente().map { it.LOCAL_SALE_ID }
            )
        }

    /**
     * La contracara del invariante de arriba, con el mismo control positivo: una venta SIN
     * enviar sigue bajando `ENVIADO` a `false` y NO levanta la cola remota — el camino del
     * nivel 1 no cambió. Sin esta prueba, un "arreglo" que dejara `ENVIADO` intacto en los DOS
     * caminos pasaría verde arriba y rompería el nivel 1 sin que nada lo dijera.
     */
    @Test
    fun `corregir una venta SIN enviar no levanta la cola remota y la deja en la cola de alta`() =
        runTest {
            insertSale(freeSale())
            val reclamo = reclamar(SALE_ID)
            check(reclamo is ResultadoReclamo.Reclamada)

            guardar(
                SALE_ID,
                reclamo.claimId,
                campos(nombreCliente = "Corregida ANTES de subir"),
                emptyList(),
                emptyList(),
                EMAIL
            )

            val sale = db.localSaleDao().getSaleById(SALE_ID)!!
            assertEquals(false, sale.ENVIADO)
            assertEquals(false, sale.CORRECCION_REMOTA_PENDIENTE)
            assertTrue(
                "la venta sin enviar SI tiene que seguir en la cola de alta",
                ventasParaSubir().any { it.LOCAL_SALE_ID == SALE_ID }
            )
            assertTrue(db.localSaleDao().getVentasConCorreccionRemotaPendiente().isEmpty())
        }

    /**
     * Encimarle una segunda corrección a una que todavía no viajó dejaría al trabajador
     * entregando un cuerpo que nadie revisó. El editor ni se abre: `claimForEdit` rechaza
     * `CORRECCION_REMOTA_PENDIENTE = 1`, y el dominio clasifica el porqué.
     */
    @Test
    fun `con una correccion ya en la cola, el editor ni se abre`() = runTest {
        insertSale(freeSale(enviado = true))
        val primero = reclamar(SALE_ID)
        check(primero is ResultadoReclamo.Reclamada)
        guardar(
            SALE_ID,
            primero.claimId,
            campos(nombreCliente = "La primera, que si viaja"),
            emptyList(),
            emptyList(),
            EMAIL
        )

        val segundo = reclamar(SALE_ID)

        assertEquals(
            ResultadoReclamo.NoCorregible(EstadoCorreccion.CorreccionEnCamino),
            segundo
        )
        assertEquals(
            "la segunda no pudo pisar a la primera",
            "La primera, que si viaja",
            db.localSaleDao().getSaleById(SALE_ID)?.NOMBRE_CLIENTE
        )
    }

    /**
     * Marca TERMINAL: el servidor cerró la puerta para siempre (la venta salió de `borrador`, o
     * la oficina escribió primero). Ni el editor se abre ni hay nada que reintentar.
     */
    @Test
    fun `con la marca terminal del servidor, el editor ni se abre`() = runTest {
        insertSale(
            freeSale(
                enviado = true,
                correccionRemotaEstado = CorreccionRemotaTerminal.RECHAZADA_ESTADO
            )
        )

        val reclamo = reclamar(SALE_ID)

        assertEquals(
            ResultadoReclamo.NoCorregible(EstadoCorreccion.LaOficinaYaLaAplico),
            reclamo
        )
    }

    /**
     * El candado se pierde entre reclamar y guardar (aquí: el subidor gana la carrera y
     * `markSentAndCloseEdit` cierra el candado incondicionalmente). El guardado se rechaza y NO
     * escribe nada — ni los campos, ni la cola remota.
     *
     * Lo que cambió con el nivel 2 es el estado con el que se clasifica el rechazo: ya no
     * [EstadoCorreccion.YaSeEnvio] sino [EstadoCorreccion.CorregibleEnviada], porque la fila
     * releída sigue siendo corregible — sólo que no con ESTE `claimId`. El texto que la UI
     * muestra sí sigue sin prometer nada (`No se pudo guardar`, ver
     * `CorreccionVentaViewModel.aTextoDeRechazoDeGuardado`).
     */
    @Test
    fun `guardar con el candado ya cerrado por el subidor se rechaza y no escribe nada`() =
        runTest {
            insertSale(freeSale())
            val reclamo = reclamar(SALE_ID)
            check(reclamo is ResultadoReclamo.Reclamada)

            // El subidor gana la carrera: marca ENVIADO=1 y cierra el candado.
            db.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 0)

            val saleAntes = db.localSaleDao().getSaleById(SALE_ID)
            assertNotNull(saleAntes)
            assertEquals(true, saleAntes?.ENVIADO)

            var excepcion: GuardadoRechazadoException? = null
            try {
                guardar(
                    SALE_ID,
                    reclamo.claimId,
                    campos(nombreCliente = "Llega tarde"),
                    emptyList(),
                    emptyList(),
                    EMAIL
                )
            } catch (rechazo: GuardadoRechazadoException) {
                excepcion = rechazo
            }

            assertNotNull(excepcion)
            assertEquals(EstadoCorreccion.CorregibleEnviada, excepcion?.estado)

            val saleDespues = db.localSaleDao().getSaleById(SALE_ID)
            assertSaleUnchanged(saleAntes!!, saleDespues!!)
        }

    // ─── Cancelar ───────────────────────────────────────────────────────

    @Test
    fun `cancelar suelta el reclamo y deja los datos originales intactos`() = runTest {
        insertSale(freeSale())
        insertProducts(listOf(producto(articuloId = 1, cantidad = 1, serverUuid = "uuid-1")))
        val reclamo = reclamar(SALE_ID)
        check(reclamo is ResultadoReclamo.Reclamada)
        val productosAntes = db.localSaleProduct().getProductsForSale(SALE_ID)
        val nombreAntes = db.localSaleDao().getSaleById(SALE_ID)?.NOMBRE_CLIENTE

        cancelar(SALE_ID, reclamo.claimId, EMAIL)

        val sale = db.localSaleDao().getSaleById(SALE_ID)
        assertNull("el candado se suelta", sale?.CLAIM_ID)
        assertNull(sale?.CLAIM_KIND)
        assertNull(sale?.CLAIMED_AT)
        assertEquals(0, sale?.REVISION)
        assertEquals(nombreAntes, sale?.NOMBRE_CLIENTE)
        assertEquals(productosAntes, db.localSaleProduct().getProductsForSale(SALE_ID))
        assertEquals(1, reencolar.llamadasReencolar)
    }

    // ─── El reencolado es optimización, nunca un requisito ─────────────

    @Test
    fun `guardar persiste la correccion aunque el reencolado lance (proceso muerto justo despues del commit)`() =
        runTest {
            insertSale(freeSale())
            val reclamo = reclamar(SALE_ID)
            check(reclamo is ResultadoReclamo.Reclamada)
            reencolar.lanzarEnReencolar = IllegalStateException("proceso muerto, simulado")

            guardar(
                SALE_ID,
                reclamo.claimId,
                campos(nombreCliente = "Sobrevive al crash del reencolado"),
                emptyList(),
                emptyList(),
                EMAIL
            )

            val sale = db.localSaleDao().getSaleById(SALE_ID)
            assertEquals("Sobrevive al crash del reencolado", sale?.NOMBRE_CLIENTE)
            assertEquals(1, sale?.REVISION)
            assertEquals("el reencolado SI se intento", 1, reencolar.llamadasReencolar)
        }

    // ─── Reclamar tras "matar" la app invalida la sesion vieja ─────────

    /**
     * El escenario completo que pidió el orquestador: reclamar → "matar" (nadie suelta el
     * candado) → reclamar de nuevo → guardar → la corrección queda, sin filas huérfanas. La
     * sesión vieja, que nunca se entera, intenta guardar después y no puede — su `claimId` ya
     * no es el vigente.
     */
    @Test
    fun `reclamar tras matar la app reclama de nuevo, la sesion vieja no puede sobrescribir la correccion`() =
        runTest {
            insertSale(freeSale())
            insertProducts(listOf(producto(articuloId = 1, cantidad = 1, serverUuid = "uuid-1")))

            val sesionVieja = reclamar(SALE_ID)
            check(sesionVieja is ResultadoReclamo.Reclamada)

            // La app "muere": nadie llama a CancelarCorreccion. Se reabre y reclama de nuevo.
            val sesionNueva = reclamar(SALE_ID)
            check(sesionNueva is ResultadoReclamo.Reclamada)
            assertNotEquals(sesionVieja.claimId, sesionNueva.claimId)

            guardar(
                SALE_ID,
                sesionNueva.claimId,
                campos(nombreCliente = "Guardado por la sesion nueva"),
                listOf(
                    producto(articuloId = 1, cantidad = 9, serverUuid = "basura-del-formulario")
                ),
                emptyList(),
                EMAIL
            )

            var excepcion: GuardadoRechazadoException? = null
            try {
                guardar(
                    SALE_ID,
                    sesionVieja.claimId,
                    campos(nombreCliente = "La sesion vieja NO debe ganar"),
                    listOf(producto(articuloId = 1, cantidad = 999)),
                    emptyList(),
                    EMAIL
                )
            } catch (rechazo: GuardadoRechazadoException) {
                excepcion = rechazo
            }
            assertNotNull(
                "el claimId de la sesion vieja ya no es el vigente: debe rechazarse",
                excepcion
            )

            val sale = db.localSaleDao().getSaleById(SALE_ID)
            assertEquals(
                "solo la sesion nueva debe haber commiteado",
                "Guardado por la sesion nueva",
                sale?.NOMBRE_CLIENTE
            )
            assertEquals(1, sale?.REVISION)

            val productos = db.localSaleProduct().getProductsForSale(SALE_ID)
            assertEquals("sin filas huerfanas", 1, productos.size)
            assertEquals(9, productos.single().CANTIDAD)
            assertEquals("uuid-1", productos.single().SERVER_UUID)
        }

    // ─── El candado no queda huérfano si la lectura posterior falla ────

    /** Decorador que delega todo en [delegado] excepto [leerVenta], que SIEMPRE lanza. */
    private class PortQueLanzaAlLeerVenta(
        private val delegado: VentaLocalCorreccionPort
    ) : VentaLocalCorreccionPort by delegado {
        override suspend fun leerVenta(saleId: String): VentaLocalParaCorregir? {
            error("Room fallo leyendo productos, simulado")
        }
    }

    /**
     * Important #1 de la ronda 1 de arreglo: si `leerVenta` lanza DESPUÉS de tomar el candado,
     * antes de este fix el candado quedaba huérfano — nadie más lo tenía, pero tampoco nadie lo
     * soltó, así que la venta no subía durante los 30 minutos del arrendamiento
     * (`claimForUpload`/`getUploadableSales` la excluyen mientras el candado siga vigente).
     */
    @Test
    fun `reclamar suelta el candado si la lectura de la venta lanza (no lo deja huerfano)`() =
        runTest {
            insertSale(freeSale())
            val reclamarConLecturaRota =
                ReclamarCorreccion(PortQueLanzaAlLeerVenta(port), FakeReloj(clock), reencolar)

            var excepcion: Throwable? = null
            try {
                reclamarConLecturaRota(SALE_ID)
            } catch (e: IllegalStateException) {
                excepcion = e
            }

            assertNotNull("el error real debe propagarse, no tragarselo", excepcion)

            val sale = db.localSaleDao().getSaleById(SALE_ID)
            assertNull("el candado NO debe quedar huerfano", sale?.CLAIM_ID)
            assertNull(sale?.CLAIM_KIND)
            assertNull(sale?.CLAIMED_AT)

            // Y la venta sí se puede volver a reclamar de inmediato — no quedó retenida.
            val siguienteIntento = reclamar(SALE_ID)
            assertTrue(siguienteIntento is ResultadoReclamo.Reclamada)
        }
}
