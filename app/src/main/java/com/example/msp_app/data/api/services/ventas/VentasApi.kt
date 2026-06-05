package com.example.msp_app.data.api.services.ventas

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// ─── Request body ──────────────────────────────────────────────────────────

data class CrearVentaBody(
    val id: String,
    val cliente: ClienteSnapshotDTO,
    val direccion: DireccionDTO,
    val gps: GPSDTO,
    val fecha_venta: String,
    val tipo_venta: String,
    val montos: MontosDTO,
    val plan_credito: PlanCreditoDTO?,
    val dia_cobranza: DiaCobranzaDTO?,
    val nota: String?,
    val combos: List<ComboDTO>,
    val productos: List<ProductoDTO>,
    val vendedores: List<VendedorDTO>
)

data class ClienteSnapshotDTO(
    val cliente_id: Int?,
    val nombre: String,
    val telefono: String?,
    val aval: String?,
    val referencia: String?
)

data class DireccionDTO(
    val calle: String,
    val numero_exterior: String?,
    val colonia: String,
    val poblacion: String,
    val ciudad: String,
    val zona_cliente_id: Int?
)

data class GPSDTO(
    val latitud: Double,
    val longitud: Double
)

data class MontosDTO(
    val anual: String,
    val corto_plazo: String,
    val contado: String
)

data class PlanCreditoDTO(
    val plazo_meses: Int,
    val enganche: String,
    val parcialidad: String,
    val frec_pago: String
)

data class DiaCobranzaDTO(
    val semana: String?,
    val mes: Int?
)

data class ProductoDTO(
    val id: String,
    val articulo_id: Int,
    val articulo: String,
    val cantidad: String,
    val precio_anual: String,
    val precio_corto: String,
    val precio_contado: String,
    val combo_id: String?,
    val almacen_origen_id: Int?,
    val almacen_destino_id: Int?
)

data class ComboDTO(
    val id: String,
    val nombre: String,
    val precio_anual: String,
    val precio_corto: String,
    val precio_contado: String,
    val cantidad: String,
    val almacen_origen_id: Int,
    val almacen_destino_id: Int
)

data class VendedorDTO(
    val id: String,
    val usuario_id: String,
    val email: String,
    val nombre: String
)

// ─── Response DTOs ─────────────────────────────────────────────────────────

data class VentaDTO(
    val id: String,
    val situacion: String,
    val sincronizacion: String,
    val tipo_venta: String,
    val fecha_venta: String,
    val created_at: String,
    val updated_at: String
)

/**
 * Retrofit interface for the v2 Go backend's ventas endpoints.
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
