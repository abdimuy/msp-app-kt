package com.example.msp_app.features.sales.viewmodels

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class NewSaleFormValidatorTest {

    // --- Casos EXACTOS del incidente del 2026-08-13 ---
    //
    // Nueve ventas se quedaron atoradas todo el día en la cola de pendientes.
    // Siete traían campos que el servidor rechaza y esta pantalla dejó capturar.
    // Cada `@Test` de este bloque reproduce uno de esos datos reales.

    @Test
    fun `incidente - ciudad vacia bloquea la venta`() {
        // 6 de las 9 ventas atoradas iban con ciudad vacía (`ciudad_required`).
        assertFalse(NewSaleFormValidator.validateCiudad(""))
        val errors = NewSaleFormValidator.validateAll(
            ventaCompleta(ciudad = ""),
            hasProducts = true
        )
        assertTrue(errors.ciudad)
        assertTrue(errors.hasAny)
    }

    @Test
    fun `incidente - colonia vacia bloquea la venta`() {
        // 4 de las 9 ventas atoradas iban con colonia vacía (`colonia_required`).
        assertFalse(NewSaleFormValidator.validateColonia(""))
        val errors = NewSaleFormValidator.validateAll(
            ventaCompleta(colonia = ""),
            hasProducts = true
        )
        assertTrue(errors.colonia)
        assertTrue(errors.hasAny)
    }

    @Test
    fun `incidente - telefono 000000 bloquea la venta en CONTADO`() {
        // El dato real: el vendedor tecleó "000000" en una venta de contado, la
        // app lo aceptó y el mapper emitió "+52000000". Antes de este fix la
        // función abría con `if (tipoVenta == "CONTADO") return true`.
        assertFalse(NewSaleFormValidator.validatePhone("000000", "CONTADO"))
        assertFalse(NewSaleFormValidator.validatePhone("000000", "CREDITO"))
    }

    @Test
    fun `incidente - telefono 000000 bloquea via validateAll en CONTADO`() {
        val errors = NewSaleFormValidator.validateAll(
            ventaCompleta(tipoVenta = "CONTADO", phone = "000000"),
            hasProducts = true
        )
        assertTrue(errors.phone)
        assertTrue(errors.hasAny)
    }

    @Test
    fun `incidente - una venta con los tres campos y telefono bueno si pasa`() {
        // Contraprueba: la misma venta bien capturada NO debe quedar bloqueada.
        val errors = NewSaleFormValidator.validateAll(ventaCompleta(), hasProducts = true)
        assertFalse(errors.hasAny)
    }

    // --- Phone validation ---

    @Test
    fun `validatePhone CONTADO acepta vacio`() {
        // Divergencia deliberada del servidor solo en CRÉDITO; en CONTADO el
        // teléfono es opcional igual que en el API.
        assertTrue(NewSaleFormValidator.validatePhone("", "CONTADO"))
        assertTrue(NewSaleFormValidator.validatePhone("   ", "CONTADO"))
    }

    @Test
    fun `validatePhone CONTADO rechaza basura`() {
        // Reemplaza al test histórico `validatePhone CONTADO always valid`, que
        // codificaba el defecto que produjo el incidente: en contado se aceptaba
        // cualquier cadena.
        assertFalse(NewSaleFormValidator.validatePhone("123", "CONTADO"))
        assertFalse(NewSaleFormValidator.validatePhone("000000", "CONTADO"))
        assertFalse(NewSaleFormValidator.validatePhone("no tiene", "CONTADO"))
    }

    @Test
    fun `validatePhone valid 10 digit`() {
        assertTrue(NewSaleFormValidator.validatePhone("5512345678", "CREDITO"))
    }

    @Test
    fun `validatePhone acepta los dos formatos en ambos tipos de venta`() {
        listOf("CONTADO", "CREDITO").forEach { tipo ->
            assertTrue(NewSaleFormValidator.validatePhone("2381202772", tipo))
            assertTrue(NewSaleFormValidator.validatePhone("+522381202772", tipo))
        }
    }

    @Test
    fun `validatePhone 9 y 11 digitos bloquean en ambos tipos de venta`() {
        listOf("CONTADO", "CREDITO").forEach { tipo ->
            assertFalse(NewSaleFormValidator.validatePhone("238120277", tipo))
            assertFalse(NewSaleFormValidator.validatePhone("23812027722", tipo))
        }
    }

    @Test
    fun `validatePhone empty is invalid for CREDITO`() {
        assertFalse(NewSaleFormValidator.validatePhone("", "CREDITO"))
    }

    @Test
    fun `validatePhone 9 digits invalid`() {
        assertFalse(NewSaleFormValidator.validatePhone("551234567", "CREDITO"))
    }

    @Test
    fun `validatePhone 11 digits invalid`() {
        assertFalse(NewSaleFormValidator.validatePhone("55123456789", "CREDITO"))
    }

    @Test
    fun `validatePhone blank invalid for CREDITO`() {
        assertFalse(NewSaleFormValidator.validatePhone("   ", "CREDITO"))
    }

    // --- Installment validation ---

    @Test
    fun `validateInstallment CONTADO always valid`() {
        assertTrue(NewSaleFormValidator.validateInstallment("", "CONTADO"))
    }

    @Test
    fun `validateInstallment positive int valid`() {
        assertTrue(NewSaleFormValidator.validateInstallment("500", "CREDITO"))
    }

    @Test
    fun `validateInstallment zero invalid`() {
        assertFalse(NewSaleFormValidator.validateInstallment("0", "CREDITO"))
    }

    @Test
    fun `validateInstallment negative invalid`() {
        assertFalse(NewSaleFormValidator.validateInstallment("-100", "CREDITO"))
    }

    @Test
    fun `validateInstallment non-numeric invalid`() {
        assertFalse(NewSaleFormValidator.validateInstallment("abc", "CREDITO"))
    }

    @Test
    fun `validateInstallment decimal invalid`() {
        assertFalse(NewSaleFormValidator.validateInstallment("100.5", "CREDITO"))
    }

    // --- PaymentFrequency validation ---

    @Test
    fun `validatePaymentFrequency CONTADO always valid`() {
        assertTrue(NewSaleFormValidator.validatePaymentFrequency("", "CONTADO"))
    }

    @Test
    fun `validatePaymentFrequency non-blank valid`() {
        assertTrue(NewSaleFormValidator.validatePaymentFrequency("SEMANAL", "CREDITO"))
    }

    @Test
    fun `validatePaymentFrequency blank invalid`() {
        assertFalse(NewSaleFormValidator.validatePaymentFrequency("", "CREDITO"))
    }

    @Test
    fun `validatePaymentFrequency whitespace invalid`() {
        assertFalse(NewSaleFormValidator.validatePaymentFrequency("   ", "CREDITO"))
    }

    // --- CollectionDay validation ---

    @Test
    fun `validateCollectionDay CONTADO always valid`() {
        assertTrue(NewSaleFormValidator.validateCollectionDay("", "CONTADO"))
    }

    @Test
    fun `validateCollectionDay non-blank valid`() {
        assertTrue(NewSaleFormValidator.validateCollectionDay("LUNES", "CREDITO"))
    }

    @Test
    fun `validateCollectionDay blank invalid`() {
        assertFalse(NewSaleFormValidator.validateCollectionDay("", "CREDITO"))
    }

    // --- ClientName validation ---

    @Test
    fun `validateClientName 3 chars valid`() {
        assertTrue(NewSaleFormValidator.validateClientName("Ana"))
    }

    @Test
    fun `validateClientName long name valid`() {
        assertTrue(NewSaleFormValidator.validateClientName("Juan Carlos Perez"))
    }

    @Test
    fun `validateClientName 2 chars invalid`() {
        assertFalse(NewSaleFormValidator.validateClientName("AB"))
    }

    @Test
    fun `validateClientName blank invalid`() {
        assertFalse(NewSaleFormValidator.validateClientName("   "))
    }

    @Test
    fun `validateClientName empty invalid`() {
        assertFalse(NewSaleFormValidator.validateClientName(""))
    }

    // --- Colonia / Población / Ciudad (obligatorias, semántica del servidor) ---

    @Test
    fun `validateColonia no vacia es valida`() {
        assertTrue(NewSaleFormValidator.validateColonia("Centro"))
    }

    @Test
    fun `validateColonia vacia es invalida`() {
        assertFalse(NewSaleFormValidator.validateColonia(""))
    }

    @Test
    fun `validateColonia solo blancos cuenta como vacia`() {
        // El servidor valida DESPUÉS de `strings.TrimSpace`, así que "   " es
        // cadena vacía para él. Si aquí pasara, la venta reventaría en la cola.
        assertFalse(NewSaleFormValidator.validateColonia("   "))
        assertFalse(NewSaleFormValidator.validateColonia("\t\n "))
    }

    @Test
    fun `validateColonia admite nombres cortos`() {
        // A propósito NO se impone un mínimo de longitud: hay colonias legítimas
        // de nombre muy corto y el servidor solo exige "no vacía".
        assertTrue(NewSaleFormValidator.validateColonia("2"))
    }

    @Test
    fun `validatePoblacion no vacia es valida`() {
        assertTrue(NewSaleFormValidator.validatePoblacion("Tehuacán"))
    }

    @Test
    fun `validatePoblacion vacia y blancos son invalidos`() {
        assertFalse(NewSaleFormValidator.validatePoblacion(""))
        assertFalse(NewSaleFormValidator.validatePoblacion("   "))
    }

    @Test
    fun `validateCiudad no vacia es valida`() {
        assertTrue(NewSaleFormValidator.validateCiudad("Puebla"))
    }

    @Test
    fun `validateCiudad vacia y blancos son invalidos`() {
        assertFalse(NewSaleFormValidator.validateCiudad(""))
        assertFalse(NewSaleFormValidator.validateCiudad("   "))
    }

    @Test
    fun `validateAll marca los tres campos de direccion por separado`() {
        // Cada error va a SU campo: el vendedor debe ver cuál le falta, no un
        // mensaje genérico al pie del formulario.
        val errors = NewSaleFormValidator.validateAll(
            ventaCompleta(colonia = "  ", poblacion = "", ciudad = "   "),
            hasProducts = true
        )
        assertTrue(errors.colonia)
        assertTrue(errors.poblacion)
        assertTrue(errors.ciudad)
        assertFalse(errors.clientName)
        assertFalse(errors.location)
    }

    @Test
    fun `validateAll exige direccion completa tambien en CONTADO`() {
        // El API no distingue tipo de venta para la dirección.
        val errors = NewSaleFormValidator.validateAll(
            ventaCompleta(tipoVenta = "CONTADO", ciudad = ""),
            hasProducts = true
        )
        assertTrue(errors.ciudad)
    }

    // --- Street validation ---

    @Test
    fun `validateStreet 5 chars valid`() {
        assertTrue(NewSaleFormValidator.validateStreet("Calle"))
    }

    @Test
    fun `validateStreet long address valid`() {
        assertTrue(NewSaleFormValidator.validateStreet("Calle Principal 123"))
    }

    @Test
    fun `validateStreet 4 chars invalid`() {
        assertFalse(NewSaleFormValidator.validateStreet("Call"))
    }

    @Test
    fun `validateStreet blank invalid`() {
        assertFalse(NewSaleFormValidator.validateStreet("   "))
    }

    // --- Downpayment validation ---

    @Test
    fun `validateDownpayment blank valid`() {
        assertTrue(NewSaleFormValidator.validateDownpayment(""))
    }

    @Test
    fun `validateDownpayment zero valid`() {
        assertTrue(NewSaleFormValidator.validateDownpayment("0"))
    }

    @Test
    fun `validateDownpayment positive valid`() {
        assertTrue(NewSaleFormValidator.validateDownpayment("200"))
    }

    @Test
    fun `validateDownpayment negative invalid`() {
        assertFalse(NewSaleFormValidator.validateDownpayment("-100"))
    }

    @Test
    fun `validateDownpayment non-numeric invalid`() {
        assertFalse(NewSaleFormValidator.validateDownpayment("abc"))
    }

    // --- Zone validation ---

    @Test
    fun `validateZone CONTADO always valid`() {
        assertTrue(NewSaleFormValidator.validateZone("CONTADO", null, ""))
    }

    @Test
    fun `validateZone CREDITO with valid zone`() {
        assertTrue(NewSaleFormValidator.validateZone("CREDITO", 1, "Zona Norte"))
    }

    @Test
    fun `validateZone CREDITO null zoneId invalid`() {
        assertFalse(NewSaleFormValidator.validateZone("CREDITO", null, "Zona Norte"))
    }

    @Test
    fun `validateZone CREDITO blank zoneName invalid`() {
        assertFalse(NewSaleFormValidator.validateZone("CREDITO", 1, ""))
    }

    // --- Location validation ---

    @Test
    fun `validateLocation valid coords and permission`() {
        assertTrue(NewSaleFormValidator.validateLocation(19.432608, -99.133209, true))
    }

    @Test
    fun `validateLocation zero latitude invalid`() {
        assertFalse(NewSaleFormValidator.validateLocation(0.0, -99.133209, true))
    }

    @Test
    fun `validateLocation no permission invalid`() {
        assertFalse(NewSaleFormValidator.validateLocation(19.432608, -99.133209, false))
    }

    // --- validateAll ---

    @Test
    fun `validateAll empty CREDITO form has all errors`() {
        val state = NewSaleFormState()
        val errors = NewSaleFormValidator.validateAll(state, hasProducts = false)
        assertTrue(errors.clientName)
        assertTrue(errors.phone)
        assertTrue(errors.location)
        assertTrue(errors.colonia)
        assertTrue(errors.poblacion)
        assertTrue(errors.ciudad)
        assertTrue(errors.installment)
        assertTrue(errors.paymentFrequency)
        assertTrue(errors.collectionDay)
        assertTrue(errors.image)
        assertTrue(errors.products)
        assertFalse(errors.downpayment)
        assertTrue(errors.zone)
    }

    @Test
    fun `validateAll valid CREDITO form has no errors`() {
        val state = NewSaleFormState(
            clientName = "Juan Perez",
            phone = "5512345678",
            street = "Calle Principal 123",
            colonia = "Centro",
            poblacion = "Tehuacán",
            ciudad = "Puebla",
            latitude = 19.432608,
            longitude = -99.133209,
            locationPermissionGranted = true,
            hasValidLocation = true,
            tipoVenta = "CREDITO",
            installment = "500",
            paymentFrequency = "SEMANAL",
            collectionDay = "LUNES",
            selectedZoneId = 1,
            selectedZoneName = "Zona Norte",
            imageUris = listOf(Uri.parse("content://test/image.jpg"))
        )
        val errors = NewSaleFormValidator.validateAll(state, hasProducts = true)
        assertFalse(errors.clientName)
        assertFalse(errors.phone)
        assertFalse(errors.location)
        assertFalse(errors.colonia)
        assertFalse(errors.poblacion)
        assertFalse(errors.ciudad)
        assertFalse(errors.installment)
        assertFalse(errors.paymentFrequency)
        assertFalse(errors.collectionDay)
        assertFalse(errors.image)
        assertFalse(errors.products)
        assertFalse(errors.downpayment)
        assertFalse(errors.zone)
        assertFalse(errors.hasAny)
    }

    @Test
    fun `validateAll CONTADO skips credit fields`() {
        val state = NewSaleFormState(
            clientName = "Juan Perez",
            phone = "",
            street = "Calle Principal 123",
            colonia = "Centro",
            poblacion = "Tehuacán",
            ciudad = "Puebla",
            latitude = 19.432608,
            longitude = -99.133209,
            locationPermissionGranted = true,
            hasValidLocation = true,
            tipoVenta = "CONTADO",
            imageUris = listOf(Uri.parse("content://test/image.jpg"))
        )
        val errors = NewSaleFormValidator.validateAll(state, hasProducts = true)
        assertFalse(errors.clientName)
        assertFalse(errors.phone)
        assertFalse(errors.location)
        assertFalse(errors.colonia)
        assertFalse(errors.poblacion)
        assertFalse(errors.ciudad)
        assertFalse(errors.installment)
        assertFalse(errors.paymentFrequency)
        assertFalse(errors.collectionDay)
        assertFalse(errors.image)
        assertFalse(errors.products)
        assertFalse(errors.downpayment)
        assertFalse(errors.zone)
        assertFalse(errors.hasAny)
    }

    // --- isAllValid ---

    @Test
    fun `isAllValid returns false for empty state`() {
        assertFalse(NewSaleFormValidator.isAllValid(NewSaleFormState(), hasProducts = false))
    }

    @Test
    fun `isAllValid returns true for complete CREDITO`() {
        val state = NewSaleFormState(
            clientName = "Juan Perez",
            phone = "5512345678",
            street = "Calle Principal 123",
            colonia = "Centro",
            poblacion = "Tehuacán",
            ciudad = "Puebla",
            latitude = 19.432608,
            longitude = -99.133209,
            locationPermissionGranted = true,
            tipoVenta = "CREDITO",
            installment = "500",
            paymentFrequency = "SEMANAL",
            collectionDay = "LUNES",
            selectedZoneId = 1,
            selectedZoneName = "Zona Norte",
            imageUris = listOf(Uri.parse("content://test/image.jpg"))
        )
        assertTrue(NewSaleFormValidator.isAllValid(state, hasProducts = true))
    }

    @Test
    fun `isAllValid bloquea si falta ciudad aunque todo lo demas este`() {
        val state = ventaCompleta(ciudad = "").copy(
            latitude = 19.432608,
            longitude = -99.133209,
            locationPermissionGranted = true
        )
        assertFalse(NewSaleFormValidator.isAllValid(state, hasProducts = true))
    }

    // --- Camino de EDICIÓN ---
    //
    // `EditSaleScreen` ya no trae reglas propias: empaqueta sus `remember` en un
    // `NewSaleFormState` y llama a `validateAll` con `hasImages` explícito
    // (en edición las imágenes válidas son las del servidor menos las borradas
    // más las nuevas, y eso no cabe en `state.imageUris`). Estos tests ejercen
    // ese contrato exacto.

    @Test
    fun `edicion - ciudad vacia bloquea el guardado`() {
        val errors = NewSaleFormValidator.validateAll(
            ventaCompleta(ciudad = ""),
            hasProducts = true,
            hasImages = true
        )
        assertTrue(errors.ciudad)
        assertTrue(errors.hasAny)
    }

    @Test
    fun `edicion - colonia en blanco bloquea el guardado`() {
        val errors = NewSaleFormValidator.validateAll(
            ventaCompleta(colonia = "   "),
            hasProducts = true,
            hasImages = true
        )
        assertTrue(errors.colonia)
        assertTrue(errors.hasAny)
    }

    @Test
    fun `edicion - telefono 000000 bloquea el guardado en CONTADO`() {
        val errors = NewSaleFormValidator.validateAll(
            ventaCompleta(tipoVenta = "CONTADO", phone = "000000"),
            hasProducts = true,
            hasImages = true
        )
        assertTrue(errors.phone)
    }

    @Test
    fun `edicion - venta bien capturada pasa aunque state imageUris este vacio`() {
        // Las imágenes ya existentes viven en el servidor, no en `imageUris`;
        // sin el parámetro `hasImages` toda edición quedaría bloqueada.
        val errors = NewSaleFormValidator.validateAll(
            ventaCompleta(),
            hasProducts = true,
            hasImages = true
        )
        assertFalse(errors.image)
        assertFalse(errors.hasAny)
    }

    @Test
    fun `edicion - sin imagenes ni productos marca ambos errores`() {
        val errors = NewSaleFormValidator.validateAll(
            ventaCompleta(),
            hasProducts = false,
            hasImages = false
        )
        assertTrue(errors.image)
        assertTrue(errors.products)
    }

    @Test
    fun `edicion - parcialidad decimal precargada de Room sigue siendo valida`() {
        // `EditSaleScreen` precarga el campo con `sale.PARCIALIDAD.toString()`,
        // o sea "500.0". Aplicarle la regla entera del alta marcaría en rojo la
        // parcialidad de TODA venta existente y bloquearía el guardado sin que
        // el vendedor tocara nada — de ahí `validateInstallmentEdit`.
        assertTrue(NewSaleFormValidator.validateInstallmentEdit("500.0", "CREDITO"))
        assertFalse(NewSaleFormValidator.validateInstallment("500.0", "CREDITO"))
    }

    @Test
    fun `edicion - parcialidad cero o no numerica sigue bloqueando`() {
        assertFalse(NewSaleFormValidator.validateInstallmentEdit("0", "CREDITO"))
        assertFalse(NewSaleFormValidator.validateInstallmentEdit("-1", "CREDITO"))
        assertFalse(NewSaleFormValidator.validateInstallmentEdit("abc", "CREDITO"))
        assertTrue(NewSaleFormValidator.validateInstallmentEdit("", "CONTADO"))
    }

    // --- Helper ---

    /**
     * Venta de CRÉDITO completa y correcta, con datos realistas de la zona.
     * Cada test sobreescribe SOLO el campo que quiere romper, así una aserción
     * en rojo señala inequívocamente qué regla falló.
     */
    private fun ventaCompleta(
        clientName: String = "Juan Hernández Cruz",
        phone: String = "2381202772",
        street: String = "Avenida Independencia 45",
        colonia: String = "Centro",
        poblacion: String = "Tehuacán",
        ciudad: String = "Puebla",
        tipoVenta: String = "CREDITO"
    ): NewSaleFormState {
        val esCredito = tipoVenta != "CONTADO"
        return NewSaleFormState(
            clientName = clientName,
            phone = phone,
            street = street,
            numero = "45",
            colonia = colonia,
            poblacion = poblacion,
            ciudad = ciudad,
            tipoVenta = tipoVenta,
            installment = if (esCredito) "500" else "",
            paymentFrequency = if (esCredito) "SEMANAL" else "",
            collectionDay = if (esCredito) "LUNES" else "",
            selectedZoneId = if (esCredito) 1 else null,
            selectedZoneName = if (esCredito) "Zona Norte" else "",
            imageUris = listOf(Uri.parse("content://test/image.jpg"))
        )
    }
}
