#!/usr/bin/env python3
"""Containers for process gases, using the established vanilla bucket silhouette."""
import hashlib
import json
import re
from generate_world_liquids import ASSETS, ROOT, fluid_entries, bucket_image


def gas_entries():
    source = (ROOT / 'src/main/java/net/createplant/registry/CEGasBuckets.java').read_text()
    existing = set(re.findall(r'case "([a-z_]+)"', source))
    return [entry for entry in fluid_entries() if entry['gas'] and entry['name'] not in existing]


# Gases that are coloured in reality: a sealed gas bucket drawn as the nitrogen dioxide one, with the gas lying in its real colour at the bottom.
COLOURED_GASES = {'chlorine': (170, 196, 38), 'fluorine': (214, 208, 98)}


def coloured_gas_buckets():
    from PIL import Image
    textures = ASSETS / 'textures/item'
    template = Image.open(textures / 'nitrogen_dioxide_bucket.png').convert('RGBA')
    gas = (153, 89, 49, 255)
    for name, colour in COLOURED_GASES.items():
        image = template.copy()
        for y in range(16):
            for x in range(16):
                if image.getpixel((x, y)) == gas: image.putpixel((x, y), colour + (255,))
        image.save(textures / (name + '_bucket.png'))


def generate():
    gases = gas_entries()
    for entry in gases:
        signature = hashlib.sha256(entry['name'].encode()).digest()
        channels = [max(0, min(255, ((entry['color'] >> shift) & 255) + signature[i] % 25 - 12))
            for i, shift in enumerate((16, 8, 0))]
        color = 0xff000000 | channels[0] << 16 | channels[1] << 8 | channels[2]
        bucket_image(entry['name'] + '_bucket', color)
    coloured_gas_buckets()
    for locale in ('en_us', 'ja_jp', 'zh_cn'):
        path = ASSETS / 'lang' / (locale + '.json')
        strings = json.loads(path.read_text())
        for entry in gases:
            name = entry['name'];fluid = strings['fluid.create_refinery_n_reactors.' + name]
            strings['item.create_refinery_n_reactors.' + name + '_bucket'] = ('Bucket of ' + fluid if locale == 'en_us'
                else fluid + '入りバケツ' if locale == 'ja_jp' else fluid + '桶')
        path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n')
    print(f'Generated {len(gases)} process-gas buckets')


if __name__ == '__main__':
    generate()
