package com.eckscanner.ui.shelf

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.eckscanner.R
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.ShelfScanItemRequest
import com.eckscanner.data.remote.ShelfScanRequest
import com.eckscanner.data.repository.ProductRepository
import com.eckscanner.databinding.ActivityShelfScanBinding
import com.eckscanner.scanner.DataWedgeReceiver
import com.eckscanner.scanner.ScanFeedback
import kotlinx.coroutines.launch

class ShelfScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShelfScanBinding
    private lateinit var repository: ProductRepository
    private lateinit var scanReceiver: DataWedgeReceiver
    private lateinit var adapter: ShelfProductAdapter

    private val items = mutableListOf<ShelfProductItem>()
    private var shelfId: Int = -1
    private var shelfName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShelfScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProductRepository(this)
        shelfId = intent.getIntExtra("shelf_id", -1)
        shelfName = intent.getStringExtra("shelf_name") ?: ""

        binding.toolbar.title = shelfName
        binding.toolbar.setNavigationOnClickListener {
            if (items.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setMessage("Tienes cambios sin guardar. Salir?")
                    .setPositiveButton("Salir") { _, _ -> finish() }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            } else finish()
        }

        adapter = ShelfProductAdapter(items) { index ->
            items.removeAt(index)
            adapter.notifyItemRemoved(index)
            updateCount()
        }
        binding.recyclerItems.layoutManager = LinearLayoutManager(this)
        binding.recyclerItems.adapter = adapter

        binding.btnSave.setOnClickListener { saveShelf() }

        scanReceiver = DataWedgeReceiver { code ->
            runOnUiThread { onScan(code) }
        }

        // Load existing items from API
        loadShelf()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanReceiver, DataWedgeReceiver.getIntentFilter())
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(scanReceiver) } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (items.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("Tienes cambios sin guardar. Salir?")
                .setPositiveButton("Salir") { _, _ -> super.onBackPressed() }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        } else super.onBackPressed()
    }

    private fun loadShelf() {
        binding.progressLoad.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getShelf(shelfId)
                if (response.isSuccessful) {
                    val shelf = response.body()?.data ?: return@launch

                    binding.txtShelfInfo.text = "${shelf.warehouse.name} | ${shelf.name}"

                    items.clear()
                    shelf.items.forEach { item ->
                        items.add(
                            ShelfProductItem(
                                productId = item.productId,
                                variantId = item.variantId,
                                productName = item.productName,
                                productCode = item.productCode,
                                variantName = item.variantName,
                                variantSku = item.variantSku,
                                warehouseStock = item.warehouseStock,
                                otherShelves = item.otherShelves ?: emptyList()
                            )
                        )
                    }
                    adapter.notifyDataSetChanged()
                    updateCount()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ShelfScanActivity, "Error cargando: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressLoad.visibility = View.GONE
            }
        }
    }

    private fun onScan(code: String) {
        lifecycleScope.launch {
            val result = repository.findByCode(code)
            if (result == null) {
                // Fallback online
                when (val online = repository.lookupOnline(code)) {
                    is ProductRepository.OnlineResult.Found -> {
                        val r = online.result
                        addProduct(
                            productId = r.product.id,
                            variantId = r.matchedVariant?.id,
                            productName = r.product.name,
                            productCode = r.product.code,
                            variantName = r.matchedVariant?.name,
                            variantSku = r.matchedVariant?.sku,
                            stock = r.stock.sumOf { it.quantity }
                        )
                    }
                    is ProductRepository.OnlineResult.NetworkError -> {
                        ScanFeedback.error(this@ShelfScanActivity)
                        Toast.makeText(this@ShelfScanActivity, online.message, Toast.LENGTH_SHORT).show()
                    }
                    is ProductRepository.OnlineResult.NotFound -> {
                        ScanFeedback.error(this@ShelfScanActivity)
                        Toast.makeText(this@ShelfScanActivity, "No encontrado: $code", Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            val variantId = result.matchedVariant?.id
            val warehouseId = ApiClient.getWarehouseId(this@ShelfScanActivity)
            val stock = if (warehouseId != -1) {
                repository.getStockInWarehouse(result.product.id, variantId ?: 0, warehouseId)?.quantity ?: 0.0
            } else {
                result.stock.sumOf { it.quantity }
            }

            addProduct(
                productId = result.product.id,
                variantId = variantId,
                productName = result.product.name,
                productCode = result.product.code,
                variantName = result.matchedVariant?.name,
                variantSku = result.matchedVariant?.sku,
                stock = stock
            )
        }
    }

    private fun addProduct(
        productId: Int,
        variantId: Int?,
        productName: String,
        productCode: String,
        variantName: String?,
        variantSku: String?,
        stock: Double
    ) {
        // Check duplicate
        val exists = items.any { it.productId == productId && it.variantId == variantId }
        if (exists) {
            ScanFeedback.duplicate(this)
            Toast.makeText(this, "Ya esta en la lista", Toast.LENGTH_SHORT).show()
            return
        }
        ScanFeedback.success(this)

        val item = ShelfProductItem(
            productId = productId,
            variantId = variantId,
            productName = productName,
            productCode = productCode,
            variantName = variantName,
            variantSku = variantSku,
            warehouseStock = stock,
            otherShelves = emptyList()
        )

        items.add(0, item)
        adapter.notifyItemInserted(0)
        binding.recyclerItems.scrollToPosition(0)

        // Show feedback
        binding.cardLastScan.visibility = View.VISIBLE
        binding.txtLastScanName.text = productName
        binding.txtLastScanDetail.text = buildString {
            append(variantSku ?: productCode)
            variantName?.let { append(" | $it") }
        }

        updateCount()
    }

    private fun updateCount() {
        binding.txtItemCount.text = "${items.size} productos"
    }

    private fun saveShelf() {
        if (items.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("La lista esta vacia. Esto eliminara todos los productos del estante. Continuar?")
                .setPositiveButton(getString(R.string.confirm)) { _, _ -> doSave() }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
            return
        }

        doSave()
    }

    private fun doSave() {
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = ShelfScanRequest(
                    items = items.map { item ->
                        ShelfScanItemRequest(
                            productId = item.productId,
                            variantId = item.variantId
                        )
                    }
                )

                val response = ApiClient.getService().scanShelf(shelfId, request)
                if (response.isSuccessful) {
                    val assigned = response.body()?.assigned ?: items.size
                    Toast.makeText(
                        this@ShelfScanActivity,
                        getString(R.string.shelf_saved, assigned),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    Toast.makeText(this@ShelfScanActivity, "Error ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ShelfScanActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }
}

data class ShelfProductItem(
    val productId: Int,
    val variantId: Int?,
    val productName: String,
    val productCode: String,
    val variantName: String?,
    val variantSku: String?,
    val warehouseStock: Double,
    val otherShelves: List<String>
)
