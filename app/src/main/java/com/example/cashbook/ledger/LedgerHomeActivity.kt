package com.example.cashbook.ledger

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cashbook.R
import com.example.cashbook.ledger.bank.BankLedgerActivity
import com.example.cashbook.ledger.cash.CashLedgerActivity
import com.example.cashbook.ledger.customers.CustomerPageActivity
import com.example.cashbook.ledger.expense.ExpenseLedgerActivity
import com.example.cashbook.ledger.suppliers.SupplierPageActivity
import com.google.android.material.card.MaterialCardView

class LedgerHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ledger_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<MaterialCardView>(R.id.tileCustomers).setOnClickListener {
            startActivity(android.content.Intent(this, CustomerPageActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.tileSuppliers).setOnClickListener {
            startActivity(android.content.Intent(this, SupplierPageActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.tileCash).setOnClickListener {
            startActivity(android.content.Intent(this, CashLedgerActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.tileBank).setOnClickListener {
            startActivity(android.content.Intent(this, BankLedgerActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.tileExpenses).setOnClickListener {
            startActivity(android.content.Intent(this, ExpenseLedgerActivity::class.java))
        }
    }
}