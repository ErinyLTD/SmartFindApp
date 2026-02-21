# SmartFind

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

Find-my-phone app triggered by SMS from designated contacts.

When a trusted contact sends an SMS containing your chosen keyword, SmartFind sounds a loud alarm -- even if your phone is on silent or Do Not Disturb mode.

## Features

- **Designated contacts** -- only people you trust can trigger the alarm
- **Custom trigger keyword** -- choose your own secret word (default: FIND)
- **Bypasses silent mode and DND** -- the alarm always sounds
- **Biometric authentication** -- all settings changes require fingerprint or PIN
- **Encrypted storage** -- contacts and settings secured with Android Keystore
- **Cooldown protection** -- prevents alarm spam with configurable cooldown
- **SMS spoof detection** -- validates SMS origin to prevent abuse
- **Audit log** -- full history of alarm triggers and events
- **Device admin** -- optional uninstall protection
- **Battery-aware** -- adapts behaviour based on power save mode

## Privacy

- Zero internet connections -- operates entirely offline
- No analytics, no tracking, no cloud services
- All data stored locally on your device
- Phone numbers redacted in audit logs

## Building

```bash
./gradlew assembleDebug
```

## Running BDD Tests

All tests use [Kotest 6.1.3](https://kotest.io/) `BehaviorSpec` (Given / When / Then) and run on JUnit 5 Platform.

### Run all tests

```bash
./gradlew testDebugUnitTest
```

### Run a single spec class

```bash
./gradlew testDebugUnitTest --tests "com.smartfind.app.util.TriggerProcessorSpec"
```

### Run tests matching a pattern

```bash
./gradlew testDebugUnitTest --tests "com.smartfind.app.*Spec"
```

### View HTML report

After a test run, open:

```
app/build/reports/tests/testDebugUnitTest/index.html
```

## Test Structure

| Spec File | Type | What it covers |
|---|---|---|
| `KeywordMatcherSpec` | Pure unit | Exact keyword matching (case-sensitive, trimmed) |
| `PhoneNumberHelperSpec` | Pure unit | Number matching with suffix/formatting |
| `PhoneNumberHelperNormalizeSpec` | Robolectric | Country-code normalization via TelephonyManager |
| `SmsTriggerLogicSpec` | Pure unit | End-to-end trigger evaluation logic (guard chain + matching) |
| `TriggerProcessorSpec` | Robolectric | Production guard checks, trigger handling, body redaction |
| `AlarmLockSpec` | Pure unit | Atomic lock for concurrent alarm prevention |
| `AlarmServiceSanitizeSpec` | Pure unit | Unicode sanitization for notification spoofing prevention |
| `EventLoggerRedactSpec` | Pure unit | PII redaction and correlation ID format |
| `SettingsManagerSpec` | Robolectric | SharedPreferences: keyword, numbers, cooldown, rate limit, unlock counter |
| `DeviceStateHelperSpec` | Robolectric | Active use detection, car mode, phone call state |
| `SmsSpoofDetectorSpec` | Robolectric | SMS origin spoofing detection |
| `BatteryHelperSpec` | Robolectric | Battery level and power save mode checks |
| `AudioHelperSpec` | Robolectric | Volume save/restore and alarm maximization |
| `MainActivityContactsSpec` | Robolectric | Contact add/remove UI logic |

### Robolectric vs pure unit tests

- **Pure unit tests** have no Android dependencies and run without Robolectric.
- **Robolectric tests** are annotated with `@RobolectricTest` and use a custom Kotest extension (`RobolectricExtension`) that bootstraps the Robolectric sandbox classloader. Robolectric is configured to use SDK 34 via `app/src/test/resources/robolectric.properties`.

## Dependencies

- Kotlin 2.1.20
- Kotest 6.1.3 (runner-junit5 + assertions-core)
- Robolectric 4.16.1
- Mockito 5.21.0 + mockito-kotlin 6.2.3
- Android Gradle Plugin 8.9.1
- Hilt 2.51.1
- Room 2.8.4

## License

SmartFind is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License v3.0](LICENSE) as published by the Free Software Foundation.

This project contains no proprietary dependencies and is fully compatible with F-Droid inclusion criteria.
