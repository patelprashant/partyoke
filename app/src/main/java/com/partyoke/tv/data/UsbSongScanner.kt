package com.partyoke.tv.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.partyoke.tv.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans a user-picked folder (typically on an attached USB drive) for karaoke
 * video files using the Storage Access Framework.
 *
 * We use SAF rather than raw file paths because USB mount points are
 * inconsistent across Android TV manufacturers, and SAF grants a persistable
 * permission to the exact folder the host selected.
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
     * Scans for a folder named "PartyOkeSongs" across all available storage locations (USB, external, internal).
     * If found, scans songs within that folder. If not found, falls back to scanning all storage locations.
     */
    suspend fun scanPartyOkeSongs(): List<Song> = withContext(Dispatchers.IO) {
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

        android.util.Log.d("UsbSongScanner", "Found ${partyOkeFolders.size} 'PartyOkeSongs' folder(s).")

        if (partyOkeFolders.isNotEmpty()) {
            partyOkeFolders.forEach { folder ->
                collectFiles(folder, results)
            }
        } else {
            android.util.Log.d("UsbSongScanner", "'PartyOkeSongs' folder not found. Falling back to scan all storage.")
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
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> collectFiles(file, out)
                file.isFile && isSupportedVideoFile(file.name) -> {
                    val name = file.name
                    out.add(
                        Song(
                            id = file.absolutePath.hashCode().toString(),
                            title = name.toTitle(),
                            uri = Uri.fromFile(file).toString(),
                        )
                    )
                }
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
