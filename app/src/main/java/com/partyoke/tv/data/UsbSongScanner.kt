package com.partyoke.tv.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.partyoke.tv.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans a user-picked folder (typically on an attached USB drive) for karaoke
 * video files using MediaStore and Storage Access Framework.
 */
class UsbSongScanner(private val context: Context) {

    /**
     * Recursively lists supported video files under [treeUri].
     *
     * @param treeUri the OPEN_DOCUMENT_TREE uri returned by the folder picker
     */
    suspend fun scan(treeUri: Uri): List<Song> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext emptyList()

        val results = mutableListOf<Song>()
        collect(root, results)
        results.sortedBy { it.title.lowercase() }
    }

    /**
     * Recursively lists supported video files under a standard java.io.File directory (useful for emulator/fallback).
     */
    suspend fun scanDirectory(dir: java.io.File): List<Song> = withContext(Dispatchers.IO) {
        android.util.Log.d("UsbSongScanner", "Scanning directory: ${dir.absolutePath}, exists: ${dir.exists()}, isDir: ${dir.isDirectory}")
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        val results = mutableListOf<Song>()
        collectFiles(dir, results)
        android.util.Log.d("UsbSongScanner", "Found ${results.size} songs in ${dir.absolutePath}")
        results.sortedBy { it.title.lowercase() }
    }

    /**
     * Scans for karaoke videos in "PartyOkeSongs" using the system MediaStore database,
     * which bypasses raw file mount permission restrictions on Android TV USB drives.
     */
    suspend fun scanViaMediaStore(): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )
        val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%PartyOkeSongs%")

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val displayCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val dataPath = cursor.getString(dataCol) ?: continue
                    val displayName = if (displayCol != -1) cursor.getString(displayCol) else null
                    val title = if (titleCol != -1) cursor.getString(titleCol) else null
                    val fileName = displayName ?: java.io.File(dataPath).name
                    val songTitle = title?.takeIf { it.isNotBlank() } ?: fileName.toTitle()

                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    results.add(
                        Song(
                            id = id.toString(),
                            title = songTitle,
                            uri = uri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UsbSongScanner", "Error querying MediaStore with selection", e)
        }

        if (results.isEmpty()) {
            try {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val displayCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val dataPath = cursor.getString(dataCol) ?: continue
                        if (dataPath.contains("PartyOkeSongs", ignoreCase = true)) {
                            val displayName = if (displayCol != -1) cursor.getString(displayCol) else null
                            val fileName = displayName ?: java.io.File(dataPath).name
                            val uri = android.content.ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id
                            ).toString()
                            results.add(
                                Song(
                                    id = id.toString(),
                                    title = fileName.toTitle(),
                                    uri = uri
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UsbSongScanner", "Error querying all MediaStore videos", e)
            }
        }

        android.util.Log.d("UsbSongScanner", "MediaStore scan found ${results.size} songs.")
        results.distinctBy { it.uri }.sortedBy { it.title.lowercase() }
    }

    /**
     * Scans for a folder named "PartyOkeSongs" across MediaStore and storage locations.
     */
    suspend fun scanPartyOkeSongs(): List<Song> = withContext(Dispatchers.IO) {
        val mediaStoreSongs = scanViaMediaStore()
        if (mediaStoreSongs.isNotEmpty()) {
            android.util.Log.d("UsbSongScanner", "Successfully loaded ${mediaStoreSongs.size} songs via MediaStore.")
            return@withContext mediaStoreSongs
        }

        android.util.Log.d("UsbSongScanner", "MediaStore returned 0 songs. Falling back to raw storage scan.")
        val results = mutableListOf<Song>()
        val partyOkeFolders = mutableListOf<java.io.File>()

        fun findFolders(dir: java.io.File) {
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        if (file.name.equals("PartyOkeSongs", ignoreCase = true)) {
                            partyOkeFolders.add(file)
                        } else {
                            findFolders(file)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val roots = mutableListOf<java.io.File>()
        context.filesDir?.let { roots.add(it) }
        context.getExternalFilesDirs(null).filterNotNull().forEach { roots.add(it) }

        val storageDir = java.io.File("/storage")
        if (storageDir.exists() && storageDir.isDirectory) {
            storageDir.listFiles()?.forEach { volume ->
                if (volume.isDirectory) {
                    roots.add(volume)
                }
            }
        }
        try {
            val extStorage = android.os.Environment.getExternalStorageDirectory()
            if (extStorage != null && extStorage.exists() && extStorage.isDirectory) {
                roots.add(extStorage)
            }
        } catch (_: Exception) {}

        roots.forEach { root ->
            if (root.name.equals("PartyOkeSongs", ignoreCase = true)) {
                partyOkeFolders.add(root)
            } else {
                findFolders(root)
            }
        }

        if (partyOkeFolders.isNotEmpty()) {
            partyOkeFolders.forEach { folder ->
                collectFiles(folder, results)
            }
        }

        if (results.isEmpty()) {
            return@withContext scanAllStorage()
        }

        results.distinctBy { it.uri }.sortedBy { it.title.lowercase() }
    }

    /**
     * Scans all available storage locations including internal files, external files dirs,
     * and mounted volumes under /storage (USB drives, SD cards, etc.).
     */
    suspend fun scanAllStorage(): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        val scannedPaths = mutableSetOf<String>()

        // 1. Internal filesDir
        context.filesDir?.let { dir ->
            if (dir.exists() && dir.isDirectory && scannedPaths.add(dir.absolutePath)) {
                collectFiles(dir, results)
            }
        }

        // 2. External files dirs (USB / SD cards)
        context.getExternalFilesDirs(null).forEach { dir ->
            if (dir != null && dir.exists() && dir.isDirectory && scannedPaths.add(dir.absolutePath)) {
                collectFiles(dir, results)
            }
        }

        // 3. Mount points under /storage (USB drives on Android TV)
        val storageDir = java.io.File("/storage")
        if (storageDir.exists() && storageDir.isDirectory) {
            storageDir.listFiles()?.forEach { volume ->
                if (volume.isDirectory && scannedPaths.add(volume.absolutePath)) {
                    collectFiles(volume, results)
                }
            }
        }

        // 4. Environment external storage
        try {
            val extStorage = android.os.Environment.getExternalStorageDirectory()
            if (extStorage != null && extStorage.exists() && extStorage.isDirectory && scannedPaths.add(extStorage.absolutePath)) {
                collectFiles(extStorage, results)
            }
        } catch (_: Exception) {
        }

        android.util.Log.d("UsbSongScanner", "Scan all storage found ${results.size} songs total.")
        results.distinctBy { it.uri }.sortedBy { it.title.lowercase() }
    }

    private fun collect(dir: DocumentFile, out: MutableList<Song>) {
        for (file in dir.listFiles()) {
            when {
                file.isDirectory -> collect(file, out)
                file.isFile && file.isSupportedVideo() -> {
                    val name = file.name ?: continue
                    out.add(
                        Song(
                            id = file.uri.toString().hashCode().toString(),
                            title = name.toTitle(),
                            uri = file.uri.toString(),
                        )
                    )
                }
            }
        }
    }

    private fun collectFiles(dir: java.io.File, out: MutableList<Song>) {
        android.util.Log.d("UsbSongScanner", "collectFiles scanning dir: ${dir.absolutePath}, exists: ${dir.exists()}, canRead: ${dir.canRead()}")
        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            android.util.Log.e("UsbSongScanner", "Exception listing files in ${dir.absolutePath}", e)
            null
        }
        if (files == null) {
            android.util.Log.w("UsbSongScanner", "dir.listFiles() returned null for ${dir.absolutePath} (Storage read permission missing or restricted)")
            return
        }
        android.util.Log.d("UsbSongScanner", "dir.listFiles() found ${files.size} items in ${dir.absolutePath}")
        files.forEach { file ->
            try {
                when {
                    file.isDirectory -> collectFiles(file, out)
                    file.isFile && isSupportedVideoFile(file.name) -> {
                        val name = file.name
                        android.util.Log.d("UsbSongScanner", "Found supported video: $name at ${file.absolutePath}")
                        out.add(
                            Song(
                                id = file.absolutePath.hashCode().toString(),
                                title = name.toTitle(),
                                uri = Uri.fromFile(file).toString(),
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UsbSongScanner", "Error processing file ${file?.absolutePath}", e)
            }
        }
    }

    private fun DocumentFile.isSupportedVideo(): Boolean {
        val n = name?.lowercase() ?: return false
        return SUPPORTED_EXTENSIONS.any { n.endsWith(it) }
    }

    private fun isSupportedVideoFile(name: String): Boolean {
        val n = name.lowercase()
        return SUPPORTED_EXTENSIONS.any { n.endsWith(it) }
    }

    /** "01 - Bohemian_Rhapsody.mp4" -> "Bohemian Rhapsody" */
    private fun String.toTitle(): String {
        var t = substringBeforeLast('.')
        t = t.replace(Regex("^\\d+\\s*[-._]\\s*"), "")
        t = t.replace('_', ' ').replace('.', ' ')
        return t.trim().ifEmpty { this }
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = listOf(
            ".mp4", ".mkv", ".webm", ".avi", ".mov", ".m4v", ".3gp", ".ts",
        )
    }
}
