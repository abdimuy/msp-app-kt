package com.example.msp_app.features.home.components.homenearbyclientssection

import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.data.models.sale.FrecuenciaPago
import com.example.msp_app.data.models.sale.SaleWithProducts

/**
 * Una fila de `sales` para los tests de esta sección.
 *
 * [cargo] llena **las dos** columnas de id a propósito: en `sales`,
 * `DOCTO_CC_ID` y `DOCTO_CC_ACR_ID` llevan siempre el mismo número, por
 * construcción de los cuatro escritores que existen — lo documenta
 * `SaleIdSpaces`. Un fixture que les pusiera números distintos probaría un
 * estado que la base no puede tener.
 */
fun venta(
    cargo: Int,
    clienteId: Int,
    cliente: String = "Cliente $clienteId",
    calle: String = "Calle $cargo",
    ciudad: String = "Delicias"
) = SaleWithProducts(
    DOCTO_CC_ACR_ID = cargo,
    DOCTO_CC_ID = cargo,
    FOLIO = "MSP-$cargo",
    CLIENTE_ID = clienteId,
    APLICADO = "S",
    COBRADOR_ID = 7,
    CLIENTE = cliente,
    ZONA_CLIENTE_ID = 3,
    LIMITE_CREDITO = 30_000.0,
    NOTAS = "",
    ZONA_NOMBRE = "Zona Centro",
    IMPORTE_PAGO_PROMEDIO = 450.0,
    TOTAL_IMPORTE = 12_000.0,
    NUM_IMPORTES = 24,
    FECHA = "2026-01-15T00:00:00-06:00",
    PARCIALIDAD = 500,
    ENGANCHE = 2_000.0,
    TIEMPO_A_CORTO_PLAZOMESES = 6,
    MONTO_A_CORTO_PLAZO = 10_000.0,
    VENDEDOR_1 = "Ernesto Zúñiga",
    VENDEDOR_2 = "",
    VENDEDOR_3 = "",
    PRECIO_TOTAL = 24_000.0,
    IMPTE_REST = 18_450.0,
    SALDO_REST = 18_450.0,
    FECHA_ULT_PAGO = "2026-08-01T10:30:00-06:00",
    CALLE = calle,
    CIUDAD = ciudad,
    ESTADO = "Chihuahua",
    TELEFONO = "6251234567",
    NOMBRE_COBRADOR = "Gabriel Roque",
    ESTADO_COBRANZA = EstadoCobranza.NO_PAGADO,
    DIA_COBRANZA = "LUNES",
    DIA_TEMPORAL_COBRANZA = "",
    PRECIO_DE_CONTADO = 20_000.0,
    AVAL_O_RESPONSABLE = "Rosa Elena Márquez",
    FREC_PAGO = FrecuenciaPago.SEMANAL,
    PRODUCTOS = "Sala Toscana 3 piezas",
    NUM_PAGOS_ATRASADOS = 2
)
