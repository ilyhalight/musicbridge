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

## Project layout

- `service/`: foreground service, listener service, `mirror/` (session mirroring, command forwarding, events).
- `data/`: Preferences DataStore `settings` with the `onboarding_done` flag.
- `ui/`: Compose UI. `navigation/` (splash routing), `onboarding/`, `panel/` (Apps and Settings tabs), `common/` (blur helpers), `MusicBridgeTheme.kt`.
- `util/`: permission checks and system settings intents.
