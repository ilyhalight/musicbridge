# Changelog

## Unreleased

### Features

- Optional ListenBrainz scrobbling for the mirrored player, with token validation and encrypted token storage
- Custom ListenBrainz-compatible server URLs, including Maloja, with server-bound queues and explicit HTTP opt-in
- Configurable listening threshold: 30 seconds by default, or half the track / 4 minutes, whichever is shorter
- Persistent WorkManager delivery queue with offline, temporary-error and rate-limit retries; no `playing_now` submissions
- Scrobbling settings replace the Apps tab, with English and Russian interface text
- Notification shows the current player
- Notification permission request on Android 13+
- Russian translation for the notification and the permissions screen title
- New build flavors `asViper`, `asQQ`, `asLuna` and `asHiby` instead of `standalone`

### Bug Fixes

- More reliable player detection and switching between players

### Improvements

- Media session callbacks publish only the changed field; full snapshots are applied only when selecting a player
- More concise diagnostic logs without repeatedly dumping the entire playback queue

## 1.0.0

- Fixed works of Stop button in the notification
- About cards for MusicBridge and the original OmniBridge
