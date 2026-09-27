import importlib.util
import io
from pathlib import Path
import unittest
import zipfile

spec=importlib.util.spec_from_file_location('package_examples',Path(__file__).with_name('package_examples.py'))
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class PackageTests(unittest.TestCase):
    def jar(self,extra=None):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w') as jar:
            jar.writestr('plugin.yml','name: RendererPostcards\ndepend: [ShutterBugRenderer]\n')
            jar.writestr('io/github/kernelperson/postcards/PostcardsPlugin.class',b'example')
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
    def test_zip_output_is_reproducible(self):
        self.assertEqual(module.make_zip({'b':b'2','a':b'1'}),module.make_zip({'a':b'1','b':b'2'}))

if __name__=='__main__': unittest.main()
