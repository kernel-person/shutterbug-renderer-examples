# Examples 1.2.0 verification record

2026-10-17. A new release of the four independent public-API examples, built against stable SDK **v1.0.2** for the matching Renderer **1.0.0** build. Older examples releases, SDK v1.0.1 and commercial downloads are not overwritten. The existing **1.1.0 resource pack** and its original artwork are unchanged.

## Scope and current behavior

- Packaging/version/documentation update only; no new example features in this release-preparation change. It packages the already tested camera, public POV and map-capture demo fixes.
- Camera aim follows six-way dispenser placement: north, south, east, west, straight up and straight down. The lens, capture origin and one-shot acceptance flash follow the same facing. Existing tagged cameras normalize to the live dispenser facing without replacing inventory or identity.
- Every POV receiver and monitor is public and photographable, including copied maps. `/pov demo` selects self without changing privacy mode. The default target is **4 updates/second**, best-effort, not guaranteed frame rate or latency; `/pov demo 1` through `/pov demo 20` changes the shared cadence until restart. Two active targets share one pipeline, with no queued frames or recording history.
- POV excludes only its target player's UUID. Current loaded framed-map pixels can appear in photographs and easel reference captures with the matching provider; self-feedback samples the previous completed image rather than recursively rendering.
- Native easel, brush, camera and monitor models remain optional. Public defaults contain no private server configuration or localhost pack URLs.

## Verification and remaining gates

JDK21 offline `mvn clean verify` passed **160 tests, zero failures/errors/skips**: Postcards 14, Camera 63, Easel 42, POV 41. Mockito was supplied as an explicit test JVM agent. Existing JVM class-sharing/deprecation notices and deliberate damaged-canvas/monitor-overflow fixture warnings remain; none caused a test failure.

Packaging passed **12 tests**, covering narrow JAR inventories, bundled API/provider/native/probe/secret rejection, localhost defaults, exact resource-pack bytes, stable release/SDK manifest metadata and deterministic output. The new release regressions first failed on the former 1.1.1 manifest and missing current evidence, then passed after the packaging update. The duplicate-member rejection fixture intentionally emits Python's duplicate-name warning.

The package manifest identifies the exact source commit, SDK version and SHA256 of every shipped file. A checksum sidecar identifies the complete ZIP; do not substitute old JAR hashes. Release handoff additionally compares fresh clean-build JARs and regenerated ZIPs byte for byte.

| Artifact | SHA256 |
| --- | --- |
| RendererPostcards.jar | `0d3267e30cf3213e5471a55dca8e899df61ad0c0ac88a067f0aac3ebe926fc15` |
| RendererRedstoneCamera.jar | `0983a4ce46af7b8fbe7da36433131b074aad3a110f87148637015596b369bb20` |
| RendererPaintersEasel.jar | `3a7d37ff28173a291bf43900df6169fb4854b2c78107f8972c4f9b2432b0618c` |
| RendererAdminPov.jar | `8d4eb5efcd7c9368b39347c492569f8303f38041a7a4f93e2d36b65e55c06886` |

The stable SDK coordinate is `com.github.kernel-person:shutterbug-renderer-api:v1.0.2`. Local preparation uses a freshly built API candidate with the same additive public source, never a renamed cached v1.0.1 JAR. Public tag/JitPack resolution is a separate release gate and must be verified before publication of this examples archive.

Exact-artifact licensed Paper acceptance, personalized buyer activation, sustained native timing and actual client appearance are not established by Java unit tests or packaging. Check all six camera directions, flash/photo pickup, painting, public copied-map viewing, owner-only commands/placement/removal, provider removal, stop and restart against the exact final artifacts. Solo footage is a demonstration, not cross-owner or buyer acceptance. No Minecraft client/launcher, live recording server, world or entitlement is modified by this release-preparation step. Windows remains coming soon; the Renderer supports macOS ARM64 and Linux x86-64 only.

Resource pack SHA256: `9fc02186d4cb0ac924e728259371635f14911350482595fa3e5c2edd949ec5cf`.

Historical records remain in [1.1.0 evidence](release-1.1.0-evidence.md) and [1.1.1 evidence](release-1.1.1-evidence.md). Their older POV privacy language and JAR hashes do not describe or verify 1.2.0.
