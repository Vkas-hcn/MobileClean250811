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

    // 编译正则表达式模式
    private val compiledPatterns = filterStrArr.map { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }

    // 垃圾文件扩展名
    private val junkExtensions = setOf(
        ".tmp", ".temp", ".log", ".cache", ".bak", ".old", ".~", ".swp",
        ".dmp", ".chk", ".gid", ".dir", ".wbk", ".xlk", ".~tmp",
        ".part", ".crdownload", ".download", ".partial", ".crash",
        ".dumpfile", ".trace", ".err", ".out", ".pid", ".lock"
    )

    // 缓存目录名
    private val cacheDirectories = setOf(
        "cache", "Cache", "CACHE", "tmp", "temp", "Temp", "TEMP",
        ".cache", ".tmp", ".temp", "thumbnail", "thumbnails",
        ".thumbnails", "lost+found", "backup", "Backup", "BACKUP"
    )

    // APK文件大小阈值（小于1MB的APK可能是垃圾）
    private val APK_SIZE_THRESHOLD = 1024 * 1024L // 1MB

    // 标准分类定义
    private val standardCategories = listOf(
        "App Cache",
        "Apk Files",
        "Log Files",
        "Temp Files",
        "Other"
    )

    // 添加文件计数器，用于限制每个分类的文件数量
    private val categoryFileCounts = mutableMapOf<String, Int>()

    suspend fun scanForJunk(callback: ScanCallback) = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始扫描垃圾文件")

        // 初始化分类计数器
        categoryFileCounts.clear()
        standardCategories.forEach { categoryName ->
            categoryFileCounts[categoryName] = 0
        }

        var totalScannedSize = 0L

        // 首先创建一些测试垃圾文件
        createTestJunkFiles(callback)

        // 获取所有可扫描的路径
        val scanPaths = getAllScanPaths()
        Log.d(TAG, "扫描路径数量: ${scanPaths.size}")

        for (path in scanPaths) {
            if (path.exists() && path.canRead()) {
                Log.d(TAG, "扫描路径: ${path.absolutePath}")
                withContext(Dispatchers.Main) {
                    callback.onScanProgress(path.absolutePath, totalScannedSize)
                }

                totalScannedSize = scanDirectory(path, callback, totalScannedSize)
                delay(50) // 减少延迟给UI更新时间
            } else {
                Log.d(TAG, "路径不可访问: ${path.absolutePath}")
            }
        }

        // 扫描完成，创建最终结果
        val finalCategories = standardCategories.map { categoryName ->
            JunkCategory(categoryName).apply {
                // 这里不添加文件，因为文件已经通过回调实时添加了
            }
        }

        Log.d(TAG, "扫描完成，总共发现的文件数:")
        categoryFileCounts.forEach { (category, count) ->
            Log.d(TAG, "$category: $count 个文件")
        }

        withContext(Dispatchers.Main) {
            callback.onScanComplete(finalCategories)
        }
    }

    private fun getAllScanPaths(): List<File> {
        val paths = mutableListOf<File>()

        try {
            // 外部存储根目录
            val externalStorage = Environment.getExternalStorageDirectory()
            if (externalStorage.exists()) {
                paths.add(externalStorage)
            }

            // 应用缓存目录
            context.cacheDir?.let { paths.add(it) }
            context.externalCacheDir?.let { paths.add(it) }

            // 常见目录
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

            // 添加一些系统可能的垃圾文件位置
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

            Log.d(TAG, "扫描目录: ${directory.absolutePath}, 文件数: ${files.size}")

            for (file in files) {
                try {
                    // 更新进度
                    withContext(Dispatchers.Main) {
                        callback.onScanProgress(file.absolutePath, totalSize)
                    }

                    if (file.isDirectory) {
                        // 检查是否匹配垃圾目录规则
                        if (isJunkDirectory(file)) {
                            totalSize = scanJunkDirectory(file, callback, totalSize)
                        } else if (file.canRead() && !isSystemDirectory(file)) {
                            // 递归扫描非系统目录
                            totalSize = scanDirectory(file, callback, totalSize)
                        }
                    } else if (file.isFile) {
                        // 检查文件是否是垃圾文件
                        val category = categorizeFile(file)
                        if (category != null && categoryFileCounts.containsKey(category)) {
                            // 检查分类是否已达到最大文件数限制
                            val currentCount = categoryFileCounts[category] ?: 0
                            if (currentCount < JunkCategory.MAX_FILES_PER_CATEGORY) {
                                val junkFile = JunkFile(
                                    name = file.name,
                                    path = file.absolutePath,
                                    size = file.length(),
                                    file = file
                                )

                                // 实时回调通知找到文件
                                withContext(Dispatchers.Main) {
                                    callback.onFileFound(junkFile, category)
                                }

                                // 更新计数器
                                categoryFileCounts[category] = currentCount + 1
                                totalSize += file.length()

                                Log.d(TAG, "发现垃圾文件: ${file.name} (${junkFile.getSizeInMB()}) -> $category")
                            } else {
                                Log.d(TAG, "分类 $category 已达到最大文件数限制，跳过文件: ${file.name}")
                            }
                        }
                    }

                    // 添加延迟让UI有时间更新
                    if (totalSize % (10 * 1024 * 1024) == 0L) { // 每扫描10MB延迟一次
                        delay(20)
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "处理文件时出错: ${file.absolutePath}", e)
                    continue
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "扫描目录时出错: ${directory.absolutePath}", e)
        }

        return totalSize
    }

    private fun isJunkDirectory(directory: File): Boolean {
        val path = directory.absolutePath

        // 使用正则表达式规则检查
        for (pattern in compiledPatterns) {
            if (pattern.matcher(path).matches()) {
                Log.d(TAG, "目录匹配垃圾规则: $path")
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

                        // 检查分类是否已达到最大文件数限制
                        val currentCount = categoryFileCounts[category] ?: 0
                        if (currentCount < JunkCategory.MAX_FILES_PER_CATEGORY) {
                            val junkFile = JunkFile(
                                name = file.name,
                                path = file.absolutePath,
                                size = file.length(),
                                file = file
                            )

                            // 实时回调通知找到文件
                            withContext(Dispatchers.Main) {
                                callback.onFileFound(junkFile, category)
                            }

                            // 更新计数器
                            categoryFileCounts[category] = currentCount + 1
                            totalSize += file.length()
                            Log.d(TAG, "垃圾目录中的文件: ${file.name} -> $category")
                        }
                    } else if (file.isDirectory) {
                        totalSize = scanJunkDirectory(file, callback, totalSize)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "扫描垃圾目录时出错: ${junkDir.absolutePath}", e)
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

        // 使用正则表达式规则进行分类
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
            // APK 文件
            extension == "apk" -> {
                // 小APK文件或者在下载目录的APK可能是垃圾
                if (file.length() < APK_SIZE_THRESHOLD ||
                    filePath.contains("download") ||
                    filePath.contains("temp")) {
                    "Apk Files"
                } else {
                    null
                }
            }

            // 日志文件
            extension == "log" ||
                    fileName.contains("log") ||
                    fileName.endsWith(".out") ||
                    fileName.endsWith(".err") ||
                    extension in setOf("crash", "trace") -> "Log Files"

            // 临时文件
            junkExtensions.contains(".$extension") ||
                    fileName.startsWith("tmp") ||
                    fileName.startsWith("temp") ||
                    fileName.contains("backup") ||
                    fileName.contains("~") -> "Temp Files"

            // 缓存文件
            filePath.contains("/cache/") ||
                    filePath.contains("/.cache/") ||
                    fileName.contains("cache") -> "App Cache"

            // 缩略图文件
            filePath.contains("thumbnail") ||
                    filePath.contains(".thumbnails") -> "App Cache"

            // 空文件
            file.length() == 0L -> "Other"

            // 重复下载文件
            fileName.contains("(1)") ||
                    fileName.contains("copy") ||
                    fileName.contains("duplicate") -> "Other"

            // 临时下载文件
            extension in setOf("part", "crdownload", "download", "partial") -> "Temp Files"

            // 其他可疑文件
            fileName.startsWith(".") && file.length() < 1024 * 1024 -> "Other" // 小于1MB的隐藏文件

            else -> null
        }
    }

    // 创建测试垃圾文件，并实时通知
    private suspend fun createTestJunkFiles(callback: ScanCallback) {
        try {
            val testDir = File(context.externalCacheDir, "test_junk")
            if (!testDir.exists()) {
                testDir.mkdirs()
            }

            // 创建各种类型的测试垃圾文件
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

                // 检查分类是否已达到最大文件数限制
                val currentCount = categoryFileCounts[category] ?: 0
                if (currentCount < JunkCategory.MAX_FILES_PER_CATEGORY) {
                    if (!file.exists()) {
                        if (content.isEmpty()) {
                            file.createNewFile() // 创建空文件
                        } else {
                            file.writeText(content)
                        }
                        Log.d(TAG, "创建测试文件: ${file.absolutePath}")
                    }

                    // 实时通知测试文件
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

                    delay(100) // 让UI有时间更新
                }
            }

            Log.d(TAG, "测试垃圾文件创建完成")
        } catch (e: Exception) {
            Log.e(TAG, "创建测试文件时出错", e)
        }
    }
}