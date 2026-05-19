package com.example.fundaments

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.Locale

class PracticeFragment : Fragment() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var categoryText: TextView
    private lateinit var instructionText: TextView
    private lateinit var promptText: TextView
    private lateinit var feedbackText: TextView
    private lateinit var nextButton: Button
    private lateinit var optionButtons: List<Button>
    private lateinit var progressBar: ProgressBar
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
            setPadding(dp(20), dp(22), dp(20), dp(24))
        }

        val profile = currentProfile
        content.addView(textView("Practice Quiz", 28f, R.color.fundaments_text, true))
        content.addView(
            textView(
                if (profile?.isComplete == true) "${profile.language} - ${profile.proficiency}" else "Complete your learner setup first.",
                15f,
                R.color.fundaments_muted
            ),
            blockParams(top = 4)
        )

        progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 45
            progressDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(color(R.color.fundaments_accent))
            }
        }
        content.addView(progressBar, blockParams(top = 18))

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(R.color.fundaments_card, 24)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        categoryText = textView("", 13f, R.color.fundaments_warning, true)
        instructionText = textView("", 15f, R.color.fundaments_muted)
        promptText = textView("", 24f, R.color.fundaments_text, true).apply {
            setPadding(0, dp(18), 0, dp(8))
        }

        card.addView(categoryText)
        card.addView(instructionText, blockParams(top = 10))
        card.addView(promptText)
        content.addView(card, blockParams(top = 20))

        val optionsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        optionButtons = listOf("A", "B", "C", "D").map { label ->
            optionButton(label).also { button ->
                optionsContainer.addView(button, blockParams(top = 12))
            }
        }
        content.addView(optionsContainer, blockParams(top = 8))

        feedbackText = textView("", 15f, R.color.fundaments_muted).apply {
            background = rounded(R.color.fundaments_card, 18)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            visibility = View.GONE
        }
        nextButton = button("Next Question") { loadQuestion() }.apply {
            visibility = View.GONE
        }

        content.addView(feedbackText, blockParams(top = 16))
        content.addView(nextButton, blockParams(top = 12))

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
        instructionText.text = question.instruction
        promptText.text = question.promptSentence
        feedbackText.text = ""
        feedbackText.visibility = View.GONE
        nextButton.visibility = View.GONE
        progressBar.progress = ((question.questionId % 20) + 1) * 5

        optionButtons.forEachIndexed { index, button ->
            val label = ('A' + index).toString()
            val option = question.options[index]
            button.text = "$label. $option"
            button.isEnabled = true
            button.setTextColor(color(R.color.fundaments_text))
            button.background = rounded(R.color.fundaments_option, 18, R.color.fundaments_option_border)
            button.setOnClickListener { submitAnswer(option, button) }
        }
    }

    private fun submitAnswer(selectedAnswer: String, selectedButton: Button) {
        val question = currentQuestion ?: return
        val userId = currentProfile?.userId ?: return
        val isCorrect = normalize(selectedAnswer) == normalize(question.correctAnswer)
        dbHelper.logAnswer(userId, question.questionId, selectedAnswer, isCorrect)

        optionButtons.forEach { button ->
            button.isEnabled = false
            val optionText = button.text.toString().substringAfter(". ").trim()
            if (normalize(optionText) == normalize(question.correctAnswer)) {
                button.setTextColor(Color.WHITE)
                button.background = rounded(R.color.fundaments_success, 18)
            }
        }

        if (!isCorrect) {
            selectedButton.setTextColor(Color.WHITE)
            selectedButton.background = rounded(R.color.fundaments_error, 18)
        }

        feedbackText.visibility = View.VISIBLE
        feedbackText.setTextColor(if (isCorrect) color(R.color.fundaments_success) else color(R.color.fundaments_error))
        feedbackText.text = if (isCorrect) {
            "Correct! ${question.explanation}"
        } else {
            "The correct answer is ${question.correctAnswer}.\n${question.explanation}"
        }
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
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun optionButton(label: String): Button {
        return Button(requireContext()).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            isAllCaps = false
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setTextColor(color(R.color.fundaments_text))
            background = rounded(R.color.fundaments_option, 18, R.color.fundaments_option_border)
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(R.color.fundaments_accent, 18)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
        }
    }

    private fun rounded(colorRes: Int, radius: Int, strokeColorRes: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color(colorRes))
            strokeColorRes?.let { setStroke(dp(2), color(it)) }
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
