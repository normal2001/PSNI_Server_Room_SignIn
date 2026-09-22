package com.example.psnisignin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView

/** Main menu containing the centered Sign In / Sign Out buttons and admin cog. */
class MainActivity : Activity() {

    /**
     * Purpose: Display the main page and connect its three navigation buttons.
     * Inputs: savedInstanceState - Android lifecycle state.
     * Optional inputs: savedInstanceState may be null.
     * Returns: Unit.
     * Error handling: Navigation uses internal activities declared in the manifest; failures are not expected.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<ImageButton>(R.id.homeButton).setOnClickListener { }
        findViewById<Button>(R.id.signInButton).setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
        }
        findViewById<Button>(R.id.signOutButton).setOnClickListener {
            startActivity(Intent(this, SignOutActivity::class.java))
        }
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val available = (application as SignInApplication).databaseAvailable
        findViewById<TextView>(R.id.databaseWarning).visibility = if (available) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.signInButton).isEnabled = available
        findViewById<Button>(R.id.signOutButton).isEnabled = available
    }
}
