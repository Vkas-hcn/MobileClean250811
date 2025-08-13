package com.each.cheat.mobileclean.clean

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

class JunkScanner(private val context: Context) {

    interface ScanCallback {
        fun onScanProgress(currentPath: String, scannedSize: Long)
        fun onFileFound(junkFile: JunkFile, categoryName: String) // 新增实时文件发现回调
        fun onScanComplete(categories: List<JunkCategory>)
    }

    private val TAG = "JunkScanner"

    private val filterStrArr = arrayOf(
        ".*(/|\\\\)crashlytics(/|\\\\|\$).*",
        ".*(/|\\\\)firebase(/|\\\\|\$).*",
        ".*(/|\\\\)bugly(/|\\\\|\$).*",
        ".*(/|\\\\)umeng(/|\\\\|\$).*",
        ".*(/|\\\\)backup(/|\\\\|\$).*",
        ".*(/|\\\\)downloads?(/|\\\\|\$).*\\.part\$",
        ".*(/|\\\\)downloads?(/|\\\\|\$).*\\.crdownload\$",
        ".*(/|\\\\)downloads?(/|\\\\|\$).*\\.tmp\$",
        ".*(/|\\\\)webview(/|\\\\|\$).*",
        ".*(/|\\\\)webviewcache(/|\\\\|\$).*",
        ".*(/|\\\\)okhttp(/|\\\\|\$).*",
        ".*(/|\\\\)fresco(/|\\\\|\$).*",
        ".*(/|\\\\)glide(/|\\\\|\$).*",
        ".*(/|\\\\)picasso(/|\\\\|\$).*",
        ".*(/|\\\\)imageloader(/|\\\\|\$).*",
        ".*(/|\\\\)adcache(/|\\\\|\$).*",
        ".*(/|\\\\)adview(/|\\\\|\$).*",
        ".*(/|\\\\)facebook(/|\\\\|\$).*\\.tmp\$",
        ".*(/|\\\\)instagram(/|\\\\|\$).*\\.cache\$",
        ".*(/|\\\\)twitter(/|\\\\|\$).*\\.log\$",
        ".*(/|\\\\)tiktok(/|\\\\|\$).*\\.temp\$",
        ".*(/|\\\\)youtube(/|\\\\|\$).*\\.cache\$",
        ".*(/|\\\\)whatsapp(/|\\\\|\$).*\\.bak\$",
        ".*(/|\\\\)wechat(/|\\\\|\$).*\\.tmp\$",
        ".*(/|\\\\)qq(/|\\\\|\$).*\\.log\$",
        ".*(/|\\\\)sina(/|\\\\|\$).*\\.cache\$",
        ".*(/|\\\\)baidu(/|\\\\|\$).*\\.temp\$",
        ".*(/|\\\\)360(/|\\\\|\$).*\\.bak\$",
        ".*(/|\\\\)tencent(/|\\\\|\$).*\\.tmp\$",
        ".*(/|\\\\)alibaba(/|\\\\|\$).*\\.log\$",
        ".*(/|\\\\)xiaomi(/|\\\\|\$).*\\.cache\$"
    )

    private val compiledPatterns = filterStrArr.map { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }

    private val junkExtensions = setOf(
        ".tmp", ".temp", ".log", ".cache", ".bak", ".old", ".~", ".swp",
        ".dmp", ".chk", ".gid", ".dir", ".wbk", ".xlk", ".~tmp",
        ".part", ".crdownload", ".download", ".partial", ".crash",
        ".dumpfile", ".trace", ".err", ".out", ".pid", ".lock"
    )

    private val cacheDirectories = setOf(
        "cache", "Cache", "CACHE", "tmp", "temp", "Temp", "TEMP",
        ".cache", ".tmp", ".temp", "thumbnail", "thumbnails",
        ".thumbnails", "lost+found", "backup", "Backup", "BACKUP"
    )

    private val APK_SIZE_THRESHOLD = 1024 * 1024L // 1MB

    private val standardCategories = listOf(
        "App Cache",
        "Apk Files",
        "Log Files",
        "Temp Files",
        "Other"
    )

    private val categoryFileCounts = mutableMapOf<String, Int>()

    suspend fun scanForJunk(callback: ScanCallback) = withContext(Dispatchers.IO) {

        categoryFileCounts.clear()
        standardCategories.forEach { categoryName ->
            categoryFileCounts[categoryName] = 0
        }

        var totalScannedSize = 0L

        createTestJunkFiles(callback)

        val scanPaths = getAllScanPaths()

        for (path in scanPaths) {
            if (path.exists() && path.canRead()) {
                withContext(Dispatchers.Main) {
                    callback.onScanProgress(path.absolutePath, totalScannedSize)
                }

                totalScannedSize = scanDirectory(path, callback, totalScannedSize)
                delay(50)
            } else {
                Log.d(TAG, "路径不可访问: ${path.absolutePath}")
            }
        }

        val finalCategories = standardCategories.map { categoryName ->
            JunkCategory(categoryName).apply {
            }
        }



        withContext(Dispatchers.Main) {
            callback.onScanComplete(finalCategories)
        }
    }

    private fun getAllScanPaths(): List<File> {
        val paths = mutableListOf<File>()

        try {
            val externalStorage = Environment.getExternalStorageDirectory()
            if (externalStorage.exists()) {
                paths.add(externalStorage)
            }

            context.cacheDir?.let { paths.add(it) }
            context.externalCacheDir?.let { paths.add(it) }

            val commonDirs = listOf(
                "Download", "Downloads", "DCIM/.thumbnails", "Pictures/.thumbnails",
                "Android/data", "Android/obb", "tencent", "Tencent",
                "sina", "baidu", "360", "UCDownloads", "QQBrowser",
                "temp", "Temp", "cache", "Cache", "log", "Log",
                "backup", "Backup", "crashlytics", "firebase"
            )

            commonDirs.forEach { dirName ->
                val dir = File(externalStorage, dirName)
                if (dir.exists()) {
                    paths.add(dir)
                }
            }

            val systemDirs = listOf(
                File("/sdcard/"),
                File("/storage/emulated/0/"),
                File(context.filesDir.parent ?: "")
            )

            systemDirs.forEach { dir ->
                if (dir.exists() && dir.canRead()) {
                    paths.add(dir)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "获取扫描路径时出错", e)
        }

        return paths.distinct()
    }

    private suspend fun scanDirectory(
        directory: File,
        callback: ScanCallback,
        currentTotalSize: Long
    ): Long {
        var totalSize = currentTotalSize

        try {
            val files = directory.listFiles()
            if (files.isNullOrEmpty()) {
                return totalSize
            }


            for (file in files) {
                try {
                    withContext(Dispatchers.Main) {
                        callback.onScanProgress(file.absolutePath, totalSize)
                    }

                    if (file.isDirectory) {
                        if (isJunkDirectory(file)) {
                            totalSize = scanJunkDirectory(file, callback, totalSize)
                        } else if (file.canRead() && !isSystemDirectory(file)) {
                            totalSize = scanDirectory(file, callback, totalSize)
                        }
                    } else if (file.isFile) {
                        val category = categorizeFile(file)
                        if (category != null && categoryFileCounts.containsKey(category)) {
                            val currentCount = categoryFileCounts[category] ?: 0
                            if (currentCount < JunkCategory.MAX_FILES_PER_CATEGORY) {
                                val junkFile = JunkFile(
                                    name = file.name,
                                    path = file.absolutePath,
                                    size = file.length(),
                                    file = file
                                )

                                withContext(Dispatchers.Main) {
                                    callback.onFileFound(junkFile, category)
                                }

                                categoryFileCounts[category] = currentCount + 1
                                totalSize += file.length()

                            }
                        }
                    }

                    if (totalSize % (10 * 1024 * 1024) == 0L) {
                        delay(20)
                    }

                } catch (e: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
        }

        return totalSize
    }

    private fun isJunkDirectory(directory: File): Boolean {
        val path = directory.absolutePath

        // 使用正则表达式规则检查
        for (pattern in compiledPatterns) {
            if (pattern.matcher(path).matches()) {
                return true
            }
        }

        // 传统的缓存目录检查
        val dirName = directory.name.lowercase()
        return cacheDirectories.any {
            dirName.contains(it.lowercase()) ||
                    dirName.equals(it, ignoreCase = true)
        } || path.contains("/cache/", ignoreCase = true) ||
                path.contains("/.cache/", ignoreCase = true)
    }

    private suspend fun scanJunkDirectory(
        junkDir: File,
        callback: ScanCallback,
        currentTotalSize: Long
    ): Long {
        var totalSize = currentTotalSize

        try {
            val files = junkDir.listFiles() ?: return totalSize

            for (file in files) {
                try {
                    if (file.isFile && file.length() > 0) {
                        val category = categorizeFile(file) ?: "App Cache"

                        val currentCount = categoryFileCounts[category] ?: 0
                        if (currentCount < JunkCategory.MAX_FILES_PER_CATEGORY) {
                            val junkFile = JunkFile(
                                name = file.name,
                                path = file.absolutePath,
                                size = file.length(),
                                file = file
                            )

                            withContext(Dispatchers.Main) {
                                callback.onFileFound(junkFile, category)
                            }

                            // 更新计数器
                            categoryFileCounts[category] = currentCount + 1
                            totalSize += file.length()
                        }
                    } else if (file.isDirectory) {
                        totalSize = scanJunkDirectory(file, callback, totalSize)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
        }

        return totalSize
    }

    private fun isSystemDirectory(directory: File): Boolean {
        val systemDirs = setOf("system", "proc", "dev", "sys", "root")
        val dirName = directory.name.lowercase()
        return systemDirs.contains(dirName) ||
                directory.absolutePath.startsWith("/system") ||
                directory.absolutePath.startsWith("/proc") ||
                directory.absolutePath.startsWith("/dev")
    }

    private fun categorizeFile(file: File): String? {
        val fileName = file.name.lowercase()
        val extension = file.extension.lowercase()
        val filePath = file.absolutePath.lowercase()

        for (pattern in compiledPatterns) {
            if (pattern.matcher(filePath).matches()) {
                return when {
                    filePath.contains("log") || extension == "log" -> "Log Files"
                    filePath.contains("cache") -> "App Cache"
                    filePath.contains("temp") || filePath.contains("tmp") -> "Temp Files"
                    extension == "apk" -> "Apk Files"
                    else -> "Other"
                }
            }
        }

        return when {
            extension == "apk" -> {
                if (file.length() < APK_SIZE_THRESHOLD ||
                    filePath.contains("download") ||
                    filePath.contains("temp")) {
                    "Apk Files"
                } else {
                    null
                }
            }

            extension == "log" ||
                    fileName.contains("log") ||
                    fileName.endsWith(".out") ||
                    fileName.endsWith(".err") ||
                    extension in setOf("crash", "trace") -> "Log Files"

            junkExtensions.contains(".$extension") ||
                    fileName.startsWith("tmp") ||
                    fileName.startsWith("temp") ||
                    fileName.contains("backup") ||
                    fileName.contains("~") -> "Temp Files"

            filePath.contains("/cache/") ||
                    filePath.contains("/.cache/") ||
                    fileName.contains("cache") -> "App Cache"

            filePath.contains("thumbnail") ||
                    filePath.contains(".thumbnails") -> "App Cache"

            file.length() == 0L -> "Other"

            fileName.contains("(1)") ||
                    fileName.contains("copy") ||
                    fileName.contains("duplicate") -> "Other"

            extension in setOf("part", "crdownload", "download", "partial") -> "Temp Files"

            fileName.startsWith(".") && file.length() < 1024 * 1024 -> "Other" // 小于1MB的隐藏文件

            else -> null
        }
    }

    private suspend fun createTestJunkFiles(callback: ScanCallback) {
        try {
            val testDir = File(context.externalCacheDir, "test_junk")
            if (!testDir.exists()) {
                testDir.mkdirs()
            }

            val testFiles = listOf(
                "cache_file.cache" to ("App Cache" to "App Cache test content"),
                "temp_file.tmp" to ("Temp Files" to "Temporary file content for testing"),
                "crash_report.log" to ("Log Files" to "Crash log content with debug information"),
                "backup.bak" to ("Temp Files" to "Backup file content"),
                "old_file.old" to ("Temp Files" to "Old file content"),
                "test.apk" to ("Apk Files" to "APK"), // 小APK文件
                "empty_file.txt" to ("Other" to ""), // 空文件
                "temp_download.part" to ("Temp Files" to "Partial download content"),
                "analytics.log" to ("Log Files" to "Analytics log content"),
                "webview_cache.db" to ("App Cache" to "WebView cache database content")
            )

            testFiles.forEach { (filename, categoryAndContent) ->
                val (category, content) = categoryAndContent
                val file = File(testDir, filename)

                val currentCount = categoryFileCounts[category] ?: 0
                if (currentCount < JunkCategory.MAX_FILES_PER_CATEGORY) {
                    if (!file.exists()) {
                        if (content.isEmpty()) {
                            file.createNewFile() // 创建空文件
                        } else {
                            file.writeText(content)
                        }
                    }

                    val junkFile = JunkFile(
                        name = file.name,
                        path = file.absolutePath,
                        size = file.length(),
                        file = file
                    )

                    withContext(Dispatchers.Main) {
                        callback.onFileFound(junkFile, category)
                    }

                    categoryFileCounts[category] = currentCount + 1

                    delay(100)
                }
            }

        } catch (e: Exception) {
        }
    }
}