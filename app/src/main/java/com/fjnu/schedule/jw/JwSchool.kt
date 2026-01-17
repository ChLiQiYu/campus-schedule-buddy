package com.fjnu.schedule.jw

enum class JwLoginMode {
    JWGLXT_AUTO,
    WEBVIEW_ONLY
}

data class JwSchool(
    val id: String,
    val name: String,
    val loginUrl: String,
    val scheduleUrl: String,
    val loginMode: JwLoginMode = JwLoginMode.JWGLXT_AUTO,
    val hint: String? = null
)
