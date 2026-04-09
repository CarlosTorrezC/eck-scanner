package com.eckscanner.ui.transfer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.eckscanner.R
import com.eckscanner.data.local.AppDatabase
import com.eckscanner.data.local.WarehouseEntity
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.CreateTransferRequest
import com.eckscanner.data.remote.TransferItemRequest
import com.eckscanner.data.repository.ProductRepository
import com.eckscanner.databinding.ActivityTransferBinding
import com.eckscanner.scanner.DataWedgeReceiver
import com.eckscanner.scanner.ScanFeedback
import kotlinx.coroutines.launch

class TransferActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransferBinding
    private lateinit var repository: ProductRepository
    private lateinit var scanReceiver: DataWedgeReceiver
    private lateinit var adapter: TransferScanAdapter

    private val scanItems = mutableListOf<TransferScanItem>()
    private var fromWarehouseId: Int = -1
    private var toWarehouseId: Int = -1
    private var warehouses: List<WarehouseEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProductRepository(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = TransferScanAdapter(scanItems)
        binding.recyclerTransferItems.layoutManager = LinearLayoutManager(this)
        binding.recyclerTransferItems.adapter = adapter

        binding.btnFrom.setOnClickListener { selectWarehouse(true) }
        binding.btnTo.setOnClickListener { selectWarehouse(false) }
        binding.btnCreateTransfer.setOnClickListener { createTransfer() }

        // Pre-select current warehouse as origin
        val currentWarehouse = ApiClient.getWarehouseId(this)
        if (currentWarehouse != -1) {
            fromWarehouseId = currentWarehouse
        }

        scanReceiver = DataWedgeReceiver { code ->
            runOnUiThread { onScan(code) }
        }

        loadWarehouses()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanReceiver, DataWedgeReceiver.getIntentFilter())
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(scanReceiver) } catch (_: Exception) {}
    }

    private fun loadWarehouses() {
        lifecycleScope.launch {
            warehouses = AppDatabase.getInstance(this@TransferActivity).warehouseDao().getAll()
            updateWarehouseButtons()
        }
    }

    private fun updateWarehouseButtons() {
        lifecycleScope.launch {
            if (fromWarehouseId != -1) {
                val w = AppDatabase.getInstance(this@TransferActivity).warehouseDao().getById(fromWarehouseId)
                binding.btnFrom.text = w?.name ?: "Almacen #$fromWarehouseId"
            }
            if (toWarehouseId != -1) {
                val w = AppDatabase.getInstance(this@TransferActivity).warehouseDao().getById(toWarehouseId)
                binding.btnTo.text = w?.name ?: "Almacen #$toWarehouseId"
            }
        }
    }

    private fun selectWarehouse(isOrigin: Boolean) {
        if (warehouses.isEmpty()) {
            Toast.makeText(this, "Sincroniza primero", Toast.LENGTH_SHORT).show()
            return
        }

        val names = warehouses.map { it.name }.toTypedArray()
        val currentId = if (isOrigin) fromWarehouseId else toWarehouseId
        val checked = warehouses.indexOfFirst { it.id == currentId }

        AlertDialog.Builder(this)
            .setTitle(if (isOrigin) getString(R.string.from_warehouse) else getString(R.string.to_warehouse))
            .setSingleChoiceItems(names, checked) { dialog, which ->
                val selected = warehouses[which]
                if (isOrigin) {
                    fromWarehouseId = selected.id
                    binding.btnFrom.text = selected.name
                } else {
                    toWarehouseId = selected.id
                    binding.btnTo.text = selected.name
                }
                dialog.dismiss()
                updatePrompt()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updatePrompt() {
        if (fromWarehouseId != -1 && toWarehouseId != -1) {
            if (fromWarehouseId == toWarehouseId) {
                binding.txtTransferPrompt.text = "Origen y destino deben ser diferentes"
            } else {
                binding.txtTransferPrompt.text = "Escanea los productos a transferir"
            }
        }
    }

    private fun onScan(code: String) {
        if (fromWarehouseId == -1 || toWarehouseId == -1) {
            Toast.makeText(this, "Selecciona origen y destino primero", Toast.LENGTH_SHORT).show()
            return
        }
        if (fromWarehouseId == toWarehouseId) {
            Toast.makeText(this, "Origen y destino deben ser diferentes", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = repository.findByCode(code)
            if (result == null) {
                ScanFeedback.error(this@TransferActivity)
                Toast.makeText(this@TransferActivity, "No encontrado: $code", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val productId = result.product.id
            val variantId = result.matchedVariant?.id

            // Check available stock in origin warehouse
            val stock = repository.getStockInWarehouse(productId, variantId ?: 0, fromWarehouseId)
            val available = stock?.available ?: 0.0
            val existing = scanItems.find { it.productId == productId && it.variantId == variantId }
            val alreadyInList = existing?.quantity ?: 0.0

            if (alreadyInList + 1 > available) {
                ScanFeedback.error(this@TransferActivity)
                Toast.makeText(this@TransferActivity, "Stock insuficiente: ${available.toInt()} disponible", Toast.LENGTH_SHORT).show()
                return@launch
            }

            ScanFeedback.success(this@TransferActivity)

            if (existing != null) {
                existing.quantity += 1.0
                val index = scanItems.indexOf(existing)
                adapter.notifyItemChanged(index)
            } else {
                val item = TransferScanItem(
                    productId = productId,
                    variantId = variantId,
                    productName = result.product.name,
                    variantName = result.matchedVariant?.name,
                    code = result.matchedVariant?.sku ?: result.product.code,
                    quantity = 1.0
                )
                scanItems.add(0, item)
                adapter.notifyItemInserted(0)
                binding.recyclerTransferItems.scrollToPosition(0)
            }

            binding.layoutTransferBottom.visibility = View.VISIBLE
            binding.txtTransferTotal.text = "${scanItems.size} productos | ${scanItems.sumOf { it.quantity }.toInt()} unidades"
        }
    }

    private fun createTransfer() {
        if (scanItems.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_transfer))
            .setMessage("${scanItems.size} productos, ${scanItems.sumOf { it.quantity }.toInt()} unidades")
            .setPositiveButton(getString(R.string.confirm)) { _, _ -> doCreateTransfer() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun doCreateTransfer() {
        binding.btnCreateTransfer.isEnabled = false
        binding.btnCreateTransfer.text = "ENVIANDO..."

        lifecycleScope.launch {
            try {
                // Step 1: Create transfer
                val request = CreateTransferRequest(
                    fromWarehouseId = fromWarehouseId,
                    toWarehouseId = toWarehouseId,
                    notes = "Creada via ECK Scanner",
                    items = scanItems.map { item ->
                        TransferItemRequest(
                            productId = item.productId,
                            variantId = item.variantId,
                            quantity = item.quantity
                        )
                    }
                )

                val createResponse = ApiClient.getService().createTransfer(request)
                if (!createResponse.isSuccessful) {
                    val error = createResponse.errorBody()?.string()?.take(100) ?: ""
                    Toast.makeText(this@TransferActivity, "Error creando: $error", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val transferId = createResponse.body()?.data?.id
                if (transferId == null) {
                    Toast.makeText(this@TransferActivity, "Error: no se obtuvo ID de transferencia", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Step 2: Send (Pendiente → En Transito)
                val sendResponse = ApiClient.getService().sendTransfer(transferId)
                if (!sendResponse.isSuccessful) {
                    Toast.makeText(this@TransferActivity, "Creada pero error al enviar", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Step 3: Receive all (En Transito → Completada)
                val receiveResponse = ApiClient.getService().receiveTransfer(transferId, null)
                if (!receiveResponse.isSuccessful) {
                    Toast.makeText(this@TransferActivity, "Enviada pero error al recibir", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // All done
                ScanFeedback.success(this@TransferActivity)
                Toast.makeText(this@TransferActivity, "Transferencia completada", Toast.LENGTH_LONG).show()
                scanItems.clear()
                adapter.notifyDataSetChanged()
                binding.layoutTransferBottom.visibility = View.GONE

            } catch (e: Exception) {
                ScanFeedback.error(this@TransferActivity)
                Toast.makeText(this@TransferActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnCreateTransfer.isEnabled = true
                binding.btnCreateTransfer.text = getString(R.string.create_transfer)
            }
        }
    }
}
