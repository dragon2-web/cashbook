package com.example.cashbook.logIn

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cashbook.R
import com.example.cashbook.dashboard.Dashboard
import com.google.firebase.auth.FirebaseAuth

class LogIn : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_in)

        auth = FirebaseAuth.getInstance()

        val email = findViewById<EditText>(R.id.etEmail)
        val password = findViewById<EditText>(R.id.etPassword)
        val loginBtn = findViewById<Button>(R.id.btnLogin)

        loginBtn.setOnClickListener {
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString().trim()

            if (emailText.isEmpty() || passwordText.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(emailText, passwordText)
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Login success → com.example.cashbook.dashboard.Dashboard
                Toast.makeText(
                    this,
                    "Login success",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(this, Dashboard::class.java)
                )
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Login failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}