package com.eckscanner.data.repository

import android.content.Context
import com.eckscanner.data.local.*
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.ProductDto

class ProductRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val productDao = db.productDao()
    private val variantDao = db.variantDao()
    private val stockDao = db.stockDao()
    private val warehouseDao = db.warehouseDao()
    private val api get() = ApiClient.getService()

    // --- Local lookups (instant, offline) ---

    suspend fun findByCode(code: String): LookupResult? {
        // 1. Try exact variant SKU match (e.g., "52193094-298")
        val variant = variantDao.findBySku(code)
        if (variant != null) {
            val product = productDao.getById(variant.productId) ?: return null
            val stock = stockDao.getForProduct(variant.productId, variant.id)
            return LookupResult(product.product, variant, product.variants, stock)
        }

        // 2. Try hyphenated format: extract product code + find variant
        if (code.contains("-")) {
            val productCode = code.substringBefore("-")
            val pw = productDao.findByCode(productCode)
            if (pw != null) {
                // Try to match variant by full SKU or suffix
                val matchedVariant = pw.variants.find { it.sku == code }
                    ?: pw.variants.find { it.sku.endsWith("-" + code.substringAfter("-")) }

                if (matchedVariant != null) {
                    val stock = stockDao.getForProduct(pw.product.id, matchedVariant.id)
                    return LookupResult(pw.product, matchedVariant, pw.variants, stock)
                }

                // Variant code not matched but product found - show all variants with all stock
                val allStock = stockDao.getAllForProduct(pw.product.id)
                return LookupResult(pw.product, null, pw.variants, allStock)
            }
        }

        // 3. Try product code/barcode exact match
        val productWithVariants = productDao.findByCode(code)
        if (productWithVariants != null) {
            // If product has variants, get ALL stock (all variants combined)
            val stock = if (productWithVariants.product.hasVariants) {
                stockDao.getAllForProduct(productWithVariants.product.id)
            } else {
                stockDao.getForProduct(productWithVariants.product.id)
            }
            return LookupResult(
                productWithVariants.product,
                null,
                productWithVariants.variants,
                stock
            )
        }

        return null
    }

    suspend fun search(query: String): List<ProductWithVariants> {
        return productDao.search(query)
    }

    suspend fun getStockForProduct(productId: Int, variantId: Int = 0): List<StockEntity> {
        return stockDao.getForProduct(productId, variantId)
    }

    suspend fun getStockInWarehouse(productId: Int, variantId: Int = 0, warehouseId: Int): StockEntity? {
        return if (variantId == 0) {
            // No variant specified - sum all variants for this product
            stockDao.getTotalForProductInWarehouse(productId, warehouseId)
        } else {
            stockDao.getForProductInWarehouse(productId, variantId, warehouseId)
        }
    }

    // --- Online lookup (fallback when not in cache) ---

    sealed class OnlineResult {
        data class Found(val result: LookupResult) : OnlineResult()
        object NotFound : OnlineResult()
        data class NetworkError(val message: String) : OnlineResult()
    }

    suspend fun lookupOnline(code: String): OnlineResult {
        return try {
            val response = api.lookupBarcode(code)
            if (response.code() == 404) return OnlineResult.NotFound
            if (!response.isSuccessful) return OnlineResult.NetworkError("Error ${response.code()}")
            val body = response.body() ?: return OnlineResult.NotFound

            val product = ProductEntity(
                id = body.product.id,
                code = body.product.code,
                name = body.product.name,
                barcode = body.product.barcode,
                salePrice = body.product.salePrice,
                purchasePrice = body.product.purchasePrice,
                hasVariants = body.product.hasVariants,
                categoryName = null,
                brandName = null,
                image = body.product.image,
                minStock = 0,
                updatedAt = null
            )

            val variant = body.variant?.let {
                VariantEntity(
                    id = it.id, productId = body.product.id,
                    name = it.name, sku = it.sku, price = it.price, active = true
                )
            }

            val stock = body.stock.map {
                StockEntity(
                    productId = body.product.id,
                    variantId = variant?.id ?: 0,
                    warehouseId = it.warehouseId,
                    quantity = it.quantity,
                    available = it.available
                )
            }

            OnlineResult.Found(LookupResult(product, variant, emptyList(), stock))
        } catch (e: java.net.UnknownHostException) {
            OnlineResult.NetworkError("Sin conexion a internet")
        } catch (e: java.net.SocketTimeoutException) {
            OnlineResult.NetworkError("Tiempo de espera agotado")
        } catch (e: Exception) {
            OnlineResult.NetworkError(e.message ?: "Error de red")
        }
    }

    // --- Warehouses ---

    suspend fun getWarehouses(): List<WarehouseEntity> = warehouseDao.getAll()

    suspend fun getWarehouse(id: Int): WarehouseEntity? = warehouseDao.getById(id)
}

data class LookupResult(
    val product: ProductEntity,
    val matchedVariant: VariantEntity?,
    val allVariants: List<VariantEntity>,
    val stock: List<StockEntity>
)
