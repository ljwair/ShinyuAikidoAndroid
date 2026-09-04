# v1.3.1 build fix

Fixed Kotlin compile error in `MainActivity.kt` caused by accessing nullable Compose state `liveHome` as non-null inside the latest-event card.

Changed:

```kotlin
if (liveHome.eventFromNetwork) {
```

to:

```kotlin
if (liveHome?.eventFromNetwork == true) {
```

No feature behavior was removed. Dynamic Aiki Theme, schedule and event loading remain enabled.
