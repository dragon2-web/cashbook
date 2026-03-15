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

class AddCreditCardActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var nameInput:        TextInputEditText
    private lateinit var bankInput:        TextInputEditText
    private lateinit var limitInput:       TextInputEditText
    private lateinit var outstandingInput: TextInputEditText
    private lateinit var dueDateInput:     TextInputEditText
    private lateinit var saveBtn:          MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_credit_card)

        nameInput        = findViewById(R.id.nameInput)
        bankInput        = findViewById(R.id.bankInput)
        limitInput       = findViewById(R.id.limitInput)
        outstandingInput = findViewById(R.id.outstandingInput)
        dueDateInput     = findViewById(R.id.dueDateInput)
        saveBtn          = findViewById(R.id.saveBtn)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        dueDateInput.setText(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        )

        saveBtn.setOnClickListener { saveCard() }
    }

    private fun saveCard() {
        val name        = nameInput.text.toString().trim()
        val bank        = bankInput.text.toString().trim()
        val limit       = limitInput.text.toString().trim().toDoubleOrNull()
        val outstanding = outstandingInput.text.toString().trim().toDoubleOrNull() ?: 0.0
        val dueDate     = dueDateInput.text.toString().trim()

        if (name.isEmpty()) { nameInput.error = "Required";  return }
        if (bank.isEmpty()) { bankInput.error = "Required";  return }
        if (limit == null)  { limitInput.error = "Required"; return }

        saveBtn.isEnabled = false
        saveBtn.text      = "Saving..."

        val cardId = UUID.randomUUID().toString()
        val now    = System.currentTimeMillis()
        val batch  = db.batch()

        batch.set(
            db.collection("liabilities").document("creditCards")
                .collection("items").document(cardId),
            mapOf(
                "id"                 to cardId,
                "name"               to name,
                "bank"               to bank,
                "creditLimit"        to limit,
                "outstandingBalance" to outstanding,
                "dueDate"            to dueDate,
                "createdAt"          to now
            )
        )

        if (outstanding > 0) {
            batch.update(db.collection("firm").document("main"), mapOf(
                "totalCreditCard" to FieldValue.increment(outstanding),
                "lastUpdatedAt"   to now
            ))
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Card saved ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                saveBtn.isEnabled = true
                saveBtn.text      = "Save"
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, AddCreditCardActivity::class.java)
    }
}