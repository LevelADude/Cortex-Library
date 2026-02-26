# Rebrand audit

This repository has been updated so user-facing branding uses **Cortex Library**.

## Intentionally not renamed technical identifiers

The following legacy identifiers are intentionally kept for compatibility and low-risk maintenance:

- Kotlin/Java package namespace: `app.shosetsu.android`
- Existing `applicationId` / flavor IDs such as `app.shosetsu.android.fdroid`
- Worker tags / notification channel IDs containing `shosetsu_...`
- Existing database / preferences keys that may be read from old installs
- Existing extension ecosystem URLs still hosted under legacy infrastructure (`shosetsuorg`, `shosetsu.app`)

These are tracked for a dedicated migration in `docs/SEPARATION_PLAN.md`.
