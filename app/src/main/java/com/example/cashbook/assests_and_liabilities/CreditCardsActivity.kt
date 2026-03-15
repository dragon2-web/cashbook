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

class CreditCardsActivity : AppCompatActivity() {

    private lateinit var container:  LinearLayout
    private lateinit var emptyState: TextView
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credit_cards)

        container  = findViewById(R.id.cardsContainer)
        emptyState = findViewById(R.id.emptyState)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            startActivity(AddCreditCardActivity.newIntent(this))
        }

        loadCards()
    }

    override fun onResume() {
        super.onResume()
        loadCards()
    }

    private fun loadCards() {
        db.collection("liabilities").document("creditCards")
            .collection("items").orderBy("createdAt").get()
            .addOnSuccessListener { snapshot ->
                container.removeAllViews()

                if (snapshot.isEmpty) {
                    emptyState.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                emptyState.visibility = View.GONE
                var totalOutstanding  = 0.0

                snapshot.documents.forEach { doc ->
                    val cardId           = doc.id
                    val name             = doc.getString("name")             ?: ""
                    val bank             = doc.getString("bank")             ?: ""
                    val creditLimit      = doc.getDouble("creditLimit")      ?: 0.0
                    val outstandingBal   = doc.getDouble("outstandingBalance") ?: 0.0
                    val dueDate          = doc.getString("dueDate")          ?: ""
                    totalOutstanding    += outstandingBal

                    addCardRow(cardId, name, bank, creditLimit, outstandingBal, dueDate)
                }

                addTotalRow(totalOutstanding)
            }
    }

    private fun addCardRow(
        cardId:         String,
        name:           String,
        bank:           String,
        creditLimit:    Double,
        outstandingBal: Double,
        dueDate:        String
    ) {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius        = 12f * resources.displayMetrics.density
            cardElevation = 4f * resources.displayMetrics.density
            setCardBackgroundColor(0xFF1E1E2E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(8)) }
            setOnClickListener {
                startActivity(CardPaymentsActivity.newIntent(this@CreditCardsActivity, cardId, name))
            }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

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
                text     = bank
                textSize = 11f
                setTextColor(0xFF90CAF9.toInt())
            })
        })

        row1.addView(LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            addView(TextView(context).apply {
                text     = formatAmount(outstandingBal)
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(0xFFC62828.toInt())
                gravity  = Gravity.END
            })
            addView(TextView(context).apply {
                text     = "Outstanding"
                textSize = 10f
                setTextColor(0xFF90A4AE.toInt())
                gravity  = Gravity.END
            })
        })

        inner.addView(row1)
        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                .also { it.setMargins(0, dp(6), 0, dp(6)) }
            setBackgroundColor(0xFF2A2A3E.toInt())
        })

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        listOf(
            "Limit"    to formatAmount(creditLimit),
            "Due Date" to dueDate,
            "Used"     to "${((outstandingBal / creditLimit) * 100).toInt()}%"
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
        fun newIntent(context: Context) = Intent(context, CreditCardsActivity::class.java)
    }
}