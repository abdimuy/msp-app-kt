package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * El **marcador** de una captura (E-APP-084): la nota que el teléfono escribe en
 * cuanto el cobrador confirma, ANTES de cualquier espera cancelable, para que un
 * abono, una visita o una garantía no se pierda si la pantalla se cierra o el
 * proceso muere a media escritura.
 *
 * - `ID` es el mismo UUID de lo capturado (`Payment.ID`, la semilla de la visita,
 *   el id de la garantía).
 * - `ESTADO`: [CAPTURADA] al confirmar; [ESCRITA] en la MISMA transacción que
 *   escribe el dinero o el estado; [CERRADA] cuando la pantalla mostró el éxito
 *   y el cobrador siguió. No hay estado de descarte en v1 (decisión del dueño
 *   2026-10-20): una captura sin guardar se guarda o queda visible.
 * - `RESUMEN` es JSON opaco para esta capa (importe, método, comprobantes, hora
 *   de captura). Ningún cálculo de saldo lo lee.
 * - `DISCREPANCIA`: el envío no coincide con la oficina; una fila con
 *   discrepancia nunca se barre.
 */
@Entity(
    tableName = "capturas_en_curso",
    indices = [
        Index(
            value = ["TIPO", "LLAVE", "ESTADO"],
            name = "index_capturas_en_curso_tipo_llave_estado"
        )
    ]
)
data class CapturaEnCursoEntity(
    @PrimaryKey val ID: String,
    val TIPO: String,
    val LLAVE: String,
    val USUARIO: String,
    val ESTADO: String,
    val RESUMEN: String,
    val CREADA_EN: String,
    val ACTUALIZADA_EN: String,
    val DISCREPANCIA: String? = null,
    val DISCREPANCIA_EN: String? = null
) {
    companion object {
        const val TIPO_ABONO = "ABONO"
        const val TIPO_CONDONACION = "CONDONACION"
        const val TIPO_VENTA_LOCAL = "VENTA_LOCAL"
        const val TIPO_VISITA = "VISITA"
        const val TIPO_GARANTIA = "GARANTIA"

        const val CAPTURADA = "CAPTURADA"
        const val ESCRITA = "ESCRITA"
        const val CERRADA = "CERRADA"
    }
}
