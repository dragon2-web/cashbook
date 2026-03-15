package com.example.cashbook.ledger.suppliers

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

class SupplierPageActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PersonAdapter
    private lateinit var addSupplierBtn: FloatingActionButton

    private val db by lazy { FirebaseFirestore.getInstance() }
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_supplier_page)

        recyclerView = findViewById(R.id.supplierRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        addSupplierBtn = findViewById(R.id.addSupplierBtn)
        addSupplierBtn.setOnClickListener {
            startActivity(AddPersonActivity.newIntent(this, "suppliers"))
        }

        listenToSuppliers()
    }

    private fun listenToSuppliers() {
        listenerRegistration = db.collection("suppliers")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Toast.makeText(this, "Failed to load suppliers", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val suppliers = snapshot?.documents?.map { document ->
                    PersonModel(
                        uid     = document.id,
                        name    = document.getString("name")    ?: "",
                        address = document.getString("address") ?: ""
                    )
                }?.toMutableList() ?: mutableListOf()

                if (!::adapter.isInitialized) {
                    adapter = PersonAdapter(suppliers) { supplier ->
                         startActivity(SupplierLedgerActivity.newIntent(this, supplier.uid))
                    }
                    recyclerView.adapter = adapter
                } else {
                    adapter.updateList(suppliers)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }
}