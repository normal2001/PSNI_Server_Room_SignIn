package com.example.psnisignin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** Passcode gate for the admin record screens. */
class AdminLoginActivity : BaseHomeActivity() {
    private lateinit var passcodeInput: EditText
    private lateinit var messageText: TextView

    /**
     * Purpose: Initialize the admin passcode screen and login action.
     * Inputs: savedInstanceState - Android lifecycle state.
     * Optional inputs: savedInstanceState may be null.
     * Returns: Unit.
     * Error handling: Incorrect passcodes are handled locally as validation messages.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)
        bindHomeButton()
        passcodeInput = findViewById(R.id.passcodeInput)
        messageText = findViewById(R.id.messageText)
        findViewById<Button>(R.id.loginButton).setOnClickListener { attemptLogin() }
    }

    /**
     * Purpose: Compare the entered four-digit value with the configured admin passcode.
     * Inputs: None; reads the passcode field.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Invalid values show a red error; correct values navigate to the date selector.
     */
    private fun attemptLogin() {
        val entered = passcodeInput.text.toString()
        if (entered == AppConfig.ADMIN_PASSCODE) {
            app.logger.audit("passcode-attempt", "result=success")
            val destination = if (app.databaseAvailable) AdminDateActivity::class.java else AdminListActivity::class.java
            startActivity(Intent(this, destination))
            finish()
        } else {
            app.logger.audit("passcode-attempt", "result=failed")
            messageText.text = "Incorrect passcode."
            messageText.visibility = View.VISIBLE
            passcodeInput.text.clear()
        }
    }
}
