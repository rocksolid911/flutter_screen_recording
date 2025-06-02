import 'dart:async';

import 'package:flutter/services.dart';

import 'flutter_screen_recording_platform_interface.dart';


class MethodChannelFlutterScreenRecording
    extends FlutterScreenRecordingPlatform {
  static const MethodChannel _channel =
  const MethodChannel('flutter_screen_recording');
  final EventChannel _deviceDiscoveryChannel = EventChannel('flutter_screen_recording/device_discovery');
  // Stream controller for discovered devices
  final StreamController<CastDevice> _deviceStreamController =
  StreamController<CastDevice>.broadcast();

  Stream<CastDevice> get onDeviceDiscovered => _deviceStreamController.stream;

  MethodChannelFlutterScreenRecording() {
    _setupDeviceDiscoveryListener();
  }

  void _setupDeviceDiscoveryListener() {
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onDeviceDiscovered':
          final deviceMap = Map<String, dynamic>.from(call.arguments);
          final device = CastDevice.fromMap(deviceMap);
          _deviceStreamController.add(device);
          break;
      }
    });
  }

  // Existing recording methods...
  Future<bool> startRecordScreen(
      String name, {
        String notificationTitle = "",
        String notificationMessage = "",
      }) async {
    final bool start = await _channel.invokeMethod('startRecordScreen', {
      "name": name,
      "audio": false,
      "title": notificationTitle,
      "message": notificationMessage,
    });
    return start;
  }

  Future<bool> startRecordScreenAndAudio(
      String name, {
        String notificationTitle = "",
        String notificationMessage = "",
      }) async {
    final bool start = await _channel.invokeMethod('startRecordScreen', {
      "name": name,
      "audio": true,
      "title": notificationTitle,
      "message": notificationMessage,
    });
    return start;
  }

  Future<String> get stopRecordScreen async {
    final String path = await _channel.invokeMethod('stopRecordScreen');
    return path;
  }

  // New casting methods
  Future<bool> discoverCastDevices() async {
    try {
      return await _channel.invokeMethod<bool>('discoverCastDevices') ?? false;
    } catch (e) {
      print("discoverCastDevices error: $e");
      return false;
    }
  }

  Future<bool> connectToCastDevice(String deviceId) async {
    try {
      return await _channel.invokeMethod<bool>('connectToCastDevice', {
        'deviceId': deviceId,
      }) ?? false;
    } catch (e) {
      print("connectToCastDevice error: $e");
      return false;
    }
  }

  Future<bool> startCasting() async {
    try {
      final screenShareResult = await _channel.invokeMethod('startScreenShare');
      if (screenShareResult != true) {
        print('Failed to get screen share permission');
        return false;
      }

      // Small delay to ensure permission is processed
      await Future.delayed(Duration(milliseconds: 500));
      return await _channel.invokeMethod<bool>('startCasting') ?? false;
    } catch (e) {
      print("startCasting error: $e");
      return false;
    }
  }

  Future<bool> stopCasting() async {
    try {
      return await _channel.invokeMethod<bool>('stopCasting') ?? false;
    } catch (e) {
      print("stopCasting error: $e");
      return false;
    }
  }

  // Add these methods to method_channel_flutter_screen_recording.dart
  Future<bool> startScreenShare() async {
    try {
      return await _channel.invokeMethod<bool>('startScreenShare') ?? false;
    } catch (e) {
      print("startScreenShare err\n$e");
      return false;
    }
  }

  Future<bool> stopScreenShare() async {
    try {
      return await _channel.invokeMethod<bool>('stopScreenShare') ?? false;
    } catch (e) {
      print("stopScreenShare err\n$e");
      return false;
    }
  }

  //write a method to listen devices discovery


  void dispose() {
    _deviceStreamController.close();
  }

  getChannel() {
    return _channel;
  }
}