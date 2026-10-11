# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
