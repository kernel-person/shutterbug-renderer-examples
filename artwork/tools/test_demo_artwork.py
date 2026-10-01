"""Behavior checks for native exports, not a substitute for client inspection."""
import hashlib
import base64
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]

class DemoArtworkTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.output = Path(cls.temp.name)
        subprocess.run([sys.executable, str(ROOT/'tools/generate.py'), '--output', str(cls.output),
                        '--art-dir', str(ROOT/'art')], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def model(self, name):
        return json.loads((self.output/f'public/resourcepack/assets/village_trades/models/item/{name}.json').read_text())

    def test_canvas_has_opaque_thickness_without_covering_live_plane(self):
        parts = self.model('painters_easel')['elements']
        slabs = [p for p in parts if p['name'] == 'canvas_backing']
        self.assertEqual(1, len(slabs), 'live map needs a physical opaque rear, not an open frame')
        slab = slabs[0]
        self.assertEqual([0,16], slab['from'][:2])
        self.assertEqual([16,32], slab['to'][:2])
        self.assertGreaterEqual(slab['to'][2]-slab['from'][2], 1.9)
        self.assertLess(slab['to'][2], 10, 'never cover the live-map plane')
        self.assertEqual({'north','south','up','down','east','west'}, set(slab['faces']))
        linen = Image.open(self.output/'public/resourcepack/assets/village_trades/textures/item/canvas_linen.png').convert('RGBA')
        self.assertEqual((255,255), linen.getchannel('A').getextrema())
        self.assertGreater(len(set(linen.get_flattened_data())), 1)

    def test_camera_and_monitor_native_definitions_resolve_to_real_geometry(self):
        for name in ('redstone_camera','redstone_camera_body','redstone_camera_lens','pov_monitor'):
            definition = self.output/f'public/resourcepack/assets/village_trades/items/{name}.json'
            self.assertTrue(definition.is_file(), f'{name} missing')
            self.assertGreater(len(self.model(name)['elements']), 0)
            self.assertTrue((self.output/f'blockbench/{name}.bbmodel').is_file())

    def test_blockbench_snapshots_use_pixel_uvs_and_embed_exported_pixels(self):
        for name in ('painters_easel','paintbrush','redstone_camera','redstone_camera_body','redstone_camera_lens','pov_monitor'):
            model=self.model(name)
            bb=json.loads((self.output/f'blockbench/{name}.bbmodel').read_text())
            textures={t['uuid']:t for t in bb['textures']}
            for native,editable in zip(model['elements'],bb['elements']):
                self.assertEqual(native['from'],editable['from']);self.assertEqual(native['to'],editable['to'])
                for side,face in native['faces'].items():
                    saved=editable['faces'][side];texture=textures[saved['texture']]
                    expected=[value*texture['width']/16 for value in face['uv']]
                    self.assertEqual(expected,saved['uv'],f'{name}/{native["name"]}/{side}')
                    image=Image.open(io.BytesIO(base64.b64decode(texture['source'].split(',',1)[1]))).convert('RGBA')
                    resource=model['textures'][face['texture'][1:]].split(':')[1]
                    exported=Image.open(self.output/f'public/resourcepack/assets/village_trades/textures/{resource}.png').convert('RGBA')
                    self.assertEqual(image.tobytes(),exported.tobytes())

    def test_java_optics_match_generated_contract_and_body_clears_backing_block(self):
        contract=json.loads((self.output/'handoff/camera-contract.json').read_text())
        properties=dict(line.split('=',1) for line in (ROOT.parent/'redstone-camera/src/main/resources/camera-model.properties').read_text().splitlines() if '=' in line)
        for java,art in [('body-half-extent','body_half_extent_blocks'),('body-scale','body_scale'),('socket-gap','lens_socket_gap_blocks'),('lens-front','lens_front_blocks'),('capture-clearance','capture_clearance_blocks')]:
            self.assertAlmostEqual(float(properties[java]),contract[art])
        self.assertGreater(contract['body_scale'],1,'no coplanar dispenser/case surfaces')

    def test_all_native_faces_have_square_unit_density_and_legal_references(self):
        for name in ('painters_easel','paintbrush','redstone_camera','redstone_camera_body','redstone_camera_lens','pov_monitor'):
            model=self.model(name)
            for part in model['elements']:
                self.assertTrue(all(-16<=v<=32 for v in part['from']+part['to']))
                dx,dy,dz=[b-a for a,b in zip(part['from'],part['to'])]
                for side,face in part['faces'].items():
                    resource=model['textures'][face['texture'][1:]].split(':')[1]
                    with Image.open(self.output/f'public/resourcepack/assets/village_trades/textures/{resource}.png') as image:width,height=image.size
                    u,v,U,V=face['uv'];self.assertTrue(all(0<=n<=16 for n in face['uv']))
                    expected=(dx,dy) if side in ('north','south') else (dz,dy) if side in ('east','west') else (dx,dz)
                    self.assertAlmostEqual(expected[0],abs(U-u)*width/16,msg=f'{name}/{part["name"]}/{side}')
                    self.assertAlmostEqual(expected[1],abs(V-v)*height/16,msg=f'{name}/{part["name"]}/{side}')

    def test_existing_brush_and_editable_texture_pixels_are_preserved(self):
        before = Image.open(ROOT/'art/paintbrush.png').convert('RGBA').tobytes()
        after = Image.open(self.output/'public/resourcepack/assets/village_trades/textures/item/paintbrush.png').convert('RGBA').tobytes()
        self.assertEqual(before, after)
        expected = Image.open(ROOT/'art/painters_easel.png').convert('RGBA').tobytes()
        self.assertEqual(expected, Image.open(self.output/'public/resourcepack/assets/village_trades/textures/item/painters_easel.png').convert('RGBA').tobytes())

    def test_generation_is_identical_and_does_not_repaint_inputs(self):
        def inputs():
            return {p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'art').glob('*.png')}
        before=inputs()
        with tempfile.TemporaryDirectory() as other:
            subprocess.run([sys.executable, str(ROOT/'tools/generate.py'), '--output', other,
                            '--art-dir', str(ROOT/'art')], check=True, capture_output=True)
            first={str(p.relative_to(self.output)):p.read_bytes() for p in self.output.rglob('*') if p.is_file()}
            second={str(p.relative_to(other)):p.read_bytes() for p in Path(other).rglob('*') if p.is_file()}
            self.assertEqual(first,second)
            for relative,data in first.items():self.assertEqual(data,(ROOT/relative).read_bytes(),f'outdated checked-in export: {relative}')
        self.assertEqual(before,inputs())

    def test_reproducible_pack_matches_reviewed_distribution(self):
        from package_pack import package
        first=package(self.output/'public/resourcepack')
        self.assertEqual(first,package(self.output/'public/resourcepack'))
        self.assertEqual(first,(ROOT.parent/'resourcepacks/renderer-examples-resourcepack-1.1.0.zip').read_bytes())

if __name__=='__main__': unittest.main()
