package com.example.cashbook.dashboard

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.cashbook.R
import com.example.cashbook.assests_and_liabilities.AssetsLiabilitiesActivity
import com.example.cashbook.bills_and_transactions.TodayPurchaseActivity
import com.example.cashbook.bills_and_transactions.TodaySalesActivity
import com.example.cashbook.bills_and_transactions.TodayTransactionActivity
import com.example.cashbook.ledger.LedgerHomeActivity
import com.example.cashbook.products.ProductPageActivity
import com.example.cashbook.profile.ProfileActivity
import com.example.cashbook.purchase.PurchaseBillActivity
import com.example.cashbook.reports.ReportsActivity
import com.example.cashbook.sales_bill.SalesBillActivity
import com.example.cashbook.transactions.AddTransactionActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class Dashboard : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private var firmListener: ListenerRegistration? = null
    override fun onResume() {
        super.onResume()
        setupTodayCounters()
    }
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        onBackPressedDispatcher.addCallback(this) {
            android.app.AlertDialog.Builder(this@Dashboard)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Exit") { _, _ -> finishAffinity() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Date
        val currentDate = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date())
        findViewById<TextView>(R.id.txtDate).text = currentDate

        // Profile
        findViewById<Button>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        //today -counters
        setupTodayCounters()

        // Quick Actions
        findViewById<MaterialButton>(R.id.btnLedgers).setOnClickListener {
            startActivity(Intent(this, LedgerHomeActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnAddSalesBill).setOnClickListener {
            startActivity(SalesBillActivity.newIntent(this))
        }
        findViewById<MaterialButton>(R.id.btnAddPurchaseBill).setOnClickListener {
            startActivity(PurchaseBillActivity.newIntent(this))
        }
        findViewById<MaterialButton>(R.id.btnReports).setOnClickListener {
            startActivity(ReportsActivity.newIntent(this))
        }
        findViewById<MaterialButton>(R.id.btnAddTransaction).setOnClickListener {
            startActivity(AddTransactionActivity.newIntent(this))
        }
        findViewById<MaterialButton>(R.id.btnProducts).setOnClickListener {
            startActivity(Intent(this, ProductPageActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnAssetsLiabilities).setOnClickListener {
            startActivity(AssetsLiabilitiesActivity.newIntent(this))
        }

        listenToFirmData()
    }

    private fun listenToFirmData() {
        firmListener = db.collection("firm")
            .document("main")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val closingBalance  = snapshot.getDouble("closingBalance")  ?: 0.0
                val cash            = snapshot.getDouble("cash")            ?: 0.0
                val bank            = snapshot.getDouble("bank")            ?: 0.0
                val receivable      = snapshot.getDouble("receivable")      ?: 0.0
                val payable         = snapshot.getDouble("payable")         ?: 0.0

                findViewById<TextView>(R.id.txtTotalBalance).text = formatAmount(closingBalance)
                findViewById<TextView>(R.id.txtCash).text         = formatAmount(cash)
                findViewById<TextView>(R.id.txtBank).text         = formatAmount(bank)
                findViewById<TextView>(R.id.txtReceivable).text   = formatAmount(receivable)
                findViewById<TextView>(R.id.txtPayables).text     = formatAmount(payable)



            }
    }
    // ── Today counters setup ──────────────────────────────────────────
    private fun setupTodayCounters() {
        val tag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.collection("counters").document(tag).get()
            .addOnSuccessListener { doc ->
                val sbCount  = doc.getLong("sbCount")  ?: 0L
                val pbCount  = doc.getLong("pbCount")  ?: 0L
                val txnCount = doc.getLong("txnCount") ?: 0L


                // txnCount includes all — subtract sbCount and pbCount for pure transactions
                val pureTxn  = txnCount - sbCount - pbCount

                findViewById<TextView>(R.id.txtTodaySales).text    = sbCount.toString()
                findViewById<TextView>(R.id.txtTodayPurchase).text = pbCount.toString()
                findViewById<TextView>(R.id.txtTodayTxn).text      = pureTxn.coerceAtLeast(0).toString()
            }

        // Navigate on click
        findViewById<FrameLayout>(R.id.btnTodaySales).setOnClickListener {
            startActivity(TodaySalesActivity.newIntent(this))
        }
        findViewById<FrameLayout>(R.id.btnTodayPurchase).setOnClickListener {
            startActivity(TodayPurchaseActivity.newIntent(this))
        }
        findViewById<FrameLayout>(R.id.btnTodayTxn).setOnClickListener {
            startActivity(TodayTransactionActivity.newIntent(this))
        }
    }

    private fun formatAmount(amount: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return "₹${format.format(amount)}"
    }

    override fun onDestroy() {
        super.onDestroy()
        firmListener?.remove()
    }
}