package com.example.msp_app.core.common.sync.pendingwork.domain.ports

/**
 * Cuántas corridas seguidas lleva el barrido de fotos pospuesto por dinero
 * pendiente (válvula de la adenda de E-APP-079): sobrevive entre corridas de
 * la reconciliación inversa. Un pago atorado para siempre no puede posponer
 * el barrido para siempre.
 */
interface MemoriaDeBarridoPospuesto {
    /** Corridas seguidas pospuestas hasta ahora; 0 si no hay registro. */
    fun seguidas(): Int

    fun anotar(seguidas: Int)

    companion object {
        /** Sin memoria entre corridas (pruebas que no la miran): la válvula nunca abre. */
        val NINGUNA: MemoriaDeBarridoPospuesto = object : MemoriaDeBarridoPospuesto {
            override fun seguidas(): Int = 0
            override fun anotar(seguidas: Int) = Unit
        }
    }
}
