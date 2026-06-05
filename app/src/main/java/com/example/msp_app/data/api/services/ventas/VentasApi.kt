package com.example.msp_app.data.api.services.ventas

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// ─── Request DTOs ──────────────────────────────────────────────────────────

data class CrearVentaBody(
    val cliente_id: String,
    val cliente_snapshot: ClienteSnapshotDTO,
    val direccion: DireccionDTO,
    val gps: GPSDTO?,
    val montos: MontosDTO,
    val plan_credito: PlanCreditoDTO,
    val dia_cobranza: DiaCobranzaDTO,
    val productos: List<ProductoDTO>,
    val combos: List<ComboDTO>,
    val vendedores: List<VendedorDTO>,
    val nota: String?,
    val tipo_venta: String,
    val zona_cliente_id: Int?,
    val almacen_id: Int?
)

data class ClienteSnapshotDTO(
    val nombre: String,
    val telefono: String?
)

data class DireccionDTO(
    val calle: String,
    val numero: String?,
    val colonia: String?,
    val poblacion: String?,
    val ciudad: String?
)

data class GPSDTO(
    val latitud: Double,
    val longitud: Double
)

data class MontosDTO(
    // decimal as string for precision
    val anual: String,
    val corto_plazo: String,
    val contado: String,
    val enganche: String?
)

data class PlanCreditoDTO(
    val tiempo_corto_plazo_meses: Int,
    val parcialidad: String,
    val frec_pago: String
)

data class DiaCobranzaDTO(
    val dia: String
)

data class ProductoDTO(
    // UUID generated client-side
    val id: String,
    val articulo_id: Int,
    val articulo: String,
    val cantidad: Int,
    val precio_lista: String,
    val precio_corto_plazo: String,
    val precio_contado: String,
    // UUID of parent combo, if any
    val combo_id: String?
)

data class ComboDTO(
    // UUID generated client-side
    val id: String,
    // catalog combo identifier
    val combo_id: String,
    val nombre: String,
    val precio_lista: String,
    val precio_corto_plazo: String,
    val precio_contado: String
)

data class VendedorDTO(
    // UUID generated client-side for snapshot row
    val id: String,
    // resolved via UsuariosApi.ensureVendedoresByEmail
    val usuario_id: String,
    val email: String,
    val nombre: String
)

// ─── Response DTOs ─────────────────────────────────────────────────────────

data class VentaDTO(
    val id: String,
    val cliente_id: String,
    val situacion: String,
    val created_at: String,
    val updated_at: String
)

/**
 * Retrofit interface for the v2 Go backend's ventas endpoints. The cliente
 * crea la venta en estado borrador subiendo el JSON + imágenes; la state-machine
 * (aprobar/aplicar) ocurre en desktop/oficina.
 *
 * El header Idempotency-Key debe ser el LOCAL_SALE_ID (UUID estable por Room)
 * para que reintentos del worker no produzcan duplicados.
 */
interface VentasApi {
    @Multipart
    @POST("v2/ventas")
    suspend fun crearVenta(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part("datos") datos: RequestBody,
        @Part imagen: List<MultipartBody.Part>
    ): VentaDTO
}
