package com.example.cashbook.assests_and_liabilities


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.cashbook.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class CardPaymentsActivity : AppCompatActivity() {

    private lateinit var container:    LinearLayout
    private lateinit var emptyState:   TextView
    private lateinit var tvOutstanding: TextView
    private lateinit var amountInput:  TextInputEditText
    private lateinit var noteInput:    TextInputEditText
    private lateinit var dateInput:    TextInputEditText
    private lateinit var modeGroup:    RadioGroup
    private lateinit var payBtn:       MaterialButton

    private var cardId   = ""
    private var cardName = ""
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_payments)

        cardId   = intent.getStringExtra(EXTRA_CARD_ID)   ?: run { finish(); return }
        cardName = intent.getStringExtra(EXTRA_CARD_NAME) ?: ""

        container    = findViewById(R.id.paymentsContainer)
        emptyState   = findViewById(R.id.emptyState)
        tvOutstanding = findViewById(R.id.tvOutstanding)
        amountInput  = findViewById(R.id.amountInput)
        noteInput    = findViewById(R.id.noteInput)
        dateInput    = findViewById(R.id.dateInput)
        modeGroup    = findViewById(R.id.modeGroup)
        payBtn       = findViewById(R.id.payBtn)

        findViewById<TextView>(R.id.tvCardName).text = cardName

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        dateInput.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))

        payBtn.setOnClickListener { makePayment() }

        loadOutstanding()
        loadPayments()
    }

    private fun loadOutstanding() {
        db.collection("liabilities").document("creditCards")
            .collection("items").document(cardId).get()
            .addOnSuccessListener { doc ->
                val outstanding = doc.getDouble("outstandingBalance") ?: 0.0
                tvOutstanding.text = formatAmount(outstanding)
            }
    }

    private fun loadPayments() {
        db.collection("liabilities").document("creditCards")
            .collection("items").document(cardId)
            .collection("payments").orderBy("createdAt").get()
            .addOnSuccessListener { snapshot ->
                container.removeAllViews()

                if (snapshot.isEmpty) {
                    emptyState.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                emptyState.visibility = View.GONE

                snapshot.documents.forEach { doc ->
                    val amount = doc.getDouble("amount") ?: 0.0
                    val date   = doc.getString("date")   ?: ""
                    val note   = doc.getString("note")   ?: ""
                    val mode   = doc.getString("mode")   ?: ""

                    addPaymentRow(date, mode, note, amount)
                }
            }
    }

    private fun addPaymentRow(date: String, mode: String, note: String, amount: Double) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0xFF1E1E2E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(4)) }
        }

        row.addView(LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(TextView(context).apply {
                text     = date
                textSize = 12f
                setTextColor(0xFF90A4AE.toInt())
            })
            addView(TextView(context).apply {
                text     = mode.uppercase().ifEmpty { "PAYMENT" }
                textSize = 11f
                setTextColor(0xFF90CAF9.toInt())
            })
            if (note.isNotEmpty()) {
                addView(TextView(context).apply {
                    text     = note
                    textSize = 11f
                    setTextColor(0xFF90A4AE.toInt())
                })
            }
        })

        row.addView(TextView(this).apply {
            text     = formatAmount(amount)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFF1B5E20.toInt())
            gravity  = Gravity.END
        })

        container.addView(row)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(0xFF2A2A3E.toInt())
        })
    }

    private fun makePayment() {
        val amount = amountInput.text.toString().trim().toDoubleOrNull()
        val note   = noteInput.text.toString().trim()
        val date   = dateInput.text.toString().trim()
        val mode   = if (modeGroup.checkedRadioButtonId == R.id.rbBank) "bank" else "cash"

        if (amount == null || amount <= 0) { amountInput.error = "Required"; return }

        payBtn.isEnabled = false
        payBtn.text      = "Paying..."

        val paymentId = UUID.randomUUID().toString()
        val now       = System.currentTimeMillis()
        val batch     = db.batch()

        val cardRef = db.collection("liabilities").document("creditCards")
            .collection("items").document(cardId)

        // Payment record
        batch.set(cardRef.collection("payments").document(paymentId), mapOf(
            "id"        to paymentId,
            "amount"    to amount,
            "date"      to date,
            "note"      to note,
            "mode"      to mode,
            "createdAt" to now
        ))

        // Reduce outstanding on card
        batch.update(cardRef, mapOf(
            "outstandingBalance" to FieldValue.increment(-amount),
            "lastUpdatedAt"      to now
        ))

        // Reduce totalCreditCard in firm
        val firmUpdates = mutableMapOf<String, Any>(
            "totalCreditCard" to FieldValue.increment(-amount),
            "lastUpdatedAt"   to now
        )
        if (mode == "bank") firmUpdates["bank"] = FieldValue.increment(-amount)
        else                firmUpdates["cash"] = FieldValue.increment(-amount)

        batch.update(db.collection("firm").document("main"), firmUpdates)

        batch.commit()
            .addOnSuccessListener {
                amountInput.setText("")
                noteInput.setText("")
                payBtn.isEnabled = true
                payBtn.text      = "Make Payment"
                Toast.makeText(this, "Payment recorded ✓", Toast.LENGTH_SHORT).show()
                loadOutstanding()
                loadPayments()
            }
            .addOnFailureListener { e ->
                payBtn.isEnabled = true
                payBtn.text      = "Make Payment"
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun formatAmount(amount: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return "₹${format.format(amount)}"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_CARD_ID   = "CARD_ID"
        const val EXTRA_CARD_NAME = "CARD_NAME"

        fun newIntent(context: Context, cardId: String, cardName: String) =
            Intent(context, CardPaymentsActivity::class.java).apply {
                putExtra(EXTRA_CARD_ID,   cardId)
                putExtra(EXTRA_CARD_NAME, cardName)
            }
    }
}