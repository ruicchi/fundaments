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
    val instruction: String,
    val promptSentence: String,
    val correctAnswer: String,
    val options: List<String>
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
                instruction TEXT NOT NULL,
                prompt_sentence TEXT NOT NULL,
                correct_answer TEXT NOT NULL,
                option_a TEXT NOT NULL,
                option_b TEXT NOT NULL,
                option_c TEXT NOT NULL,
                option_d TEXT NOT NULL,
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
                selected_answer TEXT,
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

    fun logAnswer(userId: Int, questionId: Int, selectedAnswer: String, isCorrect: Boolean) {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("question_id", questionId)
            put("selected_answer", selectedAnswer)
            put("is_correct", if (isCorrect) 1 else 0)
        }
        writableDatabase.insert("DiagnosticLogs", null, values)
    }

    fun getRandomQuestion(language: String, proficiency: String): PracticeQuestion? {
        val query = """
            SELECT Q.question_id, Q.rule_id, G.language, G.proficiency, G.category,
                   G.explanation, Q.instruction, Q.prompt_sentence, Q.correct_answer,
                   Q.option_a, Q.option_b, Q.option_c, Q.option_d
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
                instruction = cursor.getString(6),
                promptSentence = cursor.getString(7),
                correctAnswer = cursor.getString(8),
                options = listOf(cursor.getString(9), cursor.getString(10), cursor.getString(11), cursor.getString(12))
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
                    (question_id, rule_id, instruction, prompt_sentence, correct_answer, option_a, option_b, option_c, option_d)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        id,
                        id,
                        question.instruction,
                        question.prompt,
                        question.answer,
                        question.options[0],
                        question.options[1],
                        question.options[2],
                        question.options[3]
                    )
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun buildQuestionSeeds(): List<QuestionSeed> {
        val data = mutableListOf<QuestionSeed>()
        addEnglish(data)
        addJapanese(data)
        addFilipino(data)
        addSpanish(data)
        addMandarin(data)
        return data
    }

    private fun addSet(
        data: MutableList<QuestionSeed>,
        language: String,
        level: String,
        explanation: String,
        questions: List<RawQuestion>
    ) {
        questions.forEachIndexed { index, raw ->
            val rotateBy = index % 4
            val options = listOf(raw.answer, raw.wrong1, raw.wrong2, raw.wrong3).rotate(rotateBy)
            data += QuestionSeed(language, level, raw.category, explanation, raw.instruction, raw.prompt, raw.answer, options)
        }
    }

    private fun <T> List<T>.rotate(amount: Int): List<T> {
        if (isEmpty()) return this
        val shift = amount % size
        return drop(shift) + take(shift)
    }

    private fun addEnglish(data: MutableList<QuestionSeed>) {
        addSet(data, LANGUAGE_ENGLISH, LEVEL_NO_BACKGROUND, "Choose the word that completes a simple English sentence.", listOf(
            RawQuestion("Pronouns", "Pick the subject word for talking about yourself.", "__ am seven years old.", "I", "Me", "My", "Mine"),
            RawQuestion("Articles", "Use a before one thing that starts with a consonant sound.", "This is __ pencil.", "a", "an", "thee", "are"),
            RawQuestion("Articles", "Use an before one thing that starts with a vowel sound.", "She has __ apple.", "an", "a", "the", "am"),
            RawQuestion("Plural Nouns", "Choose the ending that shows more than one regular noun.", "I see three dog__.", "s", "ed", "ing", "ly"),
            RawQuestion("Be Verbs", "Use is for one person, place, or thing.", "The sun __ bright.", "is", "are", "am", "be"),
            RawQuestion("Be Verbs", "Use are when talking about many things.", "The books __ red.", "are", "is", "am", "was"),
            RawQuestion("Action Words", "Choose the action word that fits the picture in your mind.", "Birds can __.", "fly", "book", "blue", "under"),
            RawQuestion("Question Words", "Who asks about a person.", "__ is your teacher?", "Who", "Where", "When", "Why"),
            RawQuestion("Question Words", "Where asks about a place.", "__ is your bag?", "Where", "Who", "What", "When"),
            RawQuestion("Prepositions", "In tells that something is inside.", "The toy is __ the box.", "in", "on", "from", "and"),
            RawQuestion("Prepositions", "On tells that something is touching the top.", "The cup is __ the table.", "on", "in", "under", "with"),
            RawQuestion("Colors", "Choose the color word that completes the sentence.", "The grass is __.", "green", "sleep", "runs", "seven"),
            RawQuestion("Numbers", "Choose the number word for 2.", "I have __ hands.", "two", "too", "to", "ten"),
            RawQuestion("Simple Negation", "Do not means the action is not happening.", "I __ not like cold soup.", "do", "does", "is", "has"),
            RawQuestion("Possession", "My shows something belongs to me.", "This is __ bag.", "my", "me", "I", "mine are"),
            RawQuestion("Commands", "Choose the action for a classroom command.", "__ your name.", "Write", "Blue", "Because", "Under"),
            RawQuestion("Feelings", "Choose the feeling word.", "I feel __ today.", "happy", "table", "runs", "ten"),
            RawQuestion("Family", "Choose the family word.", "My mother is my __.", "parent", "pencil", "window", "banana"),
            RawQuestion("Simple Objects", "Choose the school object.", "I read a __.", "book", "jump", "yellow", "quickly"),
            RawQuestion("Greetings", "Choose the greeting used in the morning.", "__ morning!", "Good", "Run", "Cold", "Many")
        ))

        addSet(data, LANGUAGE_ENGLISH, LEVEL_BASIC, "Read the sentence and choose the grammar form that makes it correct.", listOf(
            RawQuestion("Verb Agreement", "He, she, and it often use verbs with s in the present tense.", "She __ to school.", "walks", "walk", "walking", "walked"),
            RawQuestion("Verb Agreement", "They uses the base verb in the present tense.", "They __ football.", "play", "plays", "played", "playing"),
            RawQuestion("Past Tense", "Use a past tense verb for yesterday.", "Yesterday, I __ my room.", "cleaned", "clean", "cleans", "cleaning"),
            RawQuestion("Past Tense", "Some past tense verbs change spelling.", "Last night, we __ dinner.", "ate", "eat", "eats", "eating"),
            RawQuestion("Future", "Will shows an action that happens later.", "Tomorrow, I __ read a story.", "will", "was", "did", "has"),
            RawQuestion("Present Continuous", "Am plus ing shows an action happening now.", "I am __ a picture.", "drawing", "draw", "drew", "draws"),
            RawQuestion("Question Words", "Why asks for a reason.", "__ are you late?", "Why", "Where", "Who", "When"),
            RawQuestion("Question Words", "When asks about time.", "__ is your birthday?", "When", "Where", "Who", "Why"),
            RawQuestion("Prepositions", "Under means below something.", "The cat is __ the chair.", "under", "between", "through", "before"),
            RawQuestion("Prepositions", "Beside means next to something.", "The boy sits __ his friend.", "beside", "inside", "above", "during"),
            RawQuestion("Conjunctions", "And joins matching ideas.", "I like apples __ bananas.", "and", "but", "or", "because"),
            RawQuestion("Conjunctions", "Because gives a reason.", "I drink water __ I am thirsty.", "because", "and", "but", "or"),
            RawQuestion("Adjectives", "Adjectives describe nouns.", "The __ flower smells nice.", "pretty", "quickly", "jump", "under"),
            RawQuestion("Adverbs", "Adverbs can describe how an action happens.", "She sings __.", "softly", "soft", "song", "singer"),
            RawQuestion("Comparatives", "Add er to many short adjectives when comparing two things.", "This box is __ than that one.", "smaller", "small", "smallest", "smally"),
            RawQuestion("Superlatives", "Use est for the highest degree among three or more.", "Ana is the __ runner.", "fastest", "faster", "fast", "fastly"),
            RawQuestion("Possessives", "His shows something belongs to a boy or man.", "Mark forgot __ pencil.", "his", "her", "their", "our"),
            RawQuestion("Possessives", "Their shows something belongs to many people.", "The children opened __ books.", "their", "his", "her", "its"),
            RawQuestion("Object Pronouns", "Use me after an action done to yourself.", "Please help __.", "me", "I", "my", "mine"),
            RawQuestion("Sentence Order", "English usually uses subject, verb, then object.", "The girl __ the ball.", "kicks", "ball", "the", "girl")
        ))

        addSet(data, LANGUAGE_ENGLISH, LEVEL_INTERMEDIATE, "Choose the answer that best completes the sentence using stronger grammar control.", listOf(
            RawQuestion("Present Perfect", "Have plus past participle connects past action to now.", "They __ finished their homework.", "have", "has", "had", "having"),
            RawQuestion("Present Perfect", "Use has with one subject.", "Mia __ visited that museum.", "has", "have", "had", "having"),
            RawQuestion("Conditionals", "Would often appears in imagined results.", "If I had time, I __ practice more.", "would", "will", "am", "did"),
            RawQuestion("Conditionals", "Use were in this imagined situation.", "If I __ taller, I could reach it.", "were", "was", "am", "be"),
            RawQuestion("Passive Voice", "A passive sentence uses be plus past participle.", "The cake was __ by Sam.", "baked", "bake", "baking", "bakes"),
            RawQuestion("Relative Clauses", "Who describes people.", "The girl __ won is my friend.", "who", "which", "where", "when"),
            RawQuestion("Relative Clauses", "Which describes things.", "The book __ I borrowed is funny.", "which", "who", "where", "why"),
            RawQuestion("Gerunds", "After enjoy, use an ing form.", "I enjoy __ stories.", "reading", "read", "reads", "readed"),
            RawQuestion("Infinitives", "After want, use to plus base verb.", "He wants __ learn Spanish.", "to", "for", "at", "by"),
            RawQuestion("Modal Verbs", "Should gives advice.", "You __ rest when you are sick.", "should", "musted", "does", "were"),
            RawQuestion("Modal Verbs", "Might shows possibility.", "It __ rain later.", "might", "musted", "is", "does"),
            RawQuestion("Comparatives", "Use than after many comparative adjectives.", "My bag is heavier __ yours.", "than", "then", "that", "there"),
            RawQuestion("Reported Speech", "Said is commonly used to report speech.", "She __ that she was ready.", "said", "told", "spoke", "asked to"),
            RawQuestion("Complex Sentences", "Although starts a contrast.", "__ it was raining, we played inside.", "Although", "Because", "Before", "If only"),
            RawQuestion("Transitions", "However shows contrast between ideas.", "I studied hard. __, the test was difficult.", "However", "Also", "Because", "Then"),
            RawQuestion("Prepositions", "Interested is often followed by in.", "She is interested __ science.", "in", "on", "at", "for"),
            RawQuestion("Prepositions", "Good is often followed by at for skills.", "He is good __ drawing.", "at", "in", "on", "to"),
            RawQuestion("Quantifiers", "Many is used with countable plural nouns.", "There are __ students in class.", "many", "much", "little", "any one"),
            RawQuestion("Quantifiers", "Much is used with uncountable nouns.", "How __ water do we need?", "much", "many", "few", "several"),
            RawQuestion("Sentence Clarity", "Choose the phrase that completes a clear cause-effect sentence.", "She missed the bus, so she __ late.", "arrived", "arrive", "arriving", "arrival")
        ))
    }

    private fun addJapanese(data: MutableList<QuestionSeed>) {
        addSet(data, LANGUAGE_JAPANESE, LEVEL_NO_BACKGROUND, "Choose the romaji word or particle that completes the simple Japanese sentence.", listOf(
            RawQuestion("Topic Particle", "Wa marks the topic of the sentence.", "Watashi __ gakusei desu.", "wa", "ga", "o", "ni"),
            RawQuestion("Polite Ending", "Desu makes a noun sentence polite.", "Kore wa hon __.", "desu", "masu", "wa", "o"),
            RawQuestion("Object Particle", "O marks the thing receiving the action.", "Mizu __ nomimasu.", "o", "wa", "ga", "de"),
            RawQuestion("Greetings", "Ohayou means good morning.", "__ gozaimasu.", "Ohayou", "Arigatou", "Sayonara", "Gomen"),
            RawQuestion("Thanks", "Arigatou means thank you.", "__ gozaimasu.", "Arigatou", "Ohayou", "Konbanwa", "Hai"),
            RawQuestion("Yes No", "Hai means yes.", "__, sou desu.", "Hai", "Iie", "Doko", "Dare"),
            RawQuestion("No", "Iie means no.", "__, chigaimasu.", "Iie", "Hai", "Nani", "Kore"),
            RawQuestion("This", "Kore means this.", "__ wa pen desu.", "Kore", "Doko", "Dare", "Itsu"),
            RawQuestion("That", "Sore means that near the listener.", "__ wa isu desu.", "Sore", "Watashi", "Nani", "Doko"),
            RawQuestion("Person", "Sensei means teacher.", "Tanaka-san wa __ desu.", "sensei", "hon", "mizu", "inu"),
            RawQuestion("Question Particle", "Ka marks many polite questions.", "Gakusei desu __.", "ka", "wa", "o", "ni"),
            RawQuestion("Name", "Namae means name.", "Onamae wa __ desu ka.", "nan", "doko", "itsu", "daremo"),
            RawQuestion("Place", "Gakkou means school.", "Koko wa __ desu.", "gakkou", "taberu", "akai", "hashiru"),
            RawQuestion("Noun Link", "No can connect nouns, like my book.", "Watashi __ hon desu.", "no", "o", "de", "ka"),
            RawQuestion("Subject Particle", "Ga can mark the subject being identified.", "Neko __ imasu.", "ga", "o", "wa", "kara"),
            RawQuestion("Location", "Koko means here.", "__ wa shizuka desu.", "Koko", "Dare", "Nani", "Itsu"),
            RawQuestion("Basic Verb", "Nomimasu means drink.", "Mizu o __.", "nomimasu", "tabemasu", "ikimasu", "mimasu"),
            RawQuestion("Basic Verb", "Tabemasu means eat.", "Pan o __.", "tabemasu", "nomimasu", "kikimasu", "kaerimasu"),
            RawQuestion("Color", "Akai means red.", "Ringo wa __ desu.", "akai", "aoi", "kuroi", "shiroi"),
            RawQuestion("Polite Request", "Kudasai means please give or please do.", "Mizu o __.", "kudasai", "desu", "wa", "ka")
        ))

        addSet(data, LANGUAGE_JAPANESE, LEVEL_BASIC, "Choose the particle or verb form that makes the Japanese sentence correct.", listOf(
            RawQuestion("Location Particle", "De marks where an action happens.", "Toshokan __ benkyou shimasu.", "de", "ni", "o", "wa"),
            RawQuestion("Existence Particle", "Ni marks where something exists.", "Tsukue no ue __ hon ga arimasu.", "ni", "de", "o", "ka"),
            RawQuestion("Past Polite Verb", "Mashita marks polite past tense.", "Kinou sushi o tabe__.", "mashita", "masu", "nai", "tai"),
            RawQuestion("Future Movement", "Ikimasu means go.", "Ashita gakkou e __.", "ikimasu", "kimashita", "tabemasu", "arimasu"),
            RawQuestion("Direction Particle", "E marks direction toward a place.", "Tokyo __ ikimasu.", "e", "o", "ga", "wa"),
            RawQuestion("Time Particle", "Ni can mark specific time.", "San-ji __ kaerimasu.", "ni", "de", "o", "wa"),
            RawQuestion("And for Nouns", "To connects nouns.", "Pen __ hon ga arimasu.", "to", "de", "kara", "made"),
            RawQuestion("Also", "Mo means also or too.", "Watashi __ gakusei desu.", "mo", "o", "ni", "e"),
            RawQuestion("Past Be", "Deshita is polite past of desu.", "Kinou wa ame __.", "deshita", "desu", "masu", "mashita"),
            RawQuestion("Negative Be", "Dewa arimasen means is not.", "Kore wa enpitsu __.", "dewa arimasen", "deshita", "mashita", "ikimasu"),
            RawQuestion("Adjective", "Oishii means delicious.", "Ramen wa __ desu.", "oishii", "samui", "hayai", "atarashii"),
            RawQuestion("Question Word", "Doko asks where.", "__ e ikimasu ka.", "Doko", "Dare", "Nani", "Itsu"),
            RawQuestion("Question Word", "Itsu asks when.", "__ benkyou shimasu ka.", "Itsu", "Dare", "Doko", "Nani"),
            RawQuestion("Frequency", "Mainichi means every day.", "__ nihongo o benkyou shimasu.", "Mainichi", "Dare", "Kore", "Aka"),
            RawQuestion("Like", "Suki means like.", "Watashi wa neko ga __ desu.", "suki", "arimasu", "ikimasu", "kaerimasu"),
            RawQuestion("Want", "Tai shows want to do.", "Mizu o nomi__ desu.", "tai", "mashita", "nai", "deshita"),
            RawQuestion("Negative Verb", "Masen makes a polite verb negative.", "Kyou wa ikima__.", "sen", "shita", "su", "tai"),
            RawQuestion("Counters", "Nin counts people.", "Tomodachi ga san-__ imasu.", "nin", "hon", "mai", "ko"),
            RawQuestion("Counters", "Hon counts long thin objects.", "Enpitsu ga ni-__ arimasu.", "hon", "nin", "mai", "satsu"),
            RawQuestion("Invitation", "Mashou suggests doing something together.", "Issho ni benkyou shi__.", "mashou", "mashita", "masen", "deshita")
        ))

        addSet(data, LANGUAGE_JAPANESE, LEVEL_INTERMEDIATE, "Choose the connector, particle, or form that completes the more complex sentence.", listOf(
            RawQuestion("Reason Connector", "Kara gives a reason, like because.", "Ame desu __, ikimasen.", "kara", "made", "yori", "to"),
            RawQuestion("Comparison", "Yori means than in comparisons.", "Neko wa inu __ chiisai desu.", "yori", "kara", "made", "de"),
            RawQuestion("Request Form", "Te kudasai makes a polite request.", "Mado o akete __.", "kudasai", "desu", "mashita", "nai"),
            RawQuestion("Te Form", "Use te form before kudasai.", "Hon o yonde __.", "kudasai", "deshita", "masu", "tai"),
            RawQuestion("Permission", "Mo ii desu means may or is allowed.", "Koko de tabete __ desu ka.", "mo ii", "kara", "yori", "made"),
            RawQuestion("Prohibition", "Dame means not allowed.", "Koko de hashitte wa __ desu.", "dame", "suki", "jouzu", "hayai"),
            RawQuestion("Experience", "Koto ga arimasu can describe experience.", "Nihon e itta __ ga arimasu.", "koto", "mono", "hito", "tokoro"),
            RawQuestion("Ability", "Koto ga dekimasu can describe ability.", "Kanji o yomu __ ga dekimasu.", "koto", "kara", "yori", "made"),
            RawQuestion("Before", "Mae ni means before.", "Neru __, ha o migakimasu.", "mae ni", "ato de", "kara", "yori"),
            RawQuestion("After", "Ato de means after.", "Shukudai no __, asobimasu.", "ato de", "mae ni", "made", "yori"),
            RawQuestion("Giving Reason", "Node gives a softer reason.", "Byouki na __, yasumimasu.", "node", "yori", "made", "to"),
            RawQuestion("But", "Demo means but.", "Benkyou shimashita. __ muzukashikatta desu.", "Demo", "Dakara", "Soshite", "Mata"),
            RawQuestion("Therefore", "Dakara means therefore.", "Ame desu. __ ie ni imasu.", "Dakara", "Demo", "Mata", "Sore"),
            RawQuestion("Trying", "Te mimashita means tried doing.", "Atarashii ryouri o tabete __.", "mimashita", "kudasai", "imasu", "arimasu"),
            RawQuestion("Ongoing Action", "Te imasu can show ongoing action.", "Ima, hon o yonde __.", "imasu", "arimasu", "desu", "mashita"),
            RawQuestion("State Result", "Te arimasu can show a resulting state.", "Mado ga akete __.", "arimasu", "imasu", "desu", "kara"),
            RawQuestion("Must", "Nakereba narimasen means must do.", "Benkyou shinakereba __.", "narimasen", "ikemasen ka", "ii desu", "suki desu"),
            RawQuestion("Do Not Have To", "Nakute mo ii means do not have to.", "Kyou wa ikanaku __ desu.", "temo ii", "kara", "yori", "made"),
            RawQuestion("Plan", "Tsumori means plan or intention.", "Ashita benkyou suru __ desu.", "tsumori", "koto", "mono", "hito"),
            RawQuestion("Looks Like", "Sou desu can mean looks like after adjective stems.", "Oishishi__ desu.", "sou", "kara", "made", "yori")
        ))
    }

    private fun addFilipino(data: MutableList<QuestionSeed>) {
        addSet(data, LANGUAGE_FILIPINO, LEVEL_NO_BACKGROUND, "Piliin ang salitang bubuo sa simpleng pangungusap.", listOf(
            RawQuestion("Focus Marker", "Ang marks the focus of many Filipino sentences.", "__ bata ay masaya.", "Ang", "Ng", "Sa", "Kay"),
            RawQuestion("Plural Marker", "Mga marks plural nouns.", "Ang __ bata ay naglalaro.", "mga", "si", "kay", "ng"),
            RawQuestion("Polite Word", "Po makes a sentence more respectful.", "Salamat __.", "po", "ba", "ang", "ng"),
            RawQuestion("Question Particle", "Ba can mark a yes or no question.", "Masaya ka __?", "ba", "po", "ng", "sa"),
            RawQuestion("Linker", "Na links some descriptions to nouns.", "Mabait __ bata.", "na", "ng", "sa", "po"),
            RawQuestion("Pronoun", "Ako means I or me.", "__ ay mag-aaral.", "Ako", "Ikaw", "Siya", "Sila"),
            RawQuestion("Pronoun", "Ikaw means you.", "__ ba ay handa na?", "Ikaw", "Ako", "Kami", "Sila"),
            RawQuestion("Place Marker", "Sa marks a place.", "Nasa __ ang lapis.", "mesa", "kumain", "masaya", "tatlo"),
            RawQuestion("Object Marker", "Ng can mark an object.", "Kumain ako __ mansanas.", "ng", "ang", "sa", "kay"),
            RawQuestion("Person Marker", "Si marks a person's name.", "__ Ana ay mabait.", "Si", "Ang", "Ng", "Sa"),
            RawQuestion("Greeting", "Magandang umaga is a morning greeting.", "__ umaga.", "Magandang", "Kumain", "Lapis", "Bukas"),
            RawQuestion("Yes", "Oo means yes.", "__, gusto ko.", "Oo", "Hindi", "Bakit", "Saan"),
            RawQuestion("No", "Hindi means no or not.", "__, ayaw ko.", "Hindi", "Oo", "Sino", "Ano"),
            RawQuestion("Question Word", "Sino asks who.", "__ ang guro mo?", "Sino", "Saan", "Kailan", "Bakit"),
            RawQuestion("Question Word", "Saan asks where.", "__ ang paaralan?", "Saan", "Sino", "Ano", "Bakit"),
            RawQuestion("Thing Word", "Aklat means book.", "Nagbabasa ako ng __.", "aklat", "takbo", "pula", "masaya"),
            RawQuestion("Color", "Berde means green.", "Ang dahon ay __.", "berde", "aso", "takbo", "walo"),
            RawQuestion("Family", "Ina means mother.", "Ang nanay ay __.", "ina", "aklat", "paaralan", "laro"),
            RawQuestion("Action", "Laro means play.", "Gusto ko ng __.", "laro", "mesa", "pula", "bakit"),
            RawQuestion("Thanks", "Salamat means thank you.", "__ po.", "Salamat", "Paalam", "Bakit", "Saan")
        ))

        addSet(data, LANGUAGE_FILIPINO, LEVEL_BASIC, "Piliin ang tamang pananda, panghalip, o anyo ng pandiwa.", listOf(
            RawQuestion("Possession", "Ko means my or mine.", "Aklat __ ito.", "ko", "ka", "siya", "kami"),
            RawQuestion("Place Marker", "Sa marks a place or direction.", "Pupunta ako __ paaralan.", "sa", "ng", "ang", "si"),
            RawQuestion("Plural Marker", "Mga marks more than one noun.", "May __ lapis sa mesa.", "mga", "si", "kay", "po"),
            RawQuestion("Question Word", "Kailan asks when.", "__ ang klase?", "Kailan", "Sino", "Saan", "Ano"),
            RawQuestion("Question Word", "Bakit asks why.", "__ ka umiiyak?", "Bakit", "Saan", "Sino", "Kailan"),
            RawQuestion("Action Aspect", "Nag marks an actor-focused action.", "__lalaro ang bata.", "Nag", "Ang", "Sa", "Kay"),
            RawQuestion("Completed Action", "Kumain shows a completed eating action.", "__ ako ng kanin.", "Kumain", "Kakain", "Kain", "Kinakain"),
            RawQuestion("Future Action", "Kakain shows future eating action.", "__ ako mamaya.", "Kakain", "Kumain", "Kain", "Kinain"),
            RawQuestion("Ongoing Action", "Umiinom shows drinking now or generally.", "__ ako ng tubig.", "Umiinom", "Ininom", "Iinom", "Inumin"),
            RawQuestion("Object Focus", "Binasa means was read.", "__ ko ang libro.", "Binasa", "Babasa", "Nagbasa", "Basa"),
            RawQuestion("Linker", "Ng links some adjectives to nouns.", "Maganda__ bahay.", "ng", "na", "ang", "sa"),
            RawQuestion("Connector", "At means and.", "Si Ana __ si Ben ay magkaibigan.", "at", "pero", "dahil", "kung"),
            RawQuestion("Contrast", "Pero means but.", "Gusto kong maglaro, __ umuulan.", "pero", "at", "dahil", "kung"),
            RawQuestion("Reason", "Dahil gives a reason.", "Nasa bahay ako __ umuulan.", "dahil", "pero", "at", "kung"),
            RawQuestion("Condition", "Kung introduces if.", "__ may oras, magbabasa ako.", "Kung", "Dahil", "Pero", "At"),
            RawQuestion("Direction", "Papunta means going to.", "__ kami sa parke.", "Papunta", "Galing", "Nasa", "Mayroon"),
            RawQuestion("From", "Galing means came from.", "__ ako sa tindahan.", "Galing", "Papunta", "Nasa", "Mayroon"),
            RawQuestion("Existence", "May means there is or have.", "__ lapis ako.", "May", "Nasa", "Galing", "Papunta"),
            RawQuestion("Respect", "Opo means respectful yes.", "__, guro.", "Opo", "Hindi", "Saan", "Ano"),
            RawQuestion("Negation", "Hindi makes a sentence negative.", "__ ako pagod.", "Hindi", "Oo", "May", "Nasa")
        ))

        addSet(data, LANGUAGE_FILIPINO, LEVEL_INTERMEDIATE, "Piliin ang sagot na pinakamainam para sa mas mahabang pangungusap.", listOf(
            RawQuestion("Aspect", "Nag marks an ongoing or actor-focused action.", "__babasa ako ng libro.", "Nag", "Mag", "Um", "In"),
            RawQuestion("Completed Action", "Kumain shows completed action.", "__ ako ng kanin kahapon.", "Kumain", "Kakain", "Kain", "Kinakain"),
            RawQuestion("Reason Connector", "Dahil introduces a reason.", "__ umuulan, nasa bahay kami.", "Dahil", "Pero", "At", "Kung"),
            RawQuestion("Cause Effect", "Kaya introduces a result.", "Nag-aral ako, __ pumasa ako.", "kaya", "pero", "kung", "dahil"),
            RawQuestion("Contrast", "Kahit means even though.", "__ mahirap, susubukan ko.", "Kahit", "Kaya", "Dahil", "At"),
            RawQuestion("Condition", "Kapag means when or if.", "__ tapos na, magpahinga ka.", "Kapag", "Pero", "Kaya", "Dahil"),
            RawQuestion("Object Focus", "Isinulat means something was written.", "__ ko ang sagot.", "Isinulat", "Sumulat", "Susulat", "Sulat"),
            RawQuestion("Benefactive", "Para sa means for someone.", "Ito ay __ bata.", "para sa", "mula sa", "tungkol sa", "dahil sa"),
            RawQuestion("About", "Tungkol sa means about.", "Ang kwento ay __ pamilya.", "tungkol sa", "para sa", "mula sa", "nasa"),
            RawQuestion("From", "Mula sa means from.", "Ang regalo ay __ guro.", "mula sa", "para sa", "tungkol sa", "dahil sa"),
            RawQuestion("Obligation", "Kailangan means need to.", "__ kong mag-aral.", "Kailangan", "Gusto", "Ayaw", "Sana"),
            RawQuestion("Wish", "Sana expresses hope or wish.", "__ makapasa ako.", "Sana", "Dahil", "Kaya", "Pero"),
            RawQuestion("Ability", "Kaya can mean able to.", "__ kong basahin ito.", "Kaya", "Dahil", "Pero", "Kung"),
            RawQuestion("Sequence", "Pagkatapos means after.", "__ kumain, naghugas ako.", "Pagkatapos", "Bago", "Habang", "Kahit"),
            RawQuestion("Before", "Bago means before.", "__ matulog, nagdasal ako.", "Bago", "Pagkatapos", "Kahit", "Kaya"),
            RawQuestion("While", "Habang means while.", "__ nag-aaral, tahimik siya.", "Habang", "Bago", "Pagkatapos", "Dahil"),
            RawQuestion("Respectful Request", "Paki makes a polite request.", "__ abot ang aklat.", "Paki", "Kahit", "Dahil", "Kaya"),
            RawQuestion("Emphasis", "Talaga means really.", "Masaya __ ako.", "talaga", "bukas", "mesa", "saan"),
            RawQuestion("Possession", "Akin means mine.", "Ang lapis ay __.", "akin", "ako", "ko", "kami"),
            RawQuestion("Inclusive We", "Tayo includes the listener.", "__ ay mag-aral na.", "Tayo", "Kami", "Sila", "Siya")
        ))
    }

    private fun addSpanish(data: MutableList<QuestionSeed>) {
        addSet(data, LANGUAGE_SPANISH, LEVEL_NO_BACKGROUND, "Choose the Spanish word that completes the beginner sentence.", listOf(
            RawQuestion("Article Gender", "La is used before many feminine singular nouns.", "__ casa es grande.", "La", "El", "Los", "Unos"),
            RawQuestion("Article Gender", "El is used before many masculine singular nouns.", "__ libro es rojo.", "El", "La", "Las", "Unas"),
            RawQuestion("Basic Verb", "Soy means I am for identity.", "Yo __ estudiante.", "soy", "eres", "es", "somos"),
            RawQuestion("Basic Verb", "Es means is for he, she, or it.", "Ella __ amable.", "es", "soy", "eres", "son"),
            RawQuestion("Plural Article", "Los is used before plural masculine nouns.", "__ libros son rojos.", "Los", "El", "La", "Una"),
            RawQuestion("Plural Article", "Las is used before plural feminine nouns.", "__ mesas son altas.", "Las", "La", "El", "Un"),
            RawQuestion("Greeting", "Hola means hello.", "__, amiga.", "Hola", "Adios", "Gracias", "Rojo"),
            RawQuestion("Thanks", "Gracias means thank you.", "__ por ayudar.", "Gracias", "Hola", "Adios", "Casa"),
            RawQuestion("Yes", "Si means yes in Spanish.", "__, quiero agua.", "Si", "No", "Donde", "Cuando"),
            RawQuestion("No", "No means no or not.", "__, no quiero.", "No", "Si", "Quien", "Casa"),
            RawQuestion("Pronoun", "Yo means I.", "__ leo.", "Yo", "Tu", "El", "Ellos"),
            RawQuestion("Pronoun", "Tu means you.", "__ cantas.", "Tu", "Yo", "Ella", "Nosotros"),
            RawQuestion("Color", "Rojo means red.", "El carro es __.", "rojo", "libro", "correr", "cinco"),
            RawQuestion("Number", "Dos means two.", "Tengo __ manos.", "dos", "uno", "tres", "diez"),
            RawQuestion("Question Word", "Que asks what.", "__ es esto?", "Que", "Donde", "Cuando", "Quien"),
            RawQuestion("Question Word", "Quien asks who.", "__ es tu maestro?", "Quien", "Que", "Donde", "Cuando"),
            RawQuestion("Place", "Escuela means school.", "Voy a la __.", "escuela", "comer", "azul", "feliz"),
            RawQuestion("Family", "Madre means mother.", "Mi mama es mi __.", "madre", "libro", "silla", "perro"),
            RawQuestion("Object", "Libro means book.", "Leo un __.", "libro", "correr", "verde", "siete"),
            RawQuestion("Farewell", "Adios means goodbye.", "__, amigo.", "Adios", "Hola", "Gracias", "Blanco")
        ))

        addSet(data, LANGUAGE_SPANISH, LEVEL_BASIC, "Choose the article, verb, or grammar word that completes the Spanish sentence.", listOf(
            RawQuestion("Verb Estar", "Estoy means I am for location or temporary state.", "Yo __ en la escuela.", "estoy", "soy", "eres", "es"),
            RawQuestion("Verb Estar", "Esta means is for location or temporary state.", "Ella __ cansada.", "esta", "estoy", "estas", "estan"),
            RawQuestion("Verb Ser", "Somos means we are.", "Nosotros __ estudiantes.", "somos", "soy", "eres", "son"),
            RawQuestion("Adjective Agreement", "Pequeno describes a singular masculine noun.", "El gato es peque__.", "no", "na", "nos", "nas"),
            RawQuestion("Adjective Agreement", "Pequena describes a singular feminine noun.", "La casa es peque__.", "na", "no", "nos", "nas"),
            RawQuestion("Plural Agreement", "Rojas describes plural feminine nouns.", "Las flores son __.", "rojas", "rojo", "roja", "rojos"),
            RawQuestion("Question Word", "Donde asks where.", "__ esta la biblioteca?", "Donde", "Quien", "Cuando", "Por que"),
            RawQuestion("Question Word", "Cuando asks when.", "__ es la clase?", "Cuando", "Donde", "Quien", "Que"),
            RawQuestion("Possession", "Mi means my.", "Este es __ lapiz.", "mi", "tuya", "suyo", "nuestros"),
            RawQuestion("Possession", "Tu means your.", "Es __ mochila.", "tu", "mi", "suya", "nuestros"),
            RawQuestion("Present Tense", "Como means I eat.", "Yo __ arroz.", "como", "comes", "come", "comen"),
            RawQuestion("Present Tense", "Come means he or she eats.", "El __ pan.", "come", "como", "comes", "comen"),
            RawQuestion("Future Phrase", "Voy a means I am going to.", "Yo __ estudiar.", "voy a", "vas a", "va a", "van a"),
            RawQuestion("Negation", "No comes before the verb for negation.", "Yo __ tengo tarea.", "no", "si", "nunca de", "nadie en"),
            RawQuestion("Conjunction", "Y means and.", "Leo __ escribo.", "y", "pero", "porque", "o"),
            RawQuestion("Contrast", "Pero means but.", "Quiero jugar, __ debo estudiar.", "pero", "y", "porque", "o"),
            RawQuestion("Reason", "Porque means because.", "Estudio __ tengo examen.", "porque", "pero", "y", "o"),
            RawQuestion("Preposition", "En can mean in or on.", "El libro esta __ la mesa.", "en", "con", "por", "para"),
            RawQuestion("Preposition", "Con means with.", "Voy __ mi amigo.", "con", "en", "por", "sin"),
            RawQuestion("Plural", "Add s to many vowel-ending nouns.", "Tengo dos casa__.", "s", "es", "os", "as")
        ))

        addSet(data, LANGUAGE_SPANISH, LEVEL_INTERMEDIATE, "Choose the form that best completes the more advanced Spanish sentence.", listOf(
            RawQuestion("Past Tense", "Comi means I ate.", "Ayer yo __ arroz.", "comi", "como", "comere", "comiendo"),
            RawQuestion("Past Tense", "Fui means I went.", "Ayer __ a la escuela.", "fui", "voy", "iba", "ire"),
            RawQuestion("Future Phrase", "Voy a means I am going to.", "Yo __ leer manana.", "voy a", "fui a", "estoy a", "soy a"),
            RawQuestion("Comparison", "Que is used after mas in comparisons.", "El sol es mas grande __ la luna.", "que", "de", "con", "por"),
            RawQuestion("Comparison", "Menos que means less than.", "Este libro es menos dificil __ aquel.", "que", "de", "por", "para"),
            RawQuestion("Present Perfect", "He plus participle can mean I have done.", "Yo __ terminado.", "he", "ha", "han", "hemos"),
            RawQuestion("Present Perfect", "Ha is used with el or ella.", "Ella __ estudiado.", "ha", "he", "han", "hemos"),
            RawQuestion("Subjunctive Trigger", "Quiero que can trigger a new verb form.", "Quiero que tu __.", "estudies", "estudias", "estudiar", "estudiaste"),
            RawQuestion("Conditional", "Gustaria means would like.", "Me __ aprender mas.", "gustaria", "gusta", "gusto", "gustaba"),
            RawQuestion("Object Pronoun", "Lo can replace a masculine thing.", "Yo __ leo.", "lo", "la", "le", "les"),
            RawQuestion("Object Pronoun", "La can replace a feminine thing.", "Yo __ veo.", "la", "lo", "le", "les"),
            RawQuestion("Reflexive", "Me is used for myself.", "Yo __ levanto temprano.", "me", "te", "se", "nos"),
            RawQuestion("Reflexive", "Se is used for himself or herself.", "Ella __ lava las manos.", "se", "me", "te", "nos"),
            RawQuestion("Imperfect", "Era describes what something was like.", "Cuando era pequeno, yo __ timido.", "era", "fui", "soy", "estuve"),
            RawQuestion("Preterite", "Fue can mean was for completed events.", "La fiesta __ divertida.", "fue", "era", "es", "sera"),
            RawQuestion("Por Para", "Para can show purpose.", "Estudio __ aprender.", "para", "por", "con", "sin"),
            RawQuestion("Por Para", "Por can show reason.", "Gracias __ ayudarme.", "por", "para", "con", "sin"),
            RawQuestion("Relative Pronoun", "Que connects a described noun to more information.", "El libro __ compre es interesante.", "que", "quien", "donde", "cuando"),
            RawQuestion("Sequence", "Despues means after.", "__ de clase, juego.", "Despues", "Antes", "Aunque", "Si"),
            RawQuestion("Contrast", "Aunque means although.", "__ es dificil, practico.", "Aunque", "Porque", "Entonces", "Tambien")
        ))
    }

    private fun addMandarin(data: MutableList<QuestionSeed>) {
        addSet(data, LANGUAGE_MANDARIN, LEVEL_NO_BACKGROUND, "Choose the pinyin word that completes the beginner Mandarin sentence.", listOf(
            RawQuestion("Pronoun", "Wo means I or me.", "__ shi xuesheng.", "Wo", "Ni", "Ta", "Women"),
            RawQuestion("Pronoun", "Ni means you.", "__ hao.", "Ni", "Wo", "Ta", "Tamen"),
            RawQuestion("Question Particle", "Ma turns many statements into yes or no questions.", "Ni hao __?", "ma", "de", "le", "ge"),
            RawQuestion("Negation", "Bu means not before many verbs and adjectives.", "Wo __ yao.", "bu", "ma", "de", "le"),
            RawQuestion("Be Verb", "Shi means am, is, or are.", "Ta __ laoshi.", "shi", "you", "zai", "hen"),
            RawQuestion("Have", "You means have.", "Wo __ shu.", "you", "shi", "zai", "bu"),
            RawQuestion("Good", "Hao means good.", "Ni __.", "hao", "shu", "ren", "chi"),
            RawQuestion("Thanks", "Xiexie means thank you.", "__ ni.", "Xiexie", "Nihao", "Zaijian", "Laoshi"),
            RawQuestion("Goodbye", "Zaijian means goodbye.", "__, pengyou.", "Zaijian", "Xiexie", "Nihao", "Shu"),
            RawQuestion("Person", "Ren means person.", "Ta shi hao __.", "ren", "shu", "mao", "chi"),
            RawQuestion("Book", "Shu means book.", "Wo you yi ben __.", "shu", "ren", "gou", "shui"),
            RawQuestion("Water", "Shui means water.", "Wo yao __.", "shui", "shu", "ren", "hao"),
            RawQuestion("Eat", "Chi means eat.", "Wo __ fan.", "chi", "he", "kan", "qu"),
            RawQuestion("Drink", "He means drink.", "Wo __ shui.", "he", "chi", "kan", "qu"),
            RawQuestion("Look", "Kan means look or read.", "Wo __ shu.", "kan", "chi", "he", "qu"),
            RawQuestion("Go", "Qu means go.", "Wo __ xuexiao.", "qu", "kan", "he", "chi"),
            RawQuestion("Number", "Yi means one.", "__ ge ren.", "Yi", "Er", "San", "Shi"),
            RawQuestion("Number", "Er means two.", "__ ben shu.", "Er", "Yi", "San", "Wu"),
            RawQuestion("Color", "Hong means red.", "Pingguo shi __ de.", "hong", "lan", "hei", "bai"),
            RawQuestion("Teacher", "Laoshi means teacher.", "Ta shi __.", "laoshi", "shu", "shui", "mao")
        ))

        addSet(data, LANGUAGE_MANDARIN, LEVEL_BASIC, "Choose the pinyin grammar word or phrase that makes the Mandarin sentence correct.", listOf(
            RawQuestion("Possession", "De can show possession.", "Wo __ shu.", "de", "ma", "le", "ge"),
            RawQuestion("Measure Word", "Ge is a common measure word.", "Yi __ ren.", "ge", "ben", "zhi", "kou"),
            RawQuestion("Measure Word", "Ben is used for books.", "Yi __ shu.", "ben", "ge", "zhi", "tiao"),
            RawQuestion("Location Verb", "Zai means to be at a place.", "Wo __ xuexiao.", "zai", "shi", "you", "bu"),
            RawQuestion("Question Word", "Shenme asks what.", "Ni yao __?", "shenme", "shei", "nali", "weishenme"),
            RawQuestion("Question Word", "Shei asks who.", "__ shi laoshi?", "Shei", "Shenme", "Nali", "Ji"),
            RawQuestion("Question Word", "Nali asks where.", "Xuexiao zai __?", "nali", "shei", "shenme", "ji"),
            RawQuestion("Time", "Jintian means today.", "__ wo xuexi.", "Jintian", "Mingtian", "Zuotian", "Shei"),
            RawQuestion("Tomorrow", "Mingtian means tomorrow.", "__ wo qu xuexiao.", "Mingtian", "Jintian", "Zuotian", "Nali"),
            RawQuestion("Yesterday", "Zuotian means yesterday.", "__ wo hen mang.", "Zuotian", "Mingtian", "Jintian", "Shei"),
            RawQuestion("Like", "Xihuan means like.", "Wo __ mao.", "xihuan", "zai", "you", "shi"),
            RawQuestion("Want", "Yao means want.", "Wo __ he shui.", "yao", "shi", "zai", "de"),
            RawQuestion("Can", "Hui can mean know how to.", "Wo __ shuo yingwen.", "hui", "shi", "you", "de"),
            RawQuestion("Very", "Hen often links adjectives.", "Ta __ gao.", "hen", "ma", "le", "ge"),
            RawQuestion("Also", "Ye means also.", "Wo __ xihuan.", "ye", "ma", "de", "le"),
            RawQuestion("All", "Dou means all.", "Women __ shi xuesheng.", "dou", "ye", "ma", "le"),
            RawQuestion("And", "He connects nouns.", "Wo __ ni shi pengyou.", "he", "ma", "de", "le"),
            RawQuestion("Or", "Haishi is used in questions for or.", "Ni yao cha __ shui?", "haishi", "he", "dou", "ye"),
            RawQuestion("Not Have", "Mei you means do not have.", "Wo __ qian.", "mei you", "bu shi", "hen", "zai"),
            RawQuestion("Age", "Sui marks age.", "Wo ba __.", "sui", "ge", "ben", "ma")
        ))

        addSet(data, LANGUAGE_MANDARIN, LEVEL_INTERMEDIATE, "Choose the pinyin form that completes the more complex Mandarin sentence.", listOf(
            RawQuestion("Completed Action", "Le often marks a completed action.", "Wo chi fan __.", "le", "ma", "de", "ge"),
            RawQuestion("Experience Marker", "Guo marks past experience.", "Wo qu __ Beijing.", "guo", "le", "ma", "de"),
            RawQuestion("Comparison", "Bi is used in comparison sentences.", "Wo __ ni gao.", "bi", "ba", "bei", "de"),
            RawQuestion("Progressive", "Zhengzai shows an action happening now.", "Wo __ xuexi.", "zhengzai", "le", "guo", "ba"),
            RawQuestion("Result Complement", "Wan can mean finish doing.", "Wo kan __ le.", "wan", "guo", "bi", "de"),
            RawQuestion("Potential", "Neng means can or be able to.", "Wo __ qu.", "neng", "le", "ma", "de"),
            RawQuestion("Must", "Yinggai means should.", "Ni __ xuexi.", "yinggai", "guo", "le", "de"),
            RawQuestion("Because", "Yinwei means because.", "__ xia yu, wo bu qu.", "Yinwei", "Suoyi", "Danshi", "Ranhou"),
            RawQuestion("Therefore", "Suoyi means so or therefore.", "Wo hen mang, __ bu qu.", "suoyi", "yinwei", "danshi", "he"),
            RawQuestion("But", "Danshi means but.", "Wo xiang qu, __ mei you kong.", "danshi", "suoyi", "yinwei", "he"),
            RawQuestion("Before", "Yiqian means before.", "Shui jiao __, wo kan shu.", "yiqian", "yihou", "suoyi", "danshi"),
            RawQuestion("After", "Yihou means after.", "Fangxue __, wo hui jia.", "yihou", "yiqian", "yinwei", "danshi"),
            RawQuestion("If", "Ruguo means if.", "__ you kong, wo qu.", "Ruguo", "Suoyi", "Danshi", "He"),
            RawQuestion("Then", "Jiu can mean then in if sentences.", "Ruguo xia yu, wo __ bu qu.", "jiu", "le", "guo", "de"),
            RawQuestion("Passive", "Bei can mark passive voice.", "Shu __ ta kan le.", "bei", "ba", "bi", "de"),
            RawQuestion("Disposal", "Ba can mark disposal structure.", "Wo __ shu fang zai zhuozi shang.", "ba", "bei", "bi", "le"),
            RawQuestion("Degree", "De after adjectives can introduce degree.", "Ta pao __ hen kuai.", "de", "le", "guo", "ma"),
            RawQuestion("Directional", "Lai means come toward the speaker.", "Qing guo __.", "lai", "qu", "le", "guo"),
            RawQuestion("Directional", "Qu means go away from the speaker.", "Ta zou __ le.", "qu", "lai", "de", "ma"),
            RawQuestion("Already", "Yijing means already.", "Wo __ zuo wan le.", "yijing", "haishi", "he", "ma")
        ))
    }

    private data class RawQuestion(
        val category: String,
        val instruction: String,
        val prompt: String,
        val answer: String,
        val wrong1: String,
        val wrong2: String,
        val wrong3: String
    )

    private data class QuestionSeed(
        val language: String,
        val proficiency: String,
        val category: String,
        val explanation: String,
        val instruction: String,
        val prompt: String,
        val answer: String,
        val options: List<String>
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
        private const val DB_VERSION = 4
    }
}
