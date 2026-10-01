# Four-example demo implementation plan

Approved user brief, 2026-09-29. Renderer 1.0.0, SDK v1.0.1, licensing, marketplace and all existing servers/worlds remain unchanged. Examples become 1.1.0; preserve prior release bytes and Kernel Person attribution.

### Task 1: Native artwork and existing examples

Use modeling-minecraft-assets with the existing original exporter/viewer, Java26.2/resource88. Preserve original inputs, copy the source into an isolated artwork directory, and archive approved exports. Add the camera model and desk-monitor frame; add an opaque roughly two-unit cream linen canvas slab behind the unchanged live-map plane. Retain square texels and existing brush. Export native definitions, persistent PNGs, editable Blockbench snapshots and machine-readable placement contracts. Verify references, geometry, UVs, pixel preservation, double regeneration and previews. Camera cosmetics retain dispenser storage/redstone and exact aiming; lens/capture share geometry and origin outside the model. Easel texture must survive map quantization, preserve pigment accuracy/progress and existing saves. Add lifecycle tests for restore/unload/remove.

### Task 2: Admin POV Monitor

Independent public-API-only Java plugin. Commands /pov <player>, /pov stop, /pov monitor. Permission rendererexamples.pov defaults OP. Owner-only contextual maps and desk screens, authorization checked before every delivery, target visibility respected. No frame persistence or recording. Store only owned map/monitor identity and target bindings. Restart blank, then authorize and recapture.

128x128 CLASSIC, radius16, chunksPerTick2, one worker, unchanged memory ceilings. Target one update/sec; two target feeds max, one global capture/render pipeline, shared results per target. Demand only while holding a receiver or owner within16 blocks of a loaded monitor. No queues or chunk loading; backoff and stale indication. Invalidate on target change/teleport/world change/offline, access loss, provider reload and shutdown. Test scheduling, stale completions, capacity rejection, copying/dropping maps, distinct viewers and persistence boundaries.

### Task 3: Verification, packaging and handoff

Run complete examples Maven and packaging tests plus exported artwork checks, preview actual native geometry from front/back/side/inventory. Reproducible examples1.1.0 package: four JARs, pack, instructions, checksums, provenance, editable-art handoff separately. Update walkthrough/acceptance evidence. Isolated Paper acceptance only if an existing legitimate test entitlement is available; otherwise explicitly pending without changing RIC or reusing human playtest state. Actual client appearance is separate from browser/synthetic validation. Review complete branch, resolve important findings with regressions; no automatic push, marketplace change or live-server update.
