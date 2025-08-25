package com.example.msp_app.data.models.auth

import androidx.annotation.Keep
import com.google.firebase.Timestamp

@Keep
data class User(
    val ID: String = "",
    val NOMBRE: String = "",
    val EMAIL: String = "",
    val CREATED_AT: Timestamp? = null,
    val COBRADOR_ID: Int = 0,
    val TELEFONO: String = "",
    val FECHA_CARGA_INICIAL: Timestamp? = null,
    val ZONA_CLIENTE_ID: Int = 0,
    val VERSION_APP: String = "",
    val FECHA_VERSION_APP: Timestamp? = null,
    val MODULOS: List<String> = emptyList(),
    val CAMIONETA_ASIGNADA: Int? = null
)