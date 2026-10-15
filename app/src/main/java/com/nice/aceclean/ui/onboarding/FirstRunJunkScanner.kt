package com.nice.aceclean.ui.onboarding

import com.nice.aceclean.R
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

internal enum class FirstRunJunkCategory { SYSTEM_CACHE, THUMBNAILS, EMPTY_FOLDERS, DOWNLOADS, APKS }

internal enum class FirstRunDeleteMode { FILE, EMPTY_DIRECTORY, KEEP_DIRECTORY }

internal data class FirstRunJunkTarget(
    val path: String,
    val bytes: Long,
    val mode: FirstRunDeleteMode,
)

internal data class FirstRunJunkItem(
    val name: String,
    val targets: List<FirstRunJunkTarget>,
    var selected: Boolean,
) {
    val bytes: Long get() = targets.sumOf { it.bytes }
}

internal data class FirstRunJunkGroup(
    val category: FirstRunJunkCategory,
    val items: MutableList<FirstRunJunkItem>,
    var expanded: Boolean = false,
) {
    val bytes: Long get() = items.sumOf { it.bytes }
    val selectedBytes: Long get() = items.filter { it.selected }.sumOf { it.bytes }
    val allSelected: Boolean get() = items.isNotEmpty() && items.all { it.selected }
}

internal data class FirstRunJunkResult(val groups: MutableList<FirstRunJunkGroup>) {
    val scannedItems: Int get() = groups.sumOf { group -> group.items.sumOf { it.targets.size } }
    val selectedBytes: Long get() = groups.sumOf { it.selectedBytes }
}


internal object FirstRunJunkScanner {
    private const val MAX_VISITED_ENTRIES = 80_000
    private val adTokens = listOf("ad_", "_ad", "ads_", "_ads", "advert", "promo", "banner", "google_ads", "mopub", "ironsource", "unityads")

    suspend fun scan(context: Context, onProgress: (Int) -> Unit): FirstRunJunkResult = withContext(Dispatchers.IO) {
        val appCache = linkedMapOf<String, FirstRunJunkTarget>()
        val adJunk = linkedMapOf<String, FirstRunJunkTarget>()
        val memoryCache = linkedMapOf<String, FirstRunJunkTarget>()
        val thumbnails = linkedMapOf<String, MutableList<FirstRunJunkTarget>>()
        val emptyFolders = linkedMapOf<String, FirstRunJunkTarget>()
        val downloads = linkedMapOf<String, FirstRunJunkTarget>()
        val apks = linkedMapOf<String, FirstRunJunkTarget>()
        var visited = 0

        fun report() {
            if (visited == 1 || visited % 40 == 0) onProgress(visited)
        }

        fun addCacheRoot(directory: File?) {
            if (directory == null || !directory.exists() || !directory.isDirectory) return
            val key = directory.safeCanonicalPath() ?: return
            appCache.putIfAbsent(
                key,
                FirstRunJunkTarget(key, directory.treeSize(MAX_VISITED_ENTRIES / 4), FirstRunDeleteMode.KEEP_DIRECTORY),
            )
        }

        addCacheRoot(context.cacheDir)
        context.externalCacheDirs.forEach(::addCacheRoot)

        val canReadSharedStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        val storageRoot = Environment.getExternalStorageDirectory()
        if (canReadSharedStorage && storageRoot.exists()) {
            val downloadRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).safeCanonicalPath()
            val queue = ArrayDeque<File>()
            queue.add(storageRoot)
            val seen = hashSetOf<String>()
            while (queue.isNotEmpty() && visited < MAX_VISITED_ENTRIES) {
                currentCoroutineContext().ensureActive()
                val current = queue.removeFirst()
                val currentPath = current.safeCanonicalPath() ?: continue
                val rootPath = storageRoot.safeCanonicalPath() ?: continue
                if (currentPath != rootPath && !currentPath.startsWith(rootPath + File.separator)) continue
                if (!seen.add(currentPath)) continue
                val children = runCatching { current.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
                for (child in children) {
                    if (visited++ >= MAX_VISITED_ENTRIES) break
                    report()
                    val path = child.safeCanonicalPath() ?: continue
                    if (child.isDirectory) {
                        if (child.name.equals("cache", ignoreCase = true) && path.contains("${File.separator}Android${File.separator}data${File.separator}", ignoreCase = true)) {
                            addCacheRoot(child)
                            continue
                        }
                        if (child.name.equals(".thumbnails", ignoreCase = true)) {
                            val label = child.parentFile?.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.string_onboarding_first_junk_thumbnails)
                            val bucket = thumbnails.getOrPut(label) { mutableListOf() }
                            child.collectFiles(MAX_VISITED_ENTRIES / 4).forEach { file ->
                                val filePath = file.safeCanonicalPath() ?: return@forEach
                                bucket += FirstRunJunkTarget(filePath, file.length().coerceAtLeast(0L), FirstRunDeleteMode.FILE)
                            }
                            continue
                        }
                        if (childrenSafe(child).isEmpty() && child != storageRoot) {
                            emptyFolders.putIfAbsent(path, FirstRunJunkTarget(path, 0L, FirstRunDeleteMode.EMPTY_DIRECTORY))
                        } else {
                            queue.add(child)
                        }
                        continue
                    }

                    val length = child.length().coerceAtLeast(0L)
                    val lowerName = child.name.lowercase(Locale.ROOT)
                    val target = FirstRunJunkTarget(path, length, FirstRunDeleteMode.FILE)
                    val isDirectDownload = downloadRoot != null && child.parentFile?.safeCanonicalPath() == downloadRoot
                    when {
                        lowerName.endsWith(".apk") -> apks.putIfAbsent(path, target)
                        isDirectDownload -> downloads.putIfAbsent(path, target)
                        lowerName.endsWith(".log") || lowerName.endsWith(".xlog") || lowerName.endsWith(".tmp") || lowerName.endsWith(".temp") -> memoryCache.putIfAbsent(path, target)
                        adTokens.any(lowerName::contains) -> adJunk.putIfAbsent(path, target)
                    }
                }
            }
        }

        val systemItems = mutableListOf<FirstRunJunkItem>()
        if (appCache.isNotEmpty()) systemItems += FirstRunJunkItem(context.getString(R.string.string_onboarding_app_cache), appCache.values.toList(), true)
        if (adJunk.isNotEmpty()) systemItems += FirstRunJunkItem(context.getString(R.string.string_onboarding_ad_junk), adJunk.values.toList(), true)
        if (memoryCache.isNotEmpty()) systemItems += FirstRunJunkItem(context.getString(R.string.string_onboarding_memory_cache), memoryCache.values.toList(), true)

        fun item(name: String, target: FirstRunJunkTarget, selected: Boolean) = FirstRunJunkItem(name, listOf(target), selected)

        FirstRunJunkResult(
            mutableListOf(
                FirstRunJunkGroup(FirstRunJunkCategory.SYSTEM_CACHE, systemItems),
                FirstRunJunkGroup(FirstRunJunkCategory.THUMBNAILS, thumbnails.map { (name, targets) -> FirstRunJunkItem(name, targets, true) }.toMutableList()),
                FirstRunJunkGroup(FirstRunJunkCategory.EMPTY_FOLDERS, emptyFolders.values.map { item(File(it.path).name.ifBlank { context.getString(R.string.string_onboarding_first_junk_empty_folders) }, it, true) }.toMutableList()),
                FirstRunJunkGroup(FirstRunJunkCategory.DOWNLOADS, downloads.values.map { item(File(it.path).name, it, false) }.toMutableList()),
                FirstRunJunkGroup(FirstRunJunkCategory.APKS, apks.values.map { item(File(it.path).name, it, false) }.toMutableList()),
            ),
        ).also {
            onProgress(visited)
        }
    }

    suspend fun cleanSelected(context: Context, result: FirstRunJunkResult, onProgress: (Long) -> Unit): Long = withContext(Dispatchers.IO) {
        val selected = result.groups.flatMap { it.items }.filter { it.selected }.flatMap { it.targets }
        val expectedTotal = selected.sumOf { it.bytes }.coerceAtLeast(1L)
        var processed = 0L
        var cleaned = 0L
        selected.distinctBy { it.path }.forEach { target ->
            currentCoroutineContext().ensureActive()
            cleaned += deleteSafely(context, target)
            processed += target.bytes
            onProgress((processed * 100L / expectedTotal).coerceIn(0L, 100L))
        }
        cleaned
    }

    private fun deleteSafely(context: Context, target: FirstRunJunkTarget): Long {
        val file = File(target.path)
        val canonical = file.safeCanonicalPath() ?: return 0L
        val sharedRoot = Environment.getExternalStorageDirectory().safeCanonicalPath()
        val cacheRoots = (listOf(context.cacheDir) + context.externalCacheDirs.filterNotNull()).mapNotNull { it.safeCanonicalPath() }
        val insideShared = sharedRoot != null && canonical != sharedRoot && canonical.startsWith("$sharedRoot${File.separator}")
        val insideAppCache = cacheRoots.any { canonical == it || canonical.startsWith("$it${File.separator}") }
        if (!insideShared && !insideAppCache) return 0L
        return runCatching {
            when (target.mode) {
                FirstRunDeleteMode.FILE -> {
                    val bytes = file.length().coerceAtLeast(0L)
                    if (file.isFile && file.delete()) bytes else 0L
                }
                FirstRunDeleteMode.EMPTY_DIRECTORY -> {
                    if (file.isDirectory && childrenSafe(file).isEmpty()) file.delete()
                    0L
                }
                FirstRunDeleteMode.KEEP_DIRECTORY -> {
                    val validCacheRoot = insideAppCache || (file.name.equals("cache", true) && canonical.contains("${File.separator}Android${File.separator}data${File.separator}", true))
                    if (validCacheRoot) childrenSafe(file).sumOf { deleteTreeInside(it, canonical) } else 0L
                }
            }
        }.getOrDefault(0L)
    }

    private fun deleteTreeInside(file: File, root: String, seen: MutableSet<String> = hashSetOf()): Long {
        val canonical = file.safeCanonicalPath() ?: return 0L
        if (!canonical.startsWith("$root${File.separator}") || !seen.add(canonical)) return 0L
        if (file.isDirectory) {
            val bytes = childrenSafe(file).sumOf { deleteTreeInside(it, root, seen) }
            file.delete()
            return bytes
        }
        val bytes = file.length().coerceAtLeast(0L)
        return if (file.delete()) bytes else 0L
    }

    private fun File.collectFiles(limit: Int): List<File> {
        val result = ArrayList<File>()
        val queue = ArrayDeque<File>()
        queue.add(this)
        val seen = hashSetOf<String>()
        val root = safeCanonicalPath() ?: return result
        var visited = 0
        while (queue.isNotEmpty() && visited++ < limit) {
            val directory = queue.removeFirst()
            val canonical = directory.safeCanonicalPath() ?: continue
            if (canonical != root && !canonical.startsWith(root + File.separator)) continue
            if (!seen.add(canonical)) continue
            childrenSafe(directory).forEach {
                val path = it.safeCanonicalPath()
                if (path != null && path.startsWith(root + File.separator)) {
                    if (it.isDirectory) queue.add(it) else result += it
                }
            }
        }
        return result
    }

    private fun File.treeSize(limit: Int): Long = collectFiles(limit).sumOf { it.length().coerceAtLeast(0L) }

    private fun childrenSafe(file: File): List<File> = runCatching { file.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())

    private fun File.safeCanonicalPath(): String? = runCatching { canonicalPath }.getOrNull()
}
