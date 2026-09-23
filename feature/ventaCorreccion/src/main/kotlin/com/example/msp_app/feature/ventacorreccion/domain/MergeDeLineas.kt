// El nombre del archivo es `MergeDeLineas.kt` (Task 2, "Archivos exclusivos"
// del plan) porque la función `mergeDeLineas` de abajo es la API real de
// este archivo; `ResultadoMerge` es sólo su tipo de retorno. Detekt exige
// que el ÚNICO top-level `class`/`data class` de un archivo coincida con su
// nombre — no ve la función como una alternativa válida, y la suprime a
// nivel de ARCHIVO (no de declaración: `MatchingDeclarationName` reporta
// sobre el archivo entero, un `@Suppress` en la clase no basta).
@file:Suppress("MatchingDeclarationName")

package com.example.msp_app.feature.ventacorreccion.domain

/**
 * Resultado de fusionar el estado ACTUAL de una lista de líneas (productos o
 * combos de una venta local) contra lo DESEADO tras una corrección.
 *
 * @param aActualizar líneas deseadas cuya clave YA existía en [actuales] —
 *   listas para persistir con [mergeDeLineas]'s regla de `SERVER_UUID`
 *   aplicada (ver abajo).
 * @param aInsertar líneas deseadas cuya clave es nueva.
 * @param aBorrar claves de [actuales] que ya no están en lo deseado.
 */
data class ResultadoMerge<Linea, Clave>(
    val aActualizar: List<Linea>,
    val aInsertar: List<Linea>,
    val aBorrar: List<Clave>
)

/**
 * Fusión PURA de líneas de una venta (plan "Corregir una venta antes de que
 * suba", sección "El merge de líneas") — sin Room, sin efectos. Genérica
 * sobre el tipo de línea ([Linea]: `LocalSaleProductEntity` o
 * `LocalSaleComboEntity` de `:core:database`, o cualquier otro) y su clave
 * natural ([Clave]: `ARTICULO_ID` o `COMBO_ID`), porque el criterio es
 * idéntico para ambos y ya vive duplicado (con Room de por medio) en
 * `LocalSaleProductDao.mergeProductsForSale` y
 * `LocalSaleComboDao.mergeCombosForSale` (Task 1) — esta es la misma
 * decisión, aislada, probable en Capa 1 sin levantar una base de datos.
 *
 * Invariante central (LA BASE MANDA): una línea que sobrevive (misma
 * [clave] en actuales y deseadas) conserva el `SERVER_UUID` que YA tenía en
 * [actuales], sin importar qué traiga la línea deseada — el editor nunca
 * carga el `SERVER_UUID` real, así que un valor entrante para una línea que
 * sobrevive es basura vieja o `null`; confiar en él pierde la identidad que
 * el subidor ya había acuñado en un intento previo. Una línea nueva se
 * inserta tal cual llega (su `SERVER_UUID`, si trae alguno, es irrelevante:
 * el subidor se lo acuña en su momento).
 *
 * @param actuales líneas hoy en la base para esta venta.
 * @param deseadas líneas que el formulario de corrección quiere dejar. No
 *   puede traer [clave] repetida — mismo `require` que las dos DAO.
 * @param clave identidad natural de una línea (p. ej. `ARTICULO_ID`).
 * @param serverUuid lee el `SERVER_UUID` de una línea.
 * @param conServerUuid copia una línea deseada reemplazando su `SERVER_UUID`
 *   por el preservado de [actuales].
 */
fun <Linea, Clave> mergeDeLineas(
    actuales: List<Linea>,
    deseadas: List<Linea>,
    clave: (Linea) -> Clave,
    serverUuid: (Linea) -> String?,
    conServerUuid: (Linea, String?) -> Linea
): ResultadoMerge<Linea, Clave> {
    require(deseadas.size == deseadas.distinctBy(clave).size) {
        "deseadas trae una clave repetida"
    }

    val uuidActualPorClave = actuales.associate { clave(it) to serverUuid(it) }
    val clavesDeseadas = deseadas.map(clave).toSet()
    val aBorrar = actuales.map(clave).filterNot { it in clavesDeseadas }

    val aActualizar = mutableListOf<Linea>()
    val aInsertar = mutableListOf<Linea>()
    for (linea in deseadas) {
        val k = clave(linea)
        if (uuidActualPorClave.containsKey(k)) {
            aActualizar += conServerUuid(linea, uuidActualPorClave.getValue(k))
        } else {
            aInsertar += linea
        }
    }

    return ResultadoMerge(aActualizar = aActualizar, aInsertar = aInsertar, aBorrar = aBorrar)
}
