package com.example.msp_app.core.utils

import com.example.msp_app.core.common.ventas.precios.ClasificadorDePrecios
import com.example.msp_app.core.common.ventas.precios.TipoDePrecio
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

fun parsePrice(pricesStr: String?): Map<String, Double> {
    val priceMap = mutableMapOf<String, Double>()

    if (pricesStr.isNullOrBlank()) return priceMap

    val cleaned = pricesStr.trim().removePrefix("{").removeSuffix("}")
    val parts = cleaned.split(",")

    for (part in parts) {
        val keyValue = part.split(":")
        if (keyValue.size != 2) continue

        val rawKey = keyValue[0].trim()
        val rawValue = keyValue[1].trim()

        val key = rawKey.removeSurrounding("\"").removeSurrounding("'")
        val cleanedValue = rawValue.replace(",", ".").replace(Regex("[^0-9.]"), "")

        val number = cleanedValue.toDoubleOrNull()

        if (key.isNotBlank() && number != null) {
            priceMap[key] = number
        }
    }

    return priceMap
}

fun parsePriceJsonToMap(json: String?): Map<String, Double> {
    if (json.isNullOrBlank()) return emptyMap()

    return try {
        val type = object : TypeToken<Map<String, Double>>() {}.type
        Gson().fromJson(json, type)
    } catch (e: Exception) {
        try {
            parsePrice(json)
        } catch (e2: Exception) {
            emptyMap()
        }
    }
}

/**
 * Los precios del catálogo listos para pintar: contado → corto plazo → lista,
 * desconocidos al final. E-APP-059: el orden lo da el NOMBRE de cada precio,
 * nunca el orden en que llegó del servidor (`LIST()` de Firebird no lo garantiza).
 * Sirve igual con la cadena vieja del servidor que con el JSON guardado en Room.
 */
fun preciosParaMostrar(prices: String?): List<Pair<String, Double>> = ClasificadorDePrecios.ordenar(
    try {
        parsePriceJsonToMap(prices)
    } catch (e: Exception) {
        emptyMap()
    }
)

/**
 * El precio de contado de la tarjeta del selector. E-APP-060: antes era
 * `priceMap.values.lastOrNull()`, que dependía de la posición de llegada.
 */
fun precioDeContadoDeTarjeta(prices: String?): Double? = ClasificadorDePrecios.precioDe(
    try {
        parsePriceJsonToMap(prices)
    } catch (e: Exception) {
        emptyMap()
    },
    TipoDePrecio.CONTADO
)

/**
 * E-APP-059: prefiere `precios_ordenados` del servidor cuando viene (clasificado
 * por `PRECIO_EMPRESA_ID`, no por nombre); si no viene —APIs viejas, o el producto
 * salió de Room, que sólo guarda la cadena— cae a [preciosParaMostrar] por nombre.
 * Las entradas sin nombre o sin precio se descartan; el orden lo da el tipo, no
 * la posición, también aquí.
 */
fun preciosParaMostrar(product: ProductInventory): List<Pair<String, Double>> =
    preciosClasificadosParaMostrar(product).map { it.nombre to it.valor }

/** Un precio de la lista que se pinta, con el TIPO con el que la app lo clasificó. */
data class PrecioClasificado(val tipo: TipoDePrecio, val nombre: String, val valor: Double)

/**
 * La MISMA lista y el MISMO orden que [preciosParaMostrar] (esa función delega aquí), pero con el
 * tipo de cada precio: el de `precios_ordenados` del servidor si viene, o el que da
 * [ClasificadorDePrecios.tipoDe] por nombre. Lo usa «Compartir» para no reclasificar nada por su cuenta.
 */
fun preciosClasificadosParaMostrar(product: ProductInventory): List<PrecioClasificado> {
    val ordenados = product.preciosOrdenados.orEmpty()
        .filter { !it.nombre.isNullOrBlank() && it.precio != null }
    if (ordenados.isEmpty()) {
        return preciosParaMostrar(product.PRECIOS).map { (nombre, valor) ->
            PrecioClasificado(ClasificadorDePrecios.tipoDe(nombre), nombre, valor)
        }
    }
    return ordenados
        .sortedWith(
            compareBy({ ClasificadorDePrecios.tipoDeServidor(it.tipo).ordinal }, { it.nombre })
        )
        .map {
            PrecioClasificado(
                ClasificadorDePrecios.tipoDeServidor(it.tipo),
                requireNotNull(it.nombre),
                requireNotNull(it.precio)
            )
        }
}

/** E-APP-060: el contado de la tarjeta, de `precios_ordenados` si viene, si no por nombre. */
fun precioDeContadoDeTarjeta(product: ProductInventory): Double? =
    product.preciosOrdenados.orEmpty()
        .firstOrNull {
            ClasificadorDePrecios.tipoDeServidor(it.tipo) == TipoDePrecio.CONTADO && it.precio != null
        }
        ?.precio
        ?: precioDeContadoDeTarjeta(product.PRECIOS)
