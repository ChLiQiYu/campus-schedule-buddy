package com.example.campus_schedule_buddy.util

import org.json.JSONObject

data class GroupSyncPayload(
    val code: String,
    val memberName: String,
    val totalWeeks: Int,
    val periodCount: Int,
    val freeSlots: String,
    val createdAt: Long
)

object GroupSyncSerializer {
    fun toJson(payload: GroupSyncPayload): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("code", payload.code)
        root.put("memberName", payload.memberName)
        root.put("totalWeeks", payload.totalWeeks)
        root.put("periodCount", payload.periodCount)
        root.put("freeSlots", payload.freeSlots)
        root.put("createdAt", payload.createdAt)
        return root.toString()
    }

    fun fromJson(json: String): GroupSyncPayload? {
        return try {
            val root = JSONObject(json)
            val code = root.optString("code").trim()
            val memberName = root.optString("memberName").trim()
            val totalWeeks = root.optInt("totalWeeks", -1)
            val periodCount = root.optInt("periodCount", -1)
            val freeSlots = root.optString("freeSlots")
            if (code.isBlank() || memberName.isBlank()) return null
            if (totalWeeks <= 0 || periodCount <= 0) return null
            if (freeSlots.isBlank()) return null
            val createdAt = root.optLong("createdAt", 0L)
            GroupSyncPayload(
                code = code,
                memberName = memberName,
                totalWeeks = totalWeeks,
                periodCount = periodCount,
                freeSlots = freeSlots,
                createdAt = createdAt
            )
        } catch (_: Exception) {
            null
        }
    }
}
