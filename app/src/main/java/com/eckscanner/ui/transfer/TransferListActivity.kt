package com.eckscanner.ui.transfer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.eckscanner.R
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.TransferDto
import com.eckscanner.databinding.ActivityTransferListBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class TransferListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransferListBinding
    private val transfers = mutableListOf<TransferDto>()
    private lateinit var adapter: TransferListAdapter
    private var currentStatus = "pendiente"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = TransferListAdapter(transfers) { transfer, action ->
            when (action) {
                "send" -> confirmSend(transfer)
                "receive" -> openReceive(transfer)
            }
        }
        binding.recyclerTransfers.layoutManager = LinearLayoutManager(this)
        binding.recyclerTransfers.adapter = adapter

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentStatus = if (tab.position == 0) "pendiente" else "en_transito"
                loadTransfers()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) { loadTransfers() }
        })

        loadTransfers()
    }

    override fun onResume() {
        super.onResume()
        loadTransfers()
    }

    private fun loadTransfers() {
        binding.progressBar.visibility = View.VISIBLE
        binding.txtEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getTransfers(status = currentStatus)
                if (response.isSuccessful) {
                    val data = response.body()?.data ?: emptyList()
                    transfers.clear()
                    transfers.addAll(data)
                    adapter.notifyDataSetChanged()
                    binding.txtEmpty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    Toast.makeText(this@TransferListActivity, "Error ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TransferListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun confirmSend(transfer: TransferDto) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.send_transfer))
            .setMessage("Enviar ${transfer.code}?\nDe: ${transfer.fromWarehouse.name}\nA: ${transfer.toWarehouse.name}")
            .setPositiveButton(getString(R.string.confirm)) { _, _ -> doSend(transfer.id) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun doSend(transferId: Int) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getService().sendTransfer(transferId)
                if (response.isSuccessful) {
                    Toast.makeText(this@TransferListActivity, "Transferencia enviada", Toast.LENGTH_SHORT).show()
                    loadTransfers()
                } else {
                    Toast.makeText(this@TransferListActivity, "Error ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TransferListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openReceive(transfer: TransferDto) {
        val intent = Intent(this, ReceiveTransferActivity::class.java)
        intent.putExtra("transfer_id", transfer.id)
        startActivity(intent)
    }
}
