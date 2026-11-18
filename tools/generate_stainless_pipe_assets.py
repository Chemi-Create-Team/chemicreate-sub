#!/usr/bin/env python3
"""Stainless steel pipe: Create's fluid, glass and encased pipe with the copper recoloured to stainless steel.

Geometry, blockstates and connection logic are Create's own: the blockstates point at Create's
pipe models, and StainlessPipeModels swaps Create's four pipe sprites for the stainless ones drawn
here. Each sprite keeps Create's pixels; copper shades are mapped by brightness onto a light,
cool grey ramp, and the dark red bore becomes a dark grey bore.
Create's textures are MIT licensed.
"""
from pathlib import Path
import io, json, zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
STAINLESS = [(98, 104, 112), (112, 119, 128), (130, 137, 147), (146, 153, 163), (165, 173, 183), (186, 194, 203), (215, 222, 230), (241, 246, 250)]
BORE = [(24, 25, 27), (35, 35, 37)]
SPRITES = ('pipes', 'pipes_connected', 'glass_fluid_pipe', 'encased_pipe')


def create_jar():
    jars = sorted(Path.home().glob('.gradle/caches/modules-2/files-2.1/com.simibubi.create/create-1.21.1/6.0.10-281/*/create-1.21.1-6.0.10-281.jar'))
    if not jars:
        raise SystemExit('Create 6.0.10-281 jar not found; run a Gradle build first')
    return zipfile.ZipFile(jars[0])


def luminance(c):
    return c[0] * 299 + c[1] * 587 + c[2] * 114


def copper(c):
    return c[0] - c[2] > 40 and c[0] >= 120


def bore(c):
    return c[0] - c[2] > 20 and c[0] < 120


def recolour(im):
    """Copper shades keep their brightness order on the stainless ramp; glass, fluid window and black stay as Create drew them."""
    pixels = [im.getpixel((x, y)) for y in range(im.height) for x in range(im.width) if im.getpixel((x, y))[3]]
    shades = sorted({p[:3] for p in pixels if copper(p)}, key=luminance)
    ramp = {c: STAINLESS[round(i * (len(STAINLESS) - 1) / max(1, len(shades) - 1))] for i, c in enumerate(shades)}
    reds = sorted({p[:3] for p in pixels if bore(p)}, key=luminance)
    dark = {c: BORE[min(i, len(BORE) - 1)] for i, c in enumerate(reds)}
    out = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            p = im.getpixel((x, y))
            c = ramp.get(p[:3]) or dark.get(p[:3])
            if p[3] and c:
                out.putpixel((x, y), c + (p[3],))
    return out


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text((json.dumps(data, ensure_ascii=False, indent=2) + '\n').replace('\n', '\r\n'), encoding='utf-8', newline='')


def loot(name, drop):
    write(D / f'loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
          'entries': [{'type': 'minecraft:item', 'name': NS + drop}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


if __name__ == '__main__':
    jar = create_jar()
    for name in SPRITES:
        image = Image.open(io.BytesIO(jar.read(f'assets/create/textures/block/{name}.png'))).convert('RGBA')
        recolour(image).save(A / f'textures/block/transport/stainless_steel_{name}.png')
    write(A / 'blockstates/stainless_steel_pipe.json', json.loads(jar.read('assets/create/blockstates/fluid_pipe.json')))
    write(A / 'blockstates/stainless_steel_encased_pipe.json', json.loads(jar.read('assets/create/blockstates/encased_fluid_pipe.json')))
    write(A / 'blockstates/stainless_steel_glass_pipe.json', json.loads(jar.read('assets/create/blockstates/glass_fluid_pipe.json')))
    write(A / 'models/item/stainless_steel_pipe.json', {'parent': 'create:block/fluid_pipe/item',
          'textures': {'1': NS + 'block/transport/stainless_steel_pipes', 'particle': NS + 'block/transport/stainless_steel_pipes'}})
    # Create's horizontal fluid pipe recipe with stainless steel in place of copper. The vertical one would take the grid of Create's chute.
    write(D / 'recipe/stainless_steel_pipe.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'key': {'C': {'tag': 'c:ingots/stainless_steel'}, 'S': {'tag': 'c:plates/stainless_steel'}},
          'pattern': ['SCS'], 'result': {'count': 4, 'id': NS + 'stainless_steel_pipe'}})
    loot('stainless_steel_pipe', 'stainless_steel_pipe')
    loot('stainless_steel_encased_pipe', 'stainless_steel_pipe')
    loot('stainless_steel_glass_pipe', 'stainless_steel_pipe')
    print('Generated stainless steel pipe textures, blockstates, models, recipes and loot tables')
