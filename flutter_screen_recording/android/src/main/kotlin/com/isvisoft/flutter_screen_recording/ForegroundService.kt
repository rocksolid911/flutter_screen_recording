// Complete ForegroundService.kt with proper binding support

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class ForegroundService : Service() {

    // Binder for service connection
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_recording_channel"

        fun startService(context: Context, title: String, message: String, isMediaProjection: Boolean = false) {
            Log.d("ForegroundService", "Starting foreground service - title: $title, mediaProjection: $isMediaProjection")

            val intent = Intent(context, ForegroundService::class.java).apply {
                putExtra("title", title)
                putExtra("message", message)
                putExtra("isMediaProjection", isMediaProjection)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d("ForegroundService", "✅ Foreground service start command sent")
            } catch (e: Exception) {
                Log.e("ForegroundService", "❌ Error starting foreground service: ${e.message}")
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            Log.d("ForegroundService", "Stopping foreground service")
            val intent = Intent(context, ForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ForegroundService", "onCreate() called")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ForegroundService", "onStartCommand() called")

        val title = intent?.getStringExtra("title") ?: "Screen Recording"
        val message = intent?.getStringExtra("message") ?: "Recording in progress"
        val isMediaProjection = intent?.getBooleanExtra("isMediaProjection", false) ?: false

        Log.d("ForegroundService", "Service params - title: $title, mediaProjection: $isMediaProjection")

        val notification = createNotification(title, message)

        try {
            // Start foreground service with appropriate service type
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isMediaProjection) {
                Log.d("ForegroundService", "Starting foreground with MEDIA_PROJECTION type")
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                Log.d("ForegroundService", "Starting foreground with default type")
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d("ForegroundService", "✅ Foreground service started successfully")
        } catch (e: Exception) {
            Log.e("ForegroundService", "❌ Error starting foreground: ${e.message}")
            e.printStackTrace()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d("ForegroundService", "onBind() called - returning binder")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("ForegroundService", "onUnbind() called")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d("ForegroundService", "onDestroy() called")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen recording service notifications"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("ForegroundService", "✅ Notification channel created")
        }
    }

    private fun createNotification(title: String, message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}