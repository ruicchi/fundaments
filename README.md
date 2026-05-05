# Fundaments

An offline-first Android application designed to help language learners master structurally complex grammar. 

## 🛠️ Tech Stack
*   **Language:** Kotlin
*   **Architecture:** Fragments & Bottom Navigation
*   **Database:** SQLite (Local Data Storage)
*   **Document Generation:** Native Android `PdfDocument` Class

## 📂 Project Structure
*   `MainActivity.kt`: The primary application container housing the Bottom Navigation logic.
*   `DashboardFragment.kt`: Displays high-level user statistics.
*   `PracticeFragment.kt`: Handles the interactive quiz testing interface and logs user input to the database.
*   `ReportsFragment.kt`: Queries aggregated error data using SQLite `INNER JOIN` operations and triggers the PDF generation/viewing intent.
*   `DatabaseHelper.kt`: Manages the three-table database architecture (`GrammarRules`, `PracticeQuestions`, `DiagnosticLogs`)
