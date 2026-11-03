<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" alt="Melora app icon" width="200" />

# Melora

### Where Music Comes Alive.

<br/>

[![Build APK](https://github.com/RUPESHADHIKARI/Melora/actions/workflows/melora-apk.yml/badge.svg)](https://github.com/RUPESHADHIKARI/Melora/actions/workflows/melora-apk.yml)
[![License](https://img.shields.io/github/license/RUPESHADHIKARI/Melora?style=for-the-badge&labelColor=0d1117)](https://github.com/RUPESHADHIKARI/Melora/blob/main/LICENSE)

<br/>

[**Download**](#download) · [**Features**](#features) · [**Build from source**](#build-from-source) · [**Credits**](#credits) · [**License**](#license)

</div>

---

> [!NOTE]
> **Regional Restriction** — If YouTube Music is unavailable in your region, Melora will not work without a **VPN or proxy** connecting to a supported region.

---

<div align="center">

<h1><a id="features"></a>Features</h1>

</div>

- **Stream the whole YouTube Music catalog** — songs, videos, albums, artists, playlists
- **Background & lock-screen playback** with a polished notification
- Personalized home feed with **quick picks, listen again, new releases and charts**
- **Download & offline playback** — cache songs or save full playlists to your device
- **Synced lyrics** with per-song adjustments
- Built-in **audio equalizer** (AutoEq compatible presets) and audio normalization
- **Sleep timer** with shake-to-dismiss
- **Listen Together** — share a synchronized listening session with friends
- Material 3 design with **dynamic theming**, custom accent colors and Black backgrounds
- **Import / export backups** of your settings, playlists and history
- Widget, Android Auto support and more

---

<div align="center">

<h1><a id="download"></a>Download</h1>

</div>

APKs are built automatically by GitHub Actions on every push to `main`:

1. Open the [**Actions tab**](https://github.com/RUPESHADHIKARI/Melora/actions/workflows/melora-apk.yml)
2. Open the latest **Melora APK** run
3. Scroll down to **Artifacts** and download **`melora-foss-debug`**
4. Unzip and install `app-foss-debug.apk` on your device

> Artifact downloads require you to be signed in to GitHub.

---

<div align="center">

<h1><a id="build-from-source"></a>Build from source</h1>

</div>

Requirements: **JDK 21** and the Android SDK (API 36+).

```bash
git clone https://github.com/RUPESHADHIKARI/Melora.git
cd Melora
./gradlew :app:assembleFossDebug
```

The debug APK will be located at `app/build/outputs/apk/foss/debug/app-foss-debug.apk`.

---

<div align="center">

<h1><a id="credits"></a>Credits</h1>

</div>

Melora is a rebrand and redesign of [**Metrolist**](https://github.com/MetrolistGroup/Metrolist), a free and open-source YouTube Music client for Android. The project builds on that GPL-licensed foundation — thank you to the original developers and the community.

---

<div align="center">

<h1><a id="license"></a>License</h1>

</div>

Distributed under the **GNU General Public License v3.0**. See [`LICENSE`](LICENSE) for the full license text.

<div align="center">

<br/>

*MELORA — Designed and Developed by **Rupesh***

</div>
