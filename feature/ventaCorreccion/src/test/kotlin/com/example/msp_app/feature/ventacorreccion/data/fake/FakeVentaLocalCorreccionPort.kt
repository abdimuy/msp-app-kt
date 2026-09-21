package com.example.msp_app.feature.ventacorreccion.data.fake

import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.EstadoVentaLocal
import com.example.msp_app.feature.ventacorreccion.domain.VentaLocalParaCorregir
import com.example.msp_app.feature.ventacorreccion.domain.port.VentaLocalCorreccionPort

/**
 * Fake en memoria de [VentaLocalCorreccionPort] — para pruebas que sólo necesitan el
 * comportamiento OBSERVABLE del puerto (p. ej. [com.example.msp_app.feature.ventacorreccion.ui.CorreccionVentaViewModel]),
 * no la persistencia real. Las pruebas de correctitud de la transacción (guardia primero,
 * "nada escrito") viven contra Room de verdad
 * (`com.example.msp_app.feature.ventacorreccion.usecase.CorreccionCasosDeUsoTest`) — este fake
 * NO reproduce esa atomicidad, sólo el contrato del puerto.
 */
class FakeVentaLocalCorreccionPort : VentaLocalCorreccionPort {

    private data class Fila(
        var campos: CamposVentaCorregidos,
        var productos: List<LocalSaleProductEntity>,
        var combos: List<LocalSaleComboEntity>,
        var enviado: Boolean = false,
        var permanente: Boolean = false,
        var correccionNoEnviada: Boolean = false,
        var claimId: String? = null,
        var claimKind: String? = null
    )

    private val filas = mutableMapOf<String, Fila>()

    /**
     * Si no es `null`, [guardarCorreccion] lo lanza en vez de aplicar el cambio — simula un
     * error NO clasificable como [com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardadoRechazadoException]
     * (p. ej. `IllegalArgumentException` de `LocalSaleProductDao.mergeProductsForSale` ante un
     * `ARTICULO_ID` repetido, Minor #1 de la ronda 1 de arreglo).
     */
    var lanzarEnGuardar: Throwable? = null

    // Contadores de invocación (ronda de arreglo 1 de Task 5): [ConsultarEstadoCorreccionTest]
    // los usa para blindar "sólo lee, nunca reclama" — una prueba que mire sólo el VALOR
    // devuelto no distingue una consulta de sólo lectura de una que además reclamó el candado
    // por dentro.
    var leerEstadoCallCount = 0
        private set
    var leerVentaCallCount = 0
        private set
    var reclamarParaEditarCallCount = 0
        private set
    var soltarCallCount = 0
        private set
    var guardarCorreccionCallCount = 0
        private set

    fun siembra(
        saleId: String,
        campos: CamposVentaCorregidos,
        productos: List<LocalSaleProductEntity> = emptyList(),
        combos: List<LocalSaleComboEntity> = emptyList(),
        enviado: Boolean = false
    ) {
        filas[saleId] = Fila(campos, productos, combos, enviado = enviado)
    }

    override suspend fun leerEstado(saleId: String): EstadoVentaLocal? {
        leerEstadoCallCount++
        return filas[saleId]?.let {
            EstadoVentaLocal(
                enviado = it.enviado,
                permanente = it.permanente,
                correccionNoEnviada = it.correccionNoEnviada,
                claimKind = it.claimKind,
                claimedAt = if (it.claimKind != null) 0L else null
            )
        }
    }

    override suspend fun leerVenta(saleId: String): VentaLocalParaCorregir? {
        leerVentaCallCount++
        return filas[saleId]?.let {
            VentaLocalParaCorregir(it.campos, it.productos, it.combos)
        }
    }

    override suspend fun reclamarParaEditar(saleId: String, claimId: String, ahora: Long): Boolean {
        reclamarParaEditarCallCount++
        val fila = filas[saleId] ?: return false
        if (fila.enviado || fila.permanente) return false
        if (fila.claimKind == "UPLOAD") return false
        fila.claimId = claimId
        fila.claimKind = "EDIT"
        return true
    }

    override suspend fun soltar(saleId: String, claimId: String) {
        soltarCallCount++
        val fila = filas[saleId] ?: return
        if (fila.claimId == claimId) {
            fila.claimId = null
            fila.claimKind = null
        }
    }

    override suspend fun guardarCorreccion(
        saleId: String,
        claimId: String,
        campos: CamposVentaCorregidos,
        productos: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity>
    ): Boolean {
        guardarCorreccionCallCount++
        lanzarEnGuardar?.let { throw it }
        val fila = filas[saleId] ?: return false
        if (fila.enviado || fila.claimId != claimId) return false
        fila.campos = campos
        fila.productos = productos
        fila.combos = combos
        fila.claimId = null
        fila.claimKind = null
        return true
    }
}
