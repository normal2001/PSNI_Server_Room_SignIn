package com.example.psnisignin

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Displays the selected one-month admin record table and record actions. */
class AdminListActivity : BaseHomeActivity() {
    companion object {
        /** Intent key carrying the selected inclusive end date in ISO yyyy-MM-dd form. */
        const val EXTRA_END_DATE = "end_date"
    }

    private lateinit var recordsTable: TableLayout
    private lateinit var rangeText: TextView
    private lateinit var messageText: TextView
    private lateinit var endDate: LocalDate

    /**
     * Purpose: Initialize the admin table and recover the selected ending date from the Intent.
     * Inputs: savedInstanceState - Android lifecycle state.
     * Optional inputs: savedInstanceState may be null; missing/invalid end-date intent falls back to today.
     * Returns: Unit.
     * Error handling: Invalid date extras are logged and replaced with today's date so the screen remains usable.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_list)
        bindHomeButton()

        recordsTable = findViewById(R.id.recordsTable)
        rangeText = findViewById(R.id.rangeText)
        messageText = findViewById(R.id.messageText)
        endDate = readEndDate()
        bindTabs()
        populateSettings()
        bindSettingsActions()
        showSection(if (app.databaseAvailable) R.id.visitorsSection else R.id.applicationSection)
        if (!app.databaseAvailable) {
            showSettingsMessage(R.id.applicationMessageText, getString(R.string.database_unavailable), isError = true)
        }
    }

    /**
     * Purpose: Refresh the table whenever this screen becomes visible, including after an admin edit returns.
     * Inputs: None; Android invokes this lifecycle callback.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Table-loading errors are handled inside loadTable().
     */
    override fun onResume() {
        super.onResume()
        loadTable()
    }

    /**
     * Purpose: Parse the selected end date from the Intent.
     * Inputs: None; reads EXTRA_END_DATE.
     * Optional inputs: Missing extra is allowed and falls back to today.
     * Returns: Parsed LocalDate or today's date.
     * Error handling: Parsing exceptions are logged as a warning and use today's date instead.
     */
    private fun readEndDate(): LocalDate {
        val raw = intent.getStringExtra(EXTRA_END_DATE) ?: return TimeUtils.today()
        return try {
            LocalDate.parse(raw)
        } catch (e: Exception) {
            app.logger.error("ADMIN_DATE_INVALID", "Invalid admin end date '$raw'; using today", e)
            TimeUtils.today()
        }
    }

    /** Configure the four admin tabs in their requested left-to-right order. */
    private fun bindTabs() {
        findViewById<Button>(R.id.visitorsTab).setOnClickListener { showSection(R.id.visitorsSection) }
        findViewById<Button>(R.id.fileshareTab).setOnClickListener { showSection(R.id.fileshareSection) }
        findViewById<Button>(R.id.applicationTab).setOnClickListener { showSection(R.id.applicationSection) }
        findViewById<Button>(R.id.adminTab).setOnClickListener { showSection(R.id.adminSection) }
    }

    /** Display one tab body and visually identify its selected tab. */
    private fun showSection(sectionId: Int) {
        val sections = listOf(R.id.visitorsSection, R.id.fileshareSection, R.id.applicationSection, R.id.adminSection)
        val tabs = listOf(R.id.visitorsTab, R.id.fileshareTab, R.id.applicationTab, R.id.adminTab)
        sections.forEach { findViewById<View>(it).visibility = if (it == sectionId) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { index, tabId ->
            findViewById<Button>(tabId).apply {
                alpha = if (sections[index] == sectionId) 1f else 0.65f
                setTypeface(typeface, if (sections[index] == sectionId) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    /** Fill every setting control with its persisted value or AppConfig default. */
    private fun populateSettings() {
        findViewById<CheckBox>(R.id.smbExportEnabledInput).isChecked = AppConfig.SMB_EXPORT_ENABLED
        setInput(R.id.smbHostInput, AppConfig.SMB_HOST)
        setInput(R.id.smbDomainInput, AppConfig.SMB_DOMAIN)
        setInput(R.id.smbUsernameInput, AppConfig.SMB_USERNAME)
        setInput(R.id.smbPasswordInput, AppConfig.SMB_PASSWORD)
        setInput(R.id.smbShareNameInput, AppConfig.SMB_SHARE_NAME)
        setInput(R.id.databasePathInput, AppConfig.DATABASE_PATH)
        setInput(R.id.auditLogPathInput, AppConfig.AUDIT_LOG_PATH)
        setInput(R.id.errorLogPathInput, AppConfig.ERROR_LOG_PATH)
        setInput(R.id.timestampPatternInput, AppConfig.TIMESTAMP_PATTERN)
        setInput(R.id.adminPasscodeInput, AppConfig.ADMIN_PASSCODE)
        updateDefaultPasscodeWarning()
        findViewById<CheckBox>(R.id.slackNotificationsEnabledInput).isChecked = AppConfig.SLACK_NOTIFICATIONS_ENABLED
        setInput(R.id.slackChannelInput, AppConfig.SLACK_CHANNEL)
        setInput(R.id.slackBotTokenInput, AppConfig.SLACK_BOT_TOKEN)
    }

    /** Attach save, passcode-change, and manual-export actions. */
    private fun bindSettingsActions() {
        findViewById<Button>(R.id.saveFileshareButton).setOnClickListener { saveFileshareSettings() }
        findViewById<Button>(R.id.exportCsvButton).setOnClickListener { buttonView ->
            if (!app.databaseAvailable) {
                showSettingsMessage(R.id.fileshareMessageText, "Restore database access in Application settings before exporting.", isError = true)
                return@setOnClickListener
            }
            if (saveFileshareSettings(showConfirmation = false)) {
                if (AppConfig.SMB_EXPORT_ENABLED) {
                    val button = buttonView as Button
                    button.isEnabled = false
                    showSettingsMessage(R.id.fileshareMessageText, "Exporting CSV...")
                    app.csvExporter.copyCurrentYearSnapshotAsync { success, message ->
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                button.isEnabled = true
                                showSettingsMessage(R.id.fileshareMessageText, message, isError = !success)
                            }
                        }
                    }
                } else {
                    showSettingsMessage(R.id.fileshareMessageText, "Enable SMB export before exporting.", isError = true)
                }
            }
        }
        findViewById<Button>(R.id.saveApplicationButton).setOnClickListener { saveApplicationSettings() }
        findViewById<Button>(R.id.viewAuditLogButton).setOnClickListener {
            viewLog("audit", R.id.auditLogPathInput, AppConfig.AUDIT_LOG_PATH)
        }
        findViewById<Button>(R.id.viewErrorLogButton).setOnClickListener {
            viewLog("error", R.id.errorLogPathInput, AppConfig.ERROR_LOG_PATH)
        }
        findViewById<Button>(R.id.changePasscodeButton).setOnClickListener { changePasscode() }
        findViewById<Button>(R.id.saveAdminButton).setOnClickListener { saveAdminSettings() }
    }

    /** Open a configured log with Android's default text viewer and temporary read access. */
    private fun viewLog(logName: String, inputId: Int, configuredPath: String) {
        if (inputText(inputId).trim() != configuredPath) {
            showSettingsMessage(R.id.applicationMessageText, "Save the log path before viewing it.", isError = true)
            return
        }
        try {
            val file = app.logger.resolveLocalPath(configuredPath)
            if (!file.isFile || !file.canRead()) {
                showSettingsMessage(R.id.applicationMessageText, "The $logName log does not exist yet or cannot be read.", isError = true)
                return
            }
            val uri = Uri.Builder().scheme("content").authority("$packageName.logs")
                .appendPath(logName).appendPath(file.canonicalPath).build()
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                clipData = ClipData.newRawUri("$logName log", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            findViewById<View>(R.id.applicationMessageText).visibility = View.GONE
        } catch (_: ActivityNotFoundException) {
            showSettingsMessage(R.id.applicationMessageText, "No application is installed to view text logs.", isError = true)
        } catch (e: Exception) {
            app.logger.error("ADMIN_LOG_VIEW_FAILED", "Could not open $logName log", e)
            showSettingsMessage(R.id.applicationMessageText, "Could not open the $logName log.", isError = true)
        }
    }

    /** Validate and persist the file-share controls. */
    private fun saveFileshareSettings(showConfirmation: Boolean = true): Boolean {
        val enabled = findViewById<CheckBox>(R.id.smbExportEnabledInput).isChecked
        val host = inputText(R.id.smbHostInput).trim()
        val domain = inputText(R.id.smbDomainInput).trim()
        val username = inputText(R.id.smbUsernameInput).trim()
        val password = inputText(R.id.smbPasswordInput)
        val shareName = inputText(R.id.smbShareNameInput).trim()
        if (enabled && (host.isBlank() || username.isBlank() || shareName.isBlank())) {
            showSettingsMessage(R.id.fileshareMessageText, "Host, username, and share name are required when SMB export is enabled.", isError = true)
            return false
        }
        AppConfig.saveFileshare(enabled, host, domain, username, password, shareName)
        if (showConfirmation) showSettingsMessage(R.id.fileshareMessageText, "Fileshare settings saved.")
        return true
    }

    /** Validate and persist application paths and the timestamp format. */
    private fun saveApplicationSettings() {
        val databasePath = inputText(R.id.databasePathInput).trim()
        val auditLogPath = inputText(R.id.auditLogPathInput).trim()
        val errorLogPath = inputText(R.id.errorLogPathInput).trim()
        val timestampPattern = inputText(R.id.timestampPatternInput).trim()
        if (databasePath.isBlank() || auditLogPath.isBlank() || errorLogPath.isBlank() || timestampPattern.isBlank()) {
            showSettingsMessage(R.id.applicationMessageText, "All application settings are required.", isError = true)
            return
        }
        try {
            val formatter = DateTimeFormatter.ofPattern(timestampPattern)
            LocalDateTime.parse(LocalDateTime.now().format(formatter), formatter)
        } catch (_: Exception) {
            showSettingsMessage(R.id.applicationMessageText, "Enter a valid pattern containing a complete date and time.", isError = true)
            return
        }
        AppConfig.saveApplication(databasePath, auditLogPath, errorLogPath, timestampPattern)
        if (!app.databaseAvailable) {
            val recovered = app.initializeDatabase()
            showSettingsMessage(
                R.id.applicationMessageText,
                if (recovered) "Settings saved. Database access restored. Return Home to check in or out."
                else "Settings saved, but the database could not be opened. Check that DATABASE_PATH points to a writable file location and try again.",
                isError = !recovered
            )
        } else {
            showSettingsMessage(R.id.applicationMessageText, "Application settings saved. Restart the app to use a new database path.")
        }
    }

    /** Show the warning until the saved passcode differs from the factory default. */
    private fun updateDefaultPasscodeWarning() {
        findViewById<View>(R.id.defaultPasscodeWarning).visibility =
            if (AppConfig.ADMIN_PASSCODE == "1234") View.VISIBLE else View.GONE
    }

    /** Enable passcode editing, then save four digits other than the factory default. */
    private fun changePasscode() {
        val input = findViewById<EditText>(R.id.adminPasscodeInput)
        val button = findViewById<Button>(R.id.changePasscodeButton)
        if (!input.isEnabled) {
            input.isEnabled = true
            input.requestFocus()
            input.selectAll()
            button.text = "Save"
            showSettingsMessage(R.id.adminMessageText, "Enter a new four-digit passcode. 1234 is not allowed.")
            return
        }
        val passcode = input.text.toString()
        if (!passcode.matches(Regex("\\d{4}"))) {
            showSettingsMessage(R.id.adminMessageText, "The admin passcode must contain exactly four digits.", isError = true)
            return
        }
        if (passcode == "1234") {
            showSettingsMessage(R.id.adminMessageText, getString(R.string.default_admin_passcode_not_allowed), isError = true)
            return
        }
        AppConfig.ADMIN_PASSCODE = passcode
        updateDefaultPasscodeWarning()
        input.isEnabled = false
        button.text = "Change"
        showSettingsMessage(R.id.adminMessageText, "Admin passcode changed.")
    }

    /** Persist Slack settings without changing the passcode field. */
    private fun saveAdminSettings() {
        val notificationsEnabled = findViewById<CheckBox>(R.id.slackNotificationsEnabledInput).isChecked
        val channel = inputText(R.id.slackChannelInput).trim().removePrefix("#")
        val botToken = inputText(R.id.slackBotTokenInput).trim()
        if (notificationsEnabled && botToken.isBlank()) {
            showSettingsMessage(R.id.adminMessageText, "Slack bot token is required when notifications are enabled.", isError = true)
            return
        }
        if (notificationsEnabled && channel.isBlank()) {
            showSettingsMessage(R.id.adminMessageText, "Slack channel is required for channel notifications.", isError = true)
            return
        }
        AppConfig.saveAdmin(
            slackNotificationsEnabled = notificationsEnabled,
            slackChannel = channel,
            slackBotToken = botToken
        )
        showSettingsMessage(R.id.adminMessageText, "Admin settings saved.")
    }

    private fun setInput(id: Int, value: String) {
        findViewById<EditText>(id).setText(value)
    }

    private fun inputText(id: Int): String = findViewById<EditText>(id).text.toString()

    /** Show success or validation feedback within the active settings tab. */
    private fun showSettingsMessage(id: Int, message: String, isError: Boolean = false) {
        findViewById<TextView>(id).apply {
            text = message
            setTextColor(getColor(if (isError) R.color.red_message else R.color.standard_text))
            visibility = View.VISIBLE
        }
    }

    /**
     * Purpose: Query the database and render the last-one-month table ending on the selected date.
     * Inputs: None; uses this Activity's endDate.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Database errors are logged and shown as a red message instead of crashing.
     */
    private fun loadTable() {
        recordsTable.removeAllViews()
        messageText.visibility = View.GONE
        if (!app.databaseAvailable) {
            rangeText.text = "Visitors unavailable"
            showMessage("Restore database access in Application settings to view visits.")
            return
        }
        val startDate = endDate.minusMonths(1)
        rangeText.text = "All visitors: $startDate through $endDate"
        addHeaderRow()

        try {
            val records = app.database.getRecordsBetween(startDate, endDate)
            if (records.isEmpty()) {
                val row = TableRow(this)
                row.addView(createCell("No visits found.", bold = false))
                recordsTable.addView(row)
                return
            }
            records.forEach { addRecordRow(it) }
        } catch (e: Exception) {
            app.logger.error("ADMIN_LIST_UI_FAILED", "Could not load admin visit table", e)
            showMessage("Could not load visits.")
        }
    }

    /**
     * Purpose: Add the column headings requested for the admin table.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: No expected recoverable errors.
     */
    private fun addHeaderRow() {
        val header = TableRow(this)
        header.addView(createCell("Sign-In Date/Time", bold = true))
        header.addView(createCell("Sign-Out Date/Time", bold = true))
        header.addView(createCell("First Name", bold = true))
        header.addView(createCell("Last Name", bold = true))
        header.addView(createCell("Edit", bold = true))
        header.addView(createCell("Sign Out", bold = true))
        recordsTable.addView(header)
    }

    /**
     * Purpose: Add one database record and its Edit/Sign Out actions to the table.
     * Inputs: record - database row to display.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Button actions delegate their own error handling to openEdit() and adminSignOut().
     */
    private fun addRecordRow(record: SignInRecord) {
        val row = TableRow(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(createCell(record.signInDateTime))
        row.addView(createCell(record.signOutDateTime ?: ""))
        row.addView(createCell(if (record.firstName.length > 10) "${record.firstName.take(10)}..." else record.firstName))
        row.addView(createCell(if (record.lastName.length > 10) "${record.lastName.take(10)}..." else record.lastName))
        val buttonHeight = (36 * resources.displayMetrics.density).toInt()

        val editButton = Button(this).apply {
            text = "Edit"
            layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, buttonHeight).apply {
                setMargins(1, 3, 3, 3)
            }
            setOnClickListener { openEdit(record.id) }
        }
        val signOutButton = Button(this).apply {
            text = "Sign Out"
            layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, buttonHeight).apply {
                setMargins(1, 3, 3, 3)
            }
            isEnabled = record.signOutDateTime.isNullOrBlank()
            setOnClickListener { adminSignOut(record.id, this) }
        }
        row.addView(editButton)
        row.addView(signOutButton)
        recordsTable.addView(row)
    }

    /**
     * Purpose: Build a consistently padded text cell for the dynamic TableLayout.
     * Inputs: text - cell contents.
     * Optional inputs: bold - true for header text, false by default.
     * Returns: Configured TextView ready to add to a TableRow.
     * Error handling: No expected errors.
     */
    private fun createCell(text: String, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = if (bold) 17f else 16f
        setPadding(16, 14, 16, 14)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    /**
     * Purpose: Open the timestamp editor for one record.
     * Inputs: recordId - SQLite primary key.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Internal Activity launch failures are not expected because the target is declared in the manifest.
     */
    private fun openEdit(recordId: Long) {
        startActivity(Intent(this, AdminEditActivity::class.java).apply {
            putExtra(AdminEditActivity.EXTRA_RECORD_ID, recordId)
        })
    }

    /**
     * Purpose: Perform the admin-list Sign Out action by overwriting the row's sign-out time with the current time.
     * Inputs: recordId - SQLite key; button - pressed button used to prevent double taps.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Database failures are logged/shown; successful updates queue SMB export and refresh after four seconds.
     */
    private fun adminSignOut(recordId: Long, button: Button) {
        button.isEnabled = false
        try {
            val changed = app.database.signOut(recordId, TimeUtils.nowTimestamp(), auditType = "sign-out")
            if (!changed) {
                showMessage("Record not found.")
                button.isEnabled = true
                return
            }
            app.csvExporter.exportCurrentYearAsync()
            showMessage("Successfully signed out.")
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) loadTable()
            }, 4000L)
        } catch (e: Exception) {
            app.logger.error("ADMIN_SIGN_OUT_UI_FAILED", "Admin sign-out failed for id=$recordId", e)
            showMessage("Sign out failed.")
            button.isEnabled = true
        }
    }

    /**
     * Purpose: Show a red status/error message above the admin table.
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
