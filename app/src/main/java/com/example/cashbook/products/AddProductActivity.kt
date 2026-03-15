package com.example.cashbook.products

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cashbook.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class AddProductActivity : AppCompatActivity() {

    private lateinit var nameInput:  TextInputEditText
    private lateinit var priceInput: TextInputEditText
    private lateinit var unitInput:  TextInputEditText
    private lateinit var hsnInput:   TextInputEditText
    private lateinit var saveButton: MaterialButton

    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_product)

        findViewById<TextView>(R.id.toolbar).text = "Add Product"
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        nameInput  = findViewById(R.id.productNameInput)
        priceInput = findViewById(R.id.productPriceInput)
        unitInput  = findViewById(R.id.productUnitInput)
        hsnInput   = findViewById(R.id.productHsnInput)
        saveButton = findViewById(R.id.saveProductBtn)

        saveButton.setOnClickListener { saveProduct() }
    }

    private fun saveProduct() {
        val name  = nameInput.text.toString().trim().uppercase()
        val price = priceInput.text.toString().trim().toDoubleOrNull()
        val unit  = unitInput.text.toString().trim().uppercase()
        val hsn   = hsnInput.text.toString().trim().uppercase()

        if (name.isEmpty()) {
            nameInput.error = "Product name is required"
            nameInput.requestFocus()
            return
        }

        if (price == null) {
            priceInput.error = "Valid price is required"
            priceInput.requestFocus()
            return
        }

        saveButton.isEnabled = false
        saveButton.text = "Saving..."

        val productId = UUID.randomUUID().toString()

        val data = hashMapOf(
            "id"        to productId,
            "name"      to name,
            "price"     to price,
            "unit"      to unit,
            "hsn"       to hsn,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("products")
            .document(productId)
            .set(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Product saved", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                saveButton.isEnabled = true
                saveButton.text = "Save"
                Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
            }
    }

    companion object {
        fun newIntent(context: Context) =
            Intent(context, AddProductActivity::class.java)
    }
}