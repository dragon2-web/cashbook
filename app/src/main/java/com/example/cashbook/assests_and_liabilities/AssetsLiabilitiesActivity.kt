package com.example.cashbook.assests_and_liabilities

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.cashbook.R
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

class AssetsLiabilitiesActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var tvTotalAssets:     TextView
    private lateinit var tvTotalLoans:      TextView
    private lateinit var tvTotalCreditCard: TextView
    private lateinit var tvTotalCapital:    TextView
    private lateinit var tvNetWorth:        TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assets_liabilities)

        tvTotalAssets     = findViewById(R.id.tvTotalAssets)
        tvTotalLoans      = findViewById(R.id.tvTotalLoans)
        tvTotalCreditCard = findViewById(R.id.tvTotalCreditCard)
        tvTotalCapital    = findViewById(R.id.tvTotalCapital)
        tvNetWorth        = findViewById(R.id.tvNetWorth)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<View>(R.id.cardAssets).setOnClickListener {
            startActivity(AssetsActivity.newIntent(this))
        }
        findViewById<View>(R.id.cardLoans).setOnClickListener {
            startActivity(LoansActivity.newIntent(this))
        }
        findViewById<View>(R.id.cardCreditCards).setOnClickListener {
            startActivity(CreditCardsActivity.newIntent(this))
        }
        findViewById<View>(R.id.cardCapital).setOnClickListener {
            startActivity(CapitalActivity.newIntent(this))
        }

        loadSummary()
    }

    private fun loadSummary() {
        // Load from firm/main
        db.collection("firm").document("main").get()
            .addOnSuccessListener { doc ->
                val totalAssets     = doc.getDouble("totalAssets")     ?: 0.0
                val totalLoans      = doc.getDouble("totalLoans")      ?: 0.0
                val totalCreditCard = doc.getDouble("totalCreditCard") ?: 0.0
                val totalCapital    = doc.getDouble("totalCapital")    ?: 0.0
                val cash            = doc.getDouble("cash")            ?: 0.0
                val bank            = doc.getDouble("bank")            ?: 0.0
                val receivable      = doc.getDouble("receivable")      ?: 0.0
                val payable         = doc.getDouble("payable")         ?: 0.0

                tvTotalAssets.text     = formatAmount(totalAssets)
                tvTotalLoans.text      = formatAmount(totalLoans)
                tvTotalCreditCard.text = formatAmount(totalCreditCard)
                tvTotalCapital.text    = formatAmount(totalCapital)

                val netWorth = totalAssets + cash + bank + receivable + totalCapital -
                        payable - totalLoans - totalCreditCard

                tvNetWorth.text = formatAmount(netWorth)
                tvNetWorth.setTextColor(
                    if (netWorth >= 0) 0xFF1B5E20.toInt()
                    else               0xFFC62828.toInt()
                )
            }
    }

    private fun formatAmount(amount: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return "₹${format.format(amount)}"
    }

    companion object {
        fun newIntent(context: Context) =
            Intent(context, AssetsLiabilitiesActivity::class.java)
    }
}