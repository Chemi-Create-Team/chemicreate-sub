#!/usr/bin/env python3
"""Staged thermal processing of aqueous solutions: evaporate (heated, ~100-150 C) -> solid hydrate/crystals remain,
dehydrate / partly decompose (heated, ~150-500 C) -> basic salt + gas, calcine (superheated, 700 C+) -> oxide + gas.
Writes the pressure_reaction recipes, the powder manifest entries and the three lang files; run after the other generators
(it replaces the one-step zirconia, titanium_dioxide, uranium_trioxide and plutonium_oxide_recovery recipes)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors:'
RECIPES = ROOT / 'src/main/resources/data/create_refinery_n_reactors/recipe/pressure_reaction'
LANG = ROOT / 'src/main/resources/assets/create_refinery_n_reactors/lang'
MANIFEST = ROOT / 'tools/powder_items.json'

# item: (colour, English, Japanese, Chinese)
ITEMS = {
    'zirconium_sulfate_hydrate': ((236, 232, 218), 'Zirconium Sulfate Hydrate', '硫酸ジルコニウム水和物', '硫酸锆水合物'),
    'zirconyl_sulfate': ((226, 222, 208), 'Zirconyl Sulfate', '硫酸ジルコニル', '硫酸氧锆'),
    'titanyl_sulfate_hydrate': ((214, 214, 160), 'Titanyl Sulfate Hydrate', '硫酸チタニル水和物', '硫酸氧钛水合物'),
    'aluminium_sulfate_hydrate': ((236, 238, 240), 'Aluminium Sulfate Hydrate', '硫酸アルミニウム水和物', '硫酸铝水合物'),
    'iron_sulfate_hydrate': ((150, 190, 130), 'Iron Sulfate Hydrate', '硫酸鉄水和物', '硫酸亚铁水合物'),
    'copper_sulfate_crystals': ((52, 120, 214), 'Copper Sulfate Crystals', '硫酸銅結晶', '硫酸铜晶体'),
    'zinc_sulfate_hydrate': ((226, 232, 228), 'Zinc Sulfate Hydrate', '硫酸亜鉛水和物', '硫酸锌水合物'),
    'tin_sulfate_crystals': ((206, 214, 214), 'Tin Sulfate Crystals', '硫酸スズ結晶', '硫酸亚锡晶体'),
    'lead_chloride_crystals': ((232, 228, 214), 'Lead Chloride Crystals', '塩化鉛結晶', '氯化铅晶体'),
    'uranyl_nitrate_hydrate': ((206, 204, 80), 'Uranyl Nitrate Hydrate', '硝酸ウラニル水和物', '硝酸铀酰水合物'),
    'plutonium_nitrate_crystals': ((150, 126, 92), 'Plutonium Nitrate Crystals', '硝酸プルトニウム結晶', '硝酸钚晶体'),
    'sodium_aluminate_crystals': ((232, 232, 226), 'Sodium Aluminate Crystals', 'アルミン酸ナトリウム結晶', '铝酸钠晶体'),
    'caustic_soda_flakes': ((240, 240, 244), 'Caustic Soda Flakes', '苛性ソーダフレーク', '烧碱片'),
    'ammonium_chloride_crystals': ((238, 236, 226), 'Ammonium Chloride Crystals', '塩化アンモニウム結晶', '氯化铵晶体'),
}
REASON = 'Solid left when its solution is boiled down; heated further it dehydrates and decomposes.'

# solution: (mB per batch, solid left behind, steam mB). Only solutions with a non-volatile solute leave a solid; acids,
# organics, hypochlorite and the radioactive wastes are left alone.
EVAPORATION = {
    'zirconium_sulfate_solution': (500, 'zirconium_sulfate_hydrate', 400),
    'titanyl_sulfate_solution': (500, 'titanyl_sulfate_hydrate', 400),
    'aluminium_sulfate_solution': (500, 'aluminium_sulfate_hydrate', 400),
    'iron_sulfate_solution': (500, 'iron_sulfate_hydrate', 400),
    'copper_sulfate_solution': (500, 'copper_sulfate_crystals', 400),
    'zinc_sulfate_solution': (500, 'zinc_sulfate_hydrate', 400),
    'tin_sulfate_solution': (500, 'tin_sulfate_crystals', 400),
    'lead_chloride_solution': (500, 'lead_chloride_crystals', 400),
    'uranyl_nitrate_solution': (250, 'uranyl_nitrate_hydrate', 200),
    'plutonium_nitrate_solution': (100, 'plutonium_nitrate_crystals', 80),
    'sodium_aluminate_solution': (500, 'sodium_aluminate_crystals', 400),
    'caustic_soda_solution': (500, 'caustic_soda_flakes', 400),
    'ammonium_chloride_liquor': (500, 'ammonium_chloride_crystals', 400),
    'brine': (250, 'salt', 200),
    'wastewater': (1000, 'treatment_sludge', 800),
    'neutralized_wastewater': (1000, 'treatment_sludge', 800),
}


def fluid(name, amount):
    return {'type': 'neoforge:single', 'amount': amount, 'fluid': NS + name}


def item(name):
    return {'item': NS + name}


def out_item(name, count=1):
    return {'id': name if ':' in name else NS + name, **({'count': count} if count > 1 else {})}


def out_fluid(name, amount):
    return {'id': NS + name, 'amount': amount}


def write(name, heat, ingredients, results):
    recipe = {'type': NS + 'pressure_reaction', 'heat_requirement': heat, 'ingredients': ingredients, 'results': results}
    (RECIPES / (name + '.json')).write_text(json.dumps(recipe, indent=2) + '\n')


def recipes():
    for solution, (amount, solid, steam) in EVAPORATION.items():
        write(solution.removesuffix('_solution').removesuffix('_liquor') + '_evaporation', 'heated',
              [fluid(solution, amount)], [out_item(solid), out_fluid('steam', steam)])
    # zirconium sulfate: hydrate -> zirconyl sulfate (+ SO3) -> zirconia (+ SO3)
    write('zirconium_sulfate_dehydration', 'heated', [item('zirconium_sulfate_hydrate')],
          [out_item('zirconyl_sulfate'), out_fluid('sulfur_trioxide', 250), out_fluid('steam', 200)])
    write('zirconia', 'superheated', [item('zirconyl_sulfate')], [out_item('zirconia'), out_fluid('sulfur_trioxide', 250)])
    write('titanium_dioxide', 'superheated', [item('titanyl_sulfate_hydrate')], [out_item('titanium_dioxide'), out_fluid('sulfur_trioxide', 500)])
    write('alumina_from_aluminium_sulfate', 'superheated', [item('aluminium_sulfate_hydrate')], [out_item('alumina'), out_fluid('sulfur_trioxide', 500)])
    write('iron_oxide_red_from_iron_sulfate', 'superheated', [item('iron_sulfate_hydrate'), item('iron_sulfate_hydrate')],
          [out_item('iron_oxide_red'), out_fluid('sulfur_dioxide', 250), out_fluid('sulfur_trioxide', 250)])
    write('uranium_trioxide', 'heated', [item('uranyl_nitrate_hydrate')], [out_item('uranium_trioxide'), out_fluid('nitrogen_dioxide', 100)])
    recipe = {'type': NS + 'pressure_reaction', 'ingredients': [item('plutonium_nitrate_crystals')],
              'results': [out_item('plutonium_dioxide'), out_fluid('nitrogen_dioxide', 100), out_fluid('oxygen_gas', 25)],
              'processing_time': 300, 'heat_requirement': 'heated'}
    (RECIPES / 'plutonium_oxide_recovery.json').write_text(json.dumps(recipe, indent=2) + '\n')


def manifest():
    data = json.loads(MANIFEST.read_text())
    for name, (colour, *_) in ITEMS.items():
        data['powders'][name] = {'colour': list(colour), 'reason': REASON}
    MANIFEST.write_text(json.dumps(data, indent=2) + '\n')


def lang():
    for index, code in enumerate(('en_us', 'ja_jp', 'zh_cn'), start=1):
        path = LANG / (code + '.json')
        data = json.loads(path.read_text(encoding='utf-8'))
        for name, info in ITEMS.items():
            data['item.' + NS.rstrip(':') + '.' + name] = info[index]
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


if __name__ == '__main__':
    recipes(); manifest(); lang()
    print(len(EVAPORATION), 'evaporation recipes,', len(ITEMS), 'items')
