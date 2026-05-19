package com.example.fundaments

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)
        sessionManager = SessionManager(this)
        bottomNav = findViewById(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> replaceFragment(DashboardFragment())
                R.id.nav_practice -> replaceFragment(PracticeFragment())
                R.id.nav_reports -> replaceFragment(ReportsFragment())
            }
            true
        }

        routeFromSession()
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }

    fun onLoginComplete(userId: Int) {
        sessionManager.setCurrentUserId(userId)
        val profile = dbHelper.getUserProfile(userId)
        if (profile?.isComplete == true) {
            showMainApp()
        } else {
            showOnboarding()
        }
    }

    fun onPreferencesComplete() {
        showMainApp()
    }

    fun switchLearner() {
        sessionManager.clearCurrentUser()
        bottomNav.visibility = View.GONE
        replaceFragment(LoginFragment())
    }

    private fun routeFromSession() {
        val userId = sessionManager.getCurrentUserId()
        val profile = userId?.let { dbHelper.getUserProfile(it) }
        when {
            profile == null -> {
                bottomNav.visibility = View.GONE
                replaceFragment(LoginFragment())
            }
            profile.isComplete -> showMainApp()
            else -> showOnboarding()
        }
    }

    private fun showOnboarding() {
        bottomNav.visibility = View.GONE
        replaceFragment(PreferencesFragment())
    }

    private fun showMainApp() {
        bottomNav.visibility = View.VISIBLE
        bottomNav.selectedItemId = R.id.nav_dashboard
        replaceFragment(DashboardFragment())
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
