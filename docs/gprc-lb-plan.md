# gRPC Client-Side Load Balancing for Emissary — Options Review

## Context

Emissary's `emissary.grpc.ConnectionFactory` already configures `defaultLoadBalancingPolicy("round_robin")` on every `ManagedChannel` it creates (see `src/main/java/emissary/grpc/pool/ConnectionFactory.java:209`), with `host:port` as the target. In Kubernetes, when `host` is a headless service, gRPC's default DNS resolver returns the A records of all backend pods, and `round_robin` distributes RPCs across them.

**The problem we are seeing:** A gRPC backend (Triton, fronted by a Kubernetes headless Service with HPA on CPU) scales up under load. New pods come online, but receive **no traffic**. Existing pods remain saturated and HPA never scales back down — the load was never rebalanced.

**Root cause:** gRPC's DNS name resolver only re-resolves when triggered (subchannel failure, explicit refresh, or channel going idle). With healthy long-lived HTTP/2 connections to the original pods, the resolver never re-runs, and the new pods are invisible to the client. This is the well-known "[gRPC client-side LB problem](https://grpc.io/blog/grpc-load-balancing/)" with K8s headless services.

This document outlines the realistic options, their trade-offs and complexity, so the team can pick a direction.

---

## Options

### Option A — Periodic channel refresh (client-side, in emissary)

Add a scheduled task in `emissary.grpc.pool.ConnectionFactory` (or a new component owned by `GrpcRoutingPlace`) that periodically calls `ManagedChannel.getState(true)` or `enterIdle()` on each pooled channel to force the name resolver to re-run and discover new pods.

- **Complexity:** Low. ~50–150 lines of Java + tests, all inside `emissary.grpc.*`. Adds a `ScheduledExecutorService` and a couple of config keys (e.g., `GRPC_CHANNEL_REFRESH_INTERVAL_MILLIS`).
- **Files touched:** `ConnectionFactory.java`, `GrpcRoutingPlace.java`, new config keys, new unit tests.
- **Pros:**
  - Pure framework change — benefits every emissary gRPC Place automatically.
  - No infrastructure work, no server-side cooperation needed.
  - Bounded blast radius; can be feature-flagged via config and default to off.
- **Cons:**
  - New pod discovery latency = refresh interval (typically 30s–5min).
  - Each refresh causes a brief reconnect blip on affected channels (subchannel re-establishment).
  - Does **not** rebalance load away from already-saturated pods until refresh fires, because existing RPCs in flight stick to their subchannel.
  - DNS TTL still governs actual freshness; needs reasonable JVM DNS cache settings (`networkaddress.cache.ttl`).
  - `enterIdle()` / `getState(true)` semantics can be subtle; needs careful testing under load.

### Option B — `MAX_CONNECTION_AGE` on the server

Configure the gRPC server (Triton supports `--grpc-max-connection-age`) to send a `GOAWAY` after N minutes. Clients handle `GOAWAY` natively: they finish in-flight RPCs, close the connection, re-resolve DNS, and reconnect — picking up new pods on each cycle.

- **Complexity:** Trivial server-side config change. Possibly zero emissary changes (or a tiny tweak to set `dns:///` scheme explicitly).
- **Files touched:** Triton deployment manifest. Optionally `ConnectionFactory.java` to switch target format.
- **Pros:**
  - This is the gRPC team's officially recommended pattern for this exact problem.
  - No client code, no new infrastructure.
  - Clean failover semantics — `GOAWAY` handling is well-tested in grpc-java.
  - Combines naturally with Option A or D.
- **Cons:**
  - Requires server-side control. Works for Triton; does **not** work for third-party gRPC services we don't own.
  - Reconnects on a fixed cadence, not on load — doesn't immediately relieve a saturated pod.
  - Long-running streaming RPCs get interrupted at each cycle (not relevant for Triton inference; matters for other places).
  - Each emissary process/pod must independently cycle; if many pods cycle together, brief thundering-herd risk (mitigate with `MAX_CONNECTION_AGE_GRACE` + jitter).

### Option C — Custom `NameResolver` with active polling

Implement `emissary.grpc.resolver.RefreshingDnsNameResolverProvider` (or a K8s-EndpointSlice-aware variant) registered via SPI. Polls DNS (or the K8s API) on a short interval and pushes endpoint updates to the channel through the `NameResolver.Listener2` interface, so `round_robin` immediately sees new subchannels.

- **Complexity:** Medium–High. ~200–500 lines + SPI registration + tests. K8s-API variant adds the `kubernetes-client` dependency, RBAC for the service account, and namespace/label config.
- **Files touched:** new package `emissary.grpc.resolver`, SPI file under `META-INF/services/io.grpc.NameResolverProvider`, `ConnectionFactory.java` (use the new scheme), docs.
- **Pros:**
  - New pods become routable within seconds, not minutes.
  - No server-side dependency.
  - Framework-wide benefit, works for any gRPC backend.
  - The K8s-API variant can use Pod readiness and not just DNS, which is more accurate.
- **Cons:**
  - Significantly more code to own and maintain.
  - `NameResolver` API is subtle (sync context, lifecycle, error semantics); easy to get wrong.
  - K8s-API variant requires cluster RBAC and only works in-cluster.
  - DNS-poll variant still bounded by DNS TTL; less of a win over Option A than it looks.
  - Reinvents what Envoy/xDS already do.

### Option D — Proxy in front of the gRPC service (HAProxy / Envoy / NGINX)

Deploy a Layer-7 proxy as a Kubernetes Deployment in front of Triton (or any gRPC backend). Clients talk to one stable proxy address; the proxy watches the K8s endpoints and load-balances across pods. This is the "Proxy LB" pattern from the [gRPC blog post](https://grpc.io/blog/grpc-load-balancing/#proxy-or-client-side).

- **Complexity:** Low–Medium code-wise (zero emissary changes — only `GRPC_HOST` is repointed at the proxy). Medium operational: deploy the proxy, configure it for HTTP/2/gRPC, run it HA, monitor it.
- **Files touched:** Kubernetes manifests; no Java code. Possibly emissary config updates.
- **Pros:**
  - Zero emissary code change.
  - Proxy is already K8s-aware (especially Envoy via the Endpoints API).
  - Fixes the problem for **all** clients of the backend, not just emissary.
  - Unlocks observability, retries, rate-limiting, circuit-breaking at the proxy.
  - Lowest-risk for the framework; problem moves to infra where K8s tooling is mature.
- **Cons:**
  - Adds infrastructure to deploy, monitor, version, and on-call for.
  - Sub-millisecond latency hop per RPC (usually negligible for Triton; verify).
  - Proxy itself needs HA (≥2 replicas + a Service in front) — solves one HA problem by creating another.
  - Additional cost (pod resources).
  - HTTP/2 + gRPC proxying requires correct config (HAProxy needs `mode http` with `proto h2`; Envoy is purpose-built and easier).

### Option E — xDS / look-aside load balancing (e.g., Istio, gRPC-xDS)

Use the `xds:///` target scheme with an xDS control plane (Istio service mesh or standalone gRPC-xDS). The control plane pushes endpoint and policy updates to clients out-of-band.

- **Complexity:** High operationally. Requires an xDS control plane and per-cluster integration.
- **Pros:** Industry-standard, most powerful, supports advanced policies (weighted, locality-aware, outlier detection).
- **Cons:** Way out of scale for the problem at hand unless we're already adopting a service mesh. Listed for completeness only.
- **Recommendation:** Defer unless service-mesh adoption is already on the roadmap.

---

## Comparison summary

| Option | Code change | Infra change | Discovery latency | Server-side dep | Operational cost |
|---|---|---|---|---|---|
| A. Periodic refresh | Small (emissary) | None | 30s–5min (configurable) | None | Low |
| B. `MAX_CONNECTION_AGE` | None / tiny | Server config | N minutes (age) | Yes | Very low |
| C. Custom NameResolver | Medium (emissary) | RBAC if K8s API | Seconds | None | Medium |
| D. Proxy (HAProxy/Envoy) | None | Deploy + operate proxy | Seconds (K8s-driven) | None | Medium |
| E. xDS look-aside | Small (config) | Control plane | Seconds | None | High |

---

## Team constraints (confirmed)

- We run **multiple gRPC backends**, not just Triton/Vista. We control the **deployment** (manifests, server flags) but **not the container image / source** for some of them.
- Acceptable new-pod discovery latency is **1–2 minutes**.
- **No service-mesh adoption** planned in the near term.

## Recommendation

**Land Option A and use Option B wherever the server supports it.**

1. **Option A — periodic channel refresh in emissary (primary fix).** Add the refresh mechanism in `emissary.grpc.pool` with a default interval of ~60s (well inside the 1–2 min target). This is one change that covers every current and future gRPC Place, and removes the dependency on each backend's server flags. Opt-in per Place via config; default off so existing deployments are unaffected until they enable it.
2. **Option B — set `MAX_CONNECTION_AGE` on every server where the flag is available.** Since we control deployment for all our gRPC services, we can pass this flag (typically a server CLI arg or env var, not a build-time setting) on Triton, Vista, and any others that expose it. Use a value like 5min + 30s grace + jitter. This complements Option A: even if the client-side refresh is turned off or fails, the server still forces eventual reconnection.

Together these are belt-and-suspenders: A guarantees coverage; B gives clean GOAWAY semantics on the servers that support it.

**Defer Options C, D, E.**
- **D (Envoy proxy)** is the natural next step *if* this stops being enough — e.g., we want sub-second rebalancing, richer observability, or we start adopting a mesh. With no mesh plans and a 1–2 min target, Option A alone meets the requirement at a fraction of the operational cost.
- **C (custom NameResolver)** mostly duplicates what Envoy does, with more code to own. Skip unless Option A proves insufficient and we still don't want a proxy.
- **E (xDS)** is off the table until a service-mesh decision is made.

---

## Interaction with open PR #1400 ("New gRPC channel manager")

[PR #1400](https://github.com/NationalSecurityAgency/emissary/pull/1400) (open, not yet merged) replaces `emissary.grpc.pool.ConnectionFactory` with a pluggable `emissary.grpc.channel.ChannelManager` abstraction and two concrete subclasses: `PooledChannelManager` (current behavior) and `SingletonChannelManager` (one shared channel). It also adds a `GRPC_CHANNEL_MANAGER_CLASS_NAME` config key so each Place picks its manager, makes `ChannelManager` `AutoCloseable`, and threads close-on-shutdown through `GrpcInvoker.close()` → `GrpcRoutingPlace.shutDown()`.

**Effect on this plan:**
- **No effect on the recommendation.** Channel creation in the PR (`ChannelManager.create()`) is byte-for-byte the same as today — same `host:port` target, same `defaultLoadBalancingPolicy("round_robin")`. The HPA stale-connection problem is unchanged by the PR.
- **Positive effect on implementation.** The new abstraction is a strictly better place to hang Option A than the current pool. Specifically:
  - Refresh is a per-channel concern, not a per-pool concern, so it belongs in `ChannelManager`. Putting it in the base class means **both** `PooledChannelManager` and `SingletonChannelManager` inherit it for free.
  - `ChannelManager.close()` already exists and is wired into Place shutdown — natural place to stop the scheduler. No new lifecycle plumbing needed.
  - The `GRPC_CHANNEL_*` config-key prefix in the PR aligns with the `GRPC_CHANNEL_REFRESH_INTERVAL_MILLIS` name we'd add.
- **Timing.** Land Option A *after* PR #1400 merges to avoid a rebase. If #1400 stalls, Option A can still be built on the current `ConnectionFactory` and ported later — the surface area is small either way.

## Implementation sketch (Option A — assuming PR #1400 has merged)

Files to modify:
- `src/main/java/emissary/grpc/channel/ChannelManager.java`
  - Add config key `GRPC_CHANNEL_REFRESH_INTERVAL_MILLIS` (default `-1` = disabled).
  - Add a protected `ScheduledExecutorService` and a `protected abstract Collection<ManagedChannel> channelsForRefresh()` method that subclasses implement.
  - In the constructor, if the interval is > 0, schedule a periodic task that iterates `channelsForRefresh()` and calls `channel.getState(true)` on each to force the name resolver to re-run.
  - Extend `close()` to shut down the scheduler.
- `src/main/java/emissary/grpc/channel/PooledChannelManager.java` — implement `channelsForRefresh()` by snapshotting the channels currently in the pool. (Apache Commons `GenericObjectPool` doesn't expose its objects directly; easiest path is to track created channels in a `Set` populated by `makeObject()` and pruned by `destroyObject()`.)
- `src/main/java/emissary/grpc/channel/SingletonChannelManager.java` — implement `channelsForRefresh()` by returning `List.of(channel)`.
- New unit tests under `src/test/java/emissary/grpc/channel/` covering both subclasses: scheduler fires on the configured interval, calls the channel API, is shut down by `close()`.

The refresh API call itself: for each `ManagedChannel`, call `channel.getState(true)` — the `true` argument requests a connection attempt, which through the gRPC state machine causes the name resolver to be refreshed if the channel is in `IDLE`. For channels in `READY`, an additional option is to call `NameResolver.refresh()` via the channel's internal resolver (requires retaining a `ManagedChannelBuilder.nameResolverFactory(...)` reference). Prototype both and benchmark. *(The exact API choice is the first thing to validate during implementation — there are two correct-looking paths in grpc-java and the right one depends on subchannel state.)*

Reusable pieces already in the codebase (post-#1400):
- `ChannelManager.close()` — the natural hook to shut down the refresh scheduler.
- `GrpcInvoker.close()` → `ChannelManager.close()` → place `shutDown()` — close-lifecycle already plumbed.
- The `Configurator` config pattern used throughout (`findLongEntry`, `findIntEntry`).

---

## Verification

For the Option A code change:
- **Unit tests:** mock `ManagedChannel`; assert the scheduler fires at the configured interval, calls the expected channel API, and is shut down with the Place.
- **Integration test:** spin up two in-process gRPC servers on different ports behind a custom in-process `NameResolver` that returns one server initially and both after a delay. Drive RPCs through an emissary `GrpcConnectionPlace` and confirm that after the refresh interval, RPCs round-robin to both backends.
- **End-to-end test in a dev K8s cluster:** deploy Triton behind a headless Service with HPA. Drive load until HPA scales out. Without the change, confirm new pods stay idle. With the change enabled at e.g. 60s interval, confirm RPCs reach new pods within ~1 refresh interval (verify via Triton's per-pod request counters / Grafana).

For Option B (Triton `MAX_CONNECTION_AGE`): the same dev-cluster experiment but with the server flag set and no client code changes. Confirm reconnection cycle works and new pods get traffic within `MAX_CONNECTION_AGE + GRACE` of scale-out.

For Option D (proxy): standard L7 proxy validation — `kubectl rollout restart` a backend pod and confirm the proxy reroutes within seconds; load-test for sustained throughput and verify added latency is within budget.

---

## Open questions for the team

1. Which of our gRPC servers actually expose a `MAX_CONNECTION_AGE` (or equivalent) flag we can pass via deployment? Triton does (`--grpc-max-connection-age`); we should inventory the rest.
2. What refresh interval should Option A default to? 60s is inside the 1–2 min target and gives a reasonable safety margin; shorter trades reconnect churn for faster discovery.
3. Should Option A's refresh be opt-in per Place or opt-out (default-on framework-wide)? Opt-in is safer to roll out; opt-out gives more immediate coverage.
4. Are any of our gRPC Places using long-running streaming RPCs that would be disrupted by a forced refresh? (Inference-style unary RPCs are fine; streaming needs more thought.)
