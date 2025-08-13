package com.each.cheat.mobileclean

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow
object StorageUtils {

    fun formatBytes2(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()

        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        val decimalFormat = DecimalFormat("#,##0.#")

        return "${decimalFormat.format(value)} ${units[digitGroups]}"
    }


    fun bytesToMB(bytes: Long): Double {
        return bytes / (1024.0 * 1024.0)
    }


    fun bytesToGB(bytes: Long): Double {
        return bytes / (1024.0 * 1024.0 * 1024.0)
    }
    fun getStorageInfo(context: Context): StorageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getStorageInfoNew(context)
        } else {
            getStorageInfoLegacy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getStorageInfoNew(context: Context): StorageInfo {
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        var totalBytes = 0L
        var usedBytes = 0L

        try {
            val storageVolumes = storageManager.storageVolumes
            for (storageVolume in storageVolumes) {
                if (storageVolume.isPrimary) {
                    val uuid = StorageManager.UUID_DEFAULT
                    totalBytes = storageStatsManager.getTotalBytes(uuid)
                    val freeBytes = storageStatsManager.getFreeBytes(uuid)
                    usedBytes = totalBytes - freeBytes
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return getStorageInfoLegacy()
        }

        return StorageInfo(totalBytes, usedBytes)
    }

    private fun getStorageInfoLegacy(): StorageInfo {
        val path = Environment.getExternalStorageDirectory()
        val stat = StatFs(path.path)

        val totalBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.blockCountLong * stat.blockSizeLong
        } else {
            stat.blockCount.toLong() * stat.blockSize.toLong()
        }

        val availableBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.availableBlocksLong * stat.blockSizeLong
        } else {
            stat.availableBlocks.toLong() * stat.blockSize.toLong()
        }

        val usedBytes = totalBytes - availableBytes

        return StorageInfo(totalBytes, usedBytes)
    }

    fun formatBytes(bytes: Long): String {
        val kb = 1000.0
        val mb = kb * 1000
        val gb = mb * 1000

        return when {
            bytes >= gb -> String.format("%.1f GB", bytes / gb)
            bytes >= mb -> String.format("%.1f MB", bytes / mb)
            bytes >= kb -> String.format("%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long
) {
    val usagePercentage: Int
        get() = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0

    val formattedUsed: String
        get() = StorageUtils.formatBytes(usedBytes)

    val formattedTotal: String
        get() = StorageUtils.formatBytes(totalBytes)

    val formattedInfo: String
        get() = "$formattedUsed Used / $formattedTotal Total"
}