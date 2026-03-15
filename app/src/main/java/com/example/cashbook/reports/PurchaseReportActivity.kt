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

class PurchaseReportActivity : AppCompatActivity() {

    private lateinit var btnToday:        MaterialButton
    private lateinit var btnThisMonth:    MaterialButton
    private lateinit var btnThisYear:     MaterialButton
    private lateinit var btnCustomRange:  MaterialButton
    private lateinit var tvDateRange:     TextView
    private lateinit var reportContainer: LinearLayout
    private lateinit var emptyState:      TextView
    private lateinit var btnExport:       MaterialButton
    private lateinit var progressBar:     ProgressBar
    private lateinit var tvTotalPurchase: TextView
    private lateinit var tvTotalBills:    TextView

    private val db by lazy { FirebaseFirestore.getInstance() }

    private var fromTag = ""
    private var toTag   = ""

    data class PurchaseBillRow(
        val billId:       String,
        val date:         String,
        val firmName:     String,
        val billAmount:   Double,
        val runningTotal: Double
    )

    private val rows = mutableListOf<PurchaseBillRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase_report)

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
        tvTotalPurchase = findViewById(R.id.tvTotalPurchase)
        tvTotalBills    = findViewById(R.id.tvTotalBills)
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
                if (it == active) Color.parseColor("#B71C1C") else Color.parseColor("#2A2A3E")
            )
        }
    }

    private fun loadReport() {
        progressBar.visibility     = View.VISIBLE
        reportContainer.visibility = View.GONE
        emptyState.visibility      = View.GONE
        btnExport.isEnabled        = false
        rows.clear()

        // Read directly from transactions collection, filter by purchase type
        db.collection("transactions")
            .whereGreaterThanOrEqualTo("dateTag", fromTag)
            .whereLessThanOrEqualTo("dateTag", toTag)
            .whereEqualTo("txnType", "purchase")
            .orderBy("dateTag", Query.Direction.ASCENDING)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    progressBar.visibility = View.GONE
                    emptyState.visibility  = View.VISIBLE
                    tvTotalPurchase.text   = formatAmount(0.0)
                    tvTotalBills.text      = "0 bills"
                    return@addOnSuccessListener
                }

                var pending = snapshot.documents.size

                snapshot.documents.forEach { doc ->
                    val purchaseId = doc.getString("purchaseId") ?: ""
                    val date       = doc.getString("date")       ?: ""
                    val amount     = doc.getDouble("amount")     ?: 0.0

                    if (purchaseId.isEmpty()) {
                        rows.add(PurchaseBillRow(doc.id, date, "", amount, 0.0))
                        if (--pending == 0) finishLoad()
                        return@forEach
                    }

                    db.collection("purchaseBills").document(purchaseId).get()
                        .addOnSuccessListener { billDoc ->
                            val supplierId = billDoc.getString("supplierId") ?: ""

                            if (supplierId.isEmpty()) {
                                rows.add(PurchaseBillRow(purchaseId, date, "", amount, 0.0))
                                if (--pending == 0) finishLoad()
                                return@addOnSuccessListener
                            }

                            db.collection("suppliers").document(supplierId).get()
                                .addOnSuccessListener { supDoc ->
                                    val firm = supDoc.getString("firmName")
                                        ?: supDoc.getString("name") ?: ""
                                    rows.add(PurchaseBillRow(purchaseId, date, firm, amount, 0.0))
                                    if (--pending == 0) finishLoad()
                                }
                                .addOnFailureListener {
                                    rows.add(PurchaseBillRow(purchaseId, date, "", amount, 0.0))
                                    if (--pending == 0) finishLoad()
                                }
                        }
                        .addOnFailureListener {
                            rows.add(PurchaseBillRow(purchaseId, date, "", amount, 0.0))
                            if (--pending == 0) finishLoad()
                        }
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun finishLoad() {
        rows.sortBy { it.date }
        var running = 0.0
        val finalRows = rows.map { row ->
            running += row.billAmount
            row.copy(runningTotal = running)
        }
        rows.clear(); rows.addAll(finalRows)

        val total = rows.sumOf { it.billAmount }
        progressBar.visibility     = View.GONE
        reportContainer.visibility = View.VISIBLE
        btnExport.isEnabled        = true
        tvTotalPurchase.text       = formatAmount(total)
        tvTotalBills.text          = "${rows.size} bills"
        renderReport()
    }

    private fun renderReport() {
        reportContainer.removeAllViews()
        addTableHeader()
        rows.forEachIndexed { i, row -> addBillRow(row, i) }

        val total = rows.sumOf { it.billAmount }
        addDivider(Color.parseColor("#B71C1C"), 2)
        addSummaryRow("Total Bills",    "${rows.size}",       Color.WHITE)
        addSummaryRow("Total Purchase", formatAmount(total),  Color.parseColor("#EF9A9A"))
    }

    private fun addTableHeader() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#B71C1C"))
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        listOf(
            Triple("Date",          1.3f, Gravity.START),
            Triple("Bill ID",       1.6f, Gravity.START),
            Triple("Firm Name",     2.2f, Gravity.START),
            Triple("Amount",        1.2f, Gravity.END),
            Triple("Running Total", 1.4f, Gravity.END)
        ).forEach { (col, w, g) ->
            row.addView(TextView(this).apply {
                text = col; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE); gravity = g
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
            })
        }
        reportContainer.addView(row)
    }

    private fun addBillRow(row: PurchaseBillRow, index: Int) {
        val bg = if (index % 2 == 0) Color.parseColor("#1E1E2E") else Color.parseColor("#252535")
        val r  = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(bg)
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        listOf(
            Triple(row.date,                     1.3f, Gravity.START),
            Triple(row.billId,                   1.6f, Gravity.START),
            Triple(row.firmName,                 2.2f, Gravity.START),
            Triple(formatAmount(row.billAmount),  1.2f, Gravity.END),
            Triple(formatAmount(row.runningTotal), 1.4f, Gravity.END)
        ).forEachIndexed { i, (text, w, g) ->
            r.addView(TextView(this).apply {
                this.text = text; textSize = 10f
                setTextColor(when (i) { 3 -> Color.parseColor("#EF9A9A"); 4 -> Color.parseColor("#90CAF9"); else -> Color.parseColor("#DDDDDD") })
                typeface = if (i >= 3) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = g; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
            })
        }
        reportContainer.addView(r); addDivider(Color.parseColor("#2A2A3E"), 1)
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
        reportContainer.addView(row); addDivider(Color.parseColor("#1E2D3E"), 1)
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
            putExtra(Intent.EXTRA_SUBJECT, "Purchase Report — ${tvDateRange.text}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share Purchase Report"))
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

        paint.color = Color.parseColor("#B71C1C")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, paint)
        paint.color = Color.parseColor("#C62828")
        canvas.drawRect(0f, 100f, pageWidth.toFloat(), 104f, paint)

        paint.color = Color.WHITE; paint.textSize = 24f; paint.isFakeBoldText = true
        canvas.drawText("Purchase Report", margin, 40f, paint)
        paint.textSize = 11f; paint.isFakeBoldText = false
        canvas.drawText("Period: ${tvDateRange.text}", margin, 62f, paint)
        canvas.drawText("Generated: $dateStr", margin, 80f, paint)
        paintR.color = Color.parseColor("#EF9A9A"); paintR.textSize = 11f
        canvas.drawText("CashBook", pageWidth - margin, 62f, paintR)
        y = 118f

        val total = rows.sumOf { it.billAmount }

        paint.color = Color.parseColor("#FFEBEE")
        canvas.drawRoundRect(android.graphics.RectF(margin, y, pageWidth - margin, y + 54f), 8f, 8f, paint)
        listOf(
            Triple("Total Bills",    "${rows.size}",    margin + 10f),
            Triple("Total Purchase", formatAmount(total), margin + 250f)
        ).forEach { (label, value, x) ->
            paint.color = Color.parseColor("#B71C1C"); paint.textSize = 9f; paint.isFakeBoldText = false
            canvas.drawText(label, x, y + 18f, paint)
            paint.color = Color.parseColor("#C62828"); paint.textSize = 13f; paint.isFakeBoldText = true
            canvas.drawText(value, x, y + 38f, paint)
        }
        y += 66f

        val c1 = margin + 2f; val c2 = margin + 76f; val c3 = margin + 190f
        val c4 = margin + 360f; val c5 = pageWidth - margin

        fun drawHeader() {
            checkBreak(28f)
            paint.color = Color.parseColor("#B71C1C")
            canvas.drawRect(margin, y, pageWidth - margin, y + 24f, paint)
            paint.color = Color.WHITE; paint.textSize = 9f; paint.isFakeBoldText = true
            canvas.drawText("Date",    c1, y + 16f, paint)
            canvas.drawText("Bill ID", c2, y + 16f, paint)
            canvas.drawText("Firm",    c3, y + 16f, paint)
            paintR.color = Color.WHITE; paintR.isFakeBoldText = true
            canvas.drawText("Amount",        c4, y + 16f, paintR)
            canvas.drawText("Running Total", c5, y + 16f, paintR)
            y += 28f
        }
        drawHeader()

        rows.forEachIndexed { i, row ->
            checkBreak(26f)
            if (i % 2 == 0) {
                paint.color = Color.parseColor("#FFF5F5")
                canvas.drawRect(margin, y - 3f, pageWidth - margin, y + 18f, paint)
            }
            paint.color = Color.parseColor("#333333"); paint.textSize = 9f; paint.isFakeBoldText = false
            canvas.drawText(row.date,     c1, y + 11f, paint)
            canvas.drawText(row.billId,   c2, y + 11f, paint)
            canvas.drawText(row.firmName, c3, y + 11f, paint)
            paintR.color = Color.parseColor("#C62828"); paintR.isFakeBoldText = true
            canvas.drawText(formatAmount(row.billAmount),   c4, y + 11f, paintR)
            paintR.color = Color.parseColor("#1565C0")
            canvas.drawText(formatAmount(row.runningTotal), c5, y + 11f, paintR)
            paint.color = Color.parseColor("#FFEBEE"); paint.strokeWidth = 0.5f
            canvas.drawLine(margin, y + 19f, pageWidth - margin, y + 19f, paint)
            y += 24f
        }

        checkBreak(50f); y += 6f
        listOf(
            Triple("Total Bills",    "${rows.size} bills",  Color.parseColor("#B71C1C")),
            Triple("Total Purchase", formatAmount(total),   Color.parseColor("#C62828"))
        ).forEach { (label, value, color) ->
            checkBreak(26f)
            paint.color = Color.parseColor("#FFEBEE")
            canvas.drawRect(margin, y, pageWidth - margin, y + 22f, paint)
            paint.color = Color.parseColor("#555555"); paint.textSize = 10f; paint.isFakeBoldText = false
            canvas.drawText(label, c1, y + 15f, paint)
            paintR.color = color; paintR.textSize = 11f; paintR.isFakeBoldText = true
            canvas.drawText(value, c5, y + 15f, paintR)
            y += 24f
        }

        paint.color = Color.parseColor("#E0E0E0")
        canvas.drawLine(margin, pageHeight - 28f, pageWidth - margin, pageHeight - 28f, paint)
        paint.color = Color.parseColor("#9E9E9E"); paint.textSize = 8f; paint.isFakeBoldText = false
        canvas.drawText("CashBook Purchase Report  |  ${tvDateRange.text}  |  $dateStr", margin, pageHeight - 14f, paint)

        pdf.finishPage(page)
        val file = File(cacheDir, "purchase_report_${System.currentTimeMillis()}.pdf")
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
        fun newIntent(context: Context) = Intent(context, PurchaseReportActivity::class.java)
    }
}