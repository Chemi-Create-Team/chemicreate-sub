"""Create 6.0.10 OMNIDIRECTIONAL atlas masks; no source texture pixels are used."""
# CT indexing adapted from Create; MIT notice in licenses/Create-MIT.txt.
import itertools
import json
from pathlib import Path


SIDES = ('up', 'down', 'left', 'right')
CORNERS = {
    'top_left': ('up', 'left'),
    'top_right': ('up', 'right'),
    'bottom_left': ('down', 'left'),
    'bottom_right': ('down', 'right'),
}
SOURCE = ('https://github.com/Creators-of-Create/Create/blob/'
          'ac0c444d9828da3453ae8cc65338e8de063286fb/src/main/java/'
          'com/simibubi/create/foundation/block/connected/AllCTTypes.java')


def omnidirectional_index(context):
    up, down, left, right = (context[side] for side in SIDES)
    tl, tr, bl, br = (context[corner] for corner in CORNERS)
    x = int(up) + 2 * int(down)
    y = int(left) + 2 * int(right)
    borders = sum(not context[side] for side in SIDES)
    if borders == 0:
        x += int(tr) + 2 * int(tl)
        y += 2 * int(br) + int(bl)
    if borders == 1:
        if not right and (tl or bl):
            y, x = 4, -1 + int(bl) + 2 * int(tl)
        if not left and (tr or br):
            y, x = 5, -1 + int(br) + 2 * int(tr)
        if not down and (tl or tr):
            y, x = 6, -1 + int(tl) + 2 * int(tr)
        if not up and (bl or br):
            y, x = 7, -1 + int(bl) + 2 * int(br)
    if borders == 2:
        if (up and left and tl) or (down and left and bl) or (up and right and tr) or (down and right and br):
            x += 3
    return x + 8 * y


def tile_masks():
    tiles = [None] * 64
    names = SIDES + tuple(CORNERS)
    for bits in itertools.product((False, True), repeat=len(names)):
        context = dict(zip(names, bits))
        index = omnidirectional_index(context)
        mask = {
            'outer_frame': [side for side in SIDES if not context[side]],
            'inner_corners': [corner for corner, (a, b) in CORNERS.items()
                              if context[a] and context[b] and not context[corner]],
        }
        assert 0 <= index < len(tiles), (context, index)
        assert tiles[index] is None or tiles[index] == mask, (context, index, tiles[index], mask)
        tiles[index] = mask
    assert sum(tile is not None for tile in tiles) == 47
    return tiles


if __name__ == '__main__':
    masks = tile_masks()
    output = {
        'source': SOURCE,
        'create_version': '6.0.10',
        'sheet_size': 8,
        'tile_size': 16,
        'coordinates': 'Sides and corners are in the face texture plane; up is image top.',
        'unused_tiles': 'Unused slots are never selected. Fill them with the standalone tile if desired.',
        'tiles': [{'index': i, 'x': i % 8, 'y': i // 8, 'used': mask is not None,
                   **(mask if mask is not None else {})} for i, mask in enumerate(masks)],
    }
    path = Path(__file__).with_name('diorite_casing_ct_masks.json')
    path.write_text(json.dumps(output, indent=2) + '\n')
    print(f'{path.name}: 256 contexts, 47 distinct tiles, no conflicting masks')
