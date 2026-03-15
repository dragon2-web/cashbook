package com.example.cashbook.products

data class ProductModel(
    val uid:   String = "",
    val name:  String = "",
    val price: Double = 0.0,
    val unit:  String = "",
    val hsn:   String = ""
)