package com.eckscanner.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.eckscanner.data.local.AppDatabase
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.databinding.ActivityHomeBinding
import com.eckscanner.sync.SyncManager
import com.eckscanner.ui.count.CountActivity
import com.eckscanner.ui.login.LoginActivity
import com.eckscanner.ui.lookup.LookupActivity
import com.eckscanner.ui.transfer.TransferActivity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        syncManager = SyncManager(this)

        binding.btnLookup.setOnClickListener {
            startActivity(Intent(this, LookupActivity::class.java))
        }
        binding.btnCount.setOnClickListener {
            if (ApiClient.getWarehouseId(this) == -1) {
                Toast.makeText(this, "Selecciona un almacen primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, CountActivity::class.java))
        }
        binding.btnTransfer.setOnClickListener {
            startActivity(Intent(this, TransferActivity::class.java))
        }
        binding.btnSync.setOnClickListener { performSync() }
        binding.btnWarehouse.setOnClickListener { showWarehouseSelector() }

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == com.eckscanner.R.id.action_disconnect) {
                disconnect()
                true
            } else false
        }

        updateSyncStatus()
        updateWarehouseButton()
    }

    override fun onResume() {
        super.onResume()
        updateWarehouseButton()
    }

    private fun performSync() {
        binding.btnSync.isEnabled = false
        binding.btnSync.text = getString(com.eckscanner.R.string.syncing)

        lifecycleScope.launch {
            val result = syncManager.syncAll()
            binding.btnSync.isEnabled = true
            binding.btnSync.text = getString(com.eckscanner.R.string.sync)

            if (result.error != null) {
                Toast.makeText(this@HomeActivity, "Error: ${result.error}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    this@HomeActivity,
                    getString(com.eckscanner.R.string.sync_result, result.productsUpdated, result.stockUpdated, result.warehousesUpdated),
                    Toast.LENGTH_LONG
                ).show()
                updateSyncStatus()
                updateWarehouseButton()
            }
        }
    }

    private fun updateSyncStatus() {
        val lastSync = ApiClient.getLastProductSync(this)
        if (lastSync != null) {
            try {
                val instant = Instant.parse(lastSync)
                val formatted = DateTimeFormatter.ofPattern("dd/MM HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(instant)
                binding.txtSyncStatus.text = getString(com.eckscanner.R.string.last_sync, formatted)
            } catch (_: Exception) {
                binding.txtSyncStatus.text = getString(com.eckscanner.R.string.last_sync, lastSync)
            }
        } else {
            binding.txtSyncStatus.text = getString(com.eckscanner.R.string.never_synced)
        }
    }

    private fun showWarehouseSelector() {
        lifecycleScope.launch {
            val warehouses = AppDatabase.getInstance(this@HomeActivity).warehouseDao().getAll()
            if (warehouses.isEmpty()) {
                Toast.makeText(this@HomeActivity, "Sincroniza primero para cargar almacenes", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val names = warehouses.map { it.name }.toTypedArray()
            val currentId = ApiClient.getWarehouseId(this@HomeActivity)
            val checkedItem = warehouses.indexOfFirst { it.id == currentId }

            AlertDialog.Builder(this@HomeActivity)
                .setTitle(getString(com.eckscanner.R.string.select_warehouse))
                .setSingleChoiceItems(names, checkedItem) { dialog, which ->
                    val selected = warehouses[which]
                    ApiClient.saveWarehouseId(this@HomeActivity, selected.id)
                    updateWarehouseButton()
                    dialog.dismiss()
                }
                .setNegativeButton(getString(com.eckscanner.R.string.cancel), null)
                .show()
        }
    }

    private fun updateWarehouseButton() {
        lifecycleScope.launch {
            val warehouseId = ApiClient.getWarehouseId(this@HomeActivity)
            if (warehouseId != -1) {
                val warehouse = AppDatabase.getInstance(this@HomeActivity).warehouseDao().getById(warehouseId)
                binding.btnWarehouse.text = warehouse?.name ?: "Almacen #$warehouseId"
            } else {
                binding.btnWarehouse.text = getString(com.eckscanner.R.string.select_warehouse)
            }
        }
    }

    private fun disconnect() {
        AlertDialog.Builder(this)
            .setTitle(getString(com.eckscanner.R.string.disconnect))
            .setMessage("Se eliminara la configuracion guardada")
            .setPositiveButton(getString(com.eckscanner.R.string.confirm)) { _, _ ->
                ApiClient.clearConfig(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton(getString(com.eckscanner.R.string.cancel), null)
            .show()
    }
}
