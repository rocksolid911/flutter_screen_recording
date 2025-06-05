//package com.isvisoft.flutter_screen_recording
//
//import android.Manifest
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.PendingIntent
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.os.Build
//import android.os.IBinder
//import androidx.core.app.ActivityCompat
//import androidx.core.app.NotificationCompat
//import androidx.core.content.ContextCompat
//import android.app.Activity
//import android.os.Binder
//
//class ForegroundService : Service() {
//    private val CHANNEL_ID = "ForegroundService Kotlin"
//    private val REQUEST_CODE_MEDIA_PROJECTION = 1001
//
//    companion object {
//        fun startService(context: Context, title: String, message: String) {
//            println("-------------------------- startService");
//
//            try {
//                val startIntent = Intent(context, ForegroundService::class.java)
//                startIntent.putExtra("messageExtra", message)
//                startIntent.putExtra("titleExtra", title)
//                println("-------------------------- startService2");
//
//                ContextCompat.startForegroundService(context, startIntent)
//                println("-------------------------- startService3");
//
//            } catch (err: Exception) {
//                println("startService err");
//                println(err);
//            }
//        }
//
//        fun stopService(context: Context) {
//            val stopIntent = Intent(context, ForegroundService::class.java)
//                .setAction(ACTION_STOP)
//            context.startService(stopIntent)
//        }
//
//        const val ACTION_STOP = "com.foregroundservice.ACTION_STOP"
//    }
//
//
//    @Suppress("DEPRECATION")
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//
//        if (intent?.action == ACTION_STOP) {
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                stopForeground(STOP_FOREGROUND_REMOVE)
//            } else {
//                stopForeground(true)
//            }
//
//            stopSelf()
//
//            return START_NOT_STICKY
//        } else {
//
//            try {
//
//                println("-------------------------- onStartCommand")
//
//                // Verificar permisos en Android 14 (SDK 34)
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
//                    if (ContextCompat.checkSelfPermission(
//                            this,
//                            Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
//                        )
//                        == PackageManager.PERMISSION_DENIED
//                    ) {
//                        println("MediaProjection permission not granted, requesting permission")
//
//                        // Solicitar el permiso si no ha sido concedido
//                        ActivityCompat.requestPermissions(
//                            this as Activity,
//                            arrayOf(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION),
//                            REQUEST_CODE_MEDIA_PROJECTION
//                        )
//                    } else {
//                        // Si ya está concedido, continuar normalmente
//                        startForegroundServiceWithNotification(intent)
//                    }
//                } else {
//                    // Si no es Android 14, continuar normalmente
//                    startForegroundServiceWithNotification(intent)
//                }
//
//                return START_STICKY
//            } catch (err: Exception) {
//                println("onStartCommand err")
//                println(err)
//            }
//            return START_STICKY
//        }
//    }
//
//    private fun startForegroundServiceWithNotification(intent: Intent?) {
//        var title = intent?.getStringExtra("titleExtra") ?: "Flutter Screen Recording"
//        var message = intent?.getStringExtra("messageExtra") ?: ""
//
//        createNotificationChannel()
//        val notificationIntent = Intent(this, FlutterScreenRecordingPlugin::class.java)
//
//        val pendingIntent = PendingIntent.getActivity(
//            this, 0, notificationIntent, PendingIntent.FLAG_MUTABLE
//        )
//
//        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle(title)
//            .setContentText(message)
//            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
//            .setContentIntent(pendingIntent)
//            .build()
//
//        startForeground(1, notification)
//        println("-------------------------- startForegroundServiceWithNotification")
//    }
//
//    override fun onBind(intent: Intent): IBinder {
//        return Binder()
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val serviceChannel = NotificationChannel(
//                CHANNEL_ID, "Foreground Service Channel", NotificationManager.IMPORTANCE_DEFAULT
//            )
//            val manager = getSystemService(NotificationManager::class.java)
//            manager!!.createNotificationChannel(serviceChannel)
//        }
//    }
//}

package com.isvisoft.flutter_screen_recording

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

    companion object {
        private const val TAG = "ForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_recording_channel"

        fun startService(context: Context, title: String, message: String, isMediaProjection: Boolean = false) {
            Log.d(TAG, "📞 startService called")
            Log.d(TAG, "Context: $context")
            Log.d(TAG, "Title: $title")
            Log.d(TAG, "Message: $message")
            Log.d(TAG, "Is media projection: $isMediaProjection")

            val intent = Intent(context, ForegroundService::class.java).apply {
                putExtra("title", title)
                putExtra("message", message)
                putExtra("isMediaProjection", isMediaProjection)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d(TAG, "🚀 Starting foreground service (API 26+)")
                    context.startForegroundService(intent)
                } else {
                    Log.d(TAG, "🚀 Starting regular service (API < 26)")
                    context.startService(intent)
                }
                Log.d(TAG, "✅ Service start command sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting service: ${e.message}")
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            Log.d(TAG, "🛑 Stopping service")
            val intent = Intent(context, ForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎉 ForegroundService onCreate() called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🎯 ForegroundService onStartCommand() called")
        Log.d(TAG, "Intent: $intent")
        Log.d(TAG, "Flags: $flags")
        Log.d(TAG, "Start ID: $startId")

        val title = intent?.getStringExtra("title") ?: "Screen Recording"
        val message = intent?.getStringExtra("message") ?: "Recording in progress"
        val isMediaProjection = intent?.getBooleanExtra("isMediaProjection", false) ?: false

        Log.d(TAG, "Extracted title: $title")
        Log.d(TAG, "Extracted message: $message")
        Log.d(TAG, "Is media projection: $isMediaProjection")

        try {
            Log.d(TAG, "📱 Creating notification channel...")
            createNotificationChannel()

            Log.d(TAG, "🔔 Creating notification...")
            val notification = createNotification(title, message)

            Log.d(TAG, "🚀 Starting foreground with notification...")

            // Start foreground service with correct type
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isMediaProjection) {
                Log.d(TAG, "📱 Starting with MEDIA_PROJECTION type (API 29+)")
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                Log.d(TAG, "📱 Starting with default type")
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.d(TAG, "✅ Foreground service started successfully!")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onStartCommand: ${e.message}")
            e.printStackTrace()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "🔗 ForegroundService onBind() called")
        Log.d(TAG, "Bind intent: $intent")

        // Return a simple binder
        return object : Binder() {
            fun getService(): ForegroundService = this@ForegroundService
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "🔓 ForegroundService onUnbind() called")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "💀 ForegroundService onDestroy() called")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel (API 26+)")
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen recording and casting service"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Notification channel created")
        }
    }

    private fun createNotification(title: String, message: String): Notification {
        Log.d(TAG, "Creating notification with title: $title, message: $message")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_media_play) // Use a system icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build().also {
                Log.d(TAG, "✅ Notification created successfully")
            }
    }
}


