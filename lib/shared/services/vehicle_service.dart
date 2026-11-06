import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

class VehicleService {
  VehicleService();

  static const MethodChannel _channel = MethodChannel(
    'com.vemoring/pleos_vehicle',
  );
  static const EventChannel _events = EventChannel(
    'com.vemoring/pleos_vehicle/events',
  );

  StreamSubscription<dynamic>? _subscription;
  final _batteryController = StreamController<int>.broadcast();
  Stream<int> get batteryUpdates => _batteryController.stream;

  Future<Map<String, dynamic>> initialize() async {
    if (!Platform.isAndroid) {
      return _status(
        'unavailable',
        'Pleos SDK는 Android Automotive 환경에서만 사용할 수 있어요.',
      );
    }
    try {
      final result = await _channel.invokeMethod<Object>('initialize');
      final map = result is Map
          ? Map<String, dynamic>.from(result)
          : <String, dynamic>{};
      return map;
    } on MissingPluginException {
      return _status('unavailable', 'Pleos Vehicle SDK가 이 앱 빌드에 연결되지 않았어요.');
    } catch (error) {
      return _status('connection_error', error.toString());
    }
  }

  Future<Map<String, dynamic>> getBattery() async {
    if (!Platform.isAndroid) {
      return _status(
        'unavailable',
        'Pleos SDK는 Android Automotive 환경에서만 사용할 수 있어요.',
      );
    }
    try {
      final result = await _channel.invokeMethod<Object>(
        'getEvBatteryStateOfCharge',
      );
      if (result is Map) return Map<String, dynamic>.from(result);
      return _status('no_data', '배터리 잔량을 받지 못했어요.');
    } on MissingPluginException {
      return _status('unavailable', 'Pleos Vehicle SDK 채널을 사용할 수 없어요.');
    } catch (error) {
      return _status('connection_error', error.toString());
    }
  }

  Future<Map<String, dynamic>> getDriverDoorPosition() async {
    if (!Platform.isAndroid) {
      return _status(
        'unavailable',
        'Pleos SDK는 Android Automotive 환경에서만 사용할 수 있어요.',
      );
    }
    try {
      final result = await _channel.invokeMethod<Object>(
        'getDriverDoorPosition',
      );
      if (result is Map) return Map<String, dynamic>.from(result);
      return _status('no_data', '운전석 문 상태를 받지 못했어요.');
    } on MissingPluginException {
      return _status('unavailable', 'Pleos Vehicle SDK 채널을 사용할 수 없어요.');
    } catch (error) {
      return _status('connection_error', error.toString());
    }
  }

  void startBatteryUpdates() {
    _subscription ??= _events.receiveBroadcastStream().listen((event) {
      if (event is Map && event['value'] is num) {
        _batteryController.add((event['value'] as num).toInt());
      }
    }, onError: (_) {});
  }

  Future<void> release() async {
    await _subscription?.cancel();
    _subscription = null;
    await _batteryController.close();
    if (Platform.isAndroid) {
      try {
        await _channel.invokeMethod<void>('release');
      } on MissingPluginException {
        // The platform channel is optional outside a Pleos build.
      } catch (_) {}
    }
  }

  Map<String, dynamic> _status(String status, String message) => {
    'status': status,
    'message': message,
  };
}
