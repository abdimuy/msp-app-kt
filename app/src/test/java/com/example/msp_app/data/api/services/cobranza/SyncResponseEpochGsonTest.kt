package com.example.msp_app.data.api.services.cobranza

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de compatibilidad del campo `sync_epoch` frente a Gson.
 *
 * Gson NO respeta los valores por defecto de Kotlin: instancia por `Unsafe`
 * sin llamar al constructor, así que un campo ausente en el JSON queda en el
 * default de la JVM — null para tipos referencia, 0 para primitivos — sin
 * importar lo que diga el `= ...` del constructor ni que el tipo sea no nulo.
 * Ese es exactamente el mecanismo del incidente de producción con un campo
 * no nulo que llegaba null y reventaba con NPE lejos del punto de parseo.
 *
 * Por eso `sync_epoch` es `Int?`: un servidor viejo que no manda el campo
 * produce `null` (mecanismo de generaciones apagado, la app sigue igual que
 * antes) y jamás un 0 indistinguible de un valor real. Estas pruebas fijan ese
 * comportamiento contra el Gson real, no contra el constructor de Kotlin.
 */
class SyncResponseEpochGsonTest {

    private val gson = Gson()

    private val ventasSinEpoch = """
        {
          "items": [],
          "max_updated_at": "2026-08-10T18:25:13.456789Z",
          "server_now": "2026-08-10T18:25:20Z",
          "has_more": false
        }
    """.trimIndent()

    private val pagosSinEpoch = """
        {
          "items": [],
          "max_updated_at": "2026-08-10T18:25:13.456789Z",
          "server_now": "2026-08-10T18:25:20Z",
          "has_more": false
        }
    """.trimIndent()

    @Test
    fun `servidor viejo sin sync_epoch deserializa a null sin reventar - ventas`() {
        val response = gson.fromJson(ventasSinEpoch, SyncVentasResponse::class.java)

        assertNull("campo ausente debe quedar null, no 0", response.sync_epoch)
        assertEquals("2026-08-10T18:25:13.456789Z", response.max_updated_at)
        assertTrue(response.items.isEmpty())
        assertEquals(false, response.has_more)
    }

    @Test
    fun `servidor viejo sin sync_epoch deserializa a null sin reventar - pagos`() {
        val response = gson.fromJson(pagosSinEpoch, SyncPagosResponse::class.java)

        assertNull("campo ausente debe quedar null, no 0", response.sync_epoch)
        assertEquals("2026-08-10T18:25:13.456789Z", response.max_updated_at)
        assertTrue(response.items.isEmpty())
    }

    @Test
    fun `servidor nuevo con sync_epoch lo entrega tal cual`() {
        val json = """
            {
              "items": [],
              "max_updated_at": "2026-08-10T18:25:13.456789Z",
              "server_now": "2026-08-10T18:25:20Z",
              "has_more": false,
              "sync_epoch": 42
            }
        """.trimIndent()

        assertEquals(42, gson.fromJson(json, SyncVentasResponse::class.java).sync_epoch)
        assertEquals(42, gson.fromJson(json, SyncPagosResponse::class.java).sync_epoch)
    }

    /**
     * `sync_epoch: null` explícito (servidor que declara el campo pero aún no
     * asigna generación al recurso) es indistinguible de ausente: mecanismo
     * apagado, nunca un crash.
     */
    @Test
    fun `sync_epoch nulo explicito se trata como ausente`() {
        val json = """
            {
              "items": [],
              "max_updated_at": "2026-08-10T18:25:13.456789Z",
              "server_now": "2026-08-10T18:25:20Z",
              "has_more": false,
              "sync_epoch": null
            }
        """.trimIndent()

        assertNull(gson.fromJson(json, SyncPagosResponse::class.java).sync_epoch)
    }
}
