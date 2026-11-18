"""Shared gunpowder-shaped item sprites for registered loose process powders."""
import json
import re
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
MANIFEST = json.loads(Path(__file__).with_name('powder_items.json').read_text())
SHAPE = json.loads(Path(__file__).with_name('vanilla_gunpowder_shape.json').read_text())['rows']
SHADE = (.45, .60, .75, .85, .95, 1.05, 1.15)


def pixels_for_colour(colour):
    palette = [tuple(max(0, min(255, round(c * shade))) for c in colour) + (255,) for shade in SHADE]
    return [(0, 0, 0, 0) if value == '.' else palette[int(value)] for row in SHAPE for value in row]


def powder_pixels(name):
    return pixels_for_colour(MANIFEST['powders'][name]['colour'])


def powder_model(name):
    texture = 'minecraft:item/gunpowder' if 'runtime_tint' in MANIFEST['powders'][name] else NS + 'item/' + name
    return {'parent': 'minecraft:item/generated', 'textures': {'layer0': texture}}


def png_bytes(pixels):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    rows = b''.join(b'\0' + bytes(channel for pixel in pixels[y*16:(y+1)*16] for channel in pixel) for y in range(16))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


def write_powder_item(name):
    if 'runtime_tint' not in MANIFEST['powders'][name]:
        path = ASSETS / 'textures/item' / (name + '.png')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(png_bytes(powder_pixels(name)))
    path = ASSETS / 'models/item' / (name + '.json')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(powder_model(name), indent=2) + '\n')


def registered_material_items():
    registry = ROOT / 'src/main/java/net/createplant/registry'
    items = {name for source in registry.glob('*.java') for name in re.findall(
        r'ITEMS\.register(?:SimpleItem)?\(\s*"([a-z0-9_]+)"', source.read_text()) if not name.endswith('_bucket')}
    metals = (registry / 'CEMetals.java').read_text()
    mineral_loop = re.search(r'for\s*\(String ore\s*:\s*new String\[\]\s*\{([^}]+)\}', metals)
    assert mineral_loop, 'The dynamic ore-mineral registrations need to be audited'
    items.update(re.findall(r'"([a-z_]+)"', mineral_loop.group(1)))
    item_source = (registry / 'CEItems.java').read_text()
    block_source = (registry / 'CEBlocks.java').read_text()
    for name in ('triiron_tetroxide_pile',):
        assert 'registerSimpleBlockItem(CEBlocks.' + name.upper() + ')' in item_source, name
        assert '"' + name + '"' in block_source, name
        items.add(name)
    return items


def validate_inventory():
    powders = set(MANIFEST['powders'])
    preserved = set(MANIFEST['preserved_items'])
    registered = registered_material_items()
    assert not powders & preserved, 'An item cannot be both powder and preserved'
    assert registered == powders | preserved, ('Unclassified or retired material items',
        sorted(registered - powders - preserved), sorted((powders | preserved) - registered))
    assert len(SHAPE) == 16 and all(len(row) == 16 for row in SHAPE)
    assert sum(value != '.' for row in SHAPE for value in row) == 88
    fixture = (ROOT / 'src/main/java/net/createplant/client/PowderItemsClientCheck.java').read_text()
    fixture_items = re.search(r'POWDERS = List\.of\((.*?)\);', fixture, re.DOTALL)
    assert fixture_items and set(re.findall(r'"([a-z_]+)"', fixture_items.group(1))) == powders, \
        'Native item fixture must cover every registered powder'
    return {'powders': len(powders), 'preserved_material_items': len(preserved), 'opaque_pixels_per_powder': 88}


def validate_assets(read_rgba):
    report = validate_inventory()
    client = (ROOT / 'src/main/java/net/createplant/ElectrolysisClient.java').read_text()
    tints = {name.lower(): int(colour, 16) & 0xffffff for colour, name in re.findall(
        r'event\.register\(\(stack, layer\) -> (0x[0-9A-Fa-f]+), net\.createplant\.registry\.CEUranium\.([A-Z_]+)\.get\(\)\);', client)}
    for name, material in MANIFEST['powders'].items():
        actual = json.loads((ASSETS / 'models/item' / (name + '.json')).read_text())
        assert actual == powder_model(name), 'Powder must use its generated sprite: ' + name
        if 'runtime_tint' in material:
            assert tints.get(name) == material['runtime_tint'], 'Original material tint changed: ' + name
        else:
            actual = read_rgba(ASSETS / 'textures/item' / (name + '.png'))
            assert actual == powder_pixels(name), 'Gunpowder silhouette or grain shading differs: ' + name
    return report
