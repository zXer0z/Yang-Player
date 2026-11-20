package com.yang.player.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException
import java.io.IOException

/** Gives the player and image loader a private URI, separate from the authenticated URL. */
class ServerCoverProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/*"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val app = context?.applicationContext ?: throw FileNotFoundException()
        if (mode != "r" || uri.authority != ServerLibrary.COVER_AUTHORITY || uri.pathSegments.size != 2) {
            throw FileNotFoundException()
        }
        return openPipeHelper(uri, getType(uri), null, app) { output, coverUri, _, _, _ ->
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
                    stream.write(ServerLibrary.cover(app, coverUri))
                }
            } catch (_: IOException) {
                // Missing covers and closed readers are handled by the player's placeholder.
            }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
