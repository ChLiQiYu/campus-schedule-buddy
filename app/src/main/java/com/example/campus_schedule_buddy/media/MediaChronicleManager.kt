package com.example.campus_schedule_buddy.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.campus_schedule_buddy.data.AppDatabase
import com.example.campus_schedule_buddy.data.CourseAttachmentEntity
import com.example.campus_schedule_buddy.data.WorkspaceRepository
import com.example.campus_schedule_buddy.util.TimeMappingAlgorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

class MediaChronicleManager(private val context: Context) {
    suspend fun linkMediaSince(sinceMillis: Long, targetCourseId: Long? = null): Int {
        return withContext(Dispatchers.IO) {
            if (!hasMediaPermission()) return@withContext 0
            val database = AppDatabase.getInstance(context)
            val settings = database.settingsDao().getAppSettings() ?: return@withContext 0
            val semesterId = settings.currentSemesterId
            if (semesterId <= 0L) return@withContext 0
            val semester = database.semesterDao().getSemester(semesterId) ?: return@withContext 0
            val semesterStart = runCatching { LocalDate.parse(semester.startDate) }.getOrNull()
                ?: return@withContext 0
            val periodTimes = database.settingsDao().getPeriodTimes(semesterId)
            val courses = database.courseDao().getAllCourses(semesterId)
            if (courses.isEmpty() || periodTimes.isEmpty()) return@withContext 0

            val workspaceRepository = WorkspaceRepository(database.workspaceDao())
            val mediaItems = queryMediaSince(sinceMillis)
            var addedCount = 0
            mediaItems.forEach { item ->
                val matchedCourses = TimeMappingAlgorithm.matchCoursesForMedia(
                    mediaTime = item.takenAt,
                    courses = courses,
                    periodTimes = periodTimes,
                    semesterStartDate = semesterStart
                )
                val targetCourses = if (targetCourseId != null) {
                    matchedCourses.filter { it.id == targetCourseId }
                } else {
                    matchedCourses
                }
                if (targetCourses.isEmpty()) return@forEach
                val course = targetCourses.first()
                if (workspaceRepository.hasAttachmentUri(item.uri.toString(), CourseAttachmentEntity.SOURCE_CHRONICLE_MEDIA)) {
                    return@forEach
                }
                val attachment = CourseAttachmentEntity(
                    semesterId = semesterId,
                    courseId = course.id,
                    type = CourseAttachmentEntity.TYPE_MEDIA,
                    title = item.displayName.ifBlank { "课堂媒体" },
                    uri = item.uri.toString(),
                    sourceType = CourseAttachmentEntity.SOURCE_CHRONICLE_MEDIA,
                    createdAt = item.takenAt.toEpochMilli()
                )
                workspaceRepository.addAttachment(attachment)
                addedCount += 1
            }
            addedCount
        }
    }

    private fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val imageGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val audioGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            imageGranted || audioGranted
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun queryMediaSince(sinceMillis: Long): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        items += queryImagesSince(sinceMillis)
        items += queryAudioSince(sinceMillis)
        return items.sortedBy { it.takenAt }
    }

    private fun queryImagesSince(sinceMillis: Long): List<MediaItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val selection = "${MediaStore.Images.Media.DATE_TAKEN} >= ?"
        val selectionArgs = arrayOf(sinceMillis.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        return queryMedia(collection, projection, selection, selectionArgs, sortOrder) { cursor ->
            val id = cursor.getLong(0)
            val name = cursor.getString(1) ?: ""
            val takenAt = cursor.getLong(2).takeIf { it > 0 } ?: return@queryMedia null
            val uri = ContentUris.withAppendedId(collection, id)
            MediaItem(uri, name, Instant.ofEpochMilli(takenAt))
        }
    }

    private fun queryAudioSince(sinceMillis: Long): List<MediaItem> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf((sinceMillis / 1000).toString())
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        return queryMedia(collection, projection, selection, selectionArgs, sortOrder) { cursor ->
            val id = cursor.getLong(0)
            val name = cursor.getString(1) ?: ""
            val addedSeconds = cursor.getLong(2).takeIf { it > 0 } ?: return@queryMedia null
            val uri = ContentUris.withAppendedId(collection, id)
            MediaItem(uri, name, Instant.ofEpochSecond(addedSeconds))
        }
    }

    private fun queryMedia(
        uri: Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
        sortOrder: String,
        mapper: (android.database.Cursor) -> MediaItem?
    ): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                val item = mapper(cursor)
                if (item != null) {
                    results.add(item)
                }
            }
        }
        return results
    }

    data class MediaItem(
        val uri: Uri,
        val displayName: String,
        val takenAt: Instant
    )
}
