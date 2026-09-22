package com.example.psnisignin

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.EnumSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Rebuilds the current-year CSV after each successful database write.
 * Exporting is intentionally asynchronous and best-effort: SQLite remains authoritative.
 */
class CsvExporter(
    private val database: DatabaseManager,
    private val logger: AppLogger
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Purpose: Queue a current-year CSV export without blocking the user interface.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Unit immediately after queueing.
     * Error handling: Any database, network, authentication, or SMB file error is caught on the worker thread and written to error.log.
     */
    fun exportCurrentYearAsync() {
        executor.execute {
            try {
                exportCurrentYear()
            } catch (e: Exception) {
                logger.error("SMB_EXPORT_FAILED", "CSV export failed; the local database transaction remains committed", e)
            }
        }
    }

    /** Copy the current yearly CSV to a timestamped export filename on the SMB share. */
    fun copyCurrentYearSnapshotAsync(onComplete: (success: Boolean, message: String) -> Unit) {
        executor.execute {
            try {
                val fileName = copyCurrentYearSnapshot()
                onComplete(true, "CSV exported successfully as $fileName")
            } catch (e: Exception) {
                logger.error("SMB_SNAPSHOT_EXPORT_FAILED", "Could not create the timestamped CSV export copy", e)
                onComplete(false, "CSV export failed. Check the error log for details.")
            }
        }
    }

    /**
     * Purpose: Build and write the yearly CSV snapshot to the configured SMB share.
     * Inputs: None.
     * Optional inputs: None.
     * Returns: Unit after the remote file is fully written.
     * Error handling: Throws failures to exportCurrentYearAsync(), where they are logged without affecting SQLite.
     */
    private fun exportCurrentYear() {
        if (!AppConfig.SMB_EXPORT_ENABLED) {
            logger.error("SMB_EXPORT_DISABLED", "SMB export is disabled in AppConfig")
            return
        }

        val year = LocalDate.now().year
        val records = database.getRecordsForYear(year)
        val csvBytes = buildCsv(records).toByteArray(StandardCharsets.UTF_8)
        val fileName = "PSNI_Server_Room_Sign-In_Sheet_${TimeUtils.currentYearText()}.csv"

        SMBClient().use { client ->
            client.connect(AppConfig.SMB_HOST).use { connection ->
                val auth = AuthenticationContext(
                    AppConfig.SMB_USERNAME,
                    AppConfig.SMB_PASSWORD.toCharArray(),
                    AppConfig.SMB_DOMAIN
                )
                connection.authenticate(auth).use { session ->
                    (session.connectShare(AppConfig.SMB_SHARE_NAME) as DiskShare).use { share ->
                        writeCsvFile(share, fileName, csvBytes)
                    }
                }
            }
        }
    }

    /** Create a missing yearly CSV from SQLite, then copy it to a timestamped export file. */
    private fun copyCurrentYearSnapshot(): String {
        if (!AppConfig.SMB_EXPORT_ENABLED) {
            throw IllegalStateException("SMB export is disabled in AppConfig")
        }
        val year = LocalDate.now().year
        val sourceName = "PSNI_Server_Room_Sign-In_Sheet_${year}.csv"
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
        val destinationName = "PSNI_Server_Room_Sign-In_Sheet_${timestamp}_export.csv"

        SMBClient().use { client ->
            client.connect(AppConfig.SMB_HOST).use { connection ->
                val auth = AuthenticationContext(
                    AppConfig.SMB_USERNAME,
                    AppConfig.SMB_PASSWORD.toCharArray(),
                    AppConfig.SMB_DOMAIN
                )
                connection.authenticate(auth).use { session ->
                    (session.connectShare(AppConfig.SMB_SHARE_NAME) as DiskShare).use { share ->
                        if (!share.fileExists(sourceName)) {
                            val csvBytes = buildCsv(database.getRecordsForYear(year))
                                .toByteArray(StandardCharsets.UTF_8)
                            writeCsvFile(share, sourceName, csvBytes)
                        }
                        share.openFile(
                            sourceName,
                            EnumSet.of(AccessMask.GENERIC_READ),
                            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OPEN,
                            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                        ).use { sourceFile ->
                            share.openFile(
                                destinationName,
                                EnumSet.of(AccessMask.GENERIC_WRITE),
                                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                                SMB2ShareAccess.ALL,
                                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                            ).use { destinationFile ->
                                sourceFile.inputStream.use { input ->
                                    destinationFile.outputStream.use { output ->
                                        input.copyTo(output)
                                        output.flush()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return destinationName
    }

    /** Create or replace a CSV, including its header when there are no records. */
    private fun writeCsvFile(share: DiskShare, fileName: String, csvBytes: ByteArray) {
        share.openFile(
            fileName,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        ).use { remoteFile ->
            remoteFile.outputStream.use { output ->
                output.write(csvBytes)
                output.flush()
            }
        }
    }

    /**
     * Purpose: Convert database records to the six-column CSV format.
     * Inputs: records - rows to serialize.
     * Optional inputs: None.
     * Returns: UTF-8-ready CSV text including a header row.
     * Error handling: No expected recoverable errors; individual values are safely CSV-escaped.
     */
    private fun buildCsv(records: List<SignInRecord>): String = buildString {
        appendLine("sign_in_datetime,sign_out_datetime,first_name,last_name,reason")
        records.forEach { record ->
            append(csvEscape(record.signInDateTime))
            append(',')
            append(csvEscape(record.signOutDateTime ?: ""))
            append(',')
            append(csvEscape(record.firstName))
            append(',')
            append(csvEscape(record.lastName))
            append(',')
            append(csvEscape(record.reason ?: ""))
            append('\n')
        }
    }

    /**
     * Purpose: Escape one CSV field according to standard comma/quote/newline rules.
     * Inputs: value - raw field text.
     * Optional inputs: None.
     * Returns: Safe CSV field text, quoted only when needed.
     * Error handling: No expected errors.
     */
    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

}
