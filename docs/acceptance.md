# Acceptance checklist

Before distributing example JARs, use an isolated Paper26.2/Java25 server with the exact commercial renderer release. Do not test on a customer's server.

Status on 2026-09-28: a human client playtest accepted the updated experience on the isolated development-overlay server. That is distinct from the historical automated checks below and does not validate the final personalized buyer download, clean commercial JARs, or final examples ZIP. Those exact artifacts still require the release acceptance pass before distribution.

- Postcard produces a map; full inventory/disconnect cannot drop or duplicate delivery.
- Ordinary dispensers are unchanged. Tagged camera responds once per rising edge, ignores sustained power and busy bursts, and refuses stale replaced/unloaded destinations.
- Easel placement respects cancelled placement, is creator-only, captures one fixed reference, and preserves progress/maps across restart.
- Brush hits reach correct map pixels from all four horizontal orientations; wrong dye does nothing; overlapping CMY layers converge without exceeding target pigments.
- Creative/survival punches cannot destroy or rotate the canvas. Collection requires inventory space and cannot collect twice.
- Provider removal, plugin disable and timeouts leave no usable late callback.
- Exact example JARs contain no API implementation classes, native binaries, license keys or private renderer source.

## Automated results — 2026-09-27

- Maven: 52 tests passed, including queued completion after shutdown, provider removal, late placement cancellation, malformed canvas ownership protection and overflow restoration.
- Packaging: four tests passed; rejects bundled API/native/probe classes, duplicate entries and sensitive build material. ZIP output is deterministic.
- Native macOS ARM64: initial Paper run and cached restart passed.
- Linux x86-64: initial Paper run and cached restart passed under Docker linux/amd64 emulation on a macOS ARM64 host. This is not a physical Linux host test.
- Runtime: Java25, Paper26.2 build129 / Minecraft26.2, Renderer1.0.0, public SDKv1.0.1.
- Actual licensed provider and world capture produced a postcard and camera map; directly placed adjacent redstone powered the camera without an injected redstone event. Sustained power across restart produced no duplicate camera photo.
- Easel capture, aimed CMY strokes, action-bar progress, creator-only painting, persistent canvas/map restoration and one-time collection passed. Both shutdowns exited cleanly. Event-handler exceptions fail acceptance even if a later success marker appears.

The test-only `acceptance/probe` uses a synthetic Player at the Bukkit boundary, real Paper world/events and the real licensed renderer. It is not an actual network client or human playtest and is not shipped in the examples ZIP. The private operator runner supplies the temporary license separately; no credential is in this repository. An early probe missed one Player.Spigot overload; that run was rejected and rerun with the corrected fixture. A subsequent direct-power test exposed missing physics handling; the camera was fixed and both platforms rerun.

Exact example JAR SHA256 values tested on both platforms:

| Artifact | SHA256 |
| --- | --- |
| RendererPostcards.jar | `cf1c5418b61f95e9783d93d2056abe14fff70384aab0603b260577d2f6cb45b6` |
| RendererRedstoneCamera.jar | `957adf22e1bec9be5ba886394cd32a54f543ddfa5f36689eee28c43caa817ae2` |
| RendererPaintersEasel.jar | `342080bb4438718ced0eb6356d068f11b9ee8ccaa35f2e1d77365d17fac32324` |

## Human playtest and remaining release checks

The 2026-09-28 human client playtest accepted the corrected camera aim and the updated Painter's Easel painting/model/brush experience on an isolated localhost server. Those observations used development-overlay JARs and a supplied resource pack; they are not a buyer-download or exact final release acceptance claim. The historical 2026-09-27 table above covers older JAR bytes only and must not be reused as hashes for the updated examples.

Still verify the exact final macOS ARM64 and Linux x86-64 commercial artifacts with personalized activation, a signed cached restart, a real render, the three examples, and the unmodified easel/brush pack. Record fresh JAR/ZIP hashes and separate automated, synthetic Paper, and human observations. Linux Docker `linux/amd64` emulation is not a physical Linux host test. Protection-plugin interactions, every visual orientation, and survival/creative client combinations beyond the accepted playtest remain unverified unless separately recorded. Windows is coming soon and is not supported by this release.
