package com.example.msp_app.feature.ventacorreccion.usecase

import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeReencolar
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeReloj
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeVentaLocalCorreccionPort
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.EstadoVentaLocal
import com.example.msp_app.feature.ventacorreccion.domain.VentaLocalParaCorregir
import com.example.msp_app.feature.ventacorreccion.domain.port.VentaLocalCorreccionPort
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ReclamarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ResultadoReclamo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SALE_ID = "sale-tipo-de-venta-001"

/**
 * `ResultadoReclamo.Reclamada.yaEnviada` — el único dato que el formulario de edición
 * (`EditSaleScreen`, en `:app`) usa para decidir si el **tipo de venta** (CONTADO/CRÉDITO) se
 * puede tocar.
 *
 * **Por qué ese campo existe, que es lo que hay que leer antes de cambiar un solo `assert` de
 * aquí:** una venta que el servidor todavía NO tiene sube entera en su `POST` de alta, así que
 * cualquier campo que se corrija en el teléfono llega a la oficina — el tipo de venta incluido.
 * Una venta que YA subió se corrige con TRES peticiones —`PATCH /v2/ventas/{id}` (header),
 * `PATCH /v2/ventas/{id}/cliente` y `PUT /v2/ventas/{id}/lineas`— y **ninguna de las tres lleva
 * el tipo de venta**: hoy no existe endpoint que lo cambie. Dejar el desplegable vivo en ese
 * caso sería guardar el cambio en el teléfono y no entregarlo NUNCA, sin que nada avise: el
 * dueño ve "CONTADO" en su pantalla y la oficina sigue viendo "CRÉDITO".
 *
 * **Si mañana el servidor gana un endpoint que sí cambie el tipo de venta, ésta es la prueba que
 * tiene que caerle encima a quien lo agregue.** La respuesta correcta entonces NO es ajustar el
 * `assert` para que vuelva a pasar: es quitar el bloqueo de sólo-lectura de `EditSaleScreen`,
 * meter el tipo en la petición que ahora sí lo lleve, y reescribir estas pruebas contra el
 * contrato nuevo. Mientras ese endpoint no exista, `yaEnviada` es la diferencia entre un campo
 * honesto y una promesa que nadie cumple.
 *
 * La cobertura de corregibilidad (qué venta abre el editor y cuál no) vive en
 * `CorreccionCasosDeUsoTest` (contra Room de verdad) y en `ConsultarEstadoCorreccionTest`; aquí
 * sólo se mira el campo nuevo.
 */
class ReclamarCorreccionTipoDeVentaTest {

    private val clock = FakeClock.at("2026-09-20T12:00:00Z")
    private lateinit var port: FakeVentaLocalCorreccionPort
    private lateinit var reclamar: ReclamarCorreccion

    private fun campos() = CamposVentaCorregidos(
        nombreCliente = "Rosa Elena Martinez Vazquez",
        fechaVenta = "2026-09-18T15:30:00Z",
        latitud = 19.043415,
        longitud = -98.198234,
        direccion = "Privada de las Rosas 45",
        parcialidad = 850.0,
        enganche = 500.0,
        telefono = "2221234567",
        frecPago = "SEMANAL",
        avalOResponsable = "Juan Martinez Vazquez",
        nota = null,
        diaCobranza = "MARTES",
        precioTotal = 6800.0,
        tiempoACortoPlazoMeses = 8,
        montoACortoPlazo = 6300.0,
        montoDeContado = 5800.0
    )

    @Before
    fun setUp() {
        port = FakeVentaLocalCorreccionPort()
        reclamar = ReclamarCorreccion(port, FakeReloj(clock), FakeReencolar())
    }

    /**
     * `ENVIADO = 1`: el servidor ya tiene la venta en `borrador`, la corrección viajará por las
     * tres peticiones que NO llevan el tipo de venta, y el formulario debe bloquear ese campo.
     * El día que exista el endpoint que falta, esta prueba es el aviso de que se puede reabrir.
     */
    @Test
    fun `venta ya enviada - yaEnviada en true, porque ninguna de las tres peticiones de correccion lleva el tipo de venta`() =
        runTest {
            port.siembra(SALE_ID, campos(), enviado = true)

            val resultado = reclamar(SALE_ID)

            assertTrue(
                "una venta enviada y limpia SI abre el editor",
                resultado is ResultadoReclamo.Reclamada
            )
            assertEquals(
                "sin este true el tipo de venta queda editable y el cambio se pierde en el telefono",
                true,
                (resultado as ResultadoReclamo.Reclamada).yaEnviada
            )
        }

    /**
     * `ENVIADO = 0`: la venta todavía no existe para el servidor, así que sube ENTERA en su
     * `POST` de alta — ahí el tipo de venta sí viaja y no hay nada que bloquear. Es el
     * comportamiento del nivel 1, el que había antes de que existiera `yaEnviada`.
     */
    @Test
    fun `venta sin enviar - yaEnviada en false, porque el alta sube la venta entera y ahi el tipo SI viaja`() =
        runTest {
            port.siembra(SALE_ID, campos(), enviado = false)

            val resultado = reclamar(SALE_ID)

            assertTrue(resultado is ResultadoReclamo.Reclamada)
            assertEquals(
                "una venta que nunca subio no tiene por que perder campos editables",
                false,
                (resultado as ResultadoReclamo.Reclamada).yaEnviada
            )
        }

    /**
     * `yaEnviada` sólo se puede creer porque se lee **con el candado de edición ya puesto**: el
     * subidor no puede marcar `ENVIADO` mientras ese candado siga vigente (`claimForUpload`
     * excluye la fila), así que el valor que se entrega no puede quedar viejo entre la lectura y
     * el momento en que el formulario lo usa. Invertir ese orden —leer `ENVIADO` antes de
     * reclamar— reabriría exactamente la carrera que el candado cierra: el subidor envía la
     * venta justo después de la lectura, el formulario se pinta con el tipo editable, y el
     * cambio se queda en el teléfono.
     */
    @Test
    fun `el ENVIADO que viaja en yaEnviada se lee DESPUES de tomar el candado, no antes`() =
        runTest {
            port.siembra(SALE_ID, campos(), enviado = true)
            val espia = PuertoQueAnotaElOrden(port)
            val reclamarConEspia = ReclamarCorreccion(espia, FakeReloj(clock), FakeReencolar())

            val resultado = reclamarConEspia(SALE_ID)

            assertTrue(resultado is ResultadoReclamo.Reclamada)
            assertEquals(
                "leer ENVIADO sin el candado puesto reabre la carrera con el subidor",
                listOf("reclamarParaEditar", "leerVenta", "leerEstado"),
                espia.orden
            )
        }
}

/**
 * Delega todo en el fake y sólo anota EN QUÉ ORDEN se llamó al puerto — los contadores sueltos
 * de [FakeVentaLocalCorreccionPort] dicen cuántas veces, no cuándo, y aquí lo que importa es
 * justamente el orden.
 */
private class PuertoQueAnotaElOrden(
    private val delegado: FakeVentaLocalCorreccionPort
) : VentaLocalCorreccionPort by delegado {

    val orden = mutableListOf<String>()

    override suspend fun leerEstado(saleId: String): EstadoVentaLocal? {
        orden += "leerEstado"
        return delegado.leerEstado(saleId)
    }

    override suspend fun leerVenta(saleId: String): VentaLocalParaCorregir? {
        orden += "leerVenta"
        return delegado.leerVenta(saleId)
    }

    override suspend fun reclamarParaEditar(saleId: String, claimId: String, ahora: Long): Boolean {
        orden += "reclamarParaEditar"
        return delegado.reclamarParaEditar(saleId, claimId, ahora)
    }
}
