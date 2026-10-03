# AGENTS.md

## Code style

Avoid overusing comments in code. Prefer clear naming, simple structure, and self-explanatory code over comments that merely restate what the code does.

Add comments only when they provide information that is not obvious from the code itself, such as:

- explaining complex or non-obvious logic;
- documenting important assumptions, constraints, edge cases, or workarounds;
- explaining why a particular approach was chosen when the reason is not apparent;
- warning about behavior that could easily be misunderstood or accidentally broken.

Do not add comments for trivial operations, obvious control flow, variable assignments, function calls, or code whose intent is already clear from its names and structure.

Prefer comments that explain **why**, not **what**. If a comment can be removed by making the code clearer, improve the code instead.

## Commits

ALWAYS write commit messages in English. You MUST use the semantic commits format.

NEVER make push or pull requests without ASK an user!

## Changelog

NEVER add commit hash to changelog message

## Technical summary

| Area         | Details                                                                                                                 |
| ------------ | ----------------------------------------------------------------------------------------------------------------------- |
| Platform     | Android 10+ (`minSdk` 29), `targetSdk` and `compileSdk` 36                                                              |
| Language     | Kotlin 2.2.20, Java 11 bytecode target                                                                                  |
| UI           | Jetpack Compose (BOM 2025.10.00), Material 3, MaterialKolor 4.0.5, Haze 1.7.1                                           |
| Theme        | Always dark (AMOLED), generated from the fixed orange seed `#FF9230`; English and Russian                               |
| State        | Kotlin coroutines 1.8.1 (`StateFlow` / `SharedFlow`), Preferences DataStore 1.1.7                                       |
| Android APIs | `NotificationListenerService`, `MediaSessionManager`, `MediaController`, `MediaSession`                                 |
| Build        | Gradle 8.13 wrapper, Android Gradle Plugin 8.13.0, version catalog, JDK 17                                              |
| Release      | R8 minification and resource shrinking; optional `keystore.properties`, debug-key fallback                              |
| Tests        | JUnit 4.13.2 (session selection, listening tracker, ListenBrainz payloads and response policy)                          |
| Scrobbling   | ListenBrainz-compatible servers (including Maloja), HTTPS by default / explicit HTTP opt-in, Keystore encryption, WorkManager 2.10.1 |
| Permissions  | `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

### Project structure

```text
app/src/main/java/app/toil/musicbridge/
├── MainActivity.kt                    entry point; starts the service when listener access is granted
├── MusicBridgeApplication.kt          application class, holds SettingsRepository
├── service/
│   ├── MusicBridgeService.kt          foreground service and its notification (current player, Stop)
│   ├── MusicBridgeListenerService.kt  empty listener whose grant unlocks media session access
│   ├── MusicBridgeServiceState.kt     process-wide state: isRunning, activePackage, events
│   └── mirror/
│       ├── SessionMirror.kt           tracks foreign sessions, drives the mirror session
│       ├── ActiveSessionSelection.kt  selectActive: which player to mirror
│       ├── CommandForwarder.kt        forwards transport commands to the mirrored player
│       ├── MirrorSessionData.kt       snapshot copied into the mirror session
│       ├── PlaybackObserver.kt        synchronous selected-player snapshots for scrobbling
│       └── MirrorEvent.kt             active player, metadata and playback state UI events
├── scrobbling/                        ListenTracker, ListenBrainzClient, ScrobblingEndpoint, ScrobbleQueue and ScrobbleWorker
├── data/SettingsRepository.kt         onboarding and scrobbling preferences, encrypted credentials
├── data/TokenCipher.kt                Android Keystore AES-GCM token encryption
├── ui/
│   ├── MusicBridgeTheme.kt            Material 3 theme from the orange seed
│   ├── navigation/                    splash routing between onboarding and panel
│   ├── onboarding/                    intro and permission screens
│   ├── panel/                         Scrobbling and Settings tabs
│   └── common/                        blur and permission helpers
└── util/SystemAccess.kt               permission checks and system settings intents
```

### Release signing

Without configuration the release build is signed with the debug key, so it still builds and installs.
To sign with your own key, create `keystore.properties` in the project root (it is gitignored):

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`storeFile` is resolved relative to the project root.
