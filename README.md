# ShutterBug Renderer — Java examples

By Kernel Person.

Four independent plugins built **only** against the public Renderer API. Start with Postcards: its command handler is the shortest capture → render → Minecraft map example.

**Examples 1.3.0** use `ke.ric:renderer-api:2.3.0-preview.3` and require **Renderer 1.1.0**. Postcards and Camera use the fluent snapshot API; Easel captures a reference frame; POV uses SDK streams and map sinks. Automated acceptance used these exact four plugin JARs with Renderer 1.1.0 on macOS ARM64 and Linux x86-64. It covered postcard/camera output and easel capture, painting and restart persistence; Admin POV was checked for startup only. Client visuals and the full POV workflow remain untested for these release bytes. See [1.3.0 verification and acceptance](docs/release-1.3.0-evidence.md). Published 1.2.0 and earlier artifacts remain unchanged.

| Plugin | Try it | Demonstrates |
| --- | --- | --- |
| RendererPostcards | `/postcard` | Capture your view, submit a render, convert it to a persistent map |
| RendererRedstoneCamera | `/rendercamera` | A placed dispenser camera, rising-edge redstone, bounded asynchronous work |
| RendererPaintersEasel | `/easel` | Raw rendered pixels, game-like CMY painting, canvas hit coordinates and saved progress |
| RendererAdminPov | `/pov <player>` | Bounded periodic capture, shared target feeds and public photographable map screens |

## Install

1. Run the tested server combination: **Java 25, Paper 26.2, Minecraft 26.2**.
2. Install and activate **ShutterBug Renderer**, purchased separately. These examples do not include its commercial JAR, native libraries or a license key.
3. Copy any of the four example JARs from this repository's build output or the examples ZIP into `plugins/` and restart. Each is independent; no shared support plugin is required.
4. Stand in an already loaded area. The original examples capture a 32-block radius; Admin POV uses 16. None generates or preloads distant chunks.

Renderer 1.1.0 supports **macOS ARM64, Linux x86-64 and Windows x86-64**. The examples probe ran natively on macOS ARM64 and under Docker Linux amd64 emulation on the Mac; this is not a native Linux-host test. Windows acceptance covered the commercial provider and its shipped sample, not these examples. Java 21 is the SDK/source compilation baseline; the server requires Java 25.

The renderer must register an active `RendererService`. If activation fails, commands report that the renderer is unavailable; they never handle license keys themselves. Disabling/reloading the provider cancels outstanding work. A server restart is the supported upgrade method.

## Try the examples

### Postcards — the smallest starting point

Give yourself one empty inventory slot and run `/postcard`. You receive a 128×128 map of your view. It still displays after a server restart.

Permission `rendererexamples.postcards` defaults to everyone. Each player can have one active request, with a five-second cooldown. A disconnected player or full inventory does not receive a dropped or duplicated map.

Read [PostcardsPlugin.java](postcards/src/main/java/io/github/kernelperson/postcards/PostcardsPlugin.java), then [RenderSession.java](postcards/src/main/java/io/github/kernelperson/postcards/RenderSession.java).

### Redstone Camera

As an operator, run `/rendercamera` and place it like a vanilla dispenser: the lens faces toward you. Stand on the side you want it to photograph when placing, or above/below it for vertical shots, then move out of the picture. The camera has only six directions: north, south, east, west, straight up or straight down; no diagonal or free-angle aiming. Break and place a new camera to change its aim. Pulse adjacent redstone, then open the dispenser to find the map. Continuous power takes one picture, not a stream.

The camera suppresses ordinary dispensing, needs an empty inventory slot, ignores triggers while busy/cooling down and does not consume materials. An accepted request emits one brief white flash just outside the lens, with or without the resource pack. Enable client particles to see it. The flash signals capture started, not successful completion; it does not illuminate nearby blocks. A high pling means success; a low tone means busy, full or failed. Break it to remove it; obtain another tagged camera with the command. An ordinary dispenser is never treated as a camera.

Permission `rendererexamples.camera` defaults to operators. Locations and random instance identities persist in dispenser data. An unloaded, broken or replaced camera cannot receive an old result. The teaching example limits itself to 256 loaded cameras and eight concurrent renders.

Read [RedstoneCameraPlugin.java](redstone-camera/src/main/java/io/github/kernelperson/camera/RedstoneCameraPlugin.java).

With the optional 1.1.0 resource pack, set `native-models: true` in `plugins/RendererRedstoneCamera/config.yml` and restart. The wood/metal housing stays on the dispenser; its lens, photo direction and flash all follow the dispenser's six-way facing. Both models are cosmetic native ItemDisplays. The capture point clears the housing, lens and dispenser in all six directions. Existing exact-angle cameras are normalized to their dispenser facing when loaded; their inventory and saved identity are preserved. Ordinary dispensers are not retextured.

### Painter's Easel

As an operator, make two empty inventory slots and run `/easel`.

1. Place the fence item: it builds a temporary fence/backboard/framed-map easel facing you. Leave space above and in front.
2. Hold the supplied brush in your main hand and right-click the canvas. It captures **one fixed view beyond the back of the easel**, clear of the dummy model.
3. Put cyan, magenta or yellow dye in your offhand. Hold right-click and move your aim across the map to paint. Punching also paints.
4. The round brush is roughly 24 pixels wide. One pass fills the selected pigment, joining input samples into continuous strokes (a pause or colour change starts a new stroke). Cyan + yellow builds green; magenta does nothing to a purely green area. Three pigments together create dark tones. This is a forgiving game, not physical paint simulation.
5. Watch completion in the action bar. Sneak + right-click with the brush to collect your painting, even before it reaches 100%.
6. Right-click again to begin a new canvas, or look at the empty easel and use `/easel remove`.

Only the creator paints, collects or removes an easel. Other players may view it. Brushes/dyes are not consumed. Progress saves every five seconds, on chunk unload and graceful shutdown; an abrupt crash can lose the last five seconds of strokes. Collected maps remain viewable after restarts. Back up the world's map data **and** this plugin's data together.

Permission `rendererexamples.easel` defaults to operators. Limit: 128 loaded easels.

Optional native easel and brush models (no Nexo or other plugin dependency):

1. Use the included **renderer-examples-resourcepack-1.1.0.zip**. Host it at an HTTPS URL reachable by players, not localhost, and set your server's `resource-pack` URL. Compute `resource-pack-sha1` from the exact ZIP you serve (`shasum -a 1 renderer-examples-resourcepack-1.1.0.zip` on macOS, `sha1sum` on Linux). Players must accept the pack. This revised pack includes the camera, opaque linen canvas backing, monitor and original brush; the old 1.0.0 pack is preserved separately and lacks these additions.
2. Start the server once to create `plugins/RendererPaintersEasel/config.yml`. Set `model-item: village_trades:painters_easel` and `brush-model-item: village_trades:paintbrush` there, then restart. Players must accept the pack to see the models.
3. Use `/easel` to issue the easel and brush. Existing tagged brushes gain the brush model when their owner joins; ordinary tools stay unchanged. The blue tip is cosmetic—offhand dye still chooses the painting colour.

The pack targets Minecraft 26.2, not every version supported by the Java API. The adapter replaces the fence/backboard with two invisible barrier blocks and displays the wood separately from the live map. Existing easels keep their map IDs, ownership and pigment progress. Leaving `model-item` blank restores vanilla supports on loaded easels; `/easel remove` removes the model and owned supports. Transient displays are recreated from saved frames after restart and removed on chunk unload.

The canvas backing is two model units thick, with opaque cream linen on its rear and edges. It stays behind the existing map plane, including a blank map. The map's initial ground now has palette-visible warm weave; applied pigment amounts and the `.cmy` save format are unchanged. Fully painted pixels still converge to the original target colour.

If a saved canvas is damaged, or more than 128 easels are loaded from existing chunks, affected easels stay protected but inactive. For a damaged canvas, stop the server, restore `canvases/<frame UUID>.cmy` from a backup and restart; the console identifies that UUID. For excess easels, unload other easel chunks, then reload the affected chunk. The plugin never silently discards a damaged painting or permits another player to take it.

Read [PigmentCanvas.java](painters-easel/src/main/java/io/github/kernelperson/easel/PigmentCanvas.java), [CanvasHit.java](painters-easel/src/main/java/io/github/kernelperson/easel/CanvasHit.java) and [PaintersEaselPlugin.java](painters-easel/src/main/java/io/github/kernelperson/easel/PaintersEaselPlugin.java).

### Admin POV Monitor

Permission `rendererexamples.pov` defaults to **OP**. Only authorized operators can select targets and drive captures; every receiver and desk screen displays **public, photographable pixels**, including copied maps and nearby viewers.

1. `/pov <online-player>` selects a player you can see and supplies a reusable handheld map. Hold it in either hand to watch. Running the command again changes your selected target without creating a new map ID.
2. `/pov monitor` supplies a placeable desk monitor linked to your selection. Leave two blocks of height and space in front; supports and screen must fit within one chunk. Placement fires normal block/hanging protection events. Sneak + right-click your screen to remove it, with an empty inventory slot.
3. Enable `native-models: true` in `plugins/RendererAdminPov/config.yml` after installing the pack, then restart. Without the pack setting, the same live map uses vanilla supports. The optional model does not contain a baked image.
4. `/pov stop` blanks your receivers and removes demand for your feed. Monitors stay placed for later use.

Recording alone? `/pov demo` is shorthand for `/pov YourExactPlayerName`: both select **you** using exactly the same public receiver and monitor behavior as any other target. There is no private/public mode switch. Hold the map or watch your desk monitor to drive updates. Each render samples only the previous completed frame, so feedback advances one completed snapshot at a time, without recursive rendering or extra pipelines. Label the clip as a **solo self-view demo**, not another player's view. See the [recording walkthrough](docs/recording-guide.md).

The default target is **4 updates per second per active target** (250 ms start-to-start). `/pov demo [updates-per-second]` accepts a whole number from **1 to 20**: for example, `/pov demo 4` selects self and sets the shared cadence to four updates per second. The setting applies to **all POV feeds for this server run**, not just your receiver; restart restores 4. Plain `/pov demo` and `/pov <player>` keep the current rate. Invalid or rejected commands leave the rate and selection unchanged. A rate change restarts the affected SDK streams while preserving receiver identities and failure cooldowns.

The target's server-side eye position and exact yaw/pitch are captured. This is **rendered world-view sampling, not screen sharing**: no HUD, chat, inventory UI, client shaders or audio. It does not record frames or store a history. Administrators can still take their own screenshots; this plugin cannot erase images a client has already received.

POV excludes exactly the target player's UUID from capture, so looking down does not render the target's own body around the camera. Other players and entities remain visible. Renderer 1.1.0 is required for the fluent snapshot and stream API; older providers are not supported by examples 1.3.0.

The matching provider captures loaded framed maps from their current completed pixels, including these public POV screens. Camera photos and easel reference captures can therefore include a displayed map. This is a bounded snapshot of an existing frame, not recursive rendering or screen sharing; feedback uses the previous completed image.

All POV maps use one non-contextual renderer: viewers receive the same completed pixels regardless of map ownership or viewer permission. This is intentional public display, not a privacy boundary. Target selection, capture authorization and target visibility still require the owning operator's permission; monitor placement and removal remain owner-controlled. Spectators cannot start or keep a feed running: only an authorized owner's held receiver or monitor-watching demand counts. Already delivered images and photographs cannot be unpublished; old items with a legacy “Private” name are public too.

Stopping clears the owner's selected feed; permission/access loss removes that owner's capture demand at the next one-tick check (normally 50 ms; longer if ticks lag). Periodic status/map-cache sends remain every five ticks; completed frames and explicit stop/invalidation are sent immediately. Target disappearance, teleport/world changes, provider removal and shutdown invalidate applicable frames or work, and obsolete completions cannot restore them. Other authorized owners may still demand a shared target. Restart restores receiver/monitor identity but no pixels: screens show **NO FEED** until a fresh authorized capture completes. None of these events changes the renderer into a private mode.

Capture runs only while an authorized owner holds a receiver, or looks toward their loaded monitor within 16 blocks with line of sight. Same-target subscribers share a completed frame. Maximum: **two active targets**, each with one SDK-owned stream; the SDK shares provider capture/render capacity and never queues catch-up frames. Render settings are 128×128 CLASSIC, radius 16, two capture chunks per tick, one native worker, unchanged provider memory limits and a 30-second deadline. Cadence is **best-effort, not guaranteed frame rate or latency**: tick resolution/lag, shared work and slow renders can reduce each target's achieved rate. Slow work adds no full interval after completion, skips missed frames and never creates catch-up jobs. Failures back off from 2 to at most 30 seconds regardless of the requested rate. Images older than three seconds are labelled **STALE**; a receiver without a completed frame says **NO FEED** for everyone. Unloaded nearby chunks are rejected, never generated or loaded synchronously.

Only ownership, map IDs and selected targets are stored in `bindings.yml`; monitor ownership lives in ItemFrame data. Completed frames and renderer canvases are transient; they are not saved as world-map base pixels or a recording history. Back up bindings with world map/entity data. Maximum 128 receiver owners and 128 active loaded monitors. Excess restored monitors stay protected with empty frames and do not request renders; unload other monitors, then reload their chunk to reactivate them. If several idle owners later demand more than two distinct targets, the first two targets in saved owner order are admitted; other receivers wait until demand frees a slot. See [AdminPovPlugin.java](admin-pov/src/main/java/io/github/kernelperson/pov/AdminPovPlugin.java), [FeedLoop.java](admin-pov/src/main/java/io/github/kernelperson/pov/FeedLoop.java) and [PovMap.java](admin-pov/src/main/java/io/github/kernelperson/pov/PovMap.java).

## Build and adapt

Use `ke.ric:renderer-api:2.3.0-preview.3` from `https://shutterbug-maven.pages.dev/` and Renderer 1.1.0 at runtime. A standalone SDK JAR is never a server plugin. The dependency is provided by the installed commercial provider; do not shade it into an example.

Install JDK21+ and Maven, then:

```sh
mvn clean verify
```

Output: each module's `target/Renderer*.jar`. No shading is needed: depend on the public API with Maven `provided` scope and declare `depend: [ShutterBugRenderer]` in `plugin.yml`.

```xml
<dependency>
  <groupId>ke.ric</groupId>
  <artifactId>renderer-api</artifactId>
  <version>2.3.0-preview.3</version>
  <scope>provided</scope>
</dependency>
```

Add the Maven repository when adapting an example outside this reactor:

```xml
<repositories>
  <repository>
    <id>shutterbug</id>
    <url>https://shutterbug-maven.pages.dev/</url>
  </repository>
</repositories>
```

The frozen Spigot compile dependency is not a claim that the Paper 26.2 server runs Minecraft 1.21.10.

The few small lifecycle/map helpers are deliberately copied into each plugin's own package: you can study, build or adapt one module without installing another. There is no shared runtime framework. Each plugin performs Bukkit mutations on the server thread, uses a 30-second overall deadline, cancels stale work and never queues unlimited captures.

## Commercial add-ons are welcome

You are welcome to make **free or paid plugins** that use ShutterBug Renderer. The example source is Apache-2.0: adapt it for your own projects while retaining the required license/attribution notices.

Clearly tell buyers: **“Requires ShutterBug Renderer, purchased separately.”** The server owner needs a valid Renderer license within its seat limits—not every player, and not another Renderer purchase for each add-on on that server.

Distribute your add-on, not the commercial Renderer provider, native binaries or license keys. The example-code license does not grant rights to those separate commercial artifacts. Your add-on's price and support are your own; do not imply official endorsement.

[Renderer wiki and Javadocs](https://kernel-person.github.io/shutterbug-renderer-api/) · [Public SDK](https://github.com/kernel-person/shutterbug-renderer-api)

## Acceptance status

Unit tests and builds are reproducible with `mvn clean verify`. Current release verification, the bounded server acceptance and remaining checks are recorded in [1.3.0 evidence](docs/release-1.3.0-evidence.md), with historical checks in [docs/acceptance.md](docs/acceptance.md); do not mistake automated geometry/event checks for an actual human painting playtest.

Release delivery contains two archives from the same reviewed source commit:

- `ShutterBug-Renderer-Examples-1.3.0.zip`: four plugin JARs, the byte-identical 1.1.0 resource pack, README, recording guide, license, current and historical evidence, and a manifest containing the SDK version, source commit and artifact hashes.
- `ShutterBug-Renderer-Examples-1.3.0-source.zip`: the complete tracked source tree, including all four Maven modules, tests, editable artwork and build instructions. Extract it to study or build the simplified examples. The bundle manifest links directly to the same commit.

After tests pass and reviewed source is committed, build both archives from that clean checkout:

```sh
mvn clean verify
python3 tools/package_examples.py --output target/ShutterBug-Renderer-Examples-1.3.0.zip
git archive --format=zip --prefix=ShutterBug-Renderer-Examples-1.3.0-source/ --output=target/ShutterBug-Renderer-Examples-1.3.0-source.zip HEAD
(cd target && shasum -a 256 ShutterBug-Renderer-Examples-1.3.0-source.zip > ShutterBug-Renderer-Examples-1.3.0-source.zip.sha256)
```

The plugin packager rejects bundled API/provider/native/probe payloads and verifies the reviewed resource-pack bytes. Neither archive includes the commercial provider, native libraries or license keys. The source ZIP contains only tracked Git files, excluding generated targets. Existing downloads remain unchanged. Editable artwork and regeneration instructions are in [artwork/README.md](artwork/README.md).
