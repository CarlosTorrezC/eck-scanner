package com.eckscanner.ui.transfer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.eckscanner.databinding.ItemReceiveBinding

class ReceiveAdapter(
    private val items: List<ReceiveItem>
) : RecyclerView.Adapter<ReceiveAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemReceiveBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReceiveBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.txtName.text = item.productName
        b.txtDetail.text = buildString {
            append(item.variantSku ?: item.productCode)
            item.variantName?.let { append(" | $it") }
        }

        b.txtExpected.text = item.expectedQty.toInt().toString()
        b.txtReceived.text = item.receivedQty.toInt().toString()

        val isComplete = item.receivedQty >= item.expectedQty
        b.txtReceived.setTextColor(
            when {
                item.receivedQty == 0.0 -> Color.parseColor("#666666")
                isComplete -> Color.parseColor("#2E7D32")
                else -> Color.parseColor("#F57F17")
            }
        )

        b.card.setCardBackgroundColor(
            if (isComplete) Color.parseColor("#E8F5E9") else Color.WHITE
        )
    }

    override fun getItemCount() = items.size
}
