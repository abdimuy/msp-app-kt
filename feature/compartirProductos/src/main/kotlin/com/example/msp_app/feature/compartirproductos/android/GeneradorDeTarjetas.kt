package com.example.msp_app.feature.compartirproductos.android

import com.example.msp_app.feature.compartirproductos.domain.ArmadorDeTarjeta
import com.example.msp_app.feature.compartirproductos.domain.ArticuloParaCompartir
import com.example.msp_app.feature.compartirproductos.domain.PreferenciasDeCompartir
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import com.example.msp_app.feature.compartirproductos.render.DibujanteDeTarjeta
import com.example.msp_app.feature.compartirproductos.render.FotoDeProducto
import java.io.File
import java.time.Instant

/**
 * Dibuja una tarjeta JPEG por artículo en `cacheDir/compartir/`. Es trabajo de CPU y disco:
 * quien llama lo corre fuera del hilo principal (el `ViewModel` lo hace en `Dispatchers.Default`).
 */
class GeneradorDeTarjetas(
    private val dibujante: DibujanteDeTarjeta,
    private val almacen: AlmacenDeTemporales,
    private val preferencias: PreferenciasDeCompartir
) {

    /**
     * Borra lo de envíos anteriores y escribe las tarjetas nuevas, en el mismo orden de [articulos].
     * El título de cada una es el que el vendedor editó y la app recordó, o el automático.
     * [alAvanzar] recibe (hechas, total) para el indicador de progreso.
     *
     * @param tituloSolo si viene, es el título editado en la hoja y gana sobre el recordado (solo con un producto).
     */
    fun generar(
        articulos: List<ArticuloParaCompartir>,
        seleccion: SeleccionDePrecios,
        ahora: Instant,
        tituloSolo: String? = null,
        alAvanzar: (Int, Int) -> Unit = { _, _ -> }
    ): List<File> {
        almacen.vaciar()
        return articulos.mapIndexed { i, articulo ->
            val titulo = tituloSolo.takeIf { articulos.size == 1 } ?: preferencias.tituloEditado(articulo.id)
            val tarjeta = ArmadorDeTarjeta.armar(articulo, seleccion, titulo, ahora)
            val foto = FotoDeProducto.cargar(articulo.fotoPath)
            val archivo = almacen.archivoPara(articulo.id, i)
            try {
                dibujante.escribir(tarjeta, foto, archivo)
            } finally {
                foto?.recycle()
            }
            alAvanzar(i + 1, articulos.size)
            archivo
        }
    }
}
