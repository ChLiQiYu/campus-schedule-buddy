package com.example.campus_schedule_buddy.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaQuickLinkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val manager = MediaChronicleManager(context)
            val sinceMillis = System.currentTimeMillis() - QUICK_LINK_WINDOW_MS
            val added = manager.linkMediaSince(sinceMillis, courseId.takeIf { it > 0 })
            withContext(Dispatchers.Main) {
                val message = if (added > 0) {
                    "已关联${added}条媒体记录"
                } else {
                    "未发现可关联的媒体"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            pendingResult.finish()
        }
    }

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"
        private const val QUICK_LINK_WINDOW_MS = 2 * 60 * 60 * 1000L
    }
}
