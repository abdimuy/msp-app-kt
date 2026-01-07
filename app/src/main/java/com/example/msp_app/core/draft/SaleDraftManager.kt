package com.example.msp_app.core.draft

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

// Extension property to create DataStore instance
val Context.saleDraftDataStore: DataStore<Preferences> by preferencesDataStore(name = "sale_draft")

/**
 * Lightweight product representation for draft saving
 * Only stores ID and quantity, full product will be fetched when loading draft
 */
data class DraftProduct(
    val articuloId: Int,
    val quantity: Int,
    val comboId: String? = null
)

/**
 * Combo representation for draft saving
 */
data class DraftCombo(
    val comboId: String,
    val nombreCombo: String,
    val precioLista: Double,
    val precioCortoPlazo: Double,
    val precioContado: Double
)

data class SaleDraft(
    // Common fields for both types
    val clientName: String = "",
    val street: String = "",
    val numero: String = "",
    val colonia: String = "",
    val poblacion: String = "",
    val ciudad: String = "",
    val note: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val imageUris: List<String> = emptyList(),
    val productsJson: String = "",
    val combosJson: String = "", // JSON string of combos

    // Sale type
    val tipoVenta: String = "CREDITO", // "CREDITO" or "CONTADO"

    // CREDITO-specific fields (required only for CREDITO)
    val phone: String = "", // Required for CREDITO, optional for CONTADO
    val downpayment: String = "", // Only for CREDITO
    val installment: String = "", // Required for CREDITO
    val guarantor: String = "", // Only for CREDITO
    val collectionDay: String = "", // Required for CREDITO
    val paymentFrequency: String = "", // Required for CREDITO

    // Zone fields (required for all sales)
    val zonaClienteId: Int? = null,
    val zonaClienteNombre: String = "",

    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Check if this draft has meaningful data to save
     */
    fun hasData(): Boolean {
        return clientName.isNotBlank() ||
               phone.isNotBlank() ||
               street.isNotBlank() ||
               productsJson.isNotBlank()
    }

    /**
     * Get fields relevant for current sale type
     */
    fun isFieldRelevant(fieldName: String): Boolean {
        if (tipoVenta == "CONTADO") {
            return fieldName !in listOf(
                "downpayment", "installment", "guarantor",
                "collectionDay", "paymentFrequency"
            )
        }
        return true // All fields are relevant for CREDITO
    }
}

class SaleDraftManager(private val context: Context) {

    private val gson = Gson()

    /**
     * Convert list of SaleItems to JSON string
     */
    fun saleItemsToJson(saleItems: List<Any>): String {
        // Extract DraftProduct from SaleItems
        val draftProducts = saleItems.mapNotNull { item ->
            try {
                // Using reflection to access product, quantity, and comboId
                val productField = item.javaClass.getDeclaredField("product")
                val quantityField = item.javaClass.getDeclaredField("quantity")
                val comboIdField = item.javaClass.getDeclaredField("comboId")
                productField.isAccessible = true
                quantityField.isAccessible = true
                comboIdField.isAccessible = true

                val product = productField.get(item)
                val quantity = quantityField.getInt(item)
                val comboId = comboIdField.get(item) as? String

                // Get ARTICULO_ID from product
                val articuloIdField = product.javaClass.getDeclaredField("ARTICULO_ID")
                articuloIdField.isAccessible = true
                val articuloId = articuloIdField.getInt(product)

                DraftProduct(articuloId, quantity, comboId)
            } catch (e: Exception) {
                null
            }
        }

        return gson.toJson(draftProducts)
    }

    /**
     * Convert JSON string to list of DraftProducts
     */
    fun jsonToDraftProducts(json: String): List<DraftProduct> {
        if (json.isBlank()) return emptyList()

        return try {
            gson.fromJson(json, object : TypeToken<List<DraftProduct>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Convert list of DraftCombos to JSON string
     */
    fun combosToJson(combos: List<DraftCombo>): String {
        return gson.toJson(combos)
    }

    /**
     * Convert JSON string to list of DraftCombos
     */
    fun jsonToCombos(json: String): List<DraftCombo> {
        if (json.isBlank()) return emptyList()

        return try {
            gson.fromJson(json, object : TypeToken<List<DraftCombo>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        // Common fields
        private val CLIENT_NAME = stringPreferencesKey("draft_client_name")
        private val PHONE = stringPreferencesKey("draft_phone")
        private val STREET = stringPreferencesKey("draft_street")
        private val NUMERO = stringPreferencesKey("draft_numero")
        private val COLONIA = stringPreferencesKey("draft_colonia")
        private val POBLACION = stringPreferencesKey("draft_poblacion")
        private val CIUDAD = stringPreferencesKey("draft_ciudad")
        private val NOTE = stringPreferencesKey("draft_note")
        private val LATITUDE = doublePreferencesKey("draft_latitude")
        private val LONGITUDE = doublePreferencesKey("draft_longitude")
        private val IMAGE_URIS = stringPreferencesKey("draft_image_uris")
        private val PRODUCTS_JSON = stringPreferencesKey("draft_products_json")

        // Sale type
        private val TIPO_VENTA = stringPreferencesKey("draft_tipo_venta")

        // CREDITO-specific fields
        private val DOWNPAYMENT = stringPreferencesKey("draft_downpayment")
        private val INSTALLMENT = stringPreferencesKey("draft_installment")
        private val GUARANTOR = stringPreferencesKey("draft_guarantor")
        private val COLLECTION_DAY = stringPreferencesKey("draft_collection_day")
        private val PAYMENT_FREQUENCY = stringPreferencesKey("draft_payment_frequency")

        // Zone fields
        private val ZONA_CLIENTE_ID = intPreferencesKey("draft_zona_cliente_id")
        private val ZONA_CLIENTE_NOMBRE = stringPreferencesKey("draft_zona_cliente_nombre")

        // Combos
        private val COMBOS_JSON = stringPreferencesKey("draft_combos_json")

        private val TIMESTAMP = stringPreferencesKey("draft_timestamp")
    }

    /**
     * Save draft to DataStore
     * Saves all fields regardless of type - the UI will handle showing/hiding
     */
    suspend fun saveDraft(draft: SaleDraft) {
        // Don't save if there's no meaningful data
        if (!draft.hasData()) {
            return
        }

        context.saleDraftDataStore.edit { preferences ->
            // Common fields
            preferences[CLIENT_NAME] = draft.clientName
            preferences[PHONE] = draft.phone
            preferences[STREET] = draft.street
            preferences[NUMERO] = draft.numero
            preferences[COLONIA] = draft.colonia
            preferences[POBLACION] = draft.poblacion
            preferences[CIUDAD] = draft.ciudad
            preferences[NOTE] = draft.note
            preferences[LATITUDE] = draft.latitude
            preferences[LONGITUDE] = draft.longitude
            preferences[IMAGE_URIS] = gson.toJson(draft.imageUris)
            preferences[PRODUCTS_JSON] = draft.productsJson
            preferences[COMBOS_JSON] = draft.combosJson

            // Sale type
            preferences[TIPO_VENTA] = draft.tipoVenta

            // CREDITO fields (saved always, but only used if tipoVenta is CREDITO)
            preferences[DOWNPAYMENT] = draft.downpayment
            preferences[INSTALLMENT] = draft.installment
            preferences[GUARANTOR] = draft.guarantor
            preferences[COLLECTION_DAY] = draft.collectionDay
            preferences[PAYMENT_FREQUENCY] = draft.paymentFrequency

            // Zone fields
            draft.zonaClienteId?.let { preferences[ZONA_CLIENTE_ID] = it }
            preferences[ZONA_CLIENTE_NOMBRE] = draft.zonaClienteNombre

            preferences[TIMESTAMP] = draft.timestamp.toString()
        }
    }

    /**
     * Load draft from DataStore
     */
    suspend fun loadDraft(): SaleDraft? {
        val preferences = context.saleDraftDataStore.data.first()

        // Check if there's any meaningful data
        val hasData = preferences[CLIENT_NAME] != null ||
                     preferences[PHONE] != null ||
                     preferences[PRODUCTS_JSON] != null

        if (!hasData) {
            return null
        }

        val imageUrisJson = preferences[IMAGE_URIS] ?: "[]"
        val imageUrisList: List<String> = try {
            gson.fromJson(imageUrisJson, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }

        return SaleDraft(
            clientName = preferences[CLIENT_NAME] ?: "",
            phone = preferences[PHONE] ?: "",
            street = preferences[STREET] ?: "",
            numero = preferences[NUMERO] ?: "",
            colonia = preferences[COLONIA] ?: "",
            poblacion = preferences[POBLACION] ?: "",
            ciudad = preferences[CIUDAD] ?: "",
            note = preferences[NOTE] ?: "",
            latitude = preferences[LATITUDE] ?: 0.0,
            longitude = preferences[LONGITUDE] ?: 0.0,
            imageUris = imageUrisList,
            productsJson = preferences[PRODUCTS_JSON] ?: "",
            combosJson = preferences[COMBOS_JSON] ?: "",
            tipoVenta = preferences[TIPO_VENTA] ?: "CREDITO",
            downpayment = preferences[DOWNPAYMENT] ?: "",
            installment = preferences[INSTALLMENT] ?: "",
            guarantor = preferences[GUARANTOR] ?: "",
            collectionDay = preferences[COLLECTION_DAY] ?: "",
            paymentFrequency = preferences[PAYMENT_FREQUENCY] ?: "",
            zonaClienteId = preferences[ZONA_CLIENTE_ID],
            zonaClienteNombre = preferences[ZONA_CLIENTE_NOMBRE] ?: "",
            timestamp = preferences[TIMESTAMP]?.toLongOrNull() ?: 0L
        )
    }

    /**
     * Check if draft exists
     */
    fun hasDraft(): Flow<Boolean> {
        return context.saleDraftDataStore.data.map { preferences ->
            preferences[CLIENT_NAME] != null ||
            preferences[PHONE] != null ||
            preferences[PRODUCTS_JSON] != null
        }
    }

    /**
     * Clear draft from DataStore
     */
    suspend fun clearDraft() {
        context.saleDraftDataStore.edit { preferences ->
            preferences.clear()
        }

        // Also delete saved images
        val draftImagesDir = File(context.filesDir, "draft_images")
        if (draftImagesDir.exists()) {
            draftImagesDir.deleteRecursively()
        }
    }

    /**
     * Clear old drafts that are older than the specified number of days
     */
    suspend fun clearOldDrafts(maxAgeDays: Int = 7) {
        val draft = loadDraft() ?: return

        val draftAge = System.currentTimeMillis() - draft.timestamp
        val maxAgeMillis = maxAgeDays * 24 * 60 * 60 * 1000L

        if (draftAge > maxAgeMillis) {
            clearDraft()
        }
    }

    /**
     * Copy image to persistent storage and return new path
     */
    fun copyImageToPersistentStorage(uri: Uri): String {
        val draftImagesDir = File(context.filesDir, "draft_images")
        if (!draftImagesDir.exists()) {
            draftImagesDir.mkdirs()
        }

        val fileName = "draft_${System.currentTimeMillis()}.jpg"
        val destinationFile = File(draftImagesDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return destinationFile.absolutePath
    }
}
