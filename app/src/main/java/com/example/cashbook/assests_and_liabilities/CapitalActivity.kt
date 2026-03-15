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

class CapitalActivity : AppCompatActivity() {

    private lateinit var container:    LinearLayout
    private lateinit var emptyState:   TextView
    private lateinit var tvTotal:      TextView
    private lateinit var amountInput:  TextInputEditText
    private lateinit var sourceInput:  TextInputEditText
    private lateinit var noteInput:    TextInputEditText
    private lateinit var dateInput:    TextInputEditText
    private lateinit var addBtn:       MaterialButton

    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capital)

        container   = findViewById(R.id.capitalContainer)
        emptyState  = findViewById(R.id.emptyState)
        tvTotal     = findViewById(R.id.tvTotal)
        amountInput = findViewById(R.id.amountInput)
        sourceInput = findViewById(R.id.sourceInput)
        noteInput   = findViewById(R.id.noteInput)
        dateInput   = findViewById(R.id.dateInput)
        addBtn      = findViewById(R.id.addBtn)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        dateInput.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))

        addBtn.setOnClickListener { addCapitalEntry() }

        loadCapital()
    }

    private fun loadCapital() {
        db.collection("liabilities").document("capital")
            .collection("entries").orderBy("createdAt").get()
            .addOnSuccessListener { snapshot ->
                container.removeAllViews()

                if (snapshot.isEmpty) {
                    emptyState.visibility = View.VISIBLE
                    tvTotal.text          = formatAmount(0.0)
                    return@addOnSuccessListener
                }

                emptyState.visibility = View.GONE
                var total             = 0.0

                snapshot.documents.forEach { doc ->
                    val amount = doc.getDouble("amount") ?: 0.0
                    val source = doc.getString("source") ?: ""
                    val note   = doc.getString("note")   ?: ""
                    val date   = doc.getString("date")   ?: ""
                    total     += amount

                    addCapitalRow(date, source, note, amount)
                }

                tvTotal.text = formatAmount(total)
            }
    }

    private fun addCapitalRow(date: String, source: String, note: String, amount: Double) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
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
                text     = source.ifEmpty { "Capital" }
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
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
                text     = date
                textSize = 11f
                setTextColor(0xFF90A4AE.toInt())
            })
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

    private fun addCapitalEntry() {
        val amount = amountInput.text.toString().trim().toDoubleOrNull()
        val source = sourceInput.text.toString().trim()
        val note   = noteInput.text.toString().trim()
        val date   = dateInput.text.toString().trim()

        if (amount == null || amount <= 0) { amountInput.error = "Required"; return }
        if (source.isEmpty())              { sourceInput.error = "Required"; return }

        addBtn.isEnabled = false
        addBtn.text      = "Saving..."

        val entryId = UUID.randomUUID().toString()
        val now     = System.currentTimeMillis()
        val batch   = db.batch()

        batch.set(
            db.collection("liabilities").document("capital")
                .collection("entries").document(entryId),
            mapOf(
                "id"        to entryId,
                "amount"    to amount,
                "source"    to source,
                "note"      to note,
                "date"      to date,
                "createdAt" to now
            )
        )

        batch.update(db.collection("firm").document("main"), mapOf(
            "totalCapital"  to FieldValue.increment(amount),
            "lastUpdatedAt" to now
        ))

        batch.commit()
            .addOnSuccessListener {
                amountInput.setText("")
                sourceInput.setText("")
                noteInput.setText("")
                addBtn.isEnabled = true
                addBtn.text      = "Add Entry"
                Toast.makeText(this, "Capital entry added ✓", Toast.LENGTH_SHORT).show()
                loadCapital()
            }
            .addOnFailureListener { e ->
                addBtn.isEnabled = true
                addBtn.text      = "Add Entry"
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
        fun newIntent(context: Context) = Intent(context, CapitalActivity::class.java)
    }
}