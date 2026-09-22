package com.example.psnisignin

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView

/** Admin form for correcting a record's sign-in and optional sign-out timestamps. */
class AdminEditActivity : BaseHomeActivity() {
    companion object {
        /** Intent key containing the record id to edit. */
        const val EXTRA_RECORD_ID = "record_id"
    }

    private var recordId: Long = -1L
    private lateinit var signInInput: EditText
    private lateinit var signOutInput: EditText
    private lateinit var reasonSpinner: Spinner
    private lateinit var personText: TextView
    private lateinit var messageText: TextView
    private lateinit var submitButton: Button

    /**
     * Purpose: Initialize the admin timestamp editor and pre-populate it from SQLite.
     * Inputs: savedInstanceState - Android lifecycle state.
     * Optional inputs: savedInstanceState may be null.
     * Returns: Unit.
     * Error handling: Missing record id or database lookup failure is logged/shown and disables submission.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireDatabase()) return
        setContentView(R.layout.activity_admin_edit)
        bindHomeButton()

        recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        signInInput = findViewById(R.id.signInInput)
        signOutInput = findViewById(R.id.signOutInput)
        reasonSpinner = findViewById(R.id.reasonSpinner)
        reasonSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("") + AppConfig.REASON_OPTIONS).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        personText = findViewById(R.id.personText)
        messageText = findViewById(R.id.messageText)
        submitButton = findViewById(R.id.submitButton)
        submitButton.setOnClickListener { submitEdit() }
        loadRecord()
    }

    /**
     * Purpose: Load the selected record and fill the editor fields with existing timestamps.
     * Inputs: None; uses recordId from the Intent.
     * Optional inputs: Existing sign-out may be null/blank and is shown as an empty field.
     * Returns: Unit.
     * Error handling: Invalid/missing records or SQLite failures are logged/shown and submission is disabled.
     */
    private fun loadRecord() {
        if (recordId < 0L) {
            showMessage("Invalid record.")
            submitButton.isEnabled = false
            app.logger.error("ADMIN_EDIT_INVALID_ID", "Admin edit opened without a valid record id")
            return
        }

        try {
            val record = app.database.getRecord(recordId)
            if (record == null) {
                showMessage("Record not found.")
                submitButton.isEnabled = false
                return
            }
            personText.text = "${record.firstName} ${record.lastName}"
            signInInput.setText(record.signInDateTime)
            signOutInput.setText(record.signOutDateTime ?: "")
            reasonSpinner.setSelection((listOf("") + AppConfig.REASON_OPTIONS).indexOf(record.reason).coerceAtLeast(0))
        } catch (e: Exception) {
            app.logger.error("ADMIN_EDIT_LOAD_UI_FAILED", "Could not load admin edit record id=$recordId", e)
            showMessage("Could not load this record.")
            submitButton.isEnabled = false
        }
    }

    /**
     * Purpose: Strictly validate timestamp text, update SQLite, audit admin-edit, and queue the yearly SMB CSV export.
     * Inputs: None; reads signInInput and signOutInput.
     * Optional inputs: Sign-out timestamp may be blank; sign-in timestamp is required.
     * Returns: Unit.
     * Error handling: Invalid formats show an on-screen error; database failures are logged/shown without leaving the page.
     */
    private fun submitEdit() {
        val signIn = signInInput.text.toString().trim()
        val signOut = signOutInput.text.toString().trim()
        val reason = reasonSpinner.selectedItem.toString()

        if (!TimeUtils.isValidTimestamp(signIn)) {
            showMessage("Invalid sign-in format. Use ${AppConfig.TIMESTAMP_PATTERN}.")
            return
        }
        if (!TimeUtils.isValidTimestamp(signOut, allowBlank = true)) {
            showMessage("Invalid sign-out format. Use ${AppConfig.TIMESTAMP_PATTERN} or leave it blank.")
            return
        }

        try {
            val changed = app.database.adminEdit(recordId, signIn, signOut.ifBlank { null }, reason.ifBlank { null })
            if (!changed) {
                showMessage("Record not found.")
                return
            }
            app.csvExporter.exportCurrentYearAsync()
            submitButton.isEnabled = false
            showMessage("Successfully updated.")
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) finish()
            }, 4000L)
        } catch (e: Exception) {
            app.logger.error("ADMIN_EDIT_SUBMIT_UI_FAILED", "Could not save admin edit id=$recordId", e)
            showMessage("Update failed. Please try again.")
        }
    }

    /**
     * Purpose: Show a red status/error message on the admin edit form.
     * Inputs: message - text to display.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: No expected errors.
     */
    private fun showMessage(message: String) {
        messageText.text = message
        messageText.visibility = View.VISIBLE
    }
}
