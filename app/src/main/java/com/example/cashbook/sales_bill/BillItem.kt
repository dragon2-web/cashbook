package com.example.cashbook.sales_bill

data class BillItem(
    val productName: String = "",
    val units:       Double = 0.0,
    val rate:        Double = 0.0,
    val amount:      Double = 0.0
)