# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.15] - 2026-10-12

### Added

- Each bar in the seven-day chart now carries its figure above it.

### Changed

- About, Terms and Privacy moved below Diagnostics in Settings. They are reference
  material, read once rather than returned to.

## [1.0.14] - 2026-10-12

### Fixed

- The bar chart drew its bars in the track colour - a light grey on a white card, very
  nearly invisible - because the value box was missing entirely and `barColor` was never
  used.
- The seven-day series was built by matching SQLite's `strftime(..., 'localtime')` output
  against a date format built in Kotlin. Any disagreement about the day boundary produced
  no matches and an empty chart. It is now bucketed in memory from the same events the
  rest of the screen uses.

### Changed

- The bar chart no longer hides itself when every day is quiet; a chart that vanishes is
  indistinguishable from one that is broken.

## [1.0.13] - 2026-10-12

### Added

- Tapping a tracker network opens a cross-app screen: who it is, every domain it owns,
  and every app on the device talking to it.
- Tapping a domain opens a bottom sheet with its full history and the apps that asked for
  it, reachable from both an app's screen and a network's.

## [1.0.12] - 2026-10-12

### Added

- Charts on Reports: a seven-day trend and a split by kind of tracking, drawn by hand in
  the app's palette. No charting library was added.

## [1.0.11] - 2026-10-12

### Fixed

- Allowed lookups were relayed inline on the packet loop, so every lookup queued behind
  the previous one and one that timed out held the loop for up to six seconds. Apps that
  fire dozens of lookups at once - a shopping app loading a product grid - timed out
  waiting. Lookups are now relayed on their own coroutines.

### Changed

- Full-tunnel mode is parked. The relay had not been validated on a device and took the
  network down, so the setting is no longer read and the tunnel always builds in the
  DNS-only mode that works.

## [1.0.10] - 2026-10-12

### Fixed

- Full-tunnel mode declared no DNS server on a default-route VPN, leaving the platform
  with nothing to resolve with. It now advertises the device's real resolvers.
- Shutting protection down re-posted the ongoing notification, leaving a stray
  "protection active" notification behind after the user switched it off.

## [1.0.9] - 2026-10-11

### Fixed

- UDP is now mapped per source port rather than per destination, so the relay behaves
  like a cone NAT. Two symmetric NATs cannot hole-punch, which is why two phones running
  Chanakya could not complete a WhatsApp call while one on its own could.

## [1.0.8] - 2026-10-11

### Fixed

- `facebook.com` is no longer blocked as a whole domain. It is a platform rather than a
  tracker, and blocking it broke WhatsApp calls with "check internet connection". The
  tracking endpoints (`graph.facebook.com`, `connect.facebook.net`, `an.facebook.com`)
  remain blocked.

## [1.0.7] - 2026-10-11

### Added

- Full-tunnel mode: an option to route every packet through the tunnel and forward it
  with a UDP flow relay and a userspace TCP relay, so Android's Private DNS probe is
  carried like any other connection. Later parked in 1.0.11.
- About, Terms of use and Privacy screens, reachable from Settings.

## [1.0.6] - 2026-10-11

### Fixed

- Reverted the resolver change from 1.0.4. Advertising our own resolver is what makes the
  platform hand DNS to the tunnel at all; merely routing the real resolvers left the app
  observing nothing and reporting zero blocked.

## [1.0.5] - 2026-10-11

### Fixed

- The Apps list crashed while scrolling. The icon and uid caches stored misses as null,
  and `ConcurrentHashMap` rejects null values, so the first unreadable icon threw.

## [1.0.4] - 2026-10-11

### Changed

- Rode on the device's real resolvers instead of advertising one of our own, so Private
  DNS had a resolver it could use. Reverted in 1.0.6 - it stopped DNS capture entirely.

## [1.0.3] - 2026-10-11

### Fixed

- TCP that reaches the tunnel is now reset, so Android's Private DNS falls back to plain
  DNS instead of hanging.
- The ongoing notification sat at zero because it was built once at startup and never
  refreshed.
- The "Protected" card was white-on-white in dark mode: a fixed light background with a
  theme-derived text colour.
- The Home tab was unreachable from the other tabs; returning to the start destination
  with `saveState`/`restoreState` silently did nothing.

## [1.0.2] - 2026-10-11

### Fixed

- Protection no longer broke the device's internet connection.

### Changed

- The oversized navigation bar was replaced with a compact floating pill.

## [1.0.1] - 2026-10-11

### Fixed

- DNS interception could capture nothing after the tunnel was rebuilt, because the
  upstream resolver lookup read the active network - which is Chanakya's own tunnel -
  and found no resolvers. Resolvers are now read from a non-VPN network, and the tunnel
  routes both its own resolver and the device's real ones so capture no longer depends
  on that lookup succeeding.
- Allowed queries now fall back to public resolvers when the device exposes none, so
  resolution cannot hang.
- Release builds could fail to start background workers because R8 had no keep rule for
  them.

### Added

- Diagnostics section in Settings: protection state, loaded domains and IP ranges,
  routed resolvers, and per-session query and block counters.
- Appearance setting with System, Light and Dark options.

### Changed

- The bottom navigation bar now floats, inset from the screen edges.
- Release APKs are published as `chanakya.apk`.

## [1.0.0] - 2026-10-11

### Added

- Project scaffolding: multi-module Gradle build, version catalog, CI/CD workflows.
- Core design system: colour palette, typography (Space Grotesk / Inter / JetBrains Mono)
  and reusable components.
- Core data layer: domain models, Room persistence, repositories and tracker catalogue.
- Application UI: Home, Apps, App detail, Reports and Settings screens with navigation.
- Local VPN engine: `VpnService`, DNS interception, packet parsing and rule engine.
- Per-app attribution: DNS queries are resolved to the owning app via
  `ConnectivityManager.getConnectionOwnerUid`, with a `/proc/net/udp` fallback for
  older releases.
- Live persistence: the VPN service records every observation to the database, so the
  Home, Apps and app detail screens now reflect real device activity.
- User rules: allow or block any domain globally or for a single app, straight from the
  app detail screen.
- Blocklist management: four bundled lists (including an India-specific one) that can be
  toggled independently, plus import of custom hosts/domain lists.
- Background reports: a daily protection summary notification and an optional monthly
  advertising-ID reminder.
- Reports screen: live today / last-7-days / all-time aggregates with the busiest
  networks and apps.
- App detail: domains governed by a user rule are now marked, and app icons replace the
  letter avatars across the app.
- IP-level blocking: apps can no longer bypass DNS filtering via a hardcoded resolver or
  DNS-over-HTTPS. Blocked ranges are routed into the tunnel and dropped, and the device's
  own resolver is always excluded.
- IPv6 support: DNS interception and IP-level blocking now handle IPv6 as well as IPv4,
  so filtering works on IPv6-first mobile networks.
- Blocklist import now accepts IP ranges (IPv4 and IPv6 CIDR) alongside domains.
- Weekly protection summary notification, alongside the existing daily one.
- "Estimated data saved" is now shown on the Reports screen and in the summaries.
- User rules can be paused from Settings without deleting them, and exact duplicate
  rules are no longer stored.

### Fixed

- "Start on boot" now actually restarts protection after a reboot.
- The notification permission is requested on first launch, so the daily summary can
  appear on Android 13+.
- Blocked events are rolled up into daily aggregates and pruned after 30 days, keeping
  the database bounded while lifetime totals stay accurate.

### Removed

- Debug builds no longer seed demonstration data; every figure the app shows now comes
  from real device activity.

[Unreleased]: https://github.com/AbhinayAmbati/chanakya/commits/main
[1.0.1]: https://github.com/AbhinayAmbati/chanakya/releases/tag/v1.0.1
[1.0.0]: https://github.com/AbhinayAmbati/chanakya/releases/tag/v1.0.0
