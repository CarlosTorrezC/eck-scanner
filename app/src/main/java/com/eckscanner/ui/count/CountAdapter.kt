package com.eckscanner.ui.count

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.eckscanner.databinding.ItemCountBinding

class CountAdapter(
    private val items: List<CountItem>
) : RecyclerView.Adapter<CountAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCountBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.txtName.text = item.productName
        b.txtCode.text = item.code

        if (item.variantName != null) {
            b.txtVariant.text = item.variantName
            b.txtVariant.visibility = View.VISIBLE
        } else {
            b.txtVariant.visibility = View.GONE
        }

        b.txtSystemQty.text = item.systemQty.toInt().toString()
        b.txtScannedQty.text = item.scannedQty.toInt().toString()

        val diff = item.difference.toInt()
        b.txtDiff.text = if (diff > 0) "+$diff" else "$diff"
        b.txtDiff.setTextColor(
            when {
                diff > 0 -> Color.parseColor("#2E7D32")
                diff < 0 -> Color.parseColor("#C62828")
                else -> Color.parseColor("#666666")
            }
        )
    }

    override fun getItemCount() = items.size
}
