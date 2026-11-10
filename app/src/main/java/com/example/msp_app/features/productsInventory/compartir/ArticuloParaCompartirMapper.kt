package com.example.msp_app.features.productsInventory.compartir

import com.example.msp_app.core.utils.preciosClasificadosParaMostrar
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.feature.compartirproductos.domain.ArticuloParaCompartir
import com.example.msp_app.feature.compartirproductos.domain.PrecioDeCatalogo
import java.math.BigDecimal

/**
 * Cableado de «Compartir»: del producto del catálogo a lo que necesita `:feature:compartirProductos`.
 * Los precios salen de [preciosClasificadosParaMostrar] (la clasificación y el orden de la app, E-APP-059);
 * aquí no se reclasifica ni se reordena nada. `LINEA_ARTICULO` es la categoría; vacía = sin categoría.
 */
fun ProductInventory.paraCompartir(fotoPath: String?): ArticuloParaCompartir {
    // Gson no respeta la nulabilidad de Kotlin: un campo ausente llega `null` aunque diga `String`.
    val nombreCrudo: String? = ARTICULO
    val lineaCruda: String? = LINEA_ARTICULO
    return ArticuloParaCompartir(
        id = ARTICULO_ID,
        nombre = nombreCrudo.orEmpty(),
        categoria = lineaCruda?.takeIf { it.isNotBlank() },
        precios = preciosClasificadosParaMostrar(this).map {
            PrecioDeCatalogo(it.tipo, BigDecimal.valueOf(it.valor))
        },
        fotoPath = fotoPath
    )
}
