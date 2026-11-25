#!/usr/bin/env python3
"""Heavy oil hydrocracking and the contact process for sulfuric acid: catalyst icon and recipe, translations and two Ponder
storyboards. Run from the repository root."""
import json, sys
from PIL import Image
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS, write

D = A.parent.parent / 'data'


def catalyst():
    """The catalyst cartridge filled with nickel-molybdenum on pale zeolite extrudates."""
    base = Image.open(A / 'textures/item/oxidation_catalyst.png').convert('RGBA')
    bed, pellet, shine = (58, 66, 60, 255), (168, 182, 162, 255), (214, 226, 204, 255)
    for y in range(3, 13):
        for x in range(4, 12):
            row, column = y - 3, x - 4
            on = (column + row) % 3
            base.putpixel((x, y), bed if on == 2 else shine if on == 0 and row % 2 == 0 else pellet)
    base.save(A / 'textures/item/hydrocracking_catalyst.png')
    write(A / 'models/item/hydrocracking_catalyst.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/hydrocracking_catalyst'}})
    write(D / NS[:-1] / 'advancement/recipes/misc/hydrocracking_catalyst.json', {'parent': 'minecraft:recipes/root',
        'criteria': {'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': [NS + 'hydrotreating_catalyst']}]}},
                     'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': NS + 'hydrocracking_catalyst'}}},
        'requirements': [['has_material', 'has_the_recipe']], 'rewards': {'recipes': [NS + 'hydrocracking_catalyst']}})








NAMES = {
    'fluid.create_refinery_n_reactors.cooled_hydrocracker_effluent': ('Cooled Hydrocracker Effluent', '冷却した分解反応液', '冷却的加氢裂化产物'),
    'item.create_refinery_n_reactors.hydrocracking_catalyst': ('Hydrocracking Catalyst', '水素化分解触媒', '加氢裂化催化剂'),
}
PROCESSES = {
    'process.create_refinery_n_reactors.recipe.hydrocrack_heavy_oil': ('Catalytic hydrocracking of heavy oil', '触媒による重油の水素化分解'),
    'process.create_refinery_n_reactors.recipe.separate_hydrocracker_effluent': ('Diesel, naphtha, hydrogen sulfide and recycle hydrogen', '軽油・ナフサ・硫化水素・回収水素の分離'),
}




def translations():
    for lang, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        p = A / 'lang' / f'{lang}.json'
        raw = p.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
        strings = json.loads(raw)
        for key, names in NAMES.items(): strings[key] = names[index]
        if index < 2:
            for key, text in PROCESSES.items(): strings[key] = text[index]
        p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


if __name__ == '__main__':
    catalyst(); translations()
