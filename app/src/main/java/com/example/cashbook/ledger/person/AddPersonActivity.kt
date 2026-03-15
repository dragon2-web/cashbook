package com.example.cashbook.ledger.person

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

class AddPersonActivity : AppCompatActivity() {

    private lateinit var firmNameInput: TextInputEditText
    private lateinit var nameInput: TextInputEditText
    private lateinit var addressInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var gstinInput: TextInputEditText
    private lateinit var saveButton: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private lateinit var collectionName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_person)

        collectionName = intent.getStringExtra(EXTRA_COLLECTION_NAME) ?: run {
            Toast.makeText(this, "Collection name missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ── Toolbar ───────────────────────────────────────────────
        findViewById<TextView>(R.id.toolbar).text =
            "Add ${collectionName.replaceFirstChar { it.uppercaseChar() }}"

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // ── Views ─────────────────────────────────────────────────
        firmNameInput = findViewById(R.id.firmNameInput)
        nameInput     = findViewById(R.id.customerNameInput)
        addressInput  = findViewById(R.id.customerAddressInput)
        phoneInput    = findViewById(R.id.customerPhoneInput)
        gstinInput    = findViewById(R.id.customerGSTINInput)
        saveButton    = findViewById(R.id.saveCustomerBtn)

        saveButton.setOnClickListener { savePerson() }
    }

    private fun savePerson() {
        val firmName = firmNameInput.text.toString().trim().uppercase()
        val name     = nameInput.text.toString().trim().uppercase()
        val address  = addressInput.text.toString().trim().uppercase()
        val phone    = phoneInput.text.toString().trim().uppercase()
        val gstin    = gstinInput.text.toString().trim().uppercase()

        if (name.isEmpty()) {
            nameInput.error = "Name is required"
            nameInput.requestFocus()
            return
        }

        saveButton.isEnabled = false
        saveButton.text = "Saving..."

        val personId = UUID.randomUUID().toString()

        val data = hashMapOf(
            "id"             to personId,
            "firmName"       to firmName,
            "name"           to name,
            "address"        to address,
            "phone"          to phone,
            "gstin"          to gstin,
            "closingBalance" to 0.0,
            "createdAt"      to System.currentTimeMillis()
        )

        db.collection(collectionName)
            .document(personId)
            .set(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Saved successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                saveButton.isEnabled = true
                saveButton.text = "Save"
                Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
            }
    }

    companion object {

        const val EXTRA_COLLECTION_NAME = "COLLECTION_NAME"

        fun newIntent(context: Context, collectionName: String): Intent =
            Intent(context, AddPersonActivity::class.java).apply {
                putExtra(EXTRA_COLLECTION_NAME, collectionName)
            }
    }
}