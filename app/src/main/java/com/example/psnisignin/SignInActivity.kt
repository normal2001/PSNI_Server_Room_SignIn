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

/** Screen for entering first and last name and creating a sign-in transaction. */
class SignInActivity : BaseHomeActivity() {
    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var reasonSpinner: Spinner
    private lateinit var messageText: TextView
    private lateinit var submitButton: Button

    /**
     * Purpose: Initialize the sign-in form and its button handlers.
     * Inputs: savedInstanceState - Android lifecycle state.
     * Optional inputs: savedInstanceState may be null.
     * Returns: Unit.
     * Error handling: Database errors during submission are handled in submitSignIn().
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireDatabase()) return
        setContentView(R.layout.activity_sign_in)
        bindHomeButton()

        firstNameInput = findViewById(R.id.firstNameInput)
        lastNameInput = findViewById(R.id.lastNameInput)
        reasonSpinner = findViewById(R.id.reasonSpinner)
        reasonSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("[Select option from list]") + AppConfig.REASON_OPTIONS).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        messageText = findViewById(R.id.messageText)
        submitButton = findViewById(R.id.submitSignInButton)
        submitButton.setOnClickListener { submitSignIn() }
    }

    /**
     * Purpose: Validate names, prevent a duplicate active sign-in today, then insert a new record.
     * Inputs: None; reads the two text fields on screen.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Validation and duplicate conditions show red messages; database failures are logged and shown without closing the screen.
     */
    private fun submitSignIn() {
        val firstName = firstNameInput.text.toString().trim().replaceFirstChar { it.titlecase() }
        val lastName = lastNameInput.text.toString().trim().replaceFirstChar { it.titlecase() }
        val reason = reasonSpinner.selectedItem.toString()

        if (firstName.isEmpty() || lastName.isEmpty()) {
            showMessage("First Name and Last Name are required.")
            return
        }
        if (reasonSpinner.selectedItemPosition == 0) {
            showMessage("Reason for visit is required.")
            return
        }

        try {
            if (app.database.isAlreadySignedIn(firstName, lastName, TimeUtils.today())) {
                showMessage("Already signed in today")
                returnHomeAfterDelay()
                return
            }

            val signInDateTime = TimeUtils.nowTimestamp()
            val visitReason = reason.ifBlank { null }
            app.database.insertSignIn(firstName, lastName, signInDateTime, visitReason)
            app.csvExporter.exportCurrentYearAsync()
            SlackIntegration.notifySuccessfulSignInAsync(
                firstName,
                lastName,
                signInDateTime,
                visitReason,
                app.logger
            )
            submitButton.isEnabled = false
            showMessage("Successfully signed in.")
            returnHomeAfterDelay()
        } catch (e: Exception) {
            app.logger.error("SIGN_IN_UI_FAILED", "Sign-in could not be completed", e)
            showMessage("Sign in failed. Please try again or contact an administrator.")
        }
    }

    /**
     * Purpose: Show a red status/error message on the form.
     * Inputs: message - text to display.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: No expected errors.
     */
    private fun showMessage(message: String) {
        messageText.text = message
        messageText.visibility = View.VISIBLE
    }

    /**
     * Purpose: Return to the main page four seconds after a terminal sign-in result.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Callback checks that the Activity is not already finishing/destroyed before navigating.
     */
    private fun returnHomeAfterDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) goHome()
        }, 4000L)
    }
}
