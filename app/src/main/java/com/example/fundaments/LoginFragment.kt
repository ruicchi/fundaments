package com.example.fundaments

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class LoginFragment : Fragment() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var nameInput: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dbHelper = DatabaseHelper(requireContext())

        val root = ScrollView(requireContext()).apply {
            setBackgroundColor(color(R.color.fundaments_background))
            isFillViewport = true
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        nameInput = EditText(requireContext()).apply {
            hint = "Learner name"
            setHintTextColor(color(R.color.fundaments_muted))
            setTextColor(color(R.color.fundaments_text))
            setSingleLine(true)
            textSize = 18f
            imeOptions = EditorInfo.IME_ACTION_DONE
            setBackgroundColor(color(R.color.fundaments_surface_high))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    login()
                    true
                } else {
                    false
                }
            }
        }

        content.addView(textView("Fundaments", 32f, R.color.fundaments_text, true))
        content.addView(
            textView(
                "Enter a learner name so progress and diagnostic reports stay separate on this device.",
                16f,
                R.color.fundaments_muted
            ),
            blockParams(top = 8)
        )
        content.addView(nameInput, blockParams(top = 28))
        content.addView(button("Continue") { login() }, blockParams(top = 18))

        root.addView(content)
        return root
    }

    override fun onDestroyView() {
        dbHelper.close()
        super.onDestroyView()
    }

    private fun login() {
        val learnerName = nameInput.text.toString().trim()
        if (learnerName.length < 2) {
            nameInput.error = "Enter at least 2 characters"
            return
        }
        val userId = dbHelper.findOrCreateUser(learnerName)
        (requireActivity() as MainActivity).onLoginComplete(userId)
    }

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
