# Renderer examples 1.1.0 artwork

Original programmatic Minecraft artwork by Kernel Person. This is the authoritative revised example-art project; the supplied Village Trades source project remains untouched. `history/approved-1.0.0.zip` preserves the approved easel/brush export. The old wood/brush PNG inputs and brush native geometry are retained, with corrected pixel-unit UVs in editable Blockbench snapshots only.

Target inspected client: Minecraft Java **26.2**, native resource format **88.0**, Java25. No Nexo dependency and no vanilla item replacement. Native components select these items individually:

| Item model | Purpose |
| --- | --- |
| `village_trades:redstone_camera` | Inventory camera |
| `village_trades:redstone_camera_body` | Fixed dispenser housing |
| `village_trades:redstone_camera_lens` | Separately aimed lens |
| `village_trades:painters_easel` | Existing easel, now with opaque canvas slab |
| `village_trades:paintbrush` | Preserved original brush |
| `village_trades:pov_monitor` | Frame/stand/backing around a plugin-rendered map |

## Rebuild and preview

Use Python3 with Pillow (tested 12.1.1), Node and npm:

```sh
python3 tools/generate.py
python3 -m unittest discover -s tools -p 'test_*.py'
npm ci
npm run build
npm run dev -- --host 127.0.0.1 --port 5187 --strictPort
python3 tools/package_pack.py --output /your/new/path/renderer-examples-resourcepack-1.1.0.zip
```

The output packager uses exclusive creation: choose a new destination for verification rather than overwriting approved releases. ZIP entries are sorted with fixed metadata. `art/*.png` are persistent editable inputs, never repainted by normal generation. `--seed-art` seeds missing inputs only. Geometry/atlas layouts belong to `tools/`; `.bbmodel` files are editable snapshots, not an independent native-export source of truth. The browser reads the actual generated item definitions, UVs, PNGs, model geometry and exported GUI/fixed transforms.

## Placement contracts

See `handoff/camera-contract.json`, `canvas-contract.json`, `monitor-contract.json` and per-model atlas layouts. One texel per model unit, including fractional spans on thin details. Native fixed transforms are identity; Minecraft ItemDisplay's intrinsic Y180 is accounted for once by the Java adapters.

- Camera body is stationary at the dispenser centre, scaled 1.002 to avoid coplanar block faces. Its conservative half-extent is 0.5323125 blocks. The aimed lens socket lies at `center + direction*(halfExtent/maxAbs(direction)+0.015)`. Capture is another 0.25+0.05 blocks forward. Both display entities stay in the owning block's chunk; a transformation moves the lens artwork only. Runtime properties are checked against the generated contract.
- Easel map remains X0..16, Y16..32, Z10. Opaque backing spans Z7.9375..9.9375, leaving 0.0625 model units to the live plane. The original two-block height and runtime map translation are unchanged. Wood/brush source pixels are preserved exactly.
- Monitor map is X0..16, Y0..16, Z10; raw ground anchor `[8,-16,8]`. Backing stops at Z9.9375. The frame fills the same invisible-map face offset as the easel, with no extra vertical artwork translation. Its real map is supplied by a contextual Java renderer. Two owned supports and the map entity share a chunk.

Browser screenshots are in `previews/`. They establish native-export appearance from the selected views, not Minecraft loading, exact client lighting, held animation, or successful Paper placement. Actual client acceptance and sustained licensed POV cadence are recorded separately in `../docs/acceptance.md`.
