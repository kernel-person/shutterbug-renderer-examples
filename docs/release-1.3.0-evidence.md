# Examples 1.3.0 verification record

2026-10-24. Four independent examples using `ke.ric:renderer-api:2.3.0-preview.3` from `https://shutterbug-maven.pages.dev/`, with Renderer **1.1.0** required at runtime. The exact plugin JARs below passed bounded server acceptance on macOS ARM64 and Linux x86-64. Windows acceptance covered the commercial provider/shipped sample only. These results do not establish client visuals or the full POV workflow.

## Source and scope

The accepted plugin JARs were built from candidate source `b94112fb75f94c52bd22575407d3435ca2efa183`, following the reviewed recipe migration `2816d215869ef1b2068c384a4c7546a842b6748f`. The final release commit updates documentation only. Its plugin bundle retains the accepted four JARs and resource pack byte for byte; it does not rebuild Java application or test code. Postcards and Camera use `snapshot(...).captureMap(...)`, Easel uses snapshot capture, and POV uses SDK streams with map sinks. The plugin ZIP and separate source ZIP are generated from the same clean commit, recorded in `manifest.json` and the Git source archive comment.

The resource pack remains `renderer-examples-resourcepack-1.1.0.zip`, SHA-256 `9fc02186d4cb0ac924e728259371635f14911350482595fa3e5c2edd949ec5cf`. Previous releases and dated evidence remain unchanged.

## Verification

JDK 21 `mvn -U clean verify` passed against a fresh local Maven repository with empty anonymous user and global settings. It downloaded the published preview.3 SDK from the canonical public repository: SHA-256 `909e811de2a9147b291450383677843de81391fe38bb6027e47e6661fad47cf1`. Maven recorded the `shutterbug` repository as the origin of both the SDK JAR and POM. Every test module's recorded classpath points to that freshly resolved artifact.

The full build passed **148 tests, zero failures/errors/skips**: Postcards 15, Camera 64, Easel 41 and POV 28. Mockito was supplied explicitly as a test JVM agent. The deliberate damaged-canvas and monitor-overflow fixture warnings did not cause a failure. The separate disposable-server probe also compiled against the same resolved SDK. Its accepted runtime scope is recorded below; it is not included as a binary in the plugin bundle.

Packaging regressions passed **12 tests**, including forbidden payloads, default URL checks, unchanged resource-pack bytes, current release/SDK metadata, immutable source links and deterministic ZIP output. The new release checks first failed on snapshot metadata and missing current evidence. The duplicate-member rejection test intentionally emits a ZIP warning.

The four tested JARs are:

| Artifact | SHA-256 |
| --- | --- |
| RendererPostcards.jar | `af2a693ebf95f08bc78829c0e0b400e201e5373d9ea920550614913dfc3ff4c8` |
| RendererRedstoneCamera.jar | `1ac675f670381a5bbc0fb1938fc79b3ec40280eadf7ab09ed1120c0f4c3afc6a` |
| RendererPaintersEasel.jar | `627c4658fcc4ab9de2cc99cefce5994ac65979a6b67171d47dd1f910fd3455ba` |
| RendererAdminPov.jar | `16375705e7532e4976b5624c08f8634e6a63246a00f78878eab24d44e80267e0` |

The final plugin bundle manifest and adjacent ZIP checksum files record the generated delivery hashes. No historical artifact hash is reused. Source and plugin delivery name the same clean final documentation commit, also used by the immutable `v1.3.0` release tag. The ZIP hashes differ from the candidate because documentation and source provenance changed; the four plugin JAR and resource-pack hashes above remain unchanged.

## Exact server acceptance and limits

The private acceptance record binds the exact Renderer 1.1.0 source/platform archives and embedded native hashes to the SDK identity and the four plugin hashes above. It retains the sanitized phase logs, cache/restart evidence and clean-shutdown results separately from these public source archives.

| Environment | Accepted scope |
| --- | --- |
| Native macOS ARM64 | Licensed provider activation, examples initial probe, cached restart and clean shutdown |
| Docker Linux amd64 emulation on macOS ARM64 | The same examples/provider checks; not a native Linux-host result |
| Windows x86-64 | Commercial provider and shipped sample activation, cached restart and clean shutdown; examples probe not run |

The examples probe uses a synthetic Bukkit player with real Paper world/provider operations. Initial checks cover postcard delivery, a redstone camera photo and north-facing pose, easel reference capture, pigment painting/progress and creator-only painting. Restart checks cover the same camera inventory without an extra sustained-power shot, restored easel pigment/map renderer, one-time collection and native easel/support removal. All four plugins load, but **Admin POV is startup-only**: no target feed, receiver, monitor or multi-owner workflow is exercised by this probe. Any probe failure, plugin event exception or unclean shutdown invalidates a phase even if a later success marker appears.

Human client visuals, all six camera directions, the full POV workflow and sustained throughput/latency remain untested for these release bytes. Unit-test cadence is not measured renderer performance. Genuine MCModels personalized buyer activation is separate from licensed manual activation and cached restart.

Neither archive includes a commercial provider, native binary, test probe JAR, license key or generated target directory. The source archive includes the test probe's source for disposable-server acceptance only.
