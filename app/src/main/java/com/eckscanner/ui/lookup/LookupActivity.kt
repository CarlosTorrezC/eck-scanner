package com.eckscanner.ui.lookup

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
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

        binding.editSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString().trim()
                if (query.isNotEmpty()) lookup(query)
                true
            } else false
        }

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

    private fun lookup(code: String) {
        lifecycleScope.launch {
            val localResult = repository.findByCode(code)
            if (localResult != null) {
                ScanFeedback.success(this@LookupActivity)
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

    private fun showResult(result: LookupResult) {
        binding.txtScanPrompt.visibility = View.GONE
        binding.txtNotFound.visibility = View.GONE
        binding.scrollResult.visibility = View.VISIBLE

        // Price (safe for null variant prices)
        val price = result.matchedVariant?.price ?: result.product.salePrice
        binding.txtPrice.text = String.format("%.2f", price ?: 0.0)

        // Product info
        binding.txtProductName.text = result.product.name
        binding.txtProductCode.text = result.matchedVariant?.sku ?: result.product.code

        if (result.matchedVariant != null) {
            binding.txtVariant.visibility = View.VISIBLE
            binding.txtVariant.text = result.matchedVariant.name
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

        // Stock by warehouse - using cached warehouse names (no N+1)
        binding.layoutStock.removeAllViews()
        val currentWarehouseId = ApiClient.getWarehouseId(this)

        if (result.stock.isNotEmpty()) {
            val grouped = result.stock.groupBy { it.warehouseId }
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

        // Locations - fetch online (non-blocking, secondary info)
        fetchLocations(result)
    }

    private fun fetchLocations(result: LookupResult) {
        binding.layoutLocations.removeAllViews()
        binding.txtLocationHeader.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val code = result.matchedVariant?.sku ?: result.product.code
                val response = ApiClient.getService().lookupBarcode(code)
                if (!response.isSuccessful) return@launch
                val locations = response.body()?.locations ?: return@launch
                if (locations.isEmpty()) return@launch

                binding.txtLocationHeader.visibility = View.VISIBLE
                for (loc in locations) {
                    val row = LinearLayout(this@LookupActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dpToPx(2), 0, dpToPx(2))
                    }
                    row.addView(TextView(this@LookupActivity).apply {
                        text = loc.shelfName ?: "-"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#00695C"))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    row.addView(TextView(this@LookupActivity).apply {
                        text = loc.warehouseName ?: ""
                        textSize = 12f
                        setTextColor(Color.parseColor("#888888"))
                        gravity = Gravity.END
                    })
                    binding.layoutLocations.addView(row)
                }
            } catch (_: Exception) { }
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
