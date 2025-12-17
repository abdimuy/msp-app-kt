package com.example.msp_app.features.sales.viewmodels.kits

import com.example.msp_app.features.sales.viewmodels.SaleItem

class ValidatePackageUseCase {

    fun execute(
        selectedProducts: List<SaleItem>,
        precioLista: Double,
        precioCortoplazo: Double,
        precioContado: Double
    ): Result<Unit> {
        if (selectedProducts.size < 2) {
            return Result.failure(InvalidPackageException("Debe seleccionar al menos 2 productos"))
        }

        if (precioLista <= 0 || precioCortoplazo <= 0 || precioContado <= 0) {
            return Result.failure(InvalidPackageException("Todos los precios deben ser mayores a 0"))
        }

        if (precioContado > precioLista) {
            return Result.failure(InvalidPackageException("El precio de contado no puede ser mayor al precio de lista"))
        }

        if (precioCortoplazo < precioContado || precioCortoplazo > precioLista) {
            return Result.failure(InvalidPackageException("El precio de corto plazo debe estar entre el precio de contado y el de lista"))
        }

        selectedProducts.forEach { saleItem ->
            if (saleItem.quantity <= 0) {
                return Result.failure(InvalidPackageException("Todos los productos deben tener cantidad mayor a 0"))
            }
        }

        return Result.success(Unit)
    }
}

class InvalidPackageException(message: String) : Exception(message)