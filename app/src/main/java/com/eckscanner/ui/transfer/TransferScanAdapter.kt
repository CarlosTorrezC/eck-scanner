package com.eckscanner.ui.transfer

import android.view.LayoutInflater
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
    private val items: MutableList<TransferScanItem>,
    private val onQuantityChanged: () -> Unit
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

        holder.binding.btnPlus.setOnClickListener {
            item.quantity += 1.0
            notifyItemChanged(holder.adapterPosition)
            onQuantityChanged()
        }

        holder.binding.btnMinus.setOnClickListener {
            if (item.quantity > 1) {
                item.quantity -= 1.0
                notifyItemChanged(holder.adapterPosition)
                onQuantityChanged()
            }
        }

        holder.binding.btnRemove.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos >= 0 && pos < items.size) {
                items.removeAt(pos)
                notifyItemRemoved(pos)
                onQuantityChanged()
            }
        }
    }

    override fun getItemCount() = items.size
}
