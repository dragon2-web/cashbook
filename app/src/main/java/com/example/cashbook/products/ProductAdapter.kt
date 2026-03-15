package com.example.cashbook.products

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cashbook.R
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private val products: MutableList<ProductModel>,
    private val onItemClick: (ProductModel) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val initial: TextView = view.findViewById(R.id.productInitial)
        val name:    TextView = view.findViewById(R.id.productName)
        val hsn:     TextView = view.findViewById(R.id.productHsn)
        val price:   TextView = view.findViewById(R.id.productPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]

        holder.initial.text = product.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.name.text    = product.name
        holder.hsn.text     = if (product.hsn.isNotEmpty()) "HSN: ${product.hsn}" else ""
        holder.price.text   = "₹${formatAmount(product.price)} / ${product.unit}"

        holder.itemView.setOnClickListener { onItemClick(product) }
    }

    override fun getItemCount() = products.size

    fun updateList(newList: MutableList<ProductModel>) {
        products.clear()
        products.addAll(newList)
        notifyDataSetChanged()
    }

    private fun formatAmount(amount: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return format.format(amount)
    }
}