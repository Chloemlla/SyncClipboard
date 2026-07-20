# SyncClipboard Android

Kotlin Android client for background LAN clipboard synchronization with the
SyncClipboard Windows / Linux / macOS desktop clients (and the built-in server),
over the HTTP(S) WebDAV storage protocol.

Package: `com.chloemlla.syncclipboard.mobile` · Sources: `android/`

## Features

Supports the **same clipboard profile types** the desktop client syncs over LAN:

| Profile | Pull (server → phone) | Push (phone → server) |
|---|---|---|
| **Text** | Yes | Yes (Accessibility or Shizuku) |
| **Image** | Yes (FileProvider URI clip) | Yes (Accessibility) |
| **File** | Yes (single file URI) | Yes (Accessibility) |
| **Group** | Yes (zip extract → multi-URI) | Yes (zip + multi-file clip) |

### Content control & desktop parity (Android)

| Capability | Android support |
|---|---|
| **Push / Pull direction** | Yes (main toggles) |
| **Per-type content control** | Yes — push text/image/file, pull image/file, max size (MB) in UI + engine |
| **Clipboard assist** | Easy copy image + download web image |
| **Image download confirm** | Yes (save to gallery + clipboard after confirm) |
| **Local clipboard history** | Yes — recent text (and metadata for other types), copy again / delete / favorite |
| **Ignore foreground apps** | Yes — package ignore list skips push (HotkeyBlacklist analog; needs Accessibility) |
| **Tools** | Short URL (mmp.cc), Artifact (Bearer API), OpenAI-compatible image gen; media/ffmpeg optional with explicit disabled message (no bundled binary) |
| **OSS first-run / About** | Yes |
| **lumen-crash** | Yes |
| **Global hotkeys / desktop history window** | Not applicable on Android |

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
| Live Update / promoted ongoing (when supported) | Temporarily promote the FGS notification during connecting, errors, and ~5s after a successful sync |

### Background clipboard limitation

Since Android 10 (API 29) an app can only *read* the clipboard when it is the
foreground app or the active IME. A foreground service and battery exemption do
**not** lift this. Phone → server push of images/files therefore needs the
accessibility service (or Shizuku for **text** only). *Writing* the clipboard
(pull) is not restricted.

## Settings migration (package rename)

`applicationId` was renamed from `com.syncclipboard.mobile` to
`com.chloemlla.syncclipboard.mobile`. Encrypted preferences cannot be read across
UIDs, so migration is automatic when possible via a **same-signature
ContentProvider**:

1. Install a build that still uses the **legacy** id
   (`legacyMigrate` flavor → `com.syncclipboard.mobile`) *or* keep the old app
   installed after updating it once with a migrate-capable build.
2. Install / open the **production** app (`com.chloemlla.syncclipboard.mobile`).
3. On first launch, if the new app has no server URL, it queries
   `content://com.syncclipboard.mobile.migration/settings` (signature permission)
   and imports server URL, credentials, toggles, and service-enabled flag.
4. If import succeeds and the old app had sync running, the new app restarts the
   foreground service automatically.

**Product flavors**

| Flavor | applicationId | Purpose |
|---|---|---|
| `production` (default) | `com.chloemlla.syncclipboard.mobile` | Normal releases |
| `legacyMigrate` | `com.syncclipboard.mobile` | Update path for existing installs so they can export settings |

Recommended upgrade path for end users who already have the old package:

1. Install `legacyMigrate` release over the old app (same id) → exports snapshot.
2. Install `production` release → imports via ContentProvider.
3. Uninstall the legacy app when satisfied.

If only the production APK is installed on a clean device, there is nothing to
import (manual re-entry of server settings is required).

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
./gradlew :app:testProductionDebugUnitTest
./gradlew :app:assembleProductionDebug
# Optional: legacy applicationId build for exporting settings from old installs
./gradlew :app:assembleLegacyMigrateRelease
```

Release signing reads `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
`KEY_PASSWORD` from env or Gradle properties; without them a debug/unsigned
build is produced.


## Crash reporting (Lumen Crash SDK)

Android uses Project Lumen's `lumen-crash` bundle for uncaught-exception capture,
breadcrumb trails, and a cold-start crash report UI (`LumenCrashGate`).

- Install happens in `SyncClipboardApp.attachBaseContext` via `LumenCrash.installSafely`.
- Pending reports block the normal UI before the OSS first-run gate / main screen.
- Paste upload remains available from the crash page (user-initiated only).
- Author attribution from the SDK is non-removable.

### Dependency resolution

Version is **not** hardcoded. CI and local builds resolve the latest main auto-release
(`lumen-crash-v*`), then sync GitHub **Release assets** into a local Maven tree
(`.m2-lumen-crash`). That is the default path and avoids cross-repo GitHub Packages
auth failures.

```bash
# Linux / macOS / Git Bash
cd android
export LUMEN_CRASH_VERSION="$(bash ./scripts/resolve-lumen-crash-latest.sh)"
bash ./scripts/sync-lumen-crash-release-maven.sh "$LUMEN_CRASH_VERSION" .m2-lumen-crash
export LUMEN_CRASH_MAVEN_DIR="$PWD/.m2-lumen-crash"
./gradlew :app:testProductionDebugUnitTest
```

```powershell
# PowerShell
cd android
$env:LUMEN_CRASH_VERSION = pwsh -File ./scripts/Resolve-LumenCrashLatest.ps1
pwsh -File ./scripts/Sync-LumenCrashReleaseMaven.ps1 -Version $env:LUMEN_CRASH_VERSION -OutDir .m2-lumen-crash
$env:LUMEN_CRASH_MAVEN_DIR = (Resolve-Path .\.m2-lumen-crash).Path
```

Gradle still accepts GitHub Packages as a fallback when credentials exist
(`gpr.user` / `gpr.key` or `GITHUB_ACTOR` / `GITHUB_TOKEN`). Optional secret:
`LUMEN_CRASH_GITHUB_TOKEN` (may help rate limits / private assets; not required for
public release consumption).
## Project layout

```
android/
  app/src/main/java/com/chloemlla/syncclipboard/mobile/
    core/          # Protocol: ProfileDto, SyncClient, Image/File/Group helpers, WebImageAssist
    sync/          # SyncEngine, ClipboardBridge, foreground + accessibility services
    shizuku/       # Optional privileged keep-alive / text clipboard read
    ui/            # Compose Material 3 settings + status UI
  app/src/test/    # Protocol / hash / assist unit tests
```

## Related

- Full fork overview: [root README](../README.md)
- Upstream desktop project: [Jeric-X/SyncClipboard](https://github.com/Jeric-X/SyncClipboard)

