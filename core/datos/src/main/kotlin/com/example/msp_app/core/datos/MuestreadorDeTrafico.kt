package com.example.msp_app.core.datos

import android.net.TrafficStats

/** Contadores del sistema para el UID de la app. [UNSUPPORTED] = el teléfono no los da. */
interface FuenteDeTrafico {
    fun rx(): Long
    fun tx(): Long

    companion object {
        const val UNSUPPORTED = -1L // = TrafficStats.UNSUPPORTED
    }
}

/** `TrafficStats` del propio UID: no pide ningún permiso en ninguna versión de Android. */
class FuenteDeTraficoDelSistema(private val uid: Int) : FuenteDeTrafico {
    override fun rx(): Long = TrafficStats.getUidRxBytes(uid)
    override fun tx(): Long = TrafficStats.getUidTxBytes(uid)
}

/**
 * Convierte lecturas acumuladas de `TrafficStats` en deltas por día, red y plano.
 *
 * - La primera muestra solo fija la base (no atribuye lo anterior a hoy).
 * - Contador menor al anterior = el teléfono se reinició: el delta es el valor actual.
 * - Al cambiar de red o de plano se MUESTREA PRIMERO y luego se cambia el estado:
 *   lo transcurrido hasta el cambio queda con la red/plano de antes.
 * - Un delta cae en el día (zona de negocio) de la muestra; con muestreos cada
 *   minuto el error en medianoche es de a lo más un minuto.
 * - Nunca lanza: una fuente rota o `UNSUPPORTED` no cuenta nada.
 */
class MuestreadorDeTrafico(
    private val fuente: FuenteDeTrafico,
    private val registro: RegistroDeUso,
    redInicial: TipoDeRed = TipoDeRed.SIN_RED,
    primerPlanoInicial: Boolean = false
) {
    private val candado = Any()
    private var ultima: MuestraDeTrafico? = null
    private var red: TipoDeRed = redInicial
    private var primerPlano: Boolean = primerPlanoInicial

    fun ultimaMuestra(): MuestraDeTrafico? = synchronized(candado) { ultima }

    fun restaurarUltima(muestra: MuestraDeTrafico?) = synchronized(candado) {
        if (ultima == null) ultima = muestra
    }

    fun cambiarRed(nueva: TipoDeRed) = synchronized(candado) {
        muestrearSinCandado()
        red = nueva
    }

    fun cambiarPlano(nuevoPrimerPlano: Boolean) = synchronized(candado) {
        muestrearSinCandado()
        primerPlano = nuevoPrimerPlano
    }

    fun muestrear() = synchronized(candado) { muestrearSinCandado() }

    /** Ejecuta [bloque] con la base y el registro congelados (guardado consistente). */
    fun <T> congelado(bloque: (MuestraDeTrafico?) -> T): T =
        synchronized(candado) { bloque(ultima) }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun muestrearSinCandado() {
        try {
            val rx = fuente.rx()
            val tx = fuente.tx()
            // UNSUPPORTED (-1) cae aquí: negativo = sin soporte.
            if (rx < 0 || tx < 0) return
            val previa = ultima
            ultima = MuestraDeTrafico(rx, tx)
            if (previa == null) return
            val dRx = if (rx >= previa.rx) rx - previa.rx else rx
            val dTx = if (tx >= previa.tx) tx - previa.tx else tx
            registro.trafico(dRx, dTx, red, primerPlano)
        } catch (_: Throwable) {
            // Medición: nunca propaga.
        }
    }
}
