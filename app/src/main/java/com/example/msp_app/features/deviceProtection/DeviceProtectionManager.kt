package com.example.msp_app.features.deviceProtection

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.models.auth.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlinx.coroutines.tasks.await

class DeviceProtectionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val deviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    val deviceLabel: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun isDeviceAuthorized(user: User): Boolean {
        if (!user.DEVICE_PROTECTION_ENABLED) return true

        val authorized = user.AUTHORIZED_DEVICES.any { device ->
            device["deviceId"] == deviceId
        }

        if (authorized) {
            saveLocalAuthorization(user.ID, true)
        }

        return authorized
    }

    fun isDevicePending(user: User): Boolean {
        return user.PENDING_DEVICES.any { device ->
            device["deviceId"] == deviceId
        }
    }

    fun isLocallyAuthorized(userId: String): Boolean {
        return prefs.getBoolean("$KEY_AUTHORIZED_PREFIX$userId", false)
    }

    private fun saveLocalAuthorization(userId: String, authorized: Boolean) {
        prefs.edit().putBoolean("$KEY_AUTHORIZED_PREFIX$userId", authorized).apply()
    }

    fun clearLocalAuthorization(userId: String) {
        prefs.edit().remove("$KEY_AUTHORIZED_PREFIX$userId").apply()
    }

    suspend fun registerAsPending(user: User) {
        if (isDevicePending(user)) return

        try {
            val newPending = user.PENDING_DEVICES.toMutableList()
            newPending.add(
                mapOf(
                    "deviceId" to deviceId,
                    "platform" to "android",
                    "label" to deviceLabel,
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "brand" to Build.BRAND,
                    "androidVersion" to Build.VERSION.RELEASE,
                    "sdkVersion" to Build.VERSION.SDK_INT,
                    "product" to Build.PRODUCT,
                    "device" to Build.DEVICE,
                    "language" to Locale.getDefault().displayLanguage,
                    "requestedAt" to Timestamp.now(),
                    "userId" to user.ID
                )
            )

            FirebaseFirestore.getInstance()
                .collection(Constants.USERS_COLLECTION)
                .document(user.ID)
                .update("PENDING_DEVICES", newPending)
                .await()

            Log.d(TAG, "Device registered as pending: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering device as pending", e)
        }
    }

    companion object {
        private const val TAG = "DeviceProtection"
        private const val PREFS_NAME = "device_protection"
        private const val KEY_AUTHORIZED_PREFIX = "authorized_"
    }
}
