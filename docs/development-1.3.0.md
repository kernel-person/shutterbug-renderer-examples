# Development examples 1.3.0-SNAPSHOT

Unpublished migration targeting the locally installed `ke.ric:renderer-api:2.3.0-SNAPSHOT` SDK and matching development provider. Historical release files and evidence remain unchanged. Build with Java 21 using `mvn verify` after installing that SDK.

Postcards and Redstone Camera call `snapshot(...).settings(...).radius(32).chunksPerTick(4).mapOptions(...BAYER).captureMap(...)`. Both retain NORMAL 128×128, two workers, 30-second timeout, bounded admission, one pending capture per identity and five-second cooldown. Renderer persists each new immutable photograph before callback; delivery still validates its destination and rejects full inventories or obsolete blocks. Camera directions, lens pose, rising-edge triggers, flash and sound behavior remain addon-owned.

Legacy `maps/<id>.map` files (RMP1 header plus 16,384 palette bytes) still restore their existing map IDs. They are not adopted, deleted or rewritten during restoration. New photos use provider storage. Easel canvases remain mutable addon-owned maps and canvas files; only reference capture uses the snapshot facade.

Admin POV uses one `.fresh().fps(4)` stream per demanded target, capped at two targets; `/pov demo [1..20]` changes the shared rate until restart. The streams retain CLASSIC 128×128, radius 16, two chunks per tick, one worker, 30-second timeout and NONE dithering. `toMapSink` supplies palettes to the existing permanent receivers and monitors. Demand, authorization, target visibility, nearby monitor line of sight, receiver IDs/bindings, furniture and protection remain addon-owned. Failures retry after 2, 4, 8, 16 and at most 30 seconds; displays mark evidence stale after three seconds. Changing demo rate restarts the affected streams without changing receiver identity or failure backoff. Invalidated streams cannot publish obsolete pixels.

Packaging writes a new `ShutterBug-Renderer-Examples-1.3.0-SNAPSHOT.zip` with development coordinates in its manifest. The checked-in 1.1.0 resource pack remains byte-identical. This document is not a client acceptance or publication claim.
