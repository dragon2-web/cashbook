package com.example.cashbook.sales_bill

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cashbook.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.firestore.SetOptions

class SalesBillActivity : AppCompatActivity() {

    // ── Views ─────────────────────────────────────────────────────
    private lateinit var customerInput:    AutoCompleteTextView
    private lateinit var dateInput:        TextInputEditText
    private lateinit var productInput:     AutoCompleteTextView
    private lateinit var unitsInput:       TextInputEditText
    private lateinit var rateInput:        TextInputEditText
    private lateinit var addItemBtn:       MaterialButton
    private lateinit var itemsRecycler:    RecyclerView
    private lateinit var tvBillTotal:      TextView
    private lateinit var saveBillBtn:      MaterialButton

    // ── State ─────────────────────────────────────────────────────
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val customerMap  = mutableMapOf<String, String>() // name → uid
    private val productMap   = mutableMapOf<String, Double>() // name → price
    private val billItems    = mutableListOf<BillItem>()

    private var selectedCustomerId   = ""
    private var selectedCustomerName = ""
    private var billTotal            = 0.0

    private lateinit var itemAdapter: BillItemAdapter

    // ── Lifecycle ─────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_bill)

        bindViews()
        setupToolbar()
        setupDate()
        setupItemsRecycler()
        loadCustomers()
        loadProducts()

        addItemBtn.setOnClickListener  { addItem() }
        saveBillBtn.setOnClickListener { saveBill() }
    }

    // ── Setup ─────────────────────────────────────────────────────
    private fun bindViews() {
        customerInput = findViewById(R.id.customerInput)
        dateInput     = findViewById(R.id.dateInput)
        productInput  = findViewById(R.id.productInput)
        unitsInput    = findViewById(R.id.unitsInput)
        rateInput     = findViewById(R.id.rateInput)
        addItemBtn    = findViewById(R.id.addItemBtn)
        itemsRecycler = findViewById(R.id.itemsRecycler)
        tvBillTotal   = findViewById(R.id.tvBillTotal)
        saveBillBtn   = findViewById(R.id.saveBillBtn)
    }

    private fun setupToolbar() {
        findViewById<TextView>(R.id.toolbar).text = "New Sales Bill"
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupDate() {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        dateInput.setText(today)
    }

    private fun setupItemsRecycler() {
        itemAdapter = BillItemAdapter(billItems) { position ->
            billItems.removeAt(position)
            itemAdapter.notifyItemRemoved(position)
            recalculateTotal()
        }
        itemsRecycler.layoutManager = LinearLayoutManager(this)
        itemsRecycler.adapter = itemAdapter
    }

    // ── Load Data ─────────────────────────────────────────────────
    private fun loadCustomers() {
        db.collection("customers")
            .orderBy("name")
            .get()
            .addOnSuccessListener { result ->
                customerMap.clear()
                result.forEach { doc ->
                    val name = doc.getString("name") ?: return@forEach
                    customerMap[name] = doc.id
                }
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    customerMap.keys.toList()
                )
                customerInput.setAdapter(adapter)
                customerInput.setOnItemClickListener { _, _, position, _ ->
                    selectedCustomerName = adapter.getItem(position) ?: ""
                    selectedCustomerId   = customerMap[selectedCustomerName] ?: ""
                }
            }
    }

    private fun loadProducts() {
        db.collection("products")
            .orderBy("name")
            .get()
            .addOnSuccessListener { result ->
                productMap.clear()
                result.forEach { doc ->
                    val name  = doc.getString("name")  ?: return@forEach
                    val price = doc.getDouble("price") ?: 0.0
                    productMap[name] = price
                }
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    productMap.keys.toList()
                )
                productInput.setAdapter(adapter)
                productInput.setOnItemClickListener { _, _, position, _ ->
                    val selectedProduct = adapter.getItem(position) ?: ""
                    val price = productMap[selectedProduct] ?: 0.0
                    rateInput.setText(price.toString())
                }
            }
    }

    // ── Add Item ──────────────────────────────────────────────────
    private fun addItem() {
        val productName = productInput.text.toString().trim().uppercase()
        val units       = unitsInput.text.toString().trim().toDoubleOrNull()
        val rate        = rateInput.text.toString().trim().toDoubleOrNull()

        if (productName.isEmpty()) {
            productInput.error = "Select a product"
            return
        }
        if (units == null || units <= 0) {
            unitsInput.error = "Enter valid units"
            return
        }
        if (rate == null || rate <= 0) {
            rateInput.error = "Enter valid rate"
            return
        }

        val amount = units * rate
        billItems.add(BillItem(productName, units, rate, amount))
        itemAdapter.notifyItemInserted(billItems.size - 1)

        // Clear inputs
        productInput.setText("")
        unitsInput.setText("")
        rateInput.setText("")

        recalculateTotal()
    }

    private fun recalculateTotal() {
        billTotal = billItems.sumOf { it.amount }
        tvBillTotal.text = "Total: ${formatAmount(billTotal)}"
    }

    // ── Save Bill ─────────────────────────────────────────────────
    // ── ID Generation ─────────────────────────────────────────────────
    private fun generateIds(
        date: String,
        onReady: (billId: String, txnId: String, dateTag: String) -> Unit
    ) {
        val parts   = date.split("/")
        val dateTag = "${parts[2]}-${parts[1]}-${parts[0]}"  // "2026-03-13"

        val counterRef = db.collection("counters").document(dateTag)

        db.runTransaction { tx ->
            val snap     = tx.get(counterRef)
            val sbCount  = (snap.getLong("sbCount")  ?: 0L) + 1L
            val txnCount = (snap.getLong("txnCount") ?: 0L) + 1L

            tx.set(
                counterRef,
                mapOf(
                    "sbCount"  to sbCount,
                    "txnCount" to txnCount
                ),
                SetOptions.merge()
            )

            Triple(
                "SB-$dateTag-${sbCount.toString().padStart(4, '0')}",
                "TXN-$dateTag-${txnCount.toString().padStart(4, '0')}",
                dateTag
            )

        }.addOnSuccessListener { (billId, txnId, dateTag) ->
            onReady(billId, txnId, dateTag)
        }.addOnFailureListener { e ->
            saveBillBtn.isEnabled = true
            saveBillBtn.text      = "Save Bill"
            Toast.makeText(this, "Failed to generate IDs: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Entry Point ───────────────────────────────────────────────────
    private fun saveBill() {
        if (selectedCustomerId.isEmpty()) {
            customerInput.error = "Select a customer"
            return
        }
        if (billItems.isEmpty()) {
            Toast.makeText(this, "Add at least one item", Toast.LENGTH_SHORT).show()
            return
        }
        val date = dateInput.text.toString().trim()
        if (date.isEmpty()) {
            dateInput.error = "Enter date"
            return
        }

        saveBillBtn.isEnabled = false
        saveBillBtn.text      = "Saving..."

        generateIds(date) { billId, txnId, dateTag ->
            proceedWithSave(billId, txnId, date, dateTag)
        }
    }

    // ── Main Save ─────────────────────────────────────────────────────
    private fun proceedWithSave(
        billId:  String,
        txnId:   String,
        date:    String,
        dateTag: String
    ) {
        val now            = System.currentTimeMillis()
        val billRef        = db.collection("salesBills").document(billId)
        val txnRef         = db.collection("transactions").document(txnId)
        val customerRef    = db.collection("customers").document(selectedCustomerId)
        val customerTxnRef = customerRef.collection("transactions").document(txnId)
        val firmRef        = db.collection("firm").document("main")

        val batch = db.batch()

        // ── Step 1: Save Sales Bill ───────────────────────────────────
        batch.set(billRef, mapOf(
            "billId"       to billId,
            "customerId"   to selectedCustomerId,
            "customerName" to selectedCustomerName,
            "date"         to date,
            "dateTag"      to dateTag,
            "billAmount"   to billTotal,
            "status"       to "unpaid",
            "createdAt"    to now
        ))

        // ── Step 2: Save bill items in same batch ─────────────────────
        billItems.forEach { item ->
            val itemRef = billRef.collection("items").document()
            batch.set(itemRef, mapOf(
                "productName" to item.productName,
                "units"       to item.units,
                "rate"        to item.rate,
                "amount"      to item.amount
            ))
        }

        // ── Step 3 & 4: Transaction payload ───────────────────────────
        val txnData = mapOf(
            "txnId"       to txnId,
            "txnType"     to "sales",
            "salesId"     to billId,
            "amount"      to billTotal,
            "type"        to "debit",
            "description" to "Sales Bill",
            "date"        to date,
            "dateTag"     to dateTag,
            "createdAt"   to now
        )

        // ── Step 3: Global transactions collection ────────────────────
        batch.set(txnRef, txnData)

        // ── Step 4: Customer subcollection ────────────────────────────
        batch.set(customerTxnRef, txnData)

        // ── Step 5: Update customer closingBalance ────────────────────
        batch.update(customerRef, mapOf(
            "closingBalance" to FieldValue.increment(billTotal),
            "lastTxnId"      to txnId,
            "lastUpdatedAt"  to now
        ))

        // ── Step 6: Update firm closingBalance + receivable ───────────
        batch.update(firmRef, mapOf(
            "closingBalance" to FieldValue.increment(billTotal),
            "receivable"     to FieldValue.increment(billTotal),
            "lastUpdatedAt"  to now
        ))

        // ── Commit all at once ────────────────────────────────────────
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Bill saved successfully ✓", Toast.LENGTH_SHORT).show()
                startActivity(SalesBillPreviewActivity.newIntent(this, billId))
                finish()
            }
            .addOnFailureListener { e ->
                saveBillBtn.isEnabled = true
                saveBillBtn.text      = "Save Bill"
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
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
            Intent(context, SalesBillActivity::class.java)
    }
}