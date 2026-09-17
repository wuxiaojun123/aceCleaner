package com.nice.aceclean.cleanup

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

enum class CleanupCategory { APP_CACHE, TEMP_FILES, APK_FILES, EMPTY_FILES }

data class CleanupCandidate(
    val category: CleanupCategory,
    val sizeBytes: Long,
    val uri: Uri? = null,
    val filePath: String? = null,
    val isDirectory: Boolean = false,
)

data class CleanupScanStats(
    val scannedFiles: Int = 0,
    val scannedDirectories: Int = 0,
    val inaccessibleDirectories: Int = 0,
    val limitReached: Boolean = false,
)

data class CleanupScanResult(
    val candidates: List<CleanupCandidate>,
    val stats: CleanupScanStats = CleanupScanStats(),
) {
    fun candidates(category: CleanupCategory): List<CleanupCandidate> = candidates.filter { it.category == category }
    fun size(category: CleanupCategory): Long = candidates(category).sumOf { it.sizeBytes }
    val totalBytes: Long get() = candidates.sumOf { it.sizeBytes }
}

data class CleanupDeleteResult(val deletedCount: Int, val failedCount: Int, val releasedBytes: Long)

object CleanupRepository {
    suspend fun scan(
        context: Context,
        onProgress: (CleanupScanStats) -> Unit = {},
    ): CleanupScanResult = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<CleanupCandidate>()
        val stats = MutableScanStats()
        val appCacheRoots = buildList {
            add(context.cacheDir)
            context.externalCacheDirs.filterNotNull().forEach(::add)
        }.distinctBy(File::getAbsolutePath)
        appCacheRoots.forEach { root -> scanCacheRoot(root, candidates, stats, onProgress) }

        sharedStorageRoots(context).forEach { root -> scanSharedRoot(root, candidates, stats, onProgress) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            candidates += runCatching { querySharedCandidates(context, stats, onProgress) }.getOrDefault(emptyList())
        }
        onProgress(stats.snapshot())
        CleanupScanResult(
            candidates.distinctBy { candidate ->
                candidate.filePath?.let { path -> runCatching { File(path).canonicalPath }.getOrDefault(path) }
                    ?: candidate.uri.toString()
            },
            stats.snapshot(),
        )
    }

    private suspend fun scanCacheRoot(
        root: File,
        output: MutableList<CleanupCandidate>,
        stats: MutableScanStats,
        onProgress: (CleanupScanStats) -> Unit,
    ) {
        if (!root.exists()) return
        walkFiles(root, stats, onProgress) { file ->
            if (file.isFile) {
                output += CleanupCandidate(
                    category = CleanupCategory.APP_CACHE,
                    sizeBytes = file.length().coerceAtLeast(0L),
                    filePath = file.absolutePath,
                )
            }
        }
    }

    private suspend fun scanSharedRoot(
        root: File,
        output: MutableList<CleanupCandidate>,
        stats: MutableScanStats,
        onProgress: (CleanupScanStats) -> Unit,
    ) {
        if (!root.exists() || !root.canRead()) {
            stats.inaccessibleDirectories++
            return
        }
        walkFiles(root, stats, onProgress) { file ->
            if (file.isDirectory) {
                if (isRemovableEmptyDirectory(file, root)) {
                    output += CleanupCandidate(
                        category = CleanupCategory.EMPTY_FILES,
                        sizeBytes = 0L,
                        filePath = file.absolutePath,
                        isDirectory = true,
                    )
                }
                return@walkFiles
            }
            val size = file.length().coerceAtLeast(0L)
            val category = CleanupClassifier.classify(file.name, size, file.absolutePath) ?: return@walkFiles
            output += CleanupCandidate(category, size, filePath = file.absolutePath)
        }
    }

    private suspend fun walkFiles(
        root: File,
        stats: MutableScanStats,
        onProgress: (CleanupScanStats) -> Unit,
        visit: (File) -> Unit,
    ) {
        val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: run {
            stats.inaccessibleDirectories++
            return
        }
        val pending = ArrayDeque<File>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < MAX_VISITED_ENTRIES) {
            currentCoroutineContext().ensureActive()
            val file = pending.removeLast()
            visited++
            val canonicalPath = runCatching { file.canonicalPath }.getOrNull() ?: continue
            if (canonicalPath != rootPath && !canonicalPath.startsWith("$rootPath${File.separator}")) continue
            if (file.isDirectory && shouldSkipDirectory(file, root)) continue
            if (file.isDirectory) stats.scannedDirectories++ else stats.scannedFiles++
            visit(file)
            if (file.isDirectory) {
                val children = runCatching { file.listFiles() }.getOrNull()
                if (children == null) stats.inaccessibleDirectories++ else children.forEach(pending::addLast)
            }
            if (visited % PROGRESS_INTERVAL == 0) onProgress(stats.snapshot())
        }
        if (pending.isNotEmpty()) stats.limitReached = true
    }

    private fun sharedStorageRoots(context: Context): List<File> = buildList {
        add(Environment.getExternalStorageDirectory())
        context.getExternalFilesDirs(null).filterNotNull().forEach { appDirectory ->
            generateSequence(appDirectory) { it.parentFile }
                .firstOrNull { it.name.equals("Android", ignoreCase = true) }
                ?.parentFile
                ?.let(::add)
        }
    }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }

    private fun shouldSkipDirectory(directory: File, root: File): Boolean {
        if (directory == root) return false
        val parentName = directory.parentFile?.name.orEmpty()
        return parentName.equals("Android", ignoreCase = true) &&
            directory.name.lowercase(Locale.ROOT) in setOf("data", "obb")
    }

    private fun isRemovableEmptyDirectory(directory: File, root: File): Boolean {
        if (directory == root || shouldSkipDirectory(directory, root)) return false
        if (directory.name.lowercase(Locale.ROOT) in PROTECTED_DIRECTORY_NAMES) return false
        return runCatching { directory.listFiles()?.isEmpty() == true }.getOrDefault(false)
    }

    suspend fun delete(context: Context, candidates: List<CleanupCandidate>): CleanupDeleteResult = withContext(Dispatchers.IO) {
        var deleted = 0
        var failed = 0
        var released = 0L
        candidates.forEach { candidate ->
            val success = runCatching {
                when {
                    candidate.filePath != null -> {
                        val file = File(candidate.filePath)
                        val fileDeleted = !file.exists() || file.delete()
                        if (fileDeleted && candidate.uri != null) {
                            runCatching { context.contentResolver.delete(candidate.uri, null, null) }
                        }
                        fileDeleted
                    }
                    candidate.uri != null -> context.contentResolver.delete(candidate.uri, null, null) > 0
                    else -> false
                }
            }.getOrDefault(false)
            if (success) {
                deleted++
                released += candidate.sizeBytes
            } else {
                failed++
            }
        }
        CleanupDeleteResult(deleted, failed, released)
    }

    private fun querySharedCandidates(
        context: Context,
        stats: MutableScanStats,
        onProgress: (CleanupScanStats) -> Unit,
    ): List<CleanupCandidate> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA,
        )
        return buildList {
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val pathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (cursor.moveToNext()) {
                    stats.scannedFiles++
                    if (stats.scannedFiles % PROGRESS_INTERVAL == 0) onProgress(stats.snapshot())
                    val name = cursor.getString(nameColumn).orEmpty()
                    val size = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                    val path = pathColumn.takeIf { it >= 0 }?.let(cursor::getString)?.takeIf(String::isNotBlank)
                    val category = CleanupClassifier.classify(name, size, path.orEmpty()) ?: continue
                    add(
                        CleanupCandidate(
                            category = category,
                            sizeBytes = size,
                            uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn)),
                            filePath = path,
                        ),
                    )
                }
            }
        }
    }

    private const val MAX_VISITED_ENTRIES = 300_000
    private const val PROGRESS_INTERVAL = 250
    private val PROTECTED_DIRECTORY_NAMES = setOf(
        "android", "alarms", "audiobooks", "dcim", "documents", "download", "movies",
        "music", "notifications", "pictures", "podcasts", "ringtones",
    )

    private class MutableScanStats {
        var scannedFiles = 0
        var scannedDirectories = 0
        var inaccessibleDirectories = 0
        var limitReached = false

        fun snapshot() = CleanupScanStats(
            scannedFiles = scannedFiles,
            scannedDirectories = scannedDirectories,
            inaccessibleDirectories = inaccessibleDirectories,
            limitReached = limitReached,
        )
    }
}

object CleanupClassifier {
    fun classify(fileName: String, sizeBytes: Long, filePath: String = fileName): CleanupCategory? {
        val normalizedName = fileName.lowercase(Locale.ROOT)
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val directoryNames = File(filePath).parentFile
            ?.let { parent -> generateSequence(parent) { it.parentFile }.map { it.name.lowercase(Locale.ROOT) }.take(6).toSet() }
            .orEmpty()
        return when {
            extension == "apk" -> CleanupCategory.APK_FILES
            extension in TEMPORARY_EXTENSIONS -> CleanupCategory.TEMP_FILES
            normalizedName in TEMPORARY_FILE_NAMES -> CleanupCategory.TEMP_FILES
            directoryNames.any { it in TEMPORARY_DIRECTORY_NAMES } -> CleanupCategory.TEMP_FILES
            sizeBytes == 0L && normalizedName !in PROTECTED_EMPTY_FILES -> CleanupCategory.EMPTY_FILES
            else -> null
        }
    }

    private val TEMPORARY_EXTENSIONS = setOf(
        "tmp", "temp", "log", "cache", "bak", "old", "dmp", "part", "partial", "crdownload",
    )
    private val TEMPORARY_FILE_NAMES = setOf(".ds_store", "thumbs.db", "desktop.ini")
    private val TEMPORARY_DIRECTORY_NAMES = setOf(
        "cache", "caches", "temp", "tmp", "logs", ".thumbnails", ".thumbnail", ".trash", ".trashed",
    )
    private val PROTECTED_EMPTY_FILES = setOf(".nomedia")
}
