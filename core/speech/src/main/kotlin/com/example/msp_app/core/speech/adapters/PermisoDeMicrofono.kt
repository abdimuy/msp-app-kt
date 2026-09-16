package com.example.msp_app.core.speech.adapters

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **¿Hay `RECORD_AUDIO` ahora mismo?**
 *
 * Se pregunta **cada vez** y no se cachea: el permiso se puede revocar desde
 * ajustes mientras la app vive, y un valor guardado al arrancar pintaría un
 * micrófono que ya no puede grabar. Es el mismo criterio que
 * `BluetoothPrinterDiscovery` aplica a `BLUETOOTH_CONNECT`.
 *
 * Interfaz y no una función suelta porque los dos adaptadores la consultan y
 * los dos se prueban con un fake que dice "no" — el camino que importa.
 */
interface PermisoDeMicrofono {
    fun concedido(): Boolean
}

/** El de verdad. Pregunta al sistema, sin memoria. */
@Singleton
class PermisoDeMicrofonoDelSistema @Inject constructor(
    @ApplicationContext private val context: Context
) : PermisoDeMicrofono {

    override fun concedido(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}
