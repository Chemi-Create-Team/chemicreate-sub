#!/usr/bin/env python3
"""Water-treatment recipes, new item art, one Ponder structure and integration fragments."""
import json
from pathlib import Path
from powder_assets import pixels_for_colour, png_bytes
from generate_world_liquids import bucket_image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
RECIPES = ROOT / 'src/main/resources/data/create_refinery_n_reactors/recipe'
FRAGMENTS = ROOT / 'tools/data'
NS = 'create_refinery_n_reactors:'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def item(name):
    return {'item': name if ':' in name else NS + name}


def fluid(name, amount=1000):
    return {'type': 'neoforge:single', 'fluid': name if ':' in name else NS + name, 'amount': amount}


def reaction(name, feed, reagent, product, ticks, solids=()):
    write(RECIPES / 'pressure_reaction' / (name + '.json'), {
        'type': NS + 'pressure_reaction', 'ingredients': [fluid(feed), item(reagent)],
        'results': [{'id': product if ':' in product else NS + product, 'amount': 1000}] + list(solids),
        'processing_time': ticks})






def main():
    reaction('washwater_neutralization', 'wastewater', 'minecraft:calcite', 'neutralized_wastewater', 80)
    reaction('washwater_precipitation', 'neutralized_wastewater', 'water_treatment_flocculant', 'clarified_water', 160)
    reaction('washwater_filtration', 'clarified_water', 'water_filter_medium', 'minecraft:water', 80,
        ({'id': NS + 'treatment_sludge', 'count': 3},))
    write(RECIPES / 'mixing/water_treatment_flocculant.json', {'type': 'create:mixing',
        'ingredients': [item('iron_oxide_red'), item('alumina'), item('minecraft:clay_ball')],
        'results': [{'id': NS + 'water_treatment_flocculant', 'count': 4}], 'processing_time': 80})
    write(RECIPES / 'water_filter_medium.json', {'type': 'minecraft:crafting_shapeless',
        'ingredients': [{'tag': 'minecraft:sand'}, item('minecraft:charcoal'), {'tag': 'minecraft:wool'}],
        'result': {'id': NS + 'water_filter_medium', 'count': 4}})
    powders = {'water_treatment_flocculant': {'colour': [206, 201, 175], 'reason': 'Loose iron/alumina/clay treatment reagent powder.'},
        'treatment_sludge': {'colour': [92, 80, 57], 'reason': 'Recovered ordinary wash-water residue powder.'}}
    for name, spec in powders.items():
        path = ASSETS / 'textures/item' / (name + '.png')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(png_bytes(pixels_for_colour(spec['colour'])))
    pad = []
    for y in range(16):
        for x in range(16):
            colour = (0, 0, 0, 0)
            if 2 <= x <= 13 and 4 <= y <= 12:
                colour = (65, 66, 58, 255) if x in (2, 13) or y in (4, 12) else (191, 183, 153, 255)
                if x not in (2, 13) and y not in (4, 12) and (x + y) % 3 == 0:
                    colour = (119, 119, 100, 255)
                if y == 5: colour = (231, 225, 200, 255)
            pad.append(colour)
    (ASSETS / 'textures/item/water_filter_medium.png').write_bytes(png_bytes(pad))
    for name in (*powders, 'water_filter_medium'):
        write(ASSETS / 'models/item' / (name + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})
    for name, colour in [('neutralized_wastewater', 0xFF929981), ('clarified_water', 0xBB9FB9B6)]:
        bucket_image(name + '_bucket', colour)
        write(ASSETS / 'blockstates' / (name + '.json'), {'variants': {'': {'model': 'minecraft:block/water'}}})
    names = {'water_treatment_flocculant': ('Water-treatment Flocculant', '水処理用凝集剤'),
        'water_filter_medium': ('Water Filter Medium', '水処理ろ材'), 'treatment_sludge': ('Treatment Sludge', '水処理汚泥'),
        'neutralized_wastewater': ('Neutralized Wash Water', '中和処理水'), 'clarified_water': ('Clarified Water', '澄明水')}
    languages = {locale: {} for locale in ('en_us', 'ja_jp')}
    for locale, index in [('en_us', 0), ('ja_jp', 1)]:
        target = languages[locale]
        for name, values in names.items():
            if name in ('neutralized_wastewater', 'clarified_water'):
                target['fluid.create_refinery_n_reactors.' + name] = values[index]
                target['block.create_refinery_n_reactors.' + name] = values[index]
                target['item.create_refinery_n_reactors.' + name + '_bucket'] = values[index] + (' Bucket' if index == 0 else '入りバケツ')
            else: target['item.create_refinery_n_reactors.' + name] = values[index]
    write(FRAGMENTS / 'water_treatment_lang.json', languages)
    write(FRAGMENTS / 'water_treatment_powders.json', {'powders': powders, 'preserved_items': {'water_filter_medium': 'Manufactured filter pad; not loose powder.'}})
    write(FRAGMENTS / 'water_treatment_integration.json', {'register': 'CEWaterTreatment.register(modEventBus);',
        'register_before': 'CEWorldFluids.register(modEventBus);', 'creative_materials': ['CEWaterTreatment.FLOCCULANT', 'CEWaterTreatment.FILTER_MEDIUM', 'CEWaterTreatment.SLUDGE']})
    print('Generated water treatment recipes/new assets/Ponder and integration fragments; existing art and language files are untouched.')


if __name__ == '__main__':
    main()
