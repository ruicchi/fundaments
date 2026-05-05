package com.example.fundaments

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream

class ReportsFragment : Fragment() {
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = Button(requireContext()).apply {
            text = "Generate & View PDF Report"
            setOnClickListener {
                generateAndOpenPDF()
            }
        }
        return view
    }

    private fun generateAndOpenPDF() {
        dbHelper = DatabaseHelper(requireContext())
        val cursor = dbHelper.getErrorAnalytics()

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas: Canvas = page.canvas
        val paint = Paint().apply { textSize = 16f }

        canvas.drawText("Syntax Diagnostic Profile", 50f, 50f, paint)

        var yPosition = 100f
        if (cursor.count == 0) {
            canvas.drawText("No errors logged yet. Great job!", 50f, yPosition, paint)
        } else {
            canvas.drawText("Most Frequently Failed Categories:", 50f, yPosition, paint)
            yPosition += 40f
            while (cursor.moveToNext()) {
                val category = cursor.getString(0)
                val errorCount = cursor.getInt(1)
                canvas.drawText("- $category: $errorCount errors", 50f, yPosition, paint)
                yPosition += 30f
            }
        }

        pdfDocument.finishPage(page)

        // Save to internal app files so we can view it securely via Intent
        val file = File(requireContext().getExternalFilesDir(null), "DiagnosticProfile.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            // Native Intent to open the PDF viewer
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to generate PDF", Toast.LENGTH_SHORT).show()
            pdfDocument.close()
        }
    }
}