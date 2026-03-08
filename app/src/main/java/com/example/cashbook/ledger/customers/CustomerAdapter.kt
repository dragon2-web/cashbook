package com.example.cashbook.ledger.customers

import CustomerModel
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cashbook.R

class CustomerAdapter(
private val customers: List<CustomerModel>,
private val onItemClick: (CustomerModel) -> Unit
) : RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder>() {

    class CustomerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.customerName)
        val address: TextView = view.findViewById(R.id.customerAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customer, parent, false)

        return CustomerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {

        val customer = customers[position]

        holder.name.text = customer.name
        holder.address.text = customer.address

        holder.itemView.setOnClickListener {
            onItemClick(customer)
        }
    }

    override fun getItemCount(): Int {
        return customers.size
    }
}