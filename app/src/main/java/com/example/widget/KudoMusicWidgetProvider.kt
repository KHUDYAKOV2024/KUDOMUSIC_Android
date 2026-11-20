package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.audio.PlaybackCoordinator
import com.example.data.hub.HubConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class KudoMusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        renderWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val coordinator = PlaybackCoordinator.getInstance(context)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> coordinator.togglePlayPause()
            ACTION_NEXT -> coordinator.skipNext()
            ACTION_PREV -> coordinator.skipPrevious()
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.example.widget.ACTION_PREV"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KudoMusicWidgetProvider::class.java))
            if (ids.isEmpty()) return
            renderWidgets(context, manager, ids)
        }

        private fun renderWidgets(
            context: Context,
            manager: AppWidgetManager,
            widgetIds: IntArray
        ) {
            val coordinator = PlaybackCoordinator.getInstance(context)
            val track = coordinator.playbackState.value.currentTrack
            val isPlaying = coordinator.engineState.value.isPlaying

            val baseViews = buildBaseViews(context, track?.title, track?.artist, isPlaying)
            for (id in widgetIds) {
                manager.updateAppWidget(id, baseViews)
            }

            if (track != null && (track.localArtPath != null || track.coverUrl != null)) {
                val headers = HubConfigStore(context).loadSession().buildAuthHeaders()
                widgetScope.launch {
                    val bitmap = loadArtworkBitmap(track.localArtPath, track.coverUrl, headers)
                    if (bitmap != null) {
                        val rounded = roundCorners(bitmap, 28f)
                        val updatedViews = buildBaseViews(context, track.title, track.artist, isPlaying).apply {
                            setImageViewBitmap(R.id.widget_artwork, rounded)
                            setViewPadding(R.id.widget_artwork, 0, 0, 0, 0)
                        }
                        for (id in widgetIds) {
                            manager.updateAppWidget(id, updatedViews)
                        }
                    }
                }
            }
        }

        private fun buildBaseViews(
            context: Context,
            title: String?,
            artist: String?,
            isPlaying: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_kudomusic)
            views.setTextViewText(
                R.id.widget_title,
                title?.ifBlank { context.getString(R.string.widget_idle_title) }
                    ?: context.getString(R.string.widget_idle_title)
            )
            views.setTextViewText(
                R.id.widget_artist,
                artist?.ifBlank { context.getString(R.string.widget_idle_subtitle) }
                    ?: context.getString(R.string.widget_idle_subtitle)
            )
            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_music_note)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                100,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)
            views.setOnClickPendingIntent(R.id.widget_text_container, openPending)
            views.setOnClickPendingIntent(R.id.widget_artwork, openPending)

            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                actionPendingIntent(context, ACTION_PREV, 101)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                actionPendingIntent(context, ACTION_PLAY_PAUSE, 102)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                actionPendingIntent(context, ACTION_NEXT, 103)
            )
            return views
        }

        private fun actionPendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, KudoMusicWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun loadArtworkBitmap(
            localArtPath: String?,
            coverUrl: String?,
            headers: Map<String, String>
        ): Bitmap? {
            if (!localArtPath.isNullOrBlank()) {
                val file = File(localArtPath)
                if (file.exists()) {
                    return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                }
            }
            if (!coverUrl.isNullOrBlank()) {
                var conn: HttpURLConnection? = null
                return try {
                    conn = (URL(coverUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5_000
                        readTimeout = 5_000
                        headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    }
                    if (conn.responseCode in 200..299) {
                        BitmapFactory.decodeStream(conn.inputStream)
                    } else null
                } catch (_: Exception) {
                    null
                } finally {
                    conn?.disconnect()
                }
            }
            return null
        }

        private fun roundCorners(source: Bitmap, radiusPx: Float): Bitmap {
            val size = 160
            val scaled = Bitmap.createScaledBitmap(source, size, size, true)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, size, size)
            val rectF = RectF(rect)
            canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, rect, rect, paint)
            return output
        }
    }
}
