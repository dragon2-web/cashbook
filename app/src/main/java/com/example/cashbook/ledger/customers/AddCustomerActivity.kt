package com.example.cashbook.ledger.customers

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cashbook.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class AddCustomerActivity : AppCompatActivity() {

    private lateinit var nameInput: TextInputEditText
    private lateinit var addressInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var gstinInput: TextInputEditText
    private lateinit var saveButton: MaterialButton

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_add_customer)

        nameInput = findViewById(R.id.customerNameInput)
        addressInput = findViewById(R.id.customerAddressInput)
        phoneInput = findViewById(R.id.customerPhoneInput)
        gstinInput = findViewById(R.id.customerGSTINInput)
        saveButton = findViewById(R.id.saveCustomerBtn)

        saveButton.setOnClickListener {
            saveCustomer()
        }
    }

    private fun saveCustomer() {

        val name = nameInput.text.toString().trim()
        val address = addressInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val gstin = gstinInput.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Customer name required", Toast.LENGTH_SHORT).show()
            return
        }

        val customerId = UUID.randomUUID().toString()

        val customerData = hashMapOf(
            "customerId" to customerId,
            "name" to name,
            "address" to address,
            "phone" to phone,
            "gstin" to gstin,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("customers")
            .document(customerId)
            .set(customerData)
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Customer added successfully",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "Failed to save customer",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}