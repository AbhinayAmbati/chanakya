# Contributing to Chanakya

Thanks for your interest. This document describes how we work so that contributions land
smoothly.

## Branching model

Chanakya is **trunk-based**. All work targets `main` directly through short-lived branches:

```
main  ←  feature/xyz   (branch, commit, open PR, merge, delete)
```

There is no `develop` branch. Keep branches small and short-lived.

## Commit messages

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>
```

Common types: `feat`, `fix`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`.

Examples:

```
feat(home): add protection status card
fix(vpn): handle network switch without dropping the tunnel
docs(readme): clarify build requirements
```

## Coding standards

- **Kotlin** with the official code style; 4-space indentation, 120-column limit.
- **KDoc** on every public class and function — assume the reader is new to the codebase.
- One top-level declaration per file; files named after the declaration they contain.
- Prefer immutable data and `StateFlow` for UI state.
- No third-party analytics or tracking SDKs. Ever. CI enforces a dependency allowlist.

## Pull requests

1. Branch from `main`.
2. Make the change, keeping commits focused.
3. Update `CHANGELOG.md` under `Unreleased`.
4. Ensure `./gradlew assembleDebug testDebugUnitTest lintDebug` passes.
5. Open the PR using the template and request a review.

## Reporting security issues

Please do **not** open a public issue for security problems. See [`SECURITY.md`](SECURITY.md).
