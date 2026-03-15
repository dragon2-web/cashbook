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

class TransactionReportActivity : AppCompatActivity() {

    private lateinit var btnToday:        MaterialButton
    private lateinit var btnThisMonth:    MaterialButton
    private lateinit var btnThisYear:     MaterialButton
    private lateinit var btnCustomRange:  MaterialButton
    private lateinit var tvDateRange:     TextView
    private lateinit var reportContainer: LinearLayout
    private lateinit var emptyState:      TextView
    private lateinit var btnExport:       MaterialButton
    private lateinit var progressBar:     ProgressBar
    private lateinit var tvTotalIn:       TextView
    private lateinit var tvTotalOut:      TextView

    private val db by lazy { FirebaseFirestore.getInstance() }

    private var fromTag = ""
    private var toTag   = ""

    data class TxnRow(
        val txnId:       String,
        val date:        String,
        val txnType:     String,
        val description: String,
        val partyName:   String,
        val mode:        String,
        val amount:      Double,
        val type:        String
    )

    private val rows = mutableListOf<TxnRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_report)

        bindViews()
        setupPresets()

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnExport.setOnClickListener {
            if (rows.isEmpty()) Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            else generateAndSharePdf()
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
        tvTotalIn       = findViewById(R.id.tvTotalIn)
        tvTotalOut      = findViewById(R.id.tvTotalOut)
    }

    private fun setupPresets() {
        btnToday.setOnClickListener       { setToday() }
        btnThisMonth.setOnClickListener   { setThisMonth() }
        btnThisYear.setOnClickListener    { setThisYear() }
        btnCustomRange.setOnClickListener { showFromDatePicker() }
    }

    private fun setToday() {
        val tag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()); val today = Date()
        fromTag = tag.format(today); toTag = fromTag
        tvDateRange.text = "Today — ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(today)}"
        highlightPreset(btnToday); loadReport()
    }

    private fun setThisMonth() {
        val cal = Calendar.getInstance(); val tag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        cal.set(Calendar.DAY_OF_MONTH, 1); fromTag = tag.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); toTag = tag.format(cal.time)
        tvDateRange.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        highlightPreset(btnThisMonth); loadReport()
    }

    private fun setThisYear() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        fromTag = "$year-01-01"; toTag = "$year-12-31"
        tvDateRange.text = "Year $year"; highlightPreset(btnThisYear); loadReport()
    }

    private fun showFromDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val tag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            cal.set(y, m, d); fromTag = tag.format(cal.time)
            showToDatePicker(sdf.format(cal.time))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showToDatePicker(fromDisplay: String) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val tag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            cal.set(y, m, d); toTag = tag.format(cal.time)
            tvDateRange.text = "$fromDisplay → ${sdf.format(cal.time)}"
            highlightPreset(btnCustomRange); loadReport()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun highlightPreset(active: MaterialButton) {
        listOf(btnToday, btnThisMonth, btnThisYear, btnCustomRange).forEach {
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (it == active) Color.parseColor("#1A237E") else Color.parseColor("#2A2A3E")
            )
        }
    }

    // ── Load — reads ALL txnTypes from transactions collection ────
    private fun loadReport() {
        progressBar.visibility     = View.VISIBLE
        reportContainer.visibility = View.GONE
        emptyState.visibility      = View.GONE
        btnExport.isEnabled        = false
        rows.clear()

        db.collection("transactions")
            .whereGreaterThanOrEqualTo("dateTag", fromTag)
            .whereLessThanOrEqualTo("dateTag", toTag)
            .whereIn("txnType", listOf("payment_received", "payment_made", "expense"))
            .orderBy("dateTag",   Query.Direction.ASCENDING)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE

                if (snapshot.isEmpty) {
                    emptyState.visibility = View.VISIBLE
                    tvTotalIn.text        = formatAmount(0.0)
                    tvTotalOut.text       = formatAmount(0.0)
                    return@addOnSuccessListener
                }

                snapshot.documents.forEach { doc ->
                    rows.add(TxnRow(
                        txnId       = doc.getString("txnId")       ?: doc.id,
                        date        = doc.getString("date")        ?: "",
                        txnType     = doc.getString("txnType")     ?: "",
                        description = doc.getString("description") ?: "",
                        partyName   = doc.getString("partyName")   ?: "",
                        mode        = doc.getString("mode")        ?: "",
                        amount      = doc.getDouble("amount")      ?: 0.0,
                        type        = doc.getString("type")        ?: "debit"
                    ))
                }

                val totalIn  = rows.filter { it.type == "credit" }.sumOf { it.amount }
                val totalOut = rows.filter { it.type == "debit"  }.sumOf { it.amount }
                tvTotalIn.text  = formatAmount(totalIn)
                tvTotalOut.text = formatAmount(totalOut)

                reportContainer.visibility = View.VISIBLE
                btnExport.isEnabled        = true
                renderReport()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderReport() {
        reportContainer.removeAllViews()

        val received = rows.filter { it.txnType == "payment_received" }
        val made     = rows.filter { it.txnType == "payment_made" }
        val expenses = rows.filter { it.txnType == "expense" }

        if (received.isNotEmpty()) {
            addSectionHeader("Payment Received", Color.parseColor("#1B5E20"))
            addTxnTableHeader()
            received.forEachIndexed { i, row -> addTxnRow(row, i) }
            addSummaryRow("Total Received", formatAmount(received.sumOf { it.amount }), Color.parseColor("#A5D6A7"))
        }

        if (made.isNotEmpty()) {
            addSectionHeader("Payment Made", Color.parseColor("#C62828"))
            addTxnTableHeader()
            made.forEachIndexed { i, row -> addTxnRow(row, i) }
            addSummaryRow("Total Paid", formatAmount(made.sumOf { it.amount }), Color.parseColor("#EF9A9A"))
        }

        if (expenses.isNotEmpty()) {
            addSectionHeader("Expenses", Color.parseColor("#E65100"))
            addTxnTableHeader()
            expenses.forEachIndexed { i, row -> addTxnRow(row, i) }
            addSummaryRow("Total Expenses", formatAmount(expenses.sumOf { it.amount }), Color.parseColor("#FFCC80"))
        }

        val totalIn  = rows.filter { it.type == "credit" }.sumOf { it.amount }
        val totalOut = rows.filter { it.type == "debit"  }.sumOf { it.amount }
        val net      = totalIn - totalOut

        addDivider(Color.parseColor("#1A237E"), 2)
        addSummaryRow("Total In",  formatAmount(totalIn),  Color.parseColor("#A5D6A7"))
        addSummaryRow("Total Out", formatAmount(totalOut), Color.parseColor("#EF9A9A"))
        addSummaryRow("Net Flow",  formatAmount(net),
            if (net >= 0) Color.parseColor("#A5D6A7") else Color.parseColor("#EF9A9A"))
    }

    private fun addSectionHeader(title: String, color: Int) {
        reportContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
        })
        reportContainer.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(color)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(TextView(context).apply {
                text = title.uppercase(); textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE); letterSpacing = 0.08f
            })
        })
    }

    private fun addTxnTableHeader() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0D1B2A"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        listOf(
            Triple("Date",   1.2f, Gravity.START),
            Triple("Txn ID", 1.8f, Gravity.START),
            Triple("Party",  2f,   Gravity.START),
            Triple("Mode",   0.8f, Gravity.CENTER),
            Triple("Amount", 1.2f, Gravity.END)
        ).forEach { (col, w, g) ->
            row.addView(TextView(this).apply {
                text = col; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#90CAF9")); gravity = g
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
            })
        }
        reportContainer.addView(row)
    }

    private fun addTxnRow(row: TxnRow, index: Int) {
        val bg = if (index % 2 == 0) Color.parseColor("#1E1E2E") else Color.parseColor("#252535")
        val amountColor = if (row.type == "credit") Color.parseColor("#A5D6A7")
        else Color.parseColor("#EF9A9A")
        val party = row.partyName.ifEmpty { row.description }

        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg); setPadding(dp(8), dp(10), dp(8), dp(10))
        }

        listOf(
            Triple(row.date,                 1.2f, Gravity.START),
            Triple(row.txnId,                1.8f, Gravity.START),
            Triple(party,                    2f,   Gravity.START),
            Triple(row.mode.uppercase(),     0.8f, Gravity.CENTER),
            Triple(formatAmount(row.amount), 1.2f, Gravity.END)
        ).forEachIndexed { i, (text, w, g) ->
            r.addView(TextView(this).apply {
                this.text = text; textSize = 10f
                setTextColor(if (i == 4) amountColor else Color.parseColor("#DDDDDD"))
                typeface  = if (i == 4) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity   = g; maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
            })
        }

        reportContainer.addView(r)
        addDivider(Color.parseColor("#1E2D3E"), 1)
    }

    private fun addSummaryRow(label: String, value: String, color: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#16213E"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 12f
            setTextColor(Color.parseColor("#90A4AE"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color)
        })
        reportContainer.addView(row)
        addDivider(Color.parseColor("#1E2D3E"), 1)
    }

    private fun addDivider(color: Int, height: Int) {
        reportContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
            setBackgroundColor(color)
        })
    }

    private fun generateAndSharePdf() {
        val file = buildPdf()
        val uri  = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Transaction Report — ${tvDateRange.text}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share Transaction Report"))
    }

    private fun buildPdf(): File {
        val pdf = PdfDocument(); val paint = Paint()
        val paintR = Paint().also { it.textAlign = Paint.Align.RIGHT }
        val pageWidth = 595; val pageHeight = 842; val margin = 36f
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = pdf.startPage(pageInfo); var canvas = page.canvas; var y = margin
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        fun newPage() {
            pdf.finishPage(page); pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page = pdf.startPage(pageInfo); canvas = page.canvas; y = margin
        }
        fun checkBreak(h: Float = 30f) { if (y > pageHeight - h) newPage() }

        // ── Header ────────────────────────────────────────────────
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, paint)
        paint.color = Color.parseColor("#3949AB")
        canvas.drawRect(0f, 100f, pageWidth.toFloat(), 104f, paint)

        paint.color = Color.WHITE; paint.textSize = 24f; paint.isFakeBoldText = true
        canvas.drawText("Transaction Report", margin, 40f, paint)
        paint.textSize = 11f; paint.isFakeBoldText = false
        canvas.drawText("Period: ${tvDateRange.text}", margin, 62f, paint)
        canvas.drawText("Generated: $dateStr", margin, 80f, paint)
        paintR.color = Color.parseColor("#90CAF9"); paintR.textSize = 11f
        canvas.drawText("CashBook", pageWidth - margin, 62f, paintR)
        y = 118f

        val totalIn  = rows.filter { it.type == "credit" }.sumOf { it.amount }
        val totalOut = rows.filter { it.type == "debit"  }.sumOf { it.amount }
        val net      = totalIn - totalOut

        // Summary band
        paint.color = Color.parseColor("#E8EAF6")
        canvas.drawRoundRect(android.graphics.RectF(margin, y, pageWidth - margin, y + 54f), 8f, 8f, paint)
        listOf(
            Triple("Total In",  formatAmount(totalIn),  margin + 10f),
            Triple("Total Out", formatAmount(totalOut), margin + 185f),
            Triple("Net Flow",  formatAmount(net),      margin + 360f)
        ).forEach { (label, value, x) ->
            paint.color = Color.parseColor("#1A237E"); paint.textSize = 9f; paint.isFakeBoldText = false
            canvas.drawText(label, x, y + 18f, paint)
            val vc = if (label == "Net Flow") (if (net >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
            else if (label == "Total In") Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
            paint.color = vc; paint.textSize = 13f; paint.isFakeBoldText = true
            canvas.drawText(value, x, y + 38f, paint)
        }
        y += 66f

        val c1 = margin + 2f; val c2 = margin + 70f
        val c3 = margin + 190f; val c4 = margin + 350f; val c5 = pageWidth - margin

        fun drawTxnHeader() {
            checkBreak(28f)
            paint.color = Color.parseColor("#1A237E")
            canvas.drawRect(margin, y, pageWidth - margin, y + 24f, paint)
            paint.color = Color.WHITE; paint.textSize = 9f; paint.isFakeBoldText = true
            canvas.drawText("Date", c1, y + 16f, paint)
            canvas.drawText("Txn ID", c2, y + 16f, paint)
            canvas.drawText("Party / Category", c3, y + 16f, paint)
            canvas.drawText("Mode", c4, y + 16f, paint)
            paintR.color = Color.WHITE; paintR.isFakeBoldText = true
            canvas.drawText("Amount", c5, y + 16f, paintR)
            y += 28f
        }

        fun drawSectionBand(title: String, color: Int) {
            checkBreak(36f); y += 8f
            paint.color = color
            canvas.drawRect(margin, y, pageWidth - margin, y + 22f, paint)
            paint.color = Color.WHITE; paint.textSize = 10f; paint.isFakeBoldText = true
            canvas.drawText(title.uppercase(), margin + 8f, y + 15f, paint)
            y += 26f
        }

        fun drawRows(list: List<TxnRow>, amountColor: Int, rowBg: String) {
            list.forEachIndexed { i, row ->
                checkBreak(26f)
                if (i % 2 == 0) {
                    paint.color = Color.parseColor(rowBg)
                    canvas.drawRect(margin, y - 3f, pageWidth - margin, y + 18f, paint)
                }
                val party = row.partyName.ifEmpty { row.description }
                paint.color = Color.parseColor("#333333"); paint.textSize = 9f; paint.isFakeBoldText = false
                canvas.drawText(row.date,        c1, y + 11f, paint)
                canvas.drawText(row.txnId,       c2, y + 11f, paint)
                canvas.drawText(party,           c3, y + 11f, paint)
                canvas.drawText(row.mode.uppercase(), c4, y + 11f, paint)
                paintR.color = amountColor; paintR.isFakeBoldText = true
                canvas.drawText(formatAmount(row.amount), c5, y + 11f, paintR)
                paint.color = Color.parseColor("#E8EAF6"); paint.strokeWidth = 0.5f
                canvas.drawLine(margin, y + 19f, pageWidth - margin, y + 19f, paint)
                y += 24f
            }
        }

        fun drawTotal(label: String, value: Double, color: Int) {
            checkBreak(24f)
            paint.color = Color.parseColor("#E8EAF6")
            canvas.drawRect(margin, y, pageWidth - margin, y + 20f, paint)
            paint.color = Color.parseColor("#333333"); paint.textSize = 10f; paint.isFakeBoldText = true
            canvas.drawText(label, margin + 4f, y + 14f, paint)
            paintR.color = color; paintR.isFakeBoldText = true
            canvas.drawText(formatAmount(value), c5, y + 14f, paintR)
            y += 24f
        }

        val received = rows.filter { it.txnType == "payment_received" }
        val made     = rows.filter { it.txnType == "payment_made" }
        val expenses = rows.filter { it.txnType == "expense" }

        if (received.isNotEmpty()) {
            drawSectionBand("Payment Received", Color.parseColor("#1B5E20"))
            drawTxnHeader()
            drawRows(received, Color.parseColor("#2E7D32"), "#F9FBF9")
            drawTotal("Total Received", received.sumOf { it.amount }, Color.parseColor("#2E7D32"))
        }

        if (made.isNotEmpty()) {
            drawSectionBand("Payment Made", Color.parseColor("#C62828"))
            drawTxnHeader()
            drawRows(made, Color.parseColor("#C62828"), "#FFF5F5")
            drawTotal("Total Paid", made.sumOf { it.amount }, Color.parseColor("#C62828"))
        }

        if (expenses.isNotEmpty()) {
            drawSectionBand("Expenses", Color.parseColor("#E65100"))
            drawTxnHeader()
            drawRows(expenses, Color.parseColor("#E65100"), "#FFF8F5")
            drawTotal("Total Expenses", expenses.sumOf { it.amount }, Color.parseColor("#E65100"))
        }

        checkBreak(90f); y += 10f
        listOf(
            Triple("Total In",  totalIn,  Color.parseColor("#2E7D32")),
            Triple("Total Out", totalOut, Color.parseColor("#C62828")),
            Triple("Net Flow",  net, if (net >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
        ).forEach { (label, value, color) ->
            checkBreak(26f)
            paint.color = Color.parseColor("#E8EAF6")
            canvas.drawRect(margin, y, pageWidth - margin, y + 22f, paint)
            paint.color = Color.parseColor("#333333"); paint.textSize = 10f; paint.isFakeBoldText = false
            canvas.drawText(label, margin + 4f, y + 15f, paint)
            paintR.color = color; paintR.textSize = 11f; paintR.isFakeBoldText = true
            canvas.drawText(formatAmount(value), c5, y + 15f, paintR)
            y += 24f
        }

        paint.color = Color.parseColor("#E0E0E0")
        canvas.drawLine(margin, pageHeight - 28f, pageWidth - margin, pageHeight - 28f, paint)
        paint.color = Color.parseColor("#9E9E9E"); paint.textSize = 8f; paint.isFakeBoldText = false
        canvas.drawText("CashBook Transaction Report  |  ${tvDateRange.text}  |  $dateStr", margin, pageHeight - 14f, paint)

        pdf.finishPage(page)
        val file = File(cacheDir, "txn_report_${System.currentTimeMillis()}.pdf")
        pdf.writeTo(FileOutputStream(file)); pdf.close()
        return file
    }

    private fun formatAmount(amount: Double): String {
        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
        fmt.minimumFractionDigits = 2; fmt.maximumFractionDigits = 2
        return "₹${fmt.format(amount)}"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun newIntent(context: Context) = Intent(context, TransactionReportActivity::class.java)
    }
}