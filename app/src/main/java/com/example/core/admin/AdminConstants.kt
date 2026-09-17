package com.example.core.admin

object AdminConstants {
    const val ADMIN_EMAIL = "king.khan648k@gmail.com"

    fun isAdminEmail(email: String?): Boolean {
        return email?.trim()?.equals(ADMIN_EMAIL, ignoreCase = true) == true
    }
}
