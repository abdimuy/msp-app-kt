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
        var correccionRemotaPendiente: Boolean = false,
        var correccionRemotaEstado: String? = null,
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

    /**
     * Siembra una fila. Los cuatro parámetros de estado (además de [enviado]) son los que
     * deciden [com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion]: los dos
     * últimos llegaron con el nivel 2 y tienen valor por omisión para no tocar las pruebas que
     * nunca hablan de correcciones remotas.
     */
    @Suppress("LongParameterList")
    fun siembra(
        saleId: String,
        campos: CamposVentaCorregidos,
        productos: List<LocalSaleProductEntity> = emptyList(),
        combos: List<LocalSaleComboEntity> = emptyList(),
        enviado: Boolean = false,
        permanente: Boolean = false,
        correccionNoEnviada: Boolean = false,
        correccionRemotaPendiente: Boolean = false,
        correccionRemotaEstado: String? = null
    ) {
        filas[saleId] = Fila(
            campos,
            productos,
            combos,
            enviado = enviado,
            permanente = permanente,
            correccionNoEnviada = correccionNoEnviada,
            correccionRemotaPendiente = correccionRemotaPendiente,
            correccionRemotaEstado = correccionRemotaEstado
        )
    }

    /** `ENVIADO` tal como quedó en la fila — el invariante que el nivel 2 no puede romper. */
    fun enviadoDe(saleId: String): Boolean? = filas[saleId]?.enviado

    /** `CORRECCION_REMOTA_PENDIENTE` tal como quedó en la fila. */
    fun correccionRemotaPendienteDe(saleId: String): Boolean? =
        filas[saleId]?.correccionRemotaPendiente

    override suspend fun leerEstado(saleId: String): EstadoVentaLocal? {
        leerEstadoCallCount++
        return filas[saleId]?.let {
            EstadoVentaLocal(
                enviado = it.enviado,
                permanente = it.permanente,
                correccionNoEnviada = it.correccionNoEnviada,
                correccionRemotaPendiente = it.correccionRemotaPendiente,
                correccionRemotaEstado = it.correccionRemotaEstado,
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

    /**
     * Espejo del `WHERE` de `LocalSaleDao.claimForEdit` tras el nivel 2, en el mismo orden en
     * que el dominio resuelve la precedencia: la marca terminal y la cola ganan sobre todo; una
     * venta ya enviada SÍ se puede reclamar mientras no haya divergencia marcada; sin enviar,
     * manda el fallo permanente. Si este predicado y el del DAO dejan de coincidir, las pruebas
     * que corren contra este fake dejan de decir algo sobre el flujo real.
     */
    override suspend fun reclamarParaEditar(saleId: String, claimId: String, ahora: Long): Boolean {
        reclamarParaEditarCallCount++
        val fila = filas[saleId] ?: return false
        val cerradaPorElServidor =
            fila.correccionRemotaPendiente || fila.correccionRemotaEstado != null
        val abierta = if (fila.enviado) !fila.correccionNoEnviada else !fila.permanente
        val subiendo = fila.claimKind == "UPLOAD"
        if (cerradaPorElServidor || !abierta || subiendo) return false
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
        if (fila.claimId != claimId) return false
        // Espejo de los dos guardias excluyentes por `ENVIADO`: `commitEditGuard` (sin enviar) y
        // `commitEditGuardEnviada` (ya enviada, que además exige cola vacía y sin marca
        // terminal).
        if (fila.enviado && (fila.correccionRemotaPendiente || fila.correccionRemotaEstado != null)) {
            return false
        }
        fila.campos = campos
        fila.productos = productos
        fila.combos = combos
        // `ENVIADO` NO se toca nunca: bajarlo devolvería la venta a la cola de alta con su
        // `Idempotency-Key` original y el servidor contestaría con la respuesta vieja.
        if (fila.enviado) {
            fila.correccionRemotaPendiente = true
        }
        fila.claimId = null
        fila.claimKind = null
        return true
    }
}
