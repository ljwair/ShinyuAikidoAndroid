# Zoom live schedule feed

Version 1.3.11 reads Shinyu live sessions directly from:

https://lingering-violet-0685.shinyu-leadership.workers.dev/

The Cloudflare Worker authenticates securely to Zoom and returns only Shinyu-related upcoming meetings.
The Android app converts Zoom UTC start times into the meeting timezone (normally Europe/Amsterdam), so correcting a meeting time in Zoom updates the app without rebuilding the APK.

The original `sessions` JSON format is still accepted as a fallback for backward compatibility.
