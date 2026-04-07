package com.eckscanner.ui.priceedit

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.eckscanner.data.local.AppDatabase
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.PriceUpdateRequest
import com.eckscanner.data.repository.LookupResult
import com.eckscanner.data.repository.ProductRepository
import com.eckscanner.databinding.ActivityPriceEditBinding
import com.eckscanner.scanner.DataWedgeReceiver
import com.eckscanner.scanner.ScanFeedback
import coil.load
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.launch

class PriceEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriceEditBinding
    private lateinit var repository: ProductRepository
    private lateinit var scanReceiver: DataWedgeReceiver

    private var currentProductId: Int = -1
    private var currentVariantId: Int? = null
    private var currentPrice: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPriceEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProductRepository(this)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSave.setOnClickListener { savePrice() }

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
        try { unregisterReceiver(scanReceiver) } catch (_: Exception) {}
    }

    private fun lookup(code: String) {
        lifecycleScope.launch {
            val result = repository.findByCode(code)
            if (result != null) {
                ScanFeedback.success(this@PriceEditActivity)
                showProduct(result)
            } else {
                when (val online = repository.lookupOnline(code)) {
                    is ProductRepository.OnlineResult.Found -> {
                        ScanFeedback.success(this@PriceEditActivity)
                        showProduct(online.result)
                    }
                    is ProductRepository.OnlineResult.NotFound -> {
                        ScanFeedback.error(this@PriceEditActivity)
                        Toast.makeText(this@PriceEditActivity, "No encontrado: $code", Toast.LENGTH_SHORT).show()
                    }
                    is ProductRepository.OnlineResult.NetworkError -> {
                        ScanFeedback.error(this@PriceEditActivity)
                        Toast.makeText(this@PriceEditActivity, online.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showProduct(result: LookupResult) {
        binding.txtScanPrompt.visibility = View.GONE
        binding.scrollContent.visibility = View.VISIBLE
        binding.txtSuccess.visibility = View.GONE

        currentProductId = result.product.id
        currentVariantId = result.matchedVariant?.id
        currentPrice = result.matchedVariant?.price ?: result.product.salePrice

        binding.txtProductName.text = result.product.name
        binding.txtCode.text = result.matchedVariant?.sku ?: result.product.code

        // Product image
        val image = result.product.image
        if (!image.isNullOrEmpty()) {
            val config = ApiClient.getConfig(this)
            val baseUrl = config?.first?.trimEnd('/') ?: ""
            binding.imgProduct.visibility = View.VISIBLE
            binding.imgProduct.load("$baseUrl/storage/$image") {
                crossfade(false)
                transformations(RoundedCornersTransformation(8f))
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
            }
        } else {
            binding.imgProduct.visibility = View.GONE
        }

        if (result.matchedVariant != null) {
            binding.txtVariant.visibility = View.VISIBLE
            binding.txtVariant.text = result.matchedVariant.name
        } else {
            binding.txtVariant.visibility = View.GONE
        }

        binding.txtCurrentPrice.text = "$ ${String.format("%.2f", currentPrice)}"

        // Set current price in edit field and select all for quick replace
        binding.editNewPrice.setText(String.format("%.2f", currentPrice))
        binding.editNewPrice.requestFocus()
        binding.editNewPrice.selectAll()

        // Show numeric keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.editNewPrice, InputMethodManager.SHOW_IMPLICIT)

        binding.btnSave.isEnabled = true
    }

    private fun savePrice() {
        val newPriceStr = binding.editNewPrice.text.toString().trim()
        if (newPriceStr.isEmpty()) {
            Toast.makeText(this, "Ingresa un precio", Toast.LENGTH_SHORT).show()
            return
        }

        val newPrice = newPriceStr.toDoubleOrNull()
        if (newPrice == null || newPrice < 0) {
            Toast.makeText(this, "Precio invalido", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPrice == currentPrice) {
            Toast.makeText(this, "El precio no cambio", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = PriceUpdateRequest(
                    productId = currentProductId,
                    variantId = currentVariantId,
                    salePrice = newPrice
                )

                val response = ApiClient.getService().updatePrice(request)
                if (response.isSuccessful) {
                    ScanFeedback.success(this@PriceEditActivity)
                    val body = response.body()

                    // Update local DB immediately
                    val db = AppDatabase.getInstance(this@PriceEditActivity)
                    if (currentVariantId != null) {
                        val variant = db.variantDao().findBySku(binding.txtCode.text.toString())
                        if (variant != null) {
                            db.variantDao().upsertAll(listOf(variant.copy(price = newPrice)))
                        }
                    } else {
                        val product = db.productDao().getById(currentProductId)
                        if (product != null) {
                            db.productDao().upsertAll(listOf(product.product.copy(salePrice = newPrice)))
                        }
                    }

                    binding.txtCurrentPrice.text = "$ ${String.format("%.2f", newPrice)}"
                    currentPrice = newPrice

                    binding.txtSuccess.text = "Precio actualizado: $ ${String.format("%.2f", body?.oldPrice ?: 0.0)} → $ ${String.format("%.2f", newPrice)}"
                    binding.txtSuccess.visibility = View.VISIBLE

                    // Hide keyboard
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(binding.editNewPrice.windowToken, 0)
                } else {
                    ScanFeedback.error(this@PriceEditActivity)
                    Toast.makeText(this@PriceEditActivity, "Error ${response.code()}: ${response.errorBody()?.string()?.take(100)}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                ScanFeedback.error(this@PriceEditActivity)
                Toast.makeText(this@PriceEditActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }
}
