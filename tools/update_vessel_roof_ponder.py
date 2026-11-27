#!/usr/bin/env python3
"""Move the old bottom-mounted drives in checked-in Ponder structures to the vessel roof."""
import gzip
from io import BytesIO
from pathlib import Path

from nbtlib import Compound, Int, List, String, load

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors/ponder'
NS = 'create_refinery_n_reactors:'


def move_drives(path):
    structure = load(path)
    palette = structure['palette']
    blocks = {tuple(block['pos'].unpack()): block for block in structure['blocks']}

    def state(position):
        return palette[int(blocks[position]['state'])] if position in blocks else {}

    def put(position, props, nbt=None):
        if position in blocks:
            raise ValueError(f'{path.name}: occupied position {position}')
        if props not in palette:
            palette.append(props)
        result = Compound({'pos': List[Int](position), 'state': Int(palette.index(props))})
        if nbt is not None:
            result['nbt'] = nbt
        blocks[position] = result

    def move(source, target, props=None):
        original = blocks[source]
        props = props if props is not None else state(source)
        del blocks[source]
        put(target, props, original.get('nbt'))

    modified = False
    for x, y, z in tuple(blocks):
        if state((x, y, z)).get('Name') != NS + 'vessel_stirrer':
            continue
        top = y + 1
        while state((x, top, z)).get('Name') == NS + 'lidded_vessel':
            top += 1
        if top - y <= 2:
            continue

        if path.stem == 'vessel_maintenance':
            for position in ((x, top, z), (x, top + 1, z)):
                del blocks[position]
        if path.stem == 'helium_recovery':
            pipe = String(NS + 'ceramic_pipe')
            properties = {direction: String('false') for direction in ('east', 'west', 'north', 'south', 'up', 'down', 'waterlogged')}
            def elbow(*sides):
                return Compound({'Name': pipe, 'Properties': Compound(properties | {side: String('true') for side in sides})})
            move((x + 1, top, z), (x + 2, top, z), elbow('west', 'down'))
            move((x, top, z), (x + 1, top, z), elbow('down', 'east'))
            turn = (x + 2, top - 1, z)
            move(turn, turn, elbow('up', 'east'))

        for position, expected in (((x, y, z - 1), 'create:cogwheel'), ((x, y - 1, z - 1), 'create:creative_motor')):
            if state(position).get('Name') != expected:
                raise ValueError(f'{path.name}: expected {expected} at {position}')
        move((x, y, z), (x, top, z))
        move((x, y, z - 1), (x, top, z - 1))
        motor = Compound({'Name': String('create:creative_motor'), 'Properties': Compound({'facing': String('down')})})
        move((x, y - 1, z - 1), (x, top + 1, z - 1), motor)
        casing = Compound({'Name': String('create:andesite_casing')})
        put((x, y, z), casing)
        put((x, y, z - 1), casing)
        if y == 1:
            put((x, 0, z - 1), Compound({'Name': String('minecraft:smooth_stone')}))
        structure['size'][1] = Int(max(int(structure['size'][1]), top + 2))
        modified = True

    if modified:
        structure['blocks'] = List[Compound](sorted(blocks.values(), key=lambda b: (b['pos'][1], b['pos'][0], b['pos'][2])))
        raw = BytesIO()
        structure.write(raw)
        compressed = bytearray(gzip.compress(raw.getvalue(), mtime=0))
        compressed[9] = 255
        path.write_bytes(compressed)
    return modified


if __name__ == '__main__':
    paths = sorted((ASSETS / 'plant').glob('*.nbt')) + sorted((ROOT / 'tools/data/manufacturing').glob('*.nbt'))
    print(f'Moved vessel drives in {sum(move_drives(path) for path in paths)} structures.')
