<p align="center">
  <img src="phone-app-source/img/icon.png" alt="Lumore app icon" width="120">
</p>

# Lumore

**A modern IPTV player for Android and Android TV.**
Designed & developed by **Mejri02**.

Lumore gives you a fast, good-looking interface for your own IPTV service — live TV, movies and
series from Xtream Codes, a Stalker portal or an M3U/M3U8 playlist. Lumore does not provide, host
or distribute any channels, movies or subscriptions.

## Highlights in 1.8.0

- **Four completely different interface families** — Classic (the original look), plus brand-new
  **Cinema**, **Glass** and **Neon** home screens with their own navigation, cards and artwork.
- **Three new themes** — Lumore Aurora (soft light glass), Lumore Cinema (warm widescreen) and
  Lumore Neon (dark dashboard) — alongside the 3 base themes and 7 photo themes, for **13 themes**
  in total. Existing themes are unchanged.
- **Sleep timer** in the player (15 min – 2 h) with a live countdown.
- **Video scaling** — Fit / Zoom / Stretch, remembered between sessions.
- **TV Guide** screen with beIN Sports NOW/NEXT programmes and channel search.
- **Recent searches** and a faster indexed search across live, movies and series.
- **Stalker portal support** (MAC-based portals) and **LibVLC fallback** for RTMP/UDP/RTP streams
  that ExoPlayer cannot open.
- **Brand refresh** — Lumore word mark, branded About screen with the Mejri02 credit, launcher name.
- **Stability work** — safer M3U parsing, atomic catalog cache, reusable HTTP clients, clearer
  player errors, confirmation dialogs for destructive actions, and many theme/contrast fixes.

## Features

**Sources**
- M3U / M3U8 playlists by URL, Xtream Codes accounts, and Stalker portals (MAC address + proxy).
- Multiple sources with one active account at a time.
- Human-readable login errors: wrong password, expired account, DNS failure, timeout, SSL.

**Browsing and watching**
- Live TV, Movies and Series with category browsing, category search and favorites.
- Continue watching with progress bars and resume.
- Series episodes with saved positions, auto-play of the next episode and per-episode downloads.
- Playback speed (0.75×–2×) with a saved default; automatic stream recovery (3 attempts).
- LibVLC fallback for RTSP/RTMP/UDP/RTP/MMS streams.
- Subtitles: load your own `.srt`/`.vtt`, detect the format, and nudge the timing ±500 ms.
- Picture-in-picture, gesture seek, double-tap to skip, and a channel zapper for live TV.

**Downloads**
- Offline movies and episodes with pause/resume and HTTP Range support.
- Optional Wi-Fi-only downloads, progress in the top bar and a dedicated Downloads screen.

**Personalization**
- 3 base themes + 7 photo themes + 3 themed layouts = **13 themes**.
- Accent colour, poster size (compact/normal/large) and sort order (count / A–Z, plus
  latest/oldest/rating/name inside categories).
- Favorites and continue-watching stored **per source**, so accounts never mix.

**Parental controls**
- Adult categories can be locked behind a 4-digit PIN.

**Cast to Samsung TV**
- Pair with the Lumore TV app on a Samsung Tizen TV over the same Wi-Fi network.
- Casting works only with the Samsung Tizen app, not other TV brands or Android TV.

**Data**
- JSON backup/restore of sources, favorites, progress, history and settings.
- Clear progress, favorites, search history or cached catalogs separately.
- Everything stays on the device; no analytics, no accounts, no cloud.

## Apps in this repository

| Component | Platform |
|---|---|
| Android / Android TV app | Android 7.0 (API 24) and newer |
| Samsung TV app | Tizen 5.0 and newer |
| Phone companion app | Android (legacy Capacitor build) |

## Build

Requirements: JDK 17–21, Android SDK, Android Studio (or a Gradle installation).

```bash
# From the repository root
gradle :app:assembleDebug
# or open the folder in Android Studio and press Run
```

The Android module uses:

- Android Gradle Plugin 8.9.1, Gradle 8.11+
- Kotlin 1.9.24, Compose Compiler 1.5.14
- `compileSdk` / `targetSdk` 36, `minSdk` 24
- Jetpack Compose (BOM 2024.06.00), Media3/ExoPlayer 1.3.1, OkHttp 4.12, Coil 2.6, LibVLC

If your machine only has the API 34 SDK installed, change `compileSdk`/`targetSdk` to `34` in
`app/build.gradle.kts` (the app does not use any API newer than 34 at runtime).

Release signing is optional and driven by environment variables:

```bash
export KEYSTORE_FILE=/path/to/release.p12
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=...
gradle :app:assembleRelease
```

## Privacy

Lumore only talks to the IPTV server you configure and (when you open the guide) to the public EPG
XML source. No telemetry, no ads, no third-party analytics. Playlists and credentials stay in the
app's private storage.

## Credits

- App, theme system and interface: **Mejri02**
- Built with Jetpack Compose, Media3/ExoPlayer, OkHttp, Coil, ZXing and LibVLC.

## Disclaimer

Lumore is a player only. You must supply your own legal IPTV subscription and content. The
developer does not host, provide or endorse any streams.
