package com.eckscanner.ui.count

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.eckscanner.R
import com.eckscanner.data.remote.AdjustmentItemRequest
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.StockAdjustmentRequest
import com.eckscanner.data.repository.ProductRepository
import com.eckscanner.databinding.ActivityCountBinding
import com.eckscanner.scanner.DataWedgeReceiver
import kotlinx.coroutines.launch

class CountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCountBinding
    private lateinit var repository: ProductRepository
    private lateinit var scanReceiver: DataWedgeReceiver
    private lateinit var adapter: CountAdapter

    private val countItems = mutableListOf<CountItem>()
    private var warehouseId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProductRepository(this)
        warehouseId = ApiClient.getWarehouseId(this)

        binding.toolbar.setNavigationOnClickListener {
            if (countItems.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setMessage("Tienes ${countItems.size} items escaneados. Salir sin enviar?")
                    .setPositiveButton("Salir") { _, _ -> finish() }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            } else finish()
        }

        adapter = CountAdapter(countItems)
        binding.recyclerItems.layoutManager = LinearLayoutManager(this)
        binding.recyclerItems.adapter = adapter

        binding.btnClear.setOnClickListener {
            if (countItems.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setMessage("Limpiar todo el conteo?")
                .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                    countItems.clear()
                    adapter.notifyDataSetChanged()
                    updateStatus()
                    binding.cardLastScan.visibility = View.GONE
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        binding.btnSendAdjustment.setOnClickListener { sendAdjustment() }

        scanReceiver = DataWedgeReceiver { code ->
            runOnUiThread { onScan(code) }
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanReceiver, DataWedgeReceiver.getIntentFilter())
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(scanReceiver)
    }

    private fun onScan(code: String) {
        lifecycleScope.launch {
            val result = repository.findByCode(code)
            if (result == null) {
                Toast.makeText(this@CountActivity, "No encontrado: $code", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val productId = result.product.id
            val variantId = result.matchedVariant?.id ?: 0

            // Find existing item in list
            val existing = countItems.find { it.productId == productId && it.variantId == variantId }
            if (existing != null) {
                existing.scannedQty += 1.0
                val index = countItems.indexOf(existing)
                adapter.notifyItemChanged(index)
            } else {
                // Get system stock for this warehouse
                val stock = repository.getStockInWarehouse(productId, variantId, warehouseId)
                val systemQty = stock?.quantity ?: 0.0

                val item = CountItem(
                    productId = productId,
                    variantId = if (variantId == 0) null else variantId,
                    productName = result.product.name,
                    variantName = result.matchedVariant?.name,
                    code = result.matchedVariant?.sku ?: result.product.code,
                    systemQty = systemQty,
                    scannedQty = 1.0
                )
                countItems.add(0, item)
                adapter.notifyItemInserted(0)
                binding.recyclerItems.scrollToPosition(0)
            }

            // Show last scanned
            binding.cardLastScan.visibility = View.VISIBLE
            binding.txtLastScanName.text = result.product.name
            val variantText = result.matchedVariant?.name?.let { " ($it)" } ?: ""
            binding.txtLastScanDetail.text = "${result.matchedVariant?.sku ?: result.product.code}$variantText"

            updateStatus()
        }
    }

    private fun updateStatus() {
        val total = countItems.size
        val scannedTotal = countItems.sumOf { it.scannedQty }.toInt()
        binding.txtCountStatus.text = "$total productos | $scannedTotal unidades escaneadas"
        binding.txtTotalItems.text = "Items: $total"
    }

    private fun sendAdjustment() {
        if (countItems.isEmpty()) {
            Toast.makeText(this, "No hay items para enviar", Toast.LENGTH_SHORT).show()
            return
        }

        val diffs = countItems.filter { it.difference != 0.0 }
        if (diffs.isEmpty()) {
            Toast.makeText(this, "Todo coincide, no hay diferencias", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Enviar ajuste de inventario")
            .setMessage("${diffs.size} productos con diferencia.\nSe ajustara el stock en el sistema.")
            .setPositiveButton(getString(R.string.confirm)) { _, _ -> doSendAdjustment(diffs) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun doSendAdjustment(diffs: List<CountItem>) {
        binding.btnSendAdjustment.isEnabled = false

        lifecycleScope.launch {
            try {
                val items = diffs.map { item ->
                    val diff = item.difference
                    AdjustmentItemRequest(
                        productId = item.productId,
                        variantId = item.variantId,
                        type = if (diff > 0) "entrada" else "salida",
                        quantity = kotlin.math.abs(diff)
                    )
                }

                val request = StockAdjustmentRequest(
                    warehouseId = warehouseId,
                    reason = "inventario",
                    notes = "Conteo fisico via ECK Scanner",
                    items = items
                )

                val response = ApiClient.getService().createStockAdjustment(request)
                if (response.isSuccessful) {
                    Toast.makeText(this@CountActivity, getString(R.string.adjustment_sent), Toast.LENGTH_LONG).show()
                    countItems.clear()
                    adapter.notifyDataSetChanged()
                    updateStatus()
                    binding.cardLastScan.visibility = View.GONE
                } else {
                    Toast.makeText(this@CountActivity, "Error ${response.code()}: ${response.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CountActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSendAdjustment.isEnabled = true
            }
        }
    }
}

data class CountItem(
    val productId: Int,
    val variantId: Int?,
    val productName: String,
    val variantName: String?,
    val code: String,
    val systemQty: Double,
    var scannedQty: Double
) {
    val difference: Double get() = scannedQty - systemQty
}
