package com.each.cheat.mobileclean.photo

import java.text.SimpleDateFormat
import java.util.*


data class PhotoInfo(
    val id: Long,
    val path: String,
    val name: String,
    val size: Long,
    val dateAdded: Long,
    var isSelected: Boolean = false
) {

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

data class PhotoDateGroup(
    val date: String,
    val photos: MutableList<PhotoInfo>,
    var isAllSelectedImg: Boolean = false
) {

    fun getTotalSize(): Long {
        return photos.sumOf { it.size }
    }


    fun getSelectedSize(): Long {
        return photos.filter { it.isSelected }.sumOf { it.size }
    }


    fun updateSelectAllState() {
        isAllSelectedImg = photos.isNotEmpty() && photos.all { it.isSelected }
    }


    fun setAllSelected(selected: Boolean) {
        photos.forEach { it.isSelected = selected }
        isAllSelectedImg = selected
    }
}