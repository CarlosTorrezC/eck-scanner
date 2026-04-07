package com.eckscanner.ui.shelf

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.eckscanner.R
import com.eckscanner.data.remote.ShelfDto
import com.eckscanner.databinding.ItemShelfBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ShelfListAdapter(
    private val items: List<ShelfDto>,
    private val onScan: (ShelfDto) -> Unit
) : RecyclerView.Adapter<ShelfListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemShelfBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShelfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        val ctx = holder.itemView.context

        b.txtShelfName.text = item.name
        b.txtShelfCode.text = item.code ?: ""
        b.txtProductsCount.text = ctx.getString(R.string.products_on_shelf, item.productsCount)

        if (item.lastScannedAt != null) {
            try {
                val instant = Instant.parse(item.lastScannedAt)
                val formatted = DateTimeFormatter.ofPattern("dd/MM HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(instant)
                b.txtLastScanned.text = ctx.getString(R.string.last_scanned, formatted)
            } catch (_: Exception) {
                b.txtLastScanned.text = ctx.getString(R.string.last_scanned, item.lastScannedAt.take(10))
            }
        } else {
            b.txtLastScanned.text = ctx.getString(R.string.never_scanned)
        }

        b.btnScan.setOnClickListener { onScan(item) }
    }

    override fun getItemCount() = items.size
}
