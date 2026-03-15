package com.example.cashbook.transactions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.cashbook.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class AddTransactionActivity : AppCompatActivity() {

    // ── Views ─────────────────────────────────────────────────────
    private lateinit var typeSelector:   RadioGroup
    private lateinit var partyLabel:     TextView
    private lateinit var partyInput:     AutoCompleteTextView
    private lateinit var amountInput:    TextInputEditText
    private lateinit var dateInput:      TextInputEditText
    private lateinit var modeLabel:      TextView
    private lateinit var modeGroup:      RadioGroup
    private lateinit var categoryLayout: View
    private lateinit var categoryInput:  AutoCompleteTextView
    private lateinit var noteInput:      TextInputEditText
    private lateinit var saveBtn:        MaterialButton

    // ── State ─────────────────────────────────────────────────────
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val customerMap = mutableMapOf<String, String>()
    private val supplierMap = mutableMapOf<String, String>()

    private var selectedPartyId   = ""
    private var selectedPartyName = ""
    private var currentType       = TxnType.PAYMENT_RECEIVED

    enum class TxnType { PAYMENT_RECEIVED, PAYMENT_MADE, EXPENSE }

    // ── Lifecycle ─────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        bindViews()
        setupToolbar()
        setupDate()
        setupTypeSelector()
        loadCustomers()
        loadSuppliers()

        saveBtn.setOnClickListener { saveTransaction() }
    }

    // ── Bind ──────────────────────────────────────────────────────
    private fun bindViews() {
        typeSelector   = findViewById(R.id.typeSelector)
        partyLabel     = findViewById(R.id.partyLabel)
        partyInput     = findViewById(R.id.partyInput)
        amountInput    = findViewById(R.id.amountInput)
        dateInput      = findViewById(R.id.dateInput)
        modeLabel      = findViewById(R.id.modeLabel)
        modeGroup      = findViewById(R.id.modeGroup)
        categoryLayout = findViewById(R.id.categoryLayout)
        categoryInput  = findViewById(R.id.categoryInput)
        noteInput      = findViewById(R.id.noteInput)
        saveBtn        = findViewById(R.id.saveBtn)
    }

    private fun setupToolbar() {
        findViewById<TextView>(R.id.toolbar).text = "Add Transaction"
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupDate() {
        dateInput.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
    }

    // ── Type Selector ─────────────────────────────────────────────
    private fun setupTypeSelector() {
        typeSelector.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPaymentReceived -> switchType(TxnType.PAYMENT_RECEIVED)
                R.id.rbPaymentMade     -> switchType(TxnType.PAYMENT_MADE)
                R.id.rbExpense         -> switchType(TxnType.EXPENSE)
            }
        }
        switchType(TxnType.PAYMENT_RECEIVED)
    }

    private fun switchType(type: TxnType) {
        currentType       = type
        selectedPartyId   = ""
        selectedPartyName = ""
        partyInput.setText("")

        when (type) {
            TxnType.PAYMENT_RECEIVED -> {
                partyLabel.text           = "Customer *"
                partyLabel.visibility     = View.VISIBLE
                partyInput.visibility     = View.VISIBLE
                modeLabel.visibility      = View.VISIBLE
                modeGroup.visibility      = View.VISIBLE
                categoryLayout.visibility = View.GONE
                setPartyAdapter(customerMap)
            }
            TxnType.PAYMENT_MADE -> {
                partyLabel.text           = "Supplier *"
                partyLabel.visibility     = View.VISIBLE
                partyInput.visibility     = View.VISIBLE
                modeLabel.visibility      = View.VISIBLE
                modeGroup.visibility      = View.VISIBLE
                categoryLayout.visibility = View.GONE
                setPartyAdapter(supplierMap)
            }
            TxnType.EXPENSE -> {
                partyLabel.visibility     = View.GONE
                partyInput.visibility     = View.GONE
                modeLabel.visibility      = View.VISIBLE
                modeGroup.visibility      = View.VISIBLE
                categoryLayout.visibility = View.VISIBLE
                setupCategoryAdapter()
            }
        }
    }

    // ── Load Data ─────────────────────────────────────────────────
    private fun loadCustomers() {
        db.collection("customers").orderBy("name").get()
            .addOnSuccessListener { result ->
                customerMap.clear()
                result.forEach { doc ->
                    val name = doc.getString("name") ?: return@forEach
                    customerMap[name] = doc.id
                }
                if (currentType == TxnType.PAYMENT_RECEIVED) setPartyAdapter(customerMap)
            }
    }

    private fun loadSuppliers() {
        db.collection("suppliers").orderBy("name").get()
            .addOnSuccessListener { result ->
                supplierMap.clear()
                result.forEach { doc ->
                    val name = doc.getString("name") ?: return@forEach
                    supplierMap[name] = doc.id
                }
            }
    }

    private fun setPartyAdapter(map: MutableMap<String, String>) {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            map.keys.toList()
        )
        partyInput.setAdapter(adapter)
        partyInput.setOnItemClickListener { _, _, position, _ ->
            selectedPartyName = adapter.getItem(position) ?: ""
            selectedPartyId   = map[selectedPartyName]   ?: ""
        }
    }

    private fun setupCategoryAdapter() {
        val categories = listOf(
            "Rent",
            "Salary",
            "Wages",
            "Electricity",
            "Transport",
            "Miscellaneous"
        )
        categoryInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        )
    }

    // ── Payment Mode ──────────────────────────────────────────────
    private fun getSelectedMode(): String {
        return when (modeGroup.checkedRadioButtonId) {
            R.id.rbBank -> "bank"
            else        -> "cash"
        }
    }

    // ── ID Generation ─────────────────────────────────────────────
    private fun generateTxnId(
        date:       String,
        prefix:     String,
        counterKey: String,
        onReady:    (txnId: String, dateTag: String) -> Unit
    ) {
        val parts      = date.split("/")
        val dateTag    = "${parts[2]}-${parts[1]}-${parts[0]}"
        val counterRef = db.collection("counters").document(dateTag)

        db.runTransaction { tx ->
            val snap  = tx.get(counterRef)
            val count = (snap.getLong(counterKey) ?: 0L) + 1L

            tx.set(
                counterRef,
                mapOf(counterKey to count),
                SetOptions.merge()
            )

            Pair(
                "$prefix-$dateTag-${count.toString().padStart(4, '0')}",
                dateTag
            )

        }.addOnSuccessListener { (txnId, dateTag) ->
            onReady(txnId, dateTag)
        }.addOnFailureListener { e ->
            saveBtn.isEnabled = true
            saveBtn.text      = "Save"
            Toast.makeText(this, "Failed to generate ID: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Validation + Entry Point ──────────────────────────────────
    private fun saveTransaction() {
        val amount = amountInput.text.toString().trim().toDoubleOrNull()
        val date   = dateInput.text.toString().trim()
        val note   = noteInput.text.toString().trim()
        val mode   = getSelectedMode()

        if (amount == null || amount <= 0) {
            amountInput.error = "Enter valid amount"
            return
        }
        if (date.isEmpty()) {
            dateInput.error = "Enter date"
            return
        }

        when (currentType) {
            TxnType.PAYMENT_RECEIVED -> {
                if (selectedPartyId.isEmpty()) {
                    partyInput.error = "Select a customer"
                    return
                }
            }
            TxnType.PAYMENT_MADE -> {
                if (selectedPartyId.isEmpty()) {
                    partyInput.error = "Select a supplier"
                    return
                }
            }
            TxnType.EXPENSE -> {
                if (categoryInput.text.toString().trim().isEmpty()) {
                    categoryInput.error = "Select a category"
                    return
                }
            }
        }

        saveBtn.isEnabled = false
        saveBtn.text      = "Saving..."

        when (currentType) {
            TxnType.PAYMENT_RECEIVED ->
                generateTxnId(date, "TXN", "txnCount") { txnId, dateTag ->
                    savePaymentReceived(txnId, dateTag, date, amount, mode, note)
                }
            TxnType.PAYMENT_MADE ->
                generateTxnId(date, "TXN", "txnCount") { txnId, dateTag ->
                    savePaymentMade(txnId, dateTag, date, amount, mode, note)
                }
            TxnType.EXPENSE ->
                generateTxnId(date, "EXP", "expCount") { txnId, dateTag ->
                    saveExpense(txnId, dateTag, date, amount, mode, note)
                }
        }
    }

    // ── Payment Received ──────────────────────────────────────────────
    private fun savePaymentReceived(
        txnId:   String,
        dateTag: String,
        date:    String,
        amount:  Double,
        mode:    String,
        note:    String
    ) {
        val now            = System.currentTimeMillis()
        val txnRef         = db.collection("transactions").document(txnId)
        val customerRef    = db.collection("customers").document(selectedPartyId)
        val customerTxnRef = customerRef.collection("transactions").document(txnId)
        val firmRef        = db.collection("firm").document("main")

        // ── Mode collection ref ───────────────────────────────────────
        val modeTxnRef = if (mode == "bank") {
            db.collection("bank").document("main")
                .collection("transactions").document(txnId)
        } else {
            db.collection("cash").document("main")
                .collection("transactions").document(txnId)
        }

        val batch = db.batch()

        val txnData = mapOf(
            "txnId"       to txnId,
            "txnType"     to "payment_received",
            "amount"      to amount,
            "type"        to "credit",
            "description" to "Payment Received",
            "partyName"   to selectedPartyName,
            "mode"        to mode,
            "note"        to note,
            "date"        to date,
            "dateTag"     to dateTag,
            "createdAt"   to now
        )

        // Global txn
        batch.set(txnRef, txnData)

        // Customer subcollection
        batch.set(customerTxnRef, txnData)

        // Cash / Bank subcollection
        batch.set(modeTxnRef, txnData)

        // Customer: closingBalance--
        batch.update(customerRef, mapOf(
            "closingBalance" to FieldValue.increment(-amount),
            "lastTxnId"      to txnId,
            "lastUpdatedAt"  to now
        ))

        // Firm: receivable--, cash/bank++
        val firmUpdates = mutableMapOf<String, Any>(
            "receivable"    to FieldValue.increment(-amount),
            "lastUpdatedAt" to now
        )
        if (mode == "bank") {
            firmUpdates["bank"] = FieldValue.increment(amount)
        } else {
            firmUpdates["cash"] = FieldValue.increment(amount)
        }
        batch.update(firmRef, firmUpdates)

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Payment received recorded ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                saveBtn.isEnabled = true
                saveBtn.text      = "Save"
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ── Payment Made ──────────────────────────────────────────────────
    private fun savePaymentMade(
        txnId:   String,
        dateTag: String,
        date:    String,
        amount:  Double,
        mode:    String,
        note:    String
    ) {
        val now            = System.currentTimeMillis()
        val txnRef         = db.collection("transactions").document(txnId)
        val supplierRef    = db.collection("suppliers").document(selectedPartyId)
        val supplierTxnRef = supplierRef.collection("transactions").document(txnId)
        val firmRef        = db.collection("firm").document("main")

        // ── Mode collection ref ───────────────────────────────────────
        val modeTxnRef = if (mode == "bank") {
            db.collection("bank").document("main")
                .collection("transactions").document(txnId)
        } else {
            db.collection("cash").document("main")
                .collection("transactions").document(txnId)
        }

        val batch = db.batch()

        val txnData = mapOf(
            "txnId"       to txnId,
            "txnType"     to "payment_made",
            "amount"      to amount,
            "type"        to "debit",
            "description" to "Payment Made",
            "partyName"   to selectedPartyName,
            "mode"        to mode,
            "note"        to note,
            "date"        to date,
            "dateTag"     to dateTag,
            "createdAt"   to now
        )

        // Global txn
        batch.set(txnRef, txnData)

        // Supplier subcollection
        batch.set(supplierTxnRef, txnData)

        // Cash / Bank subcollection
        batch.set(modeTxnRef, txnData)

        // Supplier: closingBalance--
        batch.update(supplierRef, mapOf(
            "closingBalance" to FieldValue.increment(-amount),
            "lastTxnId"      to txnId,
            "lastUpdatedAt"  to now
        ))

        // Firm: payable--, cash/bank--
        val firmUpdates = mutableMapOf<String, Any>(
            "payable"       to FieldValue.increment(-amount),
            "lastUpdatedAt" to now
        )
        if (mode == "bank") {
            firmUpdates["bank"] = FieldValue.increment(-amount)
        } else {
            firmUpdates["cash"] = FieldValue.increment(-amount)
        }
        batch.update(firmRef, firmUpdates)

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Payment made recorded ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                saveBtn.isEnabled = true
                saveBtn.text      = "Save"
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ── Expense ───────────────────────────────────────────────────────
    private fun saveExpense(
        txnId:   String,
        dateTag: String,
        date:    String,
        amount:  Double,
        mode:    String,
        note:    String
    ) {
        val now      = System.currentTimeMillis()
        val category = categoryInput.text.toString().trim()
        val txnRef   = db.collection("transactions").document(txnId)
        val expRef   = db.collection("expenses").document(txnId)      // ← expenses collection
        val firmRef  = db.collection("firm").document("main")

        // ── Mode collection ref ───────────────────────────────────────
        val modeTxnRef = if (mode == "bank") {
            db.collection("bank").document("main")
                .collection("transactions").document(txnId)
        } else {
            db.collection("cash").document("main")
                .collection("transactions").document(txnId)
        }

        val batch = db.batch()

        val txnData = mapOf(
            "txnId"       to txnId,
            "txnType"     to "expense",
            "amount"      to amount,
            "type"        to "debit",
            "description" to category,
            "mode"        to mode,
            "note"        to note,
            "date"        to date,
            "dateTag"     to dateTag,
            "createdAt"   to now
        )

        // Global txn
        batch.set(txnRef, txnData)

        // Expenses collection
        batch.set(expRef, txnData)

        // Cash / Bank subcollection
        batch.set(modeTxnRef, txnData)

        // Firm: expenses++, closingBalance--, cash/bank--
        val firmUpdates = mutableMapOf<String, Any>(
            "expenses"       to FieldValue.increment(amount),
            "closingBalance" to FieldValue.increment(-amount),
            "lastUpdatedAt"  to now
        )
        if (mode == "bank") {
            firmUpdates["bank"] = FieldValue.increment(-amount)
        } else {
            firmUpdates["cash"] = FieldValue.increment(-amount)
        }
        batch.update(firmRef, firmUpdates)

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Expense recorded ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                saveBtn.isEnabled = true
                saveBtn.text      = "Save"
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    companion object {
        fun newIntent(context: Context) =
            Intent(context, AddTransactionActivity::class.java)
    }
}