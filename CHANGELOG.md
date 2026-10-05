# Changelog

## 1.0.2

- Fixed tracks played with the screen off not being scrobbled: long gaps without updates are now credited as far as the player's reported position confirms them
- Fixed the same track being scrobbled twice in a row from the same player, which happened when the player kept the previous track's metadata until the screen turned on. Repeat-one is now counted once
- Fixed tracks whose metadata arrives late losing the time they had already played

## 1.0.1

- Added optional ListenBrainz scrobbling for the mirrored player. Only the currently mirrored player is tracked
- Added token validation before connecting; the token is encrypted with Android Keystore and never stored in queued tasks
- Added support for custom ListenBrainz-compatible servers, including Maloja. HTTPS is used by default
- Added separate delivery queues per server and account. Queued listens are never redirected to a new server
- Added optional comma-separated artist splitting for Maloja: `Artist 1, Artist 2` is sent as an artist array through the native `/apis/mlj_1` API. Existing queued listens are unchanged
- Added configurable listening threshold: a fixed number of seconds (30 by default) or half the track duration, up to 4 minutes
- Added persistent WorkManager delivery queue with retries for offline delivery, temporary server errors and rate limits
- Added current player to the service notification
- Added notification permission request on Android 13+
- Added Russian translation for the notification and the permissions screen title
- Replaced the Apps tab with scrobbling settings
- [!] Added `INTERNET` permission. It is used only for scrobbling, which is off by default
- [!] App data is now excluded from cloud backups and device transfers
- Added new flavors:
  - `asViper` (`com.kugou.viper`)
  - `asQQ` (`com.tencent.qqmusic`)
  - `asLuna` (`com.luna.music`)
  - `asHiby` (`com.hiby.music`)

- Fixed player detection and switching between players
- Media session callbacks now publish only the changed field; full snapshots are applied only when selecting a player

## 1.0.0

- Fixed Stop button in the notification
- Added About cards for MusicBridge and the original OmniBridge
