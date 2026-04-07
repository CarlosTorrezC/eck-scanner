package com.eckscanner.ui.location

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.eckscanner.R
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.BarcodeLocationDto
import com.eckscanner.data.repository.ProductRepository
import com.eckscanner.databinding.ActivityLocationFinderBinding
import com.eckscanner.scanner.DataWedgeReceiver
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class LocationFinderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationFinderBinding
    private lateinit var repository: ProductRepository
    private lateinit var scanReceiver: DataWedgeReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationFinderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProductRepository(this)
        binding.toolbar.setNavigationOnClickListener { finish() }

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
        binding.layoutPrompt.visibility = View.GONE
        binding.scrollResult.visibility = View.VISIBLE
        binding.txtNotFound.visibility = View.GONE
        binding.cardNoLocation.visibility = View.GONE
        binding.layoutShelves.removeAllViews()
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Use online lookup to get locations (they're not cached locally)
                val response = ApiClient.getService().lookupBarcode(code)
                if (!response.isSuccessful || response.body() == null) {
                    showNotFound()
                    return@launch
                }

                val body = response.body()!!
                binding.progressBar.visibility = View.GONE

                binding.txtProductName.text = body.product.name
                binding.txtCode.text = body.variant?.sku ?: body.product.code

                if (body.variant != null) {
                    binding.txtVariant.visibility = View.VISIBLE
                    binding.txtVariant.text = body.variant.name
                } else {
                    binding.txtVariant.visibility = View.GONE
                }

                val locations = body.locations ?: emptyList()
                if (locations.isEmpty()) {
                    binding.txtLocationHeader.text = ""
                    binding.cardNoLocation.visibility = View.VISIBLE
                } else {
                    binding.txtLocationHeader.text = "Ubicaciones (${locations.size})"
                    showLocations(locations)
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@LocationFinderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLocations(locations: List<BarcodeLocationDto>) {
        // Group by warehouse
        val grouped = locations.groupBy { it.warehouseName ?: "Sin almacen" }

        for ((warehouseName, locs) in grouped) {
            // Warehouse header
            val warehouseHeader = TextView(this).apply {
                text = warehouseName
                textSize = 13f
                setTextColor(Color.parseColor("#888888"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dpToPx(12), 0, dpToPx(4))
            }
            binding.layoutShelves.addView(warehouseHeader)

            for (loc in locs) {
                val card = MaterialCardView(this).apply {
                    radius = dpToPx(10).toFloat()
                    cardElevation = dpToPx(2).toFloat()
                    setCardBackgroundColor(Color.parseColor("#E8F5E9"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dpToPx(8) }
                }

                val inner = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                }

                val shelfName = TextView(this).apply {
                    text = loc.shelfName ?: "Sin nombre"
                    textSize = 20f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#2E7D32"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val shelfCode = TextView(this).apply {
                    text = loc.shelfCode ?: ""
                    textSize = 14f
                    setTextColor(Color.parseColor("#666666"))
                }

                inner.addView(shelfName)
                if (!loc.shelfCode.isNullOrEmpty()) {
                    inner.addView(shelfCode)
                }
                card.addView(inner)
                binding.layoutShelves.addView(card)
            }
        }
    }

    private fun showNotFound() {
        binding.progressBar.visibility = View.GONE
        binding.txtProductName.text = ""
        binding.txtVariant.visibility = View.GONE
        binding.txtCode.text = ""
        binding.txtLocationHeader.text = ""
        binding.txtNotFound.visibility = View.VISIBLE
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
