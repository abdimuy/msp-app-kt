package com.example.msp_app.core.navegacion.application

import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio

/**
 * El texto de la tarjeta de envío. "hace N min" sólo acompaña a "Todo enviado"
 * (spec §2.1.1); con pendientes no aplica. Pasados 60 minutos cambia a horas y
 * pasadas 24 h a días: "hace 600 min" no se lee.
 */
object TextoDelEnvio {

    private const val MINUTOS_POR_HORA = 60L
    private const val MINUTOS_POR_DIA = 1_440L

    fun titulo(envio: EstadoDeEnvio): String {
        val pagos = envio.pagosPendientes
        val ventas = envio.ventasPendientes
        return when {
            pagos == 0 && ventas == 0 -> "Todo enviado"
            ventas == 0 -> "$pagos ${if (pagos == 1) "pago" else "pagos"} esperando señal"
            pagos == 0 -> "$ventas ${if (ventas == 1) "venta" else "ventas"} esperando señal"
            else -> "${pagos + ventas} envíos esperando señal"
        }
    }

    fun haceCuanto(envio: EstadoDeEnvio): String? {
        if (envio.estado != EstadoDeEnvio.Estado.TODO_ENVIADO) return null
        val minutos = envio.ultimoEnvioHaceMinutos ?: return null
        return when {
            minutos < 0 -> null
            minutos < 1 -> "hace un momento"
            minutos < MINUTOS_POR_HORA -> "hace $minutos min"
            minutos < MINUTOS_POR_DIA -> "hace ${minutos / MINUTOS_POR_HORA} h"
            else -> {
                val dias = minutos / MINUTOS_POR_DIA
                if (dias == 1L) "hace 1 día" else "hace $dias días"
            }
        }
    }

    fun completo(envio: EstadoDeEnvio): String =
        listOfNotNull(titulo(envio), haceCuanto(envio)).joinToString(" · ")
}
