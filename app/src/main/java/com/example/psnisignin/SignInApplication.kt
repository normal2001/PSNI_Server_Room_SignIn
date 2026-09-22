package com.example.psnisignin

import android.app.Application

/** Application-level owner of the database, logs, and serialized SMB exporter. */
class SignInApplication : Application() {
    lateinit var logger: AppLogger
        private set
    lateinit var database: DatabaseManager
        private set
    lateinit var csvExporter: CsvExporter
        private set

    val databaseAvailable: Boolean
        get() = this::database.isInitialized

    /**
     * Purpose: Initialize application-wide services once when Android starts the app process.
     * Inputs: None; Android invokes this callback.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Database failures leave admin settings accessible for recovery.
     */
    override fun onCreate() {
        super.onCreate()
        AppConfig.initialize(this)
        logger = AppLogger(this)
        validateConfiguration()
        initializeDatabase()
    }

    /** Retry unavailable storage without replacing a working database connection. */
    fun initializeDatabase(): Boolean {
        if (databaseAvailable) return true
        val opened = try {
            DatabaseManager(this, logger)
        } catch (e: Exception) {
            logger.error("DB_OPEN_FAILED", "Could not open the configured database; admin settings remain available", e)
            return false
        }
        database = opened
        csvExporter = CsvExporter(database, logger)
        return true
    }

    /**
     * Purpose: Log obvious deployment configuration problems without preventing startup.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: Problems are warnings in error.log; they do not crash the app.
     */
    private fun validateConfiguration() {
        if (!AppConfig.ADMIN_PASSCODE.matches(Regex("\\d{4}"))) {
            logger.error("CONFIG_WARNING", "ADMIN_PASSCODE should contain exactly four digits")
        }
        if (AppConfig.SMB_EXPORT_ENABLED && (AppConfig.SMB_USERNAME == "CHANGE_ME" || AppConfig.SMB_PASSWORD == "CHANGE_ME")) {
            logger.error("CONFIG_WARNING", "Replace the default SMB username/password in AppConfig before production use")
        }
        if (AppConfig.SLACK_NOTIFICATIONS_ENABLED &&
            AppConfig.SLACK_BOT_TOKEN.isBlank()
        ) {
            logger.error("CONFIG_WARNING", "Slack bot token is required when Slack notifications are enabled")
        }
        if (AppConfig.SLACK_NOTIFICATIONS_ENABLED &&
            AppConfig.SLACK_CHANNEL.isBlank()
        ) {
            logger.error("CONFIG_WARNING", "Slack channel is required for channel notifications")
        }
    }
}
