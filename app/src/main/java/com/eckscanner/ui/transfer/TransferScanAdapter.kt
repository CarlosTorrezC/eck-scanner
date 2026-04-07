package com.eckscanner.ui.transfer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.eckscanner.databinding.ItemTransferScanBinding

data class TransferScanItem(
    val productId: Int,
    val variantId: Int?,
    val productName: String,
    val variantName: String?,
    val code: String,
    var quantity: Double
)

class TransferScanAdapter(
    private val items: List<TransferScanItem>
) : RecyclerView.Adapter<TransferScanAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTransferScanBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransferScanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.txtName.text = item.productName
        holder.binding.txtDetail.text = buildString {
            append(item.code)
            item.variantName?.let { append(" | $it") }
        }
        holder.binding.txtQty.text = item.quantity.toInt().toString()
    }

    override fun getItemCount() = items.size
}
