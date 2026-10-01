# Examples 1.1.0 verification record

2026-09-29. Local implementation branch `codex/four-example-demo`, based on `b6cbcc777f641ef38fc9051f86023f2a3397936a`. The final package manifest records its exact committed source. No push, deployment, marketplace change or live-server update is part of this handoff.

## Automated evidence

- Clean JDK21 Maven build: **102 tests, zero failures/errors** (Postcards14, Camera25, Easel42, POV21). Includes actual RGBA-to-map conversion at the asynchronous delivery boundary, not just mocked output callbacks.
- Packaging: **11 tests passed**, including four-plugin membership, precise resource allowances, prohibited provider/API/native/probe entries, duplicate ZIP members, secret markers and loopback default rejection.
- Artwork: **8 tests passed**: native reference/geometry/UV density, Blockbench geometry and pixel-UV/embedded-PNG parity, runtime camera contract, opaque slab, preserved brush/wood inputs, clean double regeneration, current-export parity and exact deterministic pack ZIP.
- Vite production viewer build passed. Its Three.js bundle produces a size advisory (~514kB uncompressed); this is a local preview tool, not code shipped in the server JARs.
- Full branch independently reviewed. Three important findings were reproduced then fixed: monitor self-cancelled protection event; camera visuals before final placement approval; restored monitors exceeding active cap. The corresponding seven focused regression tests passed, followed by the clean complete suite. Reviewer confirmed no outstanding important findings.
- Exported model previews inspected from front/back/side/oblique, with native GUI thumbnails. Camera seam corrected without repainting PNGs. These are browser observations, not Minecraft client observations.

## Exact generated artifacts

| File | SHA256 |
| --- | --- |
| RendererPostcards.jar | `38504450e7a9b8d5e6fab420b844fb979cb3187677b09b123b5ebb8341de3039` |
| RendererRedstoneCamera.jar | `ad2bd3be99aee03226cc7b587b141e0f1b8dcf670884fbeaf6df33574a22900c` |
| RendererPaintersEasel.jar | `432d74a25eaf47f3a63d3fce6e47c78195db0cbce06fb5e70cae7303f80c0b86` |
| RendererAdminPov.jar | `05de0746eca2ede68ac3a0d01f18e8ed728cc5a7ed7a462b8c9a94c84f98c01a` |
| renderer-examples-resourcepack-1.1.0.zip | `9fc02186d4cb0ac924e728259371635f14911350482595fa3e5c2edd949ec5cf` |

The old resource-pack SHA256 remains `2d9f58a89a8869bfb8d1d64026731023c568ae403d4e31829a5ed247da1a963e`. Existing 1.0.0 commercial/example downloads are untouched. RIC and the API/provider repositories were read-only dependencies during this work.

## Explicitly pending

No isolated active acceptance entitlement was available: the prior temporary license was revoked, and the human playtest license/cache must remain untouched. Thus **licensed Paper acceptance, sustained POV latency/throughput/memory measurements and actual client appearance are pending**. The detailed acceptance matrix is in [acceptance.md](acceptance.md). No one-second real-world latency guarantee is claimed.

## Implementation rulings and review scope

- Reused the existing clean isolated examples clone on a new branch; the old branch is preserved. This avoids another checkout; the tradeoff is that this checkout now presents the new branch.
- Revised artwork is self-contained in `artwork/`, leaving the supplied artwork project and approved ZIP intact. Future edits to the revised examples belong here; edits to the original project will not automatically propagate.
- Overlapped independent authoring with running test/preview checks, then verified the complete combined tree. This saved idle time; final whole-tree tests and review address cross-task interactions.
- Native/provider throughput and client rendering were not judged without a legitimate test environment. They remain explicit acceptance gates, not inferred successes. Other Minecraft versions and unchanged unrelated behaviors are outside this 26.2 update's certification.
- Minor deferred polish: an explicit capacity-waiting banner. Admission is bounded and documented; an excess target currently shows NO FEED until a slot frees, rather than a distinct waiting message.

Local source/packaging checks do not prove live permission packet timing, protection-plugin interoperability, model placement on a real client, or native-provider cancellation latency. Verify those with the isolated acceptance walkthrough before describing this update as playtested.
