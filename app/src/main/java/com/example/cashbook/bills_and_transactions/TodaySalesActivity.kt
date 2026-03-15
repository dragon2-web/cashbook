package com.example.cashbook.bills_and_transactions

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
import com.example.cashbook.ledger.TransactionDetailActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class TodaySalesActivity : AppCompatActivity() {

    private lateinit var container:   LinearLayout
    private lateinit var emptyState:  TextView
    private lateinit var tvTotal:     TextView
    private lateinit var tvCount:     TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvDateLabel: TextView
    private lateinit var btnPickDate: MaterialButton
    private lateinit var btnExport:   MaterialButton

    private val db by lazy { FirebaseFirestore.getInstance() }
    private var selectedTag      = ""
    private var selectedDateDisp = ""

    data class SaleRow(val txnId: String, val billId: String, val firm: String, val amount: Double)
    private val rows = mutableListOf<SaleRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_today_list)

        bindViews()
        findViewById<TextView>(R.id.tvTitle).text = "Sales"
        findViewById<View>(R.id.headerBand).setBackgroundColor(Color.parseColor("#1B5E20"))
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        btnPickDate.setOnClickListener { showDatePicker() }
        btnExport.setOnClickListener {
            if (rows.isEmpty()) Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            else shareAsPdf()
        }
        setToday()
    }

    private fun bindViews() {
        container   = findViewById(R.id.container)
        emptyState  = findViewById(R.id.emptyState)
        tvTotal     = findViewById(R.id.tvTotal)
        tvCount     = findViewById(R.id.tvCount)
        progressBar = findViewById(R.id.progressBar)
        tvDateLabel = findViewById(R.id.tvDateLabel)
        btnPickDate = findViewById(R.id.btnPickDate)
        btnExport   = findViewById(R.id.btnExport)
    }

    private fun setToday() {
        val today        = Date()
        selectedTag      = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today)
        selectedDateDisp = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(today)
        tvDateLabel.text = selectedDateDisp
        load()
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            selectedTag      = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            selectedDateDisp = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(cal.time)
            tvDateLabel.text = selectedDateDisp
            load()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun load() {
        progressBar.visibility = View.VISIBLE
        container.visibility   = View.GONE
        emptyState.visibility  = View.GONE
        btnExport.isEnabled    = false
        tvTotal.text           = "—"
        tvCount.text           = "—"
        rows.clear()
        container.removeAllViews()

        db.collection("transactions")
            .whereEqualTo("dateTag",  selectedTag)
            .whereEqualTo("txnType",  "sales")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE

                if (snapshot.isEmpty) {
                    emptyState.visibility = View.VISIBLE
                    tvTotal.text = formatAmount(0.0); tvCount.text = "0"
                    return@addOnSuccessListener
                }

                var pending = snapshot.documents.size

                snapshot.documents.forEach { doc ->
                    val salesId = doc.getString("salesId") ?: ""
                    val amount  = doc.getDouble("amount")  ?: 0.0
                    val txnId   = doc.getString("txnId")   ?: doc.id

                    if (salesId.isEmpty()) {
                        rows.add(SaleRow(txnId, salesId, "", amount))
                        if (--pending == 0) renderRows(); return@forEach
                    }

                    db.collection("salesBills").document(salesId).get()
                        .addOnSuccessListener { billDoc ->
                            val customerId = billDoc.getString("customerId") ?: ""
                            if (customerId.isEmpty()) {
                                rows.add(SaleRow(txnId, salesId, "", amount))
                                if (--pending == 0) renderRows(); return@addOnSuccessListener
                            }
                            db.collection("customers").document(customerId).get()
                                .addOnSuccessListener { custDoc ->
                                    val firm = custDoc.getString("firmName") ?: custDoc.getString("name") ?: ""
                                    rows.add(SaleRow(txnId, salesId, firm, amount))
                                    if (--pending == 0) renderRows()
                                }
                                .addOnFailureListener {
                                    rows.add(SaleRow(txnId, salesId, "", amount))
                                    if (--pending == 0) renderRows()
                                }
                        }
                        .addOnFailureListener {
                            rows.add(SaleRow(txnId, salesId, "", amount))
                            if (--pending == 0) renderRows()
                        }
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderRows() {
        rows.sortBy { it.txnId }
        val total = rows.sumOf { it.amount }
        tvTotal.text = formatAmount(total); tvCount.text = "${rows.size}"
        container.visibility = View.VISIBLE; btnExport.isEnabled = true

        addHeader(listOf("Bill ID", "Firm Name", "Amount"),
            listOf(1.8f, 2f, 1.2f), Color.parseColor("#1B5E20"))

        rows.forEachIndexed { i, row ->
            addRow(
                cols      = listOf(row.billId.ifEmpty { row.txnId }, row.firm, formatAmount(row.amount)),
                weights   = listOf(1.8f, 2f, 1.2f),
                gravities = listOf(Gravity.START, Gravity.START, Gravity.END),
                lastColor = Color.parseColor("#A5D6A7"),
                index     = i
            ) { startActivity(TransactionDetailActivity.newIntent(this, row.txnId)) }
        }

        addTotalRow("Total Sales", formatAmount(total), Color.parseColor("#A5D6A7"))
    }

    // ── UI helpers ────────────────────────────────────────────────
    private fun addHeader(cols: List<String>, weights: List<Float>, color: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(color)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        cols.forEachIndexed { i, col ->
            row.addView(TextView(this).apply {
                text = col; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = if (i == cols.size - 1) Gravity.END else Gravity.START
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weights[i])
            })
        }
        container.addView(row)
    }

    private fun addRow(
        cols:      List<String>,
        weights:   List<Float>,
        gravities: List<Int>,
        lastColor: Int,
        index:     Int,
        onClick:   () -> Unit
    ) {
        val bg = if (index % 2 == 0) Color.parseColor("#1E1E2E") else Color.parseColor("#252535")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
        }
        cols.forEachIndexed { i, col ->
            val isLast = i == cols.size - 1
            row.addView(TextView(this).apply {
                text = col; textSize = 12f
                setTextColor(if (isLast) lastColor else Color.parseColor("#DDDDDD"))
                typeface  = if (isLast) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity   = gravities[i]; maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weights[i])
            })
        }
        container.addView(row)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#2A2A3E"))
        })
    }

    private fun addTotalRow(label: String, value: String, color: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#16213E"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
        })
        container.addView(row)
    }

    // ── PDF ───────────────────────────────────────────────────────
    private fun shareAsPdf() {
        val pdf    = PdfDocument()
        val paint  = Paint()
        val paintR = Paint().also { it.textAlign = Paint.Align.RIGHT }
        val pw = 595; val ph = 842; val mg = 36f
        var pn = 1
        var pi = PdfDocument.PageInfo.Builder(pw, ph, pn).create()
        var pg = pdf.startPage(pi); var cv = pg.canvas; var y = mg
        val ds = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        fun np() { pdf.finishPage(pg); pn++; pi = PdfDocument.PageInfo.Builder(pw, ph, pn).create(); pg = pdf.startPage(pi); cv = pg.canvas; y = mg }
        fun cb(h: Float = 28f) { if (y > ph - h) np() }

        // Header
        paint.color = Color.parseColor("#1B5E20")
        cv.drawRect(0f, 0f, pw.toFloat(), 90f, paint)
        paint.color = Color.parseColor("#2E7D32")
        cv.drawRect(0f, 90f, pw.toFloat(), 94f, paint)
        paint.color = Color.WHITE; paint.textSize = 22f; paint.isFakeBoldText = true
        cv.drawText("Sales — $selectedDateDisp", mg, 38f, paint)
        paint.textSize = 10f; paint.isFakeBoldText = false
        cv.drawText("Generated: $ds", mg, 58f, paint)
        paintR.color = Color.parseColor("#A5D6A7"); paintR.textSize = 10f
        cv.drawText("CashBook", pw - mg, 58f, paintR)
        y = 106f

        // Summary
        paint.color = Color.parseColor("#F1F8E9")
        cv.drawRoundRect(android.graphics.RectF(mg, y, pw - mg, y + 46f), 8f, 8f, paint)
        listOf(Triple("Total Bills", "${rows.size}", mg + 10f),
            Triple("Total Sales", formatAmount(rows.sumOf { it.amount }), mg + 220f)).forEach { (l, v, x) ->
            paint.color = Color.parseColor("#33691E"); paint.textSize = 8f; paint.isFakeBoldText = false
            cv.drawText(l, x, y + 14f, paint)
            paint.color = Color.parseColor("#1B5E20"); paint.textSize = 12f; paint.isFakeBoldText = true
            cv.drawText(v, x, y + 32f, paint)
        }
        y += 58f

        val c1 = mg + 2f; val c2 = mg + 150f; val c3 = pw - mg

        fun dh() {
            cb(26f); paint.color = Color.parseColor("#1B5E20")
            cv.drawRect(mg, y, pw - mg, y + 22f, paint)
            paint.color = Color.WHITE; paint.textSize = 9f; paint.isFakeBoldText = true
            cv.drawText("Bill ID", c1, y + 15f, paint)
            cv.drawText("Firm Name", c2, y + 15f, paint)
            paintR.color = Color.WHITE; paintR.isFakeBoldText = true
            cv.drawText("Amount", c3, y + 15f, paintR); y += 26f
        }
        dh()

        rows.forEachIndexed { i, row ->
            cb(24f)
            if (i % 2 == 0) { paint.color = Color.parseColor("#F9FBF9"); cv.drawRect(mg, y - 3f, pw - mg, y + 18f, paint) }
            paint.color = Color.parseColor("#333333"); paint.textSize = 9f; paint.isFakeBoldText = false
            cv.drawText(row.billId.ifEmpty { row.txnId }, c1, y + 11f, paint)
            cv.drawText(row.firm, c2, y + 11f, paint)
            paintR.color = Color.parseColor("#2E7D32"); paintR.isFakeBoldText = true
            cv.drawText(formatAmount(row.amount), c3, y + 11f, paintR)
            paint.color = Color.parseColor("#E8F5E9"); paint.strokeWidth = 0.5f
            cv.drawLine(mg, y + 19f, pw - mg, y + 19f, paint); y += 22f
        }

        cb(24f); y += 4f
        paint.color = Color.parseColor("#E8F5E9")
        cv.drawRect(mg, y, pw - mg, y + 20f, paint)
        paint.color = Color.parseColor("#1B5E20"); paint.textSize = 10f; paint.isFakeBoldText = true
        cv.drawText("Total Sales", mg + 4f, y + 14f, paint)
        paintR.color = Color.parseColor("#1B5E20")
        cv.drawText(formatAmount(rows.sumOf { it.amount }), c3, y + 14f, paintR)

        paint.color = Color.parseColor("#E0E0E0")
        cv.drawLine(mg, ph - 26f, pw - mg, ph - 26f, paint)
        paint.color = Color.parseColor("#9E9E9E"); paint.textSize = 8f; paint.isFakeBoldText = false
        cv.drawText("CashBook Sales  |  $selectedDateDisp  |  $ds", mg, ph - 12f, paint)

        pdf.finishPage(pg)
        val file = File(cacheDir, "sales_${selectedTag}.pdf")
        pdf.writeTo(FileOutputStream(file)); pdf.close()

        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sales — $selectedDateDisp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share Sales PDF"))
    }

    private fun formatAmount(a: Double): String {
        val f = NumberFormat.getNumberInstance(Locale("en", "IN"))
        f.minimumFractionDigits = 2; f.maximumFractionDigits = 2; return "₹${f.format(a)}"
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun newIntent(context: Context) = Intent(context, TodaySalesActivity::class.java)
    }
}