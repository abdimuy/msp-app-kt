package com.example.msp_app.data.api.services.ventas

import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.data.models.sale.localsale.normalizadoParaCatalogo
import com.example.msp_app.data.models.sale.localsale.normalizeTelefonoE164
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import retrofit2.HttpException

// ─── Request body de PUT /v2/ventas/{id}/lineas ─────────────────────────────

/**
 * Cuerpo de `PUT /v2/ventas/{id}/lineas`: **reemplazo total** de las líneas de
 * una venta ya subida que sigue en `borrador`.
 *
 * El endpoint NO acepta `Idempotency-Key` ni `If-Match`: lo que se manda es el
 * conjunto completo de líneas que debe quedar, no un delta. Reenviar el mismo
 * cuerpo es inofensivo mientras la venta siga editable; en cuanto sale de
 * `borrador` el servidor responde 409 `venta_no_editable` y ese error es
 * TERMINAL (reintentarlo sólo gasta batería).
 *
 * [productos] nunca puede ir vacío (`minItems:1` en el servidor); [combos] sí.
 */
data class ReemplazarLineasRequest(
    @SerializedName("combos") val combos: List<ComboRemotoDto>,
    @SerializedName("productos") val productos: List<ProductoRemotoDto>
)

/**
 * Combo tal como lo espera el servidor. **Todos los montos y la cantidad viajan
 * como String** y son precios UNITARIOS: el servidor multiplica por la cantidad.
 */
data class ComboRemotoDto(
    @SerializedName("id") val id: String,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("precio_anual") val precioAnual: String,
    @SerializedName("precio_corto") val precioCorto: String,
    @SerializedName("precio_contado") val precioContado: String,
    @SerializedName("cantidad") val cantidad: String,
    @SerializedName("almacen_origen_id") val almacenOrigenId: Int,
    @SerializedName("almacen_destino_id") val almacenDestinoId: Int
)

/**
 * Producto tal como lo espera el servidor. Igual que [ComboRemotoDto], montos y
 * cantidad son String y los precios son unitarios.
 *
 * [comboId] apunta al `id` (UUID) del combo dentro de ESTE mismo cuerpo, no al
 * id de catálogo local; si no coincide, el servidor responde 422
 * `producto_combo_referencia_invalida`. Un producto que pertenece a un combo
 * manda los almacenes en `null`: los aporta el combo.
 */
data class ProductoRemotoDto(
    @SerializedName("id") val id: String,
    @SerializedName("articulo_id") val articuloId: Int,
    @SerializedName("articulo") val articulo: String,
    @SerializedName("cantidad") val cantidad: String,
    @SerializedName("precio_anual") val precioAnual: String,
    @SerializedName("precio_corto") val precioCorto: String,
    @SerializedName("precio_contado") val precioContado: String,
    @SerializedName("combo_id") val comboId: String? = null,
    @SerializedName("almacen_origen_id") val almacenOrigenId: Int? = null,
    @SerializedName("almacen_destino_id") val almacenDestinoId: Int? = null
)

// ─── Request body de PATCH /v2/ventas/{id} ──────────────────────────────────

/**
 * Cuerpo de `PATCH /v2/ventas/{id}`: el "header" de una venta ya subida que
 * sigue en `borrador` — dirección, GPS, fecha, plan de crédito, día de cobranza
 * y nota.
 *
 * Misma guarda y mismos errores que [ReemplazarLineasRequest]: `estado=active` y
 * `situacion=borrador`, permiso `ventas:editar`, 409 `venta_no_editable`
 * TERMINAL cuando la venta salió de borrador. Tampoco acepta `Idempotency-Key`
 * ni `If-Match`.
 *
 * **No existe campo `montos` a propósito.** El servidor acepta el campo pero lo
 * IGNORA: los montos se derivan de las líneas (`PUT /v2/ventas/{id}/lineas`).
 * Declararlo aquí sólo serviría para que alguien creyera que corrigiendo el
 * header se corrige el total. No se declara, así que no se puede mandar.
 *
 * [planCredito] y [diaCobranza] son `null` en ventas de contado, igual que en el
 * alta (`LocalSaleMappers.toV2VentaBody`): Gson omite los nulos, así que el
 * `omitempty` del servidor se cumple sin trabajo extra.
 */
data class ActualizarHeaderRequest(
    @SerializedName("direccion") val direccion: DireccionRemotaDto,
    @SerializedName("gps") val gps: GpsRemotoDto,
    @SerializedName("fecha_venta") val fechaVenta: String,
    @SerializedName("plan_credito") val planCredito: PlanCreditoRemotoDto? = null,
    @SerializedName("dia_cobranza") val diaCobranza: DiaCobranzaRemotoDto? = null,
    @SerializedName("nota") val nota: String? = null
)

/**
 * Dirección tal como la espera el servidor.
 *
 * **No existe campo `zona_cliente`.** El servidor lo declara pero es de sólo
 * lectura: lo deriva de `zona_cliente_id` y descarta lo que venga. Room guarda
 * una columna `ZONA_CLIENTE` con el nombre; mandarla sería ruido que además
 * invita a editarla creyendo que cambia algo.
 */
data class DireccionRemotaDto(
    @SerializedName("calle") val calle: String,
    @SerializedName("numero_exterior") val numeroExterior: String? = null,
    @SerializedName("colonia") val colonia: String,
    @SerializedName("poblacion") val poblacion: String,
    @SerializedName("ciudad") val ciudad: String,
    @SerializedName("zona_cliente_id") val zonaClienteId: Int? = null
)

/**
 * GPS de la venta. El servidor valida rangos (`-90..90` y `-180..180`), no
 * presencia: un `0.0/0.0` —lo que Room guarda cuando el teléfono no fijó
 * posición— pasa la validación tal cual, igual que en el alta.
 */
data class GpsRemotoDto(
    @SerializedName("latitud") val latitud: Double,
    @SerializedName("longitud") val longitud: Double
)

/**
 * Plan de crédito. **Enganche y parcialidad viajan como String** con 2
 * decimales (ver [toImporteRemoto]); [plazoMeses] es el único numérico.
 *
 * `plazo_meses = 0` significa para el servidor "usa el valor por omisión", no
 * "cero meses". La app de campo no captura el plazo del plan anual: lo que
 * manda es `TIEMPO_A_CORTO_PLAZOMESES`, exactamente como el alta.
 *
 * [frecPago] debe ser `SEMANAL`, `QUINCENAL` o `MENSUAL`.
 */
data class PlanCreditoRemotoDto(
    @SerializedName("plazo_meses") val plazoMeses: Int,
    @SerializedName("enganche") val enganche: String,
    @SerializedName("parcialidad") val parcialidad: String,
    @SerializedName("frec_pago") val frecPago: String
)

/**
 * Día de cobranza: **exactamente uno** de [semana] (`LUNES`..`DOMINGO`) o [mes]
 * (`1..31`). Mandar los dos, o ninguno, es 422.
 *
 * Room tiene una sola columna `DIA_COBRANZA` y guarda ahí el nombre del día de
 * la semana —la pantalla de captura no ofrece día del mes—, así que este cliente
 * siempre llena [semana] y deja [mes] en `null`, que Gson omite. Es la misma
 * decisión del alta.
 */
data class DiaCobranzaRemotoDto(
    @SerializedName("semana") val semana: String? = null,
    @SerializedName("mes") val mes: Int? = null
)

// ─── Request body de PATCH /v2/ventas/{id}/cliente ──────────────────────────

/**
 * Cuerpo de `PATCH /v2/ventas/{id}/cliente`. Misma guarda, mismo permiso y
 * mismos errores que [ActualizarHeaderRequest].
 */
data class ActualizarClienteRequest(
    @SerializedName("cliente") val cliente: ClienteRemotoDto
)

/**
 * Snapshot del cliente de la venta.
 *
 * [nombre] es obligatorio. [clienteId] es el id del padrón de Microsip cuando la
 * venta quedó vinculada a un cliente existente; `null` cuando es un cliente
 * nuevo capturado a mano.
 *
 * [telefono] se manda ya normalizado a E.164 por `normalizeTelefonoE164` (el
 * servidor también normaliza, pero un número inválido lo rechaza con
 * `telefono_invalid` y tumbaría la corrección entera; mandar `null` siempre
 * pasa).
 *
 * **No existe campo `referencia`.** El handler lo acepta y lo DESCARTA en
 * silencio: mandarlo daría la impresión de que se guardó. El alta tampoco lo
 * manda.
 */
data class ClienteRemotoDto(
    @SerializedName("cliente_id") val clienteId: Int? = null,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("telefono") val telefono: String? = null,
    @SerializedName("aval") val aval: String? = null
)

// ─── Respuesta ──────────────────────────────────────────────────────────────

/**
 * Vista mínima del `VentaDTO` que devuelve el 200. El servidor manda la venta
 * completa; aquí sólo se declara lo que el cliente usa para decidir
 * (`situacion` para saber si sigue editable, `sincronizacion` para saber si ya
 * viajó a Microsip). Gson ignora en silencio todo lo demás, así que agregar
 * campos en el servidor no rompe esta clase.
 */
data class VentaSituacionDTO(
    @SerializedName("situacion") val situacion: String,
    @SerializedName("sincronizacion") val sincronizacion: String
)

// ─── Código de error ────────────────────────────────────────────────────────

/**
 * Saca el código de error de negocio de un [HttpException] del API v2, o
 * `null` si el cuerpo no trae ninguno.
 *
 * **La trampa:** Huma NO publica el código en un campo `code` del JSON. Lo
 * entierra en el texto de los detalles, con la forma `code=<codigo>`:
 *
 * ```json
 * {"title":"...","status":409,"detail":"...",
 *  "errors":[{"message":"code=venta_no_editable"}]}
 * ```
 *
 * Leer `obj["code"]` (como hace `PendingLocalSalesWorker.parseProblemDetails`)
 * devuelve `null` siempre contra este endpoint.
 *
 * Es seguro llamarla dos veces sobre la misma excepción: Retrofit almacena el
 * cuerpo de error en memoria antes de construir el [HttpException]. Cualquier
 * fallo de lectura o de parseo se traduce a `null` — un código ausente nunca
 * debe tumbar el manejo del error que lo contiene.
 */
fun codigoDeErrorHttp(e: HttpException): String? {
    val cuerpo = try {
        e.response()?.errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    return extraerCodigoDeError(cuerpo)
}

private val PATRON_CODIGO = Regex("""code=([A-Za-z0-9_.-]+)""")

/**
 * Parte pura de [codigoDeErrorHttp], separada para poder probarla sin armar un
 * [HttpException]. Busca `code=...` primero en cada `errors[].message` y, si
 * ahí no aparece, en `detail` — algunos errores de Huma sólo llenan ese campo.
 */
internal fun extraerCodigoDeError(cuerpo: String?): String? {
    if (cuerpo.isNullOrBlank()) return null
    val obj = try {
        JsonParser.parseString(cuerpo).takeIf { it.isJsonObject }?.asJsonObject
    } catch (_: Exception) {
        null
    } ?: return null

    val errores = obj["errors"]?.takeIf { it.isJsonArray }?.asJsonArray
    errores?.forEach { elemento ->
        val mensaje = elemento.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("message")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
        PATRON_CODIGO.find(mensaje.orEmpty())?.let { return it.groupValues[1] }
    }

    val detalle = obj["detail"]?.takeIf { it.isJsonPrimitive }?.asString
    return PATRON_CODIGO.find(detalle.orEmpty())?.groupValues?.get(1)
}

// ─── Mapeo desde Room ───────────────────────────────────────────────────────

/**
 * Formatea un monto de Room (Double) a la cadena de 2 decimales que espera el
 * servidor (columna `NUMERIC(14,2)`).
 *
 * `BigDecimal.toPlainString()` es independiente del locale: este teléfono está
 * en es-MX y `String.format` con el locale por omisión emitiría `6500,00`, que
 * el API rechaza. Tampoco produce notación científica ni separador de miles.
 * `BigDecimal(double)` toma el valor exacto del double, así que la basura de
 * punto flotante que arrastran las sumas de componentes de combo
 * (300.29999999999995) se redondea aquí y no llega como 422 "máximo 2
 * decimales".
 */
internal fun Double.toImporteRemoto(): String =
    BigDecimal(this).setScale(2, RoundingMode.HALF_UP).toPlainString()

/**
 * Arma el cuerpo completo de reemplazo a partir de las líneas que hoy viven en
 * Room.
 *
 * El `id` de cada línea es su `SERVER_UUID` cuando ya se conoce y un UUID nuevo
 * cuando no —el reemplazo es total, así que el servidor acepta ids nuevos— y el
 * `combo_id` de cada producto se reescribe al UUID remoto de su combo, no al id
 * de catálogo local que guarda Room.
 *
 * Los productos que pertenecen a un combo mandan almacenes en `null`; los
 * sueltos, [almacenOrigenId] / [almacenDestinoId].
 */
fun construirReemplazarLineasRequest(
    productos: List<LocalSaleProductEntity>,
    combos: List<LocalSaleComboEntity>,
    almacenOrigenId: Int,
    almacenDestinoId: Int
): ReemplazarLineasRequest {
    val combosDto = combos.map { it.toComboRemotoDto(almacenOrigenId, almacenDestinoId) }

    // COMBO_ID (catálogo local) → id remoto del combo en ESTE cuerpo.
    val idRemotoPorCombo = combos.zip(combosDto).associate { (entidad, dto) ->
        entidad.COMBO_ID to dto.id
    }

    val productosDto = productos.map { producto ->
        producto.toProductoRemotoDto(
            comboIdRemoto = producto.COMBO_ID?.let { idRemotoPorCombo[it] },
            almacenOrigenId = almacenOrigenId,
            almacenDestinoId = almacenDestinoId
        )
    }

    return ReemplazarLineasRequest(combos = combosDto, productos = productosDto)
}

/**
 * `local_sale_combos` no guarda cantidad por combo, así que viaja siempre "1":
 * la cantidad real está en los productos que lo componen. Misma aproximación
 * que el `POST /v2/ventas`.
 */
fun LocalSaleComboEntity.toComboRemotoDto(
    almacenOrigenId: Int,
    almacenDestinoId: Int
): ComboRemotoDto = ComboRemotoDto(
    id = this.SERVER_UUID ?: UUID.randomUUID().toString(),
    nombre = this.NOMBRE_COMBO,
    precioAnual = this.PRECIO_LISTA.toImporteRemoto(),
    precioCorto = this.PRECIO_CORTO_PLAZO.toImporteRemoto(),
    precioContado = this.PRECIO_CONTADO.toImporteRemoto(),
    cantidad = "1",
    almacenOrigenId = almacenOrigenId,
    almacenDestinoId = almacenDestinoId
)

/**
 * @param comboIdRemoto el `id` del combo dentro de este mismo cuerpo, o `null`
 *   si el producto va suelto. Se pasa desde fuera porque el `COMBO_ID` de Room
 *   es el id de catálogo local y el servidor no lo conoce.
 */
fun LocalSaleProductEntity.toProductoRemotoDto(
    comboIdRemoto: String?,
    almacenOrigenId: Int,
    almacenDestinoId: Int
): ProductoRemotoDto {
    val perteneceACombo = this.COMBO_ID != null
    return ProductoRemotoDto(
        id = this.SERVER_UUID ?: UUID.randomUUID().toString(),
        articuloId = this.ARTICULO_ID,
        articulo = this.ARTICULO,
        cantidad = this.CANTIDAD.toString(),
        precioAnual = this.PRECIO_LISTA.toImporteRemoto(),
        precioCorto = this.PRECIO_CORTO_PLAZO.toImporteRemoto(),
        precioContado = this.PRECIO_CONTADO.toImporteRemoto(),
        comboId = comboIdRemoto,
        almacenOrigenId = if (perteneceACombo) null else almacenOrigenId,
        almacenDestinoId = if (perteneceACombo) null else almacenDestinoId
    )
}

/**
 * Arma el cuerpo de `PATCH /v2/ventas/{id}` a partir de la fila de Room.
 *
 * **Es el mismo mapeo que el alta** (`LocalSaleMappers.toV2VentaBody`), columna
 * por columna, y esa equivalencia es el punto: si el alta y la corrección
 * armaran el header distinto, corregir la dirección de una venta le cambiaría en
 * silencio campos que nadie tocó. Lo único que cambia es lo que el endpoint no
 * acepta —`montos`, `zona_cliente`— y que aquí no existe.
 *
 * Lo que hereda del alta y conviene tener presente:
 * - `plan_credito` y `dia_cobranza` sólo viajan cuando `TIPO_VENTA == "CREDITO"`;
 *   en contado van en `null` y Gson los omite.
 * - `plazo_meses` sale de `TIEMPO_A_CORTO_PLAZOMESES` (la app no captura el
 *   plazo del plan anual).
 * - `colonia`, `poblacion` y `ciudad` son obligatorias en el servidor y
 *   NULLABLE en Room; una fila sin ellas viaja con cadena vacía. Es el mismo
 *   relleno del alta, y por eso una venta ya subida no puede tenerlas en null:
 *   habría fallado al crearse.
 */
fun construirActualizarHeaderRequest(sale: LocalSaleEntity): ActualizarHeaderRequest {
    val esCredito = sale.TIPO_VENTA == "CREDITO"

    return ActualizarHeaderRequest(
        direccion = DireccionRemotaDto(
            calle = sale.DIRECCION,
            numeroExterior = sale.NUMERO,
            colonia = sale.COLONIA ?: "",
            poblacion = sale.POBLACION ?: "",
            ciudad = sale.CIUDAD ?: "",
            zonaClienteId = sale.ZONA_CLIENTE_ID
        ),
        gps = GpsRemotoDto(latitud = sale.LATITUD, longitud = sale.LONGITUD),
        fechaVenta = sale.FECHA_VENTA,
        planCredito = if (esCredito) {
            PlanCreditoRemotoDto(
                plazoMeses = sale.TIEMPO_A_CORTO_PLAZOMESES,
                enganche = (sale.ENGANCHE ?: 0.0).toImporteRemoto(),
                parcialidad = sale.PARCIALIDAD.toImporteRemoto(),
                frecPago = sale.FREC_PAGO.normalizadoParaCatalogo()
            )
        } else {
            null
        },
        diaCobranza = if (esCredito) {
            DiaCobranzaRemotoDto(
                semana = sale.DIA_COBRANZA.normalizadoParaCatalogo(),
                mes = null
            )
        } else {
            null
        },
        nota = sale.NOTA?.takeIf { it.isNotBlank() }
    )
}

/**
 * Arma el cuerpo de `PATCH /v2/ventas/{id}/cliente` a partir de la fila de Room.
 *
 * Mismo criterio que [construirActualizarHeaderRequest]: réplica del bloque
 * `cliente` de `LocalSaleMappers.toV2VentaBody`, incluido el teléfono que pasa
 * por `normalizeTelefonoE164` y sale en `null` cuando no es un móvil mexicano de
 * 10 dígitos —asimétrico a propósito: el servidor acepta "sin teléfono" y
 * rechaza "teléfono inválido", así que un número malo en Room no puede tumbar la
 * corrección entera (incidente 2026-08-13)— y el aval en blanco que viaja como
 * `null` en vez de como cadena vacía.
 *
 * `referencia` no se manda: el handler la acepta y la descarta.
 */
fun construirActualizarClienteRequest(sale: LocalSaleEntity): ActualizarClienteRequest =
    ActualizarClienteRequest(
        cliente = ClienteRemotoDto(
            clienteId = sale.CLIENTE_ID,
            nombre = sale.NOMBRE_CLIENTE,
            telefono = normalizeTelefonoE164(sale.TELEFONO),
            aval = sale.AVAL_O_RESPONSABLE?.takeIf { it.isNotBlank() }
        )
    )
