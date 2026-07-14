# SyncClipboard Android

Kotlin Android client for background LAN clipboard synchronization with the
SyncClipboard Windows client / built-in server, over the HTTP(S) WebDAV storage
protocol.

## What it does

- **Pull (server → phone):** a resident `dataSync` foreground service applies changed
  text to the device clipboard, driven by two mechanisms that back each other up:
  - **Real-time push** over the same SignalR hub (`/SyncClipboardHub`) the desktop
    client uses. A server-side change wakes the app immediately (near-zero latency).
  - **Adaptive polling** of `GET /SyncClipboard.json` as a safety net. While realtime
    is healthy the cadence drops to a slow heartbeat (~30s) to save battery/network;
    if the hub is unavailable (older/pure-WebDAV servers) it falls back to the
    configured fast interval (default 3s). Repeated failures back off exponentially.
  Works fully in the background.
- **Push (phone → server):** an optional `AccessibilityService` observes clipboard
  changes and pushes them to the server via `PUT /SyncClipboard.json` (uploading a
  transfer file first for text over 10240 chars).
- **Stays resident:** foreground service + battery-optimization exemption +
  restart-on-boot (`BootReceiver`).

## Android background clipboard limitation

Since Android 10 (API 29) an app can only *read* the clipboard when it is the
foreground app or the active IME. A foreground service and battery exemption do
**not** lift this. That is why phone→server push requires the accessibility
service — an enabled accessibility service counts as an active window, which
allows background clipboard reads. *Writing* the clipboard (pull direction) is
not restricted, so pull works without accessibility. The app only reads clipboard
text and never inspects screen/node content (`canRetrieveWindowContent="false"`).

## Protocol summary

| Direction | Request |
|---|---|
| Realtime | SignalR hub at `/SyncClipboardHub`; the server's `RemoteProfileChanged` invocation is used as a wake trigger for an immediate pull (the pushed payload is not trusted for content). |
| Pull | `GET /SyncClipboard.json` → compare `(type, hash)` → apply `text` (or `GET /file/{dataName}` when `hasData`) |
| Push | `hash = HEX_UPPER(SHA256(UTF8(text)))`; `PUT /SyncClipboard.json` (camelCase, string `type`). Text > 10240 chars: `PUT /file/{name}` (BOM-less UTF-8) first. |

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
