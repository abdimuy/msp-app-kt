package com.example.msp_app.core.database.clientes

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.msp_app.core.common.busqueda.FilaDeBusqueda
import com.example.msp_app.core.common.busqueda.FuenteDeFilas
import com.example.msp_app.core.common.busqueda.MotorDeBusqueda
import com.example.msp_app.core.common.busqueda.NormalizadorDeBusqueda
import com.example.msp_app.core.database.dao.ClienteDao
import com.example.msp_app.core.database.entities.ClienteBusquedaRow
import com.example.msp_app.core.database.entities.ClienteEntity

/**
 * Construye las consultas SQL del buscador (E-APP-157). El texto del usuario NUNCA entra al
 * texto de la consulta: solo viaja en `args`. Las palabras se escapan para `LIKE ... ESCAPE '\'`
 * ([NormalizadorDeBusqueda.escaparParaLike]), así `%`, `_` y `\` son literales.
 */
object ConsultasDeBusqueda {

    private const val SELECCION = "SELECT CLIENTE_ID, NOMBRE_NORMALIZADO FROM cliente"

    fun porPalabras(palabras: List<String>): SupportSQLiteQuery {
        val condiciones = palabras.joinToString(" AND ") { "NOMBRE_NORMALIZADO LIKE ? ESCAPE '\\'" }
        val argumentos = palabras.map {
            "%${NormalizadorDeBusqueda.escaparParaLike(it)}%"
        }.toTypedArray<Any?>()
        return SimpleSQLiteQuery("$SELECCION WHERE $condiciones", argumentos)
    }

    /** [digitos] solo contiene 0-9 (lo garantiza `ConsultaDeBusqueda.PorNumero`). */
    fun porNumero(digitos: String): SupportSQLiteQuery = SimpleSQLiteQuery(
        "$SELECCION WHERE CAST(CLIENTE_ID AS TEXT) LIKE ?",
        arrayOf<Any?>("$digitos%")
    )

    fun todas(): SupportSQLiteQuery = SimpleSQLiteQuery(SELECCION)
}

/** Resultado del buscador ya con entidades, listo para pintar. */
data class ResultadoDeClientes(
    val exactos: List<ClienteEntity>,
    val totalExactos: Int,
    val parecidos: List<ClienteEntity> = emptyList(),
    val totalParecidos: Int = 0
) {
    val hayMasDeLosMostrados: Boolean get() = totalExactos > exactos.size

    companion object {
        val VACIO = ResultadoDeClientes(emptyList(), 0)
    }
}

/** [FuenteDeFilas] sobre Room. */
internal class FuenteRoom(private val dao: ClienteDao) : FuenteDeFilas {
    override suspend fun filasConPalabras(palabras: List<String>): List<FilaDeBusqueda> =
        dao.filasDeBusqueda(ConsultasDeBusqueda.porPalabras(palabras)).map(::aFila)

    override suspend fun filasConNumero(digitos: String): List<FilaDeBusqueda> =
        dao.filasDeBusqueda(ConsultasDeBusqueda.porNumero(digitos)).map(::aFila)

    override suspend fun todasLasFilas(): List<FilaDeBusqueda> =
        dao.filasDeBusqueda(ConsultasDeBusqueda.todas()).map(::aFila)

    private fun aFila(fila: ClienteBusquedaRow) = FilaDeBusqueda(
        fila.CLIENTE_ID,
        fila.NOMBRE_NORMALIZADO
    )
}

/** Buscador de clientes sobre Room: [MotorDeBusqueda] + las entidades de los ids mostrados. */
class BuscadorDeClientes(private val dao: ClienteDao) {
    private val motor = MotorDeBusqueda(FuenteRoom(dao))

    suspend fun buscar(texto: String): ResultadoDeClientes {
        val resultado = motor.buscar(texto)
        val porId = dao.getByIds((resultado.ids + resultado.parecidosIds).distinct()).associateBy {
            it.CLIENTE_ID
        }
        return ResultadoDeClientes(
            exactos = resultado.ids.mapNotNull(porId::get),
            totalExactos = resultado.total,
            parecidos = resultado.parecidosIds.mapNotNull(porId::get),
            totalParecidos = resultado.totalParecidos
        )
    }
}
