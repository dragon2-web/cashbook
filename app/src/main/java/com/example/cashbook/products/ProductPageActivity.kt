package com.example.cashbook.products

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cashbook.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ProductPageActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProductAdapter

    private val db by lazy { FirebaseFirestore.getInstance() }
    private var listenerRegistration: ListenerRegistration? = null
    private val fullList = mutableListOf<ProductModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_product_page)

        recyclerView = findViewById(R.id.productRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val searchInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchInput)

        findViewById<FloatingActionButton>(R.id.addProductBtn).setOnClickListener {
            startActivity(AddProductActivity.newIntent(this))
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase()
                val filtered = if (query.isEmpty()) fullList.toMutableList()
                else fullList.filter {
                    it.name.lowercase().contains(query)
                }.toMutableList()
                adapter.updateList(filtered)
            }
        })

        listenToProducts()
    }

    private fun listenToProducts() {
        listenerRegistration = db.collection("products")
            .orderBy("name")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load products", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                fullList.clear()
                fullList.addAll(snapshot?.documents?.map { doc ->
                    ProductModel(
                        uid   = doc.id,
                        name  = doc.getString("name")  ?: "",
                        price = doc.getDouble("price") ?: 0.0,
                        unit  = doc.getString("unit")  ?: "",
                        hsn   = doc.getString("hsn")   ?: ""
                    )
                } ?: emptyList())

                if (!::adapter.isInitialized) {
                    adapter = ProductAdapter(fullList.toMutableList()) { product ->
                        Toast.makeText(this, product.name, Toast.LENGTH_SHORT).show()
                    }
                    recyclerView.adapter = adapter
                } else {
                    adapter.updateList(fullList.toMutableList())
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }

    companion object {
        fun newIntent(context: Context) =
            Intent(context, ProductPageActivity::class.java)
    }
}