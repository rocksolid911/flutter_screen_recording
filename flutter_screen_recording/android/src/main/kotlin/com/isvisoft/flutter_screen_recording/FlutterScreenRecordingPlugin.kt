package com.isvisoft.flutter_screen_recording

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouteSelector
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.*
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.util.Base64
import android.widget.FrameLayout
import android.widget.Toast
import android.view.Display
import android.content.Context
import android.graphics.ImageFormat
import android.media.Image
import android.os.Bundle
import android.app.Presentation
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FlutterScreenRecordingPlugin :
    MethodCallHandler,
    PluginRegistry.ActivityResultListener,
    FlutterPlugin,
    ActivityAware {

    private var startMediaProjection: ActivityResultLauncher<Intent>? = null
    private var mScreenDensity: Int = 0
    var mMediaRecorder: MediaRecorder? = null
    private lateinit var mProjectionManager: MediaProjectionManager
    var mMediaProjection: MediaProjection? = null
    private var mMediaProjectionCallback: MediaProjectionCallback? = null
    var mVirtualDisplay: VirtualDisplay? = null
    private var mDisplayWidth: Int = 1280
    private var mDisplayHeight: Int = 800
    private var videoName: String? = ""
    private var mFileName: String? = ""
    private var mTitle = "Your screen is being recorded"
    private var mMessage = "Your screen is being recorded"
    private var recordAudio: Boolean? = false
    private val SCREEN_RECORD_REQUEST_CODE = 333

    private lateinit var _result: Result
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private val eventSink = Executors.newSingleThreadExecutor()

    private var pluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    private var activityBinding: ActivityPluginBinding? = null

    private var serviceConnection: ServiceConnection? = null

    // For screen sharing
    private var mScreenShareCallback: Result? = null
    private var mImageReader: ImageReader? = null
    private var mScreenShareVirtualDisplay: VirtualDisplay? = null
    private var mScreenShareHandler: Handler? = null
    private var mScreenShareThread: HandlerThread? = null
    private var isScreenSharing = false

    // Cast properties
    private var mediaRouter: MediaRouter? = null
    private var castCallback: MediaRouter.Callback? = null
    private var isCasting = false

    // Add these new Cast-related properties:
    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var sessionManagerListener: SessionManagerListener<CastSession>? = null
    private var currentCastSession: CastSession? = null
//    private var currentPresentation: ScreenMirrorPresentation? = null
    private var currentPresentation: Presentation? = null
    private var castVirtualDisplay: VirtualDisplay? = null
    private var castImageReader: ImageReader? = null
    private var castHandler: Handler? = null
    private var castThread: HandlerThread? = null

    // Implementation of FlutterPlugin interface
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        pluginBinding = binding
        methodChannel = MethodChannel(binding.binaryMessenger, "flutter_screen_recording")
        methodChannel.setMethodCallHandler(this)

        // Set up event channel for device discovery
        eventChannel = EventChannel(binding.binaryMessenger, "flutter_screen_recording_events")
        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                // Store event sink for later use
            }

            override fun onCancel(arguments: Any?) {
                // Clean up
            }
        })
    }



    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        pluginBinding = null
    }

    // Implementation of ActivityAware interface
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addActivityResultListener(this)

        try {
            // Initialize MediaProjectionManager
            mProjectionManager = ContextCompat.getSystemService(
                binding.activity.applicationContext,
                MediaProjectionManager::class.java
            ) ?: throw Exception("MediaProjectionManager not found")

            // Initialize Cast Context
            initializeCastContext(binding.activity.applicationContext)
            // Initialize MediaRouter for cast discovery
            mediaRouter = MediaRouter.getInstance(binding.activity.applicationContext)

            // Register for activity result (modern API)
            registerForActivityResult(binding.activity)
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error initializing: ${e.message}")
        }
    }

    // Add this new method to initialize Cast Context:
    private fun initializeCastContext(context: Context) {
        try {
            castContext = CastContext.getSharedInstance(context)
            sessionManager = castContext?.sessionManager

            sessionManagerListener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
//                    Log.d("ScreenRecordingPlugin", "Cast session started: $sessionId")
//                    currentCastSession = session
//
//                    // AUTO-START CASTING AFTER CONNECTION
//                    Log.d("ScreenRecordingPlugin", "🚀 Auto-starting casting after connection...")
//
//                    // Create a dummy result for auto-start
//                    val autoStartResult = object : Result {
//                        override fun success(result: Any?) {
//                            Log.d("ScreenRecordingPlugin", "✅ Auto-start casting succeeded")
//                            methodChannel.invokeMethod("onCastingStarted", null)
//                        }
//
//                        override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
//                            Log.e("ScreenRecordingPlugin", "❌ Auto-start casting failed: $errorMessage")
//                            methodChannel.invokeMethod("onCastingFailed", mapOf("error" to errorMessage))
//                        }
//
//                        override fun notImplemented() {
//                            Log.e("ScreenRecordingPlugin", "❌ Auto-start casting not implemented")
//                        }
//                    }
//
//                    // Start casting automatically
//                    try {
////                        startCasting(autoStartResult)
////                        startSuperSimpleMirror(autoStartResult)
//                        startActualMirroring(autoStartResult)
//                    } catch (e: Exception) {
//                        Log.e("ScreenRecordingPlugin", "❌ Error auto-starting casting: ${e.message}")
//                        autoStartResult.error("AUTO_START_ERROR", e.message, null)
//                    }
//
//                    // Notify Flutter about successful connection
//                    methodChannel.invokeMethod("onCastConnected", mapOf("sessionId" to sessionId))
                    Log.d("ScreenRecordingPlugin", "Cast session started: $sessionId")
                    currentCastSession = session

                    // AUTO-START CASTING AFTER CONNECTION
                    Log.d("ScreenRecordingPlugin", "🚀 Auto-starting casting after connection...")

                    val autoStartResult = object : Result {
                        override fun success(result: Any?) {
                            Log.d("ScreenRecordingPlugin", "✅ Auto-start casting succeeded")
                            methodChannel.invokeMethod("onCastingStarted", null)
                        }

                        override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                            Log.e("ScreenRecordingPlugin", "❌ Auto-start casting failed: $errorMessage")
                            methodChannel.invokeMethod("onCastingFailed", mapOf("error" to errorMessage))
                        }

                        override fun notImplemented() {
                            Log.e("ScreenRecordingPlugin", "❌ Auto-start casting not implemented")
                        }
                    }

                    // FIXED: Use startSimpleCasting instead of startActualMirroring
                    // This will properly request permission first if needed
                    try {
                        startSimpleCasting(autoStartResult)
                    } catch (e: Exception) {
                        Log.e("ScreenRecordingPlugin", "❌ Error auto-starting casting: ${e.message}")
                        autoStartResult.error("AUTO_START_ERROR", e.message, null)
                    }

                    methodChannel.invokeMethod("onCastConnected", mapOf("sessionId" to sessionId))
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    Log.d("ScreenRecordingPlugin", "Cast session ended")
                    currentCastSession = null
                    stopScreenCasting()
                    // Notify Flutter about disconnection
                    methodChannel.invokeMethod("onCastDisconnected", null)
                }

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    Log.d("ScreenRecordingPlugin", "Cast session resumed")
                    currentCastSession = session
                }

                override fun onSessionSuspended(session: CastSession, reason: Int) {
                    Log.d("ScreenRecordingPlugin", "Cast session suspended")
                }

                override fun onSessionStarting(session: CastSession) {}
                override fun onSessionStartFailed(session: CastSession, error: Int) {
                    Log.e("ScreenRecordingPlugin", "Cast session start failed: $error")
                    methodChannel.invokeMethod("onCastConnectionFailed", mapOf("error" to error))
                }
                override fun onSessionEnding(session: CastSession) {}
                override fun onSessionResuming(session: CastSession, sessionId: String) {}
                override fun onSessionResumeFailed(session: CastSession, error: Int) {}
            }

            sessionManager?.addSessionManagerListener(sessionManagerListener!!, CastSession::class.java)

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error initializing Cast Context: ${e.message}")
        }
    }


    private fun startSimpleCasting(result: Result) {
        try {
            Log.d("ScreenRecordingPlugin", "=== STARTING SIMPLE CASTING ===")

            if (currentCastSession == null || !currentCastSession!!.isConnected) {
                result.error("NO_CAST_SESSION", "No active cast session", null)
                return
            }

            Log.d("ScreenRecordingPlugin", "✅ Cast session active: ${currentCastSession!!.castDevice?.friendlyName}")

            // Check if we have screen capture permission
            if (mMediaProjection == null) {
                Log.d("ScreenRecordingPlugin", "📱 Need screen capture permission...")

                _result = result
                mScreenShareCallback = result

                val metrics = DisplayMetrics()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activityBinding!!.activity.display?.getRealMetrics(metrics)
                } else {
                    @SuppressLint("NewApi")
                    pluginBinding!!.applicationContext.display?.getMetrics(metrics)
                }
                mScreenDensity = metrics.densityDpi
                calculateResolution(metrics)

                Log.d("ScreenRecordingPlugin", "📱 Requesting screen capture permission...")
                Log.d("ScreenRecordingPlugin", "Screen resolution: ${mDisplayWidth}x${mDisplayHeight}, density: $mScreenDensity")

                // Request permission
                if (startMediaProjection != null) {
                    startMediaProjection!!.launch(mProjectionManager.createScreenCaptureIntent())
                } else {
                    val permissionIntent = mProjectionManager.createScreenCaptureIntent()
                    ActivityCompat.startActivityForResult(
                        activityBinding!!.activity,
                        permissionIntent,
                        SCREEN_RECORD_REQUEST_CODE,
                        null
                    )
                }
                return
            }

            // We have permission, start the actual mirroring
            Log.d("ScreenRecordingPlugin", "✅ Permission available, starting mirror...")
            startActualMirroring(result)

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "❌ Error starting simple casting: ${e.message}")
            e.printStackTrace()
            result.error("SIMPLE_CASTING_ERROR", e.message, null)
        }
    }



    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }



    // Legacy activity result handling
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                handleScreenCaptureResult(resultCode, data)
            } else {
                _result.success(false)
            }
            return true
        }
        return false
    }

    // Modern activity result handling
    private fun registerForActivityResult(activity: Activity) {
        if (activity is ComponentActivity) {
            startMediaProjection = activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    handleScreenCaptureResult(result.resultCode, result.data!!)
                } else {
                    _result.success(false)
                }
            }
        }
    }



    private fun handleScreenCaptureResult(resultCode: Int, data: Intent) {
        Log.d("ScreenRecordingPlugin", "=== HANDLE SCREEN CAPTURE RESULT ===")
        Log.d("ScreenRecordingPlugin", "Result code: $resultCode")
        Log.d("ScreenRecordingPlugin", "mScreenShareCallback: $mScreenShareCallback")

        val context = pluginBinding!!.applicationContext

        // Check if this is for casting
        if (mScreenShareCallback != null) {
            Log.d("ScreenRecordingPlugin", "📱 Processing screen capture for CASTING")

            if (resultCode == Activity.RESULT_OK) {
                Log.d("ScreenRecordingPlugin", "✅ User granted screen capture permission")

                // Store the callback locally before clearing it
                val localCallback = mScreenShareCallback!!
                mScreenShareCallback = null

                // Start foreground service
                Log.d("ScreenRecordingPlugin", "🚀 Starting foreground service...")
                ForegroundService.startService(context, mTitle, mMessage, true)

                // Wait a moment for service to initialize, then proceed
                android.os.Handler().postDelayed({
                    try {
                        Log.d("ScreenRecordingPlugin", "🔐 Getting MediaProjection directly...")
                        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data)
                        Log.d("ScreenRecordingPlugin", "✅ MediaProjection obtained: $mMediaProjection")

                        if (mMediaProjection != null) {
                            // CRITICAL FIX: Register MediaProjection callback first
                            Log.d("ScreenRecordingPlugin", "📋 Registering MediaProjection callback...")
                            mMediaProjectionCallback = MediaProjectionCallback()
                            mMediaProjection?.registerCallback(mMediaProjectionCallback as MediaProjection.Callback, null)
                            Log.d("ScreenRecordingPlugin", "✅ MediaProjection callback registered")

                            Log.d("ScreenRecordingPlugin", "🎬 Starting actual mirroring...")
                            startActualMirroring(localCallback)
                        } else {
                            Log.e("ScreenRecordingPlugin", "❌ MediaProjection is null after creation")
                            localCallback.error("MEDIA_PROJECTION_NULL", "Failed to create MediaProjection", null)
                        }

                    } catch (e: Exception) {
                        Log.e("ScreenRecordingPlugin", "❌ Error getting MediaProjection: ${e.message}")
                        e.printStackTrace()
                        localCallback.error("MEDIA_PROJECTION_ERROR", e.message, null)
                    }
                }, 1000)

            } else {
                Log.e("ScreenRecordingPlugin", "❌ User denied screen capture permission")
                mScreenShareCallback?.error("PERMISSION_DENIED", "User denied screen capture permission", null)
                mScreenShareCallback = null
            }

            return
        }

        // Original recording logic
        Log.d("ScreenRecordingPlugin", "📹 Processing screen capture for RECORDING")
    }


    // Method call handler
    override fun onMethodCall(call: MethodCall, result: Result) {
        val appContext = pluginBinding!!.applicationContext

        when (call.method) {
            "discoverCastDevices" -> {
                try {
                    discoverCastDevices(result)
                } catch (e: Exception) {
                    result.error("CAST_DISCOVERY_ERROR", e.message, null)
                }
            }

            "connectToCastDevice" -> {
                try {
                    val deviceId = call.argument<String>("deviceId")
                    if (deviceId != null) {
                        connectToCastDevice(deviceId, result)
                    } else {
                        result.error("INVALID_DEVICE_ID", "Device ID is required", null)
                    }
                } catch (e: Exception) {
                    result.error("CAST_CONNECTION_ERROR", e.message, null)
                }
            }

            "startCasting" -> {
                try {
                    Log.d("ScreenRecordingPlugin", "=== METHOD CALL: startCasting ===")
                    Log.d("ScreenRecordingPlugin", "Current cast session before call: $currentCastSession")
                    Log.d("ScreenRecordingPlugin", "Current isCasting flag: $isCasting")

//                    startCasting(result)
//                    startSuperSimpleMirror(result)

                    startSimpleCasting(result)

                    Log.d("ScreenRecordingPlugin", "=== startCasting call completed ===")
                } catch (e: Exception) {
                    Log.e("ScreenRecordingPlugin", "=== startCasting EXCEPTION ===")
                    Log.e("ScreenRecordingPlugin", "Error: ${e.message}")
                    e.printStackTrace()
                    result.error("START_CASTING_ERROR", e.message, null)
                }
            }

            "stopCasting" -> {
                try {
                    stopCasting(result)
                } catch (e: Exception) {
                    result.error("STOP_CASTING_ERROR", e.message, null)
                }
            }

            "startRecordScreen" -> {
                try {
                    _result = result
                    val title = call.argument<String?>("title")
                    val message = call.argument<String?>("message")

                    if (!title.isNullOrEmpty()) {
                        mTitle = title
                    }

                    if (!message.isNullOrEmpty()) {
                        mMessage = message
                    }

                    val metrics = DisplayMetrics()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val display = activityBinding!!.activity.display
                        display?.getRealMetrics(metrics)
                    } else {
                        @SuppressLint("NewApi")
                        val defaultDisplay = appContext.display
                        defaultDisplay?.getMetrics(metrics)
                    }
                    mScreenDensity = metrics.densityDpi
                    calculateResolution(metrics)
                    videoName = call.argument<String?>("name")
                    recordAudio = call.argument<Boolean?>("audio")

                    // Use the new launcher for screen capture
                    if (startMediaProjection != null) {
                        startMediaProjection!!.launch(mProjectionManager.createScreenCaptureIntent())
                    } else {
                        // Fallback to old method if launcher is not available
                        val permissionIntent = mProjectionManager.createScreenCaptureIntent()
                        ActivityCompat.startActivityForResult(
                            activityBinding!!.activity,
                            permissionIntent,
                            SCREEN_RECORD_REQUEST_CODE,
                            null
                        )
                    }

                } catch (e: Exception) {
                    Log.e("ScreenRecordingPlugin", "Error starting recording: ${e.message}")
                    result.success(false)
                }
            }

            "stopRecordScreen" -> {
                try {
                    serviceConnection?.let {
                        appContext.unbindService(it)
                    }
                    ForegroundService.stopService(pluginBinding!!.applicationContext)
                    if (mMediaRecorder != null) {
                        stopRecordScreen()
                        result.success(mFileName)
                    } else {
                        result.success("")
                    }
                } catch (e: Exception) {
                    result.success("")
                }
            }

            "startScreenShare" -> {
                try {
                    _result = result
                    mScreenShareCallback = result

                    val metrics = DisplayMetrics()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val display = activityBinding!!.activity.display
                        display?.getRealMetrics(metrics)
                    } else {
                        @SuppressLint("NewApi")
                        val defaultDisplay = appContext.display
                        defaultDisplay?.getMetrics(metrics)
                    }
                    mScreenDensity = metrics.densityDpi
                    calculateResolution(metrics)

                    // Use the ActivityResultLauncher for screen capture permission
                    if (startMediaProjection != null) {
                        startMediaProjection!!.launch(mProjectionManager.createScreenCaptureIntent())
                    } else {
                        val permissionIntent = mProjectionManager.createScreenCaptureIntent()
                        ActivityCompat.startActivityForResult(
                            activityBinding!!.activity,
                            permissionIntent,
                            SCREEN_RECORD_REQUEST_CODE,
                            null
                        )
                    }
                } catch (e: Exception) {
                    result.error("SCREEN_SHARE_ERROR", e.message, null)
                }
            }

            "stopScreenShare" -> {
                try {
                    if (isScreenSharing) {
                        stopScreenShare()
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                } catch (e: Exception) {
                    result.error("SCREEN_SHARE_STOP_ERROR", e.message, null)
                }
            }
            "getLocalIpAddress" -> {
                try {
                    val ipAddress = NetworkInterface.getNetworkInterfaces().toList()
                        .flatMap { it.inetAddresses.toList() }
                        .filter { it is Inet4Address && !it.isLoopbackAddress }
                        .map { it.hostAddress }
                        .firstOrNull()

                    if (ipAddress != null) {
                        result.success(ipAddress)
                    } else {
                        result.error("IP_ADDRESS_ERROR", "No valid IP address found", null)
                    }
                } catch (e: Exception) {
                    result.error("IP_ADDRESS_ERROR", e.message, null)
                }
            }

            "checkCastCapabilities" -> {
                try {
                    checkCastDeviceCapabilities(result)
                } catch (e: Exception) {
                    result.error("CHECK_CAPABILITIES_ERROR", e.message, null)
                }
            }


            else -> {
                result.notImplemented()
            }
        }
    }

    // MediaProjectionCallback implementation
    private inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            mMediaRecorder?.stop()
            mMediaRecorder?.reset()
            mMediaProjection = null
            if (mVirtualDisplay != null) {
                mVirtualDisplay?.release()
                mVirtualDisplay = null
            }
        }
    }

    // Screen recording methods
    private fun startRecordScreen() {
        try {
            mMediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(pluginBinding!!.applicationContext)
            } else {
                MediaRecorder()
            }

            mFileName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath +
                    (if (videoName.isNullOrEmpty()) "/flutter_screen_recording_${System.currentTimeMillis()}.mp4" else "/$videoName.mp4")

            mMediaRecorder?.apply {
                if (recordAudio == true) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                if (recordAudio == true) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                }
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setOutputFile(mFileName)
                setVideoSize(mDisplayWidth, mDisplayHeight)
                setVideoEncodingBitRate(512 * 1000)
                setVideoFrameRate(30)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error starting screen recording: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun stopRecordScreen() {
        try {
            mMediaRecorder?.stop()
            mMediaRecorder?.reset()

            if (mVirtualDisplay != null) {
                mVirtualDisplay?.release()
                mVirtualDisplay = null
            }

            if (mMediaProjection != null) {
                if (mMediaProjectionCallback != null) {
                    mMediaProjection?.unregisterCallback(mMediaProjectionCallback as MediaProjection.Callback)
                }
                mMediaProjection?.stop()
                mMediaProjection = null
            }

            mMediaRecorder?.release()
            mMediaRecorder = null
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error stopping screen recording: ${e.message}")
        }
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        return mMediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            mDisplayWidth, mDisplayHeight, mScreenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mMediaRecorder?.surface, null, null
        )
    }

    private fun calculateResolution(metrics: DisplayMetrics) {
        mDisplayHeight = metrics.heightPixels
        mDisplayWidth = metrics.widthPixels
    }

    // Screen sharing methods
    private fun startScreenShareCapture(resultCode: Int, data: Intent) {

     //start forground service for screen sharing
        ForegroundService.startService(pluginBinding!!.applicationContext, mTitle, mMessage,true)

        // Initialize screen sharing components
        mScreenShareThread = HandlerThread("ScreenCaptureThread")
        mScreenShareThread?.start()
        mScreenShareHandler = Handler(mScreenShareThread!!.looper)

        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data)

        // Set up screen share parameters
        // Note: This is a simplified version - you'll need to implement actual sharing logic

        isScreenSharing = true
        mScreenShareCallback?.success(true)
    }

    private fun stopScreenShare() {
        isScreenSharing = false

        if (mScreenShareVirtualDisplay != null) {
            mScreenShareVirtualDisplay?.release()
            mScreenShareVirtualDisplay = null
        }

        if (mImageReader != null) {
            mImageReader?.close()
            mImageReader = null
        }

        if (mScreenShareThread != null) {
            mScreenShareThread?.quit()
            mScreenShareThread = null
        }
    }

    // Cast methods
    private fun discoverCastDevices(result: Result) {
//        val context = pluginBinding!!.applicationContext
//
//        Log.d("ScreenRecordingPlugin", "Starting cast device discovery")
//        // Clean up any existing callback
//        cleanUpCastDiscovery()
//
//        // Set up media route selector for discovery
//        val selector = MediaRouteSelector.Builder()
//            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
//            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
//            // .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_DISPLAY)
//            .build()
//
//        castCallback = object : MediaRouter.Callback() {
//            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
//                Log.d("ScreenRecordingPlugin", "Device found: ${route.name}")
//                // Send discovered device to Flutter
//                val deviceInfo = mapOf(
//                    "id" to route.id,
//                    "name" to route.name,
//                    "description" to (route.description ?: "")
//                )
//
//                // Use method channel to send event
//                methodChannel.invokeMethod("onDeviceDiscovered", deviceInfo)
//            }
//        }
//
//        mediaRouter?.addCallback(
//            selector,
//            castCallback!!,
//            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
//        )
//
//        result.success(true)
        try {
            Log.d("ScreenRecordingPlugin", "Starting Cast device discovery using MediaRouter")

            // Clean up any existing callback first
            cleanUpCastDiscovery()

            // Set up media route selector for discovery
            val selector = MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build()

            castCallback = object : MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    if (!route.isDefault) {
                        Log.d("ScreenRecordingPlugin", "Cast device found: ${route.name}")
                        val deviceInfo = mapOf(
                            "id" to route.id,
                            "name" to route.name,
                            "description" to (route.description ?: "Cast Device")
                        )
                        // Send discovered device to Flutter via method channel
                        methodChannel.invokeMethod("onDeviceDiscovered", deviceInfo)
                    }
                }

                override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    Log.d("ScreenRecordingPlugin", "Cast device removed: ${route.name}")
                    val deviceInfo = mapOf(
                        "id" to route.id,
                        "name" to route.name,
                        "action" to "removed"
                    )
                    methodChannel.invokeMethod("onDeviceRemoved", deviceInfo)
                }
            }

            mediaRouter?.addCallback(
                selector,
                castCallback!!,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )

            // Return success boolean to indicate discovery started
            result.success(true)

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error starting cast device discovery: ${e.message}")
            result.error("DISCOVERY_ERROR", e.message, null)
        }
    }

    private fun cleanUpCastDiscovery() {
        if (castCallback != null) {
            mediaRouter?.removeCallback(castCallback!!)
            castCallback = null
        }
    }

    private fun connectToCastDevice(deviceId: String, result: Result) {
//        // Get the route info for the selected device
//        val route = mediaRouter?.routes?.find { it.id == deviceId }
//
//        if (route != null) {
//            mediaRouter?.selectRoute(route)
//            result.success(true)
//        } else {
//            result.error("DEVICE_NOT_FOUND", "Cast device not found", null)
//        }
        try {
            // Find the route by ID
            val route = mediaRouter?.routes?.find { it.id == deviceId }

            if (route != null) {
                mediaRouter?.selectRoute(route)

                // Wait for Cast session to be established
                val handler = Handler()
                handler.postDelayed({
                    currentCastSession = castContext?.sessionManager?.currentCastSession
                    if (currentCastSession?.isConnected == true) {
                        result.success(true)
                    } else {
                        result.error("CONNECTION_FAILED", "Failed to establish cast session", null)
                    }
                }, 2000) // Wait 2 seconds for connection

            } else {
                result.error("DEVICE_NOT_FOUND", "Cast device not found", null)
            }
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error connecting to cast device: ${e.message}")
            result.error("CONNECTION_ERROR", e.message, null)
        }
    }



/////////////////////////////////////////// new method for without tv application ///////////////////////////////////////

    // New method to start actual mirroring:
    private fun startActualMirroring(result: Result) {
        try {
            Log.d("ScreenRecordingPlugin", "=== START ACTUAL MIRRORING ===")
            Log.d("ScreenRecordingPlugin", "Result callback: $result")
            Log.d("ScreenRecordingPlugin", "MediaProjection: $mMediaProjection")
            Log.d("ScreenRecordingPlugin", "Cast session: ${currentCastSession?.castDevice?.friendlyName}")
            Log.d("ScreenRecordingPlugin", "Screen resolution: ${mDisplayWidth}x${mDisplayHeight}")
            Log.d("ScreenRecordingPlugin", "Screen density: $mScreenDensity")

            // CRITICAL CHECK: Ensure we have MediaProjection
            if (mMediaProjection == null) {
                Log.e("ScreenRecordingPlugin", "❌ MediaProjection is null! Cannot start mirroring.")
                result.error("NO_MEDIA_PROJECTION", "MediaProjection is null", null)
                return
            }

            // CRITICAL CHECK: Ensure we have active cast session
            if (currentCastSession == null || !currentCastSession!!.isConnected) {
                Log.e("ScreenRecordingPlugin", "❌ No active cast session! Cannot start mirroring.")
                result.error("NO_CAST_SESSION", "No active cast session", null)
                return
            }

            // ENHANCED DEBUGGING: Check for presentation display
            val currentRoute = mediaRouter?.selectedRoute
            Log.d("ScreenRecordingPlugin", "🛤️ Current route: ${currentRoute?.name}")
            Log.d("ScreenRecordingPlugin", "🛤️ Route ID: ${currentRoute?.id}")
            Log.d("ScreenRecordingPlugin", "🛤️ Route description: ${currentRoute?.description}")
            Log.d("ScreenRecordingPlugin", "🛤️ Route is default: ${currentRoute?.isDefault}")

            val presentationDisplay = currentRoute?.presentationDisplay
            Log.d("ScreenRecordingPlugin", "📺 Presentation display: $presentationDisplay")

            if (presentationDisplay != null) {
                Log.d("ScreenRecordingPlugin", "📺 Method 1: Using presentation display")
                Log.d("ScreenRecordingPlugin", "Display name: ${presentationDisplay.name}")
                Log.d("ScreenRecordingPlugin", "Display size: ${presentationDisplay.width}x${presentationDisplay.height}")

                startMirroringWithPresentation(presentationDisplay, result)
            } else {
                Log.d("ScreenRecordingPlugin", "📱 Method 2: Using simple virtual display")
                Log.d("ScreenRecordingPlugin", "🔍 Reason: No presentation display found on current route")
                startMirroringWithVirtualDisplay(result)
            }

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "❌ Error starting actual mirroring: ${e.message}")
            e.printStackTrace()
            result.error("MIRRORING_ERROR", e.message, null)
        }
    }

    // Method 1: Mirror with presentation display
    private fun startMirroringWithPresentation(display: Display, result: Result) {
        try {
            Log.d("ScreenRecordingPlugin", "🖥️ Creating BIG OBVIOUS presentation for display: ${display.name}")
            Log.d("ScreenRecordingPlugin", "📱 Context: ${pluginBinding!!.applicationContext}")
            Log.d("ScreenRecordingPlugin", "📺 Display valid: ${display.isValid}")

            // CRITICAL FIX: Ensure MediaProjection callback is registered
            if (mMediaProjectionCallback == null) {
                Log.d("ScreenRecordingPlugin", "📋 Creating and registering MediaProjection callback for presentation...")
                mMediaProjectionCallback = MediaProjectionCallback()
                mMediaProjection?.registerCallback(mMediaProjectionCallback as MediaProjection.Callback, null)
            }

            // Create an OBVIOUS presentation that you definitely can't miss
            val presentation = SuperObviousPresentation(
                pluginBinding!!.applicationContext,
                display
            )
            currentPresentation = presentation

            Log.d("ScreenRecordingPlugin", "🎭 Showing presentation...")
            currentPresentation?.show()

            Log.d("ScreenRecordingPlugin", "✅ SUPER OBVIOUS Presentation created and shown")

            // Give presentation time to show up
            android.os.Handler().postDelayed({
                try {
                    // Get display metrics
                    val displayMetrics = DisplayMetrics()
                    display.getMetrics(displayMetrics)

                    Log.d("ScreenRecordingPlugin", "📏 Display: ${display.width}x${display.height}, density: ${displayMetrics.densityDpi}")

                    // Check if surface is ready
                    val surface = presentation.getSurface()
                    Log.d("ScreenRecordingPlugin", "🏄 Surface ready: ${surface != null}")

                    // Create virtual display
                    Log.d("ScreenRecordingPlugin", "🎭 Creating virtual display for presentation...")
                    castVirtualDisplay = mMediaProjection?.createVirtualDisplay(
                        "PresentationMirror",
                        display.width,
                        display.height,
                        displayMetrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        surface,
                        object : VirtualDisplay.Callback() {
                            override fun onPaused() {
                                Log.d("ScreenRecordingPlugin", "🔵 Virtual display paused")
                            }

                            override fun onResumed() {
                                Log.d("ScreenRecordingPlugin", "🟢 Virtual display resumed")
                            }

                            override fun onStopped() {
                                Log.d("ScreenRecordingPlugin", "🔴 Virtual display stopped")
                                isCasting = false
                            }
                        },
                        null
                    )

                    if (castVirtualDisplay != null) {
                        Log.d("ScreenRecordingPlugin", "✅ Virtual display created successfully!")
                        Log.d("ScreenRecordingPlugin", "📺 Virtual display ID: ${castVirtualDisplay!!.display?.displayId}")
                        isCasting = true
                        result.success(true)
                    } else {
                        Log.e("ScreenRecordingPlugin", "❌ Failed to create virtual display")
                        result.error("VIRTUAL_DISPLAY_FAILED", "Failed to create virtual display", null)
                    }

                } catch (e: Exception) {
                    Log.e("ScreenRecordingPlugin", "❌ Error creating virtual display: ${e.message}")
                    e.printStackTrace()
                    result.error("VIRTUAL_DISPLAY_ERROR", e.message, null)
                }
            }, 500)

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "❌ Error in presentation mirroring: ${e.message}")
            e.printStackTrace()
            result.error("PRESENTATION_ERROR", e.message, null)
        }
    }

    // Simple presentation class that definitely works:
    private inner class SimpleCastPresentation(context: Context, display: Display) : Presentation(context, display) {

        private var surfaceView: android.view.SurfaceView? = null
        private var textView: android.widget.TextView? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            Log.d("ScreenRecordingPlugin", "🖥️ Creating SimpleCastPresentation")

            try {
                // Create main container
                val frameLayout = FrameLayout(context)
                frameLayout.setBackgroundColor(android.graphics.Color.BLACK)

                // Create surface view for mirroring
                surfaceView = android.view.SurfaceView(context)
                val surfaceParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                frameLayout.addView(surfaceView, surfaceParams)

                // Add debug text overlay
                textView = android.widget.TextView(context)
                textView?.text = "🎬 Screen Mirroring Active\n📱➡️📺"
                textView?.setTextColor(android.graphics.Color.WHITE)
                textView?.textSize = 20f
                textView?.gravity = android.view.Gravity.CENTER
                textView?.setBackgroundColor(android.graphics.Color.argb(150, 0, 0, 0))
                textView?.setPadding(30, 20, 30, 20)

                val textParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                textParams.gravity = android.view.Gravity.CENTER
                frameLayout.addView(textView, textParams)

                setContentView(frameLayout)

                // Make it full screen
                window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                Log.d("ScreenRecordingPlugin", "✅ SimpleCastPresentation created successfully")

            } catch (e: Exception) {
                Log.e("ScreenRecordingPlugin", "❌ Error creating SimpleCastPresentation: ${e.message}")
                e.printStackTrace()
            }
        }

        fun getSurface(): android.view.Surface? {
            return surfaceView?.holder?.surface
        }

        override fun onDisplayRemoved() {
            super.onDisplayRemoved()
            Log.d("ScreenRecordingPlugin", "📺 SimpleCastPresentation display removed")
            isCasting = false
        }
    }


    private inner class SuperObviousPresentation(context: Context, display: Display) : Presentation(context, display) {

        private var surfaceView: android.view.SurfaceView? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            Log.d("ScreenRecordingPlugin", "🖥️ Creating SUPER OBVIOUS presentation")

            try {
                // Create main container with BRIGHT COLOR
                val frameLayout = FrameLayout(context)
                frameLayout.setBackgroundColor(android.graphics.Color.MAGENTA) // BRIGHT MAGENTA!

                // Create surface view for mirroring
                surfaceView = android.view.SurfaceView(context)
                val surfaceParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

                // Add surface callback for debugging
                surfaceView?.holder?.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                        Log.d("ScreenRecordingPlugin", "✅ SUPER OBVIOUS Surface created!")
                    }

                    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                        Log.d("ScreenRecordingPlugin", "📏 SUPER OBVIOUS Surface: ${width}x${height}")
                    }

                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                        Log.d("ScreenRecordingPlugin", "🔴 SUPER OBVIOUS Surface destroyed")
                    }
                })

                frameLayout.addView(surfaceView, surfaceParams)

                // Add HUGE obvious text
                val textView = android.widget.TextView(context)
                textView.text = "📱➡️📺\nCAST IS WORKING!\nYOU SHOULD SEE\nYOUR PHONE SCREEN\nHERE SOON!"
                textView.setTextColor(android.graphics.Color.WHITE)
                textView.textSize = 64f  // HUGE TEXT!
                textView.gravity = android.view.Gravity.CENTER
                textView.setBackgroundColor(android.graphics.Color.RED) // RED BACKGROUND!
                textView.setPadding(50, 50, 50, 50)

                val textParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                textParams.gravity = android.view.Gravity.CENTER
                frameLayout.addView(textView, textParams)

                setContentView(frameLayout)

                window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                Log.d("ScreenRecordingPlugin", "✅ SUPER OBVIOUS presentation setup complete")

            } catch (e: Exception) {
                Log.e("ScreenRecordingPlugin", "❌ Error creating SUPER OBVIOUS presentation: ${e.message}")
                e.printStackTrace()
            }
        }

        fun getSurface(): android.view.Surface? {
            return surfaceView?.holder?.surface
        }

        override fun onDisplayRemoved() {
            super.onDisplayRemoved()
            Log.d("ScreenRecordingPlugin", "📺 SUPER OBVIOUS presentation removed")
            isCasting = false
        }
    }

// STEP 4: Alternative - if presentation still doesn't work, try pure virtual display:

    private fun startMirroringWithVirtualDisplay(result: Result) {
        try {
            Log.d("ScreenRecordingPlugin", "📱 Creating PURE virtual display (no presentation)")

            // CRITICAL FIX: Ensure MediaProjection callback is registered
            if (mMediaProjectionCallback == null) {
                Log.d("ScreenRecordingPlugin", "📋 Creating and registering MediaProjection callback...")
                mMediaProjectionCallback = MediaProjectionCallback()
                mMediaProjection?.registerCallback(mMediaProjectionCallback as MediaProjection.Callback, null)
            }

            // Create virtual display that should automatically mirror to cast device
            Log.d("ScreenRecordingPlugin", "🎭 Creating virtual display with flags...")
            castVirtualDisplay = mMediaProjection?.createVirtualDisplay(
                "DirectCastMirror",
                mDisplayWidth,
                mDisplayHeight,
                mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                null, // No specific surface - let Cast handle it
                object : VirtualDisplay.Callback() {
                    override fun onPaused() {
                        Log.d("ScreenRecordingPlugin", "🔵 Pure virtual display paused")
                    }

                    override fun onResumed() {
                        Log.d("ScreenRecordingPlugin", "🟢 Pure virtual display resumed")
                    }

                    override fun onStopped() {
                        Log.d("ScreenRecordingPlugin", "🔴 Pure virtual display stopped")
                        isCasting = false
                    }
                },
                null
            )

            if (castVirtualDisplay != null) {
                Log.d("ScreenRecordingPlugin", "✅ Pure virtual display created!")
                Log.d("ScreenRecordingPlugin", "📺 Display ID: ${castVirtualDisplay!!.display?.displayId}")
                Log.d("ScreenRecordingPlugin", "📺 Display Name: ${castVirtualDisplay!!.display?.name}")

                isCasting = true
                result.success(true)
            } else {
                Log.e("ScreenRecordingPlugin", "❌ Failed to create pure virtual display")
                result.error("PURE_VIRTUAL_DISPLAY_FAILED", "Failed to create pure virtual display", null)
            }

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "❌ Error in pure virtual display: ${e.message}")
            e.printStackTrace()
            result.error("PURE_MIRROR_ERROR", e.message, null)
        }
    }
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    // Add this new method to start casting once permission is granted:
    private fun startCastingWithPermission(result: Result) {
        try {
            Log.d("ScreenRecordingPlugin", "=== START CASTING WITH PERMISSION ===")
            Log.d("ScreenRecordingPlugin", "MediaProjection available: $mMediaProjection")
            Log.d("ScreenRecordingPlugin", "Cast session: ${currentCastSession?.castDevice?.friendlyName}")

            // Step 1: Initialize casting thread
            Log.d("ScreenRecordingPlugin", "🧵 Setting up casting thread...")
            castThread = HandlerThread("CastingThread")
            castThread?.start()
            castHandler = Handler(castThread!!.looper)
            Log.d("ScreenRecordingPlugin", "✅ Casting thread created")

            // Step 2: Set up screen capture
            Log.d("ScreenRecordingPlugin", "📱 Setting up screen capture...")
            setupScreenCastCapture()

            // Step 3: Set up presentation display (CRITICAL!)
            Log.d("ScreenRecordingPlugin", "🖥️ Setting up presentation display...")
            setupPresentationDisplay()

            // Step 4: Set up Cast channel
            Log.d("ScreenRecordingPlugin", "📡 Setting up cast channel...")
            setupCastChannel()

            // Step 5: Start streaming
            Log.d("ScreenRecordingPlugin", "🎬 Starting screen streaming...")
            isCasting = true

            Log.d("ScreenRecordingPlugin", "✅ Casting started successfully!")
            result.success(true)

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "❌ Error in startCastingWithPermission: ${e.message}")
            e.printStackTrace()
            result.error("CAST_START_ERROR", e.message, null)
        }
    }

    // Add this method to set up screen capture for casting:
    private fun setupScreenCastCapture() {
        try {
            val context = pluginBinding!!.applicationContext
            Log.d("ScreenRecordingPlugin", "Creating ImageReader: ${mDisplayWidth}x${mDisplayHeight}")
            // Create ImageReader for screen capture
            castImageReader = ImageReader.newInstance(
                mDisplayWidth,
                mDisplayHeight,
                PixelFormat.RGBA_8888,
                2
            )

            // Set up image processing
            castImageReader?.setOnImageAvailableListener({ reader ->
                Log.d("ScreenRecordingPlugin", "📸 Image available for casting")
                val image = reader.acquireLatestImage()
                image?.let {
                    castHandler?.post {
                        processAndSendScreenFrame(it)
                    }
                    it.close()
                }
            }, castHandler)

            Log.d("ScreenRecordingPlugin", "Creating virtual display for casting...")
            // Create virtual display for screen casting
            castVirtualDisplay = mMediaProjection?.createVirtualDisplay(
                "ScreenCast",
                mDisplayWidth,
                mDisplayHeight,
                mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                castImageReader?.surface,
                object : VirtualDisplay.Callback() {
                    override fun onPaused() {
                        Log.d("ScreenRecordingPlugin", "Cast display paused")
                    }

                    override fun onResumed() {
                        Log.d("ScreenRecordingPlugin", "Cast display resumed")
                    }

                    override fun onStopped() {
                        Log.d("ScreenRecordingPlugin", "Cast display stopped")
                    }
                },
                castHandler
            )
            Log.d("ScreenRecordingPlugin", "✅ Screen cast capture setup complete")


        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error setting up cast capture: ${e.message}")
        }
    }

    // Add this method to set up presentation display:
    private fun setupPresentationDisplay() {
        try {
            val castDevice = currentCastSession?.castDevice
            if (castDevice != null) {
                val display = getPresentationDisplay(castDevice)
                if (display != null) {
                    currentPresentation = ScreenMirrorPresentation(
                        pluginBinding!!.applicationContext,
                        display
                    )
                    currentPresentation?.show()
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error setting up presentation: ${e.message}")
        }
    }

    // Add this method to get presentation display:
    private fun getPresentationDisplay(castDevice: CastDevice): Display? {
        try {
            val context = pluginBinding!!.applicationContext
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            return displayManager.displays.firstOrNull { display ->
                display.name.contains(castDevice.friendlyName, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error getting presentation display: ${e.message}")
            return null
        }
    }

    // Add this method to set up Cast channel:
    private fun setupCastChannel() {
        try {
            currentCastSession?.setMessageReceivedCallbacks(
                "urn:x-cast:com.yourapp.screencast",
                Cast.MessageReceivedCallback { castDevice, namespace, message ->
                    Log.d("ScreenRecordingPlugin", "Received cast message: $message")
                    // Handle responses from receiver if needed
                }
            )
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error setting up cast channel: ${e.message}")
        }
    }

    // Add this method to process and send screen frames:
    private fun processAndSendScreenFrame(image: Image) {
        try {
            Log.d("ScreenRecordingPlugin", "📷 Processing screen frame...")

            // Convert image to bitmap
            val bitmap = imageToBitmap(image)
            Log.d("ScreenRecordingPlugin", "Bitmap created: ${bitmap.width}x${bitmap.height}")

            // Compress bitmap
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val imageData = outputStream.toByteArray()
            Log.d("ScreenRecordingPlugin", "Image compressed: ${imageData.size} bytes")

            // Send to Cast device
            sendImageToCastDevice(imageData)

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "❌ Error processing screen frame: ${e.message}")
            e.printStackTrace()
        }
    }

    // Add this method to convert Image to Bitmap:
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
    }

    // Add this method to send image data to Cast device:
    private fun sendImageToCastDevice(imageData: ByteArray) {
        try {
            Log.d("ScreenRecordingPlugin", "📡 Sending image to cast device...")

            val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)
            val message = """{"type":"screen_frame","data":"$base64Image","timestamp":${System.currentTimeMillis()}}"""

            Log.d("ScreenRecordingPlugin", "Message size: ${message.length} characters")

            currentCastSession?.sendMessage("urn:x-cast:com.yourapp.screencast", message)
                ?.setResultCallback { result ->
                    if (result.status.isSuccess) {
                        Log.d("ScreenRecordingPlugin", "✅ Screen data sent successfully")
                    } else {
                        Log.e("ScreenRecordingPlugin", "❌ Failed to send screen data: ${result.status}")
                    }
                }
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "❌ Error sending screen data: ${e.message}")
            e.printStackTrace()
        }
    }

    // Update your stopCasting method:
    private fun stopCasting(result: Result) {
        try {
            stopScreenCasting()

            // End cast session
            sessionManager?.endCurrentSession(true)

            isCasting = false
            result.success(true)

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error stopping cast: ${e.message}")
            result.error("STOP_CAST_ERROR", e.message, null)
        }
    }

    // Add this method to stop screen casting:
    private fun stopScreenCasting() {
        try {
            // Clean up virtual display
            castVirtualDisplay?.release()
            castVirtualDisplay = null

            // Clean up image reader
            castImageReader?.close()
            castImageReader = null

            // Clean up presentation
            currentPresentation?.dismiss()
            currentPresentation = null

            // Clean up casting thread
            castThread?.quit()
            castThread = null

            Log.d("ScreenRecordingPlugin", "Screen casting stopped")

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error stopping screen casting: ${e.message}")
        }
    }

    // Add this presentation class for screen mirroring:
    private inner class ScreenMirrorPresentation(context: Context, display: Display) : Presentation(context, display) {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val frameLayout = FrameLayout(context)
            frameLayout.setBackgroundColor(android.graphics.Color.BLACK)

            // Add a simple view - you can customize this
            val textView = android.widget.TextView(context)
            textView.text = "Screen is being cast..."
            textView.setTextColor(android.graphics.Color.WHITE)
            textView.textSize = 24f
            textView.gravity = android.view.Gravity.CENTER

            frameLayout.addView(textView)
            setContentView(frameLayout)

            Log.d("ScreenRecordingPlugin", "Screen mirror presentation created")
        }

        override fun onDisplayRemoved() {
            super.onDisplayRemoved()
            Log.d("ScreenRecordingPlugin", "Presentation display removed")
        }
    }

    // Update your onDetachedFromActivity method:
    override fun onDetachedFromActivity() {
        // Clean up cast resources
        stopScreenCasting()
        sessionManagerListener?.let {
            sessionManager?.removeSessionManagerListener(it, CastSession::class.java)
        }
        sessionManagerListener = null
        currentCastSession = null

        // Clean up cast discovery
        cleanUpCastDiscovery()

        // Clean up activity result listener
        activityBinding?.removeActivityResultListener(this)
        activityBinding = null
    }

//    private fun stopCasting(result: Result) {
//        // Stop casting - implementation will depend on your specific requirements
//        // This is a simplified placeholder
//        isCasting = false
//
//        // Unselect the current route to disconnect
//        mediaRouter?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
//
//        result.success(true)
//    }

    private fun getLocalIpAddress(): String {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.getHostAddress() ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error getting IP address: ${e.message}")
        }
        return "127.0.0.1"
    }

    private fun checkCastDeviceCapabilities(result: Result) {
        try {
            Log.d("ScreenRecordingPlugin", "🔍 Checking Cast device capabilities...")

            val castDevice = currentCastSession?.castDevice
            if (castDevice != null) {
                Log.d("ScreenRecordingPlugin", "📺 Cast Device Info:")
                Log.d("ScreenRecordingPlugin", "  Name: ${castDevice.friendlyName}")
                Log.d("ScreenRecordingPlugin", "  Model: ${castDevice.modelName}")
                Log.d("ScreenRecordingPlugin", "  Device ID: ${castDevice.deviceId}")
                Log.d("ScreenRecordingPlugin", "  Device version: ${castDevice.deviceVersion}")
//                Log.d("ScreenRecordingPlugin", "  Capabilities: ${castDevice.capabilities}")

                // Check supported namespaces/apps
//                val supportedNamespaces = castDevice.supportedNamespaces
//                Log.d("ScreenRecordingPlugin", "  Supported namespaces: $supportedNamespaces")

                // Check if it's a "Default Media Receiver" vs screen mirroring capable
                val currentRoute = mediaRouter?.selectedRoute
                if (currentRoute != null) {
                    Log.d("ScreenRecordingPlugin", "📱 Route capabilities:")
                    Log.d("ScreenRecordingPlugin", "  Description: ${currentRoute.description}")
                    Log.d("ScreenRecordingPlugin", "  Connection state: ${currentRoute.connectionState}")
                    Log.d("ScreenRecordingPlugin", "  Device type: ${currentRoute.deviceType}")
                    Log.d("ScreenRecordingPlugin", "  Volume handling: ${currentRoute.volumeHandling}")
                    Log.d("ScreenRecordingPlugin", "  Presentation display: ${currentRoute.presentationDisplay}")

                    // Check control categories
                    val controlCategories = currentRoute.controlFilters
                    Log.d("ScreenRecordingPlugin", "  Control categories: $controlCategories")
                }

                val deviceInfo = mapOf(
                    "name" to castDevice.friendlyName,
                    "model" to castDevice.modelName,
                    "deviceId" to castDevice.deviceId,
                    "version" to castDevice.deviceVersion,
//                    "capabilities" to castDevice.capabilities.toString(),
                    "supportsPresentationDisplay" to (currentRoute?.presentationDisplay != null),
                    "routeDescription" to (currentRoute?.description ?: "Unknown")
                )

                result.success(deviceInfo)

            } else {
                Log.e("ScreenRecordingPlugin", "❌ No cast device found")
                result.error("NO_CAST_DEVICE", "No cast device connected", null)
            }

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "❌ Error checking capabilities: ${e.message}")
            e.printStackTrace()
            result.error("CAPABILITY_CHECK_ERROR", e.message, null)
        }
    }

}