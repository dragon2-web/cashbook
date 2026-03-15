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

class AddLoanActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var nameInput:      TextInputEditText
    private lateinit var lenderInput:    TextInputEditText
    private lateinit var typeInput:      AutoCompleteTextView
    private lateinit var principalInput: TextInputEditText
    private lateinit var interestInput:  TextInputEditText
    private lateinit var tenureInput:    TextInputEditText
    private lateinit var emiInput:       TextInputEditText
    private lateinit var startDateInput: TextInputEditText
    private lateinit var dueDateInput:   TextInputEditText
    private lateinit var saveBtn:        MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_loan)

        nameInput      = findViewById(R.id.nameInput)
        lenderInput    = findViewById(R.id.lenderInput)
        typeInput      = findViewById(R.id.typeInput)
        principalInput = findViewById(R.id.principalInput)
        interestInput  = findViewById(R.id.interestInput)
        tenureInput    = findViewById(R.id.tenureInput)
        emiInput       = findViewById(R.id.emiInput)
        startDateInput = findViewById(R.id.startDateInput)
        dueDateInput   = findViewById(R.id.dueDateInput)
        saveBtn        = findViewById(R.id.saveBtn)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        typeInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line,
                listOf("Bank", "Personal", "NBFC", "Other"))
        )

        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        startDateInput.setText(today)
        dueDateInput.setText(today)

        saveBtn.setOnClickListener { saveLoan() }
    }

    private fun saveLoan() {
        val name      = nameInput.text.toString().trim()
        val lender    = lenderInput.text.toString().trim()
        val type      = typeInput.text.toString().trim()
        val principal = principalInput.text.toString().trim().toDoubleOrNull()
        val interest  = interestInput.text.toString().trim().toDoubleOrNull()
        val tenure    = tenureInput.text.toString().trim().toIntOrNull()
        val emi       = emiInput.text.toString().trim().toDoubleOrNull()
        val startDate = startDateInput.text.toString().trim()
        val dueDate   = dueDateInput.text.toString().trim()

        if (name.isEmpty())     { nameInput.error = "Required";      return }
        if (lender.isEmpty())   { lenderInput.error = "Required";    return }
        if (principal == null)  { principalInput.error = "Required"; return }
        if (interest == null)   { interestInput.error = "Required";  return }
        if (tenure == null)     { tenureInput.error = "Required";    return }
        if (emi == null)        { emiInput.error = "Required";       return }

        saveBtn.isEnabled = false
        saveBtn.text      = "Saving..."

        val loanId = UUID.randomUUID().toString()
        val now    = System.currentTimeMillis()
        val batch  = db.batch()

        batch.set(
            db.collection("liabilities").document("loans")
                .collection("items").document(loanId),
            mapOf(
                "id"               to loanId,
                "name"             to name,
                "lender"           to lender,
                "type"             to type,
                "principalAmount"  to principal,
                "remainingBalance" to principal,
                "interestRate"     to interest,
                "tenureMonths"     to tenure,
                "emiAmount"        to emi,
                "startDate"        to startDate,
                "nextDueDate"      to dueDate,
                "createdAt"        to now
            )
        )

        batch.update(db.collection("firm").document("main"), mapOf(
            "totalLoans"    to FieldValue.increment(principal),
            "lastUpdatedAt" to now
        ))

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Loan saved ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                saveBtn.isEnabled = true
                saveBtn.text      = "Save"
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, AddLoanActivity::class.java)
    }
}