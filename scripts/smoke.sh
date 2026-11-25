#!/usr/bin/env bash
# Tes asap: pasang APK di emulator, buka, tunggu intro selesai, simpan log dan screenshot.
adb install -r apk/app-debug.apk
adb logcat -c
adb shell am start -W -n com.floatx.gameturbo/.MainActivity
sleep 30
adb logcat -d > logcat.txt
adb exec-out screencap -p > screen.png
