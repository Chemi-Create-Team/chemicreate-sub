#!/usr/bin/env python3
"""The fusion irradiation cartridge: two item textures recoloured from the lithium-lead blanket cartridge, models, the crafting and
dissolving recipes, and translations. Run from the repository root."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'atomics/src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'atomics/src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
NAMES = {
    'irradiation_cartridge': ('Irradiation Cartridge', '照射カートリッジ', '辐照组件盒'),
    'irradiated_cartridge': ('Irradiated Cartridge', '照射済みカートリッジ', '已辐照组件盒'),
}
# The blanket cartridge's green lithium-lead core and red mark take gold and titanium; once irradiated the core darkens.
CORE = {'irradiation_cartridge': ((214, 172, 62), (150, 162, 176)), 'irradiated_cartridge': ((86, 64, 72), (120, 196, 236))}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def textures():
    source = Image.open(A / 'textures/item/lithium_lead_blanket_cartridge.png').convert('RGBA')
    for name, (core, mark) in CORE.items():
        image = source.copy()
        for x in range(16):
            for y in range(16):
                r, g, b, alpha = image.getpixel((x, y))
                if not alpha: continue
                if g > r and g > b + 15:
                    shade = (r + g + b) / (3 * 101)
                    image.putpixel((x, y), tuple(min(255, round(c * shade)) for c in core) + (alpha,))
                elif r > g + 60:
                    image.putpixel((x, y), mark + (alpha,))
        image.save(A / f'textures/item/{name}.png')
        write(A / f'models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})


def recipes():
    write(D / 'recipe/irradiation_cartridge.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['TGT', 'GSG', 'TGT'],
        'key': {'T': {'item': NS + 'titanium_sheet'}, 'G': {'item': 'minecraft:gold_ingot'}, 'S': {'item': NS + 'spent_blanket_cartridge'}},
        'result': {'id': NS + 'irradiation_cartridge', 'count': 1}})
    # Dissolving the irradiated filling frees the scrap and the housing; the activated solution goes to vitrification.
    write(D / 'recipe/pressure_reaction/irradiated_cartridge.json', {'type': NS + 'pressure_reaction', 'heat_requirement': 'heated',
        'ingredients': [{'item': NS + 'irradiated_cartridge'}, {'type': 'neoforge:single', 'amount': 1000, 'fluid': NS + 'hydrochloric_acid'}],
        'results': [{'id': 'minecraft:netherite_scrap', 'count': 8}, {'id': NS + 'spent_blanket_cartridge'}, {'amount': 250, 'id': NS + 'high_level_raffinate'}]})


def translations():
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / f'{language}.json'
        strings = json.loads(path.read_text())
        for name, values in NAMES.items(): strings['item.create_refinery_n_reactors.' + name] = values[index]
        path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    textures(); recipes(); translations()
    print('Generated the irradiation cartridge')
