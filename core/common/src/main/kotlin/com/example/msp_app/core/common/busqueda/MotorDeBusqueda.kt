package com.example.msp_app.core.common.busqueda

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * De dónde salen las filas candidatas. La implementación real es Room (`:core:database`); las
 * pruebas usan una en memoria con la MISMA semántica, así el motor se prueba sin base de datos.
 */
interface FuenteDeFilas {
    /** Filas cuyo nombre contiene TODAS las [palabras] (un `LIKE` por palabra, unidas con AND). */
    suspend fun filasConPalabras(palabras: List<String>): List<FilaDeBusqueda>

    /** Filas cuyo `CLIENTE_ID` empieza con [digitos] (incluye el exacto). */
    suspend fun filasConNumero(digitos: String): List<FilaDeBusqueda>

    /** Todas las filas; solo se pide en la segunda pasada (errores de dedo). */
    suspend fun todasLasFilas(): List<FilaDeBusqueda>
}

/**
 * Resultado de una búsqueda: ids ya ordenados (a lo más [MotorDeBusqueda.LIMITE_DE_RESULTADOS]
 * por sección) y cuántos coincidieron en total, para poder decir "Mostrando 20 de N".
 */
data class ResultadoDeBusqueda(
    val ids: List<Int>,
    val total: Int,
    val parecidosIds: List<Int> = emptyList(),
    val totalParecidos: Int = 0
) {
    val hayMasDeLosMostrados: Boolean get() = total > ids.size

    companion object {
        val VACIO = ResultadoDeBusqueda(emptyList(), 0)
    }
}

/**
 * Buscador de clientes (E-APP-157). Todo el flujo, sin Android:
 *
 * 1. Número de cliente (solo dígitos): exacto primero, luego prefijo por `CLIENTE_ID`.
 * 2. Nombre: TODAS las palabras a la vez contra la fuente, SIN tope previo al filtrado
 *    (el `LIMIT 300` anterior cortaba antes de filtrar y dejaba fuera al cliente), se
 *    re-verifica en memoria, se ordena con [OrdenDeResultados] y se recorta a 20.
 * 3. Solo si el paso 2 dio 0: segunda pasada que tolera UN error de dedo por palabra de 5 o más
 *    letras (las más cortas deben coincidir exactas como subcadena); sale en `parecidos`.
 */
class MotorDeBusqueda(
    private val fuente: FuenteDeFilas,
    private val limite: Int = LIMITE_DE_RESULTADOS
) {

    suspend fun buscar(texto: String): ResultadoDeBusqueda =
        when (val consulta = ConsultaDeBusqueda.interpretar(texto)) {
            ConsultaDeBusqueda.Vacia -> ResultadoDeBusqueda.VACIO
            is ConsultaDeBusqueda.PorNumero -> porNumero(consulta)
            is ConsultaDeBusqueda.PorNombre -> porNombre(consulta)
        }

    private suspend fun porNumero(consulta: ConsultaDeBusqueda.PorNumero): ResultadoDeBusqueda {
        val filas = fuente.filasConNumero(consulta.digitos)
            .filter { it.id.toString().startsWith(consulta.digitos) }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.id.toString() != consulta.digitos }, { it.id }))
        return ResultadoDeBusqueda(filas.take(limite).map { it.id }, filas.size)
    }

    private suspend fun porNombre(consulta: ConsultaDeBusqueda.PorNombre): ResultadoDeBusqueda {
        val exactas = fuente.filasConPalabras(consulta.palabras)
            .filter { contieneTodas(it, consulta.palabras) }
        if (exactas.isNotEmpty()) {
            val ordenadas = OrdenDeResultados.ordenar(exactas, consulta)
            return ResultadoDeBusqueda(ordenadas.take(limite).map { it.id }, ordenadas.size)
        }
        val parecidas = parecidos(consulta)
        return ResultadoDeBusqueda(
            ids = emptyList(),
            total = 0,
            parecidosIds = parecidas.take(limite).map { it.id },
            totalParecidos = parecidas.size
        )
    }

    private fun contieneTodas(fila: FilaDeBusqueda, palabras: List<String>): Boolean {
        val nombre = NormalizadorDeBusqueda.normalizarGuardado(fila.nombreNormalizado)
        return palabras.all { nombre.contains(it) }
    }

    private suspend fun parecidos(consulta: ConsultaDeBusqueda.PorNombre): List<FilaDeBusqueda> {
        if (consulta.palabras.none { it.length >= LARGO_MINIMO_PARA_TOLERAR }) return emptyList()
        val coincidencias = CoincidenciaTolerante(consulta.palabras)
        val contexto = currentCoroutineContext()
        var revisadas = 0
        val candidatas = fuente.todasLasFilas().mapNotNull { fila ->
            // ~44k filas: sin esto, teclear otra consulta no podría detener el recorrido.
            if (++revisadas % FILAS_ENTRE_CANCELACIONES == 0) contexto.ensureActive()
            val nombre = NormalizadorDeBusqueda.normalizarGuardado(fila.nombreNormalizado)
            val errores = coincidencias.errores(nombre)
            if (errores == null) {
                null
            } else {
                Triple(
                    fila,
                    errores,
                    OrdenDeResultados.clave(fila, consulta)
                )
            }
        }
        return candidatas
            .sortedWith(
                compareBy<Triple<FilaDeBusqueda, Int, OrdenDeResultados.Clave>>({
                    it.second
                }, { it.third })
            )
            .map { it.first }
    }

    companion object {
        const val LIMITE_DE_RESULTADOS = 20
        const val LARGO_MINIMO_PARA_TOLERAR = 5
        const val FILAS_ENTRE_CANCELACIONES = 512
    }
}

/**
 * ¿Un nombre normalizado coincide con todas las palabras, tolerando UN error de dedo en cada
 * palabra de [MotorDeBusqueda.LARGO_MINIMO_PARA_TOLERAR] o más letras? Devuelve cuántas
 * palabras necesitaron la tolerancia, o `null` si no coincide.
 *
 * Una palabra corta debe estar tal cual como subcadena. Una palabra larga coincide si está tal
 * cual como subcadena, o si alguna palabra del nombre (o su inicio de la misma longitud ±1, para
 * lo tecleado a medias) está a distancia de edición ≤ 1 ([DistanciaDeEdicion]).
 */
internal class CoincidenciaTolerante(private val palabras: List<String>) {

    fun errores(nombre: String): Int? {
        var errores = 0
        var tokens: List<String>? = null
        for (palabra in palabras) {
            if (nombre.contains(palabra)) continue
            if (palabra.length < MotorDeBusqueda.LARGO_MINIMO_PARA_TOLERAR) return null
            val lista = tokens ?: NormalizadorDeBusqueda.palabras(nombre).also { tokens = it }
            if (lista.none { token -> parecida(palabra, token) }) return null
            errores++
        }
        return errores
    }

    private fun parecida(palabra: String, token: String): Boolean {
        if (DistanciaDeEdicion.dentroDeUno(palabra, token)) return true
        for (largo in palabra.length - 1..palabra.length + 1) {
            if (largo in 1..token.length && DistanciaDeEdicion.dentroDeUno(token, 0, largo, palabra)) return true
        }
        return false
    }
}
