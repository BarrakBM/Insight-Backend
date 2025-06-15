package com.nbk.insights.dto.authentication

data class RegisterRequest(
    val username: String,
    val password: String,
    val fullName: String
)