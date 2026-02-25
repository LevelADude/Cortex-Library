# Clean separation plan (package/applicationId migration)

## Why this is deferred

A full rename from `app.shosetsu.android` to `app.monogatari.android` is high-risk in the current codebase because package names, storage keys, and update channels are deeply coupled.

## Risks

1. **Data continuity**: SharedPreferences/DataStore keys and serialized references may stop resolving.
2. **Database and migrations**: Room DB names and migration assumptions may break during package/applicationId transition.
3. **Update path**: changing `applicationId` prevents in-place upgrades from current installs.
4. **External integrations**: links, extension repositories, and worker IDs currently use legacy namespaces.

## Safe migration sequence

1. Add runtime compatibility aliases and explicit migration code for preferences/DB.
2. Add a one-time data migration layer with telemetry and rollback support.
3. Keep package namespace stable while introducing new internal module namespaces.
4. Ship a release that can read/write both old and new keys.
5. Only then switch `applicationId` with explicit user migration guidance.
6. Remove legacy aliases in a later major release.

## Minimal safe changes applied now

- User-facing labels and copy updated to Monogatari.
- Added this plan and branding audit documentation.
- Kept technical identifiers unchanged to avoid breaking existing installs.
