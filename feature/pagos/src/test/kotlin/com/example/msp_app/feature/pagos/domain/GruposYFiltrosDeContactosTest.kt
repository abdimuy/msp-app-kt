package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cómo se parte la bitácora en tramos y qué deja ver cada filtro.
 *
 * Las dos piezas son puras y reciben el `hoy` por parámetro, así que aquí se
 * puede fijar el día y el resultado no cambia mañana — que es justamente por
 * lo que no preguntan la hora por su cuenta.
 */
class GruposYFiltrosDeContactosTest {

    // --- Agrupar por mes -----------------------------------------------------

    @Test
    fun `por mes parte en meses de calendario, del mas reciente al mas viejo`() {
        val grupos = GruposDeContactos.porMes(TODOS)

        assertEquals(
            listOf("SEPTIEMBRE 2026", "AGOSTO 2026"),
            grupos.map { it.titulo }
        )
        assertEquals(3, grupos[0].contactos.size)
        assertEquals(2, grupos[1].contactos.size)
    }

    /**
     * El subtotal contesta *"¿cuánto entró ese mes?"*, así que suma **sólo los
     * cobros**. Una promesa trae monto prometido y sumarlo diría que ese dinero
     * entró, que es la clase de cifra falsa que esta app no puede enseñar.
     */
    @Test
    fun `el subtotal suma los cobros y nada mas`() {
        val (septiembre, agosto) = GruposDeContactos.porMes(TODOS)

        // Septiembre trae UN cobro de 300 y dos visitas, una de ellas con
        // promesa. El subtotal es 300: el monto prometido no entró.
        assertEquals(dinero("300"), septiembre.cobrado)
        assertEquals(dinero("150"), agosto.cobrado)
    }

    /**
     * `null` y no cero: "no cobré nada" y "sólo hubo visitas" se ven igual con
     * un cero, y un cero en una pantalla de dinero se lee como una medición.
     */
    @Test
    fun `un mes sin un solo cobro no reporta cero, reporta nada`() {
        val soloVisitas = TODOS.filter { it.tipo == TipoDeContacto.VISITA }

        GruposDeContactos.porMes(soloVisitas).forEach {
            assertNull("un tramo sin cobros dijo ${it.cobrado}", it.cobrado)
        }
    }

    /**
     * Control positivo del de arriba: con el mismo método, un tramo que SÍ
     * tiene cobros reporta su suma. Sin esto, un `sumaDe` que devolviera
     * siempre `null` pasaría el test anterior sin agrupar nada.
     */
    @Test
    fun `control positivo - con cobros adentro si reporta la suma`() {
        assertNotNull(GruposDeContactos.porMes(TODOS).first().cobrado)
    }

    // --- Agrupar por cercanía ------------------------------------------------

    @Test
    fun `por cercania parte en hoy, esta semana, este mes y antes`() {
        val grupos = GruposDeContactos.porCercania(TODOS, HOY)

        assertEquals(
            listOf("Hoy", "Esta semana", "Este mes", "Antes"),
            grupos.map { it.titulo }
        )
    }

    /**
     * Un encabezado sin filas debajo es un hueco que se lee como un error de
     * carga, no como "no hubo nada esa semana".
     */
    @Test
    fun `los tramos vacios no se emiten`() {
        val soloDeHoy = listOf(cobro("2026-09-18T16:42:00Z", "300"))

        val grupos = GruposDeContactos.porCercania(soloDeHoy, HOY)

        assertEquals(listOf("Hoy"), grupos.map { it.titulo })
        grupos.forEach { assertTrue("${it.titulo} salió vacío", it.contactos.isNotEmpty()) }
    }

    /**
     * La semana se cuenta **hacia atrás desde hoy**, no desde el lunes: el
     * cobrador pregunta "¿fui esta semana?" queriendo decir "en estos días".
     * El 12 de septiembre está a seis días del 18 y entra; el 11 está a siete
     * y ya no.
     */
    @Test
    fun `la semana son siete dias hacia atras, no el lunes del calendario`() {
        val seisDias = visita("2026-09-12T16:00:00Z")
        val sieteDias = visita("2026-09-11T16:00:00Z")

        assertEquals(
            "Esta semana",
            GruposDeContactos.porCercania(listOf(seisDias), HOY).single().titulo
        )
        assertEquals(
            "Este mes",
            GruposDeContactos.porCercania(listOf(sieteDias), HOY).single().titulo
        )
    }

    /**
     * **Por cercanía NUNCA reporta subtotal, ni cuando hay cobros adentro.**
     *
     * Esta regla se invirtió a propósito: antes `porCercania` sumaba igual que
     * [GruposDeContactos.porMes], y eso era el defecto. Su único llamador es el
     * detalle de cliente, que le pasa **tres** contactos de veintisiete, así que
     * la suma era de una MUESTRA puesta en el renglón donde se lee *"esto entró
     * en el tramo"*: la pantalla pintaba *"Antes ——— $350"* con *"Ver los 27
     * contactos"* debajo. Una cifra parcial presentada como total, en una
     * pantalla de dinero.
     *
     * No se afloja el assert al primer tramo: se recorren **todos**, incluido el
     * que trae los dos cobros.
     */
    @Test
    fun `por cercania no reporta subtotal, porque agrupa una muestra`() {
        GruposDeContactos.porCercania(TODOS, HOY).forEach {
            assertNull(
                "el tramo “${it.titulo}” reportó ${it.cobrado}: por cercanía agrupa lo que " +
                    "cabe en el detalle, no la historia completa, así que cualquier suma " +
                    "suya es parcial presentada como total",
                it.cobrado
            )
        }
    }

    /**
     * **Control positivo del de arriba, y el que impide que el arreglo se coma
     * de más.** Los MISMOS contactos agrupados por mes sí reportan su suma: lo
     * que se quitó es el subtotal de la muestra, no la capacidad de sumar. Sin
     * esto, un `sumaDe` roto —o un `cobrado` que nadie llenara nunca— dejaría el
     * test anterior en verde.
     */
    @Test
    fun `control positivo - los mismos contactos por mes si reportan subtotal`() {
        assertTrue(
            "ningún tramo por mes reportó subtotal: entonces el test de arriba no prueba " +
                "que `porCercania` sea la que calla, sino que nadie suma",
            GruposDeContactos.porMes(TODOS).any { it.cobrado != null }
        )
    }

    // --- Filtros -------------------------------------------------------------

    @Test
    fun `cada filtro deja pasar lo suyo`() {
        val cuantos = { f: FiltroDeContactos -> TODOS.count(f::deja) }

        assertEquals(
            "todos tiene que dejar pasar todo",
            TODOS.size,
            cuantos(FiltroDeContactos.TODOS)
        )
        assertEquals(2, cuantos(FiltroDeContactos.COBROS))
        assertEquals(3, cuantos(FiltroDeContactos.VISITAS))
        assertEquals(2, cuantos(FiltroDeContactos.PROMESAS))
    }

    /**
     * La promesa es un SUBCONJUNTO de las visitas, no un tipo aparte: lo que
     * "Promesas" deja pasar tiene que seguir siendo visita. Si algún día un
     * cobro entrara a ese filtro, esto se pone rojo.
     */
    @Test
    fun `lo que promesas deja pasar sigue siendo visita`() {
        TODOS.filter(FiltroDeContactos.PROMESAS::deja).forEach {
            assertEquals(TipoDeContacto.VISITA, it.tipo)
        }
    }

    /**
     * Control positivo de los filtros: ninguno puede quedarse con la lista
     * entera ni vaciarla. Sin esto, un `deja` que devolviera siempre `true`
     * —o siempre `false`— pasaría los conteos de arriba si alguien los
     * actualizara sin mirar.
     */
    @Test
    fun `ningun filtro se queda con todo ni deja la lista vacia`() {
        FiltroDeContactos.entries
            .filterNot { it == FiltroDeContactos.TODOS }
            .forEach { filtro ->
                val quedan = TODOS.count(filtro::deja)
                assertTrue("$filtro no dejó pasar nada", quedan > 0)
                assertTrue("$filtro dejó pasar TODO, no filtra nada", quedan < TODOS.size)
            }
    }

    // --- Conteos del control segmentado (Task 4) ------------------------------

    /**
     * **El conteo es la MISMA regla que filtra, para las cuatro opciones — y
     * una en cero.**
     *
     * Si el conteo usara otra consulta, "Promesas 2" podría enseñar una fila
     * distinta de las dos que [FiltroDeContactos.deja] deja ver. Por eso la
     * comparación es genérica sobre los cuatro `entries` y no sólo sobre el
     * caso feliz: un `conteos` que hiciera trampa en un solo filtro —el típico
     * "cuento por tipo en vez de llamar a `deja`"— se pondría rojo aquí.
     *
     * El cero lo da quitar las promesas de [TODOS] a mano: sin nada que las
     * cumpla, `PROMESAS` tiene que marcar 0 sin desaparecer del mapa ni
     * lanzar.
     */
    @Test
    fun `el conteo de cada opcion es el numero de filas que su propio filtro deja ver`() {
        val sinPromesas = TODOS.filterNot(FiltroDeContactos.PROMESAS::deja)

        val conteos = FiltroDeContactos.conteos(sinPromesas)

        FiltroDeContactos.entries.forEach { filtro ->
            assertEquals(
                "el conteo de $filtro no coincide con las filas que su propio deja ve",
                sinPromesas.count(filtro::deja),
                conteos[filtro]
            )
        }
        assertEquals(0, conteos[FiltroDeContactos.PROMESAS])
    }

    // -----------------------------------------------------------------------

    private companion object {
        val HOY: LocalDate = LocalDate.of(2026, 9, 18)

        fun dinero(cuanto: String) = Money.of(BigDecimal(cuanto))

        fun cobro(cuando: String, cuanto: String) = ContactoDeCobranza(
            fecha = Instant.parse(cuando),
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = dinero(cuanto),
            tipo = TipoDeContacto.COBRO
        )

        fun visita(cuando: String, estado: EstadoCuenta = EstadoCuenta.NO_ESTABA) =
            ContactoDeCobranza(
                fecha = Instant.parse(cuando),
                etiqueta = "No estaba",
                nota = null,
                estado = estado,
                importe = null,
                tipo = TipoDeContacto.VISITA
            )

        /** Tres de septiembre y dos de agosto; dos cobros y tres visitas. */
        val TODOS = listOf(
            cobro("2026-09-18T16:42:00Z", "300"),
            visita("2026-09-18T00:05:00Z", EstadoCuenta.PROMETIO_PROXIMA),
            visita("2026-09-05T15:00:00Z", EstadoCuenta.CITA_A_UNA_HORA),
            cobro("2026-08-20T15:20:00Z", "150"),
            visita("2026-08-03T14:15:00Z")
        )
    }
}
