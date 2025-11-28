package com.example.msp_app.features.transfers.data.local.entities

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room relation for transfer with its details
 * Used for querying complete transfer information
 */
data class TransferWithDetails(
    @Embedded
    val transfer: TransferEntity,

    @Relation(
        parentColumn = "docto_in_id",
        entityColumn = "docto_in_id"
    )
    val details: List<TransferDetailEntity>
)
