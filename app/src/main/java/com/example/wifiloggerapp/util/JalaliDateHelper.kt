package com.example.wifiloggerapp.util

import java.util.Calendar
import java.util.Locale

object JalaliDateHelper {

    data class JalaliDate(val year: Int, val month: Int, val day: Int) {
        override fun toString(): String = String.format(Locale.US, "%04d/%02d/%02d", year, month, day)
    }

    /**
     * Converts Gregorian date to Jalali (Solar Hijri / Shamsi) date.
     */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) (gy + 1) else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += ((days - 1) / 365)
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return JalaliDate(jy, jm, jd)
    }

    /**
     * Formats a unix timestamp (in milliseconds) into Shamsi date and time:
     * e.g. "1405/08/22 15:30:45"
     */
    fun formatShamsiDateTime(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        val jalali = gregorianToJalali(gy, gm, gd)
        return String.format(
            Locale.US,
            "%04d/%02d/%02d %02d:%02d:%02d",
            jalali.year,
            jalali.month,
            jalali.day,
            hour,
            minute,
            second
        )
    }
}
