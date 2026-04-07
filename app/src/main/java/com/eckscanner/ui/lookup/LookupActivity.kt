package com.eckscanner.ui.lookup

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.eckscanner.R
import com.eckscanner.data.local.AppDatabase
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.repository.LookupResult
import com.eckscanner.data.repository.ProductRepository
import com.eckscanner.databinding.ActivityLookupBinding
import com.eckscanner.scanner.DataWedgeReceiver
import com.eckscanner.scanner.ScanFeedback
import coil.load
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.launch

class LookupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLookupBinding
    private lateinit var repository: ProductRepository
    private lateinit var scanReceiver: DataWedgeReceiver
    private var warehouseCache: Map<Int, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLookupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProductRepository(this)
        binding.toolbar.setNavigationOnClickListener { finish() }

        scanReceiver = DataWedgeReceiver { code ->
            runOnUiThread { lookup(code) }
        }

        // Pre-load warehouse names to avoid N+1 queries
        lifecycleScope.launch {
            val warehouses = AppDatabase.getInstance(this@LookupActivity).warehouseDao().getAll()
            warehouseCache = warehouses.associate { it.id to it.name }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanReceiver, DataWedgeReceiver.getIntentFilter())
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(scanReceiver) } catch (_: Exception) {}
    }

    private var lastBarcodeResponse: com.eckscanner.data.remote.BarcodeResponse? = null

    private fun lookup(code: String) {
        lastBarcodeResponse = null
        lifecycleScope.launch {
            val localResult = repository.findByCode(code)
            if (localResult != null) {
                ScanFeedback.success(this@LookupActivity)
                // Fetch online in background for locations (single call, cached)
                fetchBarcodeOnline(code)
                showResult(localResult)
                return@launch
            }

            // Fallback to online
            when (val online = repository.lookupOnline(code)) {
                is ProductRepository.OnlineResult.Found -> {
                    ScanFeedback.success(this@LookupActivity)
                    showResult(online.result)
                }
                is ProductRepository.OnlineResult.NotFound -> {
                    ScanFeedback.error(this@LookupActivity)
                    showNotFound()
                }
                is ProductRepository.OnlineResult.NetworkError -> {
                    ScanFeedback.error(this@LookupActivity)
                    showError(online.message)
                }
            }
        }
    }

    /** Single API call for locations - reuses response, no duplicate */
    private fun fetchBarcodeOnline(code: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getService().lookupBarcode(code)
                if (response.isSuccessful) {
                    lastBarcodeResponse = response.body()
                    showLocations()
                }
            } catch (_: Exception) { }
        }
    }

    private fun showResult(result: LookupResult) {
        binding.txtScanPrompt.visibility = View.GONE
        binding.txtNotFound.visibility = View.GONE
        binding.scrollResult.visibility = View.VISIBLE

        // Price (safe for null variant prices)
        val price = result.matchedVariant?.price ?: result.product.salePrice
        binding.txtPrice.text = String.format("%.2f", price ?: 0.0)

        // Product image
        val image = result.product.image
        if (!image.isNullOrEmpty()) {
            val config = ApiClient.getConfig(this)
            val baseUrl = config?.first?.trimEnd('/') ?: ""
            val imageUrl = "$baseUrl/storage/$image"
            binding.imgProduct.visibility = View.VISIBLE
            binding.imgProduct.load(imageUrl) {
                crossfade(false)
                transformations(RoundedCornersTransformation(8f))
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
            }
        } else {
            binding.imgProduct.visibility = View.GONE
        }

        // Product info
        binding.txtProductName.text = result.product.name
        binding.txtProductCode.text = result.matchedVariant?.sku ?: result.product.code

        if (result.matchedVariant != null) {
            binding.txtVariant.visibility = View.VISIBLE
            binding.txtVariant.text = "Escaneado: ${result.matchedVariant.name}"
        } else if (result.allVariants.isNotEmpty()) {
            binding.txtVariant.visibility = View.VISIBLE
            binding.txtVariant.text = "${result.allVariants.size} variantes"
        } else {
            binding.txtVariant.visibility = View.GONE
        }

        val cats = listOfNotNull(result.product.categoryName, result.product.brandName)
        if (cats.isNotEmpty()) {
            binding.txtCategoryBrand.visibility = View.VISIBLE
            binding.txtCategoryBrand.text = cats.joinToString(" | ")
        } else {
            binding.txtCategoryBrand.visibility = View.GONE
        }

        // Stock section
        binding.layoutStock.removeAllViews()
        val currentWarehouseId = ApiClient.getWarehouseId(this)
        val hasVariants = result.allVariants.isNotEmpty()

        if (hasVariants) {
            // Show stock per VARIANT per WAREHOUSE
            binding.txtStockHeader.text = "STOCK POR VARIANTE / ALMACEN"
            showVariantStock(result, currentWarehouseId)
        } else {
            // Simple product without variants - show stock per warehouse
            binding.txtStockHeader.text = "STOCK POR ALMACEN"
            showSimpleStock(result.stock, currentWarehouseId)
        }

        // Locations
        showLocations()
    }

    private fun showVariantStock(result: LookupResult, currentWarehouseId: Int) {
        // Group stock by variant
        val variantMap = result.allVariants.associateBy { it.id }
        val stockByVariant = result.stock.groupBy { it.variantId }

        for (variant in result.allVariants) {
            val isScanned = variant.id == result.matchedVariant?.id
            val variantStocks = stockByVariant[variant.id] ?: emptyList()
            val totalQty = variantStocks.sumOf { it.available }

            // Variant header
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(8), 0, dpToPx(2))
            }
            header.addView(TextView(this).apply {
                text = variant.name
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isScanned) Color.parseColor("#BF360C") else Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            header.addView(TextView(this).apply {
                text = "Total: ${totalQty.toInt()}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.END
                setTextColor(if (totalQty > 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
            })
            binding.layoutStock.addView(header)

            // Stock per warehouse for this variant
            if (variantStocks.isNotEmpty()) {
                val byWarehouse = variantStocks.groupBy { it.warehouseId }
                for ((warehouseId, stocks) in byWarehouse) {
                    val warehouseName = warehouseCache[warehouseId] ?: "Almacen #$warehouseId"
                    val avail = stocks.sumOf { it.available }
                    val isCurrent = warehouseId == currentWarehouseId

                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(dpToPx(16), dpToPx(1), 0, dpToPx(1))
                        if (isCurrent) setBackgroundColor(Color.parseColor("#E8F5E9"))
                    }
                    row.addView(TextView(this).apply {
                        text = if (isCurrent) "$warehouseName *" else warehouseName
                        textSize = 12f
                        setTextColor(Color.parseColor("#666666"))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    row.addView(TextView(this).apply {
                        text = "${avail.toInt()}"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.END
                        setTextColor(if (avail > 0) Color.parseColor("#2E7D32") else Color.parseColor("#999999"))
                    })
                    binding.layoutStock.addView(row)
                }
            } else {
                binding.layoutStock.addView(TextView(this).apply {
                    text = "  Sin stock"
                    textSize = 12f
                    setTextColor(Color.parseColor("#999999"))
                    setPadding(dpToPx(16), 0, 0, 0)
                })
            }
        }

        // Separator
        binding.layoutStock.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(4)
            }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        })

        // Total row
        val totalAll = result.stock.sumOf { it.available }
        val totalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        totalRow.addView(TextView(this).apply {
            text = "TOTAL"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        totalRow.addView(TextView(this).apply {
            text = "${totalAll.toInt()}"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(if (totalAll > 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
        })
        binding.layoutStock.addView(totalRow)
    }

    private fun showSimpleStock(stock: List<com.eckscanner.data.local.StockEntity>, currentWarehouseId: Int) {
        if (stock.isNotEmpty()) {
            val grouped = stock.groupBy { it.warehouseId }
            for ((warehouseId, stocks) in grouped) {
                val warehouseName = warehouseCache[warehouseId] ?: "Almacen #$warehouseId"
                val totalAvail = stocks.sumOf { it.available }
                val isCurrent = warehouseId == currentWarehouseId

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val pad = dpToPx(if (isCurrent) 6 else 0)
                    setPadding(pad, dpToPx(3), pad, dpToPx(3))
                    if (isCurrent) setBackgroundColor(Color.parseColor("#E8F5E9"))
                }
                row.addView(TextView(this).apply {
                    text = if (isCurrent) "$warehouseName *" else warehouseName
                    textSize = 14f
                    if (isCurrent) setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(TextView(this).apply {
                    text = "${totalAvail.toInt()}"
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.END
                    setTextColor(if (totalAvail > 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
                })
                binding.layoutStock.addView(row)
            }
        } else {
            binding.layoutStock.addView(TextView(this).apply {
                text = getString(R.string.no_stock)
                textSize = 14f
                setTextColor(Color.parseColor("#C62828"))
            })
        }
    }

    private fun showLocations() {
        binding.layoutLocations.removeAllViews()
        binding.txtLocationHeader.visibility = View.GONE

        val locations = lastBarcodeResponse?.locations ?: return
        if (locations.isEmpty()) return

        binding.txtLocationHeader.visibility = View.VISIBLE
        for (loc in locations) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(2), 0, dpToPx(2))
            }
            row.addView(TextView(this).apply {
                text = loc.shelfName ?: "-"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#00695C"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = loc.warehouseName ?: ""
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.END
            })
            binding.layoutLocations.addView(row)
        }
    }

    private fun showNotFound() {
        binding.txtScanPrompt.visibility = View.GONE
        binding.scrollResult.visibility = View.GONE
        binding.txtNotFound.text = getString(R.string.product_not_found)
        binding.txtNotFound.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        binding.txtScanPrompt.visibility = View.GONE
        binding.scrollResult.visibility = View.GONE
        binding.txtNotFound.text = message
        binding.txtNotFound.visibility = View.VISIBLE
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
