package com.example.cashbook.sales_bill

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.cashbook.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SalesBillPreviewActivity : AppCompatActivity() {

    private lateinit var progressBar:  ProgressBar
    private lateinit var contentCard:  View
    private lateinit var btnShare:     MaterialButton
    private lateinit var btnPrint:     MaterialButton
    private lateinit var btnClose:     MaterialButton

    // Bill fields
    private lateinit var tvBillId:       TextView
    private lateinit var tvDate:         TextView
    private lateinit var tvCustomerFirm: TextView
    private lateinit var tvCustomerName: TextView
    private lateinit var tvCustomerPhone:TextView
    private lateinit var tvCustomerAddr: TextView
    private lateinit var tvGstin:        TextView
    private lateinit var itemsContainer: LinearLayout
    private lateinit var tvSubtotal:     TextView
    private lateinit var tvGrandTotal:   TextView

    private val db by lazy { FirebaseFirestore.getInstance() }

    private var billId       = ""
    private var customerName = ""
    private var firmName     = ""
    private var phone        = ""
    private var address      = ""
    private var gstin        = ""
    private var billDate     = ""
    private var billAmount   = 0.0
    private val items        = mutableListOf<Map<String, Any>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_bill_preview)

        billId = intent.getStringExtra(EXTRA_BILL_ID) ?: run { finish(); return }

        bindViews()

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnShare.setOnClickListener { generateAndShare() }
        btnPrint.setOnClickListener { printBill() }
        btnClose.setOnClickListener { finish() }

        loadBill()
    }

    private fun bindViews() {
        progressBar     = findViewById(R.id.progressBar)
        contentCard     = findViewById(R.id.contentCard)
        btnShare        = findViewById(R.id.btnShare)
        btnPrint        = findViewById(R.id.btnPrint)
        btnClose        = findViewById(R.id.btnClose)
        tvBillId        = findViewById(R.id.tvBillId)
        tvDate          = findViewById(R.id.tvDate)
        tvCustomerFirm  = findViewById(R.id.tvCustomerFirm)
        tvCustomerName  = findViewById(R.id.tvCustomerName)
        tvCustomerPhone = findViewById(R.id.tvCustomerPhone)
        tvCustomerAddr  = findViewById(R.id.tvCustomerAddr)
        tvGstin         = findViewById(R.id.tvGstin)
        itemsContainer  = findViewById(R.id.itemsContainer)
        tvSubtotal      = findViewById(R.id.tvSubtotal)
        tvGrandTotal    = findViewById(R.id.tvGrandTotal)
    }

    // ── Fetch Bill ────────────────────────────────────────────────
    private fun loadBill() {
        progressBar.visibility = View.VISIBLE
        contentCard.visibility = View.GONE

        db.collection("salesBills").document(billId).get()
            .addOnSuccessListener { billDoc ->
                val customerId = billDoc.getString("customerId") ?: ""
                billDate   = billDoc.getString("date")       ?: ""
                billAmount = billDoc.getDouble("billAmount") ?: 0.0

                db.collection("customers").document(customerId).get()
                    .addOnSuccessListener { custDoc ->
                        customerName = custDoc.getString("name")     ?: ""
                        firmName     = custDoc.getString("firmName") ?: ""
                        phone        = custDoc.getString("phone")    ?: ""
                        address      = custDoc.getString("address")  ?: ""
                        gstin        = custDoc.getString("gstin")    ?: ""

                        db.collection("salesBills").document(billId)
                            .collection("items").get()
                            .addOnSuccessListener { itemSnap ->
                                items.clear()
                                itemSnap.documents.forEach { doc ->
                                    items.add(mapOf(
                                        "productName" to (doc.getString("productName") ?: ""),
                                        "units"       to (doc.getDouble("units")       ?: 0.0),
                                        "rate"        to (doc.getDouble("rate")        ?: 0.0),
                                        "amount"      to (doc.getDouble("amount")      ?: 0.0)
                                    ))
                                }
                                progressBar.visibility = View.GONE
                                contentCard.visibility = View.VISIBLE
                                renderPreview()
                            }
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load bill: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Render preview on screen ──────────────────────────────────
    private fun renderPreview() {
        tvBillId.text        = billId
        tvDate.text          = billDate
        tvCustomerFirm.text  = firmName.ifEmpty { customerName }
        tvCustomerName.text  = customerName
        tvCustomerPhone.text = phone
        tvCustomerAddr.text  = address
        tvGstin.text         = if (gstin.isNotEmpty()) "GSTIN: $gstin" else ""
        tvGstin.visibility   = if (gstin.isNotEmpty()) View.VISIBLE else View.GONE

        itemsContainer.removeAllViews()

        items.forEach { item ->
            val productName = item["productName"] as? String ?: ""
            val units       = item["units"]       as? Double ?: 0.0
            val rate        = item["rate"]        as? Double ?: 0.0
            val amount      = item["amount"]      as? Double ?: 0.0

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
            }

            row.addView(TextView(this).apply {
                text     = productName
                textSize = 13f
                setTextColor(Color.parseColor("#212121"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.5f)
            })
            row.addView(TextView(this).apply {
                text     = formatQty(units)
                textSize = 13f
                gravity  = Gravity.CENTER
                setTextColor(Color.parseColor("#424242"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text     = formatAmount(rate)
                textSize = 13f
                gravity  = Gravity.END
                setTextColor(Color.parseColor("#424242"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            })
            row.addView(TextView(this).apply {
                text     = formatAmount(amount)
                textSize = 13f
                gravity  = Gravity.END
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1B5E20"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            })

            itemsContainer.addView(row)

            itemsContainer.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            })
        }

        tvSubtotal.text   = formatAmount(billAmount)
        tvGrandTotal.text = formatAmount(billAmount)
    }

    // ── PDF generation ────────────────────────────────────────────
    private fun buildPdf(): File {
        val pdf        = PdfDocument()
        val paint      = Paint()
        val paintR     = Paint().also { it.textAlign = Paint.Align.RIGHT }
        val paintC     = Paint().also { it.textAlign = Paint.Align.CENTER }
        val pageWidth  = 595
        val pageHeight = 842
        val margin     = 40f

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page     = pdf.startPage(pageInfo)
        val canvas   = page.canvas
        var y        = margin

        val generatedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        // ── Header band ───────────────────────────────────────────
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 90f, paint)

        paint.color = Color.parseColor("#3949AB")
        canvas.drawRect(0f, 90f, pageWidth.toFloat(), 94f, paint)

        paint.color = Color.WHITE; paint.textSize = 26f; paint.isFakeBoldText = true
        canvas.drawText("SALES INVOICE", margin, 38f, paint)

        paint.textSize = 10f; paint.isFakeBoldText = false
        canvas.drawText("CashBook", margin, 58f, paint)
        canvas.drawText("Generated: $generatedDate", margin, 74f, paint)

        paintR.color = Color.parseColor("#90CAF9"); paintR.textSize = 13f; paintR.isFakeBoldText = true
        canvas.drawText(billId, pageWidth - margin, 44f, paintR)
        paintR.color = Color.WHITE; paintR.textSize = 10f; paintR.isFakeBoldText = false
        canvas.drawText("Date: $billDate", pageWidth - margin, 62f, paintR)

        y = 110f

        // ── Customer card ─────────────────────────────────────────
        paint.color = Color.parseColor("#F5F5F5")
        canvas.drawRoundRect(android.graphics.RectF(margin, y, pageWidth - margin, y + 100f), 8f, 8f, paint)
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(margin, y, margin + 5f, y + 100f, paint)

        paint.color = Color.parseColor("#9E9E9E"); paint.textSize = 9f; paint.isFakeBoldText = false
        canvas.drawText("BILL TO", margin + 14f, y + 16f, paint)

        paint.color = Color.parseColor("#1A237E"); paint.textSize = 14f; paint.isFakeBoldText = true
        canvas.drawText(firmName.ifEmpty { customerName }, margin + 14f, y + 34f, paint)

        paint.color = Color.parseColor("#424242"); paint.textSize = 10f; paint.isFakeBoldText = false
        var custY = y + 50f
        if (customerName.isNotEmpty() && firmName.isNotEmpty()) {
            canvas.drawText(customerName, margin + 14f, custY, paint); custY += 14f
        }
        if (phone.isNotEmpty())   { canvas.drawText("📞 $phone",   margin + 14f, custY, paint); custY += 14f }
        if (address.isNotEmpty()) { canvas.drawText("📍 $address", margin + 14f, custY, paint); custY += 14f }
        if (gstin.isNotEmpty())   { canvas.drawText("GSTIN: $gstin", margin + 14f, custY, paint) }

        y += 116f

        // ── Items table ───────────────────────────────────────────
        val c1 = margin + 4f
        val c2 = margin + 240f
        val c3 = margin + 320f
        val c4 = pageWidth - margin

        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(margin, y, pageWidth - margin, y + 26f, paint)

        paint.color = Color.WHITE; paint.textSize = 10f; paint.isFakeBoldText = true
        canvas.drawText("Product",  c1, y + 17f, paint)
        canvas.drawText("Qty",      c2, y + 17f, paint)
        canvas.drawText("Rate",     c3, y + 17f, paint)
        paintR.color = Color.WHITE; paintR.textSize = 10f; paintR.isFakeBoldText = true
        canvas.drawText("Amount",   c4, y + 17f, paintR)

        y += 30f

        items.forEachIndexed { i, item ->
            val productName = item["productName"] as? String ?: ""
            val units       = item["units"]       as? Double ?: 0.0
            val rate        = item["rate"]        as? Double ?: 0.0
            val amount      = item["amount"]      as? Double ?: 0.0

            if (i % 2 == 0) {
                paint.color = Color.parseColor("#F9F9FF")
                canvas.drawRect(margin, y - 4f, pageWidth - margin, y + 18f, paint)
            }

            paint.color = Color.parseColor("#212121"); paint.textSize = 10f; paint.isFakeBoldText = false
            canvas.drawText(productName,        c1, y + 11f, paint)
            canvas.drawText(formatQty(units),   c2, y + 11f, paint)
            canvas.drawText(formatAmount(rate), c3, y + 11f, paint)
            paintR.color = Color.parseColor("#1B5E20"); paintR.isFakeBoldText = true
            canvas.drawText(formatAmount(amount), c4, y + 11f, paintR)

            paint.color = Color.parseColor("#E8EAF6"); paint.strokeWidth = 0.5f
            canvas.drawLine(margin, y + 19f, pageWidth - margin, y + 19f, paint)

            y += 24f
        }

        y += 8f

        // ── Total band ────────────────────────────────────────────
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(margin, y, pageWidth - margin, y + 36f, paint)
        paint.color = Color.WHITE; paint.textSize = 13f; paint.isFakeBoldText = true
        canvas.drawText("Grand Total", c1, y + 24f, paint)
        paintR.color = Color.parseColor("#A5D6A7"); paintR.textSize = 15f; paintR.isFakeBoldText = true
        canvas.drawText(formatAmount(billAmount), c4, y + 24f, paintR)

        y += 50f

        // ── Footer ────────────────────────────────────────────────
        paint.color = Color.parseColor("#E0E0E0")
        canvas.drawLine(margin, y, pageWidth - margin, y, paint)
        y += 10f

        paintC.color = Color.parseColor("#9E9E9E"); paintC.textSize = 9f
        canvas.drawText("Thank you for your business!", pageWidth / 2f, y + 12f, paintC)
        paintC.textSize = 8f
        canvas.drawText("Generated by CashBook", pageWidth / 2f, y + 26f, paintC)

        pdf.finishPage(page)

        val file = File(cacheDir, "invoice_${billId}.pdf")
        pdf.writeTo(FileOutputStream(file))
        pdf.close()
        return file
    }

    // ── Share ─────────────────────────────────────────────────────
    private fun generateAndShare() {
        val file = buildPdf()
        val uri  = FileProvider.getUriForFile(this, "${packageName}.provider", file)

        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Invoice — $billId")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share Invoice"))
    }

    // ── Print ─────────────────────────────────────────────────────
    private fun printBill() {
        val file = buildPdf()
        val uri  = FileProvider.getUriForFile(this, "${packageName}.provider", file)

        // Build HTML from bill data for WebView-based printing
        val html = buildHtml()
        val webView = WebView(this)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = getSystemService(PRINT_SERVICE) as PrintManager
                val jobName      = "Invoice_$billId"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)

                printManager.print(
                    jobName,
                    printAdapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
            }
        }

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun buildHtml(): String {
        val itemRows = items.joinToString("") { item ->
            val productName = item["productName"] as? String ?: ""
            val units       = item["units"]       as? Double ?: 0.0
            val rate        = item["rate"]        as? Double ?: 0.0
            val amount      = item["amount"]      as? Double ?: 0.0
            """
            <tr>
                <td>$productName</td>
                <td style="text-align:center">${formatQty(units)}</td>
                <td style="text-align:right">${formatAmount(rate)}</td>
                <td style="text-align:right"><b>${formatAmount(amount)}</b></td>
            </tr>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="UTF-8">
        <style>
            body { font-family: Arial, sans-serif; margin: 32px; color: #212121; }
            .header { background: #1A237E; color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; }
            .header h1 { margin: 0; font-size: 24px; }
            .header p  { margin: 4px 0 0; font-size: 13px; opacity: 0.8; }
            .bill-meta { display: flex; justify-content: space-between; margin-bottom: 20px; }
            .customer-box { background: #F5F5F5; padding: 16px; border-left: 4px solid #1A237E; border-radius: 4px; }
            .customer-box h3 { margin: 0 0 6px; color: #1A237E; }
            .customer-box p  { margin: 2px 0; font-size: 13px; color: #555; }
            table { width: 100%; border-collapse: collapse; margin-top: 20px; }
            thead tr { background: #1A237E; color: white; }
            thead th { padding: 10px 8px; text-align: left; font-size: 12px; }
            tbody tr:nth-child(even) { background: #F9F9FF; }
            tbody td { padding: 9px 8px; font-size: 13px; border-bottom: 1px solid #E8EAF6; }
            .total-row { background: #1A237E !important; color: white; }
            .total-row td { padding: 12px 8px; font-size: 14px; font-weight: bold; }
            .amount-green { color: #1B5E20; font-weight: bold; }
            .footer { text-align: center; margin-top: 40px; color: #9E9E9E; font-size: 12px; }
        </style>
        </head>
        <body>
            <div class="header">
                <h1>SALES INVOICE</h1>
                <p>CashBook &nbsp;|&nbsp; $billId &nbsp;|&nbsp; $billDate</p>
            </div>

            <div class="customer-box">
                <h3>${firmName.ifEmpty { customerName }}</h3>
                ${if (firmName.isNotEmpty() && customerName.isNotEmpty()) "<p>$customerName</p>" else ""}
                ${if (phone.isNotEmpty())   "<p>📞 $phone</p>"   else ""}
                ${if (address.isNotEmpty()) "<p>📍 $address</p>" else ""}
                ${if (gstin.isNotEmpty())   "<p>GSTIN: $gstin</p>" else ""}
            </div>

            <table>
                <thead>
                    <tr>
                        <th>Product</th>
                        <th style="text-align:center">Qty</th>
                        <th style="text-align:right">Rate</th>
                        <th style="text-align:right">Amount</th>
                    </tr>
                </thead>
                <tbody>
                    $itemRows
                    <tr class="total-row">
                        <td colspan="3">Grand Total</td>
                        <td style="text-align:right">${formatAmount(billAmount)}</td>
                    </tr>
                </tbody>
            </table>

            <div class="footer">
                <p>Thank you for your business!</p>
                <p>Generated by CashBook</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    // ── Helpers ───────────────────────────────────────────────────
    private fun formatAmount(amount: Double): String {
        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
        fmt.minimumFractionDigits = 2; fmt.maximumFractionDigits = 2
        return "₹${fmt.format(amount)}"
    }

    private fun formatQty(qty: Double) =
        if (qty % 1.0 == 0.0) qty.toInt().toString() else qty.toString()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_BILL_ID = "BILL_ID"

        fun newIntent(context: Context, billId: String) =
            Intent(context, SalesBillPreviewActivity::class.java).apply {
                putExtra(EXTRA_BILL_ID, billId)
            }
    }
}