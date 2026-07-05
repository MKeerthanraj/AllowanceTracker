package com.kaysyndikayte.allowancetracker.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateUtils {
    private val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

    fun format(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(formatter)

    fun formatRange(startEpochDay: Long, endEpochDay: Long): String =
        "${format(startEpochDay)} - ${format(endEpochDay)}"

    fun totalDays(startEpochDay: Long, endEpochDay: Long): Long =
        (endEpochDay - startEpochDay) + 1

    /** Days elapsed so far, clamped between 0 and totalDays */
    fun daysElapsed(startEpochDay: Long, endEpochDay: Long): Long {
        val today = LocalDate.now().toEpochDay()
        return when {
            today < startEpochDay -> 0
            today > endEpochDay -> totalDays(startEpochDay, endEpochDay)
            else -> (today - startEpochDay) + 1
        }
    }

    fun isLive(startEpochDay: Long, endEpochDay: Long): Boolean {
        val today = LocalDate.now().toEpochDay()
        return today in startEpochDay..endEpochDay
    }
}