package com.example.msp_app.feature.compartirproductos.android

import com.example.msp_app.feature.compartirproductos.domain.ArmadorDeTarjeta
import com.example.msp_app.feature.compartirproductos.domain.ArticuloParaCompartir
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import com.example.msp_app.feature.compartirproductos.render.DibujanteDeTarjeta
import com.example.msp_app.feature.compartirproductos.render.FotoDeProducto
import java.io.File
import java.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Dibuja una tarjeta JPEG por artículo en `cacheDir/compartir/`. Es trabajo de CPU y disco:
 * quien llama lo corre fuera del hilo principal (el `ViewModel` lo hace en `Dispatchers.Default`).
 */
open class GeneradorDeTarjetas(
    private val dibujante: DibujanteDeTarjeta,
    private val almacen: AlmacenDeTemporales
) {

    /**
     * Borra lo de envíos anteriores y escribe las tarjetas nuevas, en el mismo orden de [articulos].
     * El título de cada una sale SIEMPRE del corte automático del nombre (el vendedor no lo edita).
     * [alAvanzar] recibe (hechas, total) para el indicador de progreso.
     */
    open suspend fun generar(
        articulos: List<ArticuloParaCompartir>,
        seleccion: SeleccionDePrecios,
        ahora: Instant,
        alAvanzar: (Int, Int) -> Unit = { _, _ -> }
    ): List<File> {
        almacen.vaciar()
        return articulos.mapIndexed { i, articulo ->
            // E-APP-186: si se canceló (VM destruido) no se siguen dibujando tarjetas que nadie va a mandar.
            currentCoroutineContext().ensureActive()
            val tarjeta = ArmadorDeTarjeta.armar(articulo, seleccion, ahora)
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
