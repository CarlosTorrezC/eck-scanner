package com.eckscanner.ui.shelf

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.eckscanner.data.remote.ApiClient
import com.eckscanner.data.remote.ShelfDto
import com.eckscanner.databinding.ActivityShelfListBinding
import kotlinx.coroutines.launch

class ShelfListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShelfListBinding
    private val shelves = mutableListOf<ShelfDto>()
    private lateinit var adapter: ShelfListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShelfListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ShelfListAdapter(shelves) { shelf ->
            val intent = Intent(this, ShelfScanActivity::class.java)
            intent.putExtra("shelf_id", shelf.id)
            intent.putExtra("shelf_name", shelf.name)
            startActivity(intent)
        }
        binding.recyclerShelves.layoutManager = LinearLayoutManager(this)
        binding.recyclerShelves.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadShelves()
    }

    private fun loadShelves() {
        binding.progressBar.visibility = View.VISIBLE
        binding.txtEmpty.visibility = View.GONE

        val warehouseId = ApiClient.getWarehouseId(this)

        lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getShelves(
                    warehouseId = if (warehouseId != -1) warehouseId else null
                )
                if (response.isSuccessful) {
                    val data = response.body()?.data ?: emptyList()
                    shelves.clear()
                    shelves.addAll(data)
                    adapter.notifyDataSetChanged()
                    binding.txtEmpty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    Toast.makeText(this@ShelfListActivity, "Error ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ShelfListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
