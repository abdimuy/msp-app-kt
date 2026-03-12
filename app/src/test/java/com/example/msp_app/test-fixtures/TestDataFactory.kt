package com.example.msp_app.`test-fixtures`

import com.example.msp_app.core.draft.SaleDraft
import com.example.msp_app.data.local.entities.LocalSaleComboEntity
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleImageEntity
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.data.models.productInventory.ProductInventory

object TestDataFactory {

    const val VALID_PRICES_STRING =
        "Precio de lista:1500.0, Precio 4 Meses:1200.0, Precio 1 Meses:1000.0"

    fun createLocalSaleEntity(
        saleId: String = "sale-001",
        clientName: String = "Juan Perez",
        fechaVenta: String = "2026-03-06T12:00:00Z",
        latitude: Double = 19.432608,
        longitude: Double = -99.133209,
        direccion: String = "Calle Principal 123",
        parcialidad: Double = 500.0,
        enganche: Double? = 200.0,
        telefono: String = "5512345678",
        frecPago: String = "SEMANAL",
        avalOResponsable: String? = "Maria Lopez",
        nota: String? = null,
        diaCobranza: String = "LUNES",
        precioTotal: Double = 5000.0,
        tiempoACortoPlazoMeses: Int = 4,
        montoACortoPlazo: Double = 4500.0,
        montoDeContado: Double = 4000.0,
        enviado: Boolean = false,
        numero: String? = "123",
        colonia: String? = "Centro",
        poblacion: String? = "CDMX",
        ciudad: String? = "CDMX",
        tipoVenta: String? = "CREDITO",
        zonaClienteId: Int? = 1,
        zonaCliente: String? = "Zona Norte",
        clienteId: Int? = null
    ) = LocalSaleEntity(
        LOCAL_SALE_ID = saleId,
        NOMBRE_CLIENTE = clientName,
        FECHA_VENTA = fechaVenta,
        LATITUD = latitude,
        LONGITUD = longitude,
        DIRECCION = direccion,
        PARCIALIDAD = parcialidad,
        ENGANCHE = enganche,
        TELEFONO = telefono,
        FREC_PAGO = frecPago,
        AVAL_O_RESPONSABLE = avalOResponsable,
        NOTA = nota,
        DIA_COBRANZA = diaCobranza,
        PRECIO_TOTAL = precioTotal,
        TIEMPO_A_CORTO_PLAZOMESES = tiempoACortoPlazoMeses,
        MONTO_A_CORTO_PLAZO = montoACortoPlazo,
        MONTO_DE_CONTADO = montoDeContado,
        ENVIADO = enviado,
        NUMERO = numero,
        COLONIA = colonia,
        POBLACION = poblacion,
        CIUDAD = ciudad,
        TIPO_VENTA = tipoVenta,
        ZONA_CLIENTE_ID = zonaClienteId,
        ZONA_CLIENTE = zonaCliente,
        CLIENTE_ID = clienteId
    )

    fun createLocalSaleProductEntity(
        saleId: String = "sale-001",
        articuloId: Int = 100,
        articulo: String = "Colchon King",
        cantidad: Int = 1,
        precioLista: Double = 1500.0,
        precioCortoplazo: Double = 1200.0,
        precioContado: Double = 1000.0,
        comboId: String? = null
    ) = LocalSaleProductEntity(
        LOCAL_SALE_ID = saleId,
        ARTICULO_ID = articuloId,
        ARTICULO = articulo,
        CANTIDAD = cantidad,
        PRECIO_LISTA = precioLista,
        PRECIO_CORTO_PLAZO = precioCortoplazo,
        PRECIO_CONTADO = precioContado,
        COMBO_ID = comboId
    )

    fun createLocalSaleComboEntity(
        comboId: String = "combo-001",
        saleId: String = "sale-001",
        nombreCombo: String = "Combo Recamara",
        precioLista: Double = 5000.0,
        precioCortoplazo: Double = 4500.0,
        precioContado: Double = 4000.0
    ) = LocalSaleComboEntity(
        COMBO_ID = comboId,
        LOCAL_SALE_ID = saleId,
        NOMBRE_COMBO = nombreCombo,
        PRECIO_LISTA = precioLista,
        PRECIO_CORTO_PLAZO = precioCortoplazo,
        PRECIO_CONTADO = precioContado
    )

    fun createLocalSaleImageEntity(
        imageId: String = "img-001",
        saleId: String = "sale-001",
        imageUri: String = "content://images/img1.jpg",
        fechaSubida: String = "2026-03-06T12:00:00Z"
    ) = LocalSaleImageEntity(
        LOCAL_SALE_IMAGE_ID = imageId,
        LOCAL_SALE_ID = saleId,
        IMAGE_URI = imageUri,
        FECHA_SUBIDA = fechaSubida
    )

    fun createSaleDraft(
        clientName: String = "Juan Perez",
        phone: String = "5512345678",
        street: String = "Calle Principal",
        numero: String = "123",
        colonia: String = "Centro",
        poblacion: String = "CDMX",
        ciudad: String = "CDMX",
        tipoVenta: String = "CREDITO",
        downpayment: String = "200",
        installment: String = "500",
        guarantor: String = "Maria",
        note: String = "",
        collectionDay: String = "LUNES",
        paymentFrequency: String = "SEMANAL",
        latitude: Double = 19.432608,
        longitude: Double = -99.133209,
        imageUris: List<String> = emptyList(),
        productsJson: String = "",
        combosJson: String = "",
        zonaClienteId: Int? = 1,
        zonaClienteNombre: String = "Zona Norte",
        timestamp: Long = System.currentTimeMillis()
    ) = SaleDraft(
        clientName = clientName,
        phone = phone,
        street = street,
        numero = numero,
        colonia = colonia,
        poblacion = poblacion,
        ciudad = ciudad,
        tipoVenta = tipoVenta,
        downpayment = downpayment,
        installment = installment,
        guarantor = guarantor,
        note = note,
        collectionDay = collectionDay,
        paymentFrequency = paymentFrequency,
        latitude = latitude,
        longitude = longitude,
        imageUris = imageUris,
        productsJson = productsJson,
        combosJson = combosJson,
        zonaClienteId = zonaClienteId,
        zonaClienteNombre = zonaClienteNombre,
        timestamp = timestamp
    )

    fun createProductInventory(
        id: Int = 100,
        name: String = "Colchon King",
        stock: Int = 10,
        prices: String? = VALID_PRICES_STRING,
        lineaArticuloId: Int = 1,
        lineaArticulo: String = "Colchones"
    ) = ProductInventory(
        ARTICULO_ID = id,
        ARTICULO = name,
        EXISTENCIAS = stock,
        LINEA_ARTICULO_ID = lineaArticuloId,
        LINEA_ARTICULO = lineaArticulo,
        PRECIOS = prices
    )
}
