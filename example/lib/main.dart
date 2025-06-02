import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_screen_recording/flutter_screen_recording.dart';
import 'package:flutter_screen_recording_platform_interface/flutter_screen_recording_platform_interface.dart';
import 'package:quiver/async.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:open_file/open_file.dart';



// Import your casting widget classes or define them directly
// class CastDevice {
//   final String id;
//   final String name;
//   final String description;
//
//   CastDevice({
//     required this.id,
//     required this.name,
//     required this.description,
//   });
//
//   factory CastDevice.fromMap(Map<String, dynamic> map) {
//     return CastDevice(
//       id: map['id'] ?? '',
//       name: map['name'] ?? '',
//       description: map['description'] ?? '',
//     );
//   }
// }

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  bool recording = false;
  int _time = 0;
  List<CastDevice> _discoveredDevices = [];
  CastDevice? _connectedDevice;
  bool _isCasting = false;
  StreamSubscription? _deviceSubscription;


  requestPermissions() async {
    if (!kIsWeb) {
      if (await Permission.notification.isDenied) {
        await Permission.notification.request();
      }
      if (await Permission.microphone.request().isDenied) {
        await Permission.microphone.request();
      }
    }
  }

  @override
  void initState() {
    super.initState();
    requestPermissions();
    startTimer();
    _setupDeviceListener();
  }

  void _setupDeviceListener() {
    _deviceSubscription = FlutterScreenRecording.onDeviceDiscovered.listen((device) {
      setState(() {
        // Check if device already exists in the list
        if (!_discoveredDevices.any((d) => d.id == device.id)) {
          _discoveredDevices.add(device as CastDevice);
        }
      });
    });
  }

  void startTimer() {
    CountdownTimer countDownTimer = CountdownTimer(
      Duration(seconds: 1000),
      Duration(seconds: 1),
    );

    var sub = countDownTimer.listen(null);
    sub.onData((duration) {
      setState(() => _time++);
    });

    sub.onDone(() {
      print("Done");
      sub.cancel();
    });
  }

  Future<void> _discoverDevices() async {
    setState(() {
      _discoveredDevices.clear();
    });
    await FlutterScreenRecording.discoverCastDevices();
  }

  Future<void> _connectToDevice(CastDevice device) async {
    final success = await FlutterScreenRecording.connectToCastDevice(device.id);
    if (success) {
      setState(() {
        _connectedDevice = device;
      });
    }
  }

  Future<void> _startCasting() async {
    final success = await FlutterScreenRecording.startCasting();
    if (success) {
      setState(() {
        _isCasting = true;
      });
    }
  }

  Future<void> _stopCasting() async {
    final success = await FlutterScreenRecording.stopCasting();
    if (success) {
      setState(() {
        _isCasting = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Flutter Screen Recording'),
        ),
        body: SingleChildScrollView(
          child: Padding(
            padding: EdgeInsets.all(16.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                Text('Time: $_time\n'),
                // Recording controls
                Card(
                  child: Padding(
                    padding: EdgeInsets.all(12.0),
                    child: Column(
                      children: [
                        Text('Recording Controls',
                            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                        SizedBox(height: 12),
                        !recording
                            ? Column(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: [
                            ElevatedButton(
                              child: Text("Record Screen"),
                              onPressed: () => startScreenRecord(false),
                            ),
                            ElevatedButton(
                              child: Text("Record with Audio"),
                              onPressed: () => startScreenRecord(true),
                            ),
                          ],
                        )
                            : ElevatedButton(
                          child: Text("Stop Record"),
                          onPressed: () => stopScreenRecord(),
                        ),
                      ],
                    ),
                  ),
                ),

                SizedBox(height: 20),

                // Screen share controls
                Card(
                  child: Padding(
                    padding: EdgeInsets.all(12.0),
                    child: Column(
                      children: [
                        Text('Screen Share Controls',
                            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                        SizedBox(height: 12),
                        Column(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: [
                            ElevatedButton(
                              child: Text("Start Screen Share"),
                              onPressed: () => FlutterScreenRecording.startScreenShare(),
                            ),
                            ElevatedButton(
                              child: Text("Stop Screen Share"),
                              onPressed: () => FlutterScreenRecording.stopScreenShare(),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),

                SizedBox(height: 20),

                // Cast devices section
                Card(
                  child: Padding(
                    padding: EdgeInsets.all(12.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Text('Cast Devices',
                            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                        SizedBox(height: 12),
                        ElevatedButton(
                          child: Text("Discover Cast Devices"),
                          onPressed: _discoverDevices,
                        ),
                        SizedBox(height: 12),
                        _discoveredDevices.isEmpty
                            ? Padding(
                          padding: EdgeInsets.symmetric(vertical: 16.0),
                          child: Text('No devices found', textAlign: TextAlign.center),
                        )
                            : ListView.builder(
                          shrinkWrap: true,
                          physics: NeverScrollableScrollPhysics(),
                          itemCount: _discoveredDevices.length,
                          itemBuilder: (context, index) {
                            final device = _discoveredDevices[index];
                            return ListTile(
                              title: Text(device.name),
                              // subtitle: Text(device.description.isNotEmpty
                              //     ? device.description : 'No description'),
                              trailing: _connectedDevice?.id == device.id
                                  ? Icon(Icons.check_circle, color: Colors.green)
                                  : null,
                              onTap: () => _connectToDevice(device),
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                ),

                SizedBox(height: 20),

                // Casting controls
                if (_connectedDevice != null)
                  Card(
                    child: Padding(
                      padding: EdgeInsets.all(12.0),
                      child: Column(
                        children: [
                          Text('Casting Controls',
                              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                          SizedBox(height: 12),
                          Text('Connected to: ${_connectedDevice!.name}'),
                          SizedBox(height: 12),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                            children: [
                              ElevatedButton(
                                onPressed: !_isCasting ? _startCasting : null,
                                child: Text('Start Casting'),
                              ),
                              ElevatedButton(
                                onPressed: _isCasting ? _stopCasting : null,
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.red,
                                ),
                                child: Text('Stop Casting'),
                              ),
                            ],
                          ),
                          if (_isCasting)
                            Padding(
                              padding: EdgeInsets.only(top: 16),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Icon(Icons.cast, color: Colors.blue),
                                  SizedBox(width: 8),
                                  Text('Casting to ${_connectedDevice!.name}'),
                                ],
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  startScreenRecord(bool audio) async {
    bool start = false;

    if (audio) {
      start = await FlutterScreenRecording.startRecordScreenAndAudio(
        "Title",
        titleNotification: "titleNotification",
        messageNotification: "messageNotification",
      );
    } else {
      start = await FlutterScreenRecording.startRecordScreen(
        "Title",
        titleNotification: "titleNotification",
        messageNotification: "messageNotification",
      );
    }

    if (start) {
      setState(() => recording = !recording);
    }

    return start;
  }

  stopScreenRecord() async {
    String path = await FlutterScreenRecording.stopRecordScreen;
    setState(() {
      recording = !recording;
    });
    print("Opening video");
    print(path);
    OpenFile.open(path);
  }

  @override
  void dispose() {
    _deviceSubscription?.cancel();
    super.dispose();
  }
}