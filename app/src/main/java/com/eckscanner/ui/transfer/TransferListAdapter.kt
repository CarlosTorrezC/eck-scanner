package com.eckscanner.ui.transfer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.eckscanner.databinding.ItemTransferBinding
import com.eckscanner.data.remote.TransferDto

class TransferListAdapter(
    private val items: List<TransferDto>,
    private val onAction: (TransferDto, String) -> Unit
) : RecyclerView.Adapter<TransferListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTransferBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransferBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.txtCode.text = item.code
        b.txtRoute.text = "${item.fromWarehouse.name} → ${item.toWarehouse.name}"
        b.txtDate.text = item.createdAt.take(10)

        val statusText = item.statusLabel ?: item.status
        b.txtStatus.text = statusText

        when (item.status.lowercase()) {
            "pendiente" -> {
                b.txtStatus.setTextColor(Color.parseColor("#F57F17"))
                b.btnAction.text = "Enviar"
                b.btnAction.setOnClickListener { onAction(item, "send") }
            }
            "en_transito", "entransito" -> {
                b.txtStatus.setTextColor(Color.parseColor("#1565C0"))
                b.btnAction.text = "Recibir"
                b.btnAction.setOnClickListener { onAction(item, "receive") }
            }
            else -> {
                b.txtStatus.setTextColor(Color.GRAY)
                b.btnAction.text = "Ver"
                b.btnAction.isEnabled = false
            }
        }
    }

    override fun getItemCount() = items.size
}
