package com.example.cashbook.reports

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.cashbook.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ReportsActivity : AppCompatActivity() {

    private lateinit var btnToday:        MaterialButton
    private lateinit var btnThisMonth:    MaterialButton
    private lateinit var btnThisYear:     MaterialButton
    private lateinit var btnCustomRange:  MaterialButton
    private lateinit var tvDateRange:     TextView
    private lateinit var reportContainer: LinearLayout
    private lateinit var emptyState:      TextView
    private lateinit var btnExport:       MaterialButton
    private lateinit var progressBar:     ProgressBar

    private val db by lazy { FirebaseFirestore.getInstance() }

    private var fromDate = ""
    private var toDate   = ""
    private var fromTag  = ""
    private var toTag    = ""

    private val salesRows    = mutableListOf<ReportRow>()
    private val purchaseRows = mutableListOf<ReportRow>()
    private val movementRows = mutableListOf<ReportRow>()

    private val customerBalances = mutableListOf<PartyBalance>()
    private val supplierBalances = mutableListOf<PartyBalance>()

    private var firmCash    = 0.0
    private var firmBank    = 0.0
    private var firmReceivable = 0.0
    private var firmPayable    = 0.0

    data class ReportRow(
        val date:        String,
        val billId:      String,
        val partyFirm:   String,
        val description: String,
        val txnType:     String,
        val amount:      Double,
        val type:        String
    )

    data class PartyBalance(
        val name:           String,
        val firmName:       String,
        val phone:          String,
        val closingBalance: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        bindViews()
        setupPresets()

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // ── Detailed report navigation ────────────────────────────────
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSalesReport).setOnClickListener {
            startActivity(SalesReportActivity.newIntent(this))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardPurchaseReport).setOnClickListener {
            startActivity(PurchaseReportActivity.newIntent(this))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardTransactionReport).setOnClickListener {
            startActivity(TransactionReportActivity.newIntent(this))
        }

        btnExport.setOnClickListener {
            if (salesRows.isEmpty() && purchaseRows.isEmpty() &&
                movementRows.isEmpty() && customerBalances.isEmpty()) {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            } else {
                generateAndSharePdf()
            }
        }

        setThisMonth()
    }
    private fun bindViews() {
        btnToday        = findViewById(R.id.btnToday)
        btnThisMonth    = findViewById(R.id.btnThisMonth)
        btnThisYear     = findViewById(R.id.btnThisYear)
        btnCustomRange  = findViewById(R.id.btnCustomRange)
        tvDateRange     = findViewById(R.id.tvDateRange)
        reportContainer = findViewById(R.id.reportContainer)
        emptyState      = findViewById(R.id.emptyState)
        btnExport       = findViewById(R.id.btnExport)
        progressBar     = findViewById(R.id.progressBar)
    }

    // ── Presets ───────────────────────────────────────────────────
    private fun setupPresets() {
        btnToday.setOnClickListener       { setToday() }
        btnThisMonth.setOnClickListener   { setThisMonth() }
        btnThisYear.setOnClickListener    { setThisYear() }
        btnCustomRange.setOnClickListener { showFromDatePicker() }
    }

    private fun setToday() {
        val sdf   = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val tag   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Date()
        fromDate  = sdf.format(today); toDate = fromDate
        fromTag   = tag.format(today); toTag  = fromTag
        tvDateRange.text = "Today — $fromDate"
        highlightPreset(btnToday)
        loadReport()
    }

    private fun setThisMonth() {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val tag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        cal.set(Calendar.DAY_OF_MONTH, 1)
        fromDate = sdf.format(cal.time); fromTag = tag.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        toDate = sdf.format(cal.time); toTag = tag.format(cal.time)
        tvDateRange.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        highlightPreset(btnThisMonth)
        loadReport()
    }

    private fun setThisYear() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        fromDate = "01/01/$year"; toDate = "31/12/$year"
        fromTag  = "$year-01-01"; toTag  = "$year-12-31"
        tvDateRange.text = "Year $year"
        highlightPreset(btnThisYear)
        loadReport()
    }

    private fun showFromDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val tag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            cal.set(y, m, d)
            fromDate = sdf.format(cal.time)
            fromTag  = tag.format(cal.time)
            showToDatePicker()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showToDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val tag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            cal.set(y, m, d)
            toDate = sdf.format(cal.time)
            toTag  = tag.format(cal.time)
            tvDateRange.text = "$fromDate → $toDate"
            highlightPreset(btnCustomRange)
            loadReport()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun highlightPreset(active: MaterialButton) {
        listOf(btnToday, btnThisMonth, btnThisYear, btnCustomRange).forEach {
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (it == active) Color.parseColor("#1A237E")
                else              Color.parseColor("#2A2A3E")
            )
        }
    }

    // ── Load ──────────────────────────────────────────────────────
    private fun loadReport() {
        progressBar.visibility     = View.VISIBLE
        reportContainer.visibility = View.GONE
        emptyState.visibility      = View.GONE
        btnExport.isEnabled        = false

        salesRows.clear(); purchaseRows.clear(); movementRows.clear()
        customerBalances.clear(); supplierBalances.clear()

        var tasksRemaining = 3

        fun checkDone() {
            tasksRemaining--
            if (tasksRemaining == 0) {
                progressBar.visibility     = View.GONE
                reportContainer.visibility = View.VISIBLE
                btnExport.isEnabled        = true
                renderReport()
            }
        }

        // ── Load transactions ─────────────────────────────────────
        db.collection("transactions")
            .whereGreaterThanOrEqualTo("dateTag", fromTag)
            .whereLessThanOrEqualTo("dateTag", toTag)
            .orderBy("dateTag",   Query.Direction.ASCENDING)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val txnDocs      = snapshot.documents
                val salesDocs    = txnDocs.filter { it.getString("txnType") == "sales" }
                val purchaseDocs = txnDocs.filter { it.getString("txnType") == "purchase" }
                val movementDocs = txnDocs.filter {
                    it.getString("txnType") in listOf(
                        "payment_received", "payment_made", "expense"
                    )
                }

                movementDocs.forEach { doc ->
                    movementRows.add(ReportRow(
                        date        = doc.getString("date")      ?: "",
                        billId      = doc.getString("txnId")     ?: "",
                        partyFirm   = doc.getString("partyName") ?: doc.getString("description") ?: "",
                        description = doc.getString("txnType")   ?: "",
                        txnType     = doc.getString("txnType")   ?: "",
                        amount      = doc.getDouble("amount")    ?: 0.0,
                        type        = doc.getString("type")      ?: "debit"
                    ))
                }

                var inner = 2
                fun checkInner() { inner--; if (inner == 0) checkDone() }

                fetchSalesFirmNames(salesDocs)    { checkInner() }
                fetchPurchaseFirmNames(purchaseDocs) { checkInner() }
            }
            .addOnFailureListener { checkDone() }

        // ── Load firm balances ────────────────────────────────────
        db.collection("firm").document("main").get()
            .addOnSuccessListener { doc ->
                firmCash       = doc.getDouble("cash")       ?: 0.0
                firmBank       = doc.getDouble("bank")       ?: 0.0
                firmReceivable = doc.getDouble("receivable") ?: 0.0
                firmPayable    = doc.getDouble("payable")    ?: 0.0
                checkDone()
            }
            .addOnFailureListener { checkDone() }

        // ── Load party balances ───────────────────────────────────
        var partyTasks = 2
        fun checkParty() { partyTasks--; if (partyTasks == 0) checkDone() }

        db.collection("customers").orderBy("name").get()
            .addOnSuccessListener { snap ->
                snap.documents.forEach { doc ->
                    customerBalances.add(PartyBalance(
                        name           = doc.getString("name")     ?: "",
                        firmName       = doc.getString("firmName") ?: "",
                        phone          = doc.getString("phone")    ?: "",
                        closingBalance = doc.getDouble("closingBalance") ?: 0.0
                    ))
                }
                checkParty()
            }
            .addOnFailureListener { checkParty() }

        db.collection("suppliers").orderBy("name").get()
            .addOnSuccessListener { snap ->
                snap.documents.forEach { doc ->
                    supplierBalances.add(PartyBalance(
                        name           = doc.getString("name")     ?: "",
                        firmName       = doc.getString("firmName") ?: "",
                        phone          = doc.getString("phone")    ?: "",
                        closingBalance = doc.getDouble("closingBalance") ?: 0.0
                    ))
                }
                checkParty()
            }
            .addOnFailureListener { checkParty() }
    }

    // ── Fetch firm names ──────────────────────────────────────────
    private fun fetchSalesFirmNames(
        docs:   List<com.google.firebase.firestore.DocumentSnapshot>,
        onDone: () -> Unit
    ) {
        if (docs.isEmpty()) { onDone(); return }
        var pending = docs.size
        docs.forEach { doc ->
            val salesId = doc.getString("salesId") ?: ""
            val date    = doc.getString("date")    ?: ""
            val amount  = doc.getDouble("amount")  ?: 0.0

            if (salesId.isEmpty()) {
                salesRows.add(ReportRow(date, "", "", "Sales", "sales", amount, "debit"))
                if (--pending == 0) onDone(); return@forEach
            }

            db.collection("salesBills").document(salesId).get()
                .addOnSuccessListener { billDoc ->
                    val customerId = billDoc.getString("customerId") ?: ""
                    if (customerId.isEmpty()) {
                        salesRows.add(ReportRow(date, salesId, "", "Sales", "sales", amount, "debit"))
                        if (--pending == 0) onDone(); return@addOnSuccessListener
                    }
                    db.collection("customers").document(customerId).get()
                        .addOnSuccessListener { custDoc ->
                            val firm = custDoc.getString("firmName") ?: custDoc.getString("name") ?: ""
                            salesRows.add(ReportRow(date, salesId, firm, "Sales", "sales", amount, "debit"))
                            if (--pending == 0) onDone()
                        }
                        .addOnFailureListener {
                            salesRows.add(ReportRow(date, salesId, "", "Sales", "sales", amount, "debit"))
                            if (--pending == 0) onDone()
                        }
                }
                .addOnFailureListener {
                    salesRows.add(ReportRow(date, salesId, "", "Sales", "sales", amount, "debit"))
                    if (--pending == 0) onDone()
                }
        }
    }

    private fun fetchPurchaseFirmNames(
        docs:   List<com.google.firebase.firestore.DocumentSnapshot>,
        onDone: () -> Unit
    ) {
        if (docs.isEmpty()) { onDone(); return }
        var pending = docs.size
        docs.forEach { doc ->
            val purchaseId = doc.getString("purchaseId") ?: ""
            val date       = doc.getString("date")       ?: ""
            val amount     = doc.getDouble("amount")     ?: 0.0

            if (purchaseId.isEmpty()) {
                purchaseRows.add(ReportRow(date, "", "", "Purchase", "purchase", amount, "credit"))
                if (--pending == 0) onDone(); return@forEach
            }

            db.collection("purchaseBills").document(purchaseId).get()
                .addOnSuccessListener { billDoc ->
                    val supplierId = billDoc.getString("supplierId") ?: ""
                    if (supplierId.isEmpty()) {
                        purchaseRows.add(ReportRow(date, purchaseId, "", "Purchase", "purchase", amount, "credit"))
                        if (--pending == 0) onDone(); return@addOnSuccessListener
                    }
                    db.collection("suppliers").document(supplierId).get()
                        .addOnSuccessListener { supDoc ->
                            val firm = supDoc.getString("firmName") ?: supDoc.getString("name") ?: ""
                            purchaseRows.add(ReportRow(date, purchaseId, firm, "Purchase", "purchase", amount, "credit"))
                            if (--pending == 0) onDone()
                        }
                        .addOnFailureListener {
                            purchaseRows.add(ReportRow(date, purchaseId, "", "Purchase", "purchase", amount, "credit"))
                            if (--pending == 0) onDone()
                        }
                }
                .addOnFailureListener {
                    purchaseRows.add(ReportRow(date, purchaseId, "", "Purchase", "purchase", amount, "credit"))
                    if (--pending == 0) onDone()
                }
        }
    }

    // ── Render ────────────────────────────────────────────────────
    private fun renderReport() {
        reportContainer.removeAllViews()
        salesRows.sortBy { it.date }
        purchaseRows.sortBy { it.date }
        movementRows.sortBy { it.date }

        // ── Cash & Bank Summary card ──────────────────────────────
        addSectionHeader("Cash & Bank Position", Color.parseColor("#1A237E"))

        val cashBankCard = com.google.android.material.card.MaterialCardView(this).apply {
            radius        = 8f * resources.displayMetrics.density
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#16213E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val cbInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        listOf(
            Triple("Cash in Hand",   firmCash,        Color.parseColor("#A5D6A7")),
            Triple("Bank Balance",   firmBank,        Color.parseColor("#90CAF9")),
            Triple("Receivable",     firmReceivable,  Color.parseColor("#FFE082")),
            Triple("Payable",        firmPayable,     Color.parseColor("#EF9A9A")),
            Triple("Net Cash+Bank",  firmCash + firmBank, Color.parseColor("#FFFFFF"))
        ).forEach { (label, value, color) ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 0, 0, dp(6)) }
            }
            r.addView(TextView(this).apply {
                text     = label
                textSize = 12f
                setTextColor(Color.parseColor("#90A4AE"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            r.addView(TextView(this).apply {
                text     = formatAmount(value)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color)
            })
            cbInner.addView(r)
        }
        cashBankCard.addView(cbInner)
        reportContainer.addView(cashBankCard)

        // ── Sales ─────────────────────────────────────────────────
        if (salesRows.isNotEmpty()) {
            addSectionHeader("Sales Transactions", Color.parseColor("#1B5E20"))
            addTableHeader(listOf("Date", "Bill ID", "Firm Name", "Amount"))
            var totalSales = 0.0
            salesRows.forEach { row ->
                totalSales += row.amount
                addTableRow(
                    cols        = listOf(row.date, row.billId, row.partyFirm, formatAmount(row.amount)),
                    amountColor = Color.parseColor("#A5D6A7"),
                    accent      = Color.parseColor("#1B5E20")
                )
            }
            addSummaryRow("Total Sales", totalSales, Color.parseColor("#A5D6A7"))
        }

        // ── Purchase ──────────────────────────────────────────────
        if (purchaseRows.isNotEmpty()) {
            addSectionHeader("Purchase Transactions", Color.parseColor("#B71C1C"))
            addTableHeader(listOf("Date", "Bill ID", "Firm Name", "Amount"))
            var totalPurchase = 0.0
            purchaseRows.forEach { row ->
                totalPurchase += row.amount
                addTableRow(
                    cols        = listOf(row.date, row.billId, row.partyFirm, formatAmount(row.amount)),
                    amountColor = Color.parseColor("#EF9A9A"),
                    accent      = Color.parseColor("#C62828")
                )
            }
            addSummaryRow("Total Purchase", totalPurchase, Color.parseColor("#EF9A9A"))
        }

        // ── Money Movement ────────────────────────────────────────
        if (movementRows.isNotEmpty()) {
            addSectionHeader("Money Movement", Color.parseColor("#1A237E"))
            addTableHeader(listOf("Date", "Type", "Party / Category", "Amount"))
            var totalIn = 0.0; var totalOut = 0.0
            movementRows.forEach { row ->
                val color = when (row.txnType) {
                    "payment_received" -> Color.parseColor("#A5D6A7")
                    "payment_made"     -> Color.parseColor("#EF9A9A")
                    else               -> Color.parseColor("#FFCC80")
                }
                val label = when (row.txnType) {
                    "payment_received" -> "Received"
                    "payment_made"     -> "Paid"
                    else               -> "Expense"
                }
                if (row.type == "credit") totalIn  += row.amount
                else                      totalOut += row.amount
                addTableRow(listOf(row.date, label, row.partyFirm, formatAmount(row.amount)), color, color)
            }
            addMovementSummary(totalIn, totalOut)
        }

        // ── Customer Balances ─────────────────────────────────────
        if (customerBalances.isNotEmpty()) {
            addSectionHeader("Customer Balances", Color.parseColor("#1A237E"))
            addPartyHeader()
            var totalReceivable = 0.0
            customerBalances.forEach { p ->
                totalReceivable += p.closingBalance
                addPartyRow(p.firmName.ifEmpty { p.name }, p.name, p.phone, p.closingBalance, true)
            }
            addSummaryRow("Total Receivable", totalReceivable, Color.parseColor("#FFE082"))
        }

        // ── Supplier Balances ─────────────────────────────────────
        if (supplierBalances.isNotEmpty()) {
            addSectionHeader("Supplier Balances", Color.parseColor("#4A148C"))
            addPartyHeader()
            var totalPayable = 0.0
            supplierBalances.forEach { p ->
                totalPayable += p.closingBalance
                addPartyRow(p.firmName.ifEmpty { p.name }, p.name, p.phone, p.closingBalance, false)
            }
            addSummaryRow("Total Payable", totalPayable, Color.parseColor("#EF9A9A"))
        }
    }

    // ── UI builders ───────────────────────────────────────────────
    private fun addSectionHeader(title: String, color: Int) {
        reportContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10))
        })

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setBackgroundColor(color)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        header.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT)
                .also { it.marginEnd = dp(10) }
            setBackgroundColor(Color.WHITE)
        })

        header.addView(TextView(this).apply {
            text     = title.uppercase()
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        reportContainer.addView(header)
    }

    private fun addTableHeader(cols: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0D1B2A"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val weights = listOf(1.1f, 1.5f, 2f, 1.1f)
        cols.forEachIndexed { i, col ->
            row.addView(TextView(this).apply {
                text     = col
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#90CAF9"))
                gravity  = if (i == cols.size - 1) Gravity.END else Gravity.START
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weights[i])
            })
        }
        reportContainer.addView(row)
    }

    private fun addTableRow(cols: List<String>, amountColor: Int, accent: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        val weights = listOf(1.1f, 1.5f, 2f, 1.1f)
        cols.forEachIndexed { i, col ->
            val isLast = i == cols.size - 1
            row.addView(TextView(this).apply {
                text      = col
                textSize  = 11f
                setTextColor(if (isLast) amountColor else Color.parseColor("#CCCCCC"))
                typeface  = if (isLast) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity   = if (isLast) Gravity.END else Gravity.START
                maxLines  = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weights[i])
            })
        }
        reportContainer.addView(row)
        reportContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#1E2D3E"))
        })
    }

    private fun addPartyHeader() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0D1B2A"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        listOf(Triple("Firm Name", 2f, Gravity.START),
            Triple("Contact",  1.5f, Gravity.START),
            Triple("Balance",  1f, Gravity.END)).forEach { (col, w, g) ->
            row.addView(TextView(this).apply {
                text     = col
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#90CAF9"))
                gravity  = g
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
            })
        }
        reportContainer.addView(row)
    }

    private fun addPartyRow(
        firmName: String,
        name:     String,
        phone:    String,
        balance:  Double,
        isCustomer: Boolean
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }

        row.addView(LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            addView(TextView(context).apply {
                text     = firmName.ifEmpty { name }
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#FFFFFF"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (firmName.isNotEmpty() && firmName != name) {
                addView(TextView(context).apply {
                    text     = name
                    textSize = 10f
                    setTextColor(Color.parseColor("#90A4AE"))
                })
            }
        })

        row.addView(TextView(this).apply {
            text     = phone
            textSize = 11f
            setTextColor(Color.parseColor("#CCCCCC"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        })

        row.addView(TextView(this).apply {
            text     = formatAmount(balance)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                if (isCustomer) Color.parseColor("#FFE082")
                else            Color.parseColor("#EF9A9A")
            )
            gravity  = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        reportContainer.addView(row)
        reportContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#1E2D3E"))
        })
    }

    private fun addSummaryRow(label: String, amount: Double, color: Int) {
        val row = LinearLayout(this).apply {
            orientation      = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0D1B2A"))
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        row.addView(TextView(this).apply {
            text     = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text     = formatAmount(amount)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
        })
        reportContainer.addView(row)
    }

    private fun addMovementSummary(totalIn: Double, totalOut: Double) {
        val net = totalIn - totalOut
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1B2A"))
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        listOf(
            Triple("Money In",  totalIn,  Color.parseColor("#A5D6A7")),
            Triple("Money Out", totalOut, Color.parseColor("#EF9A9A")),
            Triple("Net Flow",  net, if (net >= 0) Color.parseColor("#A5D6A7") else Color.parseColor("#EF9A9A"))
        ).forEach { (label, value, color) ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 0, 0, dp(4)) }
            }
            r.addView(TextView(this).apply {
                text     = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            r.addView(TextView(this).apply {
                text     = formatAmount(value)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color)
            })
            card.addView(r)
        }
        reportContainer.addView(card)
    }

    // ── PDF ───────────────────────────────────────────────────────
    private fun generateAndSharePdf() {
        val file = buildPdf()
        val uri  = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Report — ${tvDateRange.text}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Report PDF"))
    }

    private fun buildPdf(): File {
        val pdf        = PdfDocument()
        val paint      = Paint()
        val paintR     = Paint().also { it.textAlign = Paint.Align.RIGHT }
        val paintC     = Paint().also { it.textAlign = Paint.Align.CENTER }
        val pageWidth  = 595
        val pageHeight = 842
        val margin     = 36f

        var pageNum  = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page     = pdf.startPage(pageInfo)
        var canvas   = page.canvas
        var y        = margin

        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        fun newPage() {
            pdf.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page     = pdf.startPage(pageInfo)
            canvas   = page.canvas
            y        = margin
        }

        fun checkBreak(needed: Float = 32f) {
            if (y > pageHeight - needed) newPage()
        }

        // ── Cover header ──────────────────────────────────────────
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, paint)

        // accent strip
        paint.color = Color.parseColor("#3949AB")
        canvas.drawRect(0f, 100f, pageWidth.toFloat(), 104f, paint)

        paint.color          = Color.WHITE
        paint.textSize       = 26f
        paint.isFakeBoldText = true
        canvas.drawText("Business Report", margin, 42f, paint)

        paint.textSize       = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Period: ${tvDateRange.text}", margin, 64f, paint)
        canvas.drawText("Generated: $dateStr", margin, 82f, paint)

        paintR.color    = Color.parseColor("#90CAF9")
        paintR.textSize = 11f
        canvas.drawText("CashBook", pageWidth - margin, 64f, paintR)

        y = 120f

        // ── Column positions ──────────────────────────────────────
        val c1 = margin + 4f
        val c2 = margin + 85f
        val c3 = margin + 220f
        val c4 = pageWidth - margin

        fun drawSectionBand(title: String, bandColor: Int) {
            checkBreak(44f)
            y += 12f
            paint.color = bandColor
            canvas.drawRect(margin - 4f, y, pageWidth - margin + 4f, y + 26f, paint)
            paint.color          = Color.WHITE
            paint.textSize       = 11f
            paint.isFakeBoldText = true
            canvas.drawText(title.uppercase(), c1, y + 17f, paint)
            y += 32f
        }

        fun drawColumnHeaders(cols: List<Pair<Float, String>>) {
            checkBreak(28f)
            paint.color = Color.parseColor("#0D1B2A")
            canvas.drawRect(margin - 4f, y - 4f, pageWidth - margin + 4f, y + 18f, paint)
            paint.color          = Color.parseColor("#90CAF9")
            paint.textSize       = 9f
            paint.isFakeBoldText = true
            cols.forEach { (x, label) ->
                if (x == c4) {
                    paintR.color          = Color.parseColor("#90CAF9")
                    paintR.textSize       = 9f
                    paintR.isFakeBoldText = true
                    canvas.drawText(label, c4, y + 11f, paintR)
                } else {
                    canvas.drawText(label, x, y + 11f, paint)
                }
            }
            y += 24f
        }

        fun drawRow(
            col1: String, col2: String, col3: String, col4: String,
            col4Color: Int, rowIndex: Int
        ) {
            checkBreak(26f)
            if (rowIndex % 2 == 0) {
                paint.color = Color.parseColor("#F7F9FF")
                canvas.drawRect(margin - 4f, y - 4f, pageWidth - margin + 4f, y + 16f, paint)
            }
            paint.color          = Color.parseColor("#333333")
            paint.textSize       = 9f
            paint.isFakeBoldText = false
            canvas.drawText(col1, c1, y + 10f, paint)
            canvas.drawText(col2, c2, y + 10f, paint)
            canvas.drawText(col3, c3, y + 10f, paint)
            paintR.color          = col4Color
            paintR.textSize       = 9f
            paintR.isFakeBoldText = true
            canvas.drawText(col4, c4, y + 10f, paintR)
            paint.color       = Color.parseColor("#E0E0E0")
            paint.strokeWidth = 0.5f
            canvas.drawLine(margin - 4f, y + 17f, pageWidth - margin + 4f, y + 17f, paint)
            y += 22f
        }

        fun drawTotalBand(label: String, value: Double, color: Int) {
            checkBreak(30f)
            paint.color = Color.parseColor("#16213E")
            canvas.drawRect(margin - 4f, y, pageWidth - margin + 4f, y + 24f, paint)
            paint.color          = Color.WHITE
            paint.textSize       = 10f
            paint.isFakeBoldText = true
            canvas.drawText(label, c1, y + 16f, paint)
            paintR.color          = color
            paintR.textSize       = 11f
            paintR.isFakeBoldText = true
            canvas.drawText(formatAmount(value), c4, y + 16f, paintR)
            y += 28f
        }

        // ── Cash & Bank section ───────────────────────────────────
        drawSectionBand("Cash & Bank Position", Color.parseColor("#1A237E"))

        listOf(
            Triple("Cash in Hand",    firmCash,              Color.parseColor("#2E7D32")),
            Triple("Bank Balance",    firmBank,              Color.parseColor("#1565C0")),
            Triple("Total Receivable", firmReceivable,       Color.parseColor("#E65100")),
            Triple("Total Payable",   firmPayable,           Color.parseColor("#B71C1C")),
            Triple("Net (Cash+Bank)", firmCash + firmBank,   Color.parseColor("#1A237E"))
        ).forEachIndexed { i, (label, value, color) ->
            checkBreak(26f)
            if (i % 2 == 0) {
                paint.color = Color.parseColor("#F5F5F5")
                canvas.drawRect(margin - 4f, y - 4f, pageWidth - margin + 4f, y + 18f, paint)
            }
            paint.color          = Color.parseColor("#333333")
            paint.textSize       = 10f
            paint.isFakeBoldText = false
            canvas.drawText(label, c1, y + 12f, paint)
            paintR.color          = color
            paintR.textSize       = 11f
            paintR.isFakeBoldText = true
            canvas.drawText(formatAmount(value), c4, y + 12f, paintR)
            y += 22f
        }

        // ── Sales section ─────────────────────────────────────────
        if (salesRows.isNotEmpty()) {
            drawSectionBand("Sales Transactions", Color.parseColor("#1B5E20"))
            drawColumnHeaders(listOf(c1 to "Date", c2 to "Bill ID", c3 to "Firm Name", c4 to "Amount"))
            var totalSales = 0.0
            salesRows.sortedBy { it.date }.forEachIndexed { i, row ->
                totalSales += row.amount
                drawRow(row.date, row.billId, row.partyFirm, formatAmount(row.amount),
                    Color.parseColor("#2E7D32"), i)
            }
            drawTotalBand("Total Sales", totalSales, Color.parseColor("#2E7D32"))
        }

        // ── Purchase section ──────────────────────────────────────
        if (purchaseRows.isNotEmpty()) {
            drawSectionBand("Purchase Transactions", Color.parseColor("#B71C1C"))
            drawColumnHeaders(listOf(c1 to "Date", c2 to "Bill ID", c3 to "Firm Name", c4 to "Amount"))
            var totalPurchase = 0.0
            purchaseRows.sortedBy { it.date }.forEachIndexed { i, row ->
                totalPurchase += row.amount
                drawRow(row.date, row.billId, row.partyFirm, formatAmount(row.amount),
                    Color.parseColor("#C62828"), i)
            }
            drawTotalBand("Total Purchase", totalPurchase, Color.parseColor("#C62828"))
        }

        // ── Money Movement section ────────────────────────────────
        if (movementRows.isNotEmpty()) {
            drawSectionBand("Money Movement", Color.parseColor("#1A237E"))
            drawColumnHeaders(listOf(c1 to "Date", c2 to "Type", c3 to "Party / Category", c4 to "Amount"))
            var totalIn = 0.0; var totalOut = 0.0
            movementRows.sortedBy { it.date }.forEachIndexed { i, row ->
                val label = when (row.txnType) {
                    "payment_received" -> "Received"
                    "payment_made"     -> "Paid"
                    else               -> "Expense"
                }
                val color = when (row.txnType) {
                    "payment_received" -> Color.parseColor("#2E7D32")
                    "payment_made"     -> Color.parseColor("#C62828")
                    else               -> Color.parseColor("#E65100")
                }
                if (row.type == "credit") totalIn  += row.amount
                else                      totalOut += row.amount
                drawRow(row.date, label, row.partyFirm, formatAmount(row.amount), color, i)
            }
            checkBreak(80f)
            y += 4f
            listOf(
                Triple("Money In",  totalIn,  Color.parseColor("#2E7D32")),
                Triple("Money Out", totalOut, Color.parseColor("#C62828")),
                Triple("Net Flow",  totalIn - totalOut,
                    if (totalIn >= totalOut) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
            ).forEach { (label, value, color) ->
                drawTotalBand(label, value, color)
            }
        }

        // ── Customer Balances ─────────────────────────────────────
        if (customerBalances.isNotEmpty()) {
            drawSectionBand("Customer Balances", Color.parseColor("#1A237E"))
            drawColumnHeaders(listOf(c1 to "Firm Name", c2 to "Name", c3 to "Phone", c4 to "Balance"))
            var totalReceivable = 0.0
            customerBalances.forEachIndexed { i, p ->
                totalReceivable += p.closingBalance
                drawRow(p.firmName.ifEmpty { p.name }, p.name, p.phone,
                    formatAmount(p.closingBalance), Color.parseColor("#E65100"), i)
            }
            drawTotalBand("Total Receivable", totalReceivable, Color.parseColor("#E65100"))
        }

        // ── Supplier Balances ─────────────────────────────────────
        if (supplierBalances.isNotEmpty()) {
            drawSectionBand("Supplier Balances", Color.parseColor("#4A148C"))
            drawColumnHeaders(listOf(c1 to "Firm Name", c2 to "Name", c3 to "Phone", c4 to "Balance"))
            var totalPayable = 0.0
            supplierBalances.forEachIndexed { i, p ->
                totalPayable += p.closingBalance
                drawRow(p.firmName.ifEmpty { p.name }, p.name, p.phone,
                    formatAmount(p.closingBalance), Color.parseColor("#C62828"), i)
            }
            drawTotalBand("Total Payable", totalPayable, Color.parseColor("#C62828"))
        }

        // ── Footer ────────────────────────────────────────────────
        checkBreak(30f)
        y += 16f
        paint.color = Color.parseColor("#EEEEEE")
        canvas.drawRect(0f, y, pageWidth.toFloat(), y + 1f, paint)
        y += 8f
        paintC.color    = Color.parseColor("#9E9E9E")
        paintC.textSize = 8f
        canvas.drawText(
            "Generated by CashBook — $dateStr  |  Period: ${tvDateRange.text}",
            pageWidth / 2f, y + 10f, paintC
        )

        pdf.finishPage(page)

        val file = File(cacheDir, "report_${System.currentTimeMillis()}.pdf")
        pdf.writeTo(FileOutputStream(file))
        pdf.close()
        return file
    }

    private fun formatAmount(amount: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return "₹${format.format(amount)}"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun newIntent(context: Context) = Intent(context, ReportsActivity::class.java)
    }
}