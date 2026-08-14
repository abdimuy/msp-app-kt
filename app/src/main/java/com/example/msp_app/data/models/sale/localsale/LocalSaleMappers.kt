package com.example.msp_app.data.models.sale.localsale

import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleImageEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.MexicanPhone
import com.example.msp_app.data.api.services.localSales.LocalSaleComboRequest
import com.example.msp_app.data.api.services.localSales.LocalSaleProductRequest
import com.example.msp_app.data.api.services.localSales.LocalSaleRequest
import com.example.msp_app.data.api.services.localSales.LocalSaleUpdateRequest
import com.example.msp_app.data.api.services.ventas.ClienteSnapshotDTO
import com.example.msp_app.data.api.services.ventas.ComboDTO
import com.example.msp_app.data.api.services.ventas.CrearVentaBody
import com.example.msp_app.data.api.services.ventas.DiaCobranzaDTO
import com.example.msp_app.data.api.services.ventas.DireccionDTO
import com.example.msp_app.data.api.services.ventas.GPSDTO
import com.example.msp_app.data.api.services.ventas.MontosDTO
import com.example.msp_app.data.api.services.ventas.PlanCreditoDTO
import com.example.msp_app.data.api.services.ventas.ProductoDTO
import com.example.msp_app.data.api.services.ventas.VendedorDTO
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Normaliza un teléfono a E.164 (`+52##########`) para el body de `POST /v2/ventas`,
 * o devuelve `null` cuando el número no es un mexicano válido de 10 dígitos.
 *
 * **Última línea de defensa del incidente 2026-08-13.** La versión anterior
 * anteponía `+52` a lo que fuera con tal de que hubiera al menos un dígito y
 * dejaba pasar intacta cualquier cadena que empezara con `+`, sin contar nunca
 * los dígitos. Con el teclado numérico de la pantalla eso convirtió un `000000`
 * mal tecleado en `+52000000`, que el API rechaza con `telefono_invalid`; la
 * venta se quedó rebotando en la cola de pendientes un día entero.
 *
 * Devolver `null` en vez de emitir basura es deliberado y asimétrico a favor de
 * que la venta ENTRE: para el servidor el teléfono es opcional en todos los
 * tipos de venta (`optionalTelefono` en `internal/ventas/app/crear_venta.go`),
 * así que "sin teléfono" siempre pasa mientras que "teléfono inválido" siempre
 * rechaza. Esto además desatasca solo las filas viejas que ya viven en Room con
 * teléfonos malos —al reintentarse salen sin teléfono y entran— que es
 * exactamente la corrección que se aplicó a mano para desatorar la venta de
 * Juan Hernández Cruz.
 *
 * La regla de formato vive en [MexicanPhone], compartida con
 * `NewSaleFormValidator`, para que la pantalla de captura y el cable no puedan
 * volver a opinar distinto sobre qué teléfono es válido.
 */
internal fun normalizeTelefonoE164(raw: String): String? = MexicanPhone.toE164OrNull(raw)

// Formatea un monto a exactamente 2 decimales para el API (columna NUMERIC(14,2)).
// Double.toString() puede emitir basura de punto flotante (p. ej. una suma de
// componentes de combo: 300.29999999999995), y el backend rechaza >2 decimales
// con HTTP 422 ("el monto admite máximo 2 decimales"). BigDecimal(this) captura
// el valor exacto del double y setScale lo redondea a 2.
internal fun Double.toMoneyString(): String =
    BigDecimal(this).setScale(2, RoundingMode.HALF_UP).toPlainString()

class LocalSaleMappers {
    fun LocalSale.toEntity(): LocalSaleEntity {
        return LocalSaleEntity(
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            NOMBRE_CLIENTE = this.NOMBRE_CLIENTE,
            FECHA_VENTA = this.FECHA_VENTA,
            LATITUD = this.LATITUD,
            LONGITUD = this.LONGITUD,
            DIRECCION = this.DIRECCION,
            PARCIALIDAD = this.PARCIALIDAD,
            ENGANCHE = this.ENGANCHE,
            TELEFONO = this.TELEFONO,
            FREC_PAGO = this.FREC_PAGO,
            AVAL_O_RESPONSABLE = this.AVAL_O_RESPONSABLE,
            NOTA = this.NOTA,
            DIA_COBRANZA = this.DIA_COBRANZA,
            PRECIO_TOTAL = this.PRECIO_TOTAL,
            TIEMPO_A_CORTO_PLAZOMESES = this.TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO = this.MONTO_A_CORTO_PLAZO,
            MONTO_DE_CONTADO = this.MONTO_DE_CONTADO,
            ENVIADO = this.ENVIADO,
            NUMERO = this.NUMERO,
            COLONIA = this.COLONIA,
            POBLACION = this.POBLACION,
            CIUDAD = this.CIUDAD,
            TIPO_VENTA = this.TIPO_VENTA
        )
    }

    fun LocalSaleEntity.toDomain(): LocalSale {
        return LocalSale(
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            NOMBRE_CLIENTE = this.NOMBRE_CLIENTE,
            FECHA_VENTA = this.FECHA_VENTA,
            LATITUD = this.LATITUD,
            LONGITUD = this.LONGITUD,
            DIRECCION = this.DIRECCION,
            PARCIALIDAD = this.PARCIALIDAD,
            ENGANCHE = this.ENGANCHE,
            TELEFONO = this.TELEFONO,
            FREC_PAGO = this.FREC_PAGO,
            AVAL_O_RESPONSABLE = this.AVAL_O_RESPONSABLE,
            NOTA = this.NOTA,
            DIA_COBRANZA = this.DIA_COBRANZA,
            PRECIO_TOTAL = this.PRECIO_TOTAL,
            TIEMPO_A_CORTO_PLAZOMESES = this.TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO = this.MONTO_A_CORTO_PLAZO,
            MONTO_DE_CONTADO = this.MONTO_DE_CONTADO,
            ENVIADO = this.ENVIADO,
            NUMERO = this.NUMERO,
            COLONIA = this.COLONIA,
            POBLACION = this.POBLACION,
            CIUDAD = this.CIUDAD,
            TIPO_VENTA = this.TIPO_VENTA
        )
    }

    fun LocalSaleImage.toEntity(): LocalSaleImageEntity {
        return LocalSaleImageEntity(
            LOCAL_SALE_IMAGE_ID = this.LOCAL_SALE_IMAGE_ID,
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            IMAGE_URI = this.IMAGE_URI,
            FECHA_SUBIDA = this.FECHA_SUBIDA
        )
    }

    fun LocalSaleImageEntity.toDomain(): LocalSaleImage {
        return LocalSaleImage(
            LOCAL_SALE_IMAGE_ID = this.LOCAL_SALE_IMAGE_ID,
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            IMAGE_URI = this.IMAGE_URI,
            FECHA_SUBIDA = this.FECHA_SUBIDA
        )
    }

    fun LocalSaleProduct.toEntity(): LocalSaleProductEntity {
        return LocalSaleProductEntity(
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            ARTICULO_ID = this.ARTICULO_ID,
            ARTICULO = this.ARTICULO,
            CANTIDAD = this.CANTIDAD,
            PRECIO_LISTA = this.PRECIO_LISTA,
            PRECIO_CORTO_PLAZO = this.PRECIO_CORTO_PLAZO,
            PRECIO_CONTADO = this.PRECIO_CONTADO
        )
    }

    fun LocalSaleProductEntity.toDomain(): LocalSaleProduct {
        return LocalSaleProduct(
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            ARTICULO_ID = this.ARTICULO_ID,
            ARTICULO = this.ARTICULO,
            CANTIDAD = this.CANTIDAD,
            PRECIO_LISTA = this.PRECIO_LISTA,
            PRECIO_CORTO_PLAZO = this.PRECIO_CORTO_PLAZO,
            PRECIO_CONTADO = this.PRECIO_CONTADO
        )
    }

    fun LocalSaleEntity.toServerRequest(
        products: List<LocalSaleProductEntity>,
        userEmail: String,
        combos: List<LocalSaleComboEntity> = emptyList()
    ): LocalSaleRequest {
        return LocalSaleRequest(
            localSaleId = this.LOCAL_SALE_ID,
            userEmail = userEmail,
            nombreCliente = this.NOMBRE_CLIENTE,
            fechaVenta = this.FECHA_VENTA,
            latitud = this.LATITUD,
            longitud = this.LONGITUD,
            direccion = this.DIRECCION,
            parcialidad = this.PARCIALIDAD,
            enganche = this.ENGANCHE,
            telefono = this.TELEFONO,
            frecPago = this.FREC_PAGO,
            avalOResponsable = this.AVAL_O_RESPONSABLE,
            nota = this.NOTA,
            diaCobranza = this.DIA_COBRANZA,
            precioTotal = this.PRECIO_TOTAL,
            tiempoACortoPlazoMeses = this.TIEMPO_A_CORTO_PLAZOMESES,
            montoACortoPlazo = this.MONTO_A_CORTO_PLAZO,
            montoDeContado = this.MONTO_DE_CONTADO,
            productos = products.map { it.toServerRequest() },
            numero = this.NUMERO,
            colonia = this.COLONIA,
            poblacion = this.POBLACION,
            ciudad = this.CIUDAD,
            tipoVenta = this.TIPO_VENTA,
            zonaClienteId = this.ZONA_CLIENTE_ID,
            zonaCliente = this.ZONA_CLIENTE,
            combos = combos.takeIf { it.isNotEmpty() }?.map { it.toServerRequest() }
        )
    }

    fun LocalSaleProductEntity.toServerRequest(): LocalSaleProductRequest {
        return LocalSaleProductRequest(
            articuloId = this.ARTICULO_ID,
            articulo = this.ARTICULO,
            cantidad = this.CANTIDAD,
            precioLista = this.PRECIO_LISTA,
            precioCortoPlazo = this.PRECIO_CORTO_PLAZO,
            precioContado = this.PRECIO_CONTADO,
            comboId = this.COMBO_ID
        )
    }

    fun LocalSaleComboEntity.toServerRequest(): LocalSaleComboRequest {
        return LocalSaleComboRequest(
            comboId = this.COMBO_ID,
            nombreCombo = this.NOMBRE_COMBO,
            precioLista = this.PRECIO_LISTA,
            precioCortoPlazo = this.PRECIO_CORTO_PLAZO,
            precioContado = this.PRECIO_CONTADO
        )
    }

    /**
     * Maps Room entities to the V2 Go API request body for POST /v2/ventas.
     *
     * Known approximations (verify with QA):
     * - Combo `cantidad` is hardcoded to "1" — local catalog does not store
     *   decimal quantity per combo.
     * - `almacen_origen_id` uses [camionetaId] when set, otherwise falls back
     *   to [Constants.ALMACEN_GENERAL_ID].
     * - `almacen_destino_id` always uses [Constants.ALMACEN_GENERAL_ID].
     * - Products inside a combo send null almacen ids (the combo carries them).
     * - `dia_cobranza.semana` only — the UI captures weekday names, not day-of-month.
     * - `cliente.referencia` is always null (no current UI capture).
     */
    fun LocalSaleEntity.toV2VentaBody(
        products: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity>,
        vendedores: List<VendedorDTO>,
        camionetaId: Int?
    ): CrearVentaBody {
        val almacenOrigen = camionetaId ?: Constants.ALMACEN_GENERAL_ID
        val almacenDestino = Constants.ALMACEN_GENERAL_ID

        val combosDtos = combos.map { c ->
            val cid = c.SERVER_UUID ?: UUID.randomUUID().toString()
            ComboDTO(
                id = cid,
                nombre = c.NOMBRE_COMBO,
                precio_anual = c.PRECIO_LISTA.toMoneyString(),
                precio_corto = c.PRECIO_CORTO_PLAZO.toMoneyString(),
                precio_contado = c.PRECIO_CONTADO.toMoneyString(),
                cantidad = "1",
                almacen_origen_id = almacenOrigen,
                almacen_destino_id = almacenDestino
            )
        }
        val comboIdByCatalog = combos.zip(combosDtos).associate { (entity, dto) ->
            entity.COMBO_ID to dto.id
        }

        val productosDtos = products.map { p ->
            val isInCombo = p.COMBO_ID != null
            ProductoDTO(
                id = p.SERVER_UUID ?: UUID.randomUUID().toString(),
                articulo_id = p.ARTICULO_ID,
                articulo = p.ARTICULO,
                cantidad = p.CANTIDAD.toString(),
                precio_anual = p.PRECIO_LISTA.toMoneyString(),
                precio_corto = p.PRECIO_CORTO_PLAZO.toMoneyString(),
                precio_contado = p.PRECIO_CONTADO.toMoneyString(),
                combo_id = p.COMBO_ID?.let { comboIdByCatalog[it] },
                almacen_origen_id = if (isInCombo) null else almacenOrigen,
                almacen_destino_id = if (isInCombo) null else almacenDestino
            )
        }

        val isCredito = this.TIPO_VENTA == "CREDITO"

        return CrearVentaBody(
            id = this.LOCAL_SALE_ID,
            cliente = ClienteSnapshotDTO(
                cliente_id = this.CLIENTE_ID,
                nombre = this.NOMBRE_CLIENTE,
                telefono = normalizeTelefonoE164(this.TELEFONO),
                aval = this.AVAL_O_RESPONSABLE?.takeIf { it.isNotBlank() },
                referencia = null
            ),
            direccion = DireccionDTO(
                calle = this.DIRECCION,
                numero_exterior = this.NUMERO,
                colonia = this.COLONIA ?: "",
                poblacion = this.POBLACION ?: "",
                ciudad = this.CIUDAD ?: "",
                zona_cliente_id = this.ZONA_CLIENTE_ID
            ),
            gps = GPSDTO(latitud = this.LATITUD, longitud = this.LONGITUD),
            fecha_venta = this.FECHA_VENTA,
            tipo_venta = this.TIPO_VENTA ?: "CONTADO",
            montos = MontosDTO(
                anual = this.PRECIO_TOTAL.toMoneyString(),
                corto_plazo = this.MONTO_A_CORTO_PLAZO.toMoneyString(),
                contado = this.MONTO_DE_CONTADO.toMoneyString()
            ),
            plan_credito = if (isCredito) {
                PlanCreditoDTO(
                    plazo_meses = this.TIEMPO_A_CORTO_PLAZOMESES,
                    enganche = (this.ENGANCHE ?: 0.0).toMoneyString(),
                    parcialidad = this.PARCIALIDAD.toMoneyString(),
                    frec_pago = normalizeFrecPago(this.FREC_PAGO)
                )
            } else {
                null
            },
            dia_cobranza = if (isCredito) {
                DiaCobranzaDTO(
                    semana = normalizeDiaCobranza(this.DIA_COBRANZA),
                    mes = null
                )
            } else {
                null
            },
            nota = this.NOTA?.takeIf { it.isNotBlank() },
            combos = combosDtos,
            productos = productosDtos,
            vendedores = vendedores
        )
    }

    private fun normalizeFrecPago(raw: String): String = raw.uppercase()
        .replace("Á", "A").replace("á", "a")
        .replace("É", "E").replace("é", "e")
        .replace("Í", "I").replace("í", "i")
        .replace("Ó", "O").replace("ó", "o")
        .replace("Ú", "U").replace("ú", "u")

    private fun normalizeDiaCobranza(raw: String): String = raw.uppercase()
        .replace("Á", "A").replace("á", "a")
        .replace("É", "E").replace("é", "e")
        .replace("Í", "I").replace("í", "i")
        .replace("Ó", "O").replace("ó", "o")
        .replace("Ú", "U").replace("ú", "u")

    fun LocalSaleEntity.toUpdateRequest(
        products: List<LocalSaleProductEntity>,
        userEmail: String,
        imagenesAEliminar: List<String> = emptyList(),
        almacenOrigenId: Int? = null,
        almacenDestinoId: Int? = null,
        combos: List<LocalSaleComboEntity> = emptyList()
    ): LocalSaleUpdateRequest {
        return LocalSaleUpdateRequest(
            userEmail = userEmail,
            nombreCliente = this.NOMBRE_CLIENTE,
            fechaVenta = this.FECHA_VENTA,
            latitud = this.LATITUD,
            longitud = this.LONGITUD,
            direccion = this.DIRECCION,
            parcialidad = this.PARCIALIDAD,
            enganche = this.ENGANCHE,
            telefono = this.TELEFONO,
            frecPago = this.FREC_PAGO,
            avalOResponsable = this.AVAL_O_RESPONSABLE,
            nota = this.NOTA,
            diaCobranza = this.DIA_COBRANZA,
            precioTotal = this.PRECIO_TOTAL,
            tiempoACortoPlazoMeses = this.TIEMPO_A_CORTO_PLAZOMESES,
            montoACortoPlazo = this.MONTO_A_CORTO_PLAZO,
            productos = products.map { it.toServerRequest() },
            numero = this.NUMERO,
            colonia = this.COLONIA,
            poblacion = this.POBLACION,
            ciudad = this.CIUDAD,
            tipoVenta = this.TIPO_VENTA,
            zonaClienteId = this.ZONA_CLIENTE_ID,
            almacenOrigenId = almacenOrigenId,
            almacenDestinoId = almacenDestinoId,
            imagenesAEliminar = imagenesAEliminar,
            combos = combos.takeIf { it.isNotEmpty() }?.map { it.toServerRequest() }
        )
    }
}
