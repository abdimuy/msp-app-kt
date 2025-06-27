package com.example.msp_app.core.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
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
            // 1. Parsear con zona UTC si viene con "Z"
            val offset = when {
                iso.endsWith("Z") -> OffsetDateTime.parse(iso)
                iso.contains("T") -> LocalDateTime.parse(iso).atOffset(ZoneOffset.UTC)
                else -> LocalDate.parse(iso).atStartOfDay().atOffset(ZoneOffset.UTC)
            }
            // 2. Convertir a zona local
            val zonedLocal = offset.atZoneSameInstant(ZoneId.systemDefault())
            // 3. Formatear con patrón, locale y zona
            val formatter = DateTimeFormatter
                .ofPattern(pattern, locale)
                .withZone(ZoneId.systemDefault())
            formatter.format(zonedLocal)
        } catch (e: Exception) {
            iso
        }
    }

    /**
     * Obtiene la fecha actual en formato ISO 8601.
     *
     * @param dateTime Fecha y hora a formatear, o null para usar la actual
     * @return Fecha en formato ISO 8601 (ej. "2024-07-10T15:30:00Z")
     */
    fun getIsoDateTime(dateTime: LocalDateTime? = null): String {
        val formatter = DateTimeFormatter.ISO_INSTANT
        val zoned = (dateTime ?: LocalDateTime.now()).atZone(java.time.ZoneOffset.UTC)
        return formatter.format(zoned)
    }

    fun getCurrentDate(): LocalDate {
        return LocalDate.now(ZoneId.systemDefault())
    }

    /**
     * Compara dos fechas en formato ISO 8601 y determina si son iguales.
     *
     * @param iso1 Primera fecha en formato ISO
     * @param iso2 Segunda fecha en formato ISO
     * @return true si son iguales, false en caso contrario
     */
    fun isAfterIso(iso1: String, iso2: String): Boolean {
        val dt1 = parseIsoToDateTime(iso1)
        val dt2 = parseIsoToDateTime(iso2)
        return dt1.isAfter(dt2)
    }

    /**
     * Compara dos fechas en formato ISO 8601 y determina si la primera es anterior a la segunda.
     *
     * @param iso1 Primera fecha en formato ISO
     * @param iso2 Segunda fecha en formato ISO
     * @return true si la primera es anterior a la segunda, false en caso contrario
     */
    fun isBeforeIso(iso1: String, iso2: String): Boolean {
        val dt1 = parseIsoToDateTime(iso1)
        val dt2 = parseIsoToDateTime(iso2)
        return dt1.isBefore(dt2)
    }

    /**
     * Compara dos fechas en formato ISO 8601 y determina si son iguales.
     *
     * @param iso1 Primera fecha en formato ISO
     * @param iso2 Segunda fecha en formato ISO
     * @return true si son iguales, false en caso contrario
     */
    fun parseIsoToDateTime(iso: String): LocalDateTime {
        return when {
            iso.contains("T") && iso.endsWith("Z") ->
                OffsetDateTime.parse(iso).toLocalDateTime()

            iso.contains("T") ->
                LocalDateTime.parse(iso)

            else ->
                LocalDate.parse(iso).atStartOfDay()
        }
    }

    /**
     * Convierte un objeto Date a una cadena en formato ISO 8601.
     *
     * @return Fecha en formato ISO 8601 (ej. "2024-07-10T15:30:00Z")
     */
    fun parseDateToIso(date: Date?): String {
        if (date == null) return getIsoDateTime()
        val localDateTime = date.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
        return getIsoDateTime(localDateTime)
    }
}
