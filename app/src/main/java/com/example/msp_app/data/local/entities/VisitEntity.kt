package com.example.msp_app.data.local.entities

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
        Index(value = ["LNG"]),
    ]
)
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
    @ColumnInfo(name = "GUARDADO_EN_MICROSIP") val GUARDADO_EN_MICROSIP: Int = 0
)