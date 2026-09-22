package com.example.msp_app.data.api.services.ventas

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

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

    /**
     * Verifica si una venta existe server-side. Usado por el worker para
     * reconciliar cuando el POST falla pero el server pudo haber creado la
     * venta vía replay-with-multipart admin u otro path asíncrono.
     */
    @GET("v2/ventas/{id}")
    suspend fun obtenerVenta(@Path("id") id: String): VentaDTO

    /**
     * Reemplaza **todas** las líneas (combos y productos) de una venta ya
     * subida que sigue en `borrador`. Requiere el permiso `ventas:editar`.
     *
     * No lleva `Idempotency-Key` ni `If-Match`: el servidor no los acepta. Es
     * un reemplazo total, así que el cuerpo describe el conjunto final, no un
     * delta, y reenviarlo es inofensivo mientras la venta siga editable.
     *
     * La respuesta real es el `VentaDTO` completo; aquí se lee sólo
     * [VentaSituacionDTO] (`situacion` + `sincronizacion`) y Gson descarta el
     * resto. Un 409 `venta_no_editable` significa que la venta salió de
     * `borrador`: es TERMINAL, no se reintenta. El código de error se saca con
     * `codigoDeErrorHttp`, porque no viaja en un campo `code`.
     */
    @PUT("v2/ventas/{id}/lineas")
    suspend fun reemplazarLineas(
        @Path("id") id: String,
        @Body body: ReemplazarLineasRequest
    ): VentaSituacionDTO

    /**
     * Corrige el **header** de una venta ya subida que sigue en `borrador`:
     * dirección, GPS, fecha, plan de crédito, día de cobranza y nota. Requiere
     * el permiso `ventas:editar`.
     *
     * No lleva `Idempotency-Key` ni `If-Match`, igual que [reemplazarLineas]:
     * el cuerpo describe el estado final del header, no un delta, así que
     * reenviarlo es inofensivo mientras la venta siga editable.
     *
     * **No corrige los montos.** El servidor acepta un campo `montos` y lo
     * ignora: los deriva de las líneas. Para cambiar el total hay que llamar
     * [reemplazarLineas]. Por eso [ActualizarHeaderRequest] ni siquiera declara
     * el campo.
     *
     * La respuesta real es el `VentaDTO` completo; aquí se lee sólo
     * [VentaSituacionDTO] y Gson descarta el resto. Un 409 `venta_no_editable`
     * significa que la venta salió de `borrador`: es TERMINAL, no se reintenta.
     * El código de error se saca con `codigoDeErrorHttp`.
     */
    @PATCH("v2/ventas/{id}")
    suspend fun actualizarHeader(
        @Path("id") id: String,
        @Body body: ActualizarHeaderRequest
    ): VentaSituacionDTO

    /**
     * Corrige el **cliente** de una venta ya subida que sigue en `borrador`:
     * nombre, teléfono, aval y el `cliente_id` del padrón. Requiere el permiso
     * `ventas:editar`.
     *
     * Mismas condiciones que [actualizarHeader]: sin `Idempotency-Key` ni
     * `If-Match`, reenviable, 409 `venta_no_editable` TERMINAL, y la respuesta
     * es el `VentaDTO` completo leído como [VentaSituacionDTO].
     *
     * Ojo con `referencia`: el handler la acepta y la DESCARTA en silencio, así
     * que [ClienteRemotoDto] no la declara — un campo que se manda y no se
     * guarda es peor que no tenerlo.
     */
    @PATCH("v2/ventas/{id}/cliente")
    suspend fun actualizarCliente(
        @Path("id") id: String,
        @Body body: ActualizarClienteRequest
    ): VentaSituacionDTO
}
