# Subway Coders Changelog

## [Unreleased]

### Removed

- Per-window sound configuration and the unused `sound` config field.

### Changed

- Config editing moved to the tool window's options (gear) menu; the toolbar now holds
  Category, Shuffle and Open-in-Browser.
- Unknown fields in `categories.json` are now ignored instead of failing the whole config.

## [1.0.0]

### Added

- First release: looping "brain-rot" gameplay clips right inside your IDE.
- Four independent players, one per screen edge (left / right / top / bottom), each with its own category and sound.
- Config-driven categories with multiple clips; "Shuffle" picks another and re-reads your config.
- Play YouTube embeds or direct video URLs — paste any URL to override the category.
- "Open in browser" action and a built-in config-file editor.
