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

### Option E — Lightweight xDS look-aside (control plane only, no data plane)

Use the `xds:///service-name` target scheme with a **control-plane-only** xDS server such as [wongnai/xds](https://github.com/wongnai/xds). The control plane watches Kubernetes endpoints and pushes endpoint updates to gRPC clients via the [xDS streaming protocol](https://www.envoyproxy.io/docs/envoy/latest/api-docs/xds_protocol). gRPC-java's built-in `xds` resolver applies updates to the channel within seconds — no proxy data path, no sidecars, no mesh injection. This is the "look-aside LB" pattern from the original gRPC LB design and is what makes the xDS approach distinct from a full service mesh.

- **Complexity:** Medium. ~1 control-plane Deployment (a few hundred lines of YAML and a maintained off-the-shelf image), plus per-Place config flips from `host:port` to `xds:///service-name`, plus a gRPC bootstrap config delivered via the `GRPC_XDS_BOOTSTRAP_CONFIG` env var on every emissary process. Add `grpc-xds` to the maven dependencies. No new Java code in emissary itself if we keep `ChannelManager.create()`'s target format flexible.
- **Pros:**
  - **Sub-second** new-pod discovery — well past the 1–2 min target.
  - Solves the problem for **every** gRPC backend uniformly, with no per-server config and no per-Place code.
  - No data-plane hop, no added latency, no proxy HA to operate.
  - Standard `xds:///` is the long-term gRPC ecosystem direction; investment compounds if/when a mesh follows.
  - Decouples LB policy from app code — can roll out weighted, locality-aware, or health-aware balancing as a control-plane config change later.
- **Cons:**
  - Adds a new infrastructure component to deploy, monitor, upgrade, and on-call for.
  - Bootstrap config delivery to every emissary pod is a new ops concern (typically a ConfigMap mount + env var).
  - Failure mode if the control plane is unavailable depends on gRPC-java's xDS cache behavior — needs verification.
  - `wongnai/xds` is community-maintained and lightweight; if it stops being maintained we'd be on the hook. (Alternatives exist: `go-control-plane`-based custom server, or the gRPC team's reference impls.)
  - Slightly more nuanced than the "one Java change" of Option A — but materially less than a full service mesh.

### Option F — Full service mesh (Istio / Linkerd / Consul Connect)

A full mesh with sidecars, mTLS, and unified policy. Listed for completeness; not under consideration. Lightweight xDS (Option E) is a stepping stone toward this if it ever becomes appropriate.

---

## Comparison summary

| Option | Code change | Infra change | Discovery latency | Server-side dep | Operational cost |
|---|---|---|---|---|---|
| A. Periodic refresh | Small (emissary) | None | 30s–5min (configurable) | None | Low |
| B. `MAX_CONNECTION_AGE` | None / tiny | Server config | N minutes (age) | Yes | Very low |
| C. Custom NameResolver | Medium (emissary) | RBAC if K8s API | Seconds | None | Medium |
| D. Proxy (HAProxy/Envoy) | None | Deploy + operate proxy | Seconds (K8s-driven) | None | Medium |
| E. Lightweight xDS look-aside | Tiny (target scheme + dep + bootstrap) | Deploy + operate control plane | Sub-second | None | Medium |
| F. Full service mesh | Small (config) | Mesh install + ops | Sub-second | None | High |

---

## Team constraints (confirmed)

- We run **multiple gRPC backends**, not just Triton/Vista. We control the **deployment** (manifests, server flags) but **not the container image / source** for some of them.
- Acceptable new-pod discovery latency is **1–2 minutes** as a baseline; sub-second is welcomed if cheap.
- **Team is exploring lightweight xDS look-aside (Option E)** as the long-term direction, using a control-plane-only server like `wongnai/xds`. Full service mesh (Option F) is **not** on the table.

## Recommendation

**Target Option E (lightweight xDS look-aside) as the durable solution. Use Option A as an optional short-term bridge only if HPA pain is acute *now*.**

1. **Option E — long-term, durable fix.** Stand up a control-plane-only xDS server (e.g., `wongnai/xds`) that watches K8s endpoints. Add the `grpc-xds` dependency to emissary, allow `ChannelManager.create()` to use `xds:///service-name` targets, and deliver the gRPC bootstrap config via env var + ConfigMap to every emissary pod. After this lands, every gRPC Place is rebalanced in sub-second time on HPA events, with no per-Place or per-server config to maintain.
2. **Option A — optional bridge.** If the HPA stall is hurting production today and Option E will take more than a sprint or two to land, ship the periodic channel refresh first as a contained emissary change. It meets the 1–2 min target and is throwaway code we'd remove once E is live. **If pain is tolerable, skip A and put the engineering time into landing E directly** — A is dead weight once xDS is in place.
3. **Option B — keep on the table as defense in depth.** Setting `MAX_CONNECTION_AGE` on servers we deploy costs nothing and is useful even after E lands (it limits the worst-case stale-state window if the control plane has issues). Worth doing opportunistically; not a primary fix.

**Decline Options C, D, F.**
- **C (custom NameResolver)** reinvents what gRPC's xds resolver already provides. Once we're going to E, C has no role.
- **D (Envoy proxy)** adds a data-plane hop that E specifically avoids. Skip.
- **F (full service mesh)** is explicitly out of scope. E is the stepping stone if a mesh is ever wanted later.

---

## Implementation sketch (Option E — recommended target)

**Infrastructure (most of the work is here):**
- Deploy a control-plane-only xDS server in-cluster. `wongnai/xds` is a reasonable starting point; alternatives: a `go-control-plane`-based custom server, or `gloo`/`cilium` if already in the stack.
  - Give it a ServiceAccount with RBAC to read Endpoints / EndpointSlices for the relevant namespaces.
  - Run ≥2 replicas behind a normal ClusterIP Service so the control plane itself is HA.
  - Expose its xDS gRPC port (typically `18000`).
- Author a gRPC bootstrap JSON (see [grpc-java xDS docs](https://github.com/grpc/grpc-java/tree/master/xds)) that points at the control-plane Service. Ship it as a ConfigMap mounted into each emissary pod. Set `GRPC_XDS_BOOTSTRAP_CONFIG` (or `GRPC_XDS_BOOTSTRAP`) env var to the file path.

**Emissary changes (small):**
- `pom.xml` — add `io.grpc:grpc-xds`.
- `src/main/java/emissary/grpc/channel/ChannelManager.java` — let `target` accept arbitrary gRPC target URIs (today it's hard-coded to `host + ":" + port`). Either add a `GRPC_CHANNEL_TARGET_OVERRIDE` config key, or let `GRPC_HOST` accept a full URI like `xds:///inference-service`. Same `defaultLoadBalancingPolicy("round_robin")` setting continues to work — `round_robin` operates over xDS-supplied endpoints exactly as it does over DNS-resolved ones.
- Place configs — update `GRPC_HOST_*` entries to `xds:///service-name` for the migrated backends. Roll out one Place at a time.

**Verification:**
- Stand up the control plane in a dev cluster pointed at a Triton-like backend behind a normal (non-headless) Service.
- Drive load through emissary with `GRPC_HOST=xds:///triton`. Confirm RPCs land on all backend pods.
- Scale the backend up (HPA or manual). Confirm new pods receive traffic within seconds — verified via per-pod metrics on the backend.
- Kill a backend pod. Confirm clients re-balance immediately.
- Kill the control plane. Confirm clients continue serving from cached endpoints for at least a few minutes (verify gRPC-java xDS resolver cache behavior; this is the failure mode that most needs to be measured).

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

1. **Urgency of the HPA stall**: do we need a fix in days (→ land Option A as a bridge while Option E is built) or weeks (→ go straight to Option E and skip A)? Drives whether A is worth doing at all.
2. **Control-plane ownership**: which team owns the xDS control plane in production — platform/infra, or this team? Affects timeline and on-call story.
3. **Control-plane availability behavior**: what happens to traffic when the xDS server is unreachable? gRPC-java's xDS client caches endpoints, but we need to characterize the failure window and decide whether to run the control plane HA + on-call (yes by default), and whether Option B (`MAX_CONNECTION_AGE`) should still be set as a safety net.
4. **`wongnai/xds` vs alternatives**: is community-maintained acceptable, or do we want to fork / write our own thin server on `go-control-plane`? Affects long-term maintenance burden.
5. **Bootstrap distribution**: who owns the ConfigMap and env-var wiring for emissary pods? Likely the same team that owns the Helm chart / deployment manifests.
6. **Streaming RPCs**: do any current or near-future gRPC Places use long-running server-streaming or bidi RPCs? xDS handles these fine, but it's worth knowing if any Place would need special handling.
