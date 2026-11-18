#!/usr/bin/env python3
"""Generate only the registered loose-powder item models and sprites."""
import argparse
import json
from powder_assets import ASSETS, MANIFEST, png_bytes, powder_model, powder_pixels, validate_inventory, write_powder_item


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='check deterministic outputs without writing')
    args = parser.parse_args()
    report = validate_inventory()
    for name, material in MANIFEST['powders'].items():
        if args.check:
            path = ASSETS / 'models/item' / (name + '.json')
            assert json.loads(path.read_text()) == powder_model(name), name
            if 'runtime_tint' not in material:
                path = ASSETS / 'textures/item' / (name + '.png')
                assert path.read_bytes() == png_bytes(powder_pixels(name)), name
        else:
            write_powder_item(name)
    print(json.dumps(report))


if __name__ == '__main__':
    main()
