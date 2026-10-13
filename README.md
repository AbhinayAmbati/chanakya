# Chanakya

> See and stop the trackers inside your apps — on your phone, never on someone else's server.

**Chanakya** is an open-source Android app that creates a **local, on-device VPN** using
Android's `VpnService` API to inspect and block traffic bound for ad networks, analytics
SDKs and tracking domains. No traffic is ever routed to an external server. Interception,
filtering and logging all happen on the device.

<p align="left">
  <img alt="CI" src="https://github.com/AbhinayAmbati/chanakya/actions/workflows/ci.yml/badge.svg">
  <img alt="License" src="https://img.shields.io/badge/license-GPL--3.0-blue">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-informational">
  <img alt="Kotlin" src="https://img.shields.io/badge/kotlin-2.0-purple">
</p>

---

## Install

Chanakya is **not on Google Play**, deliberately. It is distributed as a signed APK built
by CI from the tagged commit.

1. Open the [latest release](https://github.com/AbhinayAmbati/chanakya/releases/latest).
2. Download **`chanakya.apk`**.
3. On your phone, allow installing from unknown sources for your browser or file manager.
4. Install, open the app, and tap **Turn on protection**. Android will ask you to approve
   the VPN connection — that is the local tunnel, and approving it is what lets Chanakya
   filter anything.

**Requires Android 8.0 (API 26) or newer.**

> **Read [Known limitations](#known-limitations) before you turn it on.** In particular,
> if your phone has **Private DNS** switched on, Chanakya cannot filter your DNS and on
> some devices your internet will stop working until you switch it off. This is the first
> thing to check if something seems broken.

---

## Why Chanakya

Every app you install embeds third-party SDKs that quietly phone home with device
identifiers and behavioural data. Most blockers tell you a number. Chanakya answers the
question people actually ask: **which app is talking to which tracker, and how often?**

## Features

- **Local-only VPN engine** — filtering happens entirely on-device. Nothing is routed
  externally, and there is no Chanakya server to route it to.
- **DNS-layer blocking** — outbound lookups are checked against curated blocklists.
  Blocked domains are answered with a dead address, so the connection is never made.
- **IP-level blocking (opt-in)** — for apps that skip DNS by connecting to a hardcoded
  address.
- **Per-app attribution** — every blocked request is tied to the app that made it, and to
  the tracker network behind it (*Instagram → Meta, 14 times today*).
- **Network and domain drill-down** — tap a network to see every domain it owns and every
  app on your phone talking to it; tap a domain for its full history and who asked for it.
- **Charts** — a seven-day trend and a split by kind of tracking (ad networks, analytics,
  social pixels, fingerprinting), drawn from your own local database.
- **User rules** — allow or block any domain globally or for a single app, pausable
  individually. Nothing Chanakya decides is final.
- **Custom blocklists** — import your own list from a file.
- **Advertising ID tools** — a shortcut to Android's ad-ID reset, plus an optional reminder.
- **Reports** — today, seven days and all time, with an estimated figure for data avoided.
- **No root. No account. No telemetry. Open source.**

## Screens

| Screen | Purpose |
| --- | --- |
| **Home** | Protection status, today's totals, live activity. |
| **Apps** | Every app that contacted a tracker, ranked by activity. |
| **App detail** | One app's networks and every domain it resolved, with rule controls. |
| **Network detail** | One tracker company across every app on the device. |
| **Reports** | Seven-day trend, category split, and per-window totals. |
| **Settings** | Appearance, protection, blocklists, rules, ad-ID tools, diagnostics, legal. |

Screenshots are not included yet — the honest way to judge the UI is to install it.

## Known limitations

Stated plainly, because a privacy tool that overstates itself is worse than one that
does not exist.

- **Private DNS must be off.** Android's Private DNS (often "Automatic" by default) sends
  DNS over TLS on port 853. Chanakya answers plain DNS on port 53, so it cannot read or
  filter those lookups — and on some devices the platform loses its resolver entirely and
  the connection drops until Private DNS is switched off. **Settings → Network & Internet →
  Private DNS → Off.** A proper fix is planned.
- **Encrypted DNS bypasses DNS filtering.** Any app using its own DNS-over-HTTPS or
  DNS-over-TLS talks past the filter. IP-level blocking, when enabled, catches apps that
  hardcode a resolver.
- **One VPN at a time.** Android allows a single active VPN, so Chanakya cannot run
  alongside another VPN app.
- **"Data saved" is an estimate, not a measurement.** It assumes a fixed average request
  size and is labelled as an estimate everywhere it appears.
- **It cannot erase an ad profile that already exists.** No phone-side tool can. What
  Chanakya does is stop *new* signal from leaking out.
- **Blocklist coverage is curated by hand** and is not exhaustive. Tracker networks rotate
  domains constantly.
- **Blocklists are bundled, not downloaded.** The app makes no network requests of its own.

## Architecture

Chanakya uses a clean, layered, multi-module architecture so each concern can be
understood, tested and replaced in isolation. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full picture.

| Module | Responsibility |
| --- | --- |
| `:app` | Compose UI, navigation, dependency wiring. |
| `:core:designsystem` | Colour palette, typography, reusable UI components, charts. |
| `:core:data` | Domain models, Room persistence, repositories, tracker catalogue. |
| `:core:vpn` | `VpnService`, DNS interception, packet codec, rule engine. |

The dependency direction is strictly one-way: `:app` → `:core:*`. Core modules never depend
on the UI, which keeps them unit-testable.

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

### Testing

The project has unit tests for the pure logic — the packet codec, DNS message parsing, the
rule engine and the uid resolver. There are **no instrumented or UI tests yet**, and no
device is in the CI loop, which means UI regressions are found by hand rather than by
machine. Contributions here are especially welcome; see
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## Versioning & releases

Chanakya follows [Semantic Versioning](https://semver.org/) and a documented release
process in [`VERSIONING.md`](VERSIONING.md). Releases are produced only through the manual,
gated `Release` workflow — nothing is published without explicit maintainer approval.
Every release is built and signed by CI from the tagged commit; the signing key never
enters the repository.

## Contributing

Contributions are welcome. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening
a pull request. All changes target `main`; the project is deliberately trunk-based.

The most useful things you can contribute right now:

- **Instrumented/UI tests**, so regressions are caught before release.
- **Blocklist entries** — especially India-specific SDKs that global lists miss.
- **Bug reports that name the domain.** If an app breaks, open the app's page in Chanakya
  and tell us which domain was blocked; that is usually enough to fix it.

## Privacy commitment

Chanakya collects **no** personal data. There is no analytics SDK, no account, and no
telemetry of any kind. Everything the app observes — the domains, the apps, the timings —
is written to a database **on your device** and never transmitted. Uninstalling the app
removes it.

The app makes **no network requests of its own**. The only traffic it generates is the DNS
lookups it relays on behalf of your apps, sent to the resolvers your device already uses.
Blocklists are bundled in the APK rather than downloaded.

This promise is verifiable rather than asserted: the source is open, the app contains no
third-party analytics or advertising libraries, and the release APKs are built in public
CI.

## License

Licensed under the GNU General Public License v3.0. See [`LICENSE`](LICENSE).

## Credits

Bundled typefaces — Space Grotesk, Inter and JetBrains Mono — are used under the SIL Open
Font License; the licence texts are in [`docs/fonts/`](docs/fonts).
