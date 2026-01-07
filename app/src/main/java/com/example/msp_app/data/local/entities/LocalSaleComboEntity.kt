package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "local_sale_combos",
    primaryKeys = ["COMBO_ID", "LOCAL_SALE_ID"],
    foreignKeys = [ForeignKey(
        entity = LocalSaleEntity::class,
        parentColumns = ["LOCAL_SALE_ID"],
        childColumns = ["LOCAL_SALE_ID"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["LOCAL_SALE_ID"])]
)
data class LocalSaleComboEntity(
    val COMBO_ID: String,
    val LOCAL_SALE_ID: String,
    val NOMBRE_COMBO: String,
    val PRECIO_LISTA: Double,
    val PRECIO_CORTO_PLAZO: Double,
    val PRECIO_CONTADO: Double
)
