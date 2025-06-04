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

    
    private var mScreenShareCallback: Result? = null
    private var mImageReader: ImageReader? = null
    private var mScreenShareVirtualDisplay: VirtualDisplay? = null
    private var mScreenShareHandler: Handler? = null
    private var mScreenShareThread: HandlerThread? = null
    private var isScreenSharing = false

    
    private var mediaRouter: MediaRouter? = null
    private var castCallback: MediaRouter.Callback? = null
    private var isCasting = false

    
    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var sessionManagerListener: SessionManagerListener<CastSession>? = null
    private var currentCastSession: CastSession? = null
    private var currentPresentation: ScreenMirrorPresentation? = null
    private var castVirtualDisplay: VirtualDisplay? = null
    private var castImageReader: ImageReader? = null
    private var castHandler: Handler? = null
    private var castThread: HandlerThread? = null

    
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        pluginBinding = binding
        methodChannel = MethodChannel(binding.binaryMessenger, "flutter_screen_recording")
        methodChannel.setMethodCallHandler(this)

        
        eventChannel = EventChannel(binding.binaryMessenger, "flutter_screen_recording_events")
        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                
            }

            override fun onCancel(arguments: Any?) {
                
            }
        })
    }



    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        pluginBinding = null
    }

    
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addActivityResultListener(this)

        try {
            
            mProjectionManager = ContextCompat.getSystemService(
                binding.activity.applicationContext,
                MediaProjectionManager::class.java
            ) ?: throw Exception("MediaProjectionManager not found")

            
            initializeCastContext(binding.activity.applicationContext)
            
            mediaRouter = MediaRouter.getInstance(binding.activity.applicationContext)

            
            registerForActivityResult(binding.activity)
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error initializing: ${e.message}")
        }
    }

    
    private fun initializeCastContext(context: Context) {
        try {
            castContext = CastContext.getSharedInstance(context)
            sessionManager = castContext?.sessionManager

            sessionManagerListener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    Log.d("ScreenRecordingPlugin", "Cast session started: $sessionId")
                    currentCastSession = session

                    // Log the configuration
                    logCastAppConfiguration()
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

                    
                    try {
                        startCasting(autoStartResult)
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

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }










    
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
    val context = pluginBinding!!.applicationContext

    
    if (mScreenShareCallback != null) {
        
        ForegroundService.startService(context, mTitle, mMessage, true) 
        val intentConnection = Intent(context, ForegroundService::class.java)

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    startScreenShareCapture(resultCode, data)
                } catch (e: Throwable) {
                    Log.e("ScreenRecordingPlugin", "Error: ${e.message}")
                    mScreenShareCallback?.success(false)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }

        context.bindService(
            intentConnection,
            serviceConnection!!,
            Activity.BIND_AUTO_CREATE
        )
    } else {
        
        ForegroundService.startService(context, mTitle, mMessage, true) 
        val intentConnection = Intent(context, ForegroundService::class.java)

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    startRecordScreen()
                    mMediaProjectionCallback = MediaProjectionCallback()
                    mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data)
                    mMediaProjection?.registerCallback(mMediaProjectionCallback as MediaProjection.Callback, null)
                    mVirtualDisplay = createVirtualDisplay()
                    _result.success(true)
                } catch (e: Throwable) {
                    Log.e("ScreenRecordingPlugin", "Error: ${e.message}")
                    _result.success(false)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }

        context.bindService(
            intentConnection,
            serviceConnection!!,
            Activity.BIND_AUTO_CREATE
        )
    }
}


    
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

                    startCasting(result)
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

            else -> {
                result.notImplemented()
            }
        }
    }

    
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

    
    private fun startScreenShareCapture(resultCode: Int, data: Intent) {

     
        ForegroundService.startService(pluginBinding!!.applicationContext, mTitle, mMessage,true)

        
        mScreenShareThread = HandlerThread("ScreenCaptureThread")
        mScreenShareThread?.start()
        mScreenShareHandler = Handler(mScreenShareThread!!.looper)

        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data)

        
        

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

    
    private fun discoverCastDevices(result: Result) {



































        try {
            Log.d("ScreenRecordingPlugin", "Starting Cast device discovery using MediaRouter")

            
            cleanUpCastDiscovery()

            
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









        try {
            
            val route = mediaRouter?.routes?.find { it.id == deviceId }

            if (route != null) {
                mediaRouter?.selectRoute(route)

                
                val handler = Handler()
                handler.postDelayed({
                    currentCastSession = castContext?.sessionManager?.currentCastSession
                    if (currentCastSession?.isConnected == true) {
                        result.success(true)
                    } else {
                        result.error("CONNECTION_FAILED", "Failed to establish cast session", null)
                    }
                }, 2000) 

            } else {
                result.error("DEVICE_NOT_FOUND", "Cast device not found", null)
            }
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error connecting to cast device: ${e.message}")
            result.error("CONNECTION_ERROR", e.message, null)
        }
    }




















































private fun startCasting(result: Result) {
    try {
        Log.d("ScreenRecordingPlugin", "=== START CASTING DEBUG ===")

        
        Log.d("ScreenRecordingPlugin", "Current cast session: $currentCastSession")
        Log.d("ScreenRecordingPlugin", "Cast session connected: ${currentCastSession?.isConnected}")

        if (currentCastSession == null || !currentCastSession!!.isConnected) {
            Log.e("ScreenRecordingPlugin", "❌ No active cast session")
            result.error("NO_CAST_SESSION", "No active cast session", null)
            return
        }

        Log.d("ScreenRecordingPlugin", "✅ Cast session is active")
        Log.d("ScreenRecordingPlugin", "Cast device: ${currentCastSession!!.castDevice?.friendlyName}")

        
        Log.d("ScreenRecordingPlugin", "Current MediaProjection: $mMediaProjection")

        if (mMediaProjection == null) {
            Log.d("ScreenRecordingPlugin", "📱 Requesting screen capture permission...")

            
            _result = result
            mScreenShareCallback = result

            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = activityBinding!!.activity.display
                display?.getRealMetrics(metrics)
            } else {
                @SuppressLint("NewApi")
                val defaultDisplay = pluginBinding!!.applicationContext.display
                defaultDisplay?.getMetrics(metrics)
            }
            mScreenDensity = metrics.densityDpi
            calculateResolution(metrics)

            Log.d("ScreenRecordingPlugin", "Screen resolution: ${mDisplayWidth}x${mDisplayHeight}, density: $mScreenDensity")

            
            if (startMediaProjection != null) {
                Log.d("ScreenRecordingPlugin", "Using modern ActivityResultLauncher")
                startMediaProjection!!.launch(mProjectionManager.createScreenCaptureIntent())
            } else {
                Log.d("ScreenRecordingPlugin", "Using legacy activity result")
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

        Log.d("ScreenRecordingPlugin", "✅ MediaProjection already available")
        
        startCastingWithPermission(result)

    } catch (e: Exception) {
        Log.e("ScreenRecordingPlugin", "❌ Error in startCasting: ${e.message}")
        e.printStackTrace()
        result.error("CAST_START_ERROR", e.message, null)
    }
}

    


























    private fun startCastingWithPermission(result: Result) {
        try {
            Log.d("ScreenRecordingPlugin", "=== START CASTING WITH PERMISSION ===")
            Log.d("ScreenRecordingPlugin", "MediaProjection available: $mMediaProjection")
            Log.d("ScreenRecordingPlugin", "Cast session: ${currentCastSession?.castDevice?.friendlyName}")

            
            Log.d("ScreenRecordingPlugin", "🧵 Setting up casting thread...")
            castThread = HandlerThread("CastingThread")
            castThread?.start()
            castHandler = Handler(castThread!!.looper)
            Log.d("ScreenRecordingPlugin", "✅ Casting thread created")

            
            Log.d("ScreenRecordingPlugin", "📱 Setting up screen capture...")
            setupScreenCastCapture()

            
            Log.d("ScreenRecordingPlugin", "🖥️ Setting up presentation display...")
            setupPresentationDisplay()

            
            Log.d("ScreenRecordingPlugin", "📡 Setting up cast channel...")
            setupCastChannel()

            
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

    
    private fun setupScreenCastCapture() {
        try {
            val context = pluginBinding!!.applicationContext
            Log.d("ScreenRecordingPlugin", "Creating ImageReader: ${mDisplayWidth}x${mDisplayHeight}")
            
            castImageReader = ImageReader.newInstance(
                mDisplayWidth,
                mDisplayHeight,
                PixelFormat.RGBA_8888,
                2
            )

            
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

    
    private fun setupCastChannel() {
        try {
            currentCastSession?.setMessageReceivedCallbacks(
                "urn:x-cast:com.isvisoft.flutter_screen_recording",
                Cast.MessageReceivedCallback { castDevice, namespace, message ->
                    Log.d("ScreenRecordingPlugin", "Received cast message: $message")
                    
                }
            )
        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error setting up cast channel: ${e.message}")
        }
    }

    
    private fun processAndSendScreenFrame(image: Image) {
        try {
            Log.d("ScreenRecordingPlugin", "📷 Processing screen frame...")

            
            val bitmap = imageToBitmap(image)
            Log.d("ScreenRecordingPlugin", "Bitmap created: ${bitmap.width}x${bitmap.height}")

            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val imageData = outputStream.toByteArray()
            Log.d("ScreenRecordingPlugin", "Image compressed: ${imageData.size} bytes")

            
            sendImageToCastDevice(imageData)

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "❌ Error processing screen frame: ${e.message}")
            e.printStackTrace()
        }
    }

    
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

    
    private fun stopCasting(result: Result) {
        try {
            stopScreenCasting()

            
            sessionManager?.endCurrentSession(true)

            isCasting = false
            result.success(true)

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error stopping cast: ${e.message}")
            result.error("STOP_CAST_ERROR", e.message, null)
        }
    }

    
    private fun stopScreenCasting() {
        try {
            
            castVirtualDisplay?.release()
            castVirtualDisplay = null

            
            castImageReader?.close()
            castImageReader = null

            
            currentPresentation?.dismiss()
            currentPresentation = null

            
            castThread?.quit()
            castThread = null

            Log.d("ScreenRecordingPlugin", "Screen casting stopped")

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "Error stopping screen casting: ${e.message}")
        }
    }

    
    private inner class ScreenMirrorPresentation(context: Context, display: Display) : Presentation(context, display) {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val frameLayout = FrameLayout(context)
            frameLayout.setBackgroundColor(android.graphics.Color.BLACK)

            
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

    
    override fun onDetachedFromActivity() {
        
        stopScreenCasting()
        sessionManagerListener?.let {
            sessionManager?.removeSessionManagerListener(it, CastSession::class.java)
        }
        sessionManagerListener = null
        currentCastSession = null

        
        cleanUpCastDiscovery()

        
        activityBinding?.removeActivityResultListener(this)
        activityBinding = null
    }












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

    private fun logCastAppConfiguration() {
        try {
            Log.d("ScreenRecordingPlugin", "=== CAST APP CONFIGURATION ===")

            val castOptions = CastContext.getSharedInstance(pluginBinding!!.applicationContext).castOptions
            val appId = castOptions.receiverApplicationId

            Log.d("ScreenRecordingPlugin", "📱 Configured App ID: $appId")
            Log.d("ScreenRecordingPlugin", "📱 Default Media Receiver: ${com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID}")

            if (appId == com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID) {
                Log.w("ScreenRecordingPlugin", "⚠️ Using Default Media Receiver - this won't work for custom screen casting!")
            } else {
                Log.d("ScreenRecordingPlugin", "✅ Using custom receiver app")
            }

        } catch (e: Exception) {
            Log.e("ScreenRecordingPlugin", "❌ Error checking cast configuration: ${e.message}")
        }
    }

}