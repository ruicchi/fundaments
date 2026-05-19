package com.example.fundaments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class DashboardFragment : Fragment() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var content: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dbHelper = DatabaseHelper(requireContext())

        val root = ScrollView(requireContext()).apply {
            setBackgroundColor(color(R.color.fundaments_background))
            isFillViewport = true
        }

        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        root.addView(content)
        renderDashboard()
        return root
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) renderDashboard()
    }

    override fun onDestroyView() {
        dbHelper.close()
        super.onDestroyView()
    }

    private fun renderDashboard() {
        content.removeAllViews()

        val stats = dbHelper.getDashboardStats()
        val categories = dbHelper.getCategoryPerformance(limit = 3)
        val questionCount = dbHelper.getQuestionCount()

        content.addView(textView("Fundaments", 28f, R.color.fundaments_text, true))
        content.addView(
            textView(
                "Offline grammar diagnostics for structural language practice.",
                15f,
                R.color.fundaments_muted
            ),
            blockParams(top = 4)
        )

        content.addView(sectionLabel("PROGRESS"), blockParams(top = 28))
        content.addView(statBlock("Overall Accuracy", "${stats.accuracyPercent}%", "Based on ${stats.totalAnswered} logged answers"))
        content.addView(statBlock("Correct", stats.correctAnswers.toString(), "Answers that matched the expected structure"))
        content.addView(statBlock("Needs Review", stats.incorrectAnswers.toString(), "Errors mapped to grammar categories"))
        content.addView(statBlock("Question Bank", questionCount.toString(), "Local SQLite practice prompts"))

        content.addView(sectionLabel("WEAKEST CATEGORIES"), blockParams(top = 24))
        if (categories.isEmpty()) {
            content.addView(
                textView(
                    "No diagnostics yet. Complete a practice item to build your grammar profile.",
                    15f,
                    R.color.fundaments_muted
                ),
                blockParams(top = 10)
            )
        } else {
            categories.forEach { category ->
                content.addView(categoryRow(category), blockParams(top = 10))
            }
        }
    }

    private fun statBlock(label: String, value: String, detail: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.fundaments_surface))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(textView(label, 13f, R.color.fundaments_muted, true))
            addView(textView(value, 30f, R.color.fundaments_accent, true))
            addView(textView(detail, 14f, R.color.fundaments_text))
        }
    }

    private fun categoryRow(category: CategoryPerformance): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.fundaments_surface_high))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(textView(category.category, 17f, R.color.fundaments_text, true))
            addView(
                textView(
                    "${category.incorrectAnswers} missed of ${category.totalAttempts} attempts • ${category.accuracyPercent}% accuracy",
                    14f,
                    R.color.fundaments_muted
                )
            )
            addView(ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = category.accuracyPercent
                contentDescription = "${category.category} accuracy ${category.accuracyPercent} percent"
            }, blockParams(top = 8))
            addView(textView(category.explanation, 13f, R.color.fundaments_muted), blockParams(top = 8))
        }
    }

    private fun sectionLabel(text: String): TextView = textView(text, 13f, R.color.fundaments_warning, true)

    private fun textView(text: String, sp: Float, colorRes: Int, bold: Boolean = false): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = sp
            setTextColor(color(colorRes))
            includeFontPadding = true
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
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
