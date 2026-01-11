package com.example.campus_schedule_buddy.util

import com.example.campus_schedule_buddy.data.CourseTypeReminderEntity
import com.example.campus_schedule_buddy.data.PeriodTimeEntity
import com.example.campus_schedule_buddy.data.ReminderSettingsEntity
import com.example.campus_schedule_buddy.data.SemesterEntity
import com.example.campus_schedule_buddy.model.Course
import org.json.JSONArray
import org.json.JSONObject

data class CourseJsonPayload(
    val courses: List<Course>,
    val periodTimes: List<PeriodTimeEntity>,
    val reminderSettings: ReminderSettingsEntity?,
    val courseTypeReminders: List<CourseTypeReminderEntity>,
    val semesterName: String?,
    val semesterStartDate: String?
)

object CourseJsonSerializer {
    fun toJson(
        semester: SemesterEntity?,
        periodTimes: List<PeriodTimeEntity>,
        reminderSettings: ReminderSettingsEntity?,
        courseTypeReminders: List<CourseTypeReminderEntity>,
        courses: List<Course>
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        val semesterObject = JSONObject()
        semesterObject.put("name", semester?.name ?: "")
        semesterObject.put("startDate", semester?.startDate ?: "")
        root.put("semester", semesterObject)

        val periodArray = JSONArray()
        periodTimes.forEach { time ->
            val item = JSONObject()
            item.put("period", time.period)
            item.put("startTime", time.startTime)
            item.put("endTime", time.endTime)
            periodArray.put(item)
        }
        root.put("periodTimes", periodArray)

        val reminderObject = JSONObject()
        if (reminderSettings != null) {
            reminderObject.put("leadMinutes", reminderSettings.leadMinutes)
            reminderObject.put("enableNotification", reminderSettings.enableNotification)
            reminderObject.put("enableVibrate", reminderSettings.enableVibrate)
        }
        root.put("reminderSettings", reminderObject)

        val typeArray = JSONArray()
        courseTypeReminders.forEach { item ->
            val obj = JSONObject()
            obj.put("type", item.type)
            obj.put("leadMinutes", item.leadMinutes)
            obj.put("enabled", item.enabled)
            typeArray.put(obj)
        }
        root.put("courseTypeReminders", typeArray)

        val courseArray = JSONArray()
        courses.forEach { course ->
            val obj = JSONObject()
            obj.put("name", course.name)
            obj.put("teacher", course.teacher ?: JSONObject.NULL)
            obj.put("location", course.location ?: JSONObject.NULL)
            obj.put("type", course.type)
            obj.put("dayOfWeek", course.dayOfWeek)
            obj.put("startPeriod", course.startPeriod)
            obj.put("endPeriod", course.endPeriod)
            val weeks = JSONArray()
            course.weekPattern.forEach { weeks.put(it) }
            obj.put("weekPattern", weeks)
            obj.put("note", course.note ?: JSONObject.NULL)
            obj.put("color", course.color ?: JSONObject.NULL)
            courseArray.put(obj)
        }
        root.put("courses", courseArray)

        return root.toString()
    }

    fun fromJson(json: String, semesterId: Long): CourseJsonPayload {
        val root = JSONObject(json)
        val semesterObject = root.optJSONObject("semester")
        val semesterName = semesterObject?.optString("name")?.takeIf { it.isNotBlank() }
        val semesterStartDate = semesterObject?.optString("startDate")?.takeIf { it.isNotBlank() }

        val periodTimes = mutableListOf<PeriodTimeEntity>()
        val periodArray = root.optJSONArray("periodTimes") ?: JSONArray()
        for (i in 0 until periodArray.length()) {
            val item = periodArray.optJSONObject(i) ?: continue
            val period = item.optInt("period", -1)
            val start = item.optString("startTime")
            val end = item.optString("endTime")
            if (period > 0 && start.isNotBlank() && end.isNotBlank()) {
                periodTimes.add(PeriodTimeEntity(semesterId, period, start, end))
            }
        }

        val reminderSettings = root.optJSONObject("reminderSettings")?.let { obj ->
            val lead = obj.optInt("leadMinutes", -1)
            if (lead > 0) {
                ReminderSettingsEntity(
                    semesterId = semesterId,
                    leadMinutes = lead,
                    enableNotification = obj.optBoolean("enableNotification", true),
                    enableVibrate = obj.optBoolean("enableVibrate", true)
                )
            } else {
                null
            }
        }

        val typeReminders = mutableListOf<CourseTypeReminderEntity>()
        val typeArray = root.optJSONArray("courseTypeReminders") ?: JSONArray()
        for (i in 0 until typeArray.length()) {
            val item = typeArray.optJSONObject(i) ?: continue
            val type = item.optString("type")
            val lead = item.optInt("leadMinutes", -1)
            if (type.isNotBlank() && lead > 0) {
                typeReminders.add(
                    CourseTypeReminderEntity(
                        semesterId = semesterId,
                        type = type,
                        leadMinutes = lead,
                        enabled = item.optBoolean("enabled", true)
                    )
                )
            }
        }

        val courses = mutableListOf<Course>()
        val courseArray = root.optJSONArray("courses") ?: JSONArray()
        for (i in 0 until courseArray.length()) {
            val obj = courseArray.optJSONObject(i) ?: continue
            val name = obj.optString("name")
            val dayOfWeek = obj.optInt("dayOfWeek", -1)
            val startPeriod = obj.optInt("startPeriod", -1)
            val endPeriod = obj.optInt("endPeriod", -1)
            val weekPattern = parseWeekPattern(obj.opt("weekPattern"))
            if (name.isBlank() || dayOfWeek !in 1..7 || startPeriod <= 0 || endPeriod <= 0 || weekPattern.isEmpty()) {
                continue
            }
            val color = if (obj.isNull("color")) null else obj.optInt("color")
            val teacher = obj.optString("teacher").takeIf { it.isNotBlank() }
            val location = obj.optString("location").takeIf { it.isNotBlank() }
            val note = obj.optString("note").takeIf { it.isNotBlank() }
            val type = obj.optString("type").takeIf { it.isNotBlank() } ?: "major_required"
            courses.add(
                Course(
                    id = 0,
                    semesterId = semesterId,
                    name = name,
                    teacher = teacher,
                    location = location,
                    type = type,
                    dayOfWeek = dayOfWeek,
                    startPeriod = startPeriod,
                    endPeriod = endPeriod,
                    weekPattern = weekPattern,
                    note = note,
                    color = color
                )
            )
        }

        return CourseJsonPayload(
            courses = courses,
            periodTimes = periodTimes,
            reminderSettings = reminderSettings,
            courseTypeReminders = typeReminders,
            semesterName = semesterName,
            semesterStartDate = semesterStartDate
        )
    }

    private fun parseWeekPattern(value: Any?): List<Int> {
        val weeks = mutableListOf<Int>()
        when (value) {
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val week = value.optInt(i, -1)
                    if (week > 0) weeks.add(week)
                }
            }
            is String -> {
                value.split(",").forEach { part ->
                    val week = part.trim().toIntOrNull()
                    if (week != null && week > 0) weeks.add(week)
                }
            }
        }
        return weeks.distinct().sorted()
    }
}
