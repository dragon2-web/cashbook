package com.example.cashbook.assests_and_liabilities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.cashbook.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AddAssetActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var nameInput:          TextInputEditText
    private lateinit var typeInput:          AutoCompleteTextView
    private lateinit var purchasePriceInput: TextInputEditText
    private lateinit var currentValueInput:  TextInputEditText
    private lateinit var purchaseDateInput:  TextInputEditText
    private lateinit var descriptionInput:   TextInputEditText
    private lateinit var saveBtn:            MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_asset)

        nameInput          = findViewById(R.id.nameInput)
        typeInput          = findViewById(R.id.typeInput)
        purchasePriceInput = findViewById(R.id.purchasePriceInput)
        currentValueInput  = findViewById(R.id.currentValueInput)
        purchaseDateInput  = findViewById(R.id.purchaseDateInput)
        descriptionInput   = findViewById(R.id.descriptionInput)
        saveBtn            = findViewById(R.id.saveBtn)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        typeInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line,
                listOf("Machinery", "Vehicle", "Property", "Equipment", "Other"))
        )

        purchaseDateInput.setText(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        )

        saveBtn.setOnClickListener { saveAsset() }
    }

    private fun saveAsset() {
        val name          = nameInput.text.toString().trim()
        val type          = typeInput.text.toString().trim()
        val purchasePrice = purchasePriceInput.text.toString().trim().toDoubleOrNull()
        val currentValue  = currentValueInput.text.toString().trim().toDoubleOrNull()
        val purchaseDate  = purchaseDateInput.text.toString().trim()
        val description   = descriptionInput.text.toString().trim()

        if (name.isEmpty())          { nameInput.error = "Required";          return }
        if (type.isEmpty())          { typeInput.error = "Required";          return }
        if (purchasePrice == null)   { purchasePriceInput.error = "Required"; return }
        if (currentValue == null)    { currentValueInput.error = "Required";  return }

        saveBtn.isEnabled = false
        saveBtn.text      = "Saving..."

        val assetId = UUID.randomUUID().toString()
        val now     = System.currentTimeMillis()

        val batch   = db.batch()

        batch.set(db.collection("assets").document(assetId), mapOf(
            "id"            to assetId,
            "name"          to name.uppercase(),
            "type"          to type,
            "purchasePrice" to purchasePrice,
            "currentValue"  to currentValue,
            "purchaseDate"  to purchaseDate,
            "description"   to description,
            "createdAt"     to now
        ))

        batch.update(db.collection("firm").document("main"), mapOf(
            "totalAssets"   to FieldValue.increment(currentValue),
            "lastUpdatedAt" to now
        ))

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Asset saved ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                saveBtn.isEnabled = true
                saveBtn.text      = "Save"
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, AddAssetActivity::class.java)
    }
}