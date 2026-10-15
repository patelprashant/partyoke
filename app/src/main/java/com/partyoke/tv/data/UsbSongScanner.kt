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
