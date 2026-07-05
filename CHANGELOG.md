# Subway Coders Changelog

## Unreleased

## 1.2.0 - 2026-07-05

### Added

- **Doomscroll mode** — a **Doomscroll (YouTube Shorts)** entry in the category selector that scrolls
  the endless YouTube Shorts feed instead of a looping clip. Loads the real feed in the embedded
  browser; no API keys or curated lists. The mode persists per window, and the feed follows the
  IDE's light/dark theme (and live theme switches).
- **Pause when hidden** — playback now pauses when a player's tool window is collapsed or another tab
  takes over its anchor, and resumes in place when it's shown again.
- **Focus terminal on Claude Code events** — when Claude Code asks a question or finishes, the four
  players are hidden and the built-in Terminal is focused so you snap back from the feed. Detection
  uses Claude Code hooks that the plugin auto-installs into `~/.claude/settings.json` (removed again
  when you turn the feature off). On by default; toggle it via the tool window's options (gear) menu.

## 1.1.0 - 2026-06-27

### Removed

- Per-window sound configuration and the unused `sound` config field.

### Changed

- Config editing moved to the tool window's options (gear) menu; the toolbar now holds
  Category, Shuffle and Open-in-Browser.
- Unknown fields in `categories.json` are now ignored instead of failing the whole config.

## 1.0.0

### Added

- First release: looping "brain-rot" gameplay clips right inside your IDE.
- Four independent players, one per screen edge (left / right / top / bottom), each with its own category and sound.
- Config-driven categories with multiple clips; "Shuffle" picks another and re-reads your config.
- Play YouTube embeds or direct video URLs — paste any URL to override the category.
- "Open in browser" action and a built-in config-file editor.
