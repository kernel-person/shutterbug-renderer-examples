# Examples 1.3.0 verification record

2026-10-24. Release preparation for four independent examples using `ke.ric:renderer-api:2.3.0-preview.3` from `https://shutterbug-maven.pages.dev/`, with Renderer **1.1.0** required at runtime. The exact 1.1.0 commercial provider acceptance and public examples publication remain pending.

## Source and scope

The release starts from the reviewed recipe migration `2816d215869ef1b2068c384a4c7546a842b6748f`. This preparation changes release metadata, dependency coordinates, documentation and packaging provenance; it does not change Java application or test behavior. Postcards and Camera use `snapshot(...).captureMap(...)`, Easel uses snapshot capture, and POV uses SDK streams with map sinks. The plugin ZIP and separate source ZIP are generated from the same clean commit, recorded in `manifest.json` and the Git source archive comment.

The resource pack remains `renderer-examples-resourcepack-1.1.0.zip`, SHA-256 `9fc02186d4cb0ac924e728259371635f14911350482595fa3e5c2edd949ec5cf`. Previous releases and dated evidence remain unchanged.

## Verification

JDK 21 `mvn -U clean verify` passed against a fresh local Maven repository with empty anonymous user and global settings. It downloaded the published preview.3 SDK from the canonical public repository: SHA-256 `909e811de2a9147b291450383677843de81391fe38bb6027e47e6661fad47cf1`. Maven recorded the `shutterbug` repository as the origin of both the SDK JAR and POM. Every test module's recorded classpath points to that freshly resolved artifact.

The full build passed **148 tests, zero failures/errors/skips**: Postcards 15, Camera 64, Easel 41 and POV 28. Mockito was supplied explicitly as a test JVM agent. The deliberate damaged-canvas and monitor-overflow fixture warnings did not cause a failure. The separate disposable-server probe also compiled against the same resolved SDK; its runtime checks remain pending.

Packaging regressions passed **12 tests**, including forbidden payloads, default URL checks, unchanged resource-pack bytes, current release/SDK metadata, immutable source links and deterministic ZIP output. The new release checks first failed on snapshot metadata and missing current evidence. The duplicate-member rejection test intentionally emits a ZIP warning.

The four tested JARs are:

| Artifact | SHA-256 |
| --- | --- |
| RendererPostcards.jar | `af2a693ebf95f08bc78829c0e0b400e201e5373d9ea920550614913dfc3ff4c8` |
| RendererRedstoneCamera.jar | `1ac675f670381a5bbc0fb1938fc79b3ec40280eadf7ab09ed1120c0f4c3afc6a` |
| RendererPaintersEasel.jar | `627c4658fcc4ab9de2cc99cefce5994ac65979a6b67171d47dd1f910fd3455ba` |
| RendererAdminPov.jar | `16375705e7532e4976b5624c08f8634e6a63246a00f78878eab24d44e80267e0` |

The final plugin bundle manifest and adjacent ZIP checksum files record the generated delivery hashes. No historical artifact hash is reused. Source and plugin delivery must name the same clean commit; public upload/readback remains pending.

## Remaining runtime and publication checks

- Accept the exact commercial Renderer 1.1.0 platform pair together with these four JARs on isolated Paper 26.2 / Minecraft 26.2 / Java 25. Bind both source commits, SDK identity and artifact hashes to that evidence.
- Verify postcard/camera output persistence and old map restoration, six camera directions and stale destination rejection, easel capture/painting/persistence, public POV shared-target demand and stop/permission/target/provider invalidation, cadence changes and cached restart. Clearly label any Linux emulation.
- Record actual client visual checks and sustained timing separately; no Minecraft client is launched by automated preparation.
- Publish the source commit/tag and both archives together, then verify remote readback. Clean anonymous public Maven resolution has passed as recorded above; public examples publication has not. Keep earlier artifacts intact.

Neither archive includes a commercial provider, native binary, test probe JAR, license key or generated target directory. The source archive includes the test probe's source for disposable-server acceptance only.
