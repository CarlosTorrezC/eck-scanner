package com.eckscanner.ui.transfer

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.eckscanner.R
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.ReceiveItemRequest
import com.eckscanner.data.remote.ReceiveTransferRequest
import com.eckscanner.data.remote.TransferDetailDto
import com.eckscanner.data.remote.TransferDto
import com.eckscanner.databinding.ActivityReceiveTransferBinding
import com.eckscanner.scanner.DataWedgeReceiver
import kotlinx.coroutines.launch

class ReceiveTransferActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiveTransferBinding
    private lateinit var scanReceiver: DataWedgeReceiver
    private lateinit var adapter: ReceiveAdapter

    private var transfer: TransferDto? = null
    private val receiveItems = mutableListOf<ReceiveItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ReceiveAdapter(receiveItems)
        binding.recyclerItems.layoutManager = LinearLayoutManager(this)
        binding.recyclerItems.adapter = adapter

        binding.btnReceiveAll.setOnClickListener { confirmReceiveAll() }
        binding.btnReceiveScanned.setOnClickListener { confirmReceiveScanned() }

        scanReceiver = DataWedgeReceiver { code ->
            runOnUiThread { onScan(code) }
        }

        val transferId = intent.getIntExtra("transfer_id", -1)
        if (transferId != -1) loadTransfer(transferId)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanReceiver, DataWedgeReceiver.getIntentFilter())
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(scanReceiver)
    }

    private fun loadTransfer(id: Int) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getTransfer(id)
                if (response.isSuccessful) {
                    transfer = response.body()?.data
                    transfer?.let { showTransfer(it) }
                } else {
                    Toast.makeText(this@ReceiveTransferActivity, "Error ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReceiveTransferActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showTransfer(t: TransferDto) {
        binding.txtTransferCode.text = t.code
        binding.txtTransferRoute.text = "${t.fromWarehouse.name} → ${t.toWarehouse.name}"

        receiveItems.clear()
        t.details?.forEach { detail ->
            receiveItems.add(
                ReceiveItem(
                    detailId = detail.id,
                    productId = detail.product.id,
                    productName = detail.product.name,
                    productCode = detail.product.code,
                    variantName = detail.variant?.name,
                    variantSku = detail.variant?.sku,
                    expectedQty = detail.quantity,
                    receivedQty = 0.0
                )
            )
        }
        adapter.notifyDataSetChanged()
    }

    private fun onScan(code: String) {
        // Find matching item by code or SKU
        val item = receiveItems.find { it.variantSku == code || it.productCode == code }
        if (item != null) {
            item.receivedQty += 1.0
            val index = receiveItems.indexOf(item)
            adapter.notifyItemChanged(index)
        } else {
            Toast.makeText(this, "No corresponde a esta transferencia: $code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmReceiveAll() {
        val t = transfer ?: return
        AlertDialog.Builder(this)
            .setTitle("Recibir todo")
            .setMessage("Confirmar recepcion completa de ${t.code}?")
            .setPositiveButton(getString(R.string.confirm)) { _, _ -> doReceive(null) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmReceiveScanned() {
        val scanned = receiveItems.filter { it.receivedQty > 0 }
        if (scanned.isEmpty()) {
            Toast.makeText(this, "Escanea al menos un producto", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Recibir escaneados")
            .setMessage("${scanned.size} productos escaneados")
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val items = scanned.map {
                    ReceiveItemRequest(detailId = it.detailId, receivedQuantity = it.receivedQty)
                }
                doReceive(items)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun doReceive(items: List<ReceiveItemRequest>?) {
        val t = transfer ?: return
        binding.btnReceiveAll.isEnabled = false
        binding.btnReceiveScanned.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = if (items != null) ReceiveTransferRequest(items = items) else null
                val response = ApiClient.getService().receiveTransfer(t.id, request)
                if (response.isSuccessful) {
                    Toast.makeText(this@ReceiveTransferActivity, "Transferencia recibida", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@ReceiveTransferActivity, "Error ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReceiveTransferActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnReceiveAll.isEnabled = true
                binding.btnReceiveScanned.isEnabled = true
            }
        }
    }
}

data class ReceiveItem(
    val detailId: Int,
    val productId: Int,
    val productName: String,
    val productCode: String,
    val variantName: String?,
    val variantSku: String?,
    val expectedQty: Double,
    var receivedQty: Double
)
