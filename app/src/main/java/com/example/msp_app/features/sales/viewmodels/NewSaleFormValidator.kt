package com.example.msp_app.features.sales.viewmodels

import com.example.msp_app.core.utils.MexicanPhone

/**
 * Reglas de captura de una venta. **Es la ÚNICA fuente de verdad**: la usan
 * tanto la alta (`NewSaleScreen` vía `NewSaleFormViewModel`) como la edición
 * (`EditSaleScreen`), que antes traía su propia copia de las reglas en
 * funciones locales del composable y podía divergir sin que nada lo notara.
 *
 * ## Espejo del servidor
 *
 * El contrato autoritativo vive en el API Go (`msp-api`):
 * `internal/ventas/domain/direccion.go` (calle, colonia, población y ciudad
 * obligatorias tras `trim`) y `internal/ventas/app/crear_venta.go`
 * (`optionalTelefono`: teléfono opcional en TODOS los tipos de venta, pero si
 * llega con valor debe ser un número mexicano de 10 dígitos).
 *
 * **Incidente 2026-08-13 — por qué esto se endureció.** Nueve ventas capturadas
 * por vendedores nunca entraron al API y pasaron el día rebotando en la cola de
 * pendientes. Siete traían campos que el servidor rechaza y que esta pantalla
 * dejó capturar: 6 con ciudad vacía, 4 con colonia vacía y una con el teléfono
 * `+52000000` (el vendedor tecleó `000000` y el mapper le antepuso el prefijo).
 * El vendedor dio las ventas por hechas y se enteró horas después, o nunca,
 * porque la validación que las rechazaba solo existía en el servidor.
 *
 * ## Divergencias DELIBERADAS respecto al servidor
 *
 * Estas reglas son MÁS estrictas que el API a propósito; no son bugs y no deben
 * "corregirse" para igualar al servidor:
 *
 * - **Teléfono en CRÉDITO**: el servidor lo acepta vacío; aquí es obligatorio.
 *   Una venta a crédito sin forma de contactar al cliente no es cobrable, así
 *   que la regla de negocio de cobranza gana sobre la laxitud del API. Ver
 *   [validatePhone].
 * - **Calle**: el servidor solo exige "no vacía"; aquí se piden ≥ 5 caracteres
 *   para atajar capturas tipo "s/n". Ver [validateStreet].
 * - **Nombre del cliente**: ≥ 3 caracteres, mismo motivo.
 *
 * En cambio colonia / población / ciudad usan EXACTAMENTE la semántica del
 * servidor (no vacío tras `trim`) — a propósito, para no inventar un mínimo de
 * longitud que rechace colonias legítimas de nombre corto.
 */
object NewSaleFormValidator {

    /** Valor de `tipoVenta` que apaga todas las reglas del bloque de crédito. */
    private const val TIPO_VENTA_CONTADO = "CONTADO"

    fun validateClientName(name: String): Boolean {
        return name.isNotBlank() && name.length >= 3
    }

    /**
     * Reglas del teléfono, espejo de `optionalTelefono` del servidor salvo por
     * la divergencia documentada abajo:
     *
     * - **Vacío** (o solo espacios): válido en CONTADO, inválido en CRÉDITO.
     *   El servidor acepta vacío en ambos; exigirlo en crédito es una regla de
     *   negocio de cobranza que se conserva a propósito.
     * - **Con valor**: debe ser un número mexicano de 10 dígitos, con o sin
     *   `+52`, **también en CONTADO**.
     *
     * Ese "también en CONTADO" es el fix del incidente: antes la función abría
     * con `if (tipoVenta == "CONTADO") return true` y aceptaba cualquier basura,
     * incluido el `000000` que terminó viajando como `+52000000` y rebotando
     * contra `telefono_invalid` durante todo un día.
     *
     * El formato lo decide [MexicanPhone], la misma implementación que usa el
     * mapper de red — así la pantalla y lo que sale por el cable no pueden
     * opinar distinto.
     */
    fun validatePhone(phone: String, tipoVenta: String): Boolean {
        if (phone.isBlank()) return tipoVenta == TIPO_VENTA_CONTADO
        return MexicanPhone.isValid(phone)
    }

    fun validateStreet(street: String): Boolean {
        return street.isNotBlank() && street.length >= 5
    }

    /**
     * Colonia obligatoria. `isNotBlank()` es exactamente el criterio del
     * servidor (`colonia_required` se dispara con la cadena vacía **tras
     * `strings.TrimSpace`**), así que `"   "` cuenta como vacía.
     */
    fun validateColonia(colonia: String): Boolean = colonia.isNotBlank()

    /** Población obligatoria; misma semántica que [validateColonia] (`poblacion_required`). */
    fun validatePoblacion(poblacion: String): Boolean = poblacion.isNotBlank()

    /** Ciudad obligatoria; misma semántica que [validateColonia] (`ciudad_required`). */
    fun validateCiudad(ciudad: String): Boolean = ciudad.isNotBlank()

    fun validateInstallment(amount: String, tipoVenta: String): Boolean {
        if (tipoVenta == TIPO_VENTA_CONTADO) return true
        val amountInt = amount.toIntOrNull()
        return amountInt != null && amountInt > 0
    }

    /**
     * Parcialidad en el camino de **EDICIÓN**: admite decimales, a diferencia de
     * [validateInstallment], que exige entero.
     *
     * No es un descuido: Room guarda `PARCIALIDAD` como `Double` y `EditSaleScreen`
     * precarga el campo con `sale.PARCIALIDAD.toString()`, o sea `"500.0"` para
     * una parcialidad de 500. Aplicar ahí la regla del alta (`toIntOrNull`)
     * pintaría en rojo la parcialidad de TODA venta existente en cuanto se abre a
     * editar y bloquearía el guardado sin que el vendedor haya tocado nada — se
     * repondría el atasco que la edición sirve para resolver. Al servidor el
     * importe viaja como decimal en ambos caminos, así que en el cable no hay
     * diferencia.
     */
    fun validateInstallmentEdit(amount: String, tipoVenta: String): Boolean {
        if (tipoVenta == TIPO_VENTA_CONTADO) return true
        val amountDouble = amount.toDoubleOrNull()
        return amountDouble != null && amountDouble > 0
    }

    fun validatePaymentFrequency(frequency: String, tipoVenta: String): Boolean {
        if (tipoVenta == TIPO_VENTA_CONTADO) return true
        return frequency.isNotBlank()
    }

    fun validateCollectionDay(day: String, tipoVenta: String): Boolean {
        if (tipoVenta == TIPO_VENTA_CONTADO) return true
        return day.isNotBlank()
    }

    fun validateDownpayment(downpayment: String): Boolean {
        return downpayment.isBlank() || (downpayment.toDoubleOrNull()?.let { it >= 0 } ?: false)
    }

    /**
     * La zona es obligatoria en TODO tipo de venta.
     *
     * Antes CONTADO quedaba exento porque el servidor le asignaba la caja fija
     * de mostrador. Esa excepción es la que deja ventas sin zona, y el servidor
     * las rechaza con `venta_sin_zona` en cuanto se enciende
     * `VENTAS_ZONA_OBLIGATORIA`. Se valida aquí para que el vendedor lo
     * descubra al capturar y no al aplicar.
     *
     * [tipoVenta] se conserva en la firma: la usan las demás validaciones del
     * formulario y quitarla obligaría a tocar todas las llamadas.
     */
    @Suppress("UNUSED_PARAMETER")
    fun validateZone(tipoVenta: String, zoneId: Int?, zoneName: String): Boolean {
        return zoneId != null && zoneName.isNotBlank()
    }

    fun validateLocation(latitude: Double, longitude: Double, permissionGranted: Boolean): Boolean {
        return latitude != 0.0 && longitude != 0.0 && permissionGranted
    }

    /**
     * Evalúa el formulario completo y devuelve una bandera por campo.
     *
     * [hasImages] existe para el camino de EDICIÓN: ahí las imágenes válidas son
     * las que ya están en el servidor menos las marcadas para borrar, más las
     * nuevas del carrete — un conteo que no cabe en `state.imageUris`. El valor
     * por omisión reproduce la regla del alta (`imageUris` no vacío), así que
     * `NewSaleScreen` no necesita pasarlo.
     */
    fun validateAll(
        state: NewSaleFormState,
        hasProducts: Boolean,
        hasImages: Boolean = state.imageUris.isNotEmpty()
    ): FormErrors {
        val tipoVenta = state.tipoVenta
        return FormErrors(
            clientName = !validateClientName(state.clientName),
            phone = !validatePhone(state.phone, tipoVenta),
            location = !validateStreet(state.street),
            colonia = !validateColonia(state.colonia),
            poblacion = !validatePoblacion(state.poblacion),
            ciudad = !validateCiudad(state.ciudad),
            installment = !validateInstallment(state.installment, tipoVenta),
            paymentFrequency = !validatePaymentFrequency(state.paymentFrequency, tipoVenta),
            collectionDay = !validateCollectionDay(state.collectionDay, tipoVenta),
            image = !hasImages,
            products = !hasProducts,
            downpayment = !validateDownpayment(state.downpayment),
            zone = !validateZone(tipoVenta, state.selectedZoneId, state.selectedZoneName)
        )
    }

    /**
     * Igual que [validateAll] pero además exige GPS válido, que no tiene campo
     * propio en [FormErrors] (se pinta como un aviso aparte bajo el mapa).
     */
    fun isAllValid(state: NewSaleFormState, hasProducts: Boolean): Boolean {
        val locationDataValid =
            validateLocation(state.latitude, state.longitude, state.locationPermissionGranted)
        return !validateAll(state, hasProducts).hasAny && locationDataValid
    }
}
