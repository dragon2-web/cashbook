package com.example.cashbook.ledger

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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.*

class TransactionDetailActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private val db by lazy { FirebaseFirestore.getInstance() }

    // ── Held for PDF generation ───────────────────────────────────
    private var currentTxnId   = ""
    private var currentTxnType = ""

    // Sales/Purchase bill data
    private var billId         = ""
    private var billDate       = ""
    private var billAmount     = 0.0
    private var billStatus     = ""
    private var invoiceNo      = ""
    private var partyFirmName  = ""
    private var partyName      = ""
    private var partyPhone     = ""
    private var partyAddress   = ""
    private var partyGstin     = ""
    private var billItems      = mutableListOf<BillItemData>()

    // Generic txn data
    private var txnDate        = ""
    private var txnDescription = ""
    private var txnPartyName   = ""
    private var txnMode        = ""
    private var txnNote        = ""
    private var txnAmount      = 0.0
    private var txnType        = ""

    data class BillItemData(
        val productName: String,
        val units:       Double,
        val rate:        Double,
        val amount:      Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)

        container = findViewById(R.id.detailContainer)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<ImageButton>(R.id.btnShare).setOnClickListener {
            generateAndSharePdf()
        }

        currentTxnId = intent.getStringExtra(EXTRA_TXN_ID) ?: run {
            finish()
            return
        }

        loadTransaction(currentTxnId)
    }

    // ── Load Transaction ──────────────────────────────────────────
    private fun loadTransaction(txnId: String) {
        db.collection("transactions").document(txnId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Transaction not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                currentTxnType = doc.getString("txnType") ?: ""

                when (currentTxnType) {
                    "sales"    -> loadSalesBill(doc)
                    "purchase" -> loadPurchaseBill(doc)
                    else       -> showGenericDetail(doc)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load transaction", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    // ── Sales Bill ────────────────────────────────────────────────
    private fun loadSalesBill(txnDoc: DocumentSnapshot) {
        billId   = txnDoc.getString("salesId") ?: return
        billDate = txnDoc.getString("date")    ?: ""

        db.collection("salesBills").document(billId).get()
            .addOnSuccessListener { billDoc ->
                val customerId = billDoc.getString("customerId") ?: ""
                billAmount     = billDoc.getDouble("billAmount") ?: 0.0
                billStatus     = (billDoc.getString("status")   ?: "").uppercase()
                container.removeAllViews()

                addBadge(container, "SALES BILL", 0xFF1B5E20.toInt())
                addSectionTitle(container, "Bill Details")
                addField(container, "Transaction ID", txnDoc.getString("txnId") ?: "")
                addField(container, "Bill ID",        billId)
                addField(container, "Date",           billDate)
                addField(container, "Status",         billStatus)
                addField(container, "Bill Amount",    formatAmount(billAmount))
                addDivider(container)

                if (customerId.isNotEmpty()) {
                    db.collection("customers").document(customerId).get()
                        .addOnSuccessListener { customerDoc ->
                            partyFirmName = customerDoc.getString("firmName") ?: ""
                            partyName     = customerDoc.getString("name")     ?: ""
                            partyPhone    = customerDoc.getString("phone")    ?: ""
                            partyAddress  = customerDoc.getString("address")  ?: ""
                            partyGstin    = customerDoc.getString("gstin")    ?: ""

                            addSectionTitle(container, "Customer Details")
                            addField(container, "Firm Name", partyFirmName)
                            addField(container, "Name",      partyName)
                            addField(container, "Phone",     partyPhone)
                            addField(container, "Address",   partyAddress)
                            addField(container, "GSTIN",     partyGstin)
                            addDivider(container)
                            loadSalesBillItems(billId)
                        }
                } else {
                    loadSalesBillItems(billId)
                }
            }
    }

    private fun loadSalesBillItems(salesId: String) {
        addSectionTitle(container, "Items")
        addItemHeader(container)
        billItems.clear()

        db.collection("salesBills").document(salesId)
            .collection("items").get()
            .addOnSuccessListener { items ->
                var grandTotal = 0.0
                items.documents.forEach { item ->
                    val productName = item.getString("productName") ?: ""
                    val units       = item.getDouble("units")       ?: 0.0
                    val rate        = item.getDouble("rate")        ?: 0.0
                    val amount      = item.getDouble("amount")      ?: 0.0
                    grandTotal     += amount
                    billItems.add(BillItemData(productName, units, rate, amount))
                    addItemRow(productName, units, rate, amount)
                }
                addDivider(container)
                addTotalRow("Grand Total", grandTotal, 0xFF1B5E20.toInt())
            }
    }

    // ── Purchase Bill ─────────────────────────────────────────────
    private fun loadPurchaseBill(txnDoc: DocumentSnapshot) {
        billId   = txnDoc.getString("purchaseId") ?: return
        billDate = txnDoc.getString("date")       ?: ""

        db.collection("purchaseBills").document(billId).get()
            .addOnSuccessListener { billDoc ->
                val supplierId = billDoc.getString("supplierId") ?: ""
                billAmount     = billDoc.getDouble("billAmount") ?: 0.0
                billStatus     = (billDoc.getString("status")   ?: "").uppercase()
                invoiceNo      = billDoc.getString("invoiceNo") ?: ""
                container.removeAllViews()

                addBadge(container, "PURCHASE BILL", 0xFFC62828.toInt())
                addSectionTitle(container, "Bill Details")
                addField(container, "Transaction ID", txnDoc.getString("txnId") ?: "")
                addField(container, "Bill ID",        billId)
                addField(container, "Invoice No",     invoiceNo)
                addField(container, "Date",           billDate)
                addField(container, "Status",         billStatus)
                addField(container, "Bill Amount",    formatAmount(billAmount))
                addDivider(container)

                if (supplierId.isNotEmpty()) {
                    db.collection("suppliers").document(supplierId).get()
                        .addOnSuccessListener { supplierDoc ->
                            partyFirmName = supplierDoc.getString("firmName") ?: ""
                            partyName     = supplierDoc.getString("name")     ?: ""
                            partyPhone    = supplierDoc.getString("phone")    ?: ""
                            partyAddress  = supplierDoc.getString("address")  ?: ""
                            partyGstin    = supplierDoc.getString("gstin")    ?: ""

                            addSectionTitle(container, "Supplier Details")
                            addField(container, "Firm Name", partyFirmName)
                            addField(container, "Name",      partyName)
                            addField(container, "Phone",     partyPhone)
                            addField(container, "Address",   partyAddress)
                            addField(container, "GSTIN",     partyGstin)
                            addDivider(container)
                            loadPurchaseBillItems(billId)
                        }
                } else {
                    loadPurchaseBillItems(billId)
                }
            }
    }

    private fun loadPurchaseBillItems(purchaseId: String) {
        addSectionTitle(container, "Items")
        addItemHeader(container)
        billItems.clear()

        db.collection("purchaseBills").document(purchaseId)
            .collection("items").get()
            .addOnSuccessListener { items ->
                var grandTotal = 0.0
                items.documents.forEach { item ->
                    val productName = item.getString("productName") ?: ""
                    val units       = item.getDouble("units")       ?: 0.0
                    val rate        = item.getDouble("rate")        ?: 0.0
                    val amount      = item.getDouble("amount")      ?: 0.0
                    grandTotal     += amount
                    billItems.add(BillItemData(productName, units, rate, amount))
                    addItemRow(productName, units, rate, amount)
                }
                addDivider(container)
                addTotalRow("Grand Total", grandTotal, 0xFFC62828.toInt())
            }
    }

    // ── Generic ───────────────────────────────────────────────────
    private fun showGenericDetail(doc: DocumentSnapshot) {
        container.removeAllViews()

        txnType        = doc.getString("txnType")     ?: ""
        txnDate        = doc.getString("date")        ?: ""
        txnDescription = doc.getString("description") ?: ""
        txnPartyName   = doc.getString("partyName")   ?: ""
        txnMode        = doc.getString("mode")        ?: ""
        txnNote        = doc.getString("note")        ?: ""
        txnAmount      = doc.getDouble("amount")      ?: 0.0

        val color = when (txnType) {
            "payment_received" -> 0xFF1B5E20.toInt()
            "payment_made"     -> 0xFFC62828.toInt()
            "expense"          -> 0xFFE65100.toInt()
            else               -> 0xFF1A237E.toInt()
        }

        val label = when (txnType) {
            "payment_received" -> "PAYMENT RECEIVED"
            "payment_made"     -> "PAYMENT MADE"
            "expense"          -> "EXPENSE"
            else               -> txnType.uppercase()
        }

        addBadge(container, label, color)
        addSectionTitle(container, "Transaction Details")
        addField(container, "Transaction ID", currentTxnId)
        addField(container, "Date",           txnDate)
        addField(container, "Description",    txnDescription)
        if (txnPartyName.isNotEmpty()) addField(container, "Party", txnPartyName)
        if (txnMode.isNotEmpty())      addField(container, "Mode",  txnMode.uppercase())
        if (txnNote.isNotEmpty())      addField(container, "Note",  txnNote)
        addDivider(container)
        addTotalRow("Amount", txnAmount, color)
    }

    // ── PDF Generation ────────────────────────────────────────────
    private fun generateAndSharePdf() {
        val file = when (currentTxnType) {
            "sales"    -> buildBillPdf(isSales = true)
            "purchase" -> buildBillPdf(isSales = false)
            else       -> buildGenericPdf()
        }
        sharePdf(file)
    }

    private fun buildBillPdf(isSales: Boolean): File {
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

        val accentColor  = if (isSales) "#1B5E20" else "#C62828"
        val accentColorI = Color.parseColor(accentColor)
        val title        = if (isSales) "Sales Bill" else "Purchase Bill"
        val dateStr      = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        // ── Header ────────────────────────────────────────────────────
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 90f, paint)

        paint.color          = Color.WHITE
        paint.textSize       = 22f
        paint.isFakeBoldText = true
        canvas.drawText(title, margin, 38f, paint)

        paint.textSize       = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Generated: $dateStr", margin, 62f, paint)
        paintR.color         = Color.WHITE
        paintR.textSize      = 11f
        canvas.drawText("CashBook", pageWidth - margin, 62f, paintR)

        y = 110f

        // ── Party details card ────────────────────────────────────────
        paint.color = Color.parseColor("#F5F5F5")
        canvas.drawRoundRect(android.graphics.RectF(margin, y, pageWidth - margin, y + 110f), 8f, 8f, paint)
        paint.color = accentColorI
        canvas.drawRect(margin, y, margin + 5f, y + 110f, paint)

        paint.color          = accentColorI
        paint.textSize       = 15f
        paint.isFakeBoldText = true
        canvas.drawText(partyFirmName.ifEmpty { partyName }, margin + 16f, y + 26f, paint)

        val partyDetails = listOf(
            "Name"    to partyName,
            "Phone"   to partyPhone,
            "Address" to partyAddress,
            "GSTIN"   to partyGstin
        )
        var detailY = y + 46f
        partyDetails.forEach { (label, value) ->
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

        // ── Bill info row ─────────────────────────────────────────────
        paint.color = Color.parseColor("#E8EAF6")
        canvas.drawRect(margin, y, pageWidth - margin, y + 32f, paint)
        paint.color          = Color.parseColor("#1A237E")
        paint.textSize       = 10f
        paint.isFakeBoldText = true
        canvas.drawText("Bill ID: $billId",   margin + 8f,   y + 20f, paint)
        canvas.drawText("Date: $billDate",    margin + 200f, y + 20f, paint)
        if (!isSales && invoiceNo.isNotEmpty()) {
            canvas.drawText("Invoice: $invoiceNo", margin + 350f, y + 20f, paint)
        }
        canvas.drawText("Status: $billStatus", pageWidth - margin - 100f, y + 20f, paint)
        y += 40f

        // ── Column positions ──────────────────────────────────────────
        val col1 = margin + 6f
        val col2 = margin + 220f
        val col3 = margin + 320f
        val col4 = pageWidth - margin    // right edge

        // ── Items header ──────────────────────────────────────────────
        paint.color = accentColorI
        canvas.drawRect(margin, y, pageWidth - margin, y + 28f, paint)

        paint.color          = Color.WHITE
        paint.textSize       = 10f
        paint.isFakeBoldText = true
        canvas.drawText("Product", col1, y + 18f, paint)
        canvas.drawText("Qty",     col2, y + 18f, paint)
        canvas.drawText("Rate",    col3, y + 18f, paint)

        paintR.color          = Color.WHITE
        paintR.textSize       = 10f
        paintR.isFakeBoldText = true
        canvas.drawText("Amount", col4, y + 18f, paintR)
        y += 34f

        // ── Items ─────────────────────────────────────────────────────
        var grandTotal = 0.0
        billItems.forEachIndexed { index, item ->
            grandTotal += item.amount

            if (index % 2 == 0) {
                paint.color = Color.parseColor("#F9F9FF")
                canvas.drawRect(margin, y - 4f, pageWidth - margin, y + 18f, paint)
            }

            paint.color          = Color.parseColor("#424242")
            paint.textSize       = 9.5f
            paint.isFakeBoldText = false
            canvas.drawText(item.productName,        col1, y + 11f, paint)
            canvas.drawText(formatQty(item.units),   col2, y + 11f, paint)
            canvas.drawText(formatAmount(item.rate), col3, y + 11f, paint)

            paintR.color          = accentColorI
            paintR.textSize       = 9.5f
            paintR.isFakeBoldText = false
            canvas.drawText(formatAmount(item.amount), col4, y + 11f, paintR)

            paint.color       = Color.parseColor("#E8EAF6")
            paint.strokeWidth = 0.5f
            canvas.drawLine(margin, y + 19f, pageWidth - margin, y + 19f, paint)

            y += 26f

            if (y > pageHeight - 120f) {
                pdf.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page     = pdf.startPage(pageInfo)
                canvas   = page.canvas
                y        = margin
            }
        }

        // ── Grand total ───────────────────────────────────────────────
        y += 8f
        paint.color = accentColorI
        canvas.drawRect(margin, y, pageWidth - margin, y + 32f, paint)

        paint.color          = Color.WHITE
        paint.textSize       = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Grand Total", col1, y + 21f, paint)

        paintR.color          = Color.WHITE
        paintR.textSize       = 12f
        paintR.isFakeBoldText = true
        canvas.drawText(formatAmount(grandTotal), col4, y + 21f, paintR)

        // ── Footer ────────────────────────────────────────────────────
        paint.color          = Color.parseColor("#9E9E9E")
        paint.textSize       = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Generated by CashBook — $dateStr", margin, pageHeight - 20f, paint)

        pdf.finishPage(page)

        val file = File(cacheDir, "${billId}.pdf")
        pdf.writeTo(FileOutputStream(file))
        pdf.close()
        return file
    }

    private fun buildGenericPdf(): File {
        val pdf        = PdfDocument()
        val paint      = Paint()
        val pageWidth  = 595
        val pageHeight = 842
        val margin     = 40f

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page     = pdf.startPage(pageInfo)
        val canvas   = page.canvas
        var y        = margin

        val dateStr     = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        val accentColor = when (txnType) {
            "payment_received" -> "#1B5E20"
            "payment_made"     -> "#C62828"
            "expense"          -> "#E65100"
            else               -> "#1A237E"
        }
        val accentColorI = Color.parseColor(accentColor)

        val label = when (txnType) {
            "payment_received" -> "Payment Received"
            "payment_made"     -> "Payment Made"
            "expense"          -> "Expense"
            else               -> txnType.uppercase()
        }

        // ── Header band ───────────────────────────────────────────
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 90f, paint)

        paint.color          = Color.WHITE
        paint.textSize       = 22f
        paint.isFakeBoldText = true
        canvas.drawText(label, margin, 38f, paint)

        paint.textSize       = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Generated: $dateStr", margin, 62f, paint)
        canvas.drawText("CashBook", pageWidth - margin - 60f, 62f, paint)

        y = 110f

        // ── Details card ──────────────────────────────────────────
        paint.color = Color.parseColor("#F5F5F5")
        canvas.drawRoundRect(
            android.graphics.RectF(margin, y, pageWidth - margin, y + 160f),
            8f, 8f, paint
        )
        paint.color = accentColorI
        canvas.drawRect(margin, y, margin + 5f, y + 160f, paint)

        val fields = mutableListOf(
            "Transaction ID" to currentTxnId,
            "Date"           to txnDate,
            "Description"    to txnDescription
        )
        if (txnPartyName.isNotEmpty()) fields.add("Party" to txnPartyName)
        if (txnMode.isNotEmpty())      fields.add("Mode"  to txnMode.uppercase())
        if (txnNote.isNotEmpty())      fields.add("Note"  to txnNote)

        var fieldY = y + 22f
        fields.forEach { (label2, value) ->
            if (value.isNotEmpty()) {
                paint.color          = Color.parseColor("#757575")
                paint.textSize       = 11f
                paint.isFakeBoldText = false
                canvas.drawText("$label2:", margin + 16f, fieldY, paint)
                paint.color          = Color.parseColor("#212121")
                paint.isFakeBoldText = true
                canvas.drawText(value, margin + 120f, fieldY, paint)
                fieldY += 20f
            }
        }

        y += 170f

        // ── Amount row ────────────────────────────────────────────
        paint.color = accentColorI
        canvas.drawRect(margin, y, pageWidth - margin, y + 36f, paint)
        paint.color          = Color.WHITE
        paint.textSize       = 13f
        paint.isFakeBoldText = true
        canvas.drawText("Amount", margin + 8f, y + 23f, paint)
        canvas.drawText(formatAmount(txnAmount), pageWidth - margin - 10f, y + 23f, paint)

        // ── Footer ────────────────────────────────────────────────
        paint.color          = Color.parseColor("#9E9E9E")
        paint.textSize       = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Generated by CashBook — $dateStr", margin, pageHeight - 20f, paint)

        pdf.finishPage(page)

        val file = File(cacheDir, "txn_${currentTxnId}.pdf")
        pdf.writeTo(FileOutputStream(file))
        pdf.close()
        return file
    }

    // ── Share PDF ─────────────────────────────────────────────────
    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Transaction — $currentTxnId")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share Transaction PDF"))
    }

    // ── UI Builders ───────────────────────────────────────────────
    private fun addBadge(container: LinearLayout, label: String, color: Int) {
        container.addView(TextView(this).apply {
            text     = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(color)
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, 20) }
        })
    }

    private fun addSectionTitle(container: LinearLayout, title: String) {
        container.addView(TextView(this).apply {
            text     = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF90CAF9.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 8, 0, 10) }
        })
    }

    private fun addField(container: LinearLayout, label: String, value: String) {
        if (value.isEmpty()) return
        val row = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, 10) }
        }
        row.addView(TextView(this).apply {
            text     = label
            textSize = 12f
            setTextColor(0xFF90A4AE.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text     = value
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity  = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        })
        container.addView(row)
    }

    private fun addItemHeader(container: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 10, 8, 10)
            setBackgroundColor(0xFF16213E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 4, 0, 0) }
        }
        listOf(
            Triple("Product", 2f, Gravity.START),
            Triple("Qty",     1f, Gravity.START),
            Triple("Rate",    1f, Gravity.START),
            Triple("Amount",  1f, Gravity.END)
        ).forEach { (text, weight, grav) ->
            row.addView(TextView(this).apply {
                this.text = text
                textSize  = 11f
                typeface  = Typeface.DEFAULT_BOLD
                setTextColor(0xFF90CAF9.toInt())
                gravity   = grav
                setPadding(4, 0, 4, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            })
        }
        container.addView(row)
    }

    private fun addItemRow(productName: String, units: Double, rate: Double, amount: Double) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 12, 8, 12)
        }
        listOf(
            Triple(productName,          2f, Gravity.START),
            Triple(formatQty(units),     1f, Gravity.START),
            Triple(formatAmount(rate),   1f, Gravity.START),
            Triple(formatAmount(amount), 1f, Gravity.END)
        ).forEach { (text, weight, grav) ->
            row.addView(TextView(this).apply {
                this.text = text
                textSize  = 12f
                setTextColor(0xFFFFFFFF.toInt())
                gravity   = grav
                setPadding(4, 0, 4, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            })
        }
        container.addView(row)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(0xFF2A2A3E.toInt())
        })
    }

    private fun addDivider(container: LinearLayout) {
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 12, 0, 12) }
            setBackgroundColor(0xFF3A3A4E.toInt())
        })
    }

    private fun addTotalRow(label: String, amount: Double, color: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 4)
        }
        row.addView(TextView(this).apply {
            text     = label
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text     = formatAmount(amount)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
            gravity  = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        container.addView(row)
    }

    // ── Formatters ────────────────────────────────────────────────
    private fun formatAmount(amount: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return "₹${format.format(amount)}"
    }

    private fun formatQty(qty: Double): String {
        return if (qty % 1.0 == 0.0) qty.toInt().toString() else qty.toString()
    }

    companion object {
        const val EXTRA_TXN_ID = "TXN_ID"

        fun newIntent(context: Context, txnId: String) =
            Intent(context, TransactionDetailActivity::class.java).apply {
                putExtra(EXTRA_TXN_ID, txnId)
            }
    }
}