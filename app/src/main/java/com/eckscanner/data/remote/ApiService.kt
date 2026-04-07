package com.eckscanner.data.remote

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("api/v1/warehouses")
    suspend fun getWarehouses(): Response<WarehousesResponse>

    @GET("api/v1/products")
    suspend fun getProducts(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 500,
        @Query("updated_since") updatedSince: String? = null
    ): Response<PaginatedResponse<ProductDto>>

    @GET("api/v1/stock")
    suspend fun getStock(
        @Query("warehouse_id") warehouseId: Int? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 500,
        @Query("updated_since") updatedSince: String? = null
    ): Response<PaginatedResponse<StockDto>>

    @GET("api/v1/barcode/{code}")
    suspend fun lookupBarcode(@Path("code") code: String): Response<BarcodeResponse>

    @POST("api/v1/stock-adjustments")
    suspend fun createStockAdjustment(@Body body: StockAdjustmentRequest): Response<StockAdjustmentResponse>

    @POST("api/v1/transfers")
    suspend fun createTransfer(@Body body: CreateTransferRequest): Response<TransferResponse>

    @GET("api/v1/transfers")
    suspend fun getTransfers(
        @Query("status") status: String? = null,
        @Query("warehouse_id") warehouseId: Int? = null,
        @Query("per_page") perPage: Int = 50
    ): Response<PaginatedResponse<TransferDto>>

    @GET("api/v1/transfers/{id}")
    suspend fun getTransfer(@Path("id") id: Int): Response<TransferDetailResponse>

    @POST("api/v1/transfers/{id}/send")
    suspend fun sendTransfer(@Path("id") id: Int): Response<TransferResponse>

    @POST("api/v1/transfers/{id}/receive")
    suspend fun receiveTransfer(
        @Path("id") id: Int,
        @Body body: ReceiveTransferRequest? = null
    ): Response<TransferResponse>

    @POST("api/v1/transfers/{id}/cancel")
    suspend fun cancelTransfer(@Path("id") id: Int): Response<TransferResponse>

    // Shelves
    @GET("api/v1/shelves")
    suspend fun getShelves(
        @Query("warehouse_id") warehouseId: Int? = null
    ): Response<ShelvesResponse>

    @GET("api/v1/shelves/{id}")
    suspend fun getShelf(@Path("id") id: Int): Response<ShelfDetailResponse>

    @POST("api/v1/shelves/{id}/scan")
    suspend fun scanShelf(
        @Path("id") id: Int,
        @Body body: ShelfScanRequest
    ): Response<ShelfScanResponse>
}
