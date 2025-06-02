//import 'file:D:/Workspace/flutter_screen_recording/flutter_screen_recording_platform_interface/lib/flutter_screen_recording_platform_interface.dart';
import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';


import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter_screen_recording_platform_interface/flutter_screen_recording_platform_interface.dart';



class FlutterScreenRecording {
  static Future<bool> startRecordScreen(String name, {String? titleNotification, String? messageNotification}) async {
    try {
      if (titleNotification == null) {
        titleNotification = "";
      }
      if (messageNotification == null) {
        messageNotification = "";
      }

      await _maybeStartFGS(titleNotification, messageNotification);
      final bool start = await FlutterScreenRecordingPlatform.instance.startRecordScreen(
        name,
        notificationTitle: titleNotification,
        notificationMessage: messageNotification,
      );

      return start;
    } catch (err) {
      print("startRecordScreen err");
      print(err);
    }

    return false;
  }

  static Future<bool> startRecordScreenAndAudio(String name,
      {String? titleNotification, String? messageNotification}) async {
    try {
      if (titleNotification == null) {
        titleNotification = "";
      }
      if (messageNotification == null) {
        messageNotification = "";
      }
      await _maybeStartFGS(titleNotification, messageNotification);
      final bool start = await FlutterScreenRecordingPlatform.instance.startRecordScreenAndAudio(
        name,
        notificationTitle: titleNotification,
        notificationMessage: messageNotification,
      );
      return start;
    } catch (err) {
      print("startRecordScreenAndAudio err");
      print(err);
    }
    return false;
  }

  static Future<String> get stopRecordScreen async {
    try {
      final String path = await FlutterScreenRecordingPlatform.instance.stopRecordScreen;
      if (!kIsWeb && Platform.isAndroid) {
        FlutterForegroundTask.stopService();
      }
      return path;
    } catch (err) {
      print("stopRecordScreen err");
      print(err);
    }
    return "";
  }

  static _maybeStartFGS(String titleNotification, String messageNotification) {
    try {
      if (!kIsWeb && Platform.isAndroid) {
        FlutterForegroundTask.init(
          androidNotificationOptions: AndroidNotificationOptions(
            channelId: 'notification_channel_id',
            channelName: titleNotification,
            channelDescription: messageNotification,
            channelImportance: NotificationChannelImportance.LOW,
            priority: NotificationPriority.LOW,
            // iconData: const NotificationIconData(
            //   resType: ResourceType.mipmap,
            //   resPrefix: ResourcePrefix.ic,
            //   name: 'launcher',
            // ),
          ),
          iosNotificationOptions: const IOSNotificationOptions(
            showNotification: true,
            playSound: false,
          ),
          foregroundTaskOptions: ForegroundTaskOptions(
             eventAction: ForegroundTaskEventAction.repeat(5000),
            autoRunOnBoot: true,
            autoRunOnMyPackageReplaced: true,
            allowWakeLock: true,
            allowWifiLock: true,
          ),
        );
      }
    } catch (err) {
      print("_maybeStartFGS err");
      print(err);
    }
  }

  static Future<bool> startScreenShare() async {
    try {
      await _maybeStartFGS("titleNotification","messageNotification");
      final bool start = await FlutterScreenRecordingPlatform.instance.startScreenShare();
      return start;
    } catch (err) {
      print("startScreenShare err");
      print(err);
    }
    return false;
  }

  static Future<bool>  stopScreenShare() async {
    try {
      final bool result = await FlutterScreenRecordingPlatform.instance.stopScreenShare();
      return result;
    } catch (err) {
      print("stopScreenShare err");
      print(err);
    }
    return false;
  }

  static Future<bool> discoverCastDevices() async {
    try {
      return await FlutterScreenRecordingPlatform.instance.discoverCastDevices();
    } catch (e) {
      print("discoverCastDevices error: $e");
      return false;
    }
  }

  static Future<bool> connectToCastDevice(String deviceId) async {
    try {
      return await FlutterScreenRecordingPlatform.instance.connectToCastDevice(deviceId);
    } catch (e) {
      print("connectToCastDevice error: $e");
      return false;
    }
  }

  static Future<bool> startCasting() async {
    try {
      await _maybeStartFGS("titleNotification","messageNotification");
      return await FlutterScreenRecordingPlatform.instance.startCasting();
    } catch (e) {
      print("startCasting error: $e");
      return false;
    }
  }
  static Future<bool> stopCasting() async {
    try {
      return await FlutterScreenRecordingPlatform.instance.stopCasting();
    } catch (e) {
      print("stopCasting error: $e");
      return false;
    }
  }

  static Future<bool> isCasting() async {
    try {
      return await FlutterScreenRecordingPlatform.instance.isCasting();
    } catch (e) {
      print("isCasting error: $e");
      return false;
    }
  }

  static Future<bool> isScreenSharing() async {
    try {
      return await FlutterScreenRecordingPlatform.instance.isScreenSharing();
    } catch (e) {
      print("isScreenSharing error: $e");
      return false;
    }
  }


  //method to listen devices discovery
// Update this method in flutter_screen_recording.dart
  static Stream<CastDevice> get onDeviceDiscovered {
    return FlutterScreenRecordingPlatform.instance.onDeviceDiscovered;
  }

  // Optional: Add this method if you need to clean up resources
  static void dispose() {
    FlutterScreenRecordingPlatform.instance.dispose();
  }
}


