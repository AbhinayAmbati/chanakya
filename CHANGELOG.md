# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

### Removed

- Debug builds no longer seed demonstration data; every figure the app shows now comes
  from real device activity.

[Unreleased]: https://github.com/AbhinayAmbati/chanakya/commits/main
