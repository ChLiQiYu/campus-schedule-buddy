package com.fjnu.schedule.util

import com.fjnu.schedule.model.Course
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.random.Random

data class JwImportResult(
    val courses: List<Course>,
    val skippedCount: Int
)

object JwScheduleParser {

    private const val MAX_PERIOD = 20

    private val colorPalette = listOf(
        0xFF6D8AD6.toInt(),
        0xFF9A7BB7.toInt(),
        0xFFD98AA8.toInt(),
        0xFF7DB8B1.toInt(),
        0xFF7FB7D6.toInt()
    )

    fun parse(json: String, semesterId: Long): JwImportResult {
        val root = JSONTokener(json).nextValue()
        val (items, source) = when (root) {
            is JSONObject -> {
                val array = root.optJSONArray("items") ?: JSONArray()
                array to root.optString("source")
            }
            is JSONArray -> root to ""
            else -> JSONArray() to ""
        }
        val courses = mutableListOf<Course>()
        var skipped = 0
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val course = if (source == "kbList" || source == "kbxx" || obj.has("kcmc")) {
                parseFromApi(obj, semesterId)
            } else {
                parseFromDom(obj, semesterId)
            }
            if (course == null) {
                skipped += 1
            } else {
                courses.add(course)
            }
        }
        return JwImportResult(courses, skipped)
    }

    private fun parseFromApi(obj: JSONObject, semesterId: Long): Course? {
        val name = obj.optString("kcmc").trim()
        val teacher = obj.optString("xm").trim().ifBlank { null }
        val location = obj.optString("cdmc").trim()
            .ifBlank { obj.optString("cdmc2").trim() }
            .ifBlank { null }
        val dayOfWeek = obj.optString("xqj").toIntOrNull()
            ?: mapDayOfWeek(obj.optString("xqjmc"))
        val (startPeriod, endPeriod) = parsePeriodRange(obj.optString("jcs"))
        val weekPattern = parseWeekPattern(obj.optString("zcd"))
        if (name.isBlank() || dayOfWeek == null || startPeriod == null || endPeriod == null || weekPattern.isEmpty()) {
            return null
        }
        return buildCourse(
            semesterId = semesterId,
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weekPattern = weekPattern
        )
    }

    private fun parseFromDom(obj: JSONObject, semesterId: Long): Course? {
        val raw = obj.optString("raw").replace('\u00A0', ' ').trim()
        val title = obj.optString("title").replace('\u00A0', ' ').trim()
        val rowHeader = obj.optString("rowHeader").replace('\u00A0', ' ').trim()
        val cellIndex = obj.optInt("cellIndex", -1)
        val rowSpan = obj.optInt("rowSpan", 1)
        val dayText = obj.optString("dayText").replace('\u00A0', ' ').trim()
        val periodText = obj.optString("periodText").replace('\u00A0', ' ').trim()
        val timeText = obj.optString("timeText").replace('\u00A0', ' ').trim()
        val weekText = obj.optString("weekText").replace('\u00A0', ' ').trim()
        val locationText = obj.optString("location").replace('\u00A0', ' ').trim()
        val teacherText = obj.optString("teacher").replace('\u00A0', ' ').trim()

        val combined = listOf(raw, title, rowHeader, dayText, periodText, timeText, weekText)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        val dayOfWeek = mapDayOfWeek(dayText)
            ?: mapDayOfWeek(combined)
            ?: dayFromCellIndex(cellIndex)

        val periodRangeFromText = parsePeriodRangeWithLabel(timeText.ifBlank { combined })
        val periodRangeFromCell = if (periodText.isNotBlank()) parsePeriodRange(periodText) else parsePeriodRange(rowHeader)
        val combinedHasPeriod = combined.contains("节") ||
            Regex("\\d{1,2}\\s*[-~～—–－]\\s*\\d{1,2}").containsMatchIn(combined)
        val baseRange = when {
            periodRangeFromText.first != null -> periodRangeFromText
            periodRangeFromCell.first != null -> periodRangeFromCell
            combinedHasPeriod -> parsePeriodRange(combined)
            else -> Pair(null, null)
        }
        val startPeriod = baseRange.first
        val endPeriod = baseRange.second ?: startPeriod?.let { it + (rowSpan.coerceAtLeast(1) - 1) }

        val weekSource = when {
            weekText.isNotBlank() -> weekText
            timeText.isNotBlank() -> timeText
            else -> combined
        }
        val weekPattern = parseWeekPattern(weekSource)

        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val name = obj.optString("name").trim().takeIf { it.isNotBlank() } ?: guessCourseName(lines)
        val teacherCandidate = teacherText.takeIf { it.isNotBlank() }
            ?: extractByLabels(combined, listOf("教师", "老师"))
            ?: lines.firstOrNull { it.endsWith("老师") || it.endsWith("教师") }
        val teacher = cleanField(teacherCandidate, listOf("教师", "老师"))

        val locationCandidate = locationText.takeIf { it.isNotBlank() }
            ?: extractByLabels(combined, listOf("上课地点", "上课教室", "地点", "教室"))
            ?: lines.firstOrNull { it.contains("楼") || it.contains("教室") || it.contains("实验室") }
        val location = cleanField(locationCandidate, listOf("上课地点", "上课教室", "地点", "教室"))
        if (name.isNullOrBlank() || dayOfWeek == null || startPeriod == null || endPeriod == null || weekPattern.isEmpty()) {
            return null
        }
        return buildCourse(
            semesterId = semesterId,
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weekPattern = weekPattern
        )
    }

    private fun buildCourse(
        semesterId: Long,
        name: String,
        teacher: String?,
        location: String?,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        weekPattern: List<Int>
    ): Course {
        return Course(
            id = 0,
            semesterId = semesterId,
            name = name,
            teacher = teacher?.takeIf { it.isNotBlank() },
            location = location?.takeIf { it.isNotBlank() },
            type = "major_required",
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weekPattern = weekPattern,
            note = null,
            color = colorPalette[Random.nextInt(colorPalette.size)]
        )
    }

    private fun guessCourseName(lines: List<String>): String? {
        return lines.firstOrNull { line ->
            !line.contains("周") &&
                !line.contains("节") &&
                !line.contains("教师") &&
                !line.contains("老师") &&
                !line.contains("教室") &&
                !line.contains("地点")
        } ?: lines.firstOrNull()
    }

    private fun extractByLabels(text: String, labels: List<String>): String? {
        labels.forEach { label ->
            val pattern = Regex("${label}\\s*[：:]?\\s*([^\\n]+)")
            val match = pattern.find(text)
            if (match != null) {
                val value = match.groupValues.getOrNull(1)?.trim()
                if (!value.isNullOrBlank()) {
                    return value
                }
            }
        }
        return null
    }

    private fun mapDayOfWeek(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = Regex("(周|星期)[一二三四五六日天]").find(text) ?: return null
        return when (match.value.last()) {
            '一' -> 1
            '二' -> 2
            '三' -> 3
            '四' -> 4
            '五' -> 5
            '六' -> 6
            '日', '天' -> 7
            else -> null
        }
    }

    private fun dayFromCellIndex(cellIndex: Int): Int? {
        return when (cellIndex) {
            in 2..8 -> cellIndex - 1
            in 1..7 -> cellIndex
            else -> null
        }
    }

    private fun parsePeriodRange(text: String?): Pair<Int?, Int?> {
        if (text.isNullOrBlank()) return Pair(null, null)
        var cleaned = normalizePeriodText(text)
        cleaned = cleaned.replace("节", "").replace("第", "").replace(" ", "")
        if (cleaned.isBlank()) return Pair(null, null)
        val range = Regex("(\\d{1,2})\\s*[-~～—–－]\\s*(\\d{1,2})(?!\\d)").find(cleaned)
        val raw = if (range != null) {
            val start = range.groupValues[1].toIntOrNull()
            val end = range.groupValues[2].toIntOrNull()
            Pair(start, end)
        } else {
            val single = Regex("(\\d{1,2})").find(cleaned)?.groupValues?.get(1)?.toIntOrNull()
            Pair(single, single)
        }
        return sanitizePeriodRange(raw.first, raw.second)
    }

    private fun parsePeriodRangeWithLabel(text: String?): Pair<Int?, Int?> {
        if (text.isNullOrBlank()) return Pair(null, null)
        val normalized = normalizePeriodText(text)
        val range = Regex("(\\d{1,2})\\s*[-~～—–－]\\s*(\\d{1,2})(?!\\d)\\s*节").find(normalized)
        val raw = if (range != null) {
            val start = range.groupValues[1].toIntOrNull()
            val end = range.groupValues[2].toIntOrNull()
            Pair(start, end)
        } else {
            val single = Regex("第?\\s*(\\d{1,2})\\s*节").find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            Pair(single, single)
        }
        return sanitizePeriodRange(raw.first, raw.second)
    }

    private fun normalizePeriodText(text: String): String {
        var result = text
        result = result.replace("（", "(").replace("）", ")")
        result = result.replace(Regex("\\d{1,2}\\s*[-~～—–－]\\s*\\d{1,2}\\s*周"), "")
        result = result.replace(Regex("\\d{1,2}\\s*(单|双)?\\s*周"), "")
        result = result.replace("周次", "").replace("周数", "")
        return result
    }

    private fun sanitizePeriodRange(start: Int?, end: Int?): Pair<Int?, Int?> {
        if (start == null) return Pair(null, null)
        val resolvedEnd = end ?: start
        if (start <= 0 || start > MAX_PERIOD) return Pair(null, null)
        if (resolvedEnd <= 0 || resolvedEnd > MAX_PERIOD) return Pair(null, null)
        return Pair(start, if (resolvedEnd < start) start else resolvedEnd)
    }

    private fun parseWeekPattern(rawText: String?): List<Int> {
        if (rawText.isNullOrBlank()) return emptyList()
        var text = rawText
        text = text.replace("周次", "")
        text = text.replace("周数", "")
        text = text.replace("周", "")
        text = text.replace("：", "").replace(":", "")
        text = text.replace("；", ",").replace("，", ",")
        text = text.replace("单周", "单").replace("双周", "双")
        text = text.replace(Regex("\\([^)]*节\\)"), "")
        text = text.replace("第", "")
        val parts = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val weeks = mutableSetOf<Int>()
        parts.forEach { part ->
            var token = part
            val evenOnly = token.contains("双")
            val oddOnly = token.contains("单")
            token = token.replace("双", "").replace("单", "")
            token = token.replace("(", "").replace(")", "")
            val range = token.split("-")
            if (range.size == 2) {
                val start = range[0].toIntOrNull()
                val end = range[1].toIntOrNull()
                if (start != null && end != null) {
                    for (week in start..end) {
                        if (week <= 0) continue
                        if (evenOnly && week % 2 != 0) continue
                        if (oddOnly && week % 2 == 0) continue
                        weeks.add(week)
                    }
                }
            } else {
                val value = token.toIntOrNull()
                if (value != null && value > 0) {
                    if (evenOnly && value % 2 != 0) return@forEach
                    if (oddOnly && value % 2 == 0) return@forEach
                    weeks.add(value)
                }
            }
        }
        return weeks.toList().sorted()
    }

    private fun cleanField(value: String?, labels: List<String>): String? {
        if (value.isNullOrBlank()) return null
        var text = value
        labels.forEach { label ->
            text = text?.replace(label, "")
        }
        text = text?.replace("校区:", "")
        text = text?.replace("校区：", "")
        text = text?.replace("：", "")
        text = text?.replace(":", "")
        text = text?.replace(Regex("\\s+"), " ")
        return text?.trim()?.ifBlank { null }
    }

}
