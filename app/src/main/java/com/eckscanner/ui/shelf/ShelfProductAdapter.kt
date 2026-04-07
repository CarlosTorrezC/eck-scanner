package com.eckscanner.ui.shelf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.eckscanner.R
import com.eckscanner.databinding.ItemShelfProductBinding

class ShelfProductAdapter(
    private val items: List<ShelfProductItem>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ShelfProductAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemShelfProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShelfProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        val ctx = holder.itemView.context

        b.txtName.text = item.productName
        b.txtDetail.text = buildString {
            append(item.variantSku ?: item.productCode)
            item.variantName?.let { append(" | $it") }
        }

        b.txtStock.text = ctx.getString(R.string.stock_qty, item.warehouseStock.toInt().toString())

        if (item.otherShelves.isNotEmpty()) {
            b.txtOtherShelves.visibility = View.VISIBLE
            b.txtOtherShelves.text = ctx.getString(R.string.other_shelves, item.otherShelves.joinToString(", "))
        } else {
            b.txtOtherShelves.visibility = View.GONE
        }

        b.btnRemove.setOnClickListener { onRemove(holder.adapterPosition) }
    }

    override fun getItemCount() = items.size
}
