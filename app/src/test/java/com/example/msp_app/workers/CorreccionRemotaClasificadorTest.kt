package com.example.msp_app.workers

import com.example.msp_app.feature.ventacorreccion.domain.CorreccionRemotaTerminal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La tabla de decisión de [clasificarCorreccionRemota], caso por caso: cada
 * renglón de la regla del plan "Corregir una venta DESPUÉS de que subió"
 * tiene su aserción.
 *
 * Es una función pura —código HTTP y código de negocio entran, desenlace
 * sale—, así que no hace falta ningún fake: ni red, ni base, ni WorkManager.
 * Lo que este archivo NO prueba es el worker que la usa; lo que prueba es la
 * única decisión que, si se equivoca, o pierde una corrección en silencio o
 * reintenta para siempre contra una puerta cerrada.
 */
class CorreccionRemotaClasificadorTest {

    // ── 1. 200: entregada ────────────────────────────────────────────────

    @Test
    fun `un 2xx entrega la correccion`() {
        listOf(200, 201, 202, 204, 299).forEach { codigo ->
            assertEquals(
                "HTTP $codigo debería entregar la corrección",
                DesenlaceCorreccionRemota.Entregada,
                clasificarCorreccionRemota(codigo)
            )
        }
    }

    // ── 2. 409 venta_no_editable: terminal ───────────────────────────────

    @Test
    fun `409 venta_no_editable es terminal y nunca se reintenta`() {
        // La venta salió de `borrador`: la oficina la aprobó o ya se aplicó en
        // Microsip. Esta corrección NUNCA va a entrar.
        assertEquals(
            DesenlaceCorreccionRemota.Terminal(CorreccionRemotaTerminal.RECHAZADA_ESTADO),
            clasificarCorreccionRemota(409, "venta_no_editable")
        )
    }

    @Test
    fun `un 409 sin codigo de negocio tambien es terminal`() {
        // El cuerpo de error puede llegar sin `code=` (túnel, proxy, Huma que
        // sólo llena `detail`). El 409 de este endpoint significa una sola
        // cosa, así que la falta del código no lo vuelve reintentable.
        assertEquals(
            DesenlaceCorreccionRemota.Terminal(CorreccionRemotaTerminal.RECHAZADA_ESTADO),
            clasificarCorreccionRemota(409, null)
        )
    }

    // ── 3. 403: terminal ─────────────────────────────────────────────────

    @Test
    fun `403 sin permiso ventas editar es terminal`() {
        // El permiso no aparece por reintentar.
        assertEquals(
            DesenlaceCorreccionRemota.Terminal(CorreccionRemotaTerminal.RECHAZADA_ESTADO),
            clasificarCorreccionRemota(403, "forbidden")
        )
    }

    // ── 4. 404: terminal ─────────────────────────────────────────────────

    @Test
    fun `404 venta_not_found es terminal`() {
        // El endpoint reemplaza líneas de una venta que ya existe; no la crea.
        assertEquals(
            DesenlaceCorreccionRemota.Terminal(CorreccionRemotaTerminal.RECHAZADA_ESTADO),
            clasificarCorreccionRemota(404, "venta_not_found")
        )
    }

    // ── 5. 422: terminal ─────────────────────────────────────────────────

    @Test
    fun `los 422 de validacion son terminales`() {
        listOf(
            "venta_productos_vacios",
            "producto_combo_referencia_invalida",
            "monto_invalido",
            null
        ).forEach { codigo ->
            assertEquals(
                "422 code=$codigo: el cuerpo está mal, repetirlo no lo arregla",
                DesenlaceCorreccionRemota.Terminal(CorreccionRemotaTerminal.RECHAZADA_ESTADO),
                clasificarCorreccionRemota(422, codigo)
            )
        }
    }

    // ── 6. Red y timeout: reintentable ───────────────────────────────────

    @Test
    fun `sin respuesta del servidor se reintenta`() {
        // Red caída, DNS que no resuelve, timeout: nadie tiene la corrección y
        // el teléfono es el único que la conserva.
        assertEquals(
            DesenlaceCorreccionRemota.Reintentar,
            clasificarCorreccionRemota(null, null)
        )
    }

    // ── 7. 5xx: reintentable ─────────────────────────────────────────────

    @Test
    fun `los 5xx se reintentan`() {
        listOf(500, 502, 503, 504).forEach { codigo ->
            assertEquals(
                "HTTP $codigo es del servidor, no del cuerpo: se reintenta",
                DesenlaceCorreccionRemota.Reintentar,
                clasificarCorreccionRemota(codigo)
            )
        }
    }

    // ── Los que NO son de los siete, y por qué están acá ─────────────────

    @Test
    fun `el conflicto de version deja la marca CONFLICTO, no RECHAZADA_ESTADO`() {
        // Gana la oficina, que escribió primero. Es igual de terminal que el
        // 409, pero el dueño merece ver CUÁL de las dos cosas pasó: el
        // vocabulario de `CORRECCION_REMOTA_ESTADO` tiene los dos valores.
        assertEquals(
            DesenlaceCorreccionRemota.Terminal(CorreccionRemotaTerminal.CONFLICTO),
            clasificarCorreccionRemota(412, "venta_version_conflicto")
        )
        assertEquals(
            "el código manda aunque el servidor lo mande con otro estado HTTP",
            DesenlaceCorreccionRemota.Terminal(CorreccionRemotaTerminal.CONFLICTO),
            clasificarCorreccionRemota(409, "venta_version_conflicto")
        )
    }

    @Test
    fun `401 y las senales de backoff se reintentan aunque sean 4xx`() {
        // 401 es el parpadeo del token — el interceptor lo renueva y el
        // siguiente intento pasa. 408/425/429 son backoff explícito. Marcarlos
        // terminales tiraría una corrección buena.
        listOf(401, 408, 425, 429).forEach { codigo ->
            assertEquals(
                "HTTP $codigo no es un rechazo del cuerpo",
                DesenlaceCorreccionRemota.Reintentar,
                clasificarCorreccionRemota(codigo)
            )
        }
    }

    @Test
    fun `un codigo desconocido se reintenta, nunca se declara entregado`() {
        // Ante la duda, conservar: la corrección sólo se suelta cuando el
        // servidor la confirmó o la rechazó en definitiva.
        listOf(0, 100, 302, 600).forEach { codigo ->
            assertEquals(
                "HTTP $codigo",
                DesenlaceCorreccionRemota.Reintentar,
                clasificarCorreccionRemota(codigo)
            )
        }
    }

    @Test
    fun `toda marca terminal usa el vocabulario cerrado de la columna`() {
        // Control del conjunto: si alguien agrega un desenlace terminal con un
        // literal nuevo, la columna `CORRECCION_REMOTA_ESTADO` deja de ser un
        // vocabulario cerrado y `EstadoCorreccion` no puede clasificarlo.
        val terminales = listOf(403, 404, 409, 412, 422, 400, 405)
            .map { clasificarCorreccionRemota(it, null) }
            .filterIsInstance<DesenlaceCorreccionRemota.Terminal>()

        assertEquals(
            "el recorrido no produjo ninguna marca terminal; sin eso esto sale verde sin medir",
            7,
            terminales.size
        )
        terminales.forEach { terminal ->
            assertTrue(
                "estado fuera del vocabulario: ${terminal.estado}",
                terminal.estado in CorreccionRemotaTerminal.CONOCIDOS
            )
        }
    }

    // ── El tercer valor del vocabulario, y por qué no nace acá ───────────

    @Test
    fun `la clasificacion por si sola NUNCA produce APLICADA_PARCIAL`() {
        // El tercer valor de la columna no lo decide esta función y no puede:
        // depende de cuántas de las TRES peticiones de la corrección alcanzaron
        // a entrar, y eso sólo lo sabe `ejecutarSecuenciaCorreccionRemota`. Si
        // algún día un código HTTP empezara a devolverlo desde acá, la marca
        // dejaría de significar "el servidor quedó a medias" y pasaría a
        // significar "llegó cierto código" — que es justo lo que no se le puede
        // contar al cobrador.
        val terminales = (100..599)
            .map { clasificarCorreccionRemota(it, null) }
            .filterIsInstance<DesenlaceCorreccionRemota.Terminal>()

        assertTrue(
            "el recorrido no produjo ninguna marca terminal; sin eso esto sale verde sin medir",
            terminales.isNotEmpty()
        )
        terminales.forEach { terminal ->
            assertTrue(
                "la clasificación de UNA petición devolvió ${terminal.estado}",
                terminal.estado != CorreccionRemotaTerminal.APLICADA_PARCIAL
            )
        }
    }

    @Test
    fun `el vocabulario de la columna tiene los tres valores que alguien escribe, y solo esos`() {
        // Control del conjunto al revés que el de arriba: aquél prueba que
        // nadie escriba un literal FUERA del vocabulario; éste, que el
        // vocabulario no crezca con un valor que ningún camino escribe. Los
        // caminos son dos: esta clasificación (los dos primeros valores) y la
        // secuencia de tres peticiones (el tercero).
        assertEquals(
            setOf(
                CorreccionRemotaTerminal.RECHAZADA_ESTADO,
                CorreccionRemotaTerminal.CONFLICTO,
                CorreccionRemotaTerminal.APLICADA_PARCIAL
            ),
            CorreccionRemotaTerminal.CONOCIDOS
        )
    }
}
