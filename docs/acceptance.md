# Acceptance checklist

## Examples 1.3.0 — fluent SDK server acceptance (2026-10-24)

Examples 1.3.0 uses SDK `ke.ric:renderer-api:2.3.0-preview.3` and requires Renderer 1.1.0. See [the current verification record](release-1.3.0-evidence.md). The recipe migration uses SDK-owned snapshots and streams: up to two active target streams replace the old single global POV pipeline, and a cadence change restarts streams while preserving receiver identity and failure backoff. Historical test counts, throughput observations and artifact hashes below do not validate this release.

The exact four example JARs passed the bounded synthetic-player Paper probe on native macOS ARM64 and Docker Linux amd64 emulation on the Mac. It checked postcard/camera output, a north-facing camera across restart, easel reference capture and painting, restored pigment/map state, one-time collection and native easel removal. Camera inventory survived restart without another shot under sustained power. All four plugins loaded; **Admin POV startup was the only POV runtime check**. The licensed provider's cached restart and clean shutdown were checked alongside the examples. Windows passed the commercial provider/shipped-sample checks; the examples probe did not run there.

Actual client appearance, six-direction camera playtesting, complete POV receiver/monitor and multi-owner workflows, permission/target/provider invalidation, cadence behavior and sustained timing remain untested on these release bytes. The server probe is not a network client or human playtest. Genuine personalized buyer activation remains a separate gate.

## Examples 1.2.0 — stable SDK and current demo contract (2026-10-17)

Examples 1.2.0 uses stable SDK v1.0.2 and a matching Renderer 1.0.0 build with UUID entity exclusions and current framed-map capture. The resource pack is the exact reviewed 1.1.0 ZIP; previous release files and evidence are preserved. See [the new verification record](release-1.2.0-evidence.md), not historical JAR hashes, for these bytes.

The current contract is the six-way camera and public POV behavior below: 4 updates/second by default, best-effort only, with owner-controlled capture demand and no private pixels. Current framed maps can appear in photos and easel reference captures; feedback samples the previous completed frame. Exact-artifact licensed Paper tests, supported buyer activation and actual client checks remain separate gates. Historical or solo footage does not prove sustained throughput, cross-owner controls or personalized buyer activation.

## Current POV contract — shared public screens (2026-10-17)

All receiver maps and desk screens are non-contextual, public and photographable, including copied maps and unauthorized viewers. `/pov demo` only selects self; `/pov YourExactPlayerName` and `/pov <other-player>` use the same renderer. `/pov demo [updates-per-second]` also sets the shared best-effort per-target cadence for this server run (whole numbers 1-20; restart default 4, 250 ms start-to-start). Operator permission, target visibility, binding ownership and owner-only placement/removal remain enforced. Spectators cannot request or sustain captures. Verify shared completed pixels, stale/no-feed status, normal/demo equivalence, rate/range/rejection handling, healthy work and failure cooldown preservation, one-tick scheduling without redundant map sends, two-owner/shared-target demand, stale callbacks after stop/provider reload/shutdown and restart without persisted frame pixels. Actual client appearance and sustained timing remain separate operator checks; no client is launched by automation.

Earlier references below to contextual ownership or copied-map privacy describe older release behavior, superseded by this public-display contract. They are historical evidence, not current privacy guarantees. See the [current README](../README.md#admin-pov-monitor) and [recording guide](recording-guide.md).

## Current camera contract — six-way demo fix (2026-10-17)

The camera now keeps vanilla dispenser placement facing toward its placer, with only north, south, east, west, straight up and straight down supported. The saved pose, optional lens, photo and flash must agree in each direction. Legacy exact-angle tags are normalized to the live dispenser facing on load without replacing the camera identity or inventory. The steep/diagonal checks below describe older builds, not the current aiming contract.

Pending human checks on the exact new build: place from all six sides, verify lens/photo/flash alignment and unobstructed optics, collect the photo, restart with stored inventory, and check canceled placement with the server's protection plugin. Paper 26.2's flash uses white color data; older no-data flash APIs remain supported. Unit tests do not establish visible client appearance. No client is launched by the automated checks.

## Examples 1.1.1 — camera flash and recording guide (2026-10-01)

See [patch verification](release-1.1.1-evidence.md) and the [recording guide](recording-guide.md). The pack stays at 1.1.0. Verify a visible single flash on an accepted redstone request, no flash during cooldown or a full inventory, then successful photo pickup. Enable client particles. Check steep and diagonal camera angles. This client check is pending; unit tests cannot establish appearance. No live server was updated by this patch.

The solo `/pov YourExactPlayerName` walkthrough uses the existing self-target support. It does not replace multi-owner/privacy acceptance or actual sustained update measurements. The remaining licensed Paper/client gates below also apply to 1.1.1. RIC and MCModels settings were not changed.

## Examples 1.1.0 — status recorded 2026-09-29

The four-example update is separate from the preserved 1.0.0 release below. No commercial Renderer/API, license, marketplace listing or live playtest server was modified.

- Automated Java verification: camera optics/model transform and lifecycle, map-palette linen, old pigment persistence, original examples, POV scheduler bounds/backoff, contextual-map ownership, visibility/access, binding persistence, missing chunks, timeout and obsolete asynchronous callbacks. See the release evidence for the final test count and artifact hashes.
- Artwork verification: all native references, legal bounds, unit texel density, Blockbench pixel UV/embedded PNG parity, opaque two-unit canvas backing, original PNG preservation and byte-identical double regeneration. Browser previews use actual exports; they are not screenshots from Minecraft.
- Packaging: four independent JAR inventories, narrow resource allowances, no API/provider/native/probe payloads, exact reviewed pack hash and deterministic ZIP generation. Original 1.0.0 bytes remain preserved.
- **Pending: isolated licensed Paper acceptance and sustained POV measurements.** The prior temporary acceptance license was revoked after 1.0.0 acceptance. This task does not reuse the human playtest entitlement/cache or change RIC. No new legitimate isolated acceptance entitlement was available to this run, so no fresh native rendering or timing result is claimed.
- **Pending: actual 26.2 client appearance and interaction.** Check camera aim at steep/diagonal angles, all canvas faces, blank/painted map alignment, brush appearance, monitor placement/removal, two owners plus an unauthorized copied-map viewer, permission loss, visibility changes, teleport/logout, provider reload and restart. Browser/mock checks do not establish these observations.

For a later authorized isolated test, install the exact 1.1.0 JAR/pack hashes and Renderer1.0.0. Run one target for at least five minutes, then two distinct targets for five minutes. Record completed frames, p50/p95 capture-to-delivery latency, stale-image duration, server tick time, backoffs and peak simultaneous work (must remain one). Repeat with multiple owners sharing a target, unloaded boundary chunks, permission removal, target teleport/logout and provider disable/re-enable. Never describe synthetic-clock unit-test cadence as measured renderer throughput. Restart must display blank receivers until a new authorized capture succeeds, with monitor identity restored and no frame history on disk.

Everything below is historical evidence for earlier bytes, **not** acceptance of 1.1.0.

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
