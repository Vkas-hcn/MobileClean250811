package com.each.cheat.mobileclean.clean

import java.io.File

data class JunkFile(
    val name: String,
    val path: String,
    val size: Long,
    val file: File,
    var isSelected: Boolean = true
) {
    fun getSizeInMB(): String {
        val sizeInMB = size / (1024.0 * 1024.0)
        return if (sizeInMB < 1) {
            String.format("%.1f KB", size / 1024.0)
        } else {
            String.format("%.1f MB", sizeInMB)
        }
    }
}

data class JunkCategory(
    val name: String,
    val files: MutableList<JunkFile> = mutableListOf(),
    var isExpanded: Boolean = false,
    var isSelected: Boolean = true
) {
    companion object {
        const val MAX_FILES_PER_CATEGORY = 500
    }

    fun getTotalSize(): Long {
        return files.sumOf { it.size }
    }

    fun getTotalSizeInMB(): String {
        val totalSize = getTotalSize()
        val sizeInMB = totalSize / (1024.0 * 1024.0)
        return if (sizeInMB < 1) {
            String.format("%.1f KB", totalSize / 1024.0)
        } else {
            String.format("%.1f MB", sizeInMB)
        }
    }

    fun getSelectedSize(): Long {
        return files.filter { it.isSelected }.sumOf { it.size }
    }

    fun updateSelectionState() {
        val selectedFiles = files.filter { it.isSelected }
        isSelected = selectedFiles.isNotEmpty() && selectedFiles.size == files.size
    }

    fun getFileCountInfo(): String {
        return if (files.size >= MAX_FILES_PER_CATEGORY) {
            "${files.size}+ files" // 显示+号表示可能有更多文件
        } else {
            "${files.size} files"
        }
    }
}