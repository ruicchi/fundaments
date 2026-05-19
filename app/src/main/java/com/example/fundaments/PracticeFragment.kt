package com.example.fundaments

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.Locale

class PracticeFragment : Fragment() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var categoryText: TextView
    private lateinit var promptText: TextView
    private lateinit var answerInput: EditText
    private lateinit var feedbackText: TextView
    private lateinit var submitButton: Button
    private lateinit var nextButton: Button
    private var currentQuestion: PracticeQuestion? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dbHelper = DatabaseHelper(requireContext())

        val root = ScrollView(requireContext()).apply {
            setBackgroundColor(color(R.color.fundaments_background))
            isFillViewport = true
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        val title = textView("Daily Diagnostic Quiz", 26f, R.color.fundaments_text, true)
        val subtitle = textView(
            "Complete the missing grammar element. Each answer is logged locally for diagnostics.",
            15f,
            R.color.fundaments_muted
        )

        categoryText = textView("", 14f, R.color.fundaments_accent, true)
        promptText = textView("", 24f, R.color.fundaments_text, true).apply {
            setPadding(0, dp(24), 0, dp(12))
        }

        answerInput = EditText(requireContext()).apply {
            hint = "Type your answer"
            setHintTextColor(color(R.color.fundaments_muted))
            setTextColor(color(R.color.fundaments_text))
            setSingleLine(true)
            textSize = 18f
            imeOptions = EditorInfo.IME_ACTION_DONE
            setBackgroundColor(color(R.color.fundaments_surface_high))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submitAnswer()
                    true
                } else {
                    false
                }
            }
        }

        submitButton = button("Check Answer") { submitAnswer() }
        nextButton = button("Next Question") { loadQuestion() }.apply {
            visibility = View.GONE
        }

        feedbackText = textView("", 15f, R.color.fundaments_muted).apply {
            setPadding(0, dp(18), 0, 0)
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(categoryText, blockParams(top = 28))
        content.addView(promptText)
        content.addView(answerInput, blockParams(top = 8))
        content.addView(submitButton, blockParams(top = 18))
        content.addView(nextButton, blockParams(top = 10))
        content.addView(feedbackText)

        root.addView(content)
        loadQuestion()
        return root
    }

    override fun onDestroyView() {
        dbHelper.close()
        super.onDestroyView()
    }

    private fun loadQuestion() {
        currentQuestion = dbHelper.getRandomQuestion()
        val question = currentQuestion
        if (question == null) {
            Toast.makeText(requireContext(), "No practice questions found.", Toast.LENGTH_SHORT).show()
            return
        }

        categoryText.text = question.category.uppercase(Locale.getDefault())
        promptText.text = question.promptSentence
        answerInput.setText("")
        answerInput.isEnabled = true
        feedbackText.text = ""
        submitButton.isEnabled = true
        nextButton.visibility = View.GONE
    }

    private fun submitAnswer() {
        val question = currentQuestion ?: return
        val rawAnswer = answerInput.text.toString()
        if (rawAnswer.isBlank()) {
            answerInput.error = "Answer required"
            return
        }

        val isCorrect = normalize(rawAnswer) == normalize(question.correctAnswer)
        dbHelper.logAnswer(question.questionId, isCorrect)

        val feedbackColor = if (isCorrect) R.color.fundaments_accent else R.color.fundaments_error
        feedbackText.setTextColor(color(feedbackColor))
        feedbackText.text = if (isCorrect) {
            "Correct. ${question.explanation}"
        } else {
            "Review ${question.category}. Expected: ${question.correctAnswer}\n${question.explanation}"
        }

        answerInput.isEnabled = false
        submitButton.isEnabled = false
        nextButton.visibility = View.VISIBLE
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
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
        ).apply {
            topMargin = dp(top)
        }
    }

    private fun color(resId: Int): Int = requireContext().getColor(resId)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
