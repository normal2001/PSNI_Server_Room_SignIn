package com.example.psnisignin

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Lists today's active people and provides a Sign Out button for each person. */
class SignOutActivity : BaseHomeActivity() {
    private lateinit var peopleContainer: LinearLayout
    private lateinit var messageText: TextView

    /**
     * Purpose: Initialize the sign-out screen and load today's active sign-ins.
     * Inputs: savedInstanceState - Android lifecycle state.
     * Optional inputs: savedInstanceState may be null.
     * Returns: Unit.
     * Error handling: Loading failures are logged and shown on screen.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireDatabase()) return
        setContentView(R.layout.activity_sign_out)
        bindHomeButton()
        peopleContainer = findViewById(R.id.peopleContainer)
        messageText = findViewById(R.id.messageText)
        loadPeople()
    }

    /**
     * Purpose: Query and display people signed in since 00:00:01 today without a sign-out timestamp.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Database errors are logged and replaced with a readable red message.
     */
    private fun loadPeople() {
        peopleContainer.removeAllViews()
        try {
            val records = app.database.getActiveForDay(TimeUtils.today())
            if (records.isEmpty()) {
                val empty = TextView(this).apply {
                    text = "No one is currently signed in today."
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setPadding(12, 24, 12, 24)
                }
                peopleContainer.addView(empty)
                return
            }
            records.forEach { addPersonRow(it) }
        } catch (e: Exception) {
            app.logger.error("SIGN_OUT_LIST_UI_FAILED", "Could not display active sign-ins", e)
            showMessage("Could not load the sign-out list.")
        }
    }

    /**
     * Purpose: Add one person's abbreviated name and Sign Out button to the dynamic list.
     * Inputs: record - active database record to represent.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Empty last names are not expected because sign-in validation requires content; a fallback '?' initial is still used defensively.
     */
    private fun addPersonRow(record: SignInRecord) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 8)
        }
        val name = TextView(this).apply {
            val initial = record.lastName.firstOrNull()?.uppercaseChar() ?: '?'
            text = "${record.firstName} $initial."
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(Color.parseColor("#464855"))
        }
        val button = Button(this).apply {
            text = "Sign Out"
            textSize =  18f
            setPadding(4,0,4,0)
            setOnClickListener { performSignOut(record.id, this) }
        }
        row.addView(name)
        row.addView(button)
        peopleContainer.addView(row)
    }

    /**
     * Purpose: Update one selected record with the current sign-out time.
     * Inputs: recordId - database primary key; button - pressed button so duplicate taps can be disabled.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Failed updates are logged and shown; successful updates trigger SMB export and four-second return home.
     */
    private fun performSignOut(recordId: Long, button: Button) {
        button.isEnabled = false
        try {
            val changed = app.database.signOut(recordId, TimeUtils.nowTimestamp())
            if (!changed) {
                showMessage("This record could not be found.")
                button.isEnabled = true
                return
            }
            app.csvExporter.exportCurrentYearAsync()
            showMessage("Successfully signed out.")
            returnHomeAfterDelay()
        } catch (e: Exception) {
            app.logger.error("SIGN_OUT_UI_FAILED", "Could not complete sign-out for id=$recordId", e)
            showMessage("Sign out failed. Please try again.")
            button.isEnabled = true
        }
    }

    /**
     * Purpose: Display a red message on the sign-out screen.
     * Inputs: message - text to show.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: No expected errors.
     */
    private fun showMessage(message: String) {
        messageText.text = message
        messageText.visibility = View.VISIBLE
    }

    /**
     * Purpose: Navigate home four seconds after a successful sign-out.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Avoids navigation if Android has already destroyed/finished the Activity.
     */
    private fun returnHomeAfterDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) goHome()
        }, 4000L)
    }
}
