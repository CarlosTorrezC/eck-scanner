package com.eckscanner.ui.pricelookup

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.eckscanner.R
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.repository.LookupResult
import com.eckscanner.data.repository.ProductRepository
import com.eckscanner.databinding.ActivityPriceLookupBinding
import com.eckscanner.scanner.DataWedgeReceiver
import kotlinx.coroutines.launch

class PriceLookupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriceLookupBinding
    private lateinit var repository: ProductRepository
    private lateinit var scanReceiver: DataWedgeReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPriceLookupBinding.inflate(layoutInflater)
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
        lifecycleScope.launch {
            var result = repository.findByCode(code)
            if (result == null) {
                result = repository.lookupOnline(code)
            }

            if (result != null) {
                showPrice(result)
            } else {
                showNotFound()
            }
        }
    }

    private fun showPrice(result: LookupResult) {
        binding.layoutPrompt.visibility = View.GONE
        binding.layoutResult.visibility = View.VISIBLE
        binding.txtNotFound.visibility = View.GONE
        binding.txtProductName.visibility = View.VISIBLE
        binding.txtPrice.visibility = View.VISIBLE

        binding.txtProductName.text = result.product.name
        binding.txtCode.text = result.matchedVariant?.sku ?: result.product.code

        if (result.matchedVariant != null) {
            binding.txtVariant.visibility = View.VISIBLE
            binding.txtVariant.text = result.matchedVariant.name
        } else {
            binding.txtVariant.visibility = View.GONE
        }

        val price = result.matchedVariant?.price ?: result.product.salePrice
        binding.txtPrice.text = String.format("%.2f", price)

        // Stock in current warehouse
        val warehouseId = ApiClient.getWarehouseId(this)
        if (warehouseId != -1) {
            val stockInWarehouse = result.stock.filter { it.warehouseId == warehouseId }
            val available = stockInWarehouse.sumOf { it.available }
            binding.txtStock.visibility = View.VISIBLE
            binding.txtStock.text = if (available > 0) {
                "Disponible: ${available.toInt()} unidades"
            } else {
                "Sin stock en este almacen"
            }
            binding.txtStock.setTextColor(
                if (available > 0) android.graphics.Color.parseColor("#2E7D32")
                else android.graphics.Color.parseColor("#C62828")
            )
        } else {
            binding.txtStock.visibility = View.GONE
        }
    }

    private fun showNotFound() {
        binding.layoutPrompt.visibility = View.GONE
        binding.layoutResult.visibility = View.VISIBLE
        binding.txtProductName.visibility = View.GONE
        binding.txtVariant.visibility = View.GONE
        binding.txtCode.text = ""
        binding.txtPrice.visibility = View.GONE
        binding.txtStock.visibility = View.GONE
        binding.txtNotFound.visibility = View.VISIBLE
    }
}
