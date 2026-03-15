package com.example.cashbook

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()

//        checkAuthAndRedirect()
        startActivity(Intent(
            this,
            com.example.cashbook.dashboard.Dashboard::class.java
        ))
        finish()
    }
//
//    private fun checkAuthAndRedirect() {
//        val currentUser = auth.currentUser
//
//        if (currentUser != null) {
//
//            val intent = Intent(
//                this,
//                com.example.cashbook.dashboard.Dashboard::class.java
//            )
//            startActivity(intent)
//        } else {
//            val intent = Intent(
//                this,
//                com.example.cashbook.logIn.LogIn::class.java
//            )
//            startActivity(intent)
//        }
//
//        // Kill MainActivity so user can't come back here
//        finish()
//    }
}