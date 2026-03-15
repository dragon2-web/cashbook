package com.example.cashbook.assests_and_liabilities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.cashbook.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

class AssetsActivity : AppCompatActivity() {

    private lateinit var container:  LinearLayout
    private lateinit var emptyState: TextView
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assets)

        container  = findViewById(R.id.assetsContainer)
        emptyState = findViewById(R.id.emptyState)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            startActivity(AddAssetActivity.newIntent(this))
        }

        loadAssets()
    }

    override fun onResume() {
        super.onResume()
        loadAssets()
    }

    // ── Load ──────────────────────────────────────────────────────
    private fun loadAssets() {
        db.collection("assets").orderBy("createdAt").get()
            .addOnSuccessListener { snapshot ->
                container.removeAllViews()

                if (snapshot.isEmpty) {
                    emptyState.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                emptyState.visibility = View.GONE
                var totalCurrentValue = 0.0

                snapshot.documents.forEach { doc ->
                    val assetId       = doc.id
                    val name          = doc.getString("name")          ?: ""
                    val type          = doc.getString("type")          ?: ""
                    val purchasePrice = doc.getDouble("purchasePrice") ?: 0.0
                    val currentValue  = doc.getDouble("currentValue")  ?: 0.0
                    val purchaseDate  = doc.getString("purchaseDate")  ?: ""
                    totalCurrentValue += currentValue
                    addAssetRow(assetId, name, type, purchasePrice, currentValue, purchaseDate)
                }

                addTotalRow(totalCurrentValue)
            }
    }

    // ── Asset row ─────────────────────────────────────────────────
    private fun addAssetRow(
        assetId:       String,
        name:          String,
        type:          String,
        purchasePrice: Double,
        currentValue:  Double,
        purchaseDate:  String
    ) {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius        = 12f * resources.displayMetrics.density
            cardElevation = 4f * resources.displayMetrics.density
            setCardBackgroundColor(0xFF1E1E2E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(8)) }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // ── Row 1: name + value ───────────────────────────────────
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(6)) }
        }

        row1.addView(LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(TextView(context).apply {
                text     = name
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(0xFFFFFFFF.toInt())
            })
            addView(TextView(context).apply {
                text     = type.uppercase()
                textSize = 11f
                setTextColor(0xFF90CAF9.toInt())
            })
        })

        row1.addView(LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            addView(TextView(context).apply {
                text     = formatAmount(currentValue)
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(0xFF1B5E20.toInt())
                gravity  = Gravity.END
            })
            addView(TextView(context).apply {
                text     = "Current Value"
                textSize = 10f
                setTextColor(0xFF90A4AE.toInt())
                gravity  = Gravity.END
            })
        })

        inner.addView(row1)

        // ── Divider ───────────────────────────────────────────────
        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                .also { it.setMargins(0, dp(6), 0, dp(6)) }
            setBackgroundColor(0xFF2A2A3E.toInt())
        })

        // ── Row 2: purchase info ──────────────────────────────────
        val row2 = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(10)) }
        }

        row2.addView(TextView(this).apply {
            text     = "Purchase: ${formatAmount(purchasePrice)}"
            textSize = 11f
            setTextColor(0xFF90A4AE.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row2.addView(TextView(this).apply {
            text     = "Date: $purchaseDate"
            textSize = 11f
            setTextColor(0xFF90A4AE.toInt())
            gravity  = Gravity.END
        })

        inner.addView(row2)

        // ── Row 3: action buttons ─────────────────────────────────
        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.END
        }

        val btnUpdate = com.google.android.material.button.MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text        = "Update Value"
            textSize    = 11f
            setTextColor(0xFF90CAF9.toInt())
            strokeColor = android.content.res.ColorStateList.valueOf(0xFF3949AB.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(8) }
            setOnClickListener {
                showUpdateValueDialog(assetId, name, currentValue)
            }
        }

        val btnDelete = com.google.android.material.button.MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text        = "Delete"
            textSize    = 11f
            setTextColor(0xFFC62828.toInt())
            strokeColor = android.content.res.ColorStateList.valueOf(0xFFC62828.toInt())
            setOnClickListener {
                showDeleteDialog(assetId, name, currentValue)
            }
        }

        row3.addView(btnUpdate)
        row3.addView(btnDelete)
        inner.addView(row3)

        card.addView(inner)
        container.addView(card)
    }

    // ── Update value dialog ───────────────────────────────────────
    private fun showUpdateValueDialog(
        assetId:   String,
        name:      String,
        oldValue:  Double
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        layout.addView(TextView(this).apply {
            text     = "New current value for $name"
            textSize = 13f
            setTextColor(0xFF212121.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(12)) }
        })

        val input = EditText(this).apply {
            inputType   = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(oldValue.toString())
            setSelectAllOnFocus(true)
        }

        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Update Asset Value")
            .setView(layout)
            .setPositiveButton("Update") { _, _ ->
                val newValue = input.text.toString().trim().toDoubleOrNull()

                if (newValue == null || newValue < 0) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val diff  = newValue - oldValue
                val now   = System.currentTimeMillis()
                val batch = db.batch()

                batch.update(
                    db.collection("assets").document(assetId),
                    mapOf(
                        "currentValue"  to newValue,
                        "lastUpdatedAt" to now
                    )
                )

                batch.update(
                    db.collection("firm").document("main"),
                    mapOf(
                        "totalAssets"   to FieldValue.increment(diff),
                        "lastUpdatedAt" to now
                    )
                )

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Value updated ✓", Toast.LENGTH_SHORT).show()
                        loadAssets()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Delete dialog ─────────────────────────────────────────────
    private fun showDeleteDialog(
        assetId:      String,
        name:         String,
        currentValue: Double
    ) {
        AlertDialog.Builder(this)
            .setTitle("Delete Asset")
            .setMessage("Delete \"$name\"? This will reduce total assets by ${formatAmount(currentValue)}.")
            .setPositiveButton("Delete") { _, _ ->
                val now   = System.currentTimeMillis()
                val batch = db.batch()

                batch.delete(
                    db.collection("assets").document(assetId)
                )

                batch.update(
                    db.collection("firm").document("main"),
                    mapOf(
                        "totalAssets"   to FieldValue.increment(-currentValue),
                        "lastUpdatedAt" to now
                    )
                )

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "$name deleted", Toast.LENGTH_SHORT).show()
                        loadAssets()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Total row ─────────────────────────────────────────────────
    private fun addTotalRow(total: Double) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0xFF16213E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, dp(8), 0, 0) }
        }

        row.addView(TextView(this).apply {
            text     = "Total Asset Value"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(TextView(this).apply {
            text     = formatAmount(total)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFF1B5E20.toInt())
        })

        container.addView(row)
    }

    // ── Helpers ───────────────────────────────────────────────────
    private fun formatAmount(amount: Double): String {
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return "₹${format.format(amount)}"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun newIntent(context: Context) = Intent(context, AssetsActivity::class.java)
    }
}