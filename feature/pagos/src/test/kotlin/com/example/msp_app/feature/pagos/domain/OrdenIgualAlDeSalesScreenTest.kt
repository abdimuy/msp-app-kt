package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * **La prueba de que el orden portado es el que ya corre en la calle.**
 *
 * Abajo está la expresión de `SalesScreen.kt:201-203`, copiada literal sobre
 * los `Double` crudos que la pantalla vieja recibe de Room:
 *
 * ```kotlin
 * compareByDescending<SaleWithProducts> { it.SALDO_REST == it.PRECIO_TOTAL - it.ENGANCHE }
 *     .thenBy { it.FECHA }
 * ```
 *
 * Cada caso corre las dos ordenaciones sobre **los mismos datos** —la legada
 * sobre `Double`, la nueva sobre [Money] e `Instant`— y compara la secuencia
 * de folios resultante. Sin este test, "es el mismo orden" sería una
 * afirmación; con él es una medición.
 *
 * ## Las diferencias, cada una con su caso
 *
 * 1. **Aritmética decimal en vez de binaria.** La REGLA DE DINERO prohíbe que
 *    un `Double` cruce el adaptador, así que el predicado se evalúa sobre
 *    `Money` (BigDecimal escala 2). Sobre pesos enteros —lo que trae la base—
 *    las dos dan lo mismo, y eso es lo que prueban los casos principales. Con
 *    centavos pueden discrepar, y el caso `centavos` documenta esa discrepancia
 *    en la dirección correcta: la resta binaria dice "ya abonó" de una venta
 *    que no ha recibido un peso.
 * 2. ~~La segunda clave es la fecha de negocio~~ — **corregida tras la
 *    revisión.** Empataba dos ventas del mismo día mientras la pantalla vieja
 *    las ordena por hora, y esa divergencia no la forzaba nada: el instante
 *    crudo ya venía parseado en el adaptador. Hoy `RangoDeCobranza` desempata
 *    por `Instant` y la paridad es exacta, incluido el mismo día a distinta
 *    hora. La que queda es la de arriba, y esa sí la fuerza la REGLA DE DINERO.
 */
@Suppress(
    // El ÚNICO lugar del módulo donde un `Double` de dinero es correcto: este
    // test EXISTE para correr la expresión legada tal como está escrita hoy en
    // `SalesScreen`, sobre los `Double` crudos que la pantalla vieja recibe de
    // Room. Convertirlos a `Money` aquí borraría justo lo que se está midiendo
    // —si las dos aritméticas coinciden— y volvería el test una tautología.
    // La REGLA DE DINERO sigue vigente en todo el código de producción.
    "NoDoubleForMoney"
)
class OrdenIgualAlDeSalesScreenTest {

    /**
     * Las cuatro columnas que la expresión legada mira, con los tipos que tienen
     * en `SaleWithProducts`: tres `Double` y la fecha como texto de cable.
     * Los nombres van en minúsculas por `ktlint_standard_property-naming`; el
     * mapeo es `saldoRest = SALDO_REST`, `precioTotal = PRECIO_TOTAL`,
     * `enganche = ENGANCHE`, `fecha = FECHA`.
     */
    private data class FilaLegada(
        val folio: String,
        val saldoRest: Double,
        val precioTotal: Double,
        val enganche: Double,
        val fecha: String
    )

    /** La expresión de `SalesScreen.kt:201-203`, verbatim. */
    private val ordenLegado: Comparator<FilaLegada> =
        compareByDescending<FilaLegada> { it.saldoRest == it.precioTotal - it.enganche }
            .thenBy { it.fecha }

    private fun legado(filas: List<FilaLegada>): List<String> =
        filas.sortedWith(ordenLegado).map { it.folio }

    private fun portado(filas: List<FilaLegada>): List<String> = filas
        .map { it to rangoDe(it) }
        .sortedWith(compareBy(OrdenDeCobranza.PRIMERO) { (_, rango) -> rango })
        .map { (fila, _) -> fila.folio }

    private fun rangoDe(fila: FilaLegada): RangoDeCobranza = OrdenDeCobranza.rangoDe(
        // El MISMO puente que usa `RoomVentasAdapter` en el borde.
        saldo = Money.of(fila.saldoRest),
        totalVenta = Money.of(fila.precioTotal),
        enganche = Money.of(fila.enganche),
        instanteDeVenta = AppTime.parseWireFormatOrNull(fila.fecha)
    )

    private fun fila(folio: String, saldo: Double, fecha: String) = FilaLegada(
        folio = folio,
        saldoRest = saldo,
        precioTotal = 8400.0,
        enganche = 900.0,
        fecha = fecha
    )

    /** 8400 - 900 = 7500: el saldo que significa "no ha abonado nada". */
    private val loFinanciado = 7500.0

    private val ruta = listOf(
        fila("V-5021", saldo = 2100.0, fecha = "2026-05-04T06:00:00Z"),
        fila("V-6400", saldo = loFinanciado, fecha = "2026-03-12T06:00:00Z"),
        fila("V-7115", saldo = 1800.0, fecha = "2026-01-20T06:00:00Z"),
        fila("V-5188", saldo = loFinanciado, fecha = "2026-08-10T06:00:00Z")
    )

    @Test
    fun `la ruta completa sale en el mismo orden que la pantalla vieja`() {
        assertEquals(legado(ruta), portado(ruta))
    }

    @Test
    fun `el orden esperado, escrito a mano, es el de los dos`() {
        // Primero los dos sin abonos, entre ellos el más viejo; luego los otros
        // dos, otra vez el más viejo primero.
        val esperado = listOf("V-6400", "V-5188", "V-7115", "V-5021")
        assertEquals(esperado, legado(ruta))
        assertEquals(esperado, portado(ruta))
    }

    @Test
    fun `el borde exacto y el peso de abajo coinciden en los dos ordenes`() {
        val enElBorde = listOf(
            fila("exacto", saldo = loFinanciado, fecha = "2026-05-04T06:00:00Z"),
            fila("un-peso-abajo", saldo = loFinanciado - 1, fecha = "2026-01-04T06:00:00Z")
        )
        assertEquals(listOf("exacto", "un-peso-abajo"), legado(enElBorde))
        assertEquals(legado(enElBorde), portado(enElBorde))
    }

    /**
     * **Paridad al minuto, no solo al día.**
     *
     * La primera versión de esta tarea ordenaba por la FECHA de negocio, así que
     * dos ventas del mismo día empataban mientras la pantalla vieja las separa
     * por hora. Era una divergencia que nada forzaba —el instante crudo ya venía
     * parseado en el adaptador— justo en lo único que había orden de copiar sin
     * cambios. Hoy la clave de desempate es el `Instant` y las dos secuencias
     * coinciden.
     */
    @Test
    fun `mismo dia a distinta hora, el mismo orden que la pantalla vieja`() {
        val mismoDia = listOf(
            fila("tarde", saldo = 2100.0, fecha = "2026-05-04T22:00:00Z"),
            fila("manana", saldo = 2100.0, fecha = "2026-05-04T14:00:00Z")
        )
        assertEquals(listOf("manana", "tarde"), legado(mismoDia))
        assertEquals(legado(mismoDia), portado(mismoDia))
    }

    /** Y el mismo minuto: dos ventas a la misma hora empatan en las dos. */
    @Test
    fun `mismo instante, las dos conservan el orden de la fuente`() {
        val aLaVez = listOf(
            fila("primera", saldo = 2100.0, fecha = "2026-05-04T22:00:00Z"),
            fila("segunda", saldo = 2100.0, fecha = "2026-05-04T22:00:00Z")
        )
        assertEquals(listOf("primera", "segunda"), legado(aLaVez))
        assertEquals(legado(aLaVez), portado(aLaVez))
    }

    @Test
    fun `con centavos la resta binaria se equivoca y la decimal no`() {
        // 6400.40 - 900.10 da 5500.299999999999 en binario, no 5500.30.
        val conCentavos = FilaLegada(
            folio = "centavos",
            saldoRest = 5500.30,
            precioTotal = 6400.40,
            enganche = 900.10,
            fecha = "2026-05-04T06:00:00Z"
        )
        val sinAbonosSegunElLegado =
            conCentavos.saldoRest == conCentavos.precioTotal - conCentavos.enganche
        assertNotEquals(
            "si esto deja de discrepar, la nota del KDoc sobra",
            sinAbonosSegunElLegado,
            rangoDe(conCentavos).sinAbonos
        )
        // La venta no ha recibido un peso; la decimal es la que lo dice.
        assertEquals(true, rangoDe(conCentavos).sinAbonos)
    }
}
