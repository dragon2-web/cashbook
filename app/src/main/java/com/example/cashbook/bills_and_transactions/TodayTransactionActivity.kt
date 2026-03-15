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

class TodayTransactionActivity : AppCompatActivity() {

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

    data class TxnRow(
        val txnId:   String,
        val txnType: String,
        val party:   String,
        val mode:    String,
        val amount:  Double,
        val type:    String
    )
    private val rows = mutableListOf<TxnRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_today_list)

        bindViews()
        findViewById<TextView>(R.id.tvTitle).text = "Transactions"
        findViewById<View>(R.id.headerBand).setBackgroundColor(Color.parseColor("#1A237E"))
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
            .whereEqualTo("dateTag", selectedTag)
            .whereIn("txnType", listOf("payment_received", "payment_made", "expense"))
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE

                if (snapshot.isEmpty) {
                    emptyState.visibility = View.VISIBLE
                    tvTotal.text = "—"; tvCount.text = "0"
                    return@addOnSuccessListener
                }

                snapshot.documents.forEach { doc ->
                    rows.add(TxnRow(
                        txnId   = doc.getString("txnId")       ?: doc.id,
                        txnType = doc.getString("txnType")     ?: "",
                        party   = doc.getString("partyName")   ?: doc.getString("description") ?: "",
                        mode    = doc.getString("mode")        ?: "",
                        amount  = doc.getDouble("amount")      ?: 0.0,
                        type    = doc.getString("type")        ?: "debit"
                    ))
                }

                renderRows()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderRows() {
        val totalIn  = rows.filter { it.type == "credit" }.sumOf { it.amount }
        val totalOut = rows.filter { it.type == "debit"  }.sumOf { it.amount }
        tvCount.text = "${rows.size}"
        tvTotal.text = "In: ${formatAmount(totalIn)}"
        container.visibility = View.VISIBLE; btnExport.isEnabled = true

        addHeader(listOf("Txn ID", "Type", "Party", "Mode", "Amount"),
            listOf(1.6f, 1.2f, 1.8f, 0.8f, 1.2f), Color.parseColor("#1A237E"))

        rows.forEachIndexed { i, row ->
            val label = when (row.txnType) {
                "payment_received" -> "Received"
                "payment_made"     -> "Paid"
                else               -> "Expense"
            }
            val amtColor = if (row.type == "credit") Color.parseColor("#A5D6A7")
            else Color.parseColor("#EF9A9A")

            addRow(
                cols      = listOf(row.txnId, label, row.party, row.mode.uppercase(), formatAmount(row.amount)),
                weights   = listOf(1.6f, 1.2f, 1.8f, 0.8f, 1.2f),
                gravities = listOf(Gravity.START, Gravity.START, Gravity.START, Gravity.CENTER, Gravity.END),
                lastColor = amtColor,
                index     = i
            ) { startActivity(TransactionDetailActivity.newIntent(this, row.txnId)) }
        }

        addDivider(Color.parseColor("#1A237E"), 2)
        addSummaryRow("Total In",  formatAmount(totalIn),  Color.parseColor("#A5D6A7"))
        addSummaryRow("Total Out", formatAmount(totalOut), Color.parseColor("#EF9A9A"))
        val net = totalIn - totalOut
        addSummaryRow("Net Flow",  formatAmount(net),
            if (net >= 0) Color.parseColor("#A5D6A7") else Color.parseColor("#EF9A9A"))
    }

    private fun addHeader(cols: List<String>, weights: List<Float>, color: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(color); setPadding(dp(12), dp(10), dp(12), dp(10))
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

    private fun addRow(cols: List<String>, weights: List<Float>, gravities: List<Int>,
                       lastColor: Int, index: Int, onClick: () -> Unit) {
        val bg = if (index % 2 == 0) Color.parseColor("#1E1E2E") else Color.parseColor("#252535")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(bg)
            setPadding(dp(12), dp(12), dp(12), dp(12)); setOnClickListener { onClick() }
        }
        cols.forEachIndexed { i, col ->
            val isLast = i == cols.size - 1
            row.addView(TextView(this).apply {
                text = col; textSize = 11f
                setTextColor(if (isLast) lastColor else Color.parseColor("#DDDDDD"))
                typeface = if (isLast) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = gravities[i]; maxLines = 1
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

    private fun addSummaryRow(label: String, value: String, color: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#16213E"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color)
        })
        container.addView(row)
        addDivider(Color.parseColor("#1E2D3E"), 1)
    }

    private fun addDivider(color: Int, height: Int) {
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
            setBackgroundColor(color)
        })
    }

    private fun shareAsPdf() {
        val pdf = PdfDocument(); val paint = Paint()
        val paintR = Paint().also { it.textAlign = Paint.Align.RIGHT }
        val pw = 595; val ph = 842; val mg = 36f
        var pn = 1; var pi = PdfDocument.PageInfo.Builder(pw, ph, pn).create()
        var pg = pdf.startPage(pi); var cv = pg.canvas; var y = mg
        val ds = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        fun np() { pdf.finishPage(pg); pn++; pi = PdfDocument.PageInfo.Builder(pw, ph, pn).create(); pg = pdf.startPage(pi); cv = pg.canvas; y = mg }
        fun cb(h: Float = 28f) { if (y > ph - h) np() }

        paint.color = Color.parseColor("#1A237E")
        cv.drawRect(0f, 0f, pw.toFloat(), 90f, paint)
        paint.color = Color.parseColor("#3949AB")
        cv.drawRect(0f, 90f, pw.toFloat(), 94f, paint)
        paint.color = Color.WHITE; paint.textSize = 22f; paint.isFakeBoldText = true
        cv.drawText("Transactions — $selectedDateDisp", mg, 38f, paint)
        paint.textSize = 10f; paint.isFakeBoldText = false
        cv.drawText("Generated: $ds", mg, 58f, paint)
        paintR.color = Color.parseColor("#90CAF9"); paintR.textSize = 10f
        cv.drawText("CashBook", pw - mg, 58f, paintR)
        y = 106f

        val totalIn  = rows.filter { it.type == "credit" }.sumOf { it.amount }
        val totalOut = rows.filter { it.type == "debit"  }.sumOf { it.amount }
        val net      = totalIn - totalOut

        paint.color = Color.parseColor("#E8EAF6")
        cv.drawRoundRect(android.graphics.RectF(mg, y, pw - mg, y + 46f), 8f, 8f, paint)
        listOf(
            Triple("Total In",  formatAmount(totalIn),  mg + 10f),
            Triple("Total Out", formatAmount(totalOut), mg + 180f),
            Triple("Net Flow",  formatAmount(net),      mg + 360f)
        ).forEach { (l, v, x) ->
            paint.color = Color.parseColor("#1A237E"); paint.textSize = 8f; paint.isFakeBoldText = false
            cv.drawText(l, x, y + 14f, paint)
            val vc = if (l == "Total In") Color.parseColor("#2E7D32")
            else if (l == "Total Out") Color.parseColor("#C62828")
            else if (net >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
            paint.color = vc; paint.textSize = 12f; paint.isFakeBoldText = true
            cv.drawText(v, x, y + 32f, paint)
        }
        y += 58f

        val c1 = mg + 2f; val c2 = mg + 130f; val c3 = mg + 240f
        val c4 = mg + 380f; val c5 = pw - mg

        fun dh() {
            cb(26f); paint.color = Color.parseColor("#1A237E")
            cv.drawRect(mg, y, pw - mg, y + 22f, paint)
            paint.color = Color.WHITE; paint.textSize = 9f; paint.isFakeBoldText = true
            cv.drawText("Txn ID", c1, y + 15f, paint); cv.drawText("Type", c2, y + 15f, paint)
            cv.drawText("Party", c3, y + 15f, paint); cv.drawText("Mode", c4, y + 15f, paint)
            paintR.color = Color.WHITE; paintR.isFakeBoldText = true
            cv.drawText("Amount", c5, y + 15f, paintR); y += 26f
        }
        dh()

        rows.forEachIndexed { i, row ->
            cb(24f)
            if (i % 2 == 0) { paint.color = Color.parseColor("#F7F9FF"); cv.drawRect(mg, y - 3f, pw - mg, y + 18f, paint) }
            val label = when (row.txnType) { "payment_received" -> "Received"; "payment_made" -> "Paid"; else -> "Expense" }
            val ac = if (row.type == "credit") Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
            paint.color = Color.parseColor("#333333"); paint.textSize = 9f; paint.isFakeBoldText = false
            cv.drawText(row.txnId, c1, y + 11f, paint); cv.drawText(label, c2, y + 11f, paint)
            cv.drawText(row.party, c3, y + 11f, paint); cv.drawText(row.mode.uppercase(), c4, y + 11f, paint)
            paintR.color = ac; paintR.isFakeBoldText = true
            cv.drawText(formatAmount(row.amount), c5, y + 11f, paintR)
            paint.color = Color.parseColor("#E8EAF6"); paint.strokeWidth = 0.5f
            cv.drawLine(mg, y + 19f, pw - mg, y + 19f, paint); y += 22f
        }

        cb(80f); y += 6f
        listOf(Triple("Total In", totalIn, Color.parseColor("#2E7D32")),
            Triple("Total Out", totalOut, Color.parseColor("#C62828")),
            Triple("Net Flow", net, if (net >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
        ).forEach { (label, value, color) ->
            cb(24f); paint.color = Color.parseColor("#E8EAF6")
            cv.drawRect(mg, y, pw - mg, y + 20f, paint)
            paint.color = Color.parseColor("#333333"); paint.textSize = 10f; paint.isFakeBoldText = false
            cv.drawText(label, mg + 4f, y + 14f, paint)
            paintR.color = color; paintR.isFakeBoldText = true
            cv.drawText(formatAmount(value), c5, y + 14f, paintR); y += 22f
        }

        paint.color = Color.parseColor("#E0E0E0")
        cv.drawLine(mg, ph - 26f, pw - mg, ph - 26f, paint)
        paint.color = Color.parseColor("#9E9E9E"); paint.textSize = 8f; paint.isFakeBoldText = false
        cv.drawText("CashBook Transactions  |  $selectedDateDisp  |  $ds", mg, ph - 12f, paint)

        pdf.finishPage(pg)
        val file = File(cacheDir, "transactions_${selectedTag}.pdf")
        pdf.writeTo(FileOutputStream(file)); pdf.close()

        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Transactions — $selectedDateDisp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share Transactions PDF"))
    }

    private fun formatAmount(a: Double): String {
        val f = NumberFormat.getNumberInstance(Locale("en", "IN"))
        f.minimumFractionDigits = 2; f.maximumFractionDigits = 2; return "₹${f.format(a)}"
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun newIntent(context: Context) = Intent(context, TodayTransactionActivity::class.java)
    }
}