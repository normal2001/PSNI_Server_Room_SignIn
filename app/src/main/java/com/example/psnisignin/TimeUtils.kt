package com.example.psnisignin

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Simple date/time helpers so timestamp rules are defined in one place. */
object TimeUtils {
    private fun timestampFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern(AppConfig.TIMESTAMP_PATTERN)

    /**
     * Purpose: Return the current local date/time in the application's storage format.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Timestamp string using AppConfig.TIMESTAMP_PATTERN.
     * Error handling: java.time formatting failures propagate because they indicate a programming/configuration error.
     */
    fun nowTimestamp(): String = LocalDateTime.now().format(timestampFormatter())

    /**
     * Purpose: Return today's local calendar date.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: LocalDate for the device's current local date.
     * Error handling: No expected recoverable errors.
     */
    fun today(): LocalDate = LocalDate.now()

    /**
     * Purpose: Build the lower bound requested for today's active sign-out list (00:00:01).
     * Inputs: date - calendar date to use.
     * Optional inputs: None.
     * Returns: Timestamp string at 00:00:01 for the supplied date.
     * Error handling: Formatting errors propagate as configuration/programming errors.
     */
    fun dayStartAtOneSecond(date: LocalDate): String =
        date.atTime(0, 0, 1).format(timestampFormatter())

    /**
     * Purpose: Build the exclusive upper bound for a date query.
     * Inputs: date - date whose following midnight is needed.
     * Optional inputs: None.
     * Returns: Timestamp string for midnight at the beginning of the next day.
     * Error handling: Date overflow is theoretically possible but not expected for UI-selected dates.
     */
    fun nextDayStart(date: LocalDate): String =
        date.plusDays(1).atStartOfDay().format(timestampFormatter())

    /**
     * Purpose: Validate an admin-entered timestamp strictly.
     * Inputs: value - text to validate.
     * Optional inputs: allowBlank - when true, an empty value is accepted for sign-out.
     * Returns: true when the value is blank-and-allowed or exactly parses and round-trips in the configured format.
     * Error handling: DateTimeParseException is caught and converted to false.
     */
    fun isValidTimestamp(value: String, allowBlank: Boolean = false): Boolean {
        if (value.isBlank()) return allowBlank
        return try {
            val formatter = timestampFormatter()
            val parsed = LocalDateTime.parse(value, formatter)
            parsed.format(formatter) == value
        } catch (_: DateTimeParseException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /**
     * Purpose: Return the four-digit current year for the CSV filename.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Current year as a decimal string such as "2026".
     * Error handling: No expected recoverable errors.
     */
    fun currentYearText(): String = LocalDate.now().year.toString()
}
