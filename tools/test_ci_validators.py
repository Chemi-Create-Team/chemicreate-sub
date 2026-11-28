#!/usr/bin/env python3
"""Exercise the CI validators against valid assets and deliberate regressions."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
#!/usr/bin/env python3
"""Run the real CI validators with deliberate, read-only input regressions."""
import contextlib
import io
import json
from pathlib import Path
import runpy
import struct
import sys
import traceback
import unittest
import zlib
from unittest import mock
from generate_world_liquids import bucket_pixels

ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path('src/main/resources/assets/create_refinery_n_reactors')


class ValidatorTests(unittest.TestCase):
    def setUp(self):
        self.overrides = {}
        self.missing = set()

    def content(self, path):
        file = ROOT / path
        return self.overrides[file] if file in self.overrides else file.read_bytes()

    def edit_json(self, path, edit):
        data = json.loads(self.content(path))
        edit(data)
        self.overrides[ROOT / path] = json.dumps(data, ensure_ascii=False).encode()

    def copy_input(self, source, destination):
        self.overrides[ROOT / destination] = self.content(source)

    def change_bucket_body(self, name, alloy=False):
        template = json.loads(self.content(Path('tools/vanilla_bucket_template.json')))
        mask = set(template['content_mask'])
        pixels = bucket_pixels(0xFF9BAEB7, alloy, name)
        index = next(i for i, pixel in enumerate(pixels) if pixel[3] and i not in mask)
        red, green, blue, alpha = pixels[index]
        pixels[index] = (red ^ 1, green, blue, alpha)
        def chunk(kind, data):
            return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
        rows = b''.join(b'\0' + bytes(channel for pixel in pixels[y*16:(y+1)*16] for channel in pixel) for y in range(16))
        self.overrides[ROOT / ASSETS / 'textures/item' / (name + '.png')] = (
            b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))

    def validate(self, script, failure=None):
        original_bytes, original_text = Path.read_bytes, Path.read_text
        original_glob, original_file = Path.glob, Path.is_file

        def read_bytes(path):
            if path in self.missing:
                raise FileNotFoundError(path)
            return self.overrides[path] if path in self.overrides else original_bytes(path)

        def read_text(path, encoding=None, errors=None):
            if path in self.overrides or path in self.missing:
                return read_bytes(path).decode(encoding or 'utf-8', errors or 'strict')
            return original_text(path, encoding=encoding, errors=errors)

        def glob(path, pattern, **kwargs):
            files = [file for file in original_glob(path, pattern, **kwargs) if file not in self.missing]
            files.extend(file for file in self.overrides if file.parent == path and file.match(pattern) and file not in files)
            return iter(files)

        caught = None
        output = io.StringIO()
        try:
            with contextlib.redirect_stdout(output), mock.patch.object(sys, 'path', [str(ROOT / 'tools'), *sys.path]), \
                    mock.patch.object(Path, 'read_bytes', read_bytes), mock.patch.object(Path, 'read_text', read_text), \
                    mock.patch.object(Path, 'glob', glob), \
                    mock.patch.object(Path, 'is_file', lambda path: path not in self.missing and (path in self.overrides or original_file(path))), \
                    mock.patch.object(Path, 'write_text', lambda path, text, **kwargs: len(text)):
                runpy.run_path(str(ROOT / 'tools' / script), run_name='__main__')
        except (AssertionError, KeyError):
            caught = traceback.format_exc()
        if failure is None:
            self.assertIsNone(caught, caught)
        else:
            self.assertIsNotNone(caught, output.getvalue())
            self.assertIn(failure, caught)

    def test_current_assets_pass(self):
        for script in ('validate_plant_tutorials.py', 'validate_ponder_language.py', 'validate_visual_identity.py'):
            with self.subTest(script=script):
                self.validate(script)

    def test_missing_structure_target_fails(self):
        self.edit_json(ASSETS / 'ponder/tutorials.json', lambda data: data[0]['steps'][0].update(target=[999, 999, 999]))
        self.validate('validate_plant_tutorials.py', 'a caption must point at a model position')

    def test_missing_caption_translation_fails(self):
        first = json.loads(self.content(ASSETS / 'ponder/tutorials.json'))[0]
        key = 'create_refinery_n_reactors.ponder.shared.guide.' + first['id'] + '.' + first['steps'][0]['key']
        self.edit_json(ASSETS / 'lang/en_us.json', lambda data: data.pop(key))
        self.validate('validate_plant_tutorials.py', key)

    def test_missing_runtime_header_fails(self):
        key = 'create_refinery_n_reactors.ponder.turbine_multiblock.header'
        self.edit_json(ASSETS / 'lang/ja_jp.json', lambda data: data.pop(key))
        self.validate('validate_ponder_language.py', key)

    def test_duplicate_finished_product_fails(self):
        self.copy_input(ASSETS / 'models/item/iron_catalyst.json', ASSETS / 'models/item/ammonium_nitrate.json')
        self.validate('validate_visual_identity.py', 'ammonium_nitrate')

    def test_missing_registered_bucket_fails(self):
        self.missing.add(ROOT / ASSETS / 'models/item/air_gas_bucket.json')
        self.validate('validate_visual_identity.py', 'registered_buckets')

    def test_unregistered_bucket_fails(self):
        self.copy_input(ASSETS / 'models/item/air_gas_bucket.json', ASSETS / 'models/item/unregistered_bucket.json')
        self.validate('validate_visual_identity.py', 'registered_buckets')

    def test_changed_liquid_bucket_body_fails(self):
        self.change_bucket_body('oxygen_gas_bucket')
        self.validate('validate_visual_identity.py', 'Container metal changed')

    def test_changed_alloy_bucket_body_fails(self):
        self.change_bucket_body('sulfuric_acid_bucket', alloy=True)
        self.validate('validate_visual_identity.py', 'Container metal changed')

    def test_ordinary_body_cannot_replace_alloy_bucket_body(self):
        self.copy_input(ASSETS / 'textures/item/oxygen_gas_bucket.png', ASSETS / 'textures/item/sulfuric_acid_bucket.png')
        self.validate('validate_visual_identity.py', 'Container metal changed')

    def test_missing_powder_tint_fails(self):
        path = Path('atomics/src/main/java/net/createplant/atomics/client/AtomicsClient.java')
        text = self.content(path).decode()
        line = next(line for line in text.splitlines(True) if 'CEUranium.URANIUM_TRIOXIDE.get()' in line)
        self.overrides[ROOT / path] = text.replace(line, '').encode()
        self.validate('validate_visual_identity.py', 'Missing runtime item tints')

    def test_duplicate_powder_tints_fail(self):
        path = Path('atomics/src/main/java/net/createplant/atomics/client/AtomicsClient.java')
        self.overrides[ROOT / path] = self.content(path).replace(b'0xFFFFB850', b'0xFF8A7464')
        self.validate('validate_visual_identity.py', 'uranium_trioxide')

    def test_changed_powder_silhouette_fails(self):
        from powder_assets import png_bytes, powder_pixels
        pixels = powder_pixels('sulfur_dust')
        pixels[0] = (205, 181, 75, 255)
        self.overrides[ROOT / ASSETS / 'textures/item/sulfur_dust.png'] = png_bytes(pixels)
        self.validate('validate_visual_identity.py', 'Gunpowder silhouette or grain shading differs: sulfur_dust')

    def test_new_registered_dust_needs_classification(self):
        path = Path('src/main/java/net/createplant/registry/CEItems.java')
        self.overrides[ROOT / path] = self.content(path) + b'\nITEMS.registerSimpleItem("unclassified_dust");\n'
        self.validate('validate_visual_identity.py', 'unclassified_dust')

    def test_shared_housings_do_not_allow_other_duplicates(self):
        self.copy_input(ASSETS / 'models/item/condenser_cooling_inlet.json', ASSETS / 'models/item/iron_catalyst.json')
        self.validate('validate_visual_identity.py', 'iron_catalyst')


if __name__ == '__main__':
    unittest.main()
