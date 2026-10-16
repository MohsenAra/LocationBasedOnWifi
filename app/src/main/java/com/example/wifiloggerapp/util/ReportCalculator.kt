package com.example.wifiloggerapp.util

import com.example.wifiloggerapp.data.WifiEventEntity
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ثابت برای گزارش‌های سالانه
data class WifiSession(
    val ssid: String?,
    val bssid: String?,
    val startTimestamp: Long,
    val endTimestamp: Long
) {
    val durationMillis: Long
        get() = endTimestamp - startTimestamp
}

data class DailySession(
    val date: LocalDate,
    val sessions: List<WifiSession>,
    val totalDurationMillis: Long
)

data class SsidReport(
    val ssid: String?,
    val daily: List<DailySession>,
    val totalDurationMillis: Long
)

class ReportCalculator {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun computeSessions(
        events: List<WifiEventEntity>,
        periodStartMillis: Long,
        periodEndMillis: Long
    ): List<WifiSession> {
        val sorted = events.sortedBy { it.timestamp }
        val openMap = linkedMapOf<String, WifiSession>()
        val result = mutableListOf<WifiSession>()

        fun keyOf(event: WifiEventEntity): String = event.bssid ?: event.ssid ?: "__UNKNOWN__"

        for (event in sorted) {
            val key = keyOf(event)
            val type = event.eventType.uppercase(Locale.getDefault()).trim()

            if (type == "CONNECTED") {
                val existing = openMap[key]
                if (existing != null) {
                    val closed = existing.copy(endTimestamp = maxOf(existing.startTimestamp, event.timestamp))
                    result.add(closed)
                }

                val start = maxOf(event.timestamp, periodStartMillis)
                openMap[key] = WifiSession(
                    ssid = event.ssid,
                    bssid = event.bssid,
                    startTimestamp = start,
                    endTimestamp = start
                )
            } else if (type == "DISCONNECTED") {
                val existing = openMap.remove(key)
                if (existing != null) {
                    val end = minOf(event.timestamp, periodEndMillis)
                    if (end >= existing.startTimestamp) {
                        result.add(existing.copy(endTimestamp = end))
                    } else {
                        result.add(existing.copy(endTimestamp = existing.startTimestamp))
                    }
                }
            }
        }

        for ((_, open) in openMap) {
            val end = periodEndMillis
            if (end >= open.startTimestamp) {
                result.add(open.copy(endTimestamp = end))
            }
        }

        return result.sortedBy { it.startTimestamp }
    }

    fun groupBySsidAndDay(
        sessions: List<WifiSession>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<String?, List<DailySession>> {
        val groupedBySsid = sessions.groupBy { it.ssid }
        val result = linkedMapOf<String?, List<DailySession>>()

        groupedBySsid.forEach { (ssid, list) ->
            val daily = list.groupBy { session ->
                Instant.ofEpochMilli(session.startTimestamp).atZone(zone).toLocalDate()
            }.toSortedMap().map { (date, sessionsForDay) ->
                DailySession(
                    date = date,
                    sessions = sessionsForDay,
                    totalDurationMillis = sessionsForDay.sumOf { it.durationMillis }
                )
            }
            result[ssid] = daily
        }

        return result
    }

    fun buildSsidReports(
        sessions: List<WifiSession>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<SsidReport> {
        return groupBySsidAndDay(sessions, zone).map { (ssid, daily) ->
            SsidReport(
                ssid = ssid,
                daily = daily,
                totalDurationMillis = daily.sumOf { it.totalDurationMillis }
            )
        }
    }

    fun formatDurationHumanReadable(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return "${hours} ساعت و ${minutes} دقیقه"
    }

    fun exportToCsv(reports: List<SsidReport>, zone: ZoneId = ZoneId.systemDefault()): String {
        val writer = StringWriter()
        writer.append("ssid,date,start_time,end_time,duration_minutes\n")

        reports.forEach { report ->
            val ssidLabel = report.ssid ?: "<UNKNOWN>"
            report.daily.forEach { daily ->
                daily.sessions.forEach { session ->
                    val start = Instant.ofEpochMilli(session.startTimestamp).atZone(zone)
                    val end = Instant.ofEpochMilli(session.endTimestamp).atZone(zone)
                    val startStr = start.format(timeFormatter)
                    val endStr = end.format(timeFormatter)
                    val durationMinutes = (session.durationMillis / 1000L) / 60L
                    writer.append("${escapeCsv(ssidLabel)},${daily.date.format(dateFormatter)},$startStr,$endStr,$durationMinutes\n")
                }
            }
            val totalMinutes = (report.totalDurationMillis / 1000L) / 60L
            writer.append("${escapeCsv(ssidLabel)},TOTAL, , ,$totalMinutes\n")
        }

        return writer.toString()
    }

    fun generateAnnualReports(
        year: Int,
        events: List<WifiEventEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<SsidReport> {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        val sessions = computeSessions(events, start, end)
        return buildSsidReports(sessions, zone)
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',')) "\"$value\"" else value
    }
}
