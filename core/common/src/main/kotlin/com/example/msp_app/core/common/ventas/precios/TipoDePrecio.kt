package com.example.msp_app.core.common.ventas.precios

/**
 * Los tres escalones de precio de un artículo, en el ORDEN en que se muestran:
 * contado → corto plazo (4 meses) → largo plazo ("Precio de lista"). El orden
 * de declaración ES el orden de pantalla; los nombres desconocidos van al final.
 */
enum class TipoDePrecio { CONTADO, CORTO_PLAZO, LARGO_PLAZO, DESCONOCIDO }

/**
 * Clasifica por NOMBRE los precios que llegan del catálogo y los ordena por tipo.
 *
 * E-APP-059: el servidor arma la lista con `LIST()` de Firebird, que no garantiza
 * orden; la app pintaba en orden de llegada y elegía "el último" como precio.
 * Aquí el orden y la elección dependen sólo del nombre, nunca de la posición.
 *
 * Las reglas de nombre son las de `PriceParser` (`contains`, sin distinguir
 * mayúsculas) más el alias "Precio de Contado", que existe en Microsip fuera
 * de la configuración del servidor.
 */
object ClasificadorDePrecios {

    private const val NOMBRE_LISTA = "Precio de lista"
    private const val NOMBRE_CORTO = "Precio 4 Meses"
    private const val NOMBRE_CONTADO = "Precio 1 Meses"
    private const val ALIAS_CONTADO = "Precio de Contado"

    fun tipoDe(nombre: String): TipoDePrecio = when {
        nombre.contains(NOMBRE_LISTA, ignoreCase = true) -> TipoDePrecio.LARGO_PLAZO
        nombre.contains(NOMBRE_CORTO, ignoreCase = true) -> TipoDePrecio.CORTO_PLAZO
        nombre.contains(NOMBRE_CONTADO, ignoreCase = true) -> TipoDePrecio.CONTADO
        nombre.contains(ALIAS_CONTADO, ignoreCase = true) -> TipoDePrecio.CONTADO
        else -> TipoDePrecio.DESCONOCIDO
    }

    /** El nombre canónico gana sobre el alias cuando los dos traen el mismo tipo. */
    private fun rangoDeAlias(nombre: String): Int =
        if (nombre.contains(ALIAS_CONTADO, ignoreCase = true)) 1 else 0

    private val orden: Comparator<String> =
        compareBy<String>({ tipoDe(it).ordinal }, { rangoDeAlias(it) }).thenBy { it }

    /**
     * Las entradas ordenadas por tipo; empates (desconocidos, alias) por nombre,
     * para que el resultado sea idéntico para cualquier orden de llegada.
     */
    fun <V> ordenar(precios: Map<String, V>): List<Pair<String, V>> =
        precios.keys.sortedWith(orden).map { it to precios.getValue(it) }

    /** El precio del [tipo] pedido, o `null` si el catálogo no lo trae. */
    fun <V> precioDe(precios: Map<String, V>, tipo: TipoDePrecio): V? =
        ordenar(precios).firstOrNull { (nombre, _) -> tipoDe(nombre) == tipo }?.second
}
