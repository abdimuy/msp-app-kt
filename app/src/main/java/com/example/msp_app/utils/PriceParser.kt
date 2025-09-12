package com.example.msp_app.utils

data class ParsedPrices(
    val precioLista: Double = 0.0,
    val precioCortoplazo: Double = 0.0,
    val precioContado: Double = 0.0
)

object PriceParser {

    fun parsePricesFromString(pricesString: String?): ParsedPrices {
        if (pricesString.isNullOrBlank()) {
            return ParsedPrices()
        }

        try {
            var precioLista = 0.0
            var precioCortoplazo = 0.0
            var precioContado = 0.0

            val priceEntries = pricesString.split(",")

            for (entry in priceEntries) {
                val trimmedEntry = entry.trim()

                when {
                    trimmedEntry.contains("Precio de lista:", ignoreCase = true) -> {
                        precioLista = extractPrice(trimmedEntry, "Precio de lista:")
                    }

                    trimmedEntry.contains("Precio 4 Meses:", ignoreCase = true) -> {
                        precioCortoplazo = extractPrice(trimmedEntry, "Precio 4 Meses:")
                    }

                    trimmedEntry.contains("Precio 1 Meses:", ignoreCase = true) -> {
                        precioContado = extractPrice(trimmedEntry, "Precio 1 Meses:")
                    }
                }
            }

            return ParsedPrices(
                precioLista = precioLista,
                precioCortoplazo = precioCortoplazo,
                precioContado = precioContado
            )

        } catch (e: Exception) {
            return ParsedPrices()
        }
    }

    private fun extractPrice(entry: String, prefix: String): Double {
        return try {
            val priceString = entry.substringAfter(prefix).trim()
            priceString.toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    fun pricesToJson(listPrice: Double, shortTermPrice: Double, cashPrice: Double): String {
        return "Precio de lista:$listPrice, Precio 4 Meses:$shortTermPrice, Precio 1 Meses:$cashPrice"
    }
}