package com.each.cheat.mobileclean.photo

import java.text.SimpleDateFormat
import java.util.*

/**
 * 照片信息数据类
 */
data class PhotoInfo(
    val id: Long,
    val path: String,
    val name: String,
    val size: Long,
    val dateAdded: Long,
    var isSelected: Boolean = false
) {
    /**
     * 获取格式化的日期字符串
     */
    fun getFormattedDate(): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dateAdded * 1000

        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DAY_OF_YEAR, -1)

        return when {
            isSameDay(calendar, today) -> "Today"
            isSameDay(calendar, yesterday) -> "Yesterday"
            else -> {
                val format = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
                format.format(calendar.time)
            }
        }
    }

    /**
     * 获取日期的唯一标识符（用于分组）
     */
    fun getDateKey(): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dateAdded * 1000
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(calendar.time)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}

/**
 * 按日期分组的照片数据
 */
data class PhotoDateGroup(
    val date: String,
    val photos: MutableList<PhotoInfo>,
    var isAllSelectedImg: Boolean = false
) {
    /**
     * 获取该日期组的总大小
     */
    fun getTotalSize(): Long {
        return photos.sumOf { it.size }
    }

    /**
     * 获取选中照片的总大小
     */
    fun getSelectedSize(): Long {
        return photos.filter { it.isSelected }.sumOf { it.size }
    }

    /**
     * 检查并更新全选状态
     */
    fun updateSelectAllState() {
        isAllSelectedImg = photos.isNotEmpty() && photos.all { it.isSelected }
    }

    /**
     * 设置该组所有照片的选中状态
     */
    fun setAllSelected(selected: Boolean) {
        photos.forEach { it.isSelected = selected }
        isAllSelectedImg = selected
    }
}