package com.example.campus_schedule_buddy.util

import android.content.ContentResolver
import android.net.Uri
import com.example.campus_schedule_buddy.model.Course
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import java.io.InputStream
import kotlin.random.Random

data class CourseImportResult(
    val courses: List<Course>,
    val skippedCount: Int,
    val semesterInfo: String?,
    val classInfo: String?,
    val majorInfo: String?
)

object CourseExcelImporter {
    private val formatter = DataFormatter()
    private val colorPalette = listOf(
        0xFF6D8AD6.toInt(),
        0xFF9A7BB7.toInt(),
        0xFFD98AA8.toInt(),
        0xFF7DB8B1.toInt(),
        0xFF7FB7D6.toInt()
    )

    fun importFromUri(contentResolver: ContentResolver, uri: Uri, semesterId: Long): CourseImportResult {
        contentResolver.openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream) { "无法打开文件" }
            return parseWorkbook(inputStream, semesterId)
        }
    }

    private fun parseWorkbook(inputStream: InputStream, semesterId: Long): CourseImportResult {
        HSSFWorkbook(inputStream).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val headerRow = sheet.getRow(0)
            val firstCell = getCellString(headerRow?.getCell(0))
            val parts = firstCell.split(Regex("\\s{2,}")).filter { it.isNotBlank() }
            val rowValues = if (headerRow != null && headerRow.lastCellNum.toInt() > 0) {
                (0 until headerRow.lastCellNum.toInt())
                    .map { getCellString(headerRow.getCell(it)) }
                    .filter { it.isNotBlank() }
            } else {
                emptyList()
            }
            val semesterInfo = parts.getOrNull(0) ?: rowValues.getOrNull(0)
            val classInfo = parts.getOrNull(1) ?: rowValues.getOrNull(1)
            val majorInfo = parts.getOrNull(2) ?: rowValues.getOrNull(2)

            val headerIndex = findHeaderRow(sheet)
            val startIndex = if (headerIndex >= 0) headerIndex + 1 else 1

            val courses = mutableListOf<Course>()
            var skipped = 0
            var currentDay: String? = null

            for (rowIndex in startIndex..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                val dayCell = getCellString(row.getCell(0))
                if (dayCell.isNotBlank()) {
                    currentDay = dayCell.trim()
                }
                val periodCell = getCellString(row.getCell(1))
                val infoCell = getCellString(row.getCell(2))
                if (periodCell.isBlank() || infoCell.isBlank()) {
                    continue
                }

                val dayOfWeek = mapDayOfWeek(currentDay)
                val (startPeriod, endPeriod) = parsePeriodRange(periodCell)
                val courseName = infoCell.substringBefore("上课时间:").trim()
                val weeksText = extractBetween(infoCell, "上课时间:", "地点:")
                val location = extractBetween(infoCell, "地点:", "教师:")
                val teacher = extractBetween(infoCell, "教师:", "教学班组成:")
                val weekPattern = parseWeekPattern(weeksText)

                if (courseName.isBlank() || dayOfWeek == null || startPeriod == null || endPeriod == null || weekPattern.isEmpty()) {
                    skipped += 1
                    continue
                }

                courses.add(
                    Course(
                        id = 0,
                        semesterId = semesterId,
                        name = courseName,
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
                )
            }
            return CourseImportResult(courses, skipped, semesterInfo, classInfo, majorInfo)
        }
    }

    private fun findHeaderRow(sheet: org.apache.poi.ss.usermodel.Sheet): Int {
        for (rowIndex in 0..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val lastCell = row.lastCellNum.toInt()
            if (lastCell <= 0) continue
            val cells = (0 until lastCell).map { getCellString(row.getCell(it)) }
            val hasWeekday = cells.any { it.contains("星期") }
            val hasPeriod = cells.any { it.contains("节次") }
            if (hasWeekday && hasPeriod) {
                return rowIndex
            }
        }
        return -1
    }

    private fun getCellString(cell: Cell?): String {
        if (cell == null) return ""
        return formatter.formatCellValue(cell).trim()
    }

    private fun mapDayOfWeek(day: String?): Int? {
        val value = day?.trim() ?: return null
        return when (value) {
            "星期一", "周一" -> 1
            "星期二", "周二" -> 2
            "星期三", "周三" -> 3
            "星期四", "周四" -> 4
            "星期五", "周五" -> 5
            "星期六", "周六" -> 6
            "星期日", "周日", "星期天", "周天" -> 7
            else -> null
        }
    }

    private fun parsePeriodRange(text: String): Pair<Int?, Int?> {
        val cleaned = text.replace("节", "").replace(" ", "")
        val range = Regex("(\\d+)\\s*-\\s*(\\d+)").find(cleaned)
        return if (range != null) {
            val start = range.groupValues[1].toIntOrNull()
            val end = range.groupValues[2].toIntOrNull()
            Pair(start, end)
        } else {
            val single = Regex("(\\d+)").find(cleaned)?.groupValues?.get(1)?.toIntOrNull()
            Pair(single, single)
        }
    }

    private fun extractBetween(text: String, start: String, end: String?): String? {
        val startIndex = text.indexOf(start)
        if (startIndex < 0) return null
        val fromIndex = startIndex + start.length
        val endIndex = if (end == null) {
            text.length
        } else {
            val idx = text.indexOf(end, fromIndex)
            if (idx < 0) text.length else idx
        }
        return text.substring(fromIndex, endIndex).trim()
    }

    private fun parseWeekPattern(rawText: String?): List<Int> {
        if (rawText.isNullOrBlank()) return emptyList()
        var text = rawText.replace("周", "").replace("，", ",").replace(" ", "")
        text = text.replace(Regex("\\([^)]*节\\)"), "")
        val parts = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val weeks = mutableSetOf<Int>()
        parts.forEach { part ->
            var token = part
            val evenOnly = token.contains("双")
            val oddOnly = token.contains("单")
            token = token.replace("双", "").replace("单", "").replace("(", "").replace(")", "")
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
}
