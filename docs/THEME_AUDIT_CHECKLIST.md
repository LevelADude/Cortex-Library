# Theme Audit Checklist

Quick checklist for app theme regressions:

- [ ] Theme picker only shows: Follow System, Light, Dark, Emerald Manuscript, Midnight Inc (Gold).
- [ ] Follow System resolves strictly to Light (system light) or Dark (system dark).
- [ ] Legacy saved app theme key `0` migrates to Follow System.
- [ ] Bottom navigation/rail uses theme-driven background and selected/unselected icon/text tints.
- [ ] More screen list uses themed icon accents and readable text/divider colors.
- [ ] Emerald Manuscript and Midnight Inc (Gold) apply subtle texture on Compose screen backgrounds.
- [ ] No user-facing references to "Cortex Default" remain.
