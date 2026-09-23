package com.example.msp_app.feature.ventacorreccion.domain.port

import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.EstadoVentaLocal
import com.example.msp_app.feature.ventacorreccion.domain.VentaLocalParaCorregir

/**
 * Puerto hacia la persistencia de la corrección de una venta local. Implementación real:
 * [com.example.msp_app.feature.ventacorreccion.data.RoomVentaLocalCorreccionAdapter], sobre el
 * DAO atómico de Task 1 (`LocalSaleDao`/`LocalSaleProductDao`/`LocalSaleComboDao`). La
 * atomicidad de cada operación la da SQLite (una sola sentencia, o una transacción explícita
 * para [guardarCorreccion]) — este puerto sólo expone la forma que el dominio necesita.
 */
interface VentaLocalCorreccionPort {

    /** Estado crudo de corregibilidad de la venta — `null` si no existe. */
    suspend fun leerEstado(saleId: String): EstadoVentaLocal?

    /** Venta completa (campos editables + líneas) para poblar el formulario — `null` si no existe. */
    suspend fun leerVenta(saleId: String): VentaLocalParaCorregir?

    /**
     * Intenta tomar el candado de EDICIÓN (`LocalSaleDao.claimForEdit`, reentrante para un
     * `EDIT` vivo). `true` si lo tomó.
     */
    suspend fun reclamarParaEditar(saleId: String, claimId: String, ahora: Long): Boolean

    /** Suelta el candado si [claimId] sigue siendo el vigente; no-op si no (venció y otro lo tomó). */
    suspend fun soltar(saleId: String, claimId: String)

    /**
     * Guardado atómico de la corrección: el guardia (`commitEditGuard`) va PRIMERO, dentro de
     * la MISMA transacción que todo lo demás. Si el guardia falla (`false`), NADA se escribe —
     * ni campos, ni líneas, ni se limpia el registro de fallo. Si pasa: campos + merge de
     * productos/combos (conserva `SERVER_UUID`, LA BASE MANDA — Task 1) + `clearUploadFailure`,
     * todo o nada.
     */
    suspend fun guardarCorreccion(
        saleId: String,
        claimId: String,
        campos: CamposVentaCorregidos,
        productos: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity>
    ): Boolean
}
