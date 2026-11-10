package com.example.msp_app.core.testing.padron

import com.example.msp_app.core.common.busqueda.FilaDeBusqueda
import com.example.msp_app.core.common.busqueda.FuenteDeFilas
import com.example.msp_app.core.common.busqueda.NormalizadorDeBusqueda

/**
 * [FuenteDeFilas] en memoria con la MISMA semántica que la consulta SQL real: cada palabra es una
 * subcadena literal (sin comodines) del nombre guardado, sin importar mayúsculas, unidas con AND.
 * Cuenta las llamadas para poder afirmar qué pasada se ejecutó.
 */
class FuenteEnMemoria(private val filas: List<FilaDeBusqueda>) : FuenteDeFilas {
    var llamadasPorPalabras = 0
    var llamadasPorNumero = 0
    var llamadasATodas = 0

    override suspend fun filasConPalabras(palabras: List<String>): List<FilaDeBusqueda> {
        llamadasPorPalabras++
        return filas.filter { fila ->
            palabras.all {
                fila.nombreNormalizado.contains(it, ignoreCase = true)
            }
        }
    }

    override suspend fun filasConNumero(digitos: String): List<FilaDeBusqueda> {
        llamadasPorNumero++
        return filas.filter { it.id.toString().startsWith(digitos) }
    }

    override suspend fun todasLasFilas(): List<FilaDeBusqueda> {
        llamadasATodas++
        return filas
    }

    companion object {
        /** Arma filas como las guarda el sync: `NOMBRE` pasado por la normalización nueva. */
        fun deNombres(vararg pares: Pair<Int, String>): FuenteEnMemoria = FuenteEnMemoria(
            pares.map { (id, nombre) ->
                FilaDeBusqueda(
                    id,
                    NormalizadorDeBusqueda.normalizar(nombre)
                )
            }
        )
    }
}
