# Contributing

Thanks for helping improve Cortex Library.

## Before you start
- Open an issue for bugs, regressions, or large features.
- Keep changes focused and easy to review.
- Do not include unrelated refactors in the same PR.

## Development basics
1. Fork and create a feature branch.
2. Build locally:
   ```bash
   ./gradlew :android:assembleDebug
   ```
3. Run tests:
   ```bash
   ./gradlew :android:testDebugUnitTest
   ```
4. Submit a pull request using the PR template.

## Pull request checklist
- [ ] Change is scoped and documented.
- [ ] Tests added/updated when behavior changes.
- [ ] No secrets, tokens, or private endpoints committed.
- [ ] UI changes include screenshots when applicable.

## Commit messages
Conventional Commits are preferred (for example `feat:`, `fix:`, `docs:`).
