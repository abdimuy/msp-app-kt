package com.example.msp_app.features.transfers.domain.models

import java.time.LocalDate

/**
 * Domain model for transfer filters
 */
data class TransferFilters(
    val fechaInicio: LocalDate? = null,
    val fechaFin: LocalDate? = null,
    val almacenOrigenId: Int? = null,
    val almacenDestinoId: Int? = null
) {
    /**
     * Check if any filter is active
     */
    fun hasActiveFilters(): Boolean {
        return fechaInicio != null ||
                fechaFin != null ||
                almacenOrigenId != null ||
                almacenDestinoId != null
    }

    /**
     * Get count of active filters
     */
    fun getActiveFilterCount(): Int {
        return listOf(
            fechaInicio != null,
            fechaFin != null,
            almacenOrigenId != null,
            almacenDestinoId != null
        ).count { it }
    }

    /**
     * Clear all filters
     */
    fun clear(): TransferFilters = TransferFilters()

    companion object {
        /**
         * Create filter for today
         */
        fun today(): TransferFilters {
            val today = LocalDate.now()
            return TransferFilters(
                fechaInicio = today,
                fechaFin = today
            )
        }

        /**
         * Create filter for this week
         */
        fun thisWeek(): TransferFilters {
            val today = LocalDate.now()
            val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            return TransferFilters(
                fechaInicio = startOfWeek,
                fechaFin = today
            )
        }

        /**
         * Create filter for this month
         */
        fun thisMonth(): TransferFilters {
            val today = LocalDate.now()
            val startOfMonth = today.withDayOfMonth(1)
            return TransferFilters(
                fechaInicio = startOfMonth,
                fechaFin = today
            )
        }
    }
}
