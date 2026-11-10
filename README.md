# App Data Inspector

A native Android app inventory and privacy dashboard.

## Features
- Lists installed packages visible to the app.
- Shows app label, package name, version, system/user classification.
- Shows each app's declared permissions.
- Optional foreground usage summary for the last 24 hours after the user manually enables Android Usage Access.
- Search by app name or package.
- Opens Android's own App Info screen for the selected package.

## Important Android security boundary
Android's application sandbox prevents an ordinary app from reading another app's private storage, login tokens, cookies, sessions, or private databases. This project does not bypass that boundary, extract credentials, or collect private app data. For apps you own, add an explicit debug/export interface to those apps and inspect only data you control.

## Build
Requirements: Android SDK Platform 35, JDK 17, Android Gradle Plugin 8.9.1 compatible Gradle (8.11.1).

From the project root:
```bash
gradle assembleDebug
```
The APK is at:
`app/build/outputs/apk/debug/app-debug.apk`

If your environment has a Gradle wrapper installed, use `./gradlew assembleDebug`.

## Permissions
- `QUERY_ALL_PACKAGES`: used for the on-device installed-app inventory. Google Play distribution is subject to package visibility policy.
- `PACKAGE_USAGE_STATS`: special access; usage summaries remain empty until the user enables Usage Access in Android Settings.

No network permission is requested. No app data is uploaded.
