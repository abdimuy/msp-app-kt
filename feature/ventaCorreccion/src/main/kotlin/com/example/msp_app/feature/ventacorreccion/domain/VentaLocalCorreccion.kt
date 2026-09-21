package com.example.msp_app.feature.ventacorreccion.domain

import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity

/**
 * Campos editables de `local_sale` durante una corrección — todo lo que el formulario puede
 * cambiar, EXCEPTO `ENVIADO`/candado (`CLAIM_ID`/`CLAIM_KIND`/`CLAIMED_AT`)/`REVISION`/
 * `IDEMPOTENCY_KEY`, que el caso de uso y el guardia del guardado controlan — nunca el llamador
 * (Global Constraint del plan: "la `Idempotency-Key` NO se rota jamás en este flujo").
 */
data class CamposVentaCorregidos(
    val nombreCliente: String,
    val fechaVenta: String,
    val latitud: Double,
    val longitud: Double,
    val direccion: String,
    val parcialidad: Double,
    val enganche: Double?,
    val telefono: String,
    val frecPago: String,
    val avalOResponsable: String?,
    val nota: String?,
    val diaCobranza: String,
    val precioTotal: Double,
    val tiempoACortoPlazoMeses: Int,
    val montoACortoPlazo: Double,
    val montoDeContado: Double,
    val numero: String? = null,
    val colonia: String? = null,
    val poblacion: String? = null,
    val ciudad: String? = null,
    val tipoVenta: String? = null,
    val zonaClienteId: Int? = null,
    val zonaCliente: String? = null,
    val clienteId: Int? = null
)

/**
 * Espejo puro de las columnas de `local_sale` que deciden [EstadoCorreccion] — ver
 * [evaluarCorregibilidad]. Vive aquí (no en `:core:database`) porque es la forma que el DOMINIO
 * necesita para evaluar corregibilidad, no una entidad Room.
 */
data class EstadoVentaLocal(
    val enviado: Boolean,
    val permanente: Boolean,
    val correccionNoEnviada: Boolean,
    val claimKind: String?,
    val claimedAt: Long?
)

/**
 * Venta local completa para poblar el formulario de corrección: campos editables + líneas
 * (productos/combos) tal como están HOY en la base — la fuente que el editor muestra al abrir.
 */
data class VentaLocalParaCorregir(
    val campos: CamposVentaCorregidos,
    val productos: List<LocalSaleProductEntity>,
    val combos: List<LocalSaleComboEntity>
)
