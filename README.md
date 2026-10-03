# MusicBridge

MusicBridge mirrors the active media session of any music player into its own `MediaSession`.
Some OEM ROMs (Oppo/ColorOS, vivo, OnePlus) only show media controls in the Dynamic Island, the
Quick Settings media widget or the lock screen for a whitelist of packages (NetEase Cloud Music and
similar). MusicBridge runs under such a package name, copies metadata and playback state from the
player you are actually using, and forwards every transport command back to it.

This project is a fork of Omnibridge by iTaysonLab / bruhcollective. The code is a source
reconstruction of Omnibridge 1.0, recovered from the release APK. The original credits are kept in the app.

> **Upgrading from Omnibridge:** the notification listener service class was renamed
> (`MusicBridgeListenerService`), so Android treats it as a new listener. After installing,
> **re-grant notification-listener access** to MusicBridge in system settings (the app's onboarding
> screen links there).

## Granting notification access

On Android 13+, apps installed from an APK file (via browser, file manager, or messenger) are subject to "Restricted settings". When you try to grant Notification access, the system shows "For your security, this setting is currently unavailable" (RU UI: «В целях безопасности доступ к этой функции пока ограничен»). Installs via ADB, Android Studio, or app stores are not affected.

**Fix steps:**

1. Try to enable notification access for the app once (on Android 15+ the menu item in step 3 only appears after this attempt).
2. Open **Settings → Apps → MusicBridge** (the `asNetease` build may be listed under the NetEase name).
3. Tap the ⋮ menu in the top-right corner → **Allow restricted settings** → confirm with PIN/biometrics.
4. Enable notification access again.

**Alternative via ADB:**

- Install via `adb install <apk>` (not restricted).
- For `asNetease`:
  ```
  adb shell cmd notification allow_listener com.netease.cloudmusic/app.toil.musicbridge.service.MusicBridgeListenerService
  ```
- Other flavors follow the same pattern, `<applicationId>/app.toil.musicbridge.service.MusicBridgeListenerService`,
  with the application ID from the [Flavors](#flavors) table.

## How it works

- `MusicBridgeListenerService` is an empty notification listener. Its grant lets the app read other apps' media sessions.
- `MusicBridgeService` is a foreground (media playback) service that runs `SessionMirror`.
- `SessionMirror` tracks foreign sessions, picks the active one, and applies its snapshot
  (`MirrorSessionData`) to the `MusicBridge-Mirror` session. `CommandForwarder` sends commands back.
- `MusicBridgeServiceState.events` emits `MirrorEvent`s (active player, metadata and playback state of the mirrored player) for in-process consumers.
- The app has no `INTERNET` permission.

## Flavors

| Flavor      | Application ID           | Purpose                                        |
| ----------- | ------------------------ | ---------------------------------------------- |
| `asNetease` | `com.netease.cloudmusic` | NetEase Cloud Music. Passes the OEM whitelist. |
| `asViper`   | `com.kugou.viper`        | KuGou Viper.                                   |
| `asQQ`      | `com.tencent.qqmusic`    | QQ Music.                                      |
| `asLuna`    | `com.luna.music`         | Luna Music.                                    |
| `asHiby`    | `com.hiby.music`         | Hiby Music.                                    |

The code namespace is `app.toil.musicbridge` for all flavors.

## Build

Requirements: JDK 17 and the Android SDK (platform 36). Put the SDK path in `local.properties` (`sdk.dir=...`) or set `ANDROID_HOME`.

```
gradlew.bat assembleAsNeteaseRelease
gradlew.bat assembleRelease
gradlew.bat lintAsNeteaseRelease
```

(use `./gradlew` on Linux and macOS). APKs end up in `app/build/outputs/apk/`.

## Release signing

Create `keystore.properties` in the project root (it is gitignored):

```
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`storeFile` is resolved relative to the project root. Without this file the release build is signed with the debug key so it still builds and installs.

The original 1.0 release key may be lost. An APK signed with a different key cannot update the installed one, so
**uninstall the old version before installing a build signed with a new key.**
