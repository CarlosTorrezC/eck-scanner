package com.eckscanner.ui.lookup

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
import com.eckscanner.data.local.VariantEntity
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

            // Fallback to online
            if (result == null) {
                result = repository.lookupOnline(code)
            }

            if (result != null) {
                showResult(result)
            } else {
                showNotFound()
            }
        }
    }

    private suspend fun showResult(result: LookupResult) {
        binding.txtScanPrompt.visibility = View.GONE
        binding.txtNotFound.visibility = View.GONE
        binding.scrollResult.visibility = View.VISIBLE

        binding.txtProductName.text = result.product.name
        binding.txtProductCode.text = "Cod: ${result.product.code}" +
                (result.product.barcode?.let { " | BC: $it" } ?: "")

        if (result.matchedVariant != null) {
            binding.txtVariant.visibility = View.VISIBLE
            binding.txtVariant.text = getString(R.string.variant, result.matchedVariant.name)
        } else if (result.allVariants.isNotEmpty()) {
            binding.txtVariant.visibility = View.VISIBLE
            binding.txtVariant.text = "${result.allVariants.size} variantes"
        } else {
            binding.txtVariant.visibility = View.GONE
        }

        val cats = listOfNotNull(result.product.categoryName, result.product.brandName)
        binding.txtCategoryBrand.text = cats.joinToString(" | ")
        binding.txtCategoryBrand.visibility = if (cats.isEmpty()) View.GONE else View.VISIBLE

        val price = result.matchedVariant?.price ?: result.product.salePrice
        binding.txtPrice.text = getString(R.string.price, String.format("%.2f", price))

        // Stock by warehouse
        binding.layoutStock.removeAllViews()
        val db = AppDatabase.getInstance(this)

        if (result.stock.isNotEmpty()) {
            val grouped = result.stock.groupBy { it.warehouseId }
            for ((warehouseId, stocks) in grouped) {
                val warehouse = db.warehouseDao().getById(warehouseId)
                val warehouseName = warehouse?.name ?: "Almacen #$warehouseId"
                val totalQty = stocks.sumOf { it.quantity }
                val totalAvail = stocks.sumOf { it.available }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 4, 0, 4)
                }

                val nameView = TextView(this).apply {
                    text = warehouseName
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val qtyView = TextView(this).apply {
                    text = "${totalAvail.toInt()}"
                    textSize = 18f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.END
                    setTextColor(
                        if (totalAvail > 0) android.graphics.Color.parseColor("#2E7D32")
                        else android.graphics.Color.parseColor("#C62828")
                    )
                }

                row.addView(nameView)
                row.addView(qtyView)
                binding.layoutStock.addView(row)
            }
        } else {
            val noStock = TextView(this).apply {
                text = getString(R.string.no_stock)
                textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#C62828"))
            }
            binding.layoutStock.addView(noStock)
        }
    }

    private fun showNotFound() {
        binding.txtScanPrompt.visibility = View.GONE
        binding.scrollResult.visibility = View.GONE
        binding.txtNotFound.visibility = View.VISIBLE
    }
}
