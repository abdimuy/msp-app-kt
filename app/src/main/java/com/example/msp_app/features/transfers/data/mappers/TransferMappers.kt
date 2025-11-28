package com.example.msp_app.features.transfers.data.mappers

import com.example.msp_app.features.transfers.data.api.dto.CreateTransferRequest
import com.example.msp_app.features.transfers.data.api.dto.MovementDto
import com.example.msp_app.features.transfers.data.api.dto.ProductCostDto
import com.example.msp_app.features.transfers.data.api.dto.TransferDetailItemDto
import com.example.msp_app.features.transfers.data.api.dto.TransferDetailResponse
import com.example.msp_app.features.transfers.data.api.dto.TransferListItemDto
import com.example.msp_app.features.transfers.data.local.entities.PendingTransferEntity
import com.example.msp_app.features.transfers.data.local.entities.TransferDetailEntity
import com.example.msp_app.features.transfers.data.local.entities.TransferEntity
import com.example.msp_app.features.transfers.domain.models.CreateTransferData
import com.example.msp_app.features.transfers.domain.models.MovementType
import com.example.msp_app.features.transfers.domain.models.ProductCost
import com.example.msp_app.features.transfers.domain.models.Transfer
import com.example.msp_app.features.transfers.domain.models.TransferDetail
import com.example.msp_app.features.transfers.domain.models.TransferProductItem
import com.google.gson.Gson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.example.msp_app.features.transfers.data.local.entities.TransferWithDetails as TransferWithDetailsEntity
import com.example.msp_app.features.transfers.domain.models.TransferWithDetails as TransferWithDetailsDomain

/**
 * Mappers for Transfer domain
 * Follows clean architecture principles by separating data and domain layers
 */

// ===== Date/Time Formatters =====
private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

// ===== DTO to Entity Mappers =====

fun TransferListItemDto.toEntity(): TransferEntity {
    return TransferEntity(
        doctoInId = doctoInId,
        almacenOrigenId = almacenId,
        almacenDestinoId = almacenDestinoId,
        fecha = fecha,
        descripcion = descripcion,
        folio = folio,
        usuario = usuario,
        aplicado = aplicado,
        almacenOrigenNombre = almacen,
        almacenDestinoNombre = almacenDestino,
        totalProductos = totalProductos ?: 0,
        costoTotal = costoTotal ?: 0.0,
        sincronizado = true,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}

fun TransferDetailResponse.toEntity(): TransferEntity {
    return TransferEntity(
        doctoInId = doctoInId,
        almacenOrigenId = almacenId,
        almacenDestinoId = almacenDestinoId,
        fecha = fecha,
        descripcion = descripcion,
        folio = folio,
        usuario = usuario,
        aplicado = aplicado,
        almacenOrigenNombre = almacen,
        almacenDestinoNombre = almacenDestino,
        totalProductos = salidas.size,
        costoTotal = salidas.sumOf { it.costoTotal },
        sincronizado = true,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}

fun MovementDto.toDetailEntity(doctoInId: Int): TransferDetailEntity {
    return TransferDetailEntity(
        doctoInId = doctoInId,
        articuloId = articuloId,
        claveArticulo = claveArticulo,
        articuloNombre = articulo,
        descripcion1 = descripcion1,
        descripcion2 = descripcion2,
        unidades = unidades,
        costoUnitario = costoUnitario,
        costoTotal = costoTotal,
        tipoMovimiento = tipoMovto,
        movtoId = movtoId
    )
}

// ===== Entity to Domain Mappers =====

fun TransferEntity.toDomain(): Transfer {
    return Transfer(
        doctoInId = doctoInId,
        almacenOrigenId = almacenOrigenId,
        almacenDestinoId = almacenDestinoId,
        fecha = parseDateTimeSafe(fecha),
        descripcion = descripcion,
        folio = folio,
        usuario = usuario,
        almacenOrigenNombre = almacenOrigenNombre,
        almacenDestinoNombre = almacenDestinoNombre,
        totalProductos = totalProductos,
        costoTotal = costoTotal,
        aplicado = aplicado == "S",
        sincronizado = sincronizado,
        createdAt = LocalDateTime.ofEpochSecond(createdAt / 1000, 0, java.time.ZoneOffset.UTC),
        updatedAt = LocalDateTime.ofEpochSecond(updatedAt / 1000, 0, java.time.ZoneOffset.UTC)
    )
}

fun TransferDetailEntity.toDomain(): TransferDetail {
    return TransferDetail(
        id = id,
        doctoInId = doctoInId,
        articuloId = articuloId,
        claveArticulo = claveArticulo,
        articuloNombre = articuloNombre,
        descripcion1 = descripcion1,
        descripcion2 = descripcion2,
        unidades = unidades,
        costoUnitario = costoUnitario,
        costoTotal = costoTotal,
        tipoMovimiento = MovementType.fromCode(tipoMovimiento),
        movtoId = movtoId
    )
}

fun TransferWithDetailsEntity.toDomain(): TransferWithDetailsDomain {
    return TransferWithDetailsDomain(
        transfer = transfer.toDomain(),
        details = details.map { it.toDomain() }
    )
}

// ===== Domain to DTO Mappers =====

fun CreateTransferData.toRequest(): CreateTransferRequest {
    return CreateTransferRequest(
        almacenOrigenId = almacenOrigenId,
        almacenDestinoId = almacenDestinoId,
        fecha = fecha.format(dateTimeFormatter),
        descripcion = descripcion,
        usuario = usuario,
        detalles = productos.map { it.toDto() }
    )
}

fun TransferProductItem.toDto(): TransferDetailItemDto {
    return TransferDetailItemDto(
        articuloId = articuloId,
        claveArticulo = claveArticulo,
        unidades = unidades
    )
}

// ===== Domain to Entity Mappers (for offline) =====

fun CreateTransferData.toPendingEntity(
    almacenOrigenNombre: String? = null,
    almacenDestinoNombre: String? = null
): PendingTransferEntity {
    val gson = Gson()
    return PendingTransferEntity(
        almacenOrigenId = almacenOrigenId,
        almacenDestinoId = almacenDestinoId,
        fecha = fecha.format(dateTimeFormatter),
        descripcion = descripcion,
        usuario = usuario,
        almacenOrigenNombre = almacenOrigenNombre,
        almacenDestinoNombre = almacenDestinoNombre,
        detallesJson = gson.toJson(productos.map { it.toDto() })
    )
}

// ===== DTO to Domain Mappers =====

fun ProductCostDto.toDomain(): ProductCost {
    return ProductCost(
        articuloId = articuloId,
        costoUnitario = costoUnitario
    )
}

// ===== Helper Functions =====

private fun parseDateTimeSafe(dateString: String): LocalDateTime {
    return try {
        LocalDateTime.parse(dateString, dateTimeFormatter)
    } catch (e: Exception) {
        try {
            // Try with date only
            val date = java.time.LocalDate.parse(dateString, dateFormatter)
            date.atStartOfDay()
        } catch (e2: Exception) {
            LocalDateTime.now()
        }
    }
}

// ===== List Extension Functions =====

fun List<TransferListItemDto>.toEntities(): List<TransferEntity> = map { it.toEntity() }
fun List<TransferEntity>.toDomainTransfers(): List<Transfer> = map { it.toDomain() }
fun List<TransferDetailEntity>.toDomainDetails(): List<TransferDetail> = map { it.toDomain() }
fun List<TransferWithDetailsEntity>.toDomainWithDetails(): List<TransferWithDetailsDomain> = map { it.toDomain() }
fun List<ProductCostDto>.toDomainCosts(): List<ProductCost> = map { it.toDomain() }
