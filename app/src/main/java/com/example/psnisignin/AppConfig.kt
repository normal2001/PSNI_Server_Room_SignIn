package com.example.psnisignin

import android.content.Context
import android.content.SharedPreferences

/**
 * Central place for every setting that a site administrator is expected to change.
 *
 * BuildConfig supplies deployment defaults and optional local debug defaults.
 * Values saved from the admin screen take precedence and persist between launches.
 */
object AppConfig {
    private const val PREFERENCES_NAME = "app_config"
    private var preferences: SharedPreferences? = null

    val DEFAULT_SMB_HOST = BuildConfig.SMB_HOST
    val DEFAULT_SMB_SHARE_NAME = BuildConfig.SMB_SHARE_NAME
    val DEFAULT_SMB_USERNAME = BuildConfig.SMB_USERNAME
    val DEFAULT_SMB_PASSWORD = BuildConfig.SMB_PASSWORD
    val DEFAULT_SMB_DOMAIN = BuildConfig.SMB_DOMAIN
    val DEFAULT_SMB_EXPORT_ENABLED = BuildConfig.SMB_EXPORT_ENABLED

    val DEFAULT_ADMIN_PASSCODE = BuildConfig.ADMIN_PASSCODE
    val DEFAULT_SLACK_NOTIFICATIONS_ENABLED = BuildConfig.SLACK_NOTIFICATIONS_ENABLED
    val DEFAULT_SLACK_CHANNEL = BuildConfig.SLACK_CHANNEL
    val DEFAULT_SLACK_BOT_TOKEN = BuildConfig.SLACK_BOT_TOKEN

    val DEFAULT_DATABASE_PATH = BuildConfig.DATABASE_PATH
    val DEFAULT_AUDIT_LOG_PATH = BuildConfig.AUDIT_LOG_PATH
    val DEFAULT_ERROR_LOG_PATH = BuildConfig.ERROR_LOG_PATH
    val DEFAULT_TIMESTAMP_PATTERN = BuildConfig.TIMESTAMP_PATTERN

    /** Make persisted settings available before application services are created. */
    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences?.edit()?.remove("SMB_CSV_DIRECTORY")?.remove("SLACK_NOTIFICATION_TARGET")?.remove("SLACK_USER_GROUP")?.apply()
    }

    private fun stringValue(key: String, defaultValue: String): String =
        preferences?.getString(key, defaultValue) ?: defaultValue

    private fun booleanValue(key: String, defaultValue: Boolean): Boolean =
        preferences?.getBoolean(key, defaultValue) ?: defaultValue

    var SMB_HOST: String
        get() = stringValue("SMB_HOST", DEFAULT_SMB_HOST)
        set(value) { preferences?.edit()?.putString("SMB_HOST", value)?.apply() }
    var SMB_SHARE_NAME: String
        get() = stringValue("SMB_SHARE_NAME", DEFAULT_SMB_SHARE_NAME)
        set(value) { preferences?.edit()?.putString("SMB_SHARE_NAME", value)?.apply() }
    var SMB_USERNAME: String
        get() = stringValue("SMB_USERNAME", DEFAULT_SMB_USERNAME)
        set(value) { preferences?.edit()?.putString("SMB_USERNAME", value)?.apply() }
    var SMB_PASSWORD: String
        get() = stringValue("SMB_PASSWORD", DEFAULT_SMB_PASSWORD)
        set(value) { preferences?.edit()?.putString("SMB_PASSWORD", value)?.apply() }
    var SMB_DOMAIN: String
        get() = stringValue("SMB_DOMAIN", DEFAULT_SMB_DOMAIN)
        set(value) { preferences?.edit()?.putString("SMB_DOMAIN", value)?.apply() }
    var SMB_EXPORT_ENABLED: Boolean
        get() = booleanValue("SMB_EXPORT_ENABLED", DEFAULT_SMB_EXPORT_ENABLED)
        set(value) { preferences?.edit()?.putBoolean("SMB_EXPORT_ENABLED", value)?.apply() }

    var ADMIN_PASSCODE: String
        get() = stringValue("ADMIN_PASSCODE", DEFAULT_ADMIN_PASSCODE)
        set(value) { preferences?.edit()?.putString("ADMIN_PASSCODE", value)?.apply() }
    var SLACK_NOTIFICATIONS_ENABLED: Boolean
        get() = booleanValue("SLACK_NOTIFICATIONS_ENABLED", DEFAULT_SLACK_NOTIFICATIONS_ENABLED)
        set(value) { preferences?.edit()?.putBoolean("SLACK_NOTIFICATIONS_ENABLED", value)?.apply() }
    var SLACK_CHANNEL: String
        get() = stringValue("SLACK_CHANNEL", DEFAULT_SLACK_CHANNEL)
        set(value) { preferences?.edit()?.putString("SLACK_CHANNEL", value)?.apply() }
    var SLACK_BOT_TOKEN: String
        get() = stringValue("SLACK_BOT_TOKEN", DEFAULT_SLACK_BOT_TOKEN)
        set(value) { preferences?.edit()?.putString("SLACK_BOT_TOKEN", value)?.apply() }

    var DATABASE_PATH: String
        get() = stringValue("DATABASE_PATH", DEFAULT_DATABASE_PATH)
        set(value) { preferences?.edit()?.putString("DATABASE_PATH", value)?.apply() }
    var AUDIT_LOG_PATH: String
        get() = stringValue("AUDIT_LOG_PATH", DEFAULT_AUDIT_LOG_PATH)
        set(value) { preferences?.edit()?.putString("AUDIT_LOG_PATH", value)?.apply() }
    var ERROR_LOG_PATH: String
        get() = stringValue("ERROR_LOG_PATH", DEFAULT_ERROR_LOG_PATH)
        set(value) { preferences?.edit()?.putString("ERROR_LOG_PATH", value)?.apply() }
    var TIMESTAMP_PATTERN: String
        get() = stringValue("TIMESTAMP_PATTERN", DEFAULT_TIMESTAMP_PATTERN)
        set(value) { preferences?.edit()?.putString("TIMESTAMP_PATTERN", value)?.apply() }

    /** Save all file-share settings as one preference transaction. */
    fun saveFileshare(enabled: Boolean, host: String, domain: String, username: String, password: String, shareName: String) {
        preferences?.edit()
            ?.putBoolean("SMB_EXPORT_ENABLED", enabled)
            ?.putString("SMB_HOST", host)
            ?.putString("SMB_DOMAIN", domain)
            ?.putString("SMB_USERNAME", username)
            ?.putString("SMB_PASSWORD", password)
            ?.putString("SMB_SHARE_NAME", shareName)
            ?.apply()
    }

    /** Save all application settings as one preference transaction. */
    fun saveApplication(databasePath: String, auditLogPath: String, errorLogPath: String, timestampPattern: String) {
        preferences?.edit()
            ?.putString("DATABASE_PATH", databasePath)
            ?.putString("AUDIT_LOG_PATH", auditLogPath)
            ?.putString("ERROR_LOG_PATH", errorLogPath)
            ?.putString("TIMESTAMP_PATTERN", timestampPattern)
            ?.apply()
    }

    /** Save the Slack-related admin settings as one preference transaction. */
    fun saveAdmin(
        slackNotificationsEnabled: Boolean,
        slackChannel: String,
        slackBotToken: String
    ) {
        preferences?.edit()
            ?.putBoolean("SLACK_NOTIFICATIONS_ENABLED", slackNotificationsEnabled)
            ?.putString("SLACK_CHANNEL", slackChannel)
            ?.putString("SLACK_BOT_TOKEN", slackBotToken)
            ?.apply()
    }

    /** Configurable reasons displayed on sign-in and admin-edit forms. */
    val REASON_OPTIONS = BuildConfig.REASON_OPTIONS.split('|')
    /** Use mock data for testing so that AppConfig.kt does not need editing. */
    val useMockData: Boolean = BuildConfig.USE_MOCK_DATA
}
