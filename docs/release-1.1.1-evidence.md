# Examples 1.1.1 verification record

2026-10-01. Patch to the four-example 1.1.0 release: one camera flash on an accepted render request, plus a recording guide and draft media copy. Renderer 1.0.0, SDK v1.0.1, licensing and the byte-exact 1.1.0 resource pack are unchanged. Existing 1.1.0 archives are preserved.

## Verification status

- The new flash regression failed before implementation because no FLASH particle was sent, then passed after implementation. It checks a single flash outside the lens and no flash on rejected requests or a full inventory.
- Clean JDK21 Maven verification: **105 tests, zero failures/errors** (Postcards 14, Camera 28, Easel 42, POV 21). Mockito emitted its existing dynamic-agent warning; the build passed. The first full-inventory fixture exposed Bukkit's absent unit-test sound registry; the fixture now suppresses only that unrelated legacy feedback path, then all three flash regressions passed.
- Packaging **11 tests passed**, including the added recording guide/current evidence inventory; artwork **8 tests passed**, including byte-identical regeneration. The packaging regression first failed because the recording guide/current evidence were absent from the bundle, then passed after updating the inventory.
- The flash is a one-shot client particle, with no light-block changes, persistent entities, scheduled cleanup or new pack artwork. It signals request acceptance, not render completion.
- In-game flash appearance and exact-artifact licensed Paper acceptance are still pending. No live server, world or entitlement was modified. Previous 1.1.0 acceptance limitations still apply; solo POV footage does not test cross-player privacy or prove sustained latency.
- MCModels media copy is prepared in `mcmodels-video-placeholders.md`, but no listing edit was performed: browser automation authentication failed and native browser access was not approved.

The package manifest records source commit and exact JAR/resource hashes. Do not reuse the historical 1.1.0 JAR hashes for this patch.

| Artifact | SHA256 |
| --- | --- |
| RendererPostcards.jar | `092a9a5bdf48714bf2999b3a7d6ee5a32bb339d8df19acf2b6bfc22f58598540` |
| RendererRedstoneCamera.jar | `fdbe8873f7b33fa46f85f8fa16eaaad46c52991e62d9eb261e68825e788a603c` |
| RendererPaintersEasel.jar | `b8c8d59053db544e72340a63dbc144f47a0cc8136f2e74d6aa987c0fe4cee081` |
| RendererAdminPov.jar | `b0e018c01dc14c13b0a8eeb9d5c335a4f2baab68b75b934f681263d8a1955b3d` |
| renderer-examples-resourcepack-1.1.0.zip | `9fc02186d4cb0ac924e728259371635f14911350482595fa3e5c2edd949ec5cf` |
