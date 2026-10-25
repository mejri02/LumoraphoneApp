# Lumora Phone (Android)

IPTV player for your phone (M3U / Xtream Codes / Stalker) that can also **pair with the Lumora TV app**:
send what you are watching to the TV, or use the phone as a remote control. See `PROTOCOL.md`.

## Get the APK (no Android Studio needed)
1. Create a GitHub repo and push this folder to it.
2. Open the **Actions** tab → *Build APK* → when it finishes, download **Lumora-Phone-apk** and install `Lumora-Phone.apk`.

Or locally: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (needs JDK 17 + Android SDK 34).

## Layout
* `app/src/main/java/.../MainActivity.java` – WebView shell (loads `assets/www`, handles Back).
* `app/src/main/assets/www` – the app itself (HTML/JS). `js/core/*` are the engines reused from the TV app
  (M3U/Xtream/Stalker parsers, network layer, Hls.js/mpegts.js player); `js/pair.js` is the pairing layer; `js/app.js` the UI.
