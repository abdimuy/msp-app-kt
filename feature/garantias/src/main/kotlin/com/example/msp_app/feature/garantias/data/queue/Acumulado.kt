package com.example.msp_app.feature.garantias.data.queue

import com.example.msp_app.feature.garantias.domain.port.ResultadoDeLaCola

/**
 * El marcador de una pasada mientras corre.
 *
 * [ResultadoDeLaCola] es inmutable porque es la salida; este acumulador es
 * mutable porque el procesador lo va llenando comando a comando. Se mantiene
 * aparte para que ninguna prueba pueda construir un resultado a mano y para
 * que la lógica de conteo viva en un solo lugar.
 *
 * Los contadores de pendientes no se acumulan: se leen de la cola al final,
 * porque un comando puede quedar pendiente por un rechazo, por falta de red o
 * porque nunca se intentó en esta pasada.
 */
internal class Acumulado {

    private var enviados = 0
    private var rechazados = 0
    private var fotosSubidas = 0
    private var fotosRechazadas = 0
    private var sinRed = false

    fun comandoEnviado() {
        enviados++
    }

    fun comandoRechazado() {
        rechazados++
    }

    fun fotoSubida() {
        fotosSubidas++
    }

    fun fotoRechazada() {
        fotosRechazadas++
    }

    fun marcarSinRed() {
        sinRed = true
    }

    fun resultado(comandosPendientes: Int, fotosPendientes: Int): ResultadoDeLaCola =
        ResultadoDeLaCola(
            comandosEnviados = enviados,
            comandosRechazados = rechazados,
            fotosSubidas = fotosSubidas,
            fotosRechazadas = fotosRechazadas,
            comandosPendientes = comandosPendientes,
            fotosPendientes = fotosPendientes,
            sinRed = sinRed
        )
}
