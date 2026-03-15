package com.example.cashbook.assests_and_liabilities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.cashbook.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

class LoansActivity : AppCompatActivity() {

    private lateinit var container:  LinearLayout
    private lateinit var emptyState: TextView
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loans)

        container  = findViewById(R.id.loansContainer)
        emptyState = findViewById(R.id.emptyState)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            startActivity(AddLoanActivity.newIntent(this))
        }

        loadLoans()
    }

    override fun onResume() {
        super.onResume()
        loadLoans()
    }

    private fun loadLoans() {
        db.collection("liabilities").document("loans")
            .collection("items").orderBy("createdAt").get()
            .addOnSuccessListener { snapshot ->
                container.removeAllViews()

                if (snapshot.isEmpty) {
                    emptyState.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                emptyState.visibility = View.GONE
                var totalRemaining    = 0.0

                snapshot.documents.forEach { doc ->
                    val name              = doc.getString("name")              ?: ""
                    val lender            = doc.getString("lender")            ?: ""
                    val type              = doc.getString("type")              ?: ""
                    val principalAmount   = doc.getDouble("principalAmount")   ?: 0.0
                    val remainingBalance  = doc.getDouble("remainingBalance")  ?: 0.0
                    val interestRate      = doc.getDouble("interestRate")      ?: 0.0
                    val emiAmount         = doc.getDouble("emiAmount")         ?: 0.0
                    val nextDueDate       = doc.getString("nextDueDate")       ?: ""
                    totalRemaining       += remainingBalance

                    addLoanRow(
                        name, lender, type,
                        principalAmount, remainingBalance,
                        interestRate, emiAmount, nextDueDate
                    )
                }

                addTotalRow(totalRemaining)
            }
    }

    private fun addLoanRow(
        name:             String,
        lender:           String,
        type:             String,
        principalAmount:  Double,
        remainingBalance: Double,
        interestRate:     Double,
        emiAmount:        Double,
        nextDueDate:      String
    ) {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius        = 12f * resources.displayMetrics.density
            cardElevation = 4f * resources.displayMetrics.density
            setCardBackgroundColor(0xFF1E1E2E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(8)) }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // Row 1 — name + remaining
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(6)) }
        }

        row1.addView(LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(TextView(context).apply {
                text     = name
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(0xFFFFFFFF.toInt())
            })
            addView(TextView(context).apply {
                text     = "$lender · ${type.uppercase()}"
                textSize = 11f
                setTextColor(0xFF90CAF9.toInt())
            })
        })

        row1.addView(LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            addView(TextView(context).apply {
                text     = formatAmount(remainingBalance)
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(0xFFC62828.toInt())
                gravity  = Gravity.END
            })
            addView(TextView(context).apply {
                text     = "Remaining"
                textSize = 10f
                setTextColor(0xFF90A4AE.toInt())
                gravity  = Gravity.END
            })
        })

        // Divider
        inner.addView(row1)
        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                .also { it.setMargins(0, dp(6), 0, dp(6)) }
            setBackgroundColor(0xFF2A2A3E.toInt())
        })

        // Row 2 — stats
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        listOf(
            "Principal" to formatAmount(principalAmount),
            "EMI"       to formatAmount(emiAmount),
            "Rate"      to "$interestRate%",
            "Due"       to nextDueDate
        ).forEach { (label, value) ->
            val cell = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            cell.addView(TextView(this).apply {
                text     = label
                textSize = 10f
                setTextColor(0xFF90A4AE.toInt())
            })
            cell.addView(TextView(this).apply {
                text     = value
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(0xFFFFFFFF.toInt())
            })
            row2.addView(cell)
        }

        inner.addView(row2)
        card.addView(inner)
        container.addView(card)
    }

    private fun addTotalRow(total: Double) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0xFF16213E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, dp(8), 0, 0) }
        }

        row.addView(TextView(this).apply {
            text     = "Total Outstanding"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(TextView(this).apply {
            text     = formatAmount(total)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFFC62828.toInt())
        })

        container.addView(row)
    }

    private fun formatAmount(amount: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return "₹${format.format(amount)}"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun newIntent(context: Context) = Intent(context, LoansActivity::class.java)
    }
}