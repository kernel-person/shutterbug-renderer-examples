import importlib.util
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

spec=importlib.util.spec_from_file_location('package_examples',Path(__file__).with_name('package_examples.py'))
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class PackageTests(unittest.TestCase):
    def jar(self,extra=None,package='postcards'):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w') as jar:
            jar.writestr('plugin.yml','name: RendererPostcards\ndepend: [ShutterBugRenderer]\n')
            jar.writestr('io/github/kernelperson/'+package+'/Example.class',b'example')
            if extra: jar.writestr(*extra)
        return stream.getvalue()
    def test_independent_example_is_allowed(self):
        module.verify_jar(self.jar(),'postcards')
    def test_commercial_api_native_probe_and_secret_are_rejected(self):
        for path,content in [('ke/ric/renderer/api/RendererService.class',b'api'),('native.dll',b'native'),
                             ('io/github/kernelperson/probe/ExamplesProbe.class',b'probe'),
                             ('io/github/kernelperson/postcards/Bad.class',b'-----BEGIN PRIVATE KEY-----')]:
            with self.subTest(path=path),self.assertRaises(ValueError):
                module.verify_jar(self.jar((path,content)),'postcards')
    def test_duplicate_members_are_rejected(self):
        with self.assertRaises(ValueError): module.verify_jar(self.jar(('plugin.yml','duplicate')),'postcards')
    def test_easel_config_is_allowed(self):
        module.verify_jar(self.jar(('config.yml',"model-item: ''\nbrush-model-item: ''\n"),package='easel'),'easel')
    def test_camera_and_pov_allow_only_their_declared_resources(self):
        for package in ('camera','pov'):
            module.verify_jar(self.jar(('config.yml','native-models: false\n'),package=package),package)
        module.verify_jar(self.jar(('camera-model.properties','body-scale=1.002\n'),package='camera'),'camera')
    def test_other_resources_remain_forbidden(self):
        for package,path in [('postcards','config.yml'),('pov','camera-model.properties'),('easel','other.yml')]:
            with self.subTest(package=package,path=path),self.assertRaises(ValueError):
                module.verify_jar(self.jar((path,b'not allowed'),package=package),package)
    def test_localhost_default_is_rejected(self):
        for url in ('http://localhost:8000/pack.zip','http://127.0.0.1:8000/pack.zip'):
            with self.subTest(url=url),self.assertRaises(ValueError):
                module.verify_jar(self.jar(('config.yml','resource-pack: '+url+'\n'),package='easel'),'easel')
    def test_ipv6_loopback_default_is_rejected(self):
        for url in ('http://[::1]:8000/pack.zip','http://[0:0:0:0:0:0:0:1]/pack.zip',
                    'http://[::1%lo0]/pack.zip','http://[::ffff:7f00:1]/pack.zip'):
            with self.subTest(url=url),self.assertRaises(ValueError):
                module.verify_jar(self.jar(('config.yml','resource-pack: '+url+'\n'),package='easel'),'easel')
    def make_package_root(self,root):
        (root/'docs').mkdir()
        (root/'README.md').write_text('Install examples\n')
        (root/'LICENSE').write_text('Apache-2.0\n')
        (root/'docs'/'acceptance.md').write_text('Acceptance\n')
        (root/'docs'/'release-1.1.0-evidence.md').write_text('Evidence\n')
        (root/'docs'/'release-1.1.1-evidence.md').write_text('Flash verification\n')
        (root/'docs'/'release-1.2.0-evidence.md').write_text('Stable SDK and public-screen verification\n')
        (root/'docs'/'development-1.3.0.md').write_text('Development recipe migration\n')
        (root/'docs'/'recording-guide.md').write_text('Solo POV and camera recording walkthrough\n')
        for folder,jar_name,package in [('postcards','RendererPostcards','postcards'),
                                        ('redstone-camera','RendererRedstoneCamera','camera'),
                                        ('painters-easel','RendererPaintersEasel','easel'),
                                        ('admin-pov','RendererAdminPov','pov')]:
            target=root/folder/'target'
            target.mkdir(parents=True)
            (target/(jar_name+'.jar')).write_bytes(self.jar(package=package))
        pack_source=Path(__file__).resolve().parents[1]/'resourcepacks'/module.RESOURCE_PACK
        self.assertTrue(pack_source.is_file(),'the distributable pack must be checked into the examples repository')
        pack=pack_source.read_bytes()
        (root/'resourcepacks').mkdir()
        (root/'resourcepacks'/module.RESOURCE_PACK).write_bytes(pack)
        return pack
    def test_bundle_contains_byte_exact_easel_and_brush_pack(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            pack=self.make_package_root(root)
            self.assertEqual(module.RESOURCE_PACK_SHA256,
                             hashlib.sha256(pack).hexdigest())
            files=module.collect_files(root)
            self.assertEqual({'README.md','LICENSE','docs/acceptance.md','docs/release-1.1.0-evidence.md',
                              'docs/release-1.1.1-evidence.md','docs/release-1.2.0-evidence.md','docs/development-1.3.0.md','docs/recording-guide.md',
                              'RendererPostcards.jar','RendererRedstoneCamera.jar','RendererPaintersEasel.jar','RendererAdminPov.jar',
                              module.RESOURCE_PACK},set(files))
            self.assertEqual(pack,files[module.RESOURCE_PACK])
    def test_mutated_resource_pack_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            self.make_package_root(root)
            pack=root/'resourcepacks'/module.RESOURCE_PACK
            pack.write_bytes(pack.read_bytes()+b'changed')
            with self.assertRaises(ValueError): module.collect_files(root)
    def test_zip_output_is_reproducible(self):
        self.assertEqual(module.make_zip({'b':b'2','a':b'1'}),module.make_zip({'a':b'1','b':b'2'}))
    def test_cli_packages_stable_release_with_reproducible_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            self.make_package_root(root)
            (root/'tools').mkdir()
            script=root/'tools'/'package_examples.py'
            script.write_bytes(Path(module.__file__).read_bytes())
            subprocess.run(['git','init','-q',str(root)],check=True)
            subprocess.run(['git','add','.'],cwd=root,check=True)
            subprocess.run(['git','-c','user.name=Release Test','-c','user.email=release@example.invalid',
                            'commit','-qm','Packaging fixture'],cwd=root,check=True)
            commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
            outputs=[root.parent/(root.name+'-first.zip'),root.parent/(root.name+'-second.zip')]
            try:
                for output in outputs:
                    subprocess.run([sys.executable,str(script),'--output',str(output)],check=True,capture_output=True)
                self.assertEqual(outputs[0].read_bytes(),outputs[1].read_bytes())
                with zipfile.ZipFile(outputs[0]) as archive:
                    manifest=json.loads(archive.read('manifest.json'))
                    self.assertEqual('1.3.0-SNAPSHOT',manifest['version'])
                    self.assertEqual('2.3.0-SNAPSHOT',manifest['sdkVersion'])
                    self.assertEqual(commit,manifest['sourceCommit'])
                    self.assertFalse(manifest['providerIncluded'])
                    self.assertFalse(manifest['testProbeIncluded'])
                    self.assertIn('docs/release-1.2.0-evidence.md',manifest['sha256'])
                    for name,checksum in manifest['sha256'].items():
                        self.assertEqual(hashlib.sha256(archive.read(name)).hexdigest(),checksum)
                for output in outputs:
                    self.assertEqual(hashlib.sha256(output.read_bytes()).hexdigest()+'  '+output.name+'\n',
                                     output.with_suffix('.zip.sha256').read_text())
            finally:
                for output in outputs:
                    output.unlink(missing_ok=True)
                    output.with_suffix('.zip.sha256').unlink(missing_ok=True)

if __name__=='__main__': unittest.main()
