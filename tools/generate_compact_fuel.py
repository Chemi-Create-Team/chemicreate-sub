#!/usr/bin/env python3
"""Only the five-percent compact-reactor fuel branch; existing uranium assets are left intact."""
import json
from pathlib import Path
from PIL import Image
from generate_rbmk_assets import fuel_rod_item, shade
from generate_uranium_assets import icon
from powder_assets import write_powder_item

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors/recipe'
NS = 'create_refinery_n_reactors:'
NAMES = {
    'compact_enriched_fuel_rod': ('Compact Reactor Fuel Rod (5%)', '小型炉用高濃縮燃料棒（5%）', '小型反应堆燃料棒（5%）'),
    'compact_uranium_dioxide': ('Uranium Dioxide (5%)', '小型炉用二酸化ウラン（5%）', '二氧化铀（5%）'),
    'green_compact_uranium_pellet': ('Green Uranium Pellet (5%)', '未焼結小型炉用ウランペレット（5%）', '未烧结铀芯块（5%）'),
    'compact_uranium_pellet': ('Uranium Pellet (5%)', '小型炉用ウランペレット（5%）', '铀芯块（5%）'),
}
FLUID = ('Uranium Hexafluoride (5%)', '小型炉用六フッ化ウラン（5%）', '六氟化铀（5%）')


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def item(name): return {'id': NS + name, 'count': 1}
def fluid(name, amount): return {'type': 'neoforge:single', 'fluid': name if ':' in name else NS + name, 'amount': amount}
def output_fluid(name, amount): return {'id': name if ':' in name else NS + name, 'amount': amount}


def assets():
    for name in ('compact_uranium_dioxide',): write_powder_item(name)
    rows = {y: (4, 11) for y in range(4, 13)} | {2: (6, 9), 3: (5, 10), 13: (5, 10)}
    for name, colour in [('green_compact_uranium_pellet', (142, 134, 112)), ('compact_uranium_pellet', (83, 86, 88))]:
        im = icon(name, rows, lambda x, y: 22 if y < 5 else (-20, -6, 10, 18, 10, 0, -8, -24)[x - 4], colour,
                  speckle=[(lambda x, y: y in (5, 10) and 5 <= x <= 10, (177, 139, 69))])
        im.save(A / f'textures/item/{name}.png')
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            pixel = fuel_rod_item(x, y, False) if 3 <= x <= 12 else None
            if pixel is None: continue
            if x in (4, 10): pixel = shade((182, 139, 66), 15 if x - (15 - y) < 0 else -15)
            if x in (3, 12): pixel = shade((77, 86, 92), 0)
            im.putpixel((x, y), pixel)
    im.save(A / 'textures/item/compact_enriched_fuel_rod.png')
    for name in NAMES:
        if name in ('compact_uranium_dioxide',): continue
        write(A / f'models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})


def recipes():
    branches = {
        'compact_uranium_dioxide': ([fluid('compact_enriched_uranium_hexafluoride', 250), fluid('minecraft:water', 250), fluid('hydrogen_gas', 250)], [item('compact_uranium_dioxide'), output_fluid('hydrogen_fluoride', 750)], None),
        'compact_uranium_pellet': ([{'item': NS + 'green_compact_uranium_pellet'}], [item('compact_uranium_pellet')], 'heated'),
    }
    for name, (ingredients, results, heat) in branches.items():
        recipe = {'type': NS + 'pressure_reaction', 'ingredients': ingredients, 'results': results}
        if heat: recipe['heat_requirement'] = heat
        if name == 'compact_uranium_pellet': recipe['module'] = 'absorber'
        write(D / f'pressure_reaction/{name}.json', recipe)
    write(D / 'smelting/compact_uranium_pellet.json', {'type': 'minecraft:smelting', 'ingredient': {'item': NS + 'green_compact_uranium_pellet'}, 'result': item('compact_uranium_pellet'), 'experience': 0.1, 'cookingtime': 200})
    write(D / 'pressing/green_compact_uranium_pellet.json', {'type': 'create:pressing', 'ingredients': [{'item': NS + 'compact_uranium_dioxide'}], 'results': [{'id': NS + 'green_compact_uranium_pellet', 'count': 2}]})
    write(D / 'compact_enriched_fuel_rod.json', {'type': 'minecraft:crafting_shaped', 'pattern': ['PPP', 'SPS', 'PPP'],
        'key': {'P': {'item': NS + 'compact_uranium_pellet'}, 'S': {'item': NS + 'zircaloy_cladding_tube'}}, 'result': item('compact_enriched_fuel_rod')})


def translations():
    for locale, index in [('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)]:
        path = A / f'lang/{locale}.json'
        raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
        language = json.loads(raw)
        for name, names in NAMES.items(): language['item.' + NS[:-1] + '.' + name] = names[index]
        for prefix in ('fluid.', 'fluid_type.'):
            language[prefix + NS[:-1] + '.compact_enriched_uranium_hexafluoride'] = FLUID[index]
        language['process.' + NS[:-1] + '.recipe.enrich_compact_uranium'] = ('Further uranium enrichment (5%)', '小型炉用の追加濃縮（5%）', '铀的进一步浓缩（5%）')[index]
        path.write_bytes((json.dumps(language, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


if __name__ == '__main__':
    assets();recipes();translations()
