package com.example.fundaments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment

class PracticeFragment : Fragment() {
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // For simplicity in this guide, creating a button programmatically.
        // In reality, you'd inflate an XML layout here containing your quiz UI.
        val view = Button(requireContext()).apply {
            text = "Simulate Answering Question 1 Incorrectly"
            setOnClickListener {
                dbHelper = DatabaseHelper(requireContext())
                // Simulating logging a wrong answer for Question ID 1
                dbHelper.logAnswer(1, false)
                Toast.makeText(requireContext(), "Error logged to database!", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }
}