# Camera light and public self-view demo implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Correct the black placed camera and allow an explicitly public self-view screen to create a photographic feedback tunnel.

**Architecture:** Keep display anchors inside the owning camera chunk, but sample exposed ambient light. Add an opt-in noncontextual self-view receiver using the previous completed frame; normal admin receivers remain contextual and private. No renderer or licensing changes.

**Tech Stack:** Existing Java examples, Bukkit/Paper 26.2, Renderer API local v1.0.2-SNAPSHOT.

**Spec:** User's black-camera report and clarified request for photographing the POV screen itself; inline design: `/pov demo` is a public self-view only, and normal admin feeds stay private.

## Global constraints

- Preserve world, inventories, camera aiming, models, artwork, saved paintings and human license.
- Do not launch a game client. Server-side checks do not establish client visual acceptance.
- Do not change renderer API/engine, RIC, marketplace, public tags or sale archives.
- Public demo mode is explicit, self-only and transient. Switching/stopping/restarting cannot publish a private target.

## Review focus

- Chunk-edge light samples must not load adjacent chunks (task 1).
- Lighting must update after lamp changes without forcing fullbright (task 1).
- Loss of authorization must remove public cached canvases, not merely hide future rendering (task 2).
- A mode switch must reject obsolete in-flight frames (task 2).
- Restart and provider reload must leave public mode disabled (task 2).

### Task 1: Camera lighting

Files: `redstone-camera/.../CameraModel.java`, `RedstoneCameraPlugin.java`, `CameraModelTest.java`.

- [x] Reproduce null brightness with actual Paper: dispenser light `0,0`, exposed sky `15`.
- [x] Add failing test for independent neighbor block/sky brightness on both displays.
- [x] Sample six already-loaded neighbors and refresh once per second; cancel task and remove cosmetics on close.
- [x] Verify lamp changes, loaded-chunk checks, unchanged anchors, restoration/removal and full module tests.

### Task 2: Public self-view demonstration

Files: `admin-pov/.../AdminPovPlugin.java`, new public demo renderer, existing tests/resources and walkthroughs.

- [x] Add regressions proving explicit self-only opt-in and normal contextual privacy.
- [x] Implement `/pov demo`, visibly label it public/photographable, retain bounded existing feed loop.
- [x] Use only last completed pixels; no recursive capture calls or additional pipeline.
- [x] Reset mode and reattach private receivers on stop, target switch, permission loss, logout, provider reload and shutdown. Never persist public mode.
- [x] Verify stale callbacks and cached-layer invalidation, and document one-frame-at-a-time feedback.

### Task 3: Integrated verification and local handoff

- [x] Run full examples Maven tests, relevant packaging checks, and independent diff review.
- [x] Test exact camera JAR lighting and public-map capture on isolated Paper; preserve red/green evidence.
- [ ] Back up changed recording-server JARs before installing. Preserve provider, other examples, pack, license and world.
- [ ] Graceful restart with user notice, confirm all five plugins load and no new runtime exceptions.
- [ ] Provide `/pov demo` feedback recording steps; leave final appearance for user's client check.
