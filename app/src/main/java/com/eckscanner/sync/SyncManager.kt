package com.eckscanner.sync

import android.content.Context
import com.eckscanner.data.local.*
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.ProductDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class SyncManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val api get() = ApiClient.getService()

    data class SyncResult(
        val productsUpdated: Int = 0,
        val stockUpdated: Int = 0,
        val warehousesUpdated: Int = 0,
        val error: String? = null
    )

    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val warehouses = syncWarehouses()
            val products = syncProducts()
            val stock = syncStock()
            SyncResult(
                productsUpdated = products,
                stockUpdated = stock,
                warehousesUpdated = warehouses
            )
        } catch (e: Exception) {
            SyncResult(error = e.message ?: "Error desconocido")
        }
    }

    private suspend fun syncWarehouses(): Int {
        val response = api.getWarehouses()
        if (!response.isSuccessful) return 0
        val warehouses = response.body()?.data ?: return 0

        val entities = warehouses.map {
            WarehouseEntity(id = it.id, name = it.name, code = it.code)
        }
        db.warehouseDao().upsertAll(entities)
        return entities.size
    }

    private suspend fun syncProducts(): Int {
        // If DB is empty, do full sync (ignore updated_since)
        val productCount = db.productDao().getLastUpdatedAt()
        val lastSync = if (productCount == null) null else ApiClient.getLastProductSync(context)
        // Capture NOW before sync starts - save only if all pages succeed
        val syncStartTime = Instant.now().toString()
        var page = 1
        var totalUpdated = 0
        var completedSuccessfully = true

        while (true) {
            var shouldBreak = false
            try {
                val response = api.getProducts(
                    page = page,
                    perPage = 500,
                    updatedSince = lastSync
                )
                if (!response.isSuccessful) { completedSuccessfully = false; shouldBreak = true }
                else {
                    val body = response.body()
                    if (body == null) { completedSuccessfully = false; shouldBreak = true }
                    else {
                        val products = body.data
                        if (products.isEmpty()) shouldBreak = true
                        else {
                            upsertProducts(products)
                            totalUpdated += products.size
                            val meta = body.meta
                            if (meta == null || page >= meta.lastPage) shouldBreak = true
                            else page++
                        }
                    }
                }
            } catch (e: Exception) {
                completedSuccessfully = false
                shouldBreak = true
            }
            if (shouldBreak) break
        }

        // Only save timestamp if all pages succeeded - otherwise next sync will retry
        if (completedSuccessfully) {
            ApiClient.saveLastProductSync(context, syncStartTime)
        }
        return totalUpdated
    }

    private suspend fun upsertProducts(products: List<ProductDto>) {
        val productEntities = products.map { dto ->
            ProductEntity(
                id = dto.id,
                code = dto.code,
                name = dto.name,
                barcode = dto.barcode,
                salePrice = dto.salePrice,
                purchasePrice = dto.purchasePrice,
                hasVariants = dto.hasVariants,
                categoryName = dto.category?.name,
                brandName = dto.brand?.name,
                image = dto.image,
                minStock = dto.minStock ?: 0,
                updatedAt = dto.updatedAt
            )
        }
        db.productDao().upsertAll(productEntities)

        for (dto in products) {
            val variants = dto.variants?.map { v ->
                VariantEntity(
                    id = v.id,
                    productId = dto.id,
                    name = v.name,
                    sku = v.sku,
                    price = v.price,
                    active = v.active
                )
            } ?: continue
            if (variants.isNotEmpty()) {
                db.variantDao().upsertAll(variants)
            }
        }
    }

    private suspend fun syncStock(): Int {
        var page = 1
        var totalUpdated = 0
        var completedSuccessfully = true

        while (true) {
            var shouldBreak = false
            try {
                val response = api.getStock(page = page, perPage = 500)
                if (!response.isSuccessful) { completedSuccessfully = false; shouldBreak = true }
                else {
                    val body = response.body()
                    if (body == null) { completedSuccessfully = false; shouldBreak = true }
                    else {
                        val stockList = body.data
                        if (stockList.isEmpty()) shouldBreak = true
                        else {
                            val entities = stockList.mapNotNull { dto ->
                                val warehouseId = dto.warehouse?.id ?: return@mapNotNull null
                                StockEntity(
                                    productId = dto.productId,
                                    variantId = dto.variant?.id ?: 0,
                                    warehouseId = warehouseId,
                                    quantity = dto.quantity,
                                    available = dto.available
                                )
                            }
                            if (entities.isNotEmpty()) db.stockDao().upsertAll(entities)
                            totalUpdated += entities.size
                            val meta = body.meta
                            if (meta == null || page >= meta.lastPage) shouldBreak = true
                            else page++
                        }
                    }
                }
            } catch (e: Exception) {
                completedSuccessfully = false
                shouldBreak = true
            }
            if (shouldBreak) break
        }

        if (completedSuccessfully) {
            ApiClient.saveLastStockSync(context, Instant.now().toString())
        }
        return totalUpdated
    }
}
