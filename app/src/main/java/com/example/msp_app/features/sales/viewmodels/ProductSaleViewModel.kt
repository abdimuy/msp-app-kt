package com.example.msp_app.features.sales.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.data.models.sale.localsale.LocalSaleProductPackage
import com.example.msp_app.features.sales.usecase.ValidatePackageUseCase
import com.example.msp_app.utils.PriceParser

data class SaleItem(
    val product: ProductInventory,
    val quantity: Int
)

class SaleProductsViewModel : ViewModel() {
    private val _saleItems = mutableStateListOf<SaleItem>()
    private val _packages = mutableStateListOf<LocalSaleProductPackage>()
    private val validatePackageUseCase = ValidatePackageUseCase()

    val saleItems: SnapshotStateList<SaleItem> get() = _saleItems
    val packages: SnapshotStateList<LocalSaleProductPackage> get() = _packages

    fun addProductToSale(product: ProductInventory, quantity: Int) {
        if (quantity <= 0) return
        val existingIndex = _saleItems.indexOfFirst {
            it.product.ARTICULO_ID == product.ARTICULO_ID
        }
        if (existingIndex != -1) {
            val existingItem = _saleItems[existingIndex]
            _saleItems[existingIndex] = existingItem.copy(
                quantity = existingItem.quantity + quantity
            )
        } else {
            _saleItems.add(SaleItem(product, quantity))
        }
    }

    fun addPackage(pkg: LocalSaleProductPackage) {
        _packages.add(pkg)
    }

    fun removeProductFromSale(product: ProductInventory) {
        _saleItems.removeAll { it.product.ARTICULO_ID == product.ARTICULO_ID }
    }

    fun updateQuantity(product: ProductInventory, newQuantity: Int) {
        val index = _saleItems.indexOfFirst {
            it.product.ARTICULO_ID == product.ARTICULO_ID
        }

        if (index != -1) {
            if (newQuantity <= 0) {
                _saleItems.removeAt(index)
            } else {
                _saleItems[index] = _saleItems[index].copy(quantity = newQuantity)
            }
        }
    }

    fun getQuantityForProduct(product: ProductInventory): Int {
        val individualQuantity = _saleItems.find {
            it.product.ARTICULO_ID == product.ARTICULO_ID
        }?.quantity ?: 0

        val packageQuantity = _packages.sumOf { pkg ->
            pkg.products.filter { it.product.ARTICULO_ID == product.ARTICULO_ID }
                .sumOf { it.quantity }
        }

        return individualQuantity + packageQuantity
    }

    private fun generatePackageName(products: List<SaleItem>): String {
        return products.joinToString(", ") { saleItem ->
            val words = saleItem.product.ARTICULO.split(" ")
            words.take(2).joinToString(" ")
        }
    }

    // Mover getTotalQuantity aquí
    internal fun getPackageTotalQuantity(pkg: LocalSaleProductPackage): Int {
        return pkg.products.sumOf { it.quantity }
    }

    fun createPackage(
        selectedProducts: List<SaleItem>,
        precioLista: Double,
        precioCortoplazo: Double,
        precioContado: Double
    ): Result<LocalSaleProductPackage> {

        val validationResult = validatePackageUseCase.execute(
            selectedProducts, precioLista, precioCortoplazo, precioContado
        )
        if (validationResult.isFailure) {
            return Result.failure(validationResult.exceptionOrNull()!!)
        }

        val allProductsExist = selectedProducts.all { selectedItem ->
            _saleItems.any { it.product.ARTICULO_ID == selectedItem.product.ARTICULO_ID }
        }
        if (!allProductsExist) {
            return Result.failure(Exception("Algunos productos seleccionados no están disponibles"))
        }

        val packageName = generatePackageName(selectedProducts)
        val newPackage = LocalSaleProductPackage(
            packageName = packageName,
            products = selectedProducts.toList(),
            precioLista = precioLista,
            precioCortoplazo = precioCortoplazo,
            precioContado = precioContado
        )

        selectedProducts.forEach { selectedItem ->
            val existingItem = _saleItems.find {
                it.product.ARTICULO_ID == selectedItem.product.ARTICULO_ID
            }

            if (existingItem != null) {
                val remainingQuantity = existingItem.quantity - selectedItem.quantity
                if (remainingQuantity > 0) {
                    updateQuantity(existingItem.product, remainingQuantity)
                } else {
                    removeProductFromSale(existingItem.product)
                }
            }
        }

        _packages.add(newPackage)

        return Result.success(newPackage)
    }

    fun unpackPackage(packageId: String) {
        val packageIndex = _packages.indexOfFirst { it.packageId == packageId }
        if (packageIndex == -1) return

        val packageToUnpack = _packages[packageIndex]

        packageToUnpack.products.forEach { saleItem ->
            addProductToSale(saleItem.product, saleItem.quantity)
        }

        _packages.removeAt(packageIndex)
    }

    fun updatePackagePrices(
        packageId: String,
        precioLista: Double,
        precioCortoplazo: Double,
        precioContado: Double
    ): Boolean {
        if (precioLista <= 0 || precioCortoplazo <= 0 || precioContado <= 0) {
            return false
        }

        val index = _packages.indexOfFirst { it.packageId == packageId }
        if (index == -1) return false

        val oldPackage = _packages[index]
        _packages[index] = oldPackage.copy(
            precioLista = precioLista,
            precioCortoplazo = precioCortoplazo,
            precioContado = precioContado
        )

        return true
    }

    fun togglePackageExpanded(packageId: String) {
        val index = _packages.indexOfFirst { it.packageId == packageId }
        if (index != -1) {
            val pkg = _packages[index]
            _packages[index] = pkg.copy(isExpanded = !pkg.isExpanded)
        }
    }

    fun removePackage(packageId: String) {
        _packages.removeAll { it.packageId == packageId }
    }

    fun getTotalItems(): Int {
        val individualItems = _saleItems.sumOf { it.quantity }
        val packageItems = _packages.sumOf { getPackageTotalQuantity(it) }
        return individualItems + packageItems
    }

    fun getTotalPrecioLista(): Double {
        val individualTotal = _saleItems.sumOf { saleItem ->
            val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
            parsedPrices.precioLista * saleItem.quantity
        }

        val packageTotal = _packages.sumOf { it.precioLista }

        return individualTotal + packageTotal
    }

    fun getTotalMontoCortoplazo(): Double {
        val individualTotal = _saleItems.sumOf { saleItem ->
            val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
            parsedPrices.precioCortoplazo * saleItem.quantity
        }

        val packageTotal = _packages.sumOf { it.precioCortoplazo }

        return individualTotal + packageTotal
    }

    fun getTotalMontoContado(): Double {
        val individualTotal = _saleItems.sumOf { saleItem ->
            val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
            parsedPrices.precioContado * saleItem.quantity
        }

        val packageTotal = _packages.sumOf { it.precioContado }

        return individualTotal + packageTotal
    }

    fun getTotalPrice(): Double = getTotalPrecioLista()

    fun hasItems(): Boolean = _saleItems.isNotEmpty() || _packages.isNotEmpty()

    fun getSaleItemsForWarehouse(): List<Pair<ProductInventory, Int>> {
        val individualProducts = _saleItems.map { it.product to it.quantity }

        val packageProducts = _packages.flatMap { pkg ->
            pkg.products.map { it.product to it.quantity }
        }

        return (individualProducts + packageProducts)
            .groupBy { it.first.ARTICULO_ID }
            .map { (_, pairs) ->
                val product = pairs.first().first
                val totalQuantity = pairs.sumOf { it.second }
                product to totalQuantity
            }
    }

    fun clearSale() {
        _saleItems.clear()
        _packages.clear()
    }

    fun updateProductPrices(
        product: ProductInventory,
        newListPrice: Double,
        newShortTermPrice: Double,
        newCashPrice: Double
    ) {
        val index = _saleItems.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }
        if (index != -1) {
            val oldItem = _saleItems[index]
            val updatedProduct = oldItem.product.copy(
                PRECIOS = PriceParser.pricesToJson(newListPrice, newShortTermPrice, newCashPrice)
            )
            _saleItems[index] = oldItem.copy(product = updatedProduct)
        }
    }
}