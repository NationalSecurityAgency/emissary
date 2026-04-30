# Security and Reporting Process

## Emissary Is a Framework

Emissary is a **data-processing framework and reference implementation**, not a
turnkey secured application. It provides building blocks (places, routes,
configuration APIs, a web console) that integrators assemble into production
systems. The framework ships with defaults that make it easy to run locally and
experiment with; those defaults are not appropriate for production use without
operator hardening.

**Securing a production Emissary deployment is the operator's responsibility.**
The sections below describe what the project provides and what operators must
supply.

---

## Operator Responsibilities

### Trusted-Perimeter Deployment

Emissary is designed to run inside a trusted network perimeter where only
authorized operators can reach the web console and REST API. Exposing Emissary
nodes directly to untrusted networks without additional controls (firewall rules,
reverse proxy with authentication, VPN, etc.) is not a supported configuration.

Diagnostic endpoints such as `/emissary/Environment.action` and thread-dump
endpoints are intentionally accessible to all authenticated users within the
trusted perimeter. Network-level controls are the primary mitigation for
unauthenticated access to these endpoints.

### Default Credentials

The `jetty-users.properties` shipped with the project defines demo accounts
(`emissary`, `console`) with well-known passwords. These credentials **must be
rotated before any production or shared deployment**. See the `[!CAUTION]` block
in `README.md` for the complete list of default accounts and instructions for
replacing them.

Accounts that require access to destructive operations (e.g., `/api/shutdown`)
must be assigned the `admin` role. Neither default account carries the `admin`
role by design.

### TLS / SSL

Emissary ships a sample self-signed keystore for single-host development use.
Multi-host and production deployments must use a properly issued certificate and
configure TLS appropriately. See the `[!CAUTION]` block in `README.md` covering
SSL/TLS configuration.

### Configuration Integrity

Emissary configuration values are read from files and passed into sensitive
operations (shell execution, filesystem access, template rendering). The
framework validates and sanitizes these values, but the overall security posture
depends on controlling who can write configuration files or call configuration
APIs. Operators must:

- Restrict filesystem write access to Emissary config directories.
- Restrict which accounts can call mutating configuration endpoints.
- Audit configuration values before deploying or promoting to a higher environment.

### Cluster Coordination

Peer-to-peer cluster operations (pause, refresh, cache invalidation) use shared
credentials. Operators are responsible for rotating those credentials and
ensuring inter-node traffic is confined to an isolated network segment or
encrypted transport.

---

## What the Project Provides

- **Input validation on an allowlist basis:** configuration names, place names,
  and navigation link values are validated with allowlist regular expressions
  rather than blacklists, rejecting unexpected characters before they reach
  sensitive sinks.
- **Role-based constraints on destructive endpoints:** the `/api/shutdown`
  endpoints require the `admin` role at both the container constraint and
  application layer.
- **Sanitization of CI/CD inputs:** GitHub Actions workflow inputs are passed
  through environment variable indirection and validated against strict regexes
  before use in shell commands.
- **Security hardening guidance** in `README.md`: prominent caution blocks for
  credentials, TLS, and default configuration.

These controls reduce the blast radius of misuse within the framework, but they
are not a substitute for network-level controls, credential hygiene, and a
properly scoped trust boundary that operators must establish around the cluster.

---

## Supported Versions

We maintain security support for the **most recent release only** and follow a
fail-forward approach. Discovered vulnerabilities that are resolved will be
addressed in an updated minor or patch release. Older release lines do not
receive backported security fixes.

---

## Reporting a Vulnerability

Please report security issues using [GitHub's private vulnerability reporting](https://github.com/NationalSecurityAgency/emissary/security/advisories/new).
This keeps the disclosure confidential until a fix is available.

Include in your report:

- A description of the vulnerability and the component affected.
- Steps to reproduce or a proof-of-concept.
- The version(s) you tested against.
- Your assessment of impact and any mitigating factors.

We will make every effort to acknowledge receipt promptly and address
responsibly disclosed vulnerabilities in a timely fashion. We ask that you
allow us reasonable time to investigate and patch before public disclosure.
