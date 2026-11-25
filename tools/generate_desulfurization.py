#!/usr/bin/env python3
"""Diesel hydrodesulfurization and the two-stage Claus process: catalyst icon and recipe, Claus reactions, fuel tags,
translations and the Ponder storyboard. Run from the repository root."""
import json, sys
from PIL import Image
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS, write
from generate_petrochemical_ponder import Structure, OUT
from generate_uranium_assets import plant, receiver, fluid, fluid_out, item

D = A.parent.parent / 'data'


def catalyst():
    """The cartridge of the other catalysts, filled with cobalt-molybdenum extrudates on alumina."""
    base = Image.open(A / 'textures/item/oxidation_catalyst.png').convert('RGBA')
    bed, pellet, shine = (34, 46, 66, 255), (62, 108, 158, 255), (118, 168, 206, 255)
    for y in range(3, 13):
        for x in range(4, 12):
            row, column = y - 3, x - 4
            on = (column + 2 * (row % 2)) % 4
            base.putpixel((x, y), bed if on > 1 else shine if on == 0 and row % 3 == 0 else pellet)
    base.save(A / 'textures/item/hydrotreating_catalyst.png')
    write(A / 'models/item/hydrotreating_catalyst.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/hydrotreating_catalyst'}})
    write(D / NS[:-1] / 'advancement/recipes/misc/hydrotreating_catalyst.json', {'parent': 'minecraft:recipes/root',
        'criteria': {'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': ['minecraft:lapis_lazuli']}]}},
                     'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': NS + 'hydrotreating_catalyst'}}},
        'requirements': [['has_material', 'has_the_recipe']], 'rewards': {'recipes': [NS + 'hydrotreating_catalyst']}})


def recipes():
    """Split-flow Claus: a third of the hydrogen sulfide burns to sulfur dioxide, which then takes the other two thirds to sulfur.
    One sulfur dust counts as 500 mB of a sulfur gas, as in the existing sulfur dioxide recipe."""
    r = D / NS[:-1] / 'recipe/pressure_reaction'
    write(r / 'sulfur_dioxide_from_hydrogen_sulfide.json', {'type': NS + 'pressure_reaction', 'heat_requirement': 'heated',
        'ingredients': [fluid(NS + 'hydrogen_sulfide', 500), fluid(NS + 'oxygen_gas', 750)],
        'results': [fluid_out(NS + 'sulfur_dioxide', 500), fluid_out('minecraft:water', 100)]})
    write(r / 'sulfur_from_hydrogen_sulfide.json', {'type': NS + 'pressure_reaction', 'heat_requirement': 'heated',
        'ingredients': [fluid(NS + 'hydrogen_sulfide', 1000), fluid(NS + 'sulfur_dioxide', 500)],
        'results': [item(NS + 'sulfur_dust', 3), fluid_out('minecraft:water', 200)]})
    diesel = [NS + 'low_sulfur_diesel', NS + 'flowing_low_sulfur_diesel']
    for tag in ('diesel', 'fuel'): write(D / 'c/tags/fluid' / f'{tag}.json', {'replace': False, 'values': diesel})




NAMES = {
    'fluid.create_refinery_n_reactors.cooled_hydrotreater_effluent': ('Cooled Hydrotreater Effluent', '冷却した脱硫反応液', '冷却的加氢脱硫产物'),
    'fluid.create_refinery_n_reactors.low_sulfur_diesel': ('Low-Sulfur Diesel', '低硫黄軽油', '低硫柴油'),
    'fluid.create_refinery_n_reactors.hydrogen_sulfide': ('Hydrogen Sulfide', '硫化水素', '硫化氢'),
    'item.create_refinery_n_reactors.hydrotreating_catalyst': ('Hydrotreating Catalyst', '水素化脱硫触媒', '加氢脱硫催化剂'),
}
JAPANESE_ENGLISH = {
    'process.create_refinery_n_reactors.recipe.hydrotreat_diesel': ('Catalytic hydrodesulfurization of diesel', '触媒による軽油の水素化脱硫'),
    'process.create_refinery_n_reactors.recipe.separate_hydrotreater_effluent': ('Low-sulfur diesel, hydrogen sulfide and recycle hydrogen', '低硫黄軽油・硫化水素・回収水素の分離'),
    'reactor.create_refinery_n_reactors.status.pressure': ('Raise the hydrogen to the required pressure', '水素を必要な圧力まで上げてください'),
    'jei.create_refinery_n_reactors.note.reactor_pressure': ('%s ticks at 32 RPM, one batch per section, with the gas at %s bar or more in the vessel. Each batch wears the catalyst; it lasts 4,000 batches.',
        '32 RPMで%s tick、1段につき1バッチ。反応槽内の気体は%s bar以上が必要。触媒は1バッチごとに劣化し、4,000バッチで使えなくなる'),
}




def translations():
    for lang, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        p = A / 'lang' / f'{lang}.json'
        raw = p.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
        strings = json.loads(raw)
        for key, names in NAMES.items(): strings[key] = names[index]
        if index < 2:
            for key, text in JAPANESE_ENGLISH.items(): strings[key] = text[index]
        p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


if __name__ == '__main__':
    catalyst(); recipes(); translations()
