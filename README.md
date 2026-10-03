<p align="center">
  <img src="./assets/readme/hero.svg" width="100%" alt="MusicBridge: players such as Spotify and VK X feed the MusicBridge node, which publishes the playing one as a system media widget with track title, artist and transport controls; playback commands flow back to the player.">
</p>

<div align="center">
  <h1>MusicBridge</h1>
  <a href="#install-and-set-up"><b>Install</b></a> ·
  <a href="#how-it-works"><b>How it works</b></a> ·
  <a href="#integrating-with-other-apps"><b>Integrating</b></a> ·
  <a href="#flavors"><b>Flavors</b></a> ·
  <a href="#build-from-source"><b>Build</b></a>
</div>

<br>

MusicBridge mirrors the active media session of any music player (Spotify, VK X, …) into its own
`MediaSession`, published under a package name that OEM ROMs whitelist. The lock screen, the Quick
Settings media widget and the Dynamic Island then show the player you are actually using, and
every transport command (play, pause, skip, seek, …) is forwarded back to it.

> [!WARNING]
> **Compatibility.** vivo has additional built-in checks that currently limit MusicBridge, so it may not work
> there. Other BBK phones (Oppo, OnePlus, realme) should work fine. It makes no sense on a Pixel or any other
> pure Android ROM.

## How it works

<p align="center">
  <img src="./assets/readme/how-it-works.svg" width="100%" alt="Four steps: grant notification listener access, track every player's session, pick one with selectActive, then mirror it and forward commands back with CommandForwarder.">
</p>

1. **Grant access.** `MusicBridgeListenerService` is an intentionally empty `NotificationListenerService`.
   Holding its grant is what lets the app call `MediaSessionManager.getActiveSessions`.
2. **Track players.** `SessionMirror` follows the active sessions of other apps, one callback per session token,
   in system priority order.
3. **Pick one.** `selectActive` decides what to mirror: a playing player wins, a paused one stays so the
   widget keeps its controls, and when the mirrored session is destroyed the next playing one takes over.
4. **Mirror and forward.** Metadata, playback state, queue and extras are copied to the `MusicBridge-Mirror`
   session; `CommandForwarder` sends every command it receives back to the mirrored player.

`MusicBridgeService` is a foreground (`mediaPlayback`) service that runs `SessionMirror`. Its notification shows
the current player (`Mirroring: <app>`) and a **Stop** action. `MusicBridgeServiceState` exposes `isRunning`,
`activePackage` and a `MirrorEvent` flow (active player, metadata, playback state) to in-process consumers.

## Integrating with other apps

To work with MusicBridge, your music player only needs an active `MediaSession`
(`android.media.session`, `MediaSessionCompat` or Media3 all work).
Supporting `MediaBrowserService` is not required.

For the best result:

- publish `MediaMetadata` (title, artist, album, artwork, duration);
- keep `PlaybackState` up to date and declare the actions you support
  (play, pause, skip, seek), because MusicBridge forwards these commands to your session.

## Flavors

Each flavor is the same app under a different application ID. Install the one whose package your ROM whitelists.

| Flavor      | Application ID           | Stands in for       |
| ----------- | ------------------------ | ------------------- |
| `asNetease` | `com.netease.cloudmusic` | NetEase Cloud Music |
| `asViper`   | `com.kugou.viper`        | KuGou Viper         |
| `asQQ`      | `com.tencent.qqmusic`    | QQ Music            |
| `asLuna`    | `com.luna.music`         | Luna Music          |
| `asHiby`    | `com.hiby.music`         | Hiby Music          |

All flavors share `versionName` `1.0` and the code namespace `app.toil.musicbridge`. Android allows one app per
package name, so a flavor cannot be installed next to the real app it uses the ID of.

## Install and set up

1. Build the APK of your flavor ([Build from source](#build-from-source)) and install it.
2. Open MusicBridge and follow the onboarding. Grant the permissions below.
3. Play something. Opening the app starts the service whenever notification access is granted; the
   **Settings** tab shows its state and starts it again after a Stop. There is no autostart on boot.

| Permission                   | Needed   | Why                                               |
| ---------------------------- | -------- | ------------------------------------------------- |
| Notification listener access | Required | Lets Android hand over other apps' media sessions |
| Notifications (Android 13+)  | Optional | Shows the service status and the **Stop** button  |
| Unrestricted battery use     | Optional | Keeps the service alive for longer                |

### ColorOS background activity

On ColorOS, enable **Allow background activity** for MusicBridge in
**Settings → Apps → MusicBridge → Battery usage**. Battery optimization exemption alone may not prevent
the system from freezing the app and hiding the Dynamic Island.

### "Restricted settings" on Android 13+

An APK installed from a browser, file manager or messenger is subject to _Restricted settings_: granting
notification access shows "For your security, this setting is currently unavailable". Installs through an
app store are not affected. To lift the restriction:

1. Try to enable notification access once (on Android 15+ the menu item in step 3 only appears after this attempt).
2. Open **Settings → Apps → MusicBridge**.
3. Tap the ⋮ menu in the top-right corner → **Allow restricted settings** → confirm with PIN or biometrics.
4. Enable notification access again.

### Reinstalling and upgrading

- A build signed with a different key cannot update the installed one: **uninstall the old version first, then
  grant notification access to the new one.** The original 1.0 release key may be lost.
- **Coming from Omnibridge:** the listener service class was renamed to `MusicBridgeListenerService`, so Android
  treats it as a new listener. Re-grant notification access after installing.

## Build from source

Requirements: JDK 17 and the Android SDK (platform 36). Set the SDK path in `local.properties`
(`sdk.dir=...`) or via `ANDROID_HOME`. Use `./gradlew` on Linux and macOS.

```text
gradlew.bat assembleAsNeteaseRelease
gradlew.bat assembleRelease
gradlew.bat testAsNeteaseDebugUnitTest
```

The first builds one flavor, the second builds all five. APKs land in
`app/build/outputs/apk/<flavor>/release/`, for example `asNetease/release/app-asNetease-release.apk`.
