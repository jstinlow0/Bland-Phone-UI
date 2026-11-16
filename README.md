# Bland

A black-and-white Android home screen built to make your phone boring.

- **Home:** a clock, the date, how many apps you've opened today, and your chosen apps as plain text. At the bottom: `apps` and `+ note`.
- **Gestures:** swipe up for the app list, down for notifications, left or right for the calendar.
- **App list:** a looping wheel. The middle row is biggest and the list never ends. Type to search; enter opens the middle app.
- **Pauses & daily limits:** mark an app with *pause & limit*. It shows a countdown before opening and locks for the rest of the day once you've used your opens (3 by default). Stricter limits apply right away; looser ones wait until tomorrow.
- **Calendar:** black-and-white month view with your events (asks for calendar access). Swipe left or right to change month.
- **Notifications:** each app is *show*, *hold quietly* (out of the status bar, kept in Bland's list) or *block*. One-tap setups like "people only". Needs notification access.
- **Look:** pick the font (sans, serif, mono, condensed) and text size. The clock can sit top, middle or bottom, left/center/right, in thin, bold, serif, mono or words.
- **+ note → Notion:** sends a quick note to your second-brain inbox database. First line becomes the page title. Notes that fail are kept and retried.
- **Daily quote:** one secular quote a day on the home screen. Tap it to save it to your Notion inbox. Turn it off in settings.
- **Settings:** long-press the clock.

### Connecting Notion
1. Go to notion.so/profile/integrations, create an integration and copy its secret.
2. Open your inbox database → ··· → Connections → add the integration.
3. Copy the database link, then in Bland: settings → notion second brain → connect.

The secret is stored only on your phone, inside Bland's private storage.

## Install
1. Download the APK from the latest **build-apk** run in the GitHub Actions tab (artifact `bland-launcher-apk`).
2. Open it on your phone and allow installing from that source.
3. Press home and choose **Bland** → *Always*, or go to Settings → Apps → Default apps → Home app.

## Build
`./gradlew assembleDebug` needs the Android SDK (API 35) and JDK 17.
