# SyncClipboard Android

Kotlin Android client for background LAN clipboard synchronization with the
SyncClipboard Windows / Linux / macOS desktop clients (and the built-in server),
over the HTTP(S) WebDAV storage protocol.

## What it does

Supports the same clipboard profile types the desktop client syncs:

| Profile | Pull (server → phone) | Push (phone → server) |
|---|---|---|
| **Text** | Yes | Yes (Accessibility or Shizuku) |
| **Image** | Yes (FileProvider URI clip) | Yes (Accessibility) |
| **File** | Yes (single file URI) | Yes (Accessibility) |
| **Group** | Yes (zip extract → multi URI) | Yes (zip + multi-file clip) |

- **Pull:** a resident `dataSync` foreground service applies changed profiles:
  - **Real-time** SignalR hub (`/SyncClipboardHub`) wakes an immediate pull.
  - **Adaptive polling** of `GET /SyncClipboard.json` as a safety net (~30s heartbeat
    while realtime is healthy; configured interval when not).
- **Push:** optional `AccessibilityService` observes clipboard changes (text, images,
  files) and uploads via `PUT /file/{name}` + `PUT /SyncClipboard.json`. Shizuku can
  push **text** without accessibility; binary types need Accessibility (content URIs).
- **Clipboard assist (Windows EasyCopyImage parity):**
  - **Download web image** — if the clip is HTML with `<img src="http(s)://...">` (or a
    direct image URL), download it and push as `type=Image` so Windows can paste a real image.
  - **Easy copy image** — after a successful web-image download, also rewrite the local
    clipboard as an image URI (desktop AdjustClipboard behavior).
- **Size limit:** default **20 MiB** (`MaxFileByte` on desktop). Oversized transfers
  are skipped and reported as an error status.
- **Stays resident:** foreground service + battery-optimization exemption +
  restart-on-boot (`BootReceiver`).

## Android background clipboard limitation

Since Android 10 (API 29) an app can only *read* the clipboard when it is the
foreground app or the active IME. A foreground service and battery exemption do
**not** lift this. That is why phone→server push requires the accessibility
service (or Shizuku for text). *Writing* the clipboard (pull) is not restricted.

## Protocol summary

| Direction | Request |
|---|---|
| Realtime | SignalR `/SyncClipboardHub` → wake pull (payload not trusted for content) |
| Pull text | `GET /SyncClipboard.json` → apply `text` or `GET /file/{dataName}` when `hasData` |
| Pull image | `type=Image` → `GET /file/{dataName}` → FileProvider image clip |
| Pull file | `type=File` → `GET /file/{dataName}` → FileProvider file clip |
| Pull group | `type=Group` → `GET /file/{dataName}` (zip) → extract → multi-URI clip |
| Push text | `hash = HEX_UPPER(SHA256(UTF8(text)))`; large text: file first. |
| Push image | `PUT /file/{Image_*.ext}` then profile `type=Image`, hash = `SHA256("{fileName}\|{contentSha256}")`. Ext: png/jpg/jpeg/gif/bmp. Others re-encoded to PNG. |
| Push file | `PUT /file/{File_*.ext}` then profile `type=File`, same hash rule as desktop FileProfile. |
| Push group | Zip entries (UTF-8 names) → `PUT /file/{File_*.zip}` → `type=Group`, hash per desktop GroupEntry format (`F\|name\|len\|contentHash\\0` sorted by entry name bytes). |

Auth is HTTP Basic (`base64(user:password)`, UTF-8). Server defaults: port `5033`,
`admin`/`admin`.

## Security note

The built-in server serves plain HTTP by default, so LAN traffic (including
credentials and clipboard content) is unencrypted unless HTTPS is enabled on the
server. The client accepts `https://` URLs and uses them verbatim.

## Build / verification

CI is defined in `.github/workflows/android-package.yml` (PR verification: unit
tests, lint, debug APK) and `.github/workflows/android-release.yml` (signed release
APK). Both use Java 21 and a pinned Gradle version, matching the desktop CI style.

Release signing reads `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
`KEY_PASSWORD` from env or Gradle properties; without them a debug/unsigned build
is produced.
