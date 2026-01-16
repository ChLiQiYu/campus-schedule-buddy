package com.fjnu.schedule.media

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MediaObserverService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }
    private var scanJob: Job? = null
    private lateinit var handlerThread: HandlerThread
    private lateinit var observerHandler: Handler
    private lateinit var observer: ContentObserver

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("MediaObserverThread").apply { start() }
        observerHandler = Handler(handlerThread.looper)
        observer = object : ContentObserver(observerHandler) {
            override fun onChange(selfChange: Boolean) {
                scheduleScan()
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        scheduleScan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        scanJob?.cancel()
        handlerThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleScan() {
        scanJob?.cancel()
        scanJob = serviceScope.launch {
            delay(SCAN_DEBOUNCE_MS)
            val lastScan = prefs.getLong(KEY_LAST_SCAN, System.currentTimeMillis() - DEFAULT_LOOKBACK_MS)
            val manager = MediaChronicleManager(this@MediaObserverService)
            manager.linkMediaSince(lastScan, null)
            prefs.edit().putLong(KEY_LAST_SCAN, System.currentTimeMillis()).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "media_chronicle_prefs"
        private const val KEY_LAST_SCAN = "last_scan_time"
        private const val DEFAULT_LOOKBACK_MS = 60 * 60 * 1000L
        private const val SCAN_DEBOUNCE_MS = 1500L
    }
}
