#!/usr/bin/env python3
"""Generate the stainless tank's stirrer: Create's mechanical mixer housing in stainless steel, whose gear round its middle meshes with cogwheels.
Run from the repository root after a Gradle build, which resolves the Create jar the textures are taken from."""
from pathlib import Path
import json
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_plant_tank_assets import A, D, NS, write
from electrolysis_hardware import create_texture

NAME = 'vessel_stirrer'
K = 'create_refinery_n_reactors'
NAMES = {f'block.{K}.{NAME}': ('Tank Stirrer', '撹拌機', '搅拌器')}


def steel(name):
    """A copy of a Create texture with its wood and brass recoloured in the stainless tank's cool steel, keeping the shading."""
    image = create_texture(name, 'RGBA')
    px = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            grey = .3 * r + .59 * g + .11 * b
            # Brighter and slightly blue, as the shell of the stainless tank above it.
            level = min(255, round(grey * 1.12 + 18))
            px[x, y] = (round(level * .94), round(level * .97), min(255, level + 6), a)
    out = A / f'textures/block/{NAME}/{name}.png'
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out)
    return NS + f'block/{NAME}/{name}'


def create_model(name):
    path = f'assets/create/models/block/{name}.json'
    for jar in sorted(Path.home().glob('.gradle/caches/modules-2/files-2.1/maven.modrinth/create/*/*/*.jar')):
        with zipfile.ZipFile(jar) as source:
            if path in source.namelist(): return json.loads(source.read(path))
    raise SystemExit('Create jar not found; run a Gradle build first')


def models():
    side, top = steel('mixer_base_side'), steel('mechanical_press_top')
    housing = create_model('mechanical_mixer/block')
    # The same housing as the mixer's, so the gear shows through the same gap round its middle.
    write(A / f'models/block/{NAME}.json', {'parent': 'create:block/mechanical_mixer/block',
                                          'textures': {'2': NS + 'block/reactor/steel', '4': side, '11': top, 'particle': side}})
    assert set(housing['textures']) >= {'2', '4', '11'}
    write(A / f'blockstates/{NAME}.json', {'variants': {'': {'model': NS + f'block/{NAME}'}}})
    write(A / f'models/item/{NAME}.json', {'parent': NS + f'block/{NAME}'})
    write(D / f'loot_table/blocks/{NAME}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + NAME}],
          'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    tags = D.parent / 'minecraft/tags/block/mineable/pickaxe.json'
    tag = json.loads(tags.read_text())
    if NS + NAME not in tag['values']: tag['values'].append(NS + NAME); write(tags, tag)


def structure():
    """The Ponder scene's tank: two layers over the stirrer, a cogwheel beside it on a creative motor in the floor, and burners at the corners."""
    import generate_petrochemical_ponder as ponder
    ponder.OUT = A / 'ponder/plant'
    s = ponder.Structure(9, 4)
    s.reactor(4, 2, 4, 'stirred', 2, burners=((-1, -1), (1, -1), (-1, 1), (1, 1)), windows=True)
    s.save('vessel_internals')


def names():
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / f'{language}.json'
        strings = json.loads(path.read_text())
        for key, text in NAMES.items(): strings[key] = text[index]
        path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    models(); structure(); names()
    print('Generated the tank stirrer')
