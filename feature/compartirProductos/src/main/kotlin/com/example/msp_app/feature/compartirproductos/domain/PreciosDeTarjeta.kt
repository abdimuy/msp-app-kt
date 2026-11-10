package com.example.msp_app.feature.compartirproductos.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Elige y ordena las filas de precios de la tarjeta.
 *
 * **E-APP-180**: este módulo NO clasifica ni ordena precios; la app le pasa la lista ya clasificada
 * (`preciosClasificadosParaMostrar`, la misma que pinta el catálogo).
 *
 * Reglas (E-APP-059/060, ya vigentes en la app; aquí solo se aplican):
 *  - el orden es siempre contado → corto plazo → crédito, sin importar el orden de la lista;
 *  - de cada tipo se toma el PRIMERO de la lista, igual que `ClasificadorDePrecios.precioDe`;
 *  - un monto en cero o negativo no se muestra (no se captura, no es «gratis»);
 *  - solo salen las filas que el vendedor marcó.
 */
object PreciosDeTarjeta {

    fun armar(
        precios: List<PrecioDeCatalogo>,
        seleccion: SeleccionDePrecios
    ): List<PrecioDeTarjeta> = FilaDePrecio.entries.mapNotNull { fila ->
        if (!seleccion.incluye(fila)) return@mapNotNull null
        val monto = precios.firstOrNull { it.tipo == fila.tipo }?.monto ?: return@mapNotNull null
        if (monto.signum() <= 0) {
            null
        } else {
            PrecioDeTarjeta(
                fila,
                monto,
                FormatoDePesos.formatear(monto)
            )
        }
    }
}

/** Pesos sin centavos, igual que `Double.toCurrency(noDecimals = true)` de la app: `$8,990`. */
object FormatoDePesos {
    private val ES_MX = Locale("es", "MX")

    fun formatear(monto: BigDecimal): String {
        val formato = NumberFormat.getCurrencyInstance(ES_MX).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        return formato.format(monto)
    }
}
