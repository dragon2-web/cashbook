package com.example.cashbook.ledger.customers

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cashbook.R
import com.example.cashbook.ledger.person.AddPersonActivity
import com.example.cashbook.ledger.person.PersonAdapter
import com.example.cashbook.ledger.person.PersonModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CustomerPageActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PersonAdapter
    private lateinit var addCustomerBtn: FloatingActionButton

    private val db by lazy { FirebaseFirestore.getInstance() }
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_customer_page)

        recyclerView = findViewById(R.id.customerRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        addCustomerBtn = findViewById(R.id.addCustomerBtn)
        addCustomerBtn.setOnClickListener {
            startActivity(AddPersonActivity.newIntent(this, "customers"))
        }

        listenToCustomers()
    }

    private fun listenToCustomers() {
        listenerRegistration = db.collection("customers")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Toast.makeText(this, "Failed to load customers", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val customers = snapshot?.documents?.map { document ->
                    PersonModel(
                        uid     = document.id,
                        name    = document.getString("name")    ?: "",
                        address = document.getString("address") ?: ""
                    )
                }?.toMutableList() ?: mutableListOf()

                if (!::adapter.isInitialized) {
                    adapter = PersonAdapter(customers) { customer ->
                        startActivity(CustomerLedgerActivity.newIntent(this, customer.uid))
                    }
                    recyclerView.adapter = adapter
                } else {
                    adapter.updateList(customers)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()   // stop listening when activity is destroyed
    }
}