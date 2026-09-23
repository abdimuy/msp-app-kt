package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.ui.components.FilaClaveValor
import com.example.msp_app.feature.pagos.ui.components.SIN_DATO

/**
 * La ficha "datos de la venta", con **los once renglones** que la pantalla
 * legada enseñaba (`SaleClientDetailsSection`).
 *
 * Seis de ellos se habían perdido al portar la pantalla —teléfono, dirección,
 * zona, precio a corto plazo, aval y los vendedores 2 y 3—, y no por falta de
 * espacio: no llegaban desde la capa de datos. Ahora sí llegan, todos de la
 * misma fila de `sales`.
 *
 * ## El orden, que aquí sí es una decisión
 *
 * El legado los repartía en dos columnas, así que no había un orden: había una
 * retícula. Aquí es una lista, y se ordena **de lo más consultado a lo menos**,
 * en tres bloques:
 *
 * 1. **La puerta** — teléfono, dirección, zona. Es lo que el cobrador busca con
 *    el teléfono en la mano y sin haber llegado todavía.
 * 2. **El trato** — fecha y las cifras con que se cerró la venta, de la más
 *    grande a la más chica, con el precio a corto plazo junto a los otros dos
 *    precios y no al final, que es donde el legado lo tenía por accidente de la
 *    retícula.
 * 3. **Las personas** — aval y vendedores. Se consultan cuando algo salió mal,
 *    que es lo menos frecuente, así que van al fondo.
 *
 * Los campos ausentes no abren renglón en blanco: [FilaClaveValor] pinta
 * [SIN_DATO] cuando el valor viene vacío, y la fila del precio a corto plazo no
 * se pinta en absoluto cuando no aplica ([FilaDelPrecioCorto]).
 */
@Composable
internal fun DatosDeLaVenta(detalle: DetalleVenta) {
    FilaClaveValor("Teléfono", detalle.telefono)
    FilaClaveValor("Dirección", detalle.direccion)
    FilaClaveValor("Zona", detalle.zona)
    FilaClaveValor(
        "Fecha de venta",
        detalle.fechaVenta?.let { FECHA_DE_VENTA.format(it) } ?: SIN_DATO
    )
    FilaClaveValor("Total venta", formatMoneyMxn(detalle.totalVenta.amount))
    FilaClaveValor("Precio de contado", formatMoneyMxn(detalle.precioContado.amount))
    FilaDelPrecioCorto(detalle)
    FilaClaveValor("Enganche", formatMoneyMxn(detalle.enganche.amount))
    FilaClaveValor("Abonado", formatMoneyMxn(detalle.abonado.amount))
    FilaClaveValor("Aval o responsable", detalle.aval)
    FilaDeVendedores(detalle.vendedores)
}

/**
 * "Precio a 8 meses" — el precio a corto plazo, con su plazo DENTRO de la
 * etiqueta, igual que en la pantalla legada.
 *
 * **No se pinta cuando no aplica**, y eso es la mitad de por qué esto es un
 * composable aparte: buena parte de la cartera no tiene oferta a corto plazo y
 * llega con las dos columnas en cero. Un renglón que dijera "$0" a "0 meses" no
 * es un dato — es la ausencia del dato disfrazada de cifra, y en una pantalla
 * de dinero eso se lee como un defecto de la app.
 *
 * El plazo se escribe en singular o plural según el número, en vez del
 * "mes(es)" del legado: la etiqueta la lee una persona, no un formulario.
 */
@Composable
private fun FilaDelPrecioCorto(detalle: DetalleVenta) {
    if (detalle.mesesACortoPlazo <= 0 || detalle.montoACortoPlazo <= Money.ZERO) return
    val meses = detalle.mesesACortoPlazo
    val plazo = if (meses == 1) "1 mes" else "$meses meses"
    FilaClaveValor("Precio a $plazo", formatMoneyMxn(detalle.montoACortoPlazo.amount))
}

/**
 * Quiénes vendieron, **uno por renglón** — como el legado, que los unía con
 * saltos de línea.
 *
 * A diferencia del legado, los vacíos no llegan hasta acá: el adaptador los
 * recorta (ver
 * [com.example.msp_app.feature.pagos.domain.model.DatosDeVenta.vendedores]).
 * Eso es lo que arregla el caso real de campo —el segundo y el tercero casi
 * siempre vienen en blanco—, donde `"$VENDEDOR_1\n\n"` dibujaba dos renglones
 * vacíos debajo del único nombre que había.
 *
 * Sin ninguno, la fila sigue existiendo con [SIN_DATO]: que no se sepa quién
 * vendió es información, y callar la fila la confundiría con "esta venta no
 * tiene vendedor".
 */
@Composable
private fun FilaDeVendedores(vendedores: List<String>) {
    FilaClaveValor(
        clave = if (vendedores.size > 1) "Vendedores" else "Vendedor",
        valor = vendedores.joinToString("\n")
    )
}
