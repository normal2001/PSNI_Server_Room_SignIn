package com.example.psnisignin

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.Button
import android.widget.DatePicker
import android.widget.TextView
import java.time.LocalDate

/** Lets an administrator choose the inclusive end date for a one-month record window. */
class AdminDateActivity : BaseHomeActivity() {
    private lateinit var datePicker: DatePicker

    /**
     * Purpose: Initialize the admin date selector and Submit button.
     * Inputs: savedInstanceState - Android lifecycle state.
     * Optional inputs: savedInstanceState may be null.
     * Returns: Unit.
     * Error handling: DatePicker supplies validated calendar components, so no parsing error is expected here.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_date)
        bindHomeButton()
        val dateInstructions = findViewById<TextView>(R.id.dateInstructions)
        val styledInstructions = SpannableString(dateInstructions.text)
        val endDateStart = dateInstructions.text.indexOf("end date")
        styledInstructions.setSpan(StyleSpan(Typeface.BOLD), endDateStart, endDateStart + "end date".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        dateInstructions.text = styledInstructions
        datePicker = findViewById(R.id.datePicker)
        findViewById<Button>(R.id.submitButton).setOnClickListener { submitDate() }
    }

    /**
     * Purpose: Send the selected date to AdminListActivity as the end of the requested one-month window.
     * Inputs: None; reads year/month/day from DatePicker.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: LocalDate.of errors are not expected from DatePicker-generated values.
     */
    private fun submitDate() {
        val selected = LocalDate.of(datePicker.year, datePicker.month + 1, datePicker.dayOfMonth)
        val intent = Intent(this, AdminListActivity::class.java).apply {
            putExtra(AdminListActivity.EXTRA_END_DATE, selected.toString())
        }
        startActivity(intent)
    }
}
