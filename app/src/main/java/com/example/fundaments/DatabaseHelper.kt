package com.example.fundaments

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "Fundaments.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Grammar Rules Table
        db.execSQL("CREATE TABLE GrammarRules (rule_id INTEGER PRIMARY KEY, category TEXT, explanation TEXT)")

        // 2. Practice Questions Table
        db.execSQL("CREATE TABLE PracticeQuestions (question_id INTEGER PRIMARY KEY, rule_id INTEGER, prompt TEXT, answer TEXT, FOREIGN KEY(rule_id) REFERENCES GrammarRules(rule_id))")

        // 3. Diagnostic Logs Table
        db.execSQL("CREATE TABLE DiagnosticLogs (log_id INTEGER PRIMARY KEY AUTOINCREMENT, question_id INTEGER, is_correct INTEGER, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(question_id) REFERENCES PracticeQuestions(question_id))")

        // Pre-seed test data to ensure the app has something to work with
        db.execSQL("INSERT INTO GrammarRules VALUES (1, 'Particles', 'Usage of wa vs ga')")
        db.execSQL("INSERT INTO PracticeQuestions VALUES (1, 1, 'Watashi __ Gakusei desu.', 'wa')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS DiagnosticLogs")
        db.execSQL("DROP TABLE IF EXISTS PracticeQuestions")
        db.execSQL("DROP TABLE IF EXISTS GrammarRules")
        onCreate(db)
    }

    // Method to log answers (Used in Practice screen)
    fun logAnswer(questionId: Int, isCorrect: Boolean) {
        val db = this.writableDatabase

        // Kotlin's .apply makes this very clean
        val values = ContentValues().apply {
            put("question_id", questionId)
            put("is_correct", if (isCorrect) 1 else 0) // Convert boolean to SQLite integer
        }
        db.insert("DiagnosticLogs", null, values)
    }

    // Method to generate analytics using INNER JOIN (Used in Reports screen)
    fun getErrorAnalytics(): Cursor {
        val db = this.readableDatabase
        // Kotlin Raw Strings (""") handle complex queries beautifully
        val query = """
            SELECT G.category, COUNT(L.log_id) as error_count 
            FROM DiagnosticLogs L 
            INNER JOIN PracticeQuestions Q ON L.question_id = Q.question_id 
            INNER JOIN GrammarRules G ON Q.rule_id = G.rule_id 
            WHERE L.is_correct = 0 
            GROUP BY G.category 
            ORDER BY error_count DESC
        """.trimIndent()

        return db.rawQuery(query, null)
    }
}