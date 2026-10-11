# Security Policy

## Supported versions

Only the latest released version receives security fixes.

## Reporting a vulnerability

Please report suspected vulnerabilities privately to the maintainers rather than opening a
public issue. Include:

- A description of the issue and its impact.
- Steps to reproduce.
- Any suggested remediation.

We aim to acknowledge reports within a few days and to ship a fix as promptly as the issue
warrants.

## Design commitments

- **No traffic leaves the device.** The app performs no data collection.
- **No secrets in the repository.** Signing material is supplied only through encrypted CI
  secrets and is never committed.
- **Dependencies are pinned** and reviewed; automated updates arrive via Dependabot and are
  gated by CI.
