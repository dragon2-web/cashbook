package com.example.cashbook.ledger.customers

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.cashbook.R
import com.example.cashbook.ledger.TransactionDetailActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.*

class CustomerLedgerActivity : AppCompatActivity() {

    private lateinit var transactionContainer: LinearLayout
    private lateinit var emptyState:           LinearLayout
    private lateinit var tvCustomerName:       TextView
    private lateinit var tvCustomerAddress:    TextView
    private lateinit var tvCustomerInitial:    TextView
    private lateinit var tvClosingBalance:     TextView

    private var customerUid    = ""
    private var closingBalance = 0.0

    private var customerName     = ""
    private var customerFirmName = ""
    private var customerPhone    = ""
    private var customerAddress  = ""
    private var customerGstin    = ""

    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_ledger)

        bindViews()
        setupToolbar()

        customerUid = intent.getStringExtra(EXTRA_CUSTOMER_UID) ?: run { finish(); return }

        loadCustomerDetails()
        loadTransactions()

        findViewById<ImageButton>(R.id.btnShare).setOnClickListener {
            generateAndSharePdf()
        }
    }

    private fun bindViews() {
        transactionContainer = findViewById(R.id.transactionContainer)
        emptyState           = findViewById(R.id.emptyState)
        tvCustomerName       = findViewById(R.id.customerName)
        tvCustomerAddress    = findViewById(R.id.customerAddress)
        tvCustomerInitial    = findViewById(R.id.customerInitial)
        tvClosingBalance     = findViewById(R.id.closingBalance)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun loadCustomerDetails() {
        db.collection("customers")
            .document(customerUid)
            .get()
            .addOnSuccessListener { doc ->
                customerName     = doc.getString("name")     ?: ""
                customerFirmName = doc.getString("firmName") ?: ""
                customerPhone    = doc.getString("phone")    ?: ""
                customerAddress  = doc.getString("address")  ?: ""
                customerGstin    = doc.getString("gstin")    ?: ""
                closingBalance   = doc.getDouble("closingBalance") ?: 0.0

                tvCustomerName.text    = customerName
                tvCustomerAddress.text = customerAddress
                tvCustomerInitial.text = customerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

                tvClosingBalance.text = formatAmount(closingBalance)
                tvClosingBalance.setTextColor(
                    if (closingBalance >= 0) Color.parseColor("#C62828")
                    else Color.parseColor("#1B5E20")
                )
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load customer", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadTransactions() {
        db.collection("customers")
            .document(customerUid)
            .collection("transactions")
            .orderBy("date", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    emptyState.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                var totalDebit  = 0.0
                var totalCredit = 0.0

                result.forEachIndexed { index, doc ->
                    val txnId       = doc.id
                    val date        = doc.getString("date")        ?: "—"
                    val description = doc.getString("description") ?: "—"
                    val type        = doc.getString("type")        ?: "debit"
                    val amount      = doc.getDouble("amount")      ?: 0.0

                    val debit  = if (type == "debit")  amount else 0.0
                    val credit = if (type == "credit") amount else 0.0

                    totalDebit  += debit
                    totalCredit += credit

                    addTransactionRow(txnId, date, description, debit, credit, index % 2 == 0)
                }

                addTotalsRow(totalDebit, totalCredit)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load transactions", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addTransactionRow(
        txnId:       String,
        date:        String,
        description: String,
        debit:       Double,
        credit:      Double,
        isEvenRow:   Boolean
    ) {
        val isDebit = debit > 0
        val amount  = if (isDebit) debit else credit

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setBackgroundColor(if (isEvenRow) Color.WHITE else Color.parseColor("#F9FAFE"))
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { openTransactionDetail(txnId) }
        }

        container.addView(TextView(this).apply {
            text     = date
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(10) }
        })

        container.addView(TextView(this).apply {
            text      = description
            textSize  = 13f
            setTextColor(Color.parseColor("#212121"))
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        container.addView(TextView(this).apply {
            text     = if (isDebit) formatAmount(amount) else ""
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#C62828"))
            gravity  = Gravity.START
            layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        container.addView(TextView(this).apply {
            text     = if (!isDebit) formatAmount(amount) else ""
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1B5E20"))
            gravity  = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        transactionContainer.addView(container)
        transactionContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        })
    }

    private fun addTotalsRow(totalDebit: Double, totalCredit: Double) {
        val computedClosing = totalDebit - totalCredit

        transactionContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2))
            setBackgroundColor(Color.parseColor("#1A237E"))
        })

        val totalsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setBackgroundColor(Color.parseColor("#E8EAF6"))
            gravity = Gravity.CENTER_VERTICAL
        }

        totalsRow.addView(TextView(this).apply {
            text     = "Total"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A237E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(10) }
        })

        totalsRow.addView(TextView(this).apply {
            text         = ""
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        totalsRow.addView(TextView(this).apply {
            text     = formatAmount(totalDebit)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#C62828"))
            gravity  = Gravity.START
            layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        totalsRow.addView(TextView(this).apply {
            text     = formatAmount(totalCredit)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1B5E20"))
            gravity  = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        transactionContainer.addView(totalsRow)

        transactionContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#C5CAE9"))
        })

        val closingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#E8EAF6"))
            gravity = Gravity.CENTER_VERTICAL
        }

        closingRow.addView(TextView(this).apply {
            text     = "Closing Balance"
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1A237E"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        closingRow.addView(TextView(this).apply {
            text     = formatAmount(computedClosing)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(
                if (computedClosing >= 0) Color.parseColor("#C62828")
                else Color.parseColor("#1B5E20")
            )
        })

        transactionContainer.addView(closingRow)
    }

    // ── PDF ───────────────────────────────────────────────────────
    private fun generateAndSharePdf() {
        db.collection("customers")
            .document(customerUid)
            .collection("transactions")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val file = buildPdf(snapshot.documents)
                sharePdf(file)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load transactions", Toast.LENGTH_SHORT).show()
            }
    }

    private fun buildPdf(docs: List<com.google.firebase.firestore.DocumentSnapshot>): File {
        val pdf        = PdfDocument()
        val paint      = Paint()
        val paintR     = Paint().also { it.textAlign = Paint.Align.RIGHT }
        val pageWidth  = 595
        val pageHeight = 842
        val margin     = 40f

        var pageNum  = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page     = pdf.startPage(pageInfo)
        var canvas   = page.canvas
        var y        = margin

        val dateStr = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        // ── Header ────────────────────────────────────────────────
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 90f, paint)

        paint.color          = Color.WHITE
        paint.textSize       = 22f
        paint.isFakeBoldText = true
        canvas.drawText("Customer Ledger", margin, 38f, paint)

        paint.textSize       = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Generated: $dateStr", margin, 62f, paint)
        paintR.color         = Color.WHITE
        paintR.textSize      = 11f
        canvas.drawText("CashBook", pageWidth - margin, 62f, paintR)

        y = 110f

        // ── Customer details card ─────────────────────────────────
        paint.color = Color.parseColor("#F5F5F5")
        canvas.drawRoundRect(android.graphics.RectF(margin, y, pageWidth - margin, y + 110f), 8f, 8f, paint)
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(margin, y, margin + 5f, y + 110f, paint)

        paint.color          = Color.parseColor("#1A237E")
        paint.textSize       = 15f
        paint.isFakeBoldText = true
        canvas.drawText(customerName, margin + 16f, y + 26f, paint)

        val details = listOf(
            "Firm"    to customerFirmName,
            "Phone"   to customerPhone,
            "Address" to customerAddress,
            "GSTIN"   to customerGstin
        )
        var detailY = y + 46f
        details.forEach { (label, value) ->
            if (value.isNotEmpty()) {
                paint.color          = Color.parseColor("#757575")
                paint.textSize       = 11f
                paint.isFakeBoldText = false
                canvas.drawText("$label:", margin + 16f, detailY, paint)
                paint.color          = Color.parseColor("#212121")
                paint.isFakeBoldText = true
                canvas.drawText(value, margin + 70f, detailY, paint)
                detailY += 18f
            }
        }
        y += 126f

        // ── Column positions ──────────────────────────────────────
        val col1 = margin + 6f       // Date
        val col2 = margin + 90f      // Description
        val col3 = margin + 260f     // Bill ID
        val col4 = pageWidth - margin - 65f   // Debit right edge
        val col5 = pageWidth - margin         // Credit right edge

        // ── Table header ──────────────────────────────────────────
        fun drawHeader(cvs: android.graphics.Canvas, yPos: Float) {
            paint.color = Color.parseColor("#1A237E")
            cvs.drawRect(margin, yPos, pageWidth - margin, yPos + 28f, paint)

            paint.color          = Color.WHITE
            paint.textSize       = 10f
            paint.isFakeBoldText = true
            cvs.drawText("Date",        col1, yPos + 18f, paint)
            cvs.drawText("Description", col2, yPos + 18f, paint)
            cvs.drawText("Bill ID",     col3, yPos + 18f, paint)

            paintR.color         = Color.parseColor("#FFCDD2")
            paintR.textSize      = 10f
            paintR.isFakeBoldText = true
            cvs.drawText("Debit",  col4, yPos + 18f, paintR)

            paintR.color         = Color.parseColor("#C8E6C9")
            cvs.drawText("Credit", col5, yPos + 18f, paintR)
        }

        drawHeader(canvas, y)
        y += 34f

        // ── Rows ──────────────────────────────────────────────────
        var totalDebit  = 0.0
        var totalCredit = 0.0
        var rowIndex    = 0

        for (doc in docs) {
            val amount      = doc.getDouble("amount")      ?: 0.0
            val type        = doc.getString("type")        ?: ""
            val description = doc.getString("description") ?: ""
            val date        = doc.getString("date")        ?: ""
            val billId      = doc.getString("salesId")     ?: doc.getString("purchaseId") ?: ""

            if (type == "debit") totalDebit  += amount
            else                 totalCredit += amount

            if (rowIndex % 2 == 0) {
                paint.color = Color.parseColor("#F9F9FF")
                canvas.drawRect(margin, y - 4f, pageWidth - margin, y + 18f, paint)
            }

            paint.color          = Color.parseColor("#424242")
            paint.textSize       = 9.5f
            paint.isFakeBoldText = false
            canvas.drawText(date,        col1, y + 11f, paint)
            canvas.drawText(description, col2, y + 11f, paint)
            canvas.drawText(billId,      col3, y + 11f, paint)

            paintR.isFakeBoldText = false
            paintR.textSize       = 9.5f

            if (type == "debit") {
                paintR.color = Color.parseColor("#C62828")
                canvas.drawText(formatAmount(amount), col4, y + 11f, paintR)
                paintR.color = Color.parseColor("#9E9E9E")
                canvas.drawText("—", col5, y + 11f, paintR)
            } else {
                paintR.color = Color.parseColor("#9E9E9E")
                canvas.drawText("—", col4, y + 11f, paintR)
                paintR.color = Color.parseColor("#1B5E20")
                canvas.drawText(formatAmount(amount), col5, y + 11f, paintR)
            }

            paint.color       = Color.parseColor("#E8EAF6")
            paint.strokeWidth = 0.5f
            canvas.drawLine(margin, y + 19f, pageWidth - margin, y + 19f, paint)

            y        += 26f
            rowIndex ++

            if (y > pageHeight - 120f) {
                pdf.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page     = pdf.startPage(pageInfo)
                canvas   = page.canvas
                y        = margin
                drawHeader(canvas, y)
                y += 34f
            }
        }

        // ── Summary ───────────────────────────────────────────────
        y += 8f

        paint.color = Color.parseColor("#FFEBEE")
        canvas.drawRect(margin, y, pageWidth - margin, y + 26f, paint)
        paint.color          = Color.parseColor("#C62828")
        paint.textSize       = 10f
        paint.isFakeBoldText = true
        canvas.drawText("Total Debit", col1, y + 17f, paint)
        paintR.color         = Color.parseColor("#C62828")
        paintR.textSize      = 10f
        paintR.isFakeBoldText = true
        canvas.drawText(formatAmount(totalDebit), col4, y + 17f, paintR)
        y += 28f

        paint.color = Color.parseColor("#E8F5E9")
        canvas.drawRect(margin, y, pageWidth - margin, y + 26f, paint)
        paint.color          = Color.parseColor("#1B5E20")
        canvas.drawText("Total Credit", col1, y + 17f, paint)
        paintR.color         = Color.parseColor("#1B5E20")
        canvas.drawText(formatAmount(totalCredit), col5, y + 17f, paintR)
        y += 28f

        val closingBal = totalDebit - totalCredit
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(margin, y, pageWidth - margin, y + 32f, paint)
        paint.color          = Color.WHITE
        paint.textSize       = 12f
        canvas.drawText("Closing Balance", col1, y + 21f, paint)
        paintR.color         = Color.WHITE
        paintR.textSize      = 12f
        canvas.drawText(formatAmount(Math.abs(closingBal)), col5, y + 21f, paintR)

        // ── Footer ────────────────────────────────────────────────
        paint.color          = Color.parseColor("#9E9E9E")
        paint.textSize       = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Generated by CashBook — $dateStr", margin, pageHeight - 20f, paint)

        pdf.finishPage(page)

        val file = File(cacheDir, "ledger_${customerName.replace(" ", "_")}.pdf")
        pdf.writeTo(FileOutputStream(file))
        pdf.close()
        return file
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ledger — $customerName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Ledger PDF"))
    }

    private fun openTransactionDetail(txnId: String) {
        startActivity(TransactionDetailActivity.newIntent(this, txnId))
    }

    private fun formatAmount(amount: Double): String {
        if (amount == 0.0) return "—"
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return format.format(amount)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_CUSTOMER_UID = "CUSTOMER_UID"
        fun newIntent(context: Context, customerUid: String) =
            Intent(context, CustomerLedgerActivity::class.java).apply {
                putExtra(EXTRA_CUSTOMER_UID, customerUid)
            }
    }
}