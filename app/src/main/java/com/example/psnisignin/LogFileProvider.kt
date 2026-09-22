package com.example.psnisignin

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Shares only configured logs with viewers explicitly granted read access by the admin screen. */
class LogFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun resolveLog(uri: Uri): File {
        val providerContext = requireNotNull(context)
        val segments = uri.pathSegments
        if (uri.authority != "${providerContext.packageName}.logs" || segments.size != 2) {
            throw FileNotFoundException("Unknown log URI")
        }
        val path = when (segments[0]) {
            "audit" -> AppConfig.AUDIT_LOG_PATH
            "error" -> AppConfig.ERROR_LOG_PATH
            else -> throw FileNotFoundException("Unknown log")
        }
        val file = AppLogger(providerContext).resolveLocalPath(path).canonicalFile
        // A previously granted URI must never expose a newly configured file.
        if (file.path != segments[1] || !file.isFile || !file.canRead()) {
            throw FileNotFoundException("Log unavailable")
        }
        return file
    }

    override fun getType(uri: Uri): String = "text/plain"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Logs are read-only")
        return ParcelFileDescriptor.open(resolveLog(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val file = resolveLog(uri)
        val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
        return MatrixCursor(columns.toTypedArray()).apply {
            addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) file.name else file.length() })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Logs are read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Logs are read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Logs are read-only")
}
