package com.example.psnisignin

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/** Writes the requested audit and application error log files. */
class AppLogger(private val context: Context) {

    /**
     * Purpose: Append one successful database transaction to the audit log.
     * Inputs: transactionType - sign-in, sign-out, or admin-edit; details - short human-readable context.
     * Optional inputs: details may be an empty string.
     * Returns: Unit.
     * Error handling: File write failures are redirected to Android Log and error.log when possible.
     */
    fun audit(transactionType: String, details: String = "") {
        val line = "${TimeUtils.nowTimestamp()} | $transactionType | $details\n"
        try {
            append(resolveLocalPath(AppConfig.AUDIT_LOG_PATH), line)
        } catch (e: Exception) {
            Log.e("PSNI-Audit", "Could not write audit log", e)
            error("AUDIT_WRITE_FAILED", "Could not write audit entry: ${e.message}", e)
        }
    }

    /**
     * Purpose: Append an application warning/error to the configured error log.
     * Inputs: code - stable short identifier; message - readable description.
     * Optional inputs: throwable - related exception, if one exists.
     * Returns: Unit.
     * Error handling: If error.log itself cannot be written, the message is sent to Android Log only.
     */
    fun error(code: String, message: String, throwable: Throwable? = null) {
        val exceptionText = throwable?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""
        val line = "${TimeUtils.nowTimestamp()} | $code | $message$exceptionText\n"
        try {
            append(resolveLocalPath(AppConfig.ERROR_LOG_PATH), line)
        } catch (logFailure: Exception) {
            Log.e("PSNI-Error", "$line (also failed to write error.log)", logFailure)
        }
        if (throwable != null) Log.e("PSNI-Error", "$code: $message", throwable)
        else Log.w("PSNI-Error", "$code: $message")
    }

    /**
     * Purpose: Resolve a configurable local file path using Android-safe semantics.
     * Inputs: configuredPath - absolute path or a relative path such as ./audit.log.
     * Optional inputs: None.
     * Returns: File pointing to the requested location.
     * Error handling: This function does not access the file; permission errors occur later during I/O.
     */
    fun resolveLocalPath(configuredPath: String): File {
        val raw = File(configuredPath)
        return if (raw.isAbsolute) raw else File(context.filesDir, configuredPath.removePrefix("./"))
    }

    /**
     * Purpose: Append UTF-8 text to a local file, creating parent folders and the file when needed.
     * Inputs: file - destination file; text - text to append.
     * Optional inputs: None.
     * Returns: Unit.
     * Error handling: IOException is propagated to the public logging methods for fallback handling.
     */
    @Throws(IOException::class)
    private fun append(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.appendText(text, Charsets.UTF_8)
    }
}
