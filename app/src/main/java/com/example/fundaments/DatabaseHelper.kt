package com.example.fundaments

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.roundToInt

data class PracticeQuestion(
    val questionId: Int,
    val ruleId: Int,
    val category: String,
    val explanation: String,
    val promptSentence: String,
    val correctAnswer: String
)

data class DashboardStats(
    val totalAnswered: Int,
    val correctAnswers: Int,
    val incorrectAnswers: Int,
    val accuracyPercent: Int
)

data class CategoryPerformance(
    val category: String,
    val explanation: String,
    val totalAttempts: Int,
    val correctAnswers: Int,
    val incorrectAnswers: Int,
    val accuracyPercent: Int
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE GrammarRules (
                rule_id INTEGER PRIMARY KEY,
                category TEXT NOT NULL,
                explanation TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE PracticeQuestions (
                question_id INTEGER PRIMARY KEY,
                rule_id INTEGER NOT NULL,
                prompt_sentence TEXT NOT NULL,
                correct_answer TEXT NOT NULL,
                FOREIGN KEY(rule_id) REFERENCES GrammarRules(rule_id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE DiagnosticLogs (
                log_id INTEGER PRIMARY KEY AUTOINCREMENT,
                question_id INTEGER NOT NULL,
                is_correct INTEGER NOT NULL,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(question_id) REFERENCES PracticeQuestions(question_id)
            )
            """.trimIndent()
        )

        seedDiagnosticContent(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS DiagnosticLogs")
        db.execSQL("DROP TABLE IF EXISTS PracticeQuestions")
        db.execSQL("DROP TABLE IF EXISTS GrammarRules")
        onCreate(db)
    }

    fun logAnswer(questionId: Int, isCorrect: Boolean) {
        val values = ContentValues().apply {
            put("question_id", questionId)
            put("is_correct", if (isCorrect) 1 else 0)
        }
        writableDatabase.insert("DiagnosticLogs", null, values)
    }

    fun getRandomQuestion(): PracticeQuestion? {
        val query = """
            SELECT Q.question_id, Q.rule_id, G.category, G.explanation,
                   Q.prompt_sentence, Q.correct_answer
            FROM PracticeQuestions Q
            INNER JOIN GrammarRules G ON Q.rule_id = G.rule_id
            ORDER BY RANDOM()
            LIMIT 1
        """.trimIndent()

        readableDatabase.rawQuery(query, null).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return PracticeQuestion(
                questionId = cursor.getInt(0),
                ruleId = cursor.getInt(1),
                category = cursor.getString(2),
                explanation = cursor.getString(3),
                promptSentence = cursor.getString(4),
                correctAnswer = cursor.getString(5)
            )
        }
    }

    fun getDashboardStats(): DashboardStats {
        return getStats(whereClause = "")
    }

    fun getMonthlyDashboardStats(): DashboardStats {
        return getStats(whereClause = "WHERE timestamp >= datetime('now', 'start of month')")
    }

    private fun getStats(whereClause: String): DashboardStats {
        val query = """
            SELECT COUNT(log_id),
                   COALESCE(SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN is_correct = 0 THEN 1 ELSE 0 END), 0)
            FROM DiagnosticLogs
            $whereClause
        """.trimIndent()

        readableDatabase.rawQuery(query, null).use { cursor ->
            cursor.moveToFirst()
            val total = cursor.getInt(0)
            val correct = cursor.getInt(1)
            val incorrect = cursor.getInt(2)
            val accuracy = if (total == 0) 0 else ((correct.toDouble() / total) * 100).roundToInt()
            return DashboardStats(total, correct, incorrect, accuracy)
        }
    }

    fun getCategoryPerformance(limit: Int? = null): List<CategoryPerformance> {
        return getCategoryPerformance(limit = limit, monthlyOnly = false)
    }

    fun getMonthlyCategoryPerformance(limit: Int? = null): List<CategoryPerformance> {
        return getCategoryPerformance(limit = limit, monthlyOnly = true)
    }

    private fun getCategoryPerformance(limit: Int? = null, monthlyOnly: Boolean): List<CategoryPerformance> {
        val limitClause = limit?.let { " LIMIT $it" }.orEmpty()
        val whereClause = if (monthlyOnly) {
            "WHERE L.timestamp >= datetime('now', 'start of month')"
        } else {
            ""
        }
        val query = """
            SELECT G.category,
                   G.explanation,
                   COUNT(L.log_id) AS total_attempts,
                   COALESCE(SUM(CASE WHEN L.is_correct = 1 THEN 1 ELSE 0 END), 0) AS correct_answers,
                   COALESCE(SUM(CASE WHEN L.is_correct = 0 THEN 1 ELSE 0 END), 0) AS incorrect_answers
            FROM GrammarRules G
            INNER JOIN PracticeQuestions Q ON G.rule_id = Q.rule_id
            INNER JOIN DiagnosticLogs L ON Q.question_id = L.question_id
            $whereClause
            GROUP BY G.rule_id, G.category, G.explanation
            HAVING COUNT(L.log_id) > 0
            ORDER BY incorrect_answers DESC, total_attempts DESC, G.category ASC
            $limitClause
        """.trimIndent()

        val results = mutableListOf<CategoryPerformance>()
        readableDatabase.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val total = cursor.getInt(2)
                val correct = cursor.getInt(3)
                val incorrect = cursor.getInt(4)
                val accuracy = if (total == 0) 0 else ((correct.toDouble() / total) * 100).roundToInt()
                results += CategoryPerformance(
                    category = cursor.getString(0),
                    explanation = cursor.getString(1),
                    totalAttempts = total,
                    correctAnswers = correct,
                    incorrectAnswers = incorrect,
                    accuracyPercent = accuracy
                )
            }
        }
        return results
    }

    fun getQuestionCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(question_id) FROM PracticeQuestions", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun seedDiagnosticContent(db: SQLiteDatabase) {
        val rules = listOf(
            RuleSeed(1, "Particles", "Marks the topic, subject, object, or direction of a sentence."),
            RuleSeed(2, "Verb Conjugation", "Changes verbs based on tense, politeness, and sentence intent."),
            RuleSeed(3, "Sentence Order", "Keeps modifiers, objects, and verbs in a valid sentence structure."),
            RuleSeed(4, "Counters", "Uses the correct counting form for an object's shape or category."),
            RuleSeed(5, "Polite Requests", "Forms respectful requests without changing the intended meaning.")
        )

        val questions = listOf(
            QuestionSeed(1, 1, "Watashi __ gakusei desu.", "wa"),
            QuestionSeed(2, 1, "Neko __ isu no shita ni imasu.", "ga"),
            QuestionSeed(3, 1, "Toshokan __ benkyou shimasu.", "de"),
            QuestionSeed(4, 2, "Kinou, watashi wa sushi o __.", "tabemashita"),
            QuestionSeed(5, 2, "Ashita, gakkou e __.", "ikimasu"),
            QuestionSeed(6, 3, "Watashi wa hon o __.", "yomimasu"),
            QuestionSeed(7, 4, "Enpitsu ga san-__ arimasu.", "bon"),
            QuestionSeed(8, 5, "Mizu o __.", "kudasai")
        )

        db.beginTransaction()
        try {
            rules.forEach { rule ->
                db.execSQL(
                    "INSERT INTO GrammarRules (rule_id, category, explanation) VALUES (?, ?, ?)",
                    arrayOf<Any>(rule.id, rule.category, rule.explanation)
                )
            }
            questions.forEach { question ->
                db.execSQL(
                    """
                    INSERT INTO PracticeQuestions
                    (question_id, rule_id, prompt_sentence, correct_answer)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(question.id, question.ruleId, question.prompt, question.answer)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private data class RuleSeed(val id: Int, val category: String, val explanation: String)
    private data class QuestionSeed(val id: Int, val ruleId: Int, val prompt: String, val answer: String)

    companion object {
        private const val DB_NAME = "Fundaments.db"
        private const val DB_VERSION = 2
    }
}
