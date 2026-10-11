# Architecture

## Goals

1. **Local by construction.** No code path sends user traffic or behavioural data off the
   device. This is a structural property, not a policy.
2. **Understandable.** A new developer should be able to follow a feature from a Compose
   screen down to the network layer without getting lost.
3. **Testable.** Core logic has no Android UI dependency and is covered by unit tests.

## Module graph

```
            ┌──────────┐
            │  :app    │   Compose UI, navigation, dependency container
            └────┬─────┘
      ┌──────────┼───────────┐
      ▼          ▼           ▼
┌───────────┐ ┌───────────┐ ┌───────────┐
│  :core:   │ │  :core:   │ │  :core:   │
│ designsys │ │   data    │ │    vpn    │
└───────────┘ └───────────┘ └─────┬─────┘
                                  │ depends on
                                  ▼
                            ┌───────────┐
                            │  :core:   │
                            │   data    │
                            └───────────┘
```

- `:core:designsystem` — palette, typography, reusable components. No business logic.
- `:core:data` — domain models, Room database, repositories, the tracker catalogue.
- `:core:vpn` — the `VpnService`, DNS interception and the rule engine. Depends on `:core:data`
  for rules and persistence.
- `:app` — the only module that knows about the others' UI.

## Layers within a module

```
ui        Compose screens + ViewModels (in :app)
   │
domain    models & repository interfaces (in :core:data)
   │
data      Room DAOs, DataStore, catalogue (in :core:data)
```

## The traffic pipeline

```
  App makes a DNS query
        │
        ▼
  Android routes it to the VPN DNS server
        │
        ▼
  ChanakyaVpnService  ── reads the packet from the TUN interface
        │
        ▼
  DnsInterceptor  ── parses the query, resolves the owning app (UID)
        │
        ▼
  RuleEngine  ── allow / block against the blocklist snapshot
        │
   ┌────┴─────┐
   ▼          ▼
 BLOCK      ALLOW
 return     forward to the real
 0.0.0.0    resolver, relay the answer
   │          │
   └────┬─────┘
        ▼
  Event recorded → Room → dashboard
```

## Dependency injection

Chanakya uses **manual dependency injection** through a single `AppContainer` created in the
`Application` class. This keeps the wiring explicit and readable — a deliberate choice over a
framework while the team is small. The container exposes repositories and managers to
ViewModels via their factories.

## Data model (high level)

- `AppInfo` — an installed application (uid, package, label).
- `TrackerNetwork` — a known tracker operator with a `TrackerCategory`.
- `BlockedEvent` — one observed DNS query and the decision taken.
- `AppUsageSummary` — an aggregate per app for the dashboard.

## Testing strategy

| Layer | Approach |
| --- | --- |
| Rule engine | Pure unit tests over a precedence matrix. |
| Packet parsing | Golden byte-array fixtures in / out. |
| Repositories | Unit tests over in-memory fakes. |
| UI | Compose previews + instrumented smoke tests. |
