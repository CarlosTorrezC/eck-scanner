package com.eckscanner.data.remote

import com.google.gson.annotations.SerializedName

// --- Responses ---

data class PaginatedResponse<T>(
    val data: List<T>,
    val meta: PaginationMeta?
)

data class PaginationMeta(
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("last_page") val lastPage: Int,
    val total: Int
)

data class WarehousesResponse(
    val data: List<WarehouseDto>
)

data class BarcodeResponse(
    val product: BarcodeProductDto,
    val variant: BarcodeVariantDto?,
    val stock: List<BarcodeStockDto>,
    val locations: List<BarcodeLocationDto>?
)

data class BarcodeLocationDto(
    @SerializedName("shelf_id") val shelfId: Int,
    @SerializedName("shelf_name") val shelfName: String?,
    @SerializedName("shelf_code") val shelfCode: String?,
    @SerializedName("warehouse_id") val warehouseId: Int,
    @SerializedName("warehouse_name") val warehouseName: String?
)

data class StockAdjustmentResponse(
    val message: String,
    val adjustment: AdjustmentDto?
)

data class TransferResponse(
    val data: TransferDto?,
    val transfer: TransferDto?,
    val message: String?
) {
    fun resolveTransfer(): TransferDto? = data ?: transfer
}

data class TransferDetailResponse(
    val data: TransferDto
)

// --- DTOs ---

data class WarehouseDto(
    val id: Int,
    val name: String,
    val code: String?
)

data class ProductDto(
    val id: Int,
    val code: String,
    val name: String,
    val barcode: String?,
    @SerializedName("sale_price") val salePrice: Double,
    @SerializedName("purchase_price") val purchasePrice: Double?,
    @SerializedName("has_variants") val hasVariants: Boolean,
    val category: NamedDto?,
    val brand: NamedDto?,
    val image: String?,
    @SerializedName("min_stock") val minStock: Int?,
    val variants: List<VariantDto>?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class VariantDto(
    val id: Int,
    val name: String,
    val sku: String,
    val price: Double?,
    val active: Boolean
)

data class NamedDto(
    val id: Int,
    val name: String
)

data class StockDto(
    val id: Int,
    @SerializedName("product_id") val productId: Int,
    val product: StockProductDto?,
    val variant: StockVariantDto?,
    val warehouse: NamedDto?,
    val quantity: Double,
    val available: Double
)

data class StockProductDto(
    val id: Int,
    val code: String,
    val name: String
)

data class StockVariantDto(
    val id: Int,
    val name: String,
    val sku: String
)

data class BarcodeProductDto(
    val id: Int,
    val code: String,
    val name: String,
    val barcode: String?,
    @SerializedName("sale_price") val salePrice: Double,
    @SerializedName("purchase_price") val purchasePrice: Double?,
    @SerializedName("has_variants") val hasVariants: Boolean,
    val image: String?
)

data class BarcodeVariantDto(
    val id: Int,
    val name: String,
    val sku: String,
    val price: Double?
)

data class BarcodeStockDto(
    @SerializedName("warehouse_id") val warehouseId: Int,
    val warehouse: String,
    val quantity: Double,
    val available: Double
)

// --- Requests ---

data class StockAdjustmentRequest(
    @SerializedName("warehouse_id") val warehouseId: Int,
    val reason: String = "inventario",
    val notes: String? = null,
    val items: List<AdjustmentItemRequest>
)

data class AdjustmentItemRequest(
    @SerializedName("product_id") val productId: Int,
    @SerializedName("variant_id") val variantId: Int? = null,
    val type: String,
    val quantity: Double
)

data class CreateTransferRequest(
    @SerializedName("from_warehouse_id") val fromWarehouseId: Int,
    @SerializedName("to_warehouse_id") val toWarehouseId: Int,
    val notes: String? = null,
    val items: List<TransferItemRequest>
)

data class TransferItemRequest(
    @SerializedName("product_id") val productId: Int,
    @SerializedName("variant_id") val variantId: Int? = null,
    val quantity: Double
)

data class ReceiveTransferRequest(
    val items: List<ReceiveItemRequest>?
)

data class ReceiveItemRequest(
    @SerializedName("detail_id") val detailId: Int,
    @SerializedName("received_quantity") val receivedQuantity: Double
)

// --- Transfer ---

data class TransferDto(
    val id: Int,
    val code: String,
    @SerializedName("from_warehouse") val fromWarehouse: NamedDto,
    @SerializedName("to_warehouse") val toWarehouse: NamedDto,
    val status: String,
    @SerializedName("status_label") val statusLabel: String?,
    val notes: String?,
    val details: List<TransferDetailDto>?,
    val user: NamedDto?,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("created_at") val createdAt: String
)

data class TransferDetailDto(
    val id: Int,
    val product: StockProductDto,
    val variant: StockVariantDto?,
    val quantity: Double,
    @SerializedName("received_quantity") val receivedQuantity: Double?
)

data class AdjustmentDto(
    val code: String,
    val items: List<AdjustmentResultItem>?
)

data class AdjustmentResultItem(
    @SerializedName("product_id") val productId: Int,
    @SerializedName("variant_id") val variantId: Int?,
    val type: String,
    val quantity: Double,
    val balance: Double?
)

// --- Price Update ---

data class PriceUpdateRequest(
    @SerializedName("product_id") val productId: Int,
    @SerializedName("variant_id") val variantId: Int? = null,
    @SerializedName("sale_price") val salePrice: Double
)

data class PriceUpdateResponse(
    val message: String,
    @SerializedName("product_id") val productId: Int?,
    @SerializedName("variant_id") val variantId: Int?,
    @SerializedName("old_price") val oldPrice: Double?,
    @SerializedName("new_price") val newPrice: Double?
)

// --- Shelves ---

data class ShelvesResponse(
    val data: List<ShelfDto>
)

data class ShelfDetailResponse(
    val data: ShelfWithItemsDto
)

data class ShelfScanResponse(
    val message: String,
    val assigned: Int?
)

data class ShelfDto(
    val id: Int,
    val name: String,
    val code: String?,
    val warehouse: NamedDto,
    @SerializedName("products_count") val productsCount: Int,
    @SerializedName("last_scanned_at") val lastScannedAt: String?
)

data class ShelfWithItemsDto(
    val id: Int,
    val name: String,
    val code: String?,
    val warehouse: NamedDto,
    @SerializedName("last_scanned_at") val lastScannedAt: String?,
    val items: List<ShelfItemDto>
)

data class ShelfItemDto(
    @SerializedName("product_id") val productId: Int,
    @SerializedName("variant_id") val variantId: Int?,
    @SerializedName("product_code") val productCode: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("variant_name") val variantName: String?,
    @SerializedName("variant_sku") val variantSku: String?,
    @SerializedName("warehouse_stock") val warehouseStock: Double,
    @SerializedName("other_shelves") val otherShelves: List<String>?
)

data class ShelfScanRequest(
    val items: List<ShelfScanItemRequest>
)

data class ShelfScanItemRequest(
    @SerializedName("product_id") val productId: Int,
    @SerializedName("variant_id") val variantId: Int? = null
)
