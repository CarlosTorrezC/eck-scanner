package com.eckscanner.ui.shelf

import android.graphics.Color
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

        // Show variant name prominently, product name secondary
        if (item.variantName != null) {
            b.txtName.text = "${item.variantName} - ${item.productName}"
            b.txtName.setTextColor(Color.parseColor("#004D40"))
        } else {
            b.txtName.text = item.productName
            b.txtName.setTextColor(Color.parseColor("#333333"))
        }

        b.txtDetail.text = item.variantSku ?: item.productCode

        // Hide stock for shelves - not relevant
        b.txtStock.visibility = View.GONE

        if (item.otherShelves.isNotEmpty()) {
            b.txtOtherShelves.visibility = View.VISIBLE
            b.txtOtherShelves.text = "Tambien en: ${item.otherShelves.joinToString(", ")}"
        } else {
            b.txtOtherShelves.visibility = View.GONE
        }

        b.btnRemove.setOnClickListener { onRemove(holder.adapterPosition) }
    }

    override fun getItemCount() = items.size
}
