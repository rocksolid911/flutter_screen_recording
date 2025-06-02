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

// Update your ForegroundService.kt file with this code:

package com.isvisoft.flutter_screen_recording

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "ScreenRecordingChannel"
        private const val NOTIFICATION_ID = 1

        fun startService(context: Context, title: String, message: String, isMediaProjection: Boolean = false) {
            val intent = Intent(context, ForegroundService::class.java).apply {
                putExtra("title", title)
                putExtra("message", message)
                putExtra("isMediaProjection", isMediaProjection)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "Screen Recording"
        val message = intent?.getStringExtra("message") ?: "Recording in progress"
        val isMediaProjection = intent?.getBooleanExtra("isMediaProjection", false) ?: false

        val notification = createNotification(title, message)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isMediaProjection) {
            // For Android 10+ with media projection (casting/screen sharing)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34+)
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                // Android 10-13 (API 29-33)
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            // For regular screen recording or older Android versions
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service for screen recording and casting"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_camera) // You can replace with your own icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }
}