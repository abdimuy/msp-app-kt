package com.example.msp_app.features.sales.precios

import com.example.msp_app.core.common.ventas.precios.LineaDePrecios
import com.example.msp_app.core.common.ventas.precios.ReglaOrdenDePrecios
import com.example.msp_app.core.common.ventas.precios.TipoDeLinea
import com.example.msp_app.core.common.ventas.precios.VeredictoOrdenDePrecios
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.data.models.sale.localsale.toMoneyString
import com.example.msp_app.features.sales.viewmodels.SaleItem
import com.example.msp_app.utils.ParsedPrices
import com.example.msp_app.utils.PriceParser
import java.math.BigDecimal

/**
 * Único lugar donde las líneas de una venta (alta y corrección) se convierten
 * en lo que se guarda y se ENVÍA al servidor.
 *
 * E-APP-061: en CONTADO cada línea lleva sólo el precio de contado; corto y
 * anual van en 0 (productos igual que combos). Antes los productos salían con
 * los tres precios del catálogo aunque la venta fuera de contado, y un combo
 * de contado (0/0/C) junto a un producto con tres precios armaba totales
 * imposibles que el servidor rechazaba con `monto_tier_order_invalid`.
 * El catálogo (`SaleItem.product.PRECIOS`) no se toca: al volver a crédito
 * se envían otra vez sus tres precios.
 */
object LineasDeVenta {

    private const val CONTADO = "CONTADO"

    /** Combos viajan con cantidad "1" (`LocalSaleMappers.toV2VentaBody`). */
    private val CANTIDAD_COMBO = BigDecimal.ONE

    fun esContado(tipoVenta: String?): Boolean = tipoVenta == CONTADO

    fun preciosSegunTipoVenta(precios: ParsedPrices, tipoVenta: String?): ParsedPrices =
        if (esContado(tipoVenta)) {
            ParsedPrices(
                precioLista = 0.0,
                precioCortoplazo = 0.0,
                precioContado = precios.precioContado
            )
        } else {
            precios
        }

    /** El precio que se muestra en la fila de un artículo: contado en CONTADO, lista en crédito. */
    fun precioDeReferencia(precios: ParsedPrices, tipoVenta: String?): Double =
        if (esContado(tipoVenta)) precios.precioContado else precios.precioLista

    fun productos(
        saleId: String,
        items: List<SaleItem>,
        tipoVenta: String?
    ): List<LocalSaleProductEntity> = items.map { item ->
        val precios = preciosSegunTipoVenta(
            PriceParser.parsePricesFromString(item.product.PRECIOS),
            tipoVenta
        )
        LocalSaleProductEntity(
            LOCAL_SALE_ID = saleId,
            ARTICULO_ID = item.product.ARTICULO_ID,
            ARTICULO = item.product.ARTICULO,
            CANTIDAD = item.quantity,
            PRECIO_LISTA = precios.precioLista,
            PRECIO_CORTO_PLAZO = precios.precioCortoplazo,
            PRECIO_CONTADO = precios.precioContado,
            COMBO_ID = item.comboId
        )
    }

    fun combos(combos: List<LocalSaleComboEntity>, tipoVenta: String?): List<LocalSaleComboEntity> =
        if (esContado(tipoVenta)) {
            combos.map { it.copy(PRECIO_LISTA = 0.0, PRECIO_CORTO_PLAZO = 0.0) }
        } else {
            combos
        }

    /**
     * E-APP-062: la misma regla que el servidor, sobre los mismos números que
     * se envían (redondeados a centavos igual que el cuerpo de la petición).
     */
    fun validar(
        productos: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity>
    ): VeredictoOrdenDePrecios {
        val lineas = combos.map { c ->
            LineaDePrecios(
                tipo = TipoDeLinea.COMBO,
                nombre = c.NOMBRE_COMBO,
                cantidad = CANTIDAD_COMBO,
                contado = c.PRECIO_CONTADO.dinero(),
                corto = c.PRECIO_CORTO_PLAZO.dinero(),
                anual = c.PRECIO_LISTA.dinero()
            )
        } + productos.map { p ->
            LineaDePrecios(
                tipo = TipoDeLinea.PRODUCTO,
                nombre = p.ARTICULO,
                cantidad = BigDecimal(p.CANTIDAD),
                contado = p.PRECIO_CONTADO.dinero(),
                corto = p.PRECIO_CORTO_PLAZO.dinero(),
                anual = p.PRECIO_LISTA.dinero(),
                dentroDeCombo = p.COMBO_ID != null
            )
        }
        return ReglaOrdenDePrecios.validar(lineas)
    }

    /** El mensaje del servidor, con mayúscula inicial y la línea que hay que corregir. */
    fun mensajeParaVendedor(invalido: VeredictoOrdenDePrecios.Invalido): String {
        val mensaje = invalido.mensaje.replaceFirstChar { it.uppercase() }
        val linea = invalido.linea ?: return mensaje
        return "$mensaje\n${linea.nombre}: contado ${linea.contado.toPlainString()}, " +
            "corto plazo ${linea.corto.toPlainString()}, anual ${linea.anual.toPlainString()}"
    }

    private fun Double.dinero(): BigDecimal = BigDecimal(toMoneyString())
}
