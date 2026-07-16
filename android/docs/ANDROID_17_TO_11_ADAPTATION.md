# Android 17 to 11 Adaptation Notes (SyncClipboard)

Source references:
- Project-Lumen `docs/ANDROID_17_VIVO_ADAPTATION.md`
- Lumen docs `ANDROID_16` ... `ANDROID_11` Vivo adaptation notes

SyncClipboard Android currently ships with:

- `compileSdk = 37`
- `targetSdk = 37`
- `minSdk = 26` (Android 8.0; covers Android 11-17 runtime matrix)

This note tracks high-priority items from Android 17 down to Android 11 that apply to the clipboard-sync client.

## Android 17 (API 37)

### 1. Background audio hardening
**N/A** - app does not play continuous media; notifications are silent/low-importance service status only.

### 2. Explicit URI grants for share/send
**Mostly N/A** - no `ACTION_SEND` export path. Image download saves through MediaStore `GallerySaver` (no FileProvider share handoff). FileProvider remains available for internal paths.

### 3. Large-screen adaptation
**Done**
- `resizeableActivity="true"` on UI activities
- `configChanges` includes orientation/screenSize/screenLayout/smallestScreenSize/keyboardHidden/uiMode
- No forced portrait orientation
- No `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out

### 4. cleartext / network security
**Product exception documented**
- Built-in SyncClipboard server is commonly plain HTTP on LAN IPs
- Policy lives only in `res/xml/network_security_config.xml` (`cleartextTrafficPermitted=true`)
- Manifest does **not** set `usesCleartextTraffic`
- HTTPS URLs still work when configured

### 5. Local network permission (`ACCESS_LOCAL_NETWORK`)
**Done**
- Declared for targetSdk 37 LAN access to the built-in server / direct IP HTTP(S)

### 6. Certificate Transparency default
**Accept default**
- Public HTTPS servers should present CT-capable certs
- No CT opt-out in network security config

### 7. Dynamic native library loading
**N/A** - no `System.load` / downloaded `.so`

### 8. Loopback host protection
**N/A** - no cross-app localhost server/client

## Android 16 (API 36)

### Adaptive / large-screen
Covered above.

### Predictive back
**Done** - `android:enableOnBackInvokedCallback="true"`. UI is single-activity settings; no obsolete `onBackPressed()` path.

### scheduleAtFixedRate backlog
**N/A** - sync loops use coroutine `delay`, not `ScheduledExecutorService.scheduleAtFixedRate`.

### Intent redirection / matching
**Done** - `android:intentMatchingFlags="enforceIntentFilter"`. Notification/service PendingIntents use explicit components + `FLAG_IMMUTABLE`.

### Edge-to-edge enforcement
**Done**
- `MainActivity.enableEdgeToEdge()`
- `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`
- Transparent system bars in theme

### JobScheduler / abandoned jobs
**N/A** - no JobScheduler/WorkManager path today; FGS + coroutines only.

## Android 15 (API 35)

### Edge-to-edge
Covered above.

### Force-stop cancels pending work
**Partial**
- `BootReceiver` restores service on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` when `serviceEnabled`
- No exact-alarm schedule to rebuild (not used)

### dataSync FGS 6-hour timeout
**Done**
- `SyncForegroundService` remains `foregroundServiceType="dataSync"` (product needs resident sync)
- Implements `onTimeout(startId, fgsType)` and stops engine + service cleanly

### Manifest size limits / old targetSdk install floor
**OK** - small Manifest; targetSdk 37.

## Android 14 (API 34)

### Exact alarms
**N/A** - no `SCHEDULE_EXACT_ALARM` usage.

### Foreground service types required
**Done** - typed `dataSync` + `FOREGROUND_SERVICE_DATA_SYNC`.

### Implicit intent / PendingIntent package
**Done** - explicit activity/service classes; immutable flags.

### Safer dynamic code loading
**N/A**.

### Background activity launch
**OK** - no BAL grants; dialog confirm activity is app-owned.

### Partial photo access
**OK** - writes via MediaStore insert; no full-library `READ_MEDIA_*` path.

## Android 13 (API 33)

### `POST_NOTIFICATIONS`
**Done** - declared; MainScreen requests before start service on T+.

### Safer context-registered receivers
**N/A / low** - no dynamic app receivers requiring export flags today (Shizuku listeners are binder callbacks).

### Package-less PendingIntents
**Done**.

### Granular media permissions
**OK** - MediaStore write path; legacy `WRITE_EXTERNAL_STORAGE` maxSdk 28 only.

### Shared user ID
**OK** - none.

## Android 12 (API 31)

### Background FGS start limits
**Done** - `SyncForegroundService.startSafely()` catches `ForegroundServiceStartNotAllowedException` and logs instead of crashing. Boot/task-removed paths share the helper.

### PendingIntent mutability
**Done** - `FLAG_IMMUTABLE`.

### Explicit `android:exported`
**Done** on all components with filters.

### FGS notification immediate display
**Done** - `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)`.

### Untrusted touch overlays
**N/A**.

## Android 11 (API 30)

### Scoped storage
**Done**
- Image save uses MediaStore relative path `Pictures/SyncClipboard` on Q+
- No `MANAGE_EXTERNAL_STORAGE` / legacy external opt-out

### Package visibility
**Done**
- `<queries>` for Shizuku package, legacy package id, export action, market/https view intents
- `SettingsMigrator` / `ShizukuManager` package lookups use modern `PackageInfoFlags` on API 33+

### Background custom-view toasts
**N/A** - no custom toast views.

### Camera/mic FGS types
**N/A**.

## Verification checklist

- Fresh install target 37: can start dataSync FGS from UI; notification posts (with notification permission).
- LAN HTTP server URL still connects (cleartext config + local-network permission on supporting devices).
- Shizuku card detects install/running/permission with package visibility queries.
- Legacy package migration still resolves `com.syncclipboard.mobile`.
- Large screen / foldable rotation keeps MainActivity state via configChanges.
- If system fires dataSync timeout, service stops cleanly without process crash.
- Background-restricted FGS start attempts do not crash the app.

## Refresh log

- 2026-07-16: Raised targetSdk to 37 and applied Android 17 to 11 adaptations mapped from Project-Lumen Vivo notes.
