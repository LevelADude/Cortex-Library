# Cortex Remote Preset Catalog

This folder is designed to be published as a standalone repository so Cortex Library can receive source preset updates without shipping a new app build.

## Licensing

This catalog content is covered by the repository root [`LICENSE`](../LICENSE). Keep that file when splitting this folder into its own repository.

## Files

- `catalog.json` – primary bundle consumed by the app.
- `catalog.schema.json` – validation schema for versioned catalog payloads.
- `presets/` – optional per-preset JSON fragments for maintainers.

## Publishing

1. Push this folder to GitHub as its own repository (or keep it nested here).
2. Use the raw URL for `catalog.json`, for example:
   - `https://raw.githubusercontent.com/<org>/<repo>/<branch>/catalog.json`
3. In Cortex app: **Settings → Preset Catalog**
   - Paste URL in **Catalog URL**
   - (Optional) set **Pinned domain** to `raw.githubusercontent.com`
   - Tap **Fetch & Preview**
   - Select presets and tap **Import Selected**

## Schema notes

- `catalogVersion` is strict and currently must be `1`.
- `catalog.json` must validate against `catalog.schema.json` (same top-level keys and preset object shape).
- `configJson` must be the same JSON config string consumed by app connectors.
- Use conservative allowlists (`allowedPdfDomains`) and bounded scrape configs (`maxPages <= 2`).
