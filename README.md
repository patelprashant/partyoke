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

Phones  ──scan QR──▶  http://<tv-lan-ip>:8128  (join page + live WS updates)
```

The QR encodes the TV's LAN address (e.g. `http://192.168.1.50:8128`). Phones
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

- **Server port:** `8128` (see `PartyViewModel.PORT` and `PartyServer`).
- **Supported video formats:** mp4, mkv, webm, avi, mov, m4v, 3gp, ts
  (`UsbSongScanner.SUPPORTED_EXTENSIONS`).
- **Min / Target SDK:** 24 / 34.

---

## 🤖 CI/CD

GitHub Actions builds and publishes the app:

- **CI** runs on every PR + push to `main`: ktlint, Android lint, debug APK (uploaded as an artifact).
- **Release** runs on a `vX.Y.Z` tag: builds a **signed** APK + AAB and publishes a GitHub Release with auto-generated notes.

Versioning is **tag-driven** (push `v1.2.0` → release `1.2.0`). Full setup + the one-time
signing-secrets steps are in **[RELEASING.md](RELEASING.md)**.

---

## 🎛️ Admin features (Tier 1)

The **first phone to join is the admin** (no PIN needed). The admin's phone shows
an extra control panel; if the admin leaves, the next-earliest player is promoted
automatically.

| Feature | Where | Notes |
|---|---|---|
| 👑 **Admin = first player** | phone + TV badge | Auto-promotes on admin leave |
| 📋 **Queue management** | admin phone | Bump a turn to next, or remove it |
| 🎨 **Rename party + accent** | admin phone | Live party name + accent color on TV *and* phones (defaults: "PartyOke" / `#FF2E92`) |
| 👏 **Applause + leaderboard** | all phones | One applause per guest per turn, no self-applause; leaderboard shows on TV + phones |

Admin actions are HTTP POSTs (`/admin/rename`, `/admin/accent`,
`/admin/queue/bump`, `/admin/queue/remove`) and the server verifies the sender is
the admin. Applause is `/applaud` (any guest).

## 🧭 Roadmap ideas

- Lyrics overlay (`.lrc` / `.srt`)
- Join control (lock/unlock, max players, name approval)
- Full drag-to-reorder queue on the admin phone
- Session persistence across restarts
- Pluggable `ActivityLog` (file or Room) for post-party stats

---

## ⚠️ Notes & constraints

- TV and all phones must share the **same local network**.
- `usesCleartextTraffic` is enabled because the local server is plain HTTP on the LAN.
- Song **video files never leave the USB/TV**; phones only see titles.
