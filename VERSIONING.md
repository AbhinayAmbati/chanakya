# Versioning & Release Policy

## Semantic versioning

Chanakya uses **MAJOR.MINOR.PATCH**:

- **MAJOR** — incompatible changes users must act on (e.g. a change to how protection works).
- **MINOR** — new, backward-compatible functionality.
- **PATCH** — backward-compatible bug fixes.

The version lives in exactly one place: `versionName` (and the integer `versionCode`) in
`app/build.gradle.kts`. Every release bumps `versionCode` by one.

## Where versions are declared

| What | Where |
| --- | --- |
| App version | `app/build.gradle.kts` (`versionName`, `versionCode`) |
| Library/plugin versions | `gradle/libs.versions.toml` |
| Release notes | `CHANGELOG.md` |

No version string may be hardcoded anywhere else.

## Release process

1. **Freeze** — merge the final changes for the release into `main`.
2. **Bump** — update `versionName` and `versionCode` in `app/build.gradle.kts`.
3. **Changelog** — move the relevant entries in `CHANGELOG.md` from `Unreleased` into a new
   `## [x.y.z] - YYYY-MM-DD` section.
4. **Verify** — confirm CI is green on `main`.
5. **Approve** — a maintainer explicitly approves the release.
6. **Publish** — run the `Release` workflow from the Actions tab, passing the exact version.
   The workflow refuses to run if the requested version does not match `versionName`.
7. **Tag** — the workflow creates the `vX.Y.Z` tag and the GitHub Release with the APK.

## Rules

- Releases are **manual and gated**; there is no automatic publish on merge.
- The `Release` workflow is the **only** way an artifact is published.
- Never rewrite published tags or release notes; publish a new patch instead.
