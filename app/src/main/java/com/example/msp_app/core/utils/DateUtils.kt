package com.example.msp_app.core.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {
    /**
     * Formatea una fecha en formato ISO 8601 (ej. "2024-07-10T15:30:00Z")
     * al formato especificado.
     *
     * @param iso Fecha en string ISO (de servidor o BD)
     * @param pattern Formato deseado (ej. "dd/MM/yyyy")
     * @param locale Idioma (por defecto español)
     * @return Fecha formateada como string, o el mismo string si falla
     */
    fun formatIsoDate(
        iso: String,
        pattern: String = "dd/MM/yyyy",
        locale: Locale = Locale("es", "MX")
    ): String {
        return try {
            val parsed = when {
                iso.contains("T") && iso.endsWith("Z") ->
                    OffsetDateTime.parse(iso)
                        .toLocalDateTime()

                iso.contains("T") ->
                    LocalDateTime.parse(iso)

                else ->
                    LocalDate.parse(iso).atStartOfDay()
            }

            val formatter = DateTimeFormatter.ofPattern(pattern, locale)
            parsed.format(formatter)
        } catch (e: Exception) {
            iso // fallback en caso de error
        }
    }
}
