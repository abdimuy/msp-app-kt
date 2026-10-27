package com.example.msp_app.features.sales.data

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.localsale.LocalSaleComboDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleProductDao
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleImageEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.database.escribirSinCancelar
import com.example.msp_app.core.database.sinCancelar

/** Lo que pasó al escribir una venta local (E-APP-094). */
enum class ResultadoDeLaVentaLocal {
    /** Se escribió ahora, completa: cabecera, líneas, combos, fotos y marcador. */
    ESCRITA,

    /** Ya estaba escrita con ese `saleId`: no se escribió nada. */
    YA_ESTABA,

    /**
     * Hay líneas con ese `saleId` pero no su cabecera. No se escribe nada: ni
     * se pisan (podrían ser de una venta que sí salió) ni se duplica la venta.
     */
    LINEAS_HUERFANAS
}

/**
 * La escritura de una venta local nueva (E-APP-094, diseño D5 de "Captura nunca
 * se pierde").
 *
 * Antes (MEDIDO por la compuerta ANTES del 2026-10-26): `insertSale` (REPLACE),
 * productos, combos y cada foto eran auto-commits sueltos bajo un
 * `viewModelScope` cancelable. Una falla a la mitad dejaba la cabecera y una
 * línea sin el resto; y como el `saleId` nacía en cada toque, volver a guardar
 * el mismo borrador creaba una segunda venta (dos ventas en Microsip).
 *
 * Ahora, con el `saleId` estable del borrador:
 * 1. [capturar]: marcador VENTA_LOCAL CAPTURADA, en `NonCancellable`, antes de
 *    comprimir las fotos (la única espera cancelable).
 * 2. [escribir]: UNA [escribirSinCancelar] con inserts `ABORT` de la cabecera,
 *    las líneas, los combos y las fotos, y el marcador a ESCRITA. Si la venta ya
 *    está, no escribe nada ([ResultadoDeLaVentaLocal.YA_ESTABA]).
 * 3. [cerrar]: tras encolar la subida, el marcador pasa a CERRADA.
 */
class RegistroDeVentaLocal(
    private val db: AppDatabase,
    private val clock: AppClock = AppClock.System,
    private val ventas: LocalSaleDao = db.localSaleDao(),
    private val lineas: LocalSaleProductDao = db.localSaleProduct(),
    private val combos: LocalSaleComboDao = db.localSaleComboDao()
) {
    private val capturas get() = db.capturaEnCursoDao()

    suspend fun capturar(saleId: String, usuario: String) {
        val ahora = AppTime.toWireFormat(clock.now())
        sinCancelar {
            capturas.capturar(
                CapturaEnCursoEntity(
                    ID = saleId,
                    TIPO = CapturaEnCursoEntity.TIPO_VENTA_LOCAL,
                    LLAVE = LLAVE_VENTA_NUEVA,
                    USUARIO = usuario,
                    ESTADO = CapturaEnCursoEntity.CAPTURADA,
                    RESUMEN = "{}",
                    CREADA_EN = ahora,
                    ACTUALIZADA_EN = ahora
                )
            )
        }
    }

    /** ¿Ya hay una venta local con ese id? Sólo lectura. */
    suspend fun yaSeGuardo(saleId: String): Boolean = ventas.getSaleById(saleId) != null

    suspend fun escribir(
        venta: LocalSaleEntity,
        productos: List<LocalSaleProductEntity>,
        combosDeLaVenta: List<LocalSaleComboEntity>,
        imagenes: List<LocalSaleImageEntity>
    ): ResultadoDeLaVentaLocal = db.escribirSinCancelar {
        val saleId = venta.LOCAL_SALE_ID
        val ahora = AppTime.toWireFormat(clock.now())
        when {
            ventas.getSaleById(saleId) != null -> {
                capturas.marcarEscrita(saleId, ahora)
                ResultadoDeLaVentaLocal.YA_ESTABA
            }
            // `local_sale_products` no tiene FK a `local_sale`: es la única tabla
            // hija que puede tener filas sin cabecera.
            lineas.getProductsForSale(saleId).isNotEmpty() ->
                ResultadoDeLaVentaLocal.LINEAS_HUERFANAS
            else -> {
                ventas.insertarVentaNueva(venta)
                if (productos.isNotEmpty()) lineas.insertarProductosNuevos(productos)
                if (combosDeLaVenta.isNotEmpty()) combos.insertarCombosNuevos(combosDeLaVenta)
                if (imagenes.isNotEmpty()) ventas.insertarImagenesNuevas(imagenes)
                capturas.marcarEscrita(saleId, ahora)
                ResultadoDeLaVentaLocal.ESCRITA
            }
        }
    }

    suspend fun cerrar(saleId: String) {
        sinCancelar { capturas.marcarCerrada(saleId, AppTime.toWireFormat(clock.now())) }
    }

    companion object {
        const val LLAVE_VENTA_NUEVA = "venta_nueva"
    }
}
