package com.example.psnisignin

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.LocalDate

/**
 * Small SQLite data-access class. UI activities call these methods instead of containing SQL themselves.
 */
class DatabaseManager(context: Context, private val logger: AppLogger) {
    private val databaseFile: File = logger.resolveLocalPath(AppConfig.DATABASE_PATH)
    private val db: SQLiteDatabase

    init {
        databaseFile.parentFile?.mkdirs()
        db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            createTableIfNeeded()
        } catch (e: Exception) {
            db.close()
            throw e
        }
    }

    /**
     * Purpose: Create the sign_in_records table and indexes when the database is first opened.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: SQLite exceptions are logged and rethrown because the app cannot function without its database.
     */
    private fun createTableIfNeeded() {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sign_in_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    first_name TEXT NOT NULL,
                    last_name TEXT NOT NULL,
                    sign_in_datetime TEXT NOT NULL,
                    sign_out_datetime TEXT NULL,
                    reason TEXT NULL
                )
                """.trimIndent()
            )
            addColumnIfMissing("reason", "TEXT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_active_day ON sign_in_records(sign_in_datetime, sign_out_datetime)")
        } catch (e: Exception) {
            logger.error("DB_INIT_FAILED", "Could not initialize SQLite database at ${databaseFile.absolutePath}", e)
            throw e
        }
    }

    /** Add one nullable column when opening a database created by an earlier app version. */
    private fun addColumnIfMissing(columnName: String, definition: String) {
        val exists = db.rawQuery("PRAGMA table_info(sign_in_records)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) {
                    found = true
                    break
                }
            }
            found
        }
        if (!exists) db.execSQL("ALTER TABLE sign_in_records ADD COLUMN $columnName $definition")
    }

    /**
     * Purpose: Check whether the same person already has an unsigned-out record on a given date.
     * Inputs: firstName, lastName, date.
     * Optional inputs: None.
     * Returns: true when a case-insensitive name match exists for that date with no sign-out timestamp.
     * Error handling: SQLite errors are logged and rethrown for the activity to display a safe message.
     */
    @Synchronized
    fun isAlreadySignedIn(firstName: String, lastName: String, date: LocalDate): Boolean {
        val start = date.atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern(AppConfig.TIMESTAMP_PATTERN))
        val end = TimeUtils.nextDayStart(date)
        return try {
            db.rawQuery(
                """SELECT id FROM sign_in_records
                   WHERE first_name = ? COLLATE NOCASE
                     AND last_name = ? COLLATE NOCASE
                     AND sign_in_datetime >= ? AND sign_in_datetime < ?
                     AND (sign_out_datetime IS NULL OR sign_out_datetime = '')
                   LIMIT 1""".trimIndent(),
                arrayOf(firstName, lastName, start, end)
            ).use { it.moveToFirst() }
        } catch (e: Exception) {
            logger.error("DB_DUPLICATE_CHECK_FAILED", "Could not check existing sign-in for $firstName $lastName", e)
            throw e
        }
    }

    /**
     * Purpose: Insert a new local sign-in row inside a SQLite transaction.
     * Inputs: firstName, lastName, signInDateTime, and reason.
     * Optional inputs: reason may be null.
     * Returns: Newly generated row id.
     * Error handling: Transaction rolls back automatically unless marked successful; errors are logged and rethrown.
     */
    @Synchronized
    fun insertSignIn(firstName: String, lastName: String, signInDateTime: String, reason: String?): Long {
        db.beginTransaction()
        return try {
            val values = ContentValues().apply {
                put("first_name", firstName)
                put("last_name", lastName)
                put("sign_in_datetime", signInDateTime)
                putNull("sign_out_datetime")
                if (reason.isNullOrBlank()) putNull("reason") else put("reason", reason)
            }
            val id = db.insertOrThrow("sign_in_records", null, values)
            db.setTransactionSuccessful()
            logger.audit("sign-in", "id=$id; first_name=$firstName; last_name=$lastName; reason=${reason.orEmpty()}; sign_in=$signInDateTime")
            id
        } catch (e: Exception) {
            logger.error("DB_SIGN_IN_FAILED", "Could not insert sign-in for $firstName $lastName", e)
            throw e
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Purpose: Get everyone signed in since 00:00:01 today who has no sign-out timestamp.
     * Inputs: date - date to query.
     * Optional inputs: None.
     * Returns: List of active SignInRecord objects ordered by sign-in time.
     * Error handling: SQLite failures are logged and rethrown.
     */
    @Synchronized
    fun getActiveForDay(date: LocalDate): List<SignInRecord> = try {
        queryRecords(
            """SELECT id, first_name, last_name, sign_in_datetime, sign_out_datetime, reason
               FROM sign_in_records
               WHERE sign_in_datetime >= ? AND sign_in_datetime < ?
                 AND (sign_out_datetime IS NULL OR sign_out_datetime = '')
               ORDER BY sign_in_datetime ASC""".trimIndent(),
            arrayOf(TimeUtils.dayStartAtOneSecond(date), TimeUtils.nextDayStart(date))
        )
    } catch (e: Exception) {
        logger.error("DB_ACTIVE_LIST_FAILED", "Could not load today's active sign-ins", e)
        throw e
    }

    /**
     * Purpose: Set the sign-out timestamp for one record inside a SQLite transaction.
     * Inputs: id - record key; signOutDateTime - timestamp to store.
     * Optional inputs: auditType defaults to "sign-out" and may be overridden for a future transaction category.
     * Returns: true when exactly one or more rows were updated.
     * Error handling: Failed transactions roll back; errors are logged and rethrown.
     */
    @Synchronized
    fun signOut(id: Long, signOutDateTime: String, auditType: String = "sign-out"): Boolean {
        db.beginTransaction()
        return try {
            val record = getRecord(id)
            val values = ContentValues().apply { put("sign_out_datetime", signOutDateTime) }
            val changed = db.update("sign_in_records", values, "id = ?", arrayOf(id.toString()))
            if (changed > 0) {
                db.setTransactionSuccessful()
                logger.audit(auditType, "id=$id; first_name=${record?.firstName.orEmpty()}; last_name=${record?.lastName.orEmpty()}; reason=${record?.reason.orEmpty()}; sign_out=$signOutDateTime")
                true
            } else {
                logger.error("DB_SIGN_OUT_NOT_FOUND", "No record found for id=$id")
                false
            }
        } catch (e: Exception) {
            logger.error("DB_SIGN_OUT_FAILED", "Could not sign out id=$id", e)
            throw e
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Purpose: Load records within an inclusive UI date window.
     * Inputs: startDate and endDate.
     * Optional inputs: None.
     * Returns: Records whose sign-in timestamp falls from startDate midnight through the end of endDate.
     * Error handling: SQLite failures are logged and rethrown.
     */
    @Synchronized
    fun getRecordsBetween(startDate: LocalDate, endDate: LocalDate): List<SignInRecord> = try {
        val start = startDate.atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern(AppConfig.TIMESTAMP_PATTERN))
        queryRecords(
            """SELECT id, first_name, last_name, sign_in_datetime, sign_out_datetime, reason
               FROM sign_in_records
               WHERE sign_in_datetime >= ? AND sign_in_datetime < ?
               ORDER BY sign_in_datetime DESC""".trimIndent(),
            arrayOf(start, TimeUtils.nextDayStart(endDate))
        )
    } catch (e: Exception) {
        logger.error("DB_RANGE_QUERY_FAILED", "Could not load records from $startDate through $endDate", e)
        throw e
    }

    /**
     * Purpose: Load one record by primary key for the admin edit screen.
     * Inputs: id - SQLite record id.
     * Optional inputs: None.
     * Returns: Matching SignInRecord, or null if the row no longer exists.
     * Error handling: SQLite failures are logged and rethrown.
     */
    @Synchronized
    fun getRecord(id: Long): SignInRecord? = try {
        queryRecords(
            "SELECT id, first_name, last_name, sign_in_datetime, sign_out_datetime, reason FROM sign_in_records WHERE id = ? LIMIT 1",
            arrayOf(id.toString())
        ).firstOrNull()
    } catch (e: Exception) {
        logger.error("DB_GET_RECORD_FAILED", "Could not load record id=$id", e)
        throw e
    }

    /**
     * Purpose: Update admin-editable sign-in and sign-out timestamps inside a SQLite transaction.
     * Inputs: id, signInDateTime, signOutDateTime, and reason.
     * Optional inputs: signOutDateTime and reason may be null.
     * Returns: true if an existing row was changed.
     * Error handling: Transaction rolls back on failure; errors are logged and rethrown.
     */
    @Synchronized
    fun adminEdit(id: Long, signInDateTime: String, signOutDateTime: String?, reason: String?): Boolean {
        db.beginTransaction()
        return try {
            val record = getRecord(id)
            val values = ContentValues().apply {
                put("sign_in_datetime", signInDateTime)
                if (signOutDateTime.isNullOrBlank()) putNull("sign_out_datetime") else put("sign_out_datetime", signOutDateTime)
                if (reason.isNullOrBlank()) putNull("reason") else put("reason", reason)
            }
            val changed = db.update("sign_in_records", values, "id = ?", arrayOf(id.toString()))
            if (changed > 0) {
                db.setTransactionSuccessful()
                logger.audit("admin-edit", "id=$id; first_name=${record?.firstName.orEmpty()}; last_name=${record?.lastName.orEmpty()}; reason=${reason.orEmpty()}; sign_in=$signInDateTime; sign_out=${signOutDateTime ?: ""}")
                true
            } else {
                logger.error("DB_ADMIN_EDIT_NOT_FOUND", "No record found for admin edit id=$id")
                false
            }
        } catch (e: Exception) {
            logger.error("DB_ADMIN_EDIT_FAILED", "Could not edit record id=$id", e)
            throw e
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Purpose: Load every row whose sign-in occurred in a given year for rebuilding the yearly CSV snapshot.
     * Inputs: year - four-digit calendar year.
     * Optional inputs: None.
     * Returns: All matching records ordered chronologically.
     * Error handling: SQLite failures are logged and rethrown to the exporter, which logs and abandons only that export attempt.
     */
    @Synchronized
    fun getRecordsForYear(year: Int): List<SignInRecord> {
        val start = "%04d-01-01 00:00:00".format(year)
        val end = "%04d-01-01 00:00:00".format(year + 1)
        return queryRecords(
            """SELECT id, first_name, last_name, sign_in_datetime, sign_out_datetime, reason
               FROM sign_in_records
               WHERE sign_in_datetime >= ? AND sign_in_datetime < ?
               ORDER BY sign_in_datetime ASC""".trimIndent(),
            arrayOf(start, end)
        )
    }

    /**
     * Purpose: Execute a SELECT that returns the standard record columns and convert its cursor to objects.
     * Inputs: sql - SELECT statement; args - positional arguments for ? placeholders.
     * Optional inputs: None.
     * Returns: Mutable list of SignInRecord data.
     * Error handling: Cursor is always closed by use(); SQL errors propagate to the calling public method for logging.
     */
    private fun queryRecords(sql: String, args: Array<String>): List<SignInRecord> {
        val records = mutableListOf<SignInRecord>()
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                records += SignInRecord(
                    id = cursor.getLong(0),
                    firstName = cursor.getString(1),
                    lastName = cursor.getString(2),
                    signInDateTime = cursor.getString(3),
                    signOutDateTime = if (cursor.isNull(4)) null else cursor.getString(4),
                    reason = if (cursor.isNull(5)) null else cursor.getString(5)
                )
            }
        }
        return records
    }
}
