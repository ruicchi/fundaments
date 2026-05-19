package com.example.fundaments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream

class ReportsFragment : Fragment() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var summaryText: TextView
    private lateinit var topCategoriesText: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dbHelper = DatabaseHelper(requireContext())
        sessionManager = SessionManager(requireContext())

        val root = ScrollView(requireContext()).apply {
            setBackgroundColor(color(R.color.fundaments_background))
            isFillViewport = true
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        content.addView(textView("Reports", 28f, R.color.fundaments_text, true))
        content.addView(
            textView(
                "Generate a local Monthly Syntax Diagnostic Profile for the active learner.",
                15f,
                R.color.fundaments_muted
            ),
            blockParams(top = 4)
        )

        summaryText = textView("", 16f, R.color.fundaments_text).apply {
            setBackgroundColor(color(R.color.fundaments_surface))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        topCategoriesText = textView("", 15f, R.color.fundaments_muted).apply {
            setBackgroundColor(color(R.color.fundaments_surface_high))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        content.addView(summaryText, blockParams(top = 28))
        content.addView(topCategoriesText, blockParams(top = 12))
        content.addView(button("Generate PDF Report") { generateAndOpenPdf() }, blockParams(top = 18))

        root.addView(content)
        renderPreview()
        return root
    }

    override fun onResume() {
        super.onResume()
        if (::summaryText.isInitialized) renderPreview()
    }

    override fun onDestroyView() {
        dbHelper.close()
        super.onDestroyView()
    }

    private fun renderPreview() {
        val profile = currentProfile()
        if (profile == null) {
            summaryText.text = "Complete learner setup before generating reports."
            topCategoriesText.text = ""
            return
        }

        val stats = dbHelper.getMonthlyDashboardStats(profile.userId)
        val categories = dbHelper.getMonthlyCategoryPerformance(profile.userId, limit = 3)

        summaryText.text = """
            Learner: ${profile.learnerName}
            Language: ${profile.language}
            Level: ${profile.proficiency}
            Overall accuracy: ${stats.accuracyPercent}%
            Questions answered: ${stats.totalAnswered}
            Correct: ${stats.correctAnswers}
            Needs review: ${stats.incorrectAnswers}
        """.trimIndent()

        topCategoriesText.text = if (categories.isEmpty()) {
            "Top failed categories will appear after this learner answers practice questions."
        } else {
            categories.joinToString(separator = "\n") {
                "${it.category}: ${it.incorrectAnswers} missed, ${it.accuracyPercent}% accuracy"
            }
        }
    }

    private fun generateAndOpenPdf() {
        try {
            if (currentProfile() == null) {
                Toast.makeText(requireContext(), "Complete learner setup first.", Toast.LENGTH_LONG).show()
                return
            }
            val file = createDiagnosticPdf()
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open diagnostic report"))
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No PDF viewer is installed.", Toast.LENGTH_LONG).show()
        } catch (exception: Exception) {
            Toast.makeText(requireContext(), "Failed to generate PDF report.", Toast.LENGTH_LONG).show()
        }
    }

    private fun createDiagnosticPdf(): File {
        val profile = currentProfile() ?: error("Missing learner profile")
        val stats = dbHelper.getMonthlyDashboardStats(profile.userId)
        val categories = dbHelper.getMonthlyCategoryPerformance(profile.userId, limit = 3)
        val allCategories = dbHelper.getMonthlyCategoryPerformance(profile.userId)
        val file = File(requireContext().getExternalFilesDir(null), "MonthlySyntaxDiagnosticProfile.pdf")

        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(16, 20, 24)
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(34, 48, 58)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(44, 55, 63)
                textSize = 12f
            }
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(69, 160, 111)
                style = Paint.Style.FILL
            }

            var y = 56f
            canvas.drawText("Monthly Syntax Diagnostic Profile", 48f, y, titlePaint)
            y += 34f
            canvas.drawText("Learner: ${profile.learnerName}", 48f, y, bodyPaint)
            y += 18f
            canvas.drawText("Language: ${profile.language}   Level: ${profile.proficiency}", 48f, y, bodyPaint)
            y += 24f
            canvas.drawText("Overall accuracy: ${stats.accuracyPercent}%", 48f, y, headingPaint)
            y += 22f
            canvas.drawText("Questions answered: ${stats.totalAnswered}", 48f, y, bodyPaint)
            y += 18f
            canvas.drawText("Correct answers: ${stats.correctAnswers}", 48f, y, bodyPaint)
            y += 18f
            canvas.drawText("Incorrect answers: ${stats.incorrectAnswers}", 48f, y, bodyPaint)

            y += 38f
            canvas.drawText("Top Grammar Categories to Review", 48f, y, headingPaint)
            y += 24f

            if (categories.isEmpty()) {
                canvas.drawText("No diagnostic attempts logged yet.", 48f, y, bodyPaint)
            } else {
                categories.forEachIndexed { index, category ->
                    canvas.drawText(
                        "${index + 1}. ${category.category}: ${category.incorrectAnswers} missed of ${category.totalAttempts} attempts",
                        48f,
                        y,
                        bodyPaint
                    )
                    y += 18f
                    canvas.drawText(category.explanation, 64f, y, bodyPaint)
                    y += 22f
                }
            }

            y += 18f
            canvas.drawText("Category Accuracy", 48f, y, headingPaint)
            y += 24f

            allCategories.forEach { category ->
                canvas.drawText("${category.category} (${category.accuracyPercent}%)", 48f, y, bodyPaint)
                canvas.drawRect(220f, y - 10f, 520f, y - 2f, Paint().apply { color = Color.rgb(218, 225, 230) })
                canvas.drawRect(220f, y - 10f, 220f + (300f * category.accuracyPercent / 100f), y - 2f, accentPaint)
                y += 22f
            }

            document.finishPage(page)
            FileOutputStream(file).use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }

        return file
    }

    private fun currentProfile(): UserProfile? {
        val userId = sessionManager.getCurrentUserId() ?: return null
        val profile = dbHelper.getUserProfile(userId) ?: return null
        return if (profile.isComplete) profile else null
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
