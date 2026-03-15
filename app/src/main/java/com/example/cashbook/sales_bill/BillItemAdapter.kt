package com.example.cashbook.sales_bill

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cashbook.R
import java.text.NumberFormat
import java.util.Locale

class BillItemAdapter(
    private val items: MutableList<BillItem>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<BillItemAdapter.BillItemViewHolder>() {

    class BillItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName:   TextView    = view.findViewById(R.id.tvItemName)
        val tvDetail: TextView    = view.findViewById(R.id.tvItemDetail)
        val tvAmount: TextView    = view.findViewById(R.id.tvItemAmount)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bill_row, parent, false)
        return BillItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: BillItemViewHolder, position: Int) {
        val item = items[position]

        holder.tvName.text   = item.productName
        holder.tvDetail.text = "${formatAmount(item.units)} × ₹${formatAmount(item.rate)}"
        holder.tvAmount.text = "₹${formatAmount(item.amount)}"

        holder.btnRemove.setOnClickListener { onRemove(holder.adapterPosition) }
    }

    override fun getItemCount() = items.size

    private fun formatAmount(value: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return format.format(value)
    }
}