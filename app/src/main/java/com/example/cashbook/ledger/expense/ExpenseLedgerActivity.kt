package com.example.cashbook.ledger.expense

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.cashbook.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.NumberFormat
import java.util.Locale

class ExpenseLedgerActivity : AppCompatActivity() {

    private lateinit var tvClosingBalance:     TextView
    private lateinit var transactionContainer: LinearLayout
    private lateinit var emptyState:           TextView

    private val db by lazy { FirebaseFirestore.getInstance() }
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_ledger)

        tvClosingBalance     = findViewById(R.id.tvClosingBalance)
        transactionContainer = findViewById(R.id.transactionContainer)
        emptyState           = findViewById(R.id.emptyState)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        loadFirmExpenses()
        listenToTransactions()
    }

    private fun loadFirmExpenses() {
        db.collection("firm").document("main").get()
            .addOnSuccessListener { doc ->
                val expenses = doc.getDouble("expenses") ?: 0.0
                tvClosingBalance.text = formatAmount(expenses)
            }
    }

    private fun listenToTransactions() {
        listenerRegistration = db.collection("expenses")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                transactionContainer.removeAllViews()

                if (snapshot.isEmpty) {
                    emptyState.visibility = android.view.View.VISIBLE
                    return@addSnapshotListener
                }

                emptyState.visibility = android.view.View.GONE

                var computedTotal = 0.0

                snapshot.documents.forEach { doc ->
                    val amount      = doc.getDouble("amount")      ?: 0.0
                    val description = doc.getString("description") ?: ""
                    val date        = doc.getString("date")        ?: ""
                    val mode        = doc.getString("mode")        ?: ""
                    val note        = doc.getString("note")        ?: ""

                    computedTotal += amount

                    addExpenseRow(date, description, mode, note, amount)
                }

                addTotalsRow(computedTotal)
                verifyBalance(computedTotal)
            }
    }

    private fun addExpenseRow(
        date:        String,
        description: String,
        mode:        String,
        note:        String,
        amount:      Double
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 14, 12, 14)
        }

        val tvDate = TextView(this).apply {
            text     = date
            textSize = 12f
            setTextColor(0xFF9E9E9E.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }

        val tvDesc = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)

            addView(TextView(context).apply {
                text     = description
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
            })

            if (note.isNotEmpty()) {
                addView(TextView(context).apply {
                    text     = note
                    textSize = 11f
                    setTextColor(0xFF90A4AE.toInt())
                })
            }

            addView(TextView(context).apply {
                text     = mode.uppercase()
                textSize = 10f
                setTextColor(0xFF90CAF9.toInt())
            })
        }

        val tvAmount = TextView(this).apply {
            text     = formatAmount(amount)
            textSize = 13f
            gravity  = Gravity.END
            setTextColor(0xFFC62828.toInt())
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }

        row.addView(tvDate)
        row.addView(tvDesc)
        row.addView(tvAmount)

        val divider = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(12, 0, 12, 0) }
            setBackgroundColor(0xFF2A2A3E.toInt())
        }

        transactionContainer.addView(row)
        transactionContainer.addView(divider)
    }

    private fun addTotalsRow(computedTotal: Double) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 16, 12, 8)
            setBackgroundColor(0xFF16213E.toInt())
        }

        val tvLabel = TextView(this).apply {
            text      = "Total Expenses"
            textSize  = 14f
            typeface  = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
        }

        val tvTotal = TextView(this).apply {
            text      = formatAmount(computedTotal)
            textSize  = 14f
            typeface  = Typeface.DEFAULT_BOLD
            gravity   = Gravity.END
            setTextColor(0xFFC62828.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
        }

        row.addView(tvLabel)
        row.addView(tvTotal)
        transactionContainer.addView(row)
    }

    private fun verifyBalance(computedTotal: Double) {
        db.collection("firm").document("main").get()
            .addOnSuccessListener { doc ->
                val firmExpenses = doc.getDouble("expenses") ?: 0.0
                if (Math.abs(computedTotal - firmExpenses) > 0.01) {
                    Toast.makeText(
                        this,
                        "⚠ Mismatch! Computed: ${formatAmount(computedTotal)} | Firm: ${formatAmount(firmExpenses)}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun formatAmount(amount: Double): String {
        if (amount == 0.0) return "—"
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return "₹${format.format(amount)}"
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }

    companion object {
        fun newIntent(context: Context) =
            Intent(context, ExpenseLedgerActivity::class.java)
    }
}