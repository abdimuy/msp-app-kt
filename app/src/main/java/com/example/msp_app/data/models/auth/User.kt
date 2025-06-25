package com.example.msp_app.data.models.auth

import com.google.firebase.Timestamp

data class User(
    val ID: String = "",
    val NOMBRE: String = "",
    val EMAIL: String = "",
    val CREATED_AT: Timestamp? = null,
    val COBRADOR_ID: Int = 0,
    val TELEFONO: String = "",
    val FECHA_CARGA_INICIAL: Timestamp? = null,
    val ZONA_CLIENTE_ID: Int = 0
)