package com.eckscanner.ui.lookup

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.eckscanner.R
import com.eckscanner.data.local.AppDatabase
import com.eckscanner.data.local.StockEntity
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.BarcodeLocationDto
import com.eckscanner.data.repository.LookupResult
import com.eckscanner.data.repository.ProductRepository
import com.eckscanner.databinding.ActivityLookupBinding
import com.eckscanner.scanner.DataWedgeReceiver
import kotlinx.coroutines.launch

class LookupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLookupBinding
    private lateinit var repository: ProductRepository
    private lateinit var scanReceiver: DataWedgeReceiver

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
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanReceiver, DataWedgeReceiver.getIntentFilter())
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(scanReceiver)
    }

    private fun lookup(code: String) {
        lifecycleScope.launch {
            // Try local first (instant)
            var result = repository.findByCode(code)
            if (result == null) {
                result = repository.lookupOnline(code)
            }

            if (result != null) {
                showResult(result, code)
            } else {
                showNotFound()
            }
        }
    }

    private fun showResult(result: LookupResult, scannedCode: String) {
        binding.txtScanPrompt.visibility = View.GONE
        binding.txtNotFound.visibility = View.GONE
        binding.scrollResult.visibility = View.VISIBLE

        // Price - big
        val price = result.matchedVariant?.price ?: result.product.salePrice
        binding.txtPrice.text = String.format("%.2f", price)

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

        // Stock by warehouse
        binding.layoutStock.removeAllViews()
        val db = AppDatabase.getInstance(this)
        val currentWarehouseId = ApiClient.getWarehouseId(this)

        lifecycleScope.launch {
            if (result.stock.isNotEmpty()) {
                val grouped = result.stock.groupBy { it.warehouseId }
                for ((warehouseId, stocks) in grouped) {
                    val warehouse = db.warehouseDao().getById(warehouseId)
                    val warehouseName = warehouse?.name ?: "Almacen #$warehouseId"
                    val totalAvail = stocks.sumOf { it.available }
                    val isCurrent = warehouseId == currentWarehouseId

                    val row = LinearLayout(this@LookupActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dpToPx(3), 0, dpToPx(3))
                        if (isCurrent) {
                            setBackgroundColor(Color.parseColor("#E8F5E9"))
                            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
                        }
                    }

                    val nameView = TextView(this@LookupActivity).apply {
                        text = if (isCurrent) "$warehouseName *" else warehouseName
                        textSize = 14f
                        if (isCurrent) setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val qtyView = TextView(this@LookupActivity).apply {
                        text = "${totalAvail.toInt()}"
                        textSize = 17f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.END
                        setTextColor(
                            if (totalAvail > 0) Color.parseColor("#2E7D32")
                            else Color.parseColor("#C62828")
                        )
                    }

                    row.addView(nameView)
                    row.addView(qtyView)
                    binding.layoutStock.addView(row)
                }
            } else {
                val noStock = TextView(this@LookupActivity).apply {
                    text = getString(R.string.no_stock)
                    textSize = 14f
                    setTextColor(Color.parseColor("#C62828"))
                }
                binding.layoutStock.addView(noStock)
            }

            // Locations - fetch online
            fetchLocations(result)
        }
    }

    private fun fetchLocations(result: LookupResult) {
        binding.layoutLocations.removeAllViews()

        lifecycleScope.launch {
            try {
                val code = result.matchedVariant?.sku ?: result.product.code
                val response = ApiClient.getService().lookupBarcode(code)
                if (!response.isSuccessful) return@launch
                val locations = response.body()?.locations ?: return@launch

                if (locations.isNotEmpty()) {
                    binding.txtLocationHeader.visibility = View.VISIBLE
                    for (loc in locations) {
                        val row = LinearLayout(this@LookupActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(0, dpToPx(2), 0, dpToPx(2))
                        }

                        val shelfView = TextView(this@LookupActivity).apply {
                            text = loc.shelfName ?: "Sin nombre"
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#00695C"))
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val warehouseView = TextView(this@LookupActivity).apply {
                            text = loc.warehouseName ?: ""
                            textSize = 12f
                            setTextColor(Color.parseColor("#888888"))
                            gravity = Gravity.END
                        }

                        row.addView(shelfView)
                        row.addView(warehouseView)
                        binding.layoutLocations.addView(row)
                    }
                } else {
                    binding.txtLocationHeader.visibility = View.GONE
                }
            } catch (_: Exception) {
                // Silently fail - locations are secondary info
                binding.txtLocationHeader.visibility = View.GONE
            }
        }
    }

    private fun showNotFound() {
        binding.txtScanPrompt.visibility = View.GONE
        binding.scrollResult.visibility = View.GONE
        binding.txtNotFound.visibility = View.VISIBLE
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
