package com.eckscanner.data.local

import androidx.room.*

@Entity(tableName = "warehouses")
data class WarehouseEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val code: String?
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: Int,
    val code: String,
    val name: String,
    val barcode: String?,
    @ColumnInfo(name = "sale_price") val salePrice: Double,
    @ColumnInfo(name = "purchase_price") val purchasePrice: Double?,
    @ColumnInfo(name = "has_variants") val hasVariants: Boolean,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "brand_name") val brandName: String?,
    val image: String?,
    @ColumnInfo(name = "min_stock") val minStock: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: String?
)

@Entity(
    tableName = "variants",
    foreignKeys = [ForeignKey(
        entity = ProductEntity::class,
        parentColumns = ["id"],
        childColumns = ["product_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("product_id"), Index("sku")]
)
data class VariantEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "product_id") val productId: Int,
    val name: String,
    val sku: String,
    val price: Double?,
    val active: Boolean
)

@Entity(
    tableName = "stock",
    indices = [Index("product_id"), Index("variant_id"), Index("warehouse_id")],
    primaryKeys = ["product_id", "warehouse_id", "variant_id"]
)
data class StockEntity(
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "variant_id") val variantId: Int = 0,
    @ColumnInfo(name = "warehouse_id") val warehouseId: Int,
    val quantity: Double,
    val available: Double
)

data class ProductWithVariants(
    @Embedded val product: ProductEntity,
    @Relation(parentColumn = "id", entityColumn = "product_id")
    val variants: List<VariantEntity>
)
