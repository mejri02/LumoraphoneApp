<p align="center">
  <img src="docs/images/app-icon.png" alt="Lumora TV icon" width="120">
</p>

# Lumora TV

A modern IPTV player for Android, Android TV, and Samsung Tizen TVs, with a companion phone app.

Lumora gives you a clean, fast interface for managing your own IPTV sources and watching live TV, movies, and series from your provider. It does not provide, host, or distribute any channels, movies, or subscriptions.

## Sections

<p align="center">
  <img src="docs/images/tile-live.png" alt="Live TV" width="110">
  <img src="docs/images/tile-vod.png" alt="Movies" width="110">
  <img src="docs/images/tile-series.png" alt="Series" width="110">
  <img src="docs/images/tile-favs.png" alt="Favorites" width="110">
</p>

## Screenshots

<p align="center">
  <img src="docs/screenshots/home.png" alt="Home screen" width="200">
  <img src="docs/screenshots/catalog.png" alt="Catalog" width="200">
  <img src="docs/screenshots/themes.png" alt="Themes in settings" width="200">
  <img src="docs/screenshots/downloads.png" alt="Downloads" width="200">
</p>

## Features

**Sources**
- Add M3U / M3U8 playlists by URL.
- Connect Xtream Codes accounts (server URL, username, password).
- Manage multiple sources and switch between them.
- Login checks give clear messages for common problems: wrong password, invalid server, network or timeout errors, expired or inactive accounts.

**Browsing and watching**
- Live TV, Movies, and Series, with category browsing and category search.
- Continue watching for movies and episodes, with progress bars and resume.
- Most viewed channels, and new releases.
- Series episodes, with saved positions.
- Playback speed control (0.5x to 2x) and automatic stream recovery.
- Live "Now / Next" program info and TV guide (EPG) support.
- Subtitles: load your own `.srt` or `.vtt` file, with sync adjustment. Optional OpenSubtitles search (API key stored encrypted with Android Keystore).

**Downloads**
- Download movies and episodes for offline viewing.
- Manage downloads in Settings.

**Personalization**
- Several color themes and an accent color choice.
- Sort categories by item count or A to Z.
- Favorites per account.
- Arabic and English interface, with full right-to-left support.

**Parental controls**
- Adult categories can be locked behind a 4-digit PIN.

**Phone and TV together**
- The phone app can send playback to the Lumora TV app over the same Wi-Fi network.

**Settings and data**
- Back button navigates through screens, sheets, and dialogs.
- Backup export and import (JSON). The backup contains passwords, so keep it private.
- Clear data option.

## Apps in this repository

| Component | Platform |
|---|---|
| Android / Android TV app | Android 7.0 (API 24) and newer |
| Samsung TV app | Tizen 5.0 and newer |
| Phone companion app | Android |

## Requirements

- Android 7.0 (API 24) or newer, or an Android TV device.
- An IPTV service that provides an M3U playlist or Xtream Codes access.

## Installation

1. Open the [Releases page](https://github.com/mejri02/LumoraphoneApp/releases).
2. Download the latest `.apk` from the release assets.
3. Install it on your device. If Android asks, allow installs from your browser or file manager.

## Privacy

- Your sources, favorites, watch progress, and settings are stored on your device.
- The OpenSubtitles API key is encrypted locally with Android Keystore (AES-GCM).
- The app connects only to the servers you configure, plus the public EPG source used for the built-in guide.

## Disclaimer

Lumora TV is a media player. It does not provide, host, or distribute TV channels, movies, or subscriptions. You are responsible for the content and services you access through the app, and for complying with the laws in your country.

## Support

Report bugs and request features on the [Issues page](https://github.com/mejri02/LumoraphoneApp/issues). Please include your device model, Android version, app version, and steps to reproduce the problem.
