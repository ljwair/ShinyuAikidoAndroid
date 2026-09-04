# Weekly Aiki Theme - live source

The app no longer requires a rebuild when the weekly theme changes.

Source of truth:

`https://www.shinyuembody.org/Aiki-theme`

On launch, and then every 15 minutes while open, the app downloads the page and extracts the theme title, focus text and practice prompt. The latest successful copy is cached on the device for offline use. `ShinyuData.weeklyAikiTheme` remains only as a bundled fallback.
