package com.example.cashbook.ledger.person

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cashbook.R

class PersonAdapter(
    private val customers: MutableList<PersonModel>,
    private val onItemClick: (PersonModel) -> Unit
) : RecyclerView.Adapter<PersonAdapter.PersonViewHolder>() {

    class PersonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val initial: TextView = view.findViewById(R.id.customerInitial)
        val name: TextView    = view.findViewById(R.id.customerName)
        val address: TextView = view.findViewById(R.id.customerAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person, parent, false)
        return PersonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        val person = customers[position]

        holder.name.text    = person.name
        holder.address.text = person.address
        holder.initial.text = person.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        holder.itemView.setOnClickListener { onItemClick(person) }
    }

    override fun getItemCount() = customers.size

    fun updateList(newList: MutableList<PersonModel>) {
        customers.clear()
        customers.addAll(newList)
        notifyDataSetChanged()
    }
}