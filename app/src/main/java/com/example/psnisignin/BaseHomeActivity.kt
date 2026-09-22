package com.example.psnisignin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton

/** Base class for screens that always expose the requested upper-left Home button. */
abstract class BaseHomeActivity : Activity() {
    protected val app: SignInApplication
        get() = application as SignInApplication

    /**
     * Purpose: Attach the common Home button behavior after a child activity sets its layout.
     * Inputs: savedInstanceState - Android lifecycle state, unused by this base class.
     * Optional inputs: savedInstanceState may be null on first creation.
     * Returns: Unit.
     * Error handling: Child activities must call bindHomeButton() after setContentView; no lookup is attempted here.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    /**
     * Purpose: Wire the layout's homeButton to return to MainActivity and clear intermediate screens.
     * Inputs: None; requires a view with id homeButton in the current layout.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Missing view id throws during development, making an invalid layout obvious.
     */
    protected fun bindHomeButton() {
        findViewById<ImageButton>(R.id.homeButton).setOnClickListener { goHome() }
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }
    }

    /**
     * Purpose: Navigate to the main page from any secondary screen.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Standard Android activity launch errors are not expected for this internal activity.
     */
    protected fun goHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    /** Also protect database screens restored by Android after a process restart. */
    protected fun requireDatabase(): Boolean {
        if (app.databaseAvailable) return true
        goHome()
        return false
    }
}
