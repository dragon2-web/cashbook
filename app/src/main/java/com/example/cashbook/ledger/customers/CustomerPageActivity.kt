package com.example.cashbook.ledger.customers

import CustomerModel
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cashbook.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore

class CustomerPageActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CustomerAdapter
    private lateinit var addCustomerBtn: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_customer_page)

        recyclerView = findViewById(R.id.customerRecyclerView)
        addCustomerBtn = findViewById(R.id.addCustomerBtn)

        recyclerView.layoutManager = LinearLayoutManager(this)

        loadCustomers()

        addCustomerBtn.setOnClickListener {

            Toast.makeText(this, "Add Customer Clicked", Toast.LENGTH_SHORT).show()

        }
    }

    private fun loadCustomers() {

        val db = FirebaseFirestore.getInstance()

        db.collection("customers")
            .get()
            .addOnSuccessListener { result ->

                val customers = mutableListOf<CustomerModel>()

                for (document in result) {

                    val uid = document.id
                    val name = document.getString("name") ?: ""
                    val address = document.getString("address") ?: ""

                    val customer = CustomerModel(uid, name, address)

                    customers.add(customer)
                }

                adapter = CustomerAdapter(customers) { customer ->

                    Toast.makeText(
                        this,
                        "Clicked: ${customer.name}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // customer.uid can be used for next page
                }

                recyclerView.adapter = adapter
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "Failed to load customers",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}