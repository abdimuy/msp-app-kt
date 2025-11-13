package com.example.msp_app.features.transfers.data.repository

import android.content.Context
import com.example.msp_app.core.logging.Logger
import com.example.msp_app.core.logging.logTransferError
import com.example.msp_app.features.transfers.data.api.TransfersApiService
import com.example.msp_app.features.transfers.data.api.dto.CostPreviewRequest
import com.example.msp_app.features.transfers.data.mappers.toRequest
import com.example.msp_app.features.transfers.domain.models.CreateTransferData
import com.example.msp_app.features.transfers.domain.models.ProductCost
import com.example.msp_app.features.transfers.domain.models.Transfer
import com.example.msp_app.features.transfers.domain.models.TransferFilters
import com.example.msp_app.features.transfers.domain.models.TransferWithDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/**
 * Repository for transfers operations
 * Handles data operations and business logic for warehouse transfers
 */
class TransfersRepository(
    private val apiService: TransfersApiService,
    private val context: Context? = null
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val logger by lazy {
        context?.let { Logger.get() }
    }

    /**
     * Create a new transfer
     */
    suspend fun createTransfer(data: CreateTransferData): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.createTransfer(data.toRequest())
                val httpCode = response.code()

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Result.success(body.doctoInId)
                    } else {
                        // ⚠️ CASO CRÍTICO: Response exitoso pero body es null
                        // El traspaso probablemente SÍ se creó pero no podemos confirmarlo
                        logger?.logTransferError(
                            almacenOrigenId = data.almacenOrigenId,
                            almacenDestinoId = data.almacenDestinoId,
                            productCount = data.productos.size,
                            httpCode = httpCode,
                            errorMessage = "Respuesta vacía del servidor",
                            isBodyNull = true,
                            additionalData = mapOf(
                                "headers" to response.headers().toString(),
                                "rawResponse" to response.raw().toString()
                            )
                        )
                        Result.failure(Exception("Respuesta vacía del servidor"))
                    }
                } else {
                    // ❌ ERROR HTTP (400, 500, etc)
                    val errorBody = response.errorBody()?.string()
                    logger?.logTransferError(
                        almacenOrigenId = data.almacenOrigenId,
                        almacenDestinoId = data.almacenDestinoId,
                        productCount = data.productos.size,
                        httpCode = httpCode,
                        errorMessage = parseErrorMessage(errorBody),
                        responseBody = errorBody
                    )
                    Result.failure(Exception(parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                // ❌ EXCEPCIÓN (timeout, network error, etc)
                logger?.logTransferError(
                    almacenOrigenId = data.almacenOrigenId,
                    almacenDestinoId = data.almacenDestinoId,
                    productCount = data.productos.size,
                    errorMessage = e.message ?: "Error desconocido",
                    exception = e
                )
                Result.failure(e)
            }
        }
    }

    /**
     * Get transfers list with optional filters
     */
    suspend fun getTransfers(filters: TransferFilters? = null): Result<List<Transfer>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getTransfers(
                    fechaInicio = filters?.fechaInicio?.format(dateFormatter),
                    fechaFin = filters?.fechaFin?.format(dateFormatter),
                    almacenOrigenId = filters?.almacenOrigenId,
                    almacenDestinoId = filters?.almacenDestinoId
                )

                if (response.isSuccessful) {
                    val transfers = response.body()?.map { dto ->
                        Transfer(
                            doctoInId = dto.doctoInId,
                            almacenOrigenId = dto.almacenId,
                            almacenDestinoId = dto.almacenDestinoId,
                            fecha = parseDateTimeSafe(dto.fecha),
                            descripcion = dto.descripcion,
                            folio = dto.folio,
                            usuario = dto.usuario,
                            almacenOrigenNombre = dto.almacen,
                            almacenDestinoNombre = dto.almacenDestino,
                            totalProductos = dto.totalProductos ?: 0,
                            costoTotal = dto.costoTotal ?: 0.0,
                            aplicado = dto.aplicado == "S",
                            sincronizado = true,
                            createdAt = java.time.LocalDateTime.now(),
                            updatedAt = java.time.LocalDateTime.now()
                        )
                    } ?: emptyList()

                    Result.success(transfers)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Result.failure(Exception(parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Get transfer details
     */
    suspend fun getTransferDetail(doctoInId: Int): Result<TransferWithDetails> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getTransferDetail(doctoInId)

                if (response.isSuccessful) {
                    val dto = response.body()
                    if (dto != null) {
                        val transfer = Transfer(
                            doctoInId = dto.doctoInId,
                            almacenOrigenId = dto.almacenId,
                            almacenDestinoId = dto.almacenDestinoId,
                            fecha = parseDateTimeSafe(dto.fecha),
                            descripcion = dto.descripcion,
                            folio = dto.folio,
                            usuario = dto.usuario,
                            almacenOrigenNombre = dto.almacen,
                            almacenDestinoNombre = dto.almacenDestino,
                            totalProductos = dto.salidas.size,
                            costoTotal = dto.salidas.sumOf { it.costoTotal },
                            aplicado = dto.aplicado == "S",
                            sincronizado = true,
                            createdAt = java.time.LocalDateTime.now(),
                            updatedAt = java.time.LocalDateTime.now()
                        )

                        val details = dto.salidas.map { movement ->
                            com.example.msp_app.features.transfers.domain.models.TransferDetail(
                                id = movement.movtoId?.toLong() ?: 0,
                                doctoInId = dto.doctoInId,
                                articuloId = movement.articuloId,
                                claveArticulo = movement.claveArticulo,
                                articuloNombre = movement.articulo,
                                descripcion1 = movement.descripcion1,
                                descripcion2 = movement.descripcion2,
                                unidades = movement.unidades,
                                costoUnitario = movement.costoUnitario,
                                costoTotal = movement.costoTotal,
                                tipoMovimiento = com.example.msp_app.features.transfers.domain.models.MovementType.fromCode(
                                    movement.tipoMovto
                                ),
                                movtoId = movement.movtoId
                            )
                        }

                        val transferWithDetails = TransferWithDetails(
                            transfer = transfer,
                            details = details
                        )

                        Result.success(transferWithDetails)
                    } else {
                        Result.failure(Exception("Traspaso no encontrado"))
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Result.failure(Exception(parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Get product costs preview
     */
    suspend fun getProductCosts(
        almacenId: Int,
        productIds: List<Int>
    ): Result<List<ProductCost>> {
        return withContext(Dispatchers.IO) {
            try {
                if (productIds.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }

                val request = CostPreviewRequest(
                    almacenId = almacenId,
                    articulosIds = productIds
                )

                val response = apiService.getProductCosts(request)

                if (response.isSuccessful) {
                    val costs = response.body()?.map {
                        ProductCost(
                            articuloId = it.articuloId,
                            costoUnitario = it.costoUnitario
                        )
                    } ?: emptyList()
                    Result.success(costs)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Result.failure(Exception(parseErrorMessage(errorBody)))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ===== Helper Functions =====

    private fun parseErrorMessage(errorBody: String?): String {
        return try {
            val gson = com.google.gson.Gson()
            val errorResponse = gson.fromJson(errorBody, ErrorResponse::class.java)
            errorResponse.message ?: errorResponse.error ?: "Error desconocido"
        } catch (e: Exception) {
            errorBody ?: "Error al procesar la solicitud"
        }
    }

    private fun parseDateTimeSafe(dateString: String): java.time.LocalDateTime {
        return try {
            java.time.LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        } catch (e: Exception) {
            try {
                val date = java.time.LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                date.atStartOfDay()
            } catch (e2: Exception) {
                java.time.LocalDateTime.now()
            }
        }
    }

    private data class ErrorResponse(
        val message: String?,
        val error: String?
    )
}
