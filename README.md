# ShutterBug Renderer — Java examples

Three independent, small plugins built **only** against the public Renderer API. Start with Postcards: its command handler is the shortest capture → render → Minecraft map example.

| Plugin | Try it | Demonstrates |
| --- | --- | --- |
| RendererPostcards | `/postcard` | Capture your view, submit a render, convert it to a persistent map |
| RendererRedstoneCamera | `/rendercamera` | A placed dispenser camera, rising-edge redstone, bounded asynchronous work |
| RendererPaintersEasel | `/easel` | Raw rendered pixels, game-like CMY painting, canvas hit coordinates and saved progress |

## Install

1. Run the tested server combination: **Java 25, Paper 26.2, Minecraft 26.2**.
2. Install and activate **ShutterBug Renderer**, purchased separately. These examples do not include its commercial JAR, native libraries or a license key.
3. Copy any of the three example JARs into `plugins/` and restart. Each is independent; no shared support plugin is required.
4. Stand in an already loaded area. Examples capture a 32-block radius; they do not generate or preload distant chunks.

Renderer platforms: **macOS ARM64 and Linux x86-64**. Windows is coming soon and is not supported by this release. Java 21 is the SDK/source compilation baseline, not the server runtime requirement.

The renderer must register an active `RendererService`. If activation fails, commands report that the renderer is unavailable; they never handle license keys themselves. Disabling/reloading the provider cancels outstanding work. A server restart is the supported upgrade method.

## Try the examples

### Postcards — the smallest starting point

Give yourself one empty inventory slot and run `/postcard`. You receive a 128×128 map of your view. It still displays after a server restart.

Permission `rendererexamples.postcards` defaults to everyone. Each player can have one active request, with a five-second cooldown. A disconnected player or full inventory does not receive a dropped or duplicated map.

Read [PostcardsPlugin.java](postcards/src/main/java/io/github/kernelperson/postcards/PostcardsPlugin.java), then [RenderSession.java](postcards/src/main/java/io/github/kernelperson/postcards/RenderSession.java).

### Redstone Camera

As an operator, run `/rendercamera` and place the dispenser while facing the direction you want to photograph. Pulse adjacent redstone, then open the dispenser to find the map. Continuous power takes one picture, not a stream.

The camera suppresses ordinary dispensing, needs an empty inventory slot, ignores triggers while busy/cooling down and does not consume materials. A high pling means success; a low tone means busy, full or failed. Break it to remove it; obtain another tagged camera with the command. An ordinary dispenser is never treated as a camera.

Permission `rendererexamples.camera` defaults to operators. Locations and random instance identities persist in dispenser data. An unloaded, broken or replaced camera cannot receive an old result. The teaching example limits itself to 256 loaded cameras and eight concurrent renders.

Read [RedstoneCameraPlugin.java](redstone-camera/src/main/java/io/github/kernelperson/camera/RedstoneCameraPlugin.java).

### Painter's Easel

As an operator, make two empty inventory slots and run `/easel`.

1. Place the fence item: it builds a temporary fence/backboard/framed-map easel facing you. Leave space above and in front.
2. Hold the supplied brush in your main hand and right-click the canvas. It captures **one fixed view beyond the back of the easel**, clear of the dummy model.
3. Put cyan, magenta or yellow dye in your offhand. Punch where you want to paint on the map.
4. Each soft brush stroke adds only pigment needed there. Cyan + yellow builds green; magenta does nothing to a purely green area. Three pigments together create dark tones. This is a forgiving game, not physical paint simulation.
5. Watch completion in the action bar. Sneak + right-click with the brush to collect your painting, even before it reaches 100%.
6. Right-click again to begin a new canvas, or look at the empty easel and use `/easel remove`.

Only the creator paints, collects or removes an easel. Other players may view it. Brushes/dyes are not consumed. Progress saves every five seconds, on chunk unload and graceful shutdown; an abrupt crash can lose the last five seconds of strokes. Collected maps remain viewable after restarts. Back up the world's map data **and** this plugin's data together.

Permission `rendererexamples.easel` defaults to operators. Limit: 128 loaded easels. The custom model is intentionally absent: the vanilla dummy can be replaced independently of the capture/painting logic.

If a saved canvas is damaged, or more than 128 easels are loaded from existing chunks, affected easels stay protected but inactive. For a damaged canvas, stop the server, restore `canvases/<frame UUID>.cmy` from a backup and restart; the console identifies that UUID. For excess easels, unload other easel chunks, then reload the affected chunk. The plugin never silently discards a damaged painting or permits another player to take it.

Read [PigmentCanvas.java](painters-easel/src/main/java/io/github/kernelperson/easel/PigmentCanvas.java), [CanvasHit.java](painters-easel/src/main/java/io/github/kernelperson/easel/CanvasHit.java) and [PaintersEaselPlugin.java](painters-easel/src/main/java/io/github/kernelperson/easel/PaintersEaselPlugin.java).

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

Release packaging: after committing reviewed source, run `python3 tools/package_examples.py --output target/ShutterBug-Renderer-Examples-1.0.0.zip`. It validates the three JAR inventories and records source/artifact hashes. The test-only acceptance probe is never included.
