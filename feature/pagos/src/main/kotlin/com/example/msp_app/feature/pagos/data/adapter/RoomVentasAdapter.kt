package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.SaleWithProductsEntity
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.port.VentasPort
import java.math.BigDecimal

/**
 * Adaptador Room de [VentasPort] sobre [SaleDao.getByClientId] (schema
 * inmutable, no se toca).
 *
 * **Aquí vive la frontera de la REGLA DE DINERO.** Es la única capa de este
 * módulo que ve `Double`/`Int` de Room, y ninguno de los dos la cruza:
 *  - `SALDO_REST` / `PRECIO_TOTAL` / `PRECIO_DE_CONTADO` / `ENGANCHE` son
 *    `Double` → [Money.of] con `Double`, que internamente usa
 *    [BigDecimal.valueOf] (representación decimal, no los bits binarios).
 *  - `PARCIALIDAD` es `Int` → [BigDecimal.valueOf] con el `Long` del entero.
 *    **Nunca** `BigDecimal(double)`: ese constructor arrastra el error binario
 *    del flotante y mete centavos fantasma en un número que era exacto.
 *
 * [SaleDao.getByClientId] agrupa por `DOCTO_CC_ID`, así que devuelve una fila
 * por venta con sus artículos concatenados en `PRODUCTOS`.
 */
class RoomVentasAdapter(
    private val saleDao: SaleDao
) : VentasPort {

    override suspend fun ventasDelCliente(clienteId: Int): List<DatosDeVenta> =
        saleDao.getByClientId(clienteId).map { it.aDatosDeVenta() }

    /**
     * Resuelve una venta por su `DOCTO_CC_ACR_ID` sin conocer al cliente — el
     * camino de entrada "desde un pago se entra directo a su venta" (Task 21).
     * Se pasa por el cliente para reusar la MISMA proyección (con `PRODUCTOS`)
     * que la lista, en vez de una segunda con otras columnas que podría
     * despegarse.
     */
    override suspend fun venta(ventaId: Int): DatosDeVenta? {
        val cabecera = saleDao.getById(ventaId) ?: return null
        return ventasDelCliente(cabecera.CLIENTE_ID).firstOrNull { it.ventaId == ventaId }
    }

    /**
     * La ruta completa, sobre [SaleDao.getAll] — la MISMA proyección
     * (`GROUP BY DOCTO_CC_ID`, una fila por venta con sus artículos
     * concatenados) que ya usa la lectura por cliente, y el MISMO conjunto que
     * lee hoy la pantalla que esta lista reemplaza.
     */
    override suspend fun todasLasVentas(): List<DatosDeVenta> =
        saleDao.getAll().map { it.aDatosDeVenta() }
}

private fun SaleWithProductsEntity.aDatosDeVenta(): DatosDeVenta {
    // Una sola lectura de `FECHA`: el instante crudo ordena y su fecha de negocio
    // se muestra. Parsear dos veces abriría la puerta a que un día no coincidan.
    val instante = AppTime.parseWireFormatOrNull(FECHA)
    return DatosDeVenta(
        ventaId = DOCTO_CC_ACR_ID,
        creditoId = DOCTO_CC_ID,
        folio = FOLIO,
        clienteId = CLIENTE_ID,
        clienteNombre = CLIENTE,
        telefono = TELEFONO,
        direccion = listOf(CALLE, CIUDAD).map(::enUnRenglon)
            .filter { it.isNotBlank() }
            .joinToString(", "),
        // `ESTADO` es la entidad federativa. NO entra a `direccion` —eso cambiaría lo
        // que pintan las pantallas de detalle— pero sí al texto que busca la lista,
        // que es donde lo usaba `SalesScreen.kt:68`, la pantalla que la Task 21
        // retiró y de la que esta lista hereda el criterio. Quien la quiere pegada
        // pide `DatosDeVenta.direccionCompleta`.
        entidad = enUnRenglon(ESTADO),
        zona = ZONA_NOMBRE,
        aval = AVAL_O_RESPONSABLE,
        // No hay columna de teléfono del aval en el schema ni en el DTO de cobranza;
        // ver el KDoc de `DetalleCliente.telefonoAval`. No se sustituye por TELEFONO,
        // que es el del cliente.
        telefonoAval = null,
        notas = NOTAS,
        descripcion = PRODUCTOS.orEmpty(),
        fechaVenta = instante?.let(AppTime::toBusinessDate),
        instanteDeVenta = instante,
        saldo = Money.of(SALDO_REST),
        parcialidad = Money.of(BigDecimal.valueOf(PARCIALIDAD.toLong())),
        // La columna es nullable: sin dato no se afirma un promedio, y `Money.ZERO`
        // sería afirmar que suele dar cero, que es otra cosa.
        pagoPromedio = IMPORTE_PAGO_PROMEDIO?.let(Money::of),
        frecuencia = FREC_PAGO.orEmpty().lowercase(),
        diaDeCobranza = DIA_COBRANZA.trim(),
        diaTemporal = DIA_TEMPORAL_COBRANZA.trim(),
        abonosTotales = NUM_IMPORTES,
        totalVenta = Money.of(PRECIO_TOTAL),
        precioContado = Money.of(PRECIO_DE_CONTADO),
        enganche = Money.of(ENGANCHE),
        mesesACortoPlazo = TIEMPO_A_CORTO_PLAZOMESES,
        montoACortoPlazo = Money.of(MONTO_A_CORTO_PLAZO),
        // Los TRES, no el primero que traiga algo: las columnas vacías se caen aquí
        // para que ninguna pantalla pinte un renglón en blanco. Ver el KDoc de
        // `DatosDeVenta.vendedores`.
        vendedores = listOf(VENDEDOR_1, VENDEDOR_2, VENDEDOR_3)
            .map { it.trim() }
            .filter { it.isNotBlank() },
        // La columna es nullable en el schema: sin dato no se afirma atraso.
        atrasos = NUM_PAGOS_ATRASADOS ?: 0,
        fechaUltimoPago = AppTime.parseWireFormatOrNull(FECHA_ULT_PAGO)
            ?.let(AppTime::toBusinessDate)
    )
}

/**
 * Un pedazo de domicilio en UN solo renglón.
 *
 * `CALLE` y `CIUDAD` se capturan a mano en Microsip y llegan con saltos de
 * línea dentro; un `\n` crudo parte en dos la fila de la ficha y descuadra la
 * pantalla. La regla es la de la pantalla legada, que hacía exactamente esto
 * antes de concatenar (`SaleClientDetailsSection`: `CALLE.replace("\n", " ")`).
 */
private fun enUnRenglon(texto: String): String = texto.replace('\n', ' ').trim()
