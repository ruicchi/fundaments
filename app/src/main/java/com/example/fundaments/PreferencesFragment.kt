package com.example.fundaments

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment

class PreferencesFragment : Fragment() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var ageInput: EditText
    private lateinit var languageSpinner: Spinner
    private lateinit var proficiencySpinner: Spinner

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dbHelper = DatabaseHelper(requireContext())
        sessionManager = SessionManager(requireContext())

        val root = ScrollView(requireContext()).apply {
            setBackgroundColor(color(R.color.fundaments_background))
            isFillViewport = true
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        ageInput = EditText(requireContext()).apply {
            hint = "Age"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setHintTextColor(color(R.color.fundaments_muted))
            setTextColor(color(R.color.fundaments_text))
            setSingleLine(true)
            textSize = 18f
            setBackgroundColor(color(R.color.fundaments_surface_high))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        languageSpinner = spinner(DatabaseHelper.LANGUAGES)
        proficiencySpinner = spinner(DatabaseHelper.PROFICIENCY_LEVELS)

        val userName = sessionManager.getCurrentUserId()?.let { dbHelper.getUserProfile(it)?.learnerName }.orEmpty()

        content.addView(textView("Welcome, $userName", 28f, R.color.fundaments_text, true))
        content.addView(
            textView(
                "Set the learner profile so practice questions match the child, language, and current skill level.",
                15f,
                R.color.fundaments_muted
            ),
            blockParams(top = 8)
        )
        content.addView(label("AGE"), blockParams(top = 28))
        content.addView(ageInput, blockParams(top = 8))
        content.addView(label("LANGUAGE"), blockParams(top = 18))
        content.addView(languageSpinner, blockParams(top = 8))
        content.addView(label("PROFICIENCY"), blockParams(top = 18))
        content.addView(proficiencySpinner, blockParams(top = 8))
        content.addView(button("Start Learning") { savePreferences() }, blockParams(top = 24))

        root.addView(content)
        return root
    }

    override fun onDestroyView() {
        dbHelper.close()
        super.onDestroyView()
    }

    private fun savePreferences() {
        val age = ageInput.text.toString().toIntOrNull()
        if (age == null || age !in 4..99) {
            ageInput.error = "Enter an age from 4 to 99"
            return
        }

        val userId = sessionManager.getCurrentUserId() ?: return
        dbHelper.updateUserPreferences(
            userId = userId,
            age = age,
            language = languageSpinner.selectedItem.toString(),
            proficiency = proficiencySpinner.selectedItem.toString()
        )
        (requireActivity() as MainActivity).onPreferencesComplete()
    }

    private fun spinner(values: List<String>): Spinner {
        return Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, values).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setBackgroundColor(color(R.color.fundaments_surface_high))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    private fun label(text: String): TextView = textView(text, 13f, R.color.fundaments_warning, true)

    private fun textView(text: String, sp: Float, colorRes: Int, bold: Boolean = false): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = sp
            setTextColor(color(colorRes))
            includeFontPadding = true
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setBackgroundColor(color(R.color.fundaments_accent))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun blockParams(top: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }
    }

    private fun color(resId: Int): Int = requireContext().getColor(resId)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
