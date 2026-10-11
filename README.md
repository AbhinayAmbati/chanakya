# Chanakya

> See and stop the trackers inside your apps — on your phone, never on someone else's server.

**Chanakya** is an open-source Android application that creates a **local, on-device VPN**
using Android's `VpnService` API to inspect and block outbound traffic destined for ad
networks, analytics SDKs and tracking domains. No traffic is ever routed to an external
server. Everything — interception, filtering, logging — happens on the device.

<p align="left">
  <img alt="CI" src="https://github.com/AbhinayAmbati/chanakya/actions/workflows/ci.yml/badge.svg">
  <img alt="License" src="https://img.shields.io/badge/license-GPL--3.0-blue">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-informational">
  <img alt="Kotlin" src="https://img.shields.io/badge/kotlin-2.0-purple">
</p>

---

## Why Chanakya

Every app you install embeds third-party SDKs that quietly phone home with device
identifiers and behavioural data. Existing tools address parts of this problem but rarely
answer the question people actually care about: **which app is talking to which tracker,
and how often?**

Chanakya answers that — and proves nothing left the device.

## Features

- **Local-only VPN engine** — filtering happens entirely on-device; nothing is routed externally.
- **DNS-layer tracker blocking** — outbound DNS queries are checked against curated blocklists.
- **Per-app attribution** — maps blocked traffic to the app *and* the tracker network
  responsible (e.g. *Instagram → Meta, 14 calls today*).
- **Live tracker dashboard** — real-time and historical view grouped by app and network.
- **Per-app detail screen** — drill into a single app to see every tracker it contacted.
- **Advertising ID management** — surfaces Android's native ad-ID reset and reminds users.
- **Custom blocklists** — import and merge additional lists, or whitelist domains.
- **Daily/weekly reports** — trackers blocked, top offenders, estimated data saved.
- **No root required** and **fully open-source**.

## Screens

| Screen | Purpose |
| --- | --- |
| **Home** | Protection status, today's totals, quick actions. |
| **Apps** | Every app that contacted a tracker, ranked by activity. |
| **App detail** | Full per-app breakdown: networks, domains, timeline. |
| **Reports** | Daily and weekly summaries. |
| **Settings** | Blocklists, whitelist, ad-ID tools, about. |

## Architecture

Chanakya follows a clean, layered, multi-module architecture so that each concern can be
understood, tested and replaced in isolation. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
for the full picture.

| Module | Responsibility |
| --- | --- |
| `:app` | Compose UI, navigation, dependency wiring. |
| `:core:designsystem` | Colour palette, typography, reusable UI components. |
| `:core:data` | Domain models, Room persistence, repositories, blocklist catalogue. |
| `:core:vpn` | `VpnService`, DNS interception, packet handling, rule engine. |

The dependency direction is strictly one-way: `:app` → `:core:*`. Core modules never depend
on the UI, which keeps them unit-testable and reusable.

## Getting started

### Requirements

- Android Studio Ladybug (or newer)
- JDK 17
- Android SDK 35

### Build

```bash
# Assemble a debug build
./gradlew assembleDebug

# Run the unit tests
./gradlew testDebugUnitTest

# Static analysis
./gradlew lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Versioning & releases

Chanakya follows [Semantic Versioning](https://semver.org/) and a documented release
process. Version bumps, tagging and the changelog policy are described in
[`VERSIONING.md`](VERSIONING.md). Releases are produced only through the manual, gated
`Release` workflow — nothing is published without explicit maintainer approval.

## Contributing

Contributions are welcome. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a
pull request. All changes target the `main` branch directly; the project is deliberately
trunk-based.

## Privacy commitment

Chanakya collects **no** personal data. There is no analytics SDK, no account, and no
telemetry that leaves the device. The only network requests the app makes are to fetch
updated blocklists. This promise is verifiable: the source is open and the release builds
are reproducible.

## License

Licensed under the GNU General Public License v3.0. See [`LICENSE`](LICENSE).
