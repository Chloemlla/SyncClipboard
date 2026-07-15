# SyncClipboard Android

Kotlin Android client for background LAN clipboard synchronization with the
SyncClipboard Windows / Linux / macOS desktop clients (and the built-in server),
over the HTTP(S) WebDAV storage protocol.

Package: `com.syncclipboard.mobile` · Sources: `android/`

## Features

Supports the **same clipboard profile types** the desktop client syncs over LAN:

| Profile | Pull (server → phone) | Push (phone → server) |
|---|---|---|
| **Text** | Yes | Yes (Accessibility or Shizuku) |
| **Image** | Yes (FileProvider URI clip) | Yes (Accessibility) |
| **File** | Yes (single file URI) | Yes (Accessibility) |
| **Group** | Yes (zip extract → multi-URI) | Yes (zip + multi-file clip) |

### Sync engine

- **Pull:** a resident `dataSync` foreground service applies changed profiles.
  - **Real-time** SignalR hub (`/SyncClipboardHub`) wakes an immediate pull
    (same feed as the desktop client).
  - **Adaptive polling** of `GET /SyncClipboard.json` as a safety net (~30s
    heartbeat while realtime is healthy; configured interval when not;
    exponential backoff on failures).
- **Push:** optional `AccessibilityService` observes clipboard changes (text,
  images, files) and uploads via `PUT /file/{name}` + `PUT /SyncClipboard.json`.
  Shizuku can push **text** without accessibility; binary types need
  Accessibility (content URIs require app context).
- **Size limit:** default **20 MiB** (desktop `MaxFileByte`). Oversized
  transfers are skipped and reported as an error status.
- **Stays resident:** foreground service + battery-optimization exemption +
  restart-on-boot (`BootReceiver`) + optional Shizuku keep-alive.

### Clipboard assist (Windows EasyCopyImage parity)

These options improve **Android ↔ Windows image interop** when copying from
browsers or other apps that only put HTML / image URLs on the clipboard:

| Setting | Default | Behavior |
|---|---|---|
| **Download web image** | On | If the clip is HTML with `<img src="http(s)://...">` or a direct image URL, download it and push as `type=Image` so Windows can paste a real image. |
| **Easy copy image** | On | After a successful web-image download, also rewrite the **local** clipboard as an image URI (desktop *AdjustClipboard* behavior). |

Complex formats (WebP / HEIC / AVIF, etc.) are re-encoded to PNG/JPEG when needed
so the desktop `ImageTool` extensions (png/jpg/jpeg/gif/bmp) can accept them.

## Requirements & permissions

| Permission / capability | Why |
|---|---|
| Network | Talk to LAN / remote SyncClipboard server |
| Foreground service (`dataSync`) + notification | Keep pull loop alive |
| Ignore battery optimizations | Reduce Doze kills |
| Accessibility (optional but recommended for push) | Background clipboard **read** on Android 10+ |
| Shizuku (optional) | Advanced keep-alive + text push without Accessibility |
| Notifications (Android 13+) | Ongoing sync status |

### Background clipboard limitation

Since Android 10 (API 29) an app can only *read* the clipboard when it is the
foreground app or the active IME. A foreground service and battery exemption do
**not** lift this. Phone → server push of images/files therefore needs the
accessibility service (or Shizuku for **text** only). *Writing* the clipboard
(pull) is not restricted.

## Quick start

1. On desktop, enable the built-in server (default port `5033`, user/password
   `admin`/`admin`) or point both sides at the same WebDAV / official server.
2. Install the APK from GitHub Releases (this fork) or build from `android/`.
3. In the app: enter `host:port` (or full `http(s)://…`), username, password.
4. Tap **Test connection**, then **Start**.
5. Grant battery exemption; enable Accessibility if you want phone → desktop push
   (required for images/files).
6. Optional: install/start Shizuku and grant access for stronger keep-alive and
   text-only push without Accessibility.

## Protocol summary

| Direction | Request |
|---|---|
| Realtime | SignalR `/SyncClipboardHub` → wake pull (payload not trusted for content) |
| Pull text | `GET /SyncClipboard.json` → apply `text` or `GET /file/{dataName}` when `hasData` |
| Pull image | `type=Image` → `GET /file/{dataName}` → FileProvider image clip |
| Pull file | `type=File` → `GET /file/{dataName}` → FileProvider file clip |
| Pull group | `type=Group` → `GET /file/{dataName}` (zip) → extract → multi-URI clip |
| Push text | `hash = HEX_UPPER(SHA256(UTF8(text)))`; text > 10240 chars: upload file first |
| Push image | `PUT /file/{Image_*.ext}` then profile `type=Image`, hash = `SHA256("{fileName}\|{contentSha256}")` |
| Push file | `PUT /file/{File_*.ext}` then profile `type=File` (same hash rule as desktop FileProfile) |
| Push group | Zip (UTF-8 entry names) → `PUT /file/{File_*.zip}` → `type=Group`, hash per desktop GroupEntry (`F\|name\|len\|contentHash\\0`, sorted by entry-name bytes) |

Auth is HTTP Basic (`base64(user:password)`, UTF-8).

## Desktop interop notes (Win / Linux / macOS)

- Profile types and hashes match `SyncClipboard.Shared` (`TextProfile` /
  `ImageProfile` / `FileProfile` / `GroupProfile`).
- Prefer enabling desktop **EasyCopyImage** / **Download web image** as well so
  browser copies become Image profiles in both directions.
- Single image-named files may travel as `Image` or `File` depending on source;
  the Android client treats desktop image extensions as images when applying
  File profiles for better paste UX.
- File / Group are best-effort on Android (URI clipboard items). Some OEM
  paste targets only accept images or plain text.

## Security note

The built-in server serves plain HTTP by default, so LAN traffic (including
credentials and clipboard content) is unencrypted unless HTTPS is enabled on the
server. The client accepts `https://` URLs and uses them verbatim.

Credentials are stored on-device with EncryptedSharedPreferences (AES256).

## Build / verification

CI is defined in `.github/workflows/android-package.yml` (PR verification: unit
tests, lint, debug APK) and `.github/workflows/android-release.yml` (signed
release APK). Both use Java 21 and a pinned Gradle version.

```bash
# From repo root (or android/)
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Release signing reads `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
`KEY_PASSWORD` from env or Gradle properties; without them a debug/unsigned
build is produced.

## Project layout

```
android/
  app/src/main/java/com/syncclipboard/mobile/
    core/          # Protocol: ProfileDto, SyncClient, Image/File/Group helpers, WebImageAssist
    sync/          # SyncEngine, ClipboardBridge, foreground + accessibility services
    shizuku/       # Optional privileged keep-alive / text clipboard read
    ui/            # Compose Material 3 settings + status UI
  app/src/test/    # Protocol / hash / assist unit tests
```

## Related

- Full fork overview: [root README](../README.md)
- Upstream desktop project: [Jeric-X/SyncClipboard](https://github.com/Jeric-X/SyncClipboard)
