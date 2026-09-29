# ShutterBug Renderer — Java examples

By Kernel Person.

Four independent plugins, examples version **1.1.0**, built **only** against public Renderer API **v1.0.1**. They use the unchanged commercial Renderer **1.0.0**. Start with Postcards: its command handler is the shortest capture → render → Minecraft map example.

| Plugin | Try it | Demonstrates |
| --- | --- | --- |
| RendererPostcards | `/postcard` | Capture your view, submit a render, convert it to a persistent map |
| RendererRedstoneCamera | `/rendercamera` | A placed dispenser camera, rising-edge redstone, bounded asynchronous work |
| RendererPaintersEasel | `/easel` | Raw rendered pixels, game-like CMY painting, canvas hit coordinates and saved progress |
| RendererAdminPov | `/pov <player>` | Bounded periodic capture, shared target feeds and private contextual map screens |

## Install

1. Run the tested server combination: **Java 25, Paper 26.2, Minecraft 26.2**.
2. Install and activate **ShutterBug Renderer**, purchased separately. These examples do not include its commercial JAR, native libraries or a license key.
3. Copy any of the four example JARs from this repository's build output or the examples ZIP into `plugins/` and restart. Each is independent; no shared support plugin is required.
4. Stand in an already loaded area. The original examples capture a 32-block radius; Admin POV uses 16. None generates or preloads distant chunks.

Renderer platforms: **macOS ARM64 and Linux x86-64**. Windows is coming soon and is not supported by this release. Java 21 is the SDK/source compilation baseline, not the server runtime requirement.

The renderer must register an active `RendererService`. If activation fails, commands report that the renderer is unavailable; they never handle license keys themselves. Disabling/reloading the provider cancels outstanding work. A server restart is the supported upgrade method.

## Try the examples

### Postcards — the smallest starting point

Give yourself one empty inventory slot and run `/postcard`. You receive a 128×128 map of your view. It still displays after a server restart.

Permission `rendererexamples.postcards` defaults to everyone. Each player can have one active request, with a five-second cooldown. A disconnected player or full inventory does not receive a dropped or duplicated map.

Read [PostcardsPlugin.java](postcards/src/main/java/io/github/kernelperson/postcards/PostcardsPlugin.java), then [RenderSession.java](postcards/src/main/java/io/github/kernelperson/postcards/RenderSession.java).

### Redstone Camera

As an operator, run `/rendercamera` and place the dispenser while looking in the direction you want to photograph. Placement stores your exact yaw and up/down pitch, independently of the dispenser's blocky appearance. Break and place a new camera to change its aim. Pulse adjacent redstone, then open the dispenser to find the map. Continuous power takes one picture, not a stream.

The camera suppresses ordinary dispensing, needs an empty inventory slot, ignores triggers while busy/cooling down and does not consume materials. A high pling means success; a low tone means busy, full or failed. Break it to remove it; obtain another tagged camera with the command. An ordinary dispenser is never treated as a camera.

Permission `rendererexamples.camera` defaults to operators. Locations and random instance identities persist in dispenser data. An unloaded, broken or replaced camera cannot receive an old result. The teaching example limits itself to 256 loaded cameras and eight concurrent renders.

Read [RedstoneCameraPlugin.java](redstone-camera/src/main/java/io/github/kernelperson/camera/RedstoneCameraPlugin.java).

With the optional 1.1.0 resource pack, set `native-models: true` in `plugins/RendererRedstoneCamera/config.yml` and restart. The wood/metal housing stays on the dispenser; its lens follows the exact stored yaw/pitch. Both are cosmetic native ItemDisplays. The capture point clears the housing, lens and dispenser at every angle. Inventory, redstone and saved camera identity are unchanged. Ordinary dispensers are not retextured.

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

Permission `rendererexamples.pov` defaults to **OP**. This is an administration example, not public surveillance access.

1. `/pov <online-player>` selects a player you can see and supplies a reusable handheld map. Hold it in either hand to watch. Running the command again changes your selected target without creating a new map ID.
2. `/pov monitor` supplies a placeable desk monitor linked to your selection. Leave two blocks of height and space in front; supports and screen must fit within one chunk. Placement fires normal block/hanging protection events. Sneak + right-click your screen to remove it, with an empty inventory slot.
3. Enable `native-models: true` in `plugins/RendererAdminPov/config.yml` after installing the pack, then restart. Without the pack setting, the same private live map uses vanilla supports. The optional model does not contain a baked image.
4. `/pov stop` blanks your receivers and removes demand for your feed. Monitors stay placed for later use.

The target's server-side eye position and exact yaw/pitch are captured. This is **rendered world-view sampling, not screen sharing**: no HUD, chat, inventory UI, client shaders or audio. It does not record frames or store a history. Administrators can still take their own screenshots; this plugin cannot erase images a client has already received.

Only the owning authorized admin gets image pixels. Other players see a blank contextual map, even if they copy/drop/pick up the receiver or approach someone else's monitor. Access and target visibility are checked again at delivery. Permission loss, stopping, logout, target disappearance, teleport/world changes and provider reload invalidate applicable work or images. After a server restart the screen is blank until authorization and a fresh capture succeed.

Capture runs only while an authorized owner holds a receiver, or looks toward their loaded monitor within 16 blocks with line of sight. Same-target subscribers share a completed frame. Maximum: **two active targets and one capture/render pipeline globally**; there is no frame queue. Render settings are 128×128 CLASSIC, radius 16, two capture chunks per tick, one native worker, unchanged provider memory limits and a 30-second deadline. A one-second interval is a target, not a latency guarantee: slow work skips intervals; failures back off from 2 to at most 30 seconds. Images older than three seconds are labelled **STALE**; a blank authorized receiver says **NO FEED**. Unloaded nearby chunks are rejected, never generated or loaded synchronously.

Only ownership, map IDs and selected targets are stored in `bindings.yml`; monitor ownership lives in ItemFrame data. Back these up with world map/entity data. Maximum 128 receiver owners and 128 active loaded monitors. Excess restored monitors stay protected with empty frames and do not request renders; unload other monitors, then reload their chunk to reactivate them. If several idle owners later demand more than two distinct targets, the first two targets in saved owner order are admitted; other receivers wait until demand frees a slot. See [AdminPovPlugin.java](admin-pov/src/main/java/io/github/kernelperson/pov/AdminPovPlugin.java), [FeedLoop.java](admin-pov/src/main/java/io/github/kernelperson/pov/FeedLoop.java) and [PrivateMap.java](admin-pov/src/main/java/io/github/kernelperson/pov/PrivateMap.java).

## Build and adapt

Install JDK21+ and Maven, then:

```sh
mvn clean verify
```

Output: each module's `target/Renderer*.jar`. No shading is needed: depend on the public API with Maven `provided` scope and declare `depend: [ShutterBugRenderer]` in `plugin.yml`.

```xml
<dependency>
  <groupId>com.github.kernel-person</groupId>
  <artifactId>shutterbug-renderer-api</artifactId>
  <version>v1.0.1</version>
  <scope>provided</scope>
</dependency>
```

Use the JitPack repository `https://jitpack.io`. The frozen Spigot compile dependency is not a claim that the tested Paper26.2 server runs Minecraft1.21.10.

The few small lifecycle/map helpers are deliberately copied into each plugin's own package: you can study, build or adapt one module without installing another. There is no shared runtime framework. Each plugin performs Bukkit mutations on the server thread, uses a 30-second overall deadline, cancels stale work and never queues unlimited captures.

## Commercial add-ons are welcome

You are welcome to make **free or paid plugins** that use ShutterBug Renderer. The example source is Apache-2.0: adapt it for your own projects while retaining the required license/attribution notices.

Clearly tell buyers: **“Requires ShutterBug Renderer, purchased separately.”** The server owner needs a valid Renderer license within its seat limits—not every player, and not another Renderer purchase for each add-on on that server.

Distribute your add-on, not the commercial Renderer provider, native binaries or license keys. The example-code license does not grant rights to those separate commercial artifacts. Your add-on's price and support are your own; do not imply official endorsement.

[Renderer wiki and Javadocs](https://kernel-person.github.io/shutterbug-renderer-api/) · [Public SDK](https://github.com/kernel-person/shutterbug-renderer-api)

## Acceptance status

Unit tests and builds are reproducible with `mvn clean verify`. Runtime acceptance and remaining manual visual checks are recorded in [docs/acceptance.md](docs/acceptance.md); do not mistake automated geometry/event checks for an actual human painting playtest.

Release packaging: after committing reviewed source, run `python3 tools/package_examples.py --output target/ShutterBug-Renderer-Examples-1.1.0.zip`. It validates four JAR inventories and the reviewed resource-pack bytes, then records source/artifact hashes. The ZIP contains four plugin JARs, the pack, README, license, acceptance notes and manifest. No commercial provider, keys or test probe are included. Existing 1.0.0 downloads are not replaced. Editable artwork and regeneration instructions are in [artwork/README.md](artwork/README.md).
