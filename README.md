# 🎤 PartyOke — Android TV Karaoke

A **local, offline-first** karaoke party app for Android TV. Songs load from a
USB drive, guests join from their phones by scanning a QR code (no app install),
and each player gets a **randomly assigned** karaoke video on their turn.

No internet required — the TV runs a tiny embedded web server and everyone
coordinates over the local Wi-Fi.

---

## ✨ Features

- 📺 **Native Android TV** — Kotlin + Jetpack Compose for TV (`androidx.tv:tv-material3`)
- 💾 **USB songs** — pick your karaoke folder via the Storage Access Framework; videos play with Media3/ExoPlayer
- 📱 **Phone join via QR** — guests scan a QR code and join from a mobile web page (served by the TV, zero install)
- 🔀 **Random song assignment** — no-repeat "bag" shuffle; every song is used before any repeats
- 📋 **Live lobby + up-next queue** — players and turns sync to every phone in real time over WebSockets
- 📝 **Activity logging** — joins, turns, skips and assignments are recorded (swappable `ActivityLog` interface)

---

## 🏗️ Architecture

```
Android TV App (Kotlin)
├─ UI            Jetpack Compose for TV  (Setup → Lobby → Playing)
├─ Playback      Media3 / ExoPlayer
├─ Songs         Storage Access Framework → USB folder scan
├─ Game core     PartyManager (StateFlow single source of truth)
├─ Local server  Ktor (Netty) + WebSockets  → serves phone web app
└─ Activity log  InMemoryActivityLog (interface-backed, swappable)

Phones  ──scan QR──▶  http://<tv-lan-ip>:8080  (join page + live WS updates)
```

The QR encodes the TV's LAN address (e.g. `http://192.168.1.50:8080`). Phones
open it in a browser, enter a name, and get a random song queued automatically.

### Key source files

| File | Responsibility |
|------|----------------|
| `model/Models.kt` | Serializable domain types + `PartyState` |
| `game/PartyManager.kt` | Authoritative state, random assignment, turn flow |
| `data/UsbSongScanner.kt` | SAF recursive video scan |
| `net/PartyServer.kt` | Embedded Ktor HTTP + WebSocket server |
| `net/JoinPage.kt` | Phone web app (offline HTML/CSS/JS) |
| `net/QrGenerator.kt` | ZXing QR bitmap |
| `ui/PartyScreens.kt` | Setup / Lobby / Playing screens |
| `ui/VideoPlayer.kt` | ExoPlayer Compose wrapper |
| `PartyViewModel.kt` | Wires framework ↔ game core ↔ server |

---

## ▶️ Build & run

> Requires **Android Studio** (Giraffe or newer) with the Android SDK.
> This repo was scaffolded in an environment without the Android SDK, so build
> it locally.

1. Open the project in Android Studio. It will download the Gradle wrapper jar
   and sync automatically.
2. Connect an Android TV device or start an **Android TV emulator** (API 24+).
3. Run the `app` configuration.

### Using it at a party

1. Plug a USB drive with karaoke videos into the TV.
2. Launch PartyOke → **Select USB Karaoke Folder** → pick the folder.
3. The **Lobby** shows a QR code. Make sure phones are on the **same Wi-Fi**.
4. Guests scan → enter a name → they appear in the lobby and get a song queued.
5. Press **Start Party**. Songs play in order; use **Skip / Next** to advance.

---

## ⚙️ Configuration

- **Server port:** `8080` (see `PartyViewModel.PORT` and `PartyServer`).
- **Supported video formats:** mp4, mkv, webm, avi, mov, m4v, 3gp, ts
  (`UsbSongScanner.SUPPORTED_EXTENSIONS`).
- **Min / Target SDK:** 24 / 34.

---

## 🧭 Roadmap ideas

- Lyrics overlay (`.lrc` / `.srt`)
- Audience applause / scoring from phones
- Session persistence across restarts
- Pluggable `ActivityLog` (file or Room) for post-party stats

---

## ⚠️ Notes & constraints

- TV and all phones must share the **same local network**.
- `usesCleartextTraffic` is enabled because the local server is plain HTTP on the LAN.
- Song **video files never leave the USB/TV**; phones only see titles.
