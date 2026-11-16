# Bland

A black-and-white Android home screen built to make your phone boring.

- **Home:** a clock, the date, how many apps you've opened today, and up to 6 pinned apps as plain text. No icons, badges, widgets or wallpaper.
- **App list:** swipe up (or tap `apps`). Start typing to search, and press enter to open the top match.
- **Pause:** long-press an app and pick *pause before opening*. Opening it then shows a countdown (10s by default) and how many times you've opened it today. You can't skip it, and leaving the screen restarts it.
- **Long-press any app** to pin it, unpin it, reorder it, hide it, open its app info, or uninstall it.
- **Settings:** long-press the clock. You can set the pause length, how many apps fit on home, and whether hidden apps show. There's also a guide to making the whole phone grayscale.

## Install
1. Download the APK from the latest **build-apk** run in the GitHub Actions tab (artifact `bland-launcher-apk`).
2. Open it on your phone and allow installing from that source.
3. Press home and choose **Bland** → *Always*, or go to Settings → Apps → Default apps → Home app.

## Build
`./gradlew assembleDebug` needs the Android SDK (API 35) and JDK 17.
