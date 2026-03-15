package com.example.cashbook.ledger.cash

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

class CashLedgerActivity : AppCompatActivity() {

    private lateinit var tvClosingBalance:   TextView
    private lateinit var transactionContainer: LinearLayout
    private lateinit var emptyState:         TextView

    private val db by lazy { FirebaseFirestore.getInstance() }
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cash_ledger)

        tvClosingBalance     = findViewById(R.id.tvClosingBalance)
        transactionContainer = findViewById(R.id.transactionContainer)
        emptyState           = findViewById(R.id.emptyState)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        loadFirmBalance()
        listenToTransactions()
    }

    // ── Load firm cash balance ────────────────────────────────────
    private fun loadFirmBalance() {
        db.collection("firm").document("main").get()
            .addOnSuccessListener { doc ->
                val cash = doc.getDouble("cash") ?: 0.0
                tvClosingBalance.text = formatAmount(cash)
            }
    }

    // ── Listen to cash transactions ───────────────────────────────
    private fun listenToTransactions() {
        listenerRegistration = db.collection("cash")
            .document("main")
            .collection("transactions")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                transactionContainer.removeAllViews()

                if (snapshot.isEmpty) {
                    emptyState.visibility = android.view.View.VISIBLE
                    return@addSnapshotListener
                }

                emptyState.visibility = android.view.View.GONE

                var computedBalance = 0.0

                snapshot.documents.forEach { doc ->
                    val amount      = doc.getDouble("amount")      ?: 0.0
                    val type        = doc.getString("type")        ?: ""
                    val description = doc.getString("description") ?: ""
                    val partyName   = doc.getString("partyName")   ?: ""
                    val date        = doc.getString("date")        ?: ""

                    if (type == "credit") computedBalance += amount
                    else                  computedBalance -= amount

                    addTransactionRow(date, description, partyName, amount, type)
                }

                addTotalsRow(computedBalance)
                verifyBalance(computedBalance)
            }
    }

    // ── Row builder ───────────────────────────────────────────────
    private fun addTransactionRow(
        date:        String,
        description: String,
        partyName : String,
        amount:      Double,
        type:        String
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

        val tvDesc = TextView(this).apply {
            text     = partyName.ifEmpty { description }
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        }

        val tvDebit = TextView(this).apply {
            text     = if (type == "debit") formatAmount(amount) else "—"
            textSize = 13f
            gravity  = Gravity.START
            setTextColor(0xFFC62828.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }

        val tvCredit = TextView(this).apply {
            text     = if (type == "credit") formatAmount(amount) else "—"
            textSize = 13f
            gravity  = Gravity.END
            setTextColor(0xFF1B5E20.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }

        row.addView(tvDate)
        row.addView(tvDesc)
        row.addView(tvDebit)
        row.addView(tvCredit)

        // Divider
        val divider = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(12, 0, 12, 0) }
            setBackgroundColor(0xFF2A2A3E.toInt())
        }

        transactionContainer.addView(row)
        transactionContainer.addView(divider)
    }

    // ── Totals row ────────────────────────────────────────────────
    private fun addTotalsRow(computedBalance: Double) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 16, 12, 8)
            setBackgroundColor(0xFF16213E.toInt())
        }

        val tvLabel = TextView(this).apply {
            text      = "Closing Balance"
            textSize  = 14f
            typeface  = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
        }

        val tvBalance = TextView(this).apply {
            text      = formatAmount(computedBalance)
            textSize  = 14f
            typeface  = Typeface.DEFAULT_BOLD
            gravity   = Gravity.END
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
        }

        row.addView(tvLabel)
        row.addView(tvBalance)
        transactionContainer.addView(row)
    }

    // ── Verify balance matches firm ───────────────────────────────
    private fun verifyBalance(computedBalance: Double) {
        db.collection("firm").document("main").get()
            .addOnSuccessListener { doc ->
                val firmCash = doc.getDouble("cash") ?: 0.0
                if (Math.abs(computedBalance - firmCash) > 0.01) {
                    Toast.makeText(
                        this,
                        "⚠ Balance mismatch! Computed: ${formatAmount(computedBalance)} | Firm: ${formatAmount(firmCash)}",
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
            Intent(context, CashLedgerActivity::class.java)
    }
}