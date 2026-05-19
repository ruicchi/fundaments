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
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.Locale

class PracticeFragment : Fragment() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var categoryText: TextView
    private lateinit var promptText: TextView
    private lateinit var answerInput: EditText
    private lateinit var feedbackText: TextView
    private lateinit var submitButton: Button
    private lateinit var nextButton: Button
    private var currentQuestion: PracticeQuestion? = null
    private var currentProfile: UserProfile? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dbHelper = DatabaseHelper(requireContext())
        sessionManager = SessionManager(requireContext())
        currentProfile = sessionManager.getCurrentUserId()?.let { dbHelper.getUserProfile(it) }

        val root = ScrollView(requireContext()).apply {
            setBackgroundColor(color(R.color.fundaments_background))
            isFillViewport = true
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        content.addView(textView("Daily Diagnostic Quiz", 26f, R.color.fundaments_text, true))
        content.addView(
            textView(
                "Complete the missing grammar element. Questions match the selected learner profile.",
                15f,
                R.color.fundaments_muted
            ),
            blockParams(top = 4)
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
        val profile = currentProfile
        if (profile?.isComplete != true) {
            Toast.makeText(requireContext(), "Complete learner preferences first.", Toast.LENGTH_SHORT).show()
            return
        }

        currentQuestion = dbHelper.getRandomQuestion(profile.language.orEmpty(), profile.proficiency.orEmpty())
        val question = currentQuestion
        if (question == null) {
            Toast.makeText(requireContext(), "No practice questions found.", Toast.LENGTH_SHORT).show()
            return
        }

        categoryText.text = "${question.language} / ${question.proficiency} / ${question.category}".uppercase(Locale.getDefault())
        promptText.text = question.promptSentence
        answerInput.setText("")
        answerInput.isEnabled = true
        feedbackText.text = ""
        feedbackText.setTextColor(color(R.color.fundaments_muted))
        submitButton.isEnabled = true
        nextButton.visibility = View.GONE
    }

    private fun submitAnswer() {
        val question = currentQuestion ?: return
        val userId = currentProfile?.userId ?: return
        val rawAnswer = answerInput.text.toString()
        if (rawAnswer.isBlank()) {
            answerInput.error = "Answer required"
            return
        }

        val isCorrect = normalize(rawAnswer) == normalize(question.correctAnswer)
        dbHelper.logAnswer(userId, question.questionId, isCorrect)

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
        ).apply { topMargin = dp(top) }
    }

    private fun color(resId: Int): Int = requireContext().getColor(resId)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
