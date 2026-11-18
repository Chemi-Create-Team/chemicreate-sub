#!/usr/bin/env python3
"""Regeneration recipes and translations for finite catalyst cartridges.
Run from the repository root."""
import json, sys
from PIL import Image
from powder_assets import write_powder_item
sys.path.insert(0, 'tools')
from minecraft_style import grain
from generate_reactor_assets import A, D, NS, write

# Catalyst, then what brings a spent one back: promoters and metal lost from the surface.
REGENERATION = {
    'iron_catalyst': [{'item': 'minecraft:iron_nugget'}] * 3,
    'oxidation_catalyst': [{'item': 'minecraft:gold_nugget'}] * 3,
    'cracking_catalyst': [{'tag': 'c:ingots/aluminum'}],
    'polymerization_catalyst': [{'tag': 'c:ingots/titanium'}],
    'hydrotreating_catalyst': [{'tag': 'c:ingots/molybdenum'}],
    'hydrocracking_catalyst': [{'tag': 'c:ingots/nickel'}],
}
def recipes():
    for catalyst, extra in REGENERATION.items():
        ingredients = [{'item': NS + catalyst}] + extra
        write(D / f'recipe/{catalyst}_regeneration.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ingredients,
                                                          'result': {'id': NS + catalyst, 'count': 1}})


TEXTS = {
    'jei.create_refinery_n_reactors.note.reactor': ("%s ticks in the reaction tower, one batch per layer with a basket. Each batch wears that layer's catalyst; it lasts 4,000 batches.",
                                                '反応塔で%s tick、バスケットのある段ごとに1バッチ。触媒は1バッチごとにその段で劣化し、4,000バッチで使えなくなる', '反应塔中每批耗时 %s tick，每个装篮的层各处理1批。每批消耗该层催化剂寿命，可使用 4,000 批。'),
}

# Left from the catalyst-basket vessel, whose catalytic work moved to the reaction tower.
RETIRED = ('reactor.create_refinery_n_reactors.status.catalyst', 'reactor.create_refinery_n_reactors.catalyst_life', 'reactor.create_refinery_n_reactors.catalyst', 'reactor.create_refinery_n_reactors.none')


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; strings = json.loads(raw)
        for key, values in TEXTS.items():
            if values[index] is not None: strings[key] = values[index]
        for key in RETIRED: strings.pop(key, None)
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


if __name__ == '__main__':
    recipes(); translations()
    print('Generated catalyst regeneration recipes and translations')
