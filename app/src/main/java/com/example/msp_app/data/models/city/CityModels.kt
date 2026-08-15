package com.example.msp_app.data.models.city

import com.google.gson.annotations.SerializedName
import java.text.Normalizer
import java.util.Locale

/**
 * Fila del catálogo `CIUDADES` de Microsip, tal como la expone `GET /v2/ciudades`.
 *
 * **El estado viaja pegado a la ciudad y nunca se elige aparte.** El catálogo no
 * es solo de Puebla: abarca Oaxaca (11523) y Veracruz (11751), así que fijar el
 * estado por separado produce clientes con la ciudad de un estado y el estado de
 * otro. Por eso [estado] y [estadoId] son campos de ESTA fila y el selector emite
 * siempre el objeto completo — nunca dos callbacks independientes.
 */
data class City(
    val ciudadId: Int,
    val ciudad: String,
    val estadoId: Int,
    val estado: String
)

/**
 * Forma de alambre de una ciudad. Todos los campos son nullable **a propósito**:
 * Gson construye la instancia por `Unsafe` e ignora los valores por omisión de
 * Kotlin, así que un campo ausente en el JSON aterriza como `null` aunque el tipo
 * se declare no-nulo — y revienta después con un NPE ofuscado, lejos del parseo.
 * Declararlos nullable obliga a validarlos en [toCityOrNull].
 */
data class CityDto(
    @SerializedName("ciudad_id")
    val ciudadId: Int? = null,

    @SerializedName("ciudad")
    val ciudad: String? = null,

    @SerializedName("estado_id")
    val estadoId: Int? = null,

    @SerializedName("estado")
    val estado: String? = null
)

/**
 * Convierte una fila de alambre a [City], o `null` si viene incompleta.
 *
 * Se descartan las filas sin nombre de ciudad: son inservibles para el selector
 * (no habría qué mostrar ni qué capturar). El estado ausente NO descarta la fila
 * — el catálogo real trae filas con el estado en blanco y la ciudad sigue siendo
 * elegible; el servidor resuelve el `ESTADO_ID` desde la misma fila al aplicar.
 */
fun CityDto.toCityOrNull(): City? {
    val nombre = ciudad?.trim().orEmpty()
    val id = ciudadId
    if (nombre.isEmpty() || id == null) return null

    return City(
        ciudadId = id,
        ciudad = nombre,
        estadoId = estadoId ?: 0,
        estado = estado?.trim().orEmpty()
    )
}

/**
 * Normaliza un nombre de ciudad con **exactamente** el mismo criterio que
 * `domain.NormalizeCiudad` del API Go (`internal/ventas/domain/ciudad_normalize.go`):
 * descompone (NFD), quita marcas diacríticas, recompone (NFC), pasa a mayúsculas y
 * colapsa cada corrida de espacios.
 *
 * Debe coincidir con el servidor porque es el criterio con el que éste decide si
 * la ciudad capturada existe en el catálogo. Gracias a esa normalización, la
 * mugre conocida del catálogo de producción —espacios finales en `COYOMEAPAN ` y
 * `ESPERANZA `, acentos, mayúsculas inconsistentes— no impide la coincidencia, y
 * recortar el nombre en el teléfono es seguro.
 */
fun normalizeCiudad(value: String): String {
    val descompuesto = Normalizer.normalize(value, Normalizer.Form.NFD)
    val sinMarcas = COMBINING_MARKS.replace(descompuesto, "")
    return Normalizer.normalize(sinMarcas, Normalizer.Form.NFC)
        .uppercase(Locale.ROOT)
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")
