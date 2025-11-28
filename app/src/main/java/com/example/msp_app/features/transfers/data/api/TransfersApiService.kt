package com.example.msp_app.features.transfers.data.api

import com.example.msp_app.features.transfers.data.api.dto.CostPreviewRequest
import com.example.msp_app.features.transfers.data.api.dto.CreateTransferRequest
import com.example.msp_app.features.transfers.data.api.dto.CreateTransferResponse
import com.example.msp_app.features.transfers.data.api.dto.ProductCostDto
import com.example.msp_app.features.transfers.data.api.dto.TransferDetailResponse
import com.example.msp_app.features.transfers.data.api.dto.TransferListItemDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API Service for warehouse transfers operations
 */
interface TransfersApiService {

    /**
     * Create a new transfer between warehouses
     * @param request Transfer creation data
     * @return Response with created transfer ID and details
     */
    @POST("traspasos")
    suspend fun createTransfer(
        @Body request: CreateTransferRequest
    ): Response<CreateTransferResponse>

    /**
     * Get list of transfers with optional filters
     * @param fechaInicio Start date filter (optional)
     * @param fechaFin End date filter (optional)
     * @param almacenOrigenId Source warehouse filter (optional)
     * @param almacenDestinoId Destination warehouse filter (optional)
     * @return List of transfers
     */
    @GET("traspasos")
    suspend fun getTransfers(
        @Query("fechaInicio") fechaInicio: String? = null,
        @Query("fechaFin") fechaFin: String? = null,
        @Query("almacenOrigenId") almacenOrigenId: Int? = null,
        @Query("almacenDestinoId") almacenDestinoId: Int? = null
    ): Response<List<TransferListItemDto>>

    /**
     * Get detailed information about a specific transfer
     * @param doctoInId Transfer document ID
     * @return Transfer details with movements
     */
    @GET("traspasos/{doctoInId}")
    suspend fun getTransferDetail(
        @Path("doctoInId") doctoInId: Int
    ): Response<TransferDetailResponse>

    /**
     * Get product costs preview for a warehouse
     * @param request Request with warehouse and product IDs
     * @return List of product costs
     */
    @POST("traspasos/costos")
    suspend fun getProductCosts(
        @Body request: CostPreviewRequest
    ): Response<List<ProductCostDto>>
}
