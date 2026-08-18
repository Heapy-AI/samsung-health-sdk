package com.example.shealthpoc

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.FileNotFoundException

/**
 * Publishes the exported JSON into the shared Downloads collection so the files can be opened
 * on the phone itself (My Files > Downloads > SHealthPoC) without USB/ADB.
 *
 * Uses MediaStore only. minSdk is 29, so writing our own files into
 * [MediaStore.Downloads] needs **no runtime permission at all** - no WRITE_EXTERNAL_STORAGE,
 * no MANAGE_EXTERNAL_STORAGE.
 *
 * The app-private copies written by [HealthDataExporter] are kept as they are.
 */
class DownloadsExporter(private val context: Context) {

    /**
     * Writes [content] to `Download/SHealthPoC/<fileName>`, replacing the previous export
     * instead of piling up "steps (1).json" duplicates.
     *
     * @return the user-visible path, e.g. `Download/SHealthPoC/steps.json`
     */
    fun publish(fileName: String, content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val existing = findExisting(fileName)

        val uri = if (existing != null) {
            runCatching { writeTo(existing, bytes) }
                .getOrElse {
                    // Row still in MediaStore but the file was removed by the user - start over.
                    if (it is FileNotFoundException) {
                        runCatching { context.contentResolver.delete(existing, null, null) }
                        writeTo(insertNew(fileName), bytes)
                    } else {
                        throw it
                    }
                }
        } else {
            writeTo(insertNew(fileName), bytes)
        }

        // Clear IS_PENDING so the file becomes visible to other apps (My Files, etc.)
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        return DISPLAY_DIR + fileName
    }

    private fun insertNew(fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_JSON)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore.insert() returned null for $fileName")
    }

    private fun writeTo(uri: Uri, bytes: ByteArray): Uri {
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 1) },
            null,
            null,
        )
        // "wt" truncates, so a shorter export never leaves stale bytes behind.
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("openOutputStream() returned null for $uri")
        return uri
    }

    private fun findExisting(fileName: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?, ?)"
        val args = arrayOf(fileName, RELATIVE_PATH, RELATIVE_PATH.trimEnd('/'))

        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }

    companion object {
        const val FOLDER_NAME = "SHealthPoC"
        private const val MIME_JSON = "application/json"

        /** MediaStore value, e.g. "Download/SHealthPoC/" */
        private val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME + "/"

        /** What we show the user, e.g. "Download/SHealthPoC/steps.json" */
        val DISPLAY_DIR: String = RELATIVE_PATH
    }
}
