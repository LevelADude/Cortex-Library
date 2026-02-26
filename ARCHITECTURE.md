# Architecture Overview

Cortex Library is an Android application focused on discovering, downloading, and reading open-access research content.

## High-level layers
- **UI (Compose/screens):** search, source management, downloads, settings, and PDF preview.
- **Domain models:** source definitions, search results, download items, and source test sessions.
- **Data layer:**
  - connector implementations for API and scraping sources,
  - repositories for sources, search, downloads, and debug events,
  - network client/connectivity monitor,
  - local DataStore and file-based utilities.

## Content flow
1. A source is selected from presets or imported definitions.
2. A connector executes search/listing logic against the source endpoint.
3. Search results are normalized into domain models.
4. Downloads are managed through repository/state-machine logic.
5. PDF resolution pipeline selects a compatible resolver for rendering/preview.

## Reliability and diagnostics
- Repository-level tests cover parsing, merge rules, cache policy, and download transitions.
- Debug ring-buffer/events provide lightweight diagnostics for source execution and failures.

## Preset catalog identity rules (`stablePresetId`)
- `stablePresetId` is the immutable identity key for built-in and remote presets.
- Import/merge behavior is keyed by `stablePresetId` first (not display name), so catalog updates replace the matching preset instead of creating duplicates.
- When exporting user-created presets without a stable id, the app derives a deterministic fallback (`user_<id-prefix>`) to keep catalog payloads mergeable.
- Catalog validation rejects duplicate `stablePresetId` values before import.

## Design goals
- Keep core reading features usable offline after download.
- Isolate provider-specific behavior behind connectors.
- Prefer deterministic, testable repository/domain logic.
