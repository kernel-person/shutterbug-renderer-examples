# Camera lighting and public self-view feedback — development checks

Historical evidence for the earlier private/public split. That POV behavior and its gray-map redaction policy are superseded: every current POV receiver is a shared public screen, and `/pov demo` is self-target shorthand only. The checks and hashes below describe earlier bytes, not acceptance of the current cleanup. See the [current contract](../README.md#admin-pov-monitor).

2026-10-17. Local development changes; not a new commercial renderer release.

## Root causes

- The camera's two ItemDisplay entities intentionally stay at the center of the dispenser so ownership and restoration remain in its chunk. Minecraft 26.2 samples lighting at that entity position, before applying the visible model transform. The opaque block therefore supplied block/sky light `0,0`, even with sky light `15` immediately outside it.
- Normal POV receivers use contextual, owner-only map renderers. Renderer deliberately redacts those maps before reading any pixel cache. The reported gray self-screen was this privacy behavior, not a missing map texture. Ordinary public maps passed exact-artifact capture checks.

## Changes

- Camera displays now sample the maximum block and raw sky light of their six already-loaded adjacent cells. A single once-per-second task refreshes both parts; close cancels the task. No fullbright floor, geometry change, chunk loading, inventory mutation or resource-pack replacement.
- `/pov demo` explicitly exposes only the owner's self-view, including copied receivers, for public demonstrations and photographs. Its map uses the last completed frame, so feedback advances one frame at a time without recursive render calls or another pipeline.
- Normal admin feeds remain private. Demo mode is transient and is removed on stop, private selection, logout, world/teleport invalidation, provider reload and shutdown. Permission loss is checked at the existing five-tick cadence. Already published photographs cannot be recalled.

## Runtime evidence

- Exact installed renderer JAR loaded through Paper's real PluginClassLoader: SPI discovery and ordinary public map pixels passed. A contextual overlay remained private, and capture did not invoke arbitrary map callbacks.
- Actual ItemFrame capture through the exact renderer JAR reached the expected colored pixel in entity ray tracing, rather than the neutral placeholder.
- Old camera JAR failed the isolated lighting regression: center `0,0`, exposed sky `15`, both display brightness overrides absent.
- Rebuilt camera JAR passed the same probe with its test chunk held loaded, including lamp on/off updates, unchanged entity anchors and cosmetic cleanup. The first lamp subtest without a chunk ticket correctly skipped an unloaded test chunk; it was repeated with an explicit fixture ticket.
- The exact new POV JAR passed an isolated Paper probe using its real public renderer: the reader captured completed palette value `18`, then `50` after a completed-frame change; the earlier frozen snapshot remained `18`. Removing the public renderer and installing a contextual one restored `PRIVATE` with its public canvas already warmed.
- The four-module clean Maven suite passed 151 tests. The full follow-up run also passed all 151 tests after the reviewed stale-callback test was strengthened. The packager suite passed all 11 tests; both final JARs passed its artifact/secret checks. Independent camera and POV reviews found no production issues.
- Tested JAR SHA-256: camera `2c8de58264f605c9c5f96965a8a54d929018c239dd51dab13e7a129220568a30`; POV `9bcca5ad408d84f058553db91c70a3751cdd99308205959d182c172c9b577299`.

These are automated/headless checks. No game client was launched. Final appearance and the feedback-tunnel recording remain client checks for the operator. Renderer/native code, licensing, existing artwork, marketplace configuration, public SDK tags and commercial ZIPs are unchanged.
