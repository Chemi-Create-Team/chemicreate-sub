#!/usr/bin/env python3
"""Build the small diorite casing workshop and its English/Japanese Ponder captions."""
import json
from pathlib import Path
from generate_petrochemical_ponder import Structure, OUT, integer, named, string

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'

scene = Structure(7, 6)
scene.block(2, 1, 1, 'minecraft:diorite')
for x in range(1, 6):
    for y in range(1, 4):
        scene.block(x, y, 5, NS + 'diorite_casing')
for y in range(1, 4):
    for z in (3, 4):
        scene.block(1, y, z, NS + 'diorite_casing')
    for x in (2, 5):
        scene.block(x, y, 4, NS + 'diorite_casing')
for x in range(2, 6):
    scene.block(x, 4, 4, NS + 'diorite_casing')
for x in range(3, 6):
    for z in (2, 3):
        scene.block(x, 1, z, NS + 'diorite_casing')
for y in (2, 3):
    nbt = [string('id', 'create:fluid_tank')]
    nbt += [named(3, 'Size', integer(1)), named(3, 'Height', integer(2)), named(3, 'Window', integer(1))] if y == 2 else [named(11, 'Controller', b''.join(integer(v) for v in (3, 4, 2, 3)))]
    scene.block(4, y, 3, 'create:fluid_tank', nbt=nbt, bottom=str(y == 2).lower(), top=str(y == 3).lower(), shape='window')
scene.save('diorite_casing')
destination = ASSETS / 'ponder/plant/diorite_casing.nbt'
destination.write_bytes((OUT / 'diorite_casing.nbt').read_bytes())
(OUT / 'diorite_casing.nbt').unlink()

TEXTS = {
    'title': ('Building with Diorite Casing', '閃緑岩ケーシングで白い工場を組む'),
    'make': ('Right-click one diorite block with one andesite alloy to make one Diorite Casing. The pale stone panel and its metal frame provide a bright building material for the factory.',
        '閃緑岩1個へ安山岩合金1個を右クリックすると、閃緑岩ケーシング1個になります。白い石のパネルと金属枠で、明るい工場の部材を作れます。'),
    'walls': ('Place the casings next to each other to connect their panels into walls. Use the continuous pale surface behind equipment and around the work area.',
        'ケーシングを隣同士に置くと、パネルがつながった壁になります。機械の背面や作業場所の周囲に、連続した白い面を作れます。'),
    'supports': ('Stack the casing into posts, join the top with a beam and build a plinth underneath equipment. Keep aisles and the working faces of machines accessible.',
        '縦に積んで柱を作り、上を梁でつなぎ、機械の下には台座を組みます。通路や機械の操作面へ届く空間を残してください。'),
    'machines': ('Use casing for walls, posts and bases. Keep ports and controls accessible.',
        'ケーシングは壁・柱・台座に使えます。配管口や操作面はふさがないようにします。'),
}
for language, index in (('en_us', 0), ('ja_jp', 1)):
    path = ASSETS / 'lang' / (language + '.json')
    raw = path.read_bytes().decode('utf-8')
    newline = '\r\n' if '\r\n' in raw else '\n'
    values = json.loads(raw)
    values['block.create_refinery_n_reactors.diorite_casing'] = ('Diorite Casing', '閃緑岩ケーシング')[index]
    for key, pair in TEXTS.items():
        values['create_refinery_n_reactors.ponder.shared.diorite_casing.' + key] = pair[index]
    values['create_refinery_n_reactors.ponder.diorite_casing.header'] = TEXTS['title'][index]
    path.write_bytes((json.dumps(values, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
