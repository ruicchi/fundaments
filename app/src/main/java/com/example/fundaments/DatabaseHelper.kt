package com.example.fundaments

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.roundToInt

data class UserProfile(
    val userId: Int,
    val learnerName: String,
    val age: Int?,
    val language: String?,
    val proficiency: String?
) {
    val isComplete: Boolean
        get() = age != null && !language.isNullOrBlank() && !proficiency.isNullOrBlank()
}

data class PracticeQuestion(
    val questionId: Int,
    val ruleId: Int,
    val language: String,
    val proficiency: String,
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
            CREATE TABLE Users (
                user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                learner_name TEXT NOT NULL UNIQUE,
                age INTEGER,
                language TEXT,
                proficiency TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE GrammarRules (
                rule_id INTEGER PRIMARY KEY,
                language TEXT NOT NULL,
                proficiency TEXT NOT NULL,
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
                user_id INTEGER NOT NULL,
                question_id INTEGER NOT NULL,
                is_correct INTEGER NOT NULL,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(user_id) REFERENCES Users(user_id),
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
        db.execSQL("DROP TABLE IF EXISTS Users")
        onCreate(db)
    }

    fun findOrCreateUser(learnerName: String): Int {
        val cleanName = learnerName.trim()
        readableDatabase.rawQuery(
            "SELECT user_id FROM Users WHERE lower(learner_name) = lower(?)",
            arrayOf(cleanName)
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0)
        }

        val values = ContentValues().apply {
            put("learner_name", cleanName)
        }
        return writableDatabase.insert("Users", null, values).toInt()
    }

    fun getUserProfile(userId: Int): UserProfile? {
        readableDatabase.rawQuery(
            "SELECT user_id, learner_name, age, language, proficiency FROM Users WHERE user_id = ?",
            arrayOf(userId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return UserProfile(
                userId = cursor.getInt(0),
                learnerName = cursor.getString(1),
                age = if (cursor.isNull(2)) null else cursor.getInt(2),
                language = if (cursor.isNull(3)) null else cursor.getString(3),
                proficiency = if (cursor.isNull(4)) null else cursor.getString(4)
            )
        }
    }

    fun updateUserPreferences(userId: Int, age: Int, language: String, proficiency: String) {
        val values = ContentValues().apply {
            put("age", age)
            put("language", language)
            put("proficiency", proficiency)
        }
        writableDatabase.update("Users", values, "user_id = ?", arrayOf(userId.toString()))
    }

    fun logAnswer(userId: Int, questionId: Int, isCorrect: Boolean) {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("question_id", questionId)
            put("is_correct", if (isCorrect) 1 else 0)
        }
        writableDatabase.insert("DiagnosticLogs", null, values)
    }

    fun getRandomQuestion(language: String, proficiency: String): PracticeQuestion? {
        val query = """
            SELECT Q.question_id, Q.rule_id, G.language, G.proficiency, G.category,
                   G.explanation, Q.prompt_sentence, Q.correct_answer
            FROM PracticeQuestions Q
            INNER JOIN GrammarRules G ON Q.rule_id = G.rule_id
            WHERE G.language = ? AND G.proficiency = ?
            ORDER BY RANDOM()
            LIMIT 1
        """.trimIndent()

        readableDatabase.rawQuery(query, arrayOf(language, proficiency)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return PracticeQuestion(
                questionId = cursor.getInt(0),
                ruleId = cursor.getInt(1),
                language = cursor.getString(2),
                proficiency = cursor.getString(3),
                category = cursor.getString(4),
                explanation = cursor.getString(5),
                promptSentence = cursor.getString(6),
                correctAnswer = cursor.getString(7)
            )
        }
    }

    fun getDashboardStats(userId: Int): DashboardStats {
        return getStats(userId, monthlyOnly = false)
    }

    fun getMonthlyDashboardStats(userId: Int): DashboardStats {
        return getStats(userId, monthlyOnly = true)
    }

    private fun getStats(userId: Int, monthlyOnly: Boolean): DashboardStats {
        val monthClause = if (monthlyOnly) "AND timestamp >= datetime('now', 'start of month')" else ""
        val query = """
            SELECT COUNT(log_id),
                   COALESCE(SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN is_correct = 0 THEN 1 ELSE 0 END), 0)
            FROM DiagnosticLogs
            WHERE user_id = ? $monthClause
        """.trimIndent()

        readableDatabase.rawQuery(query, arrayOf(userId.toString())).use { cursor ->
            cursor.moveToFirst()
            val total = cursor.getInt(0)
            val correct = cursor.getInt(1)
            val incorrect = cursor.getInt(2)
            val accuracy = if (total == 0) 0 else ((correct.toDouble() / total) * 100).roundToInt()
            return DashboardStats(total, correct, incorrect, accuracy)
        }
    }

    fun getCategoryPerformance(userId: Int, limit: Int? = null): List<CategoryPerformance> {
        return getCategoryPerformance(userId = userId, limit = limit, monthlyOnly = false)
    }

    fun getMonthlyCategoryPerformance(userId: Int, limit: Int? = null): List<CategoryPerformance> {
        return getCategoryPerformance(userId = userId, limit = limit, monthlyOnly = true)
    }

    private fun getCategoryPerformance(userId: Int, limit: Int? = null, monthlyOnly: Boolean): List<CategoryPerformance> {
        val limitClause = limit?.let { " LIMIT $it" }.orEmpty()
        val monthClause = if (monthlyOnly) "AND L.timestamp >= datetime('now', 'start of month')" else ""
        val query = """
            SELECT G.category,
                   G.explanation,
                   COUNT(L.log_id) AS total_attempts,
                   COALESCE(SUM(CASE WHEN L.is_correct = 1 THEN 1 ELSE 0 END), 0) AS correct_answers,
                   COALESCE(SUM(CASE WHEN L.is_correct = 0 THEN 1 ELSE 0 END), 0) AS incorrect_answers
            FROM GrammarRules G
            INNER JOIN PracticeQuestions Q ON G.rule_id = Q.rule_id
            INNER JOIN DiagnosticLogs L ON Q.question_id = L.question_id
            WHERE L.user_id = ? $monthClause
            GROUP BY G.rule_id, G.category, G.explanation
            HAVING COUNT(L.log_id) > 0
            ORDER BY incorrect_answers DESC, total_attempts DESC, G.category ASC
            $limitClause
        """.trimIndent()

        val results = mutableListOf<CategoryPerformance>()
        readableDatabase.rawQuery(query, arrayOf(userId.toString())).use { cursor ->
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

    fun getQuestionCount(language: String, proficiency: String): Int {
        val query = """
            SELECT COUNT(Q.question_id)
            FROM PracticeQuestions Q
            INNER JOIN GrammarRules G ON Q.rule_id = G.rule_id
            WHERE G.language = ? AND G.proficiency = ?
        """.trimIndent()
        readableDatabase.rawQuery(query, arrayOf(language, proficiency)).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun seedDiagnosticContent(db: SQLiteDatabase) {
        val questions = buildQuestionSeeds()

        db.beginTransaction()
        try {
            questions.forEachIndexed { index, question ->
                val id = index + 1
                db.execSQL(
                    """
                    INSERT INTO GrammarRules
                    (rule_id, language, proficiency, category, explanation)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(id, question.language, question.proficiency, question.category, question.explanation)
                )
                db.execSQL(
                    """
                    INSERT INTO PracticeQuestions
                    (question_id, rule_id, prompt_sentence, correct_answer)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(id, id, question.prompt, question.answer)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun buildQuestionSeeds(): List<QuestionSeed> {
        val data = mutableListOf<QuestionSeed>()
        fun add(language: String, level: String, category: String, explanation: String, prompt: String, answer: String) {
            data += QuestionSeed(language, level, category, explanation, prompt, answer)
        }

        add("English", "No Background", "Basic Subject", "Simple English sentences often start with I, you, he, she, it, we, or they.", "__ am happy.", "I")
        add("English", "No Background", "Articles", "Use a before a singular noun that begins with a consonant sound.", "This is __ book.", "a")
        add("English", "No Background", "Plural Nouns", "Many regular plurals are made by adding s.", "I have two cat__.", "s")
        add("English", "Basic", "Verb Agreement", "Use is with one person or thing in the present tense.", "The dog __ small.", "is")
        add("English", "Basic", "Past Tense", "Many regular past tense verbs end in ed.", "Yesterday I play__ outside.", "ed")
        add("English", "Basic", "Question Words", "Where asks about a place.", "__ is your school?", "Where")
        add("English", "Intermediate", "Comparatives", "Use than when comparing two things.", "My bag is heavier __ yours.", "than")
        add("English", "Intermediate", "Present Perfect", "Use have with plural subjects in present perfect sentences.", "They __ finished the quiz.", "have")
        add("English", "Intermediate", "Conditionals", "Use would in the result part of many imagined situations.", "If I had time, I __ read more.", "would")

        add("Japanese", "No Background", "Topic Particle", "The particle wa marks the topic of the sentence.", "Watashi __ gakusei desu.", "wa")
        add("Japanese", "No Background", "Polite Ending", "Desu makes a simple noun sentence polite.", "Kore wa hon __.", "desu")
        add("Japanese", "No Background", "Object Particle", "The particle o marks the direct object.", "Mizu __ nomimasu.", "o")
        add("Japanese", "Basic", "Location Particle", "De marks where an action happens.", "Toshokan __ benkyou shimasu.", "de")
        add("Japanese", "Basic", "Past Polite Verb", "Tabemashita means ate in polite past tense.", "Kinou sushi o __.", "tabemashita")
        add("Japanese", "Basic", "Existence Particle", "Ni marks where something exists.", "Tsukue no ue __ hon ga arimasu.", "ni")
        add("Japanese", "Intermediate", "Reason Connector", "Kara can show a reason, like because.", "Ame desu __, ikimasen.", "kara")
        add("Japanese", "Intermediate", "Comparison", "Yori is used when saying than in comparisons.", "Neko wa inu __ chiisai desu.", "yori")
        add("Japanese", "Intermediate", "Request Form", "Kudasai makes a polite request.", "Mado o akete __.", "kudasai")

        add("Filipino", "No Background", "Simple Marker", "Ang marks the main focus of many Filipino sentences.", "__ bata ay masaya.", "Ang")
        add("Filipino", "No Background", "Linker", "Na links some describing words to nouns.", "Mabait __ bata.", "na")
        add("Filipino", "No Background", "Polite Word", "Po makes a sentence more respectful.", "Salamat __.", "po")
        add("Filipino", "Basic", "Possession", "Ko means my or mine.", "Aklat __ ito.", "ko")
        add("Filipino", "Basic", "Place Marker", "Sa marks a place or direction.", "Pupunta ako __ paaralan.", "sa")
        add("Filipino", "Basic", "Plural Marker", "Mga marks plural nouns.", "Ang __ bata ay naglalaro.", "mga")
        add("Filipino", "Intermediate", "Aspect", "Nag marks an ongoing or actor-focused action.", "__babasa ako ng libro.", "Nag")
        add("Filipino", "Intermediate", "Completed Action", "Kumain shows a completed eat action.", "__ ako ng kanin kahapon.", "Kumain")
        add("Filipino", "Intermediate", "Connector", "Dahil introduces a reason.", "__ umuulan, nasa bahay kami.", "Dahil")

        add("Spanish", "No Background", "Gender Article", "La is used before many singular feminine nouns.", "__ casa es grande.", "La")
        add("Spanish", "No Background", "Basic Verb", "Soy means I am for identity or description.", "Yo __ estudiante.", "soy")
        add("Spanish", "No Background", "Plural Article", "Los is used before plural masculine nouns.", "__ libros son rojos.", "Los")
        add("Spanish", "Basic", "Verb Estar", "Estoy means I am for location or temporary state.", "Yo __ en la escuela.", "estoy")
        add("Spanish", "Basic", "Adjective Agreement", "Adjectives often match singular nouns in number.", "El gato es peque__.", "no")
        add("Spanish", "Basic", "Question Word", "Donde asks where.", "__ esta la biblioteca?", "Donde")
        add("Spanish", "Intermediate", "Past Tense", "Comi means I ate.", "Ayer yo __ arroz.", "comi")
        add("Spanish", "Intermediate", "Future Phrase", "Voy a means I am going to.", "Yo __ leer manana.", "voy a")
        add("Spanish", "Intermediate", "Comparison", "Que is used after mas in comparisons.", "El sol es mas grande __ la luna.", "que")

        add("Mandarin", "No Background", "Pronoun", "Wo means I or me.", "__ shi xuesheng.", "Wo")
        add("Mandarin", "No Background", "Question Particle", "Ma turns many statements into yes or no questions.", "Ni hao __?", "ma")
        add("Mandarin", "No Background", "Negation", "Bu means not before many verbs and adjectives.", "Wo __ yao.", "bu")
        add("Mandarin", "Basic", "Possession Particle", "De can show possession.", "Wo __ shu.", "de")
        add("Mandarin", "Basic", "Measure Word", "Ge is a common measure word.", "Yi __ ren.", "ge")
        add("Mandarin", "Basic", "Location Verb", "Zai means to be at a place.", "Wo __ xuexiao.", "zai")
        add("Mandarin", "Intermediate", "Completed Action", "Le often marks a completed action.", "Wo chi fan __.", "le")
        add("Mandarin", "Intermediate", "Experience Marker", "Guo marks past experience.", "Wo qu __ Beijing.", "guo")
        add("Mandarin", "Intermediate", "Comparison", "Bi is used in comparison sentences.", "Wo __ ni gao.", "bi")

        return data
    }

    private data class QuestionSeed(
        val language: String,
        val proficiency: String,
        val category: String,
        val explanation: String,
        val prompt: String,
        val answer: String
    )

    companion object {
        const val LANGUAGE_ENGLISH = "English"
        const val LANGUAGE_JAPANESE = "Japanese"
        const val LANGUAGE_FILIPINO = "Filipino"
        const val LANGUAGE_SPANISH = "Spanish"
        const val LANGUAGE_MANDARIN = "Mandarin"
        const val LEVEL_NO_BACKGROUND = "No Background"
        const val LEVEL_BASIC = "Basic"
        const val LEVEL_INTERMEDIATE = "Intermediate"

        val LANGUAGES = listOf(LANGUAGE_ENGLISH, LANGUAGE_JAPANESE, LANGUAGE_FILIPINO, LANGUAGE_SPANISH, LANGUAGE_MANDARIN)
        val PROFICIENCY_LEVELS = listOf(LEVEL_NO_BACKGROUND, LEVEL_BASIC, LEVEL_INTERMEDIATE)

        private const val DB_NAME = "Fundaments.db"
        private const val DB_VERSION = 3
    }
}
