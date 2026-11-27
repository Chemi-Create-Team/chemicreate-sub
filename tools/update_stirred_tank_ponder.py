#!/usr/bin/env python3
"""Turn every stainless tank that carries a roof stirrer in the checked-in Ponder structures into a stirred tank.

Only the stirred tank stirs, so a 3x3 vessel under a vessel_stirrer becomes stirred_tank blocks with the same states and
block entity data. Needs nbtlib. Run from the repository root."""
import gzip
from io import BytesIO
from pathlib import Path

from nbtlib import Compound, Int, List, String, load

ROOT = Path(__file__).resolve().parents[1]
PONDER = ROOT / 'src/main/resources/assets/create_refinery_n_reactors/ponder'
NS = 'create_refinery_n_reactors:'
VESSEL, STIRRED = NS + 'lidded_vessel', NS + 'stirred_tank'


def convert(path):
    structure = load(path)
    palette = structure['palette']
    blocks = {tuple(int(v) for v in block['pos']): block for block in structure['blocks']}
    name = lambda pos: str(palette[int(blocks[pos]['state'])]['Name']) if pos in blocks else ''
    cells = set()
    for (x, y, z) in blocks:
        if name((x, y, z)) != NS + 'vessel_stirrer' or name((x, y - 1, z)) != VESSEL: continue
        columns = [(x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)]
        if not all(name((cx, y - 1, cz)) == VESSEL for cx, cz in columns): continue
        for cx, cz in columns:
            cy = y - 1
            while name((cx, cy, cz)) == VESSEL:
                cells.add((cx, cy, cz)); cy -= 1
    for pos in cells:
        block = blocks[pos]
        state = Compound(palette[int(block['state'])])
        state['Name'] = String(STIRRED)
        if state not in palette: palette.append(state)
        block['state'] = Int(palette.index(state))
        if 'nbt' in block and 'id' in block['nbt']: block['nbt']['id'] = String(STIRRED)
    if cells:
        raw = BytesIO(); structure.write(raw)
        compressed = bytearray(gzip.compress(raw.getvalue(), mtime=0))
        compressed[9] = 255  # the same unknown-OS byte as the other structure tools
        path.write_bytes(compressed)
    return len(cells)


if __name__ == '__main__':
    for path in sorted(PONDER.rglob('*.nbt')) + sorted((ROOT / 'tools/data/manufacturing').glob('*.nbt')):
        count = convert(path)
        if count: print(f'{path.relative_to(ROOT)}: {count} cells')
