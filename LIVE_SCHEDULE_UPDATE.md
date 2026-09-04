# Live schedule update - v1.3.9

The Android app now has a dedicated Live schedule section on Home and Schedule.

## Included now

- Friday 4 September 2026: Aiki-Stretching
- 30 minutes
- Online from home
- Zoom meeting ID 848 3904 5940
- Passcode 907838
- Join Zoom button

The WhatsApp schedule calls this a Friday morning session, while the supplied Zoom invitation displays 20:30 Amsterdam. To avoid publishing a conflicting clock time, this bundled update shows the date and duration but no clock time.

## Live updates without rebuilding the app

Every 15 minutes the app checks:

https://www.shinyuembody.org/shinyu-app-live.json

If that JSON exists and contains upcoming sessions, it replaces the bundled fallback sessions. A template is included as `shinyu-app-live.example.json`.

Schema:

```json
{
  "sessions": [
    {
      "date": "2026-09-04",
      "title": "Aiki-Stretching",
      "time": "08:30 - 09:00",
      "durationMinutes": 30,
      "location": "Online from home",
      "joinUrl": "https://us02web.zoom.us/j/84839045940",
      "meetingId": "848 3904 5940",
      "passcode": "907838"
    }
  ]
}
```

`time`, `durationMinutes`, `location`, `joinUrl`, `meetingId`, and `passcode` are optional. `date` and `title` are required.

Direct automatic import from a private Zoom account is not included because the app currently has no Zoom OAuth credentials or Zoom webhook service. A future Zoom integration can write the same JSON feed, allowing the Android app to update without republishing the APK.
