package com.example.msp_app.core.updates

import android.util.Log
import com.example.msp_app.core.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UpdateInfo(
    val latestVersion: String,
    val apkUrl: String
)

object UpdateChecker {

    private const val FIELD_LATEST_VERSION = "LATEST_VERSION"
    private const val FIELD_APK_URL = "APK_URL"

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable

    init {
        FirebaseFirestore.getInstance()
            .collection(Constants.COLLECTION_CONFIG)
            .document(Constants.DOCUMENT_API_SETTINGS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("UpdateChecker", "Error checking for updates: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val latestVersion = snapshot.getString(FIELD_LATEST_VERSION)
                    val apkUrl = snapshot.getString(FIELD_APK_URL)

                    if (latestVersion != null &&
                        apkUrl != null &&
                        latestVersion != Constants.APP_VERSION
                    ) {
                        _updateAvailable.value = UpdateInfo(latestVersion, apkUrl)
                    } else {
                        _updateAvailable.value = null
                    }
                }
            }
    }
}
