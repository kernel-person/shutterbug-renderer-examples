# Renderer example plugins: approved implementation plan

Goal: three small independently installable Java plugins using only the published v1.0.1 Renderer API, demonstrating commercial third-party add-ons without distributing the commercial provider.

Architecture: three Maven modules; each owns its plugin lifecycle, bounded rendering and map persistence. Small duplicated boundary helpers keep the examples independently copyable. No shared runtime plugin, private SDK classes, native code, RIC changes or existing ShutterBug edits.

Constraints: Java21 compile baseline; tested runtime Java25/Paper26.2/Minecraft26.2. macOS ARM64 and Linux x86-64 only. SDK provided-scope. Source Apache-2.0. No billing changes, marketplace issuance, submission or publication of the MCModels draft.

## Task 1: Renderer prerequisite verification
- Run clean unchanged full Python discovery in the existing release worktree; inspect every failure.
- Retain exact release artifacts/source provenance. Do not alter tracked files during the run.
- Confirm ephemeral license reset/revocation when browser access permits.

## Task 2: Postcards and minimal API boundaries
- RED: tests for busy/cooldown, stale/cancelled delivery, render completion on the server thread, persistence validation.
- Implement /postcard: capture current view, render128x128 NORMAL/radius32/chunksPerTick4, convert and persist map, deliver only if player/session/inventory remain valid.
- Render client lifecycle closes on provider removal and shutdown; overall deadline covers capture as well as render.
- GREEN: all module tests/build, no API or native classes in JAR.

## Task 3: Redstone camera
- RED: rising edges, continuous power, busy/cooldown, destroyed/replaced/unloaded destinations.
- Implement tagged dispenser placement, horizontal facing, five-second cooldown, one in-flight request per camera, bounded global work, inventory output with no vanilla dispensing.
- Persist tagged camera locations; invalidate callbacks on break/unload/provider removal.
- GREEN: full reactor tests and artifact boundaries.

## Task 4: Painter's easel
- RED: CMY mixing, wrong pigments, capped strokes, finite progress, image/state roundtrip, corrupt input rejection, front-only bounded ray/canvas intersection.
- Implement vanilla fence/backboard/framed map dummy. Creator starts a frozen capture beyond the easel, paints with main-hand brush/offhand CMY dye, collects map; only creator edits/collects.
- Brush radius8pixels with soft edge and bounded pigment increments; texture fades as pigment is applied. No render per stroke. Persist target/progress/ownership/map IDs and restore on enable.
- Prevent brush attacks/rotation/removal of owned frames and preserve unrelated blocks. No consumption/economy/recipes or custom models.
- GREEN: full reactor and Paper interaction/persistence acceptance.

## Task 5: Documentation, public repository and draft packaging
- Beginner walkthrough first: Postcards, then Redstone Camera, then Painter's Easel.
- Explicitly welcome free and paid third-party add-ons; server owners acquire the Renderer separately, no redistribution of commercial provider/keys.
- Build three independent JARs, scan archives, prepare example ZIP and link source/tutorials.
- Fresh independent review of examples; fix important findings with red/green regressions.
- Test exact provider/examples on isolated macOS and Linux Paper, record emulation; no claims of visual playtesting without actual evidence.
- Publish public examples source under kernel-person. Update wiki and MCModels17590 draft only after gates pass, verify uploaded download checksum; leave website actions pending if browser unavailable.

Review focus: late async callbacks after disable; creative-mode frame destruction; redstone-trigger bursts; malicious/corrupt saved dimensions; map recovery after restart and full inventories.
