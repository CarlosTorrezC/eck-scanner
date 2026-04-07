package com.eckscanner.data.local

import androidx.room.*

@Dao
interface ProductDao {
    @Transaction
    @Query("SELECT * FROM products WHERE code = :code OR barcode = :code LIMIT 1")
    suspend fun findByCode(code: String): ProductWithVariants?

    @Transaction
    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Int): ProductWithVariants?

    @Transaction
    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%' LIMIT 20")
    suspend fun search(query: String): List<ProductWithVariants>

    @Query("SELECT MAX(updated_at) FROM products")
    suspend fun getLastUpdatedAt(): String?

    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE id NOT IN (:activeIds)")
    suspend fun deleteNotIn(activeIds: List<Int>)
}

@Dao
interface VariantDao {
    @Query("SELECT * FROM variants WHERE sku = :sku LIMIT 1")
    suspend fun findBySku(sku: String): VariantEntity?

    @Query("SELECT * FROM variants WHERE product_id = :productId AND active = 1")
    suspend fun getByProductId(productId: Int): List<VariantEntity>

    @Upsert
    suspend fun upsertAll(variants: List<VariantEntity>)

    @Query("DELETE FROM variants WHERE product_id = :productId AND id NOT IN (:activeIds)")
    suspend fun deleteNotIn(productId: Int, activeIds: List<Int>)
}

@Dao
interface StockDao {
    @Query("""
        SELECT * FROM stock
        WHERE product_id = :productId AND variant_id = :variantId
    """)
    suspend fun getForProduct(productId: Int, variantId: Int = 0): List<StockEntity>

    @Query("""
        SELECT * FROM stock
        WHERE product_id = :productId AND variant_id = :variantId AND warehouse_id = :warehouseId
        LIMIT 1
    """)
    suspend fun getForProductInWarehouse(productId: Int, variantId: Int = 0, warehouseId: Int): StockEntity?

    @Upsert
    suspend fun upsertAll(stock: List<StockEntity>)

    @Query("DELETE FROM stock")
    suspend fun deleteAll()
}

@Dao
interface WarehouseDao {
    @Query("SELECT * FROM warehouses ORDER BY name")
    suspend fun getAll(): List<WarehouseEntity>

    @Query("SELECT * FROM warehouses WHERE id = :id")
    suspend fun getById(id: Int): WarehouseEntity?

    @Upsert
    suspend fun upsertAll(warehouses: List<WarehouseEntity>)
}
