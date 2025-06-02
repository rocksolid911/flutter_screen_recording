 library flutter_screen_recording_platform_interface;

import 'dart:async';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'method_channel_flutter_screen_recording.dart';


abstract class FlutterScreenRecordingPlatform extends PlatformInterface {
  /// Constructs a UrlLauncherPlatform.
  FlutterScreenRecordingPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterScreenRecordingPlatform _instance =
  MethodChannelFlutterScreenRecording();

  /// The default instance of [FlutterScreenRecordingPlatform] to use.
  ///
  /// Defaults to [MethodChannelUrlLauncher].
  static FlutterScreenRecordingPlatform get instance => _instance;

  static set instance(FlutterScreenRecordingPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<bool> startRecordScreen(
      String name, {
        String notificationTitle = "",
        String notificationMessage = "",
      }) {
    throw UnimplementedError();
  }

  Future<bool> startRecordScreenAndAudio(
      String name, {
        String notificationTitle = "",
        String notificationMessage = "",
      }) {
    throw UnimplementedError();
  }

  Future<String> get stopRecordScreen {
    throw UnimplementedError();
  }

  // Add these methods to the FlutterScreenRecordingPlatform abstract class

  // Screen sharing methods
  Future<bool> startScreenShare() {
    throw UnimplementedError();
  }

  Future<bool> stopScreenShare() {
    throw UnimplementedError();
  }

  Future<bool> isScreenSharing() {
    throw UnimplementedError();
  }

  // Casting methods
  Future<bool> discoverCastDevices() {
    throw UnimplementedError();
  }

  Future<bool> connectToCastDevice(String deviceId) {
    throw UnimplementedError();
  }

  Future<bool> startCasting() {
    throw UnimplementedError();
  }

  Future<bool> stopCasting() {
    throw UnimplementedError();
  }

  Future<bool> isCasting() {
    throw UnimplementedError();
  }


  Future<String?> getLocalIpAddress(){
    throw UnimplementedError();
  }

  Future<Map<String, dynamic>?> checkCastCapabilities(){
    throw UnimplementedError();
  }

  // Optional: Add this method if you need to clean up resources
  void dispose() {
    // Default implementation is empty
  }

  // Stream for device discovery (getter only in interface)
  Stream<CastDevice> get onDeviceDiscovered;

}

 class CastDevice {
   final String id;
   final String name;
   final String model;

   CastDevice({required this.id, required this.name, required this.model});

   factory CastDevice.fromMap(Map<String, dynamic> map) {
     return CastDevice(
       id: map['id'] ?? '',
       name: map['name'] ?? '',
       model: map['model'] ?? '',
     );
   }

   Map<String, dynamic> toMap() {
     return {
       'id': id,
       'name': name,
       'model': model,
     };
   }
 }