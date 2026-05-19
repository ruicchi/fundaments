package com.example.fundaments

import android.content.Context

class SessionManager(context: Context) {
    private val preferences = context.getSharedPreferences("fundaments_session", Context.MODE_PRIVATE)

    fun getCurrentUserId(): Int? {
        val userId = preferences.getInt(KEY_USER_ID, 0)
        return if (userId > 0) userId else null
    }

    fun setCurrentUserId(userId: Int) {
        preferences.edit().putInt(KEY_USER_ID, userId).apply()
    }

    fun clearCurrentUser() {
        preferences.edit().remove(KEY_USER_ID).apply()
    }

    companion object {
        private const val KEY_USER_ID = "current_user_id"
    }
}
