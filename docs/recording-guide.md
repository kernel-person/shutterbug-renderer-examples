# Recording the four examples

For the current unreleased development branch, use the locally supplied SDK **v1.0.2-SNAPSHOT** and matching development provider described in the [README](../README.md). Published examples **1.1.1**, Renderer **1.0.0** and SDK **v1.0.1** downloads remain unchanged. The resource pack remains **1.1.0**. These are shot instructions, not a claim that the latest build has passed client acceptance.

## Prepare

Use Java 25 / Paper 26.2 / Minecraft 26.2 and an already activated macOS ARM64 or Linux x86-64 Renderer. Stop the server before replacing example JARs, then restart; do not use `/reload`. Keep your existing world, plugin data and license. Back up the world and example data together. Do not film license configuration, account information or private console output.

Install the four example JARs and accept `renderer-examples-resourcepack-1.1.0.zip`. Configure the model options in the [README](../README.md). Use a loaded, well-lit scene with no other rendering jobs running. Enable client particles for the flash shot. Windows remains coming soon.

## Painter's Easel — about 20–30 seconds

1. `/easel`, then place it with an interesting view beyond its back.
2. Show the blank linen canvas and its opaque, thick backing from the side.
3. Right-click the canvas with the supplied brush and wait for the reference capture to finish.
4. Put cyan dye in the offhand; hold right-click while sweeping across the canvas. Repeat with yellow and magenta. Include a green area to show cyan + yellow combining.
5. Sneak-right-click with the brush to collect the painting, then hold the resulting map toward the recording camera.

Only the initial scene capture renders. Painting strokes update the existing map; they are not repeated native renders. The creator owns painting and collection.

## Redstone Camera — about 10–15 seconds

1. `/rendercamera`; place it like a vanilla dispenser, with the lens facing toward you. Stand on the scene side of the block while placing, then step aside. Only north, south, east, west, straight up and straight down are supported; place from above/below for vertical shots. Break and place afresh to change aim. Existing cameras use their dispenser facing, not old diagonal angles.
2. Frame the camera lens from an oblique angle and pulse adjacent redstone once.
3. An accepted request emits one brief white flash just outside the lens. This is a visual particle, not illumination of nearby blocks, and it means **capture started**, not that the photo is ready.
4. Wait for the high success tone, open the dispenser and take the map from its inventory. Show the resulting photo.

A powered camera does not continuously shoot. Busy/cooldown/unavailable requests and full inventories do not flash. Wait at least five seconds and until the previous render finishes before another pulse. Particle visibility and duration are client-dependent; check the actual recording before relying on it.

## Admin POV — solo, without another account

1. As an operator, run `/pov demo` or `/pov YourExactPlayerName` for a photographable self-view at the current shared rate (default **4 updates/second**, 250 ms start-to-start). To choose the rate, use `/pov demo 4` or another whole number **1-20**. The optional rate changes **all POV feeds until server restart**, which restores 4; plain `/pov demo` only selects self and retains the current rate. Every POV receiver and monitor is **public**, including copied maps and nearby viewers. There is no private mode or viewer redaction.
2. Hold the supplied map in the offhand so the rendered view and world can appear together. Slowly turn between two recognizably different scenes; pause for each snapshot to arrive.
3. Run `/pov monitor`, place the desk monitor, and face its screen within 16 blocks with a clear line of sight. Keep holding the receiver if you turn away from the monitor, otherwise demand may stop.
4. For the mirror-tunnel shot, look into your own public monitor and pause: each completed update includes the previous completed frame, not a recursive render. You can also photograph this public screen with the redstone camera. No frame queue or recording history is added.
5. Run `/pov stop` to demonstrate **NO FEED** on the same public screen. Restart restores the same receiver/monitor identity without old pixels, then authorized demand can produce a fresh frame. Permission/access loss removes the owner's demand at the next one-tick check; periodic map/status delivery remains every five ticks (both take longer if ticks lag). Target invalidation, provider removal and shutdown discard applicable work. There is no mode change, and existing photographs or client screenshots are not erased.

Caption this **“Solo self-view demo — periodic rendered snapshots.”** It demonstrates the same render-to-map path without friends. It does not demonstrate another player's POV or multi-owner command controls. Do not call it live screen sharing: HUD, inventory, chat, shaders and audio are not captured. Use a landscape for the normal view and your own public monitor for deliberate feedback.

For actual other-player footage, use a second legitimate Minecraft account/client that you control, or invite one tester. Keep server account authentication enabled. Target that account with `/pov ExactOtherName`, move it, then film the receiver or desk screen: any nearby viewer can see and photograph its shared pixels. A solo clip does not replace two-owner/shared-target, copied-map public-viewing and owner-only command/placement/removal checks. Only the authorized owner's viewing demand drives captures.

The cadence is **best-effort, not a guaranteed frame rate or latency**. Tick resolution/lag, two targets sharing the pipeline and slow captures/renders can reduce the achieved rate. Slow renders add no full interval after completion and skip missed frames without a catch-up backlog; delayed images show STALE. Maximum two active target feeds, served round-robin, and one capture/render pipeline globally, with no recording history. Changing rate never cancels healthy work or bypasses the exponential 2-30 second failure cooldown. Completed frames are sent immediately; periodic status/map-cache sends remain every five ticks. Record at least one uncut sequence so the clip represents actual performance.

## Postcards — optional short fourth clip

Look at a scene, run `/postcard`, wait for delivery and hold up the map. This is the smallest public-API example.

## Before the listing goes live

- Replace the draft video placeholders with real clips; do not use model-browser previews as in-game footage.
- Verify this exact examples build in the client, including flash visibility, photo pickup, painting, POV, restart and removal.
- Finish the separate supported MCModels buyer-download/activation test and authorized Renderer production issuance work. Neither this recording guide nor a successful manual-key playtest proves buyer autoactivation.
- Keep MCModels resource 17590 draft/unsubmitted until explicitly authorized to submit. No RIC or marketplace configuration changes are part of this update.
