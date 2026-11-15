package com.langhost.data

import android.content.Context
import java.io.File

/** Creates temporary files inside the only directory exposed by the app's FileProvider. */
internal object ShareFileStore {
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    fun create(context: Context, fileName: String): File {
        require(fileName.startsWith("langhost_"))
        require(fileName.none { it == '/' || it == '\\' })

        val directory = File(context.cacheDir, "shared")
        check(directory.isDirectory || directory.mkdirs()) {
            "Unable to create share cache directory"
        }
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("langhost_") && file.lastModified() < cutoff) {
                file.delete()
            }
        }
        return File(directory, fileName)
    }
}
