package com.example.msp_app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Visit",
    indices = [
        Index(value = ["CLIENTE_ID"]),
        Index(value = ["COBRADOR_ID"]),
        Index(value = ["FECHA"]),
        Index(value = ["FORMA_COBRO_ID"]),
        Index(value = ["ZONA_CLIENTE_ID"]),
        Index(value = ["IMPTE_DOCTO_CC_ID"]),
        Index(value = ["GUARDADO_EN_MICROSIP"]),
        Index(value = ["TIPO_VISITA"]),
        Index(value = ["LAT"]),
        Index(value = ["LNG"])
    ]
)
/**
 * Una visita de cobranza capturada en el teléfono.
 *
 * La migración 29→30 le agrega **cinco columnas, todas nullable**
 * (`PROMESA_VENTA_ID`, `PROMESA_FECHA`, `PROMESA_MONTO_CENTAVOS`,
 * `CITA_FECHA`, `CITA_HORA`). Ninguna columna preexistente se toca.
 *
 * **Cardinalidad — por qué columnas y no tabla hija:** una visita produce a lo
 * más UNA promesa y UNA cita: son el resultado de esa visita, no una lista.
 * El dominio ya lo tiene decidido y en verde —
 * `VisitaEnVentana.fechaPromesa`/`montoPrometido`/`horaCita` son singulares y
 * `EstadoCuentaDeriver` los lee así— y una tabla hija contradiría a ese
 * consumidor ya escrito. Además, columnas hacen que la promesa se escriba en
 * el MISMO `INSERT` que la visita: no hay ventana en la que exista una visita
 * con promesa a medias. Si algún día una visita necesitara varias promesas, se
 * podría agregar la tabla hija de forma aditiva sin tocar estas columnas.
 */
data class VisitEntity(
    @PrimaryKey val ID: String,
    @ColumnInfo(name = "CLIENTE_ID") val CLIENTE_ID: Int,
    @ColumnInfo(name = "COBRADOR") val COBRADOR: String = "",
    @ColumnInfo(name = "COBRADOR_ID") val COBRADOR_ID: Int,
    @ColumnInfo(name = "FECHA") val FECHA: String,
    @ColumnInfo(name = "FORMA_COBRO_ID") val FORMA_COBRO_ID: Int,
    @ColumnInfo(name = "LAT") val LAT: Double,
    @ColumnInfo(name = "LNG") val LNG: Double,
    @ColumnInfo(name = "NOTA") val NOTA: String? = null,
    @ColumnInfo(name = "TIPO_VISITA") val TIPO_VISITA: String,
    @ColumnInfo(name = "ZONA_CLIENTE_ID") val ZONA_CLIENTE_ID: Int,
    @ColumnInfo(name = "IMPTE_DOCTO_CC_ID") val IMPTE_DOCTO_CC_ID: Int = 0,
    @ColumnInfo(name = "GUARDADO_EN_MICROSIP") val GUARDADO_EN_MICROSIP: Int = 0,
    /**
     * Venta a la que aplica la promesa. `NULL` = la promesa es del cliente
     * completo (el cobrador toca una puerta, no una venta); un valor = esa
     * venta en particular, que puede no ser [IMPTE_DOCTO_CC_ID] cuando el
     * cliente promete sobre otra de sus cuentas.
     */
    @ColumnInfo(name = "PROMESA_VENTA_ID") val PROMESA_VENTA_ID: Int? = null,
    /**
     * Día en que el cliente prometió pagar, `yyyy-MM-dd` en la zona de
     * negocio (`AppTime`). Es la mitad "cuándo" de la promesa estructurada:
     * sin ella, *Prometió: próxima* sigue siendo inderivable (§13).
     */
    @ColumnInfo(name = "PROMESA_FECHA") val PROMESA_FECHA: String? = null,
    /**
     * Monto prometido en **centavos** enteros. `Long`, nunca `Double`/`Float`:
     * es dinero y `0.1 + 0.2 != 0.3`. Ojo: es una **convención que este módulo
     * sigue**, no una compuerta que lo obligue — la regla `NoDoubleForMoney`
     * solo está aplicada a `:core:common`, y no puede activarse aquí porque
     * `PaymentEntity.IMPORTE` es un `Double` preexistente que la regla de
     * inmutabilidad prohíbe cambiar. `Long` (centavos) es una de las tres
     * formas que esa regla nombra como correctas. El adaptador lo cruza a
     * `BigDecimal.valueOf(centavos, 2)` para `VisitaEnVentana.montoPrometido`,
     * exacto y sin puente por flotante.
     *
     * `NULL` = prometió una fecha pero no un monto — un caso real de campo, no
     * un cero: cero significaría "prometió no pagar".
     */
    @ColumnInfo(name = "PROMESA_MONTO_CENTAVOS") val PROMESA_MONTO_CENTAVOS: Long? = null,
    /**
     * Día de la cita, `yyyy-MM-dd`. Con [CITA_HORA] cubre los tres casos del
     * mock: hoy con hora, otro día con hora, y otro día sin hora.
     */
    @ColumnInfo(name = "CITA_FECHA") val CITA_FECHA: String? = null,
    /**
     * Hora de la cita, `HH:mm` en la zona de negocio.
     *
     * **Corrección (Arreglo C).** Este KDoc decía que la hora *"solo existe
     * embebida en el texto de [NOTA] (`NewVisitDialog.kt:119-125`)"*. Las dos
     * mitades quedaron falsas: la Task 19 escribe esta columna
     * (`RegistroDeVisitaAdapter`), la lectura de cobranza la consume
     * (`RoomVisitasAdapter`), y `NewVisitDialog` lo borró la Task 21. Quien
     * leyera esto concluiría que *Cita a una hora* no se puede derivar, que es
     * el razonamiento equivocado que ya costó una vuelta entera en la Task 17.
     */
    @ColumnInfo(name = "CITA_HORA") val CITA_HORA: String? = null
)
