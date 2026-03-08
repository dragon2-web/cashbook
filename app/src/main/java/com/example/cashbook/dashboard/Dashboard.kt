package com.example.cashbook.dashboard

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.cashbook.R
import com.example.cashbook.ledger.LedgerHomeActivity
import com.example.cashbook.profile.ProfileActivity
import com.google.android.material.button.MaterialButton

class Dashboard : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Example values
        findViewById<TextView>(R.id.txtTotalBalance).text = "₹4,50,000"
        findViewById<TextView>(R.id.txtCash).text = "₹1,20,000"
        findViewById<TextView>(R.id.txtBank).text = "₹3,30,000"
        findViewById<TextView>(R.id.txtReceivable).text = "₹60,000"
        findViewById<TextView>(R.id.txtPayables).text = "₹30,000"
        findViewById<TextView>(R.id.txtExpenses).text = "₹8,000"
        findViewById<TextView>(R.id.txtWages).text = "₹5,000"
//Date Display
        val dateText = findViewById<TextView>(R.id.txtDate)
        val currentDate =SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date())
        dateText.text = currentDate

//Profile page
        val btnProfile = findViewById<Button>(R.id.btnProfile)

        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

//Ledger Page
        val ledgerButton = findViewById<MaterialButton>(R.id.btnLedgers)
        ledgerButton.setOnClickListener {
            openLedgerPage()
        }



    }



    private fun openLedgerPage() {
        val intent = Intent(this, LedgerHomeActivity::class.java)
        startActivity(intent)
    }


}