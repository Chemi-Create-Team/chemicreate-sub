#!/usr/bin/env python3
"""Two more routes to sulfuric acid: sulfur dioxide oxidised in water by an iron catalyst, and sulfurous acid split in the electrolysis cell.
Sulfur also burns in air, as in a real sulfur burner, so the cold route needs no electrolysis oxygen.

The first runs in a lidded vessel with an impeller: iron dissolved in the water carries oxygen to the dissolved sulfur dioxide, 2 SO2 + O2 + 2 H2O ->
2 H2SO4. The iron stays in the acid as iron sulfate, so a nugget or a pinch of iron oxide goes in with every batch. Air works as well as oxygen;
its nitrogen bubbles out. The second is the hybrid sulfur cycle: the absorption tower makes sulfurous acid, which the cell splits into hydrogen and
sulfuric acid (its absorption and cell recipes are in ProcessRecipe and ElectrolysisProcess). Run from the repository root.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
# One batch: as much acid as the dilute acid mixed from 250 mB of sulfuric acid; air carries the oxygen at 21 percent.
WATER, SULFUR_DIOXIDE, OXYGEN, AIR = 1000, 250, 125, 600
# Burning one sulfur dust takes 500 mB of oxygen, as the oxygen recipe; air carries it at 21 percent.
BURNT_SULFUR_DIOXIDE, BURNING_AIR = 500, 2400
TEXTS = {
    'fluid.create_refinery_n_reactors.sulfurous_acid': ('Sulfurous Acid', '亜硫酸', '亚硫酸'),
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def fluid(id, amount): return {'type': 'neoforge:single', 'fluid': id, 'amount': amount}


def recipes():
    iron = [{'item': 'minecraft:iron_nugget'}, {'item': NS + 'iron_oxide_red'}]
    for name, gas, amount, extra in (('oxygen', NS + 'oxygen_gas', OXYGEN, []),
                                     ('air', NS + 'air_gas', AIR, [{'id': NS + 'nitrogen_gas', 'amount': AIR - OXYGEN}])):
        write(D / f'recipe/pressure_reaction/dilute_sulfuric_acid_iron_catalysed_{name}.json', {
            'type': NS + 'pressure_reaction', 'processing_time': 200,
            'ingredients': [iron, fluid('minecraft:water', WATER), fluid(NS + 'sulfur_dioxide', SULFUR_DIOXIDE), fluid(gas, amount)],
            'results': [{'id': NS + 'dilute_sulfuric_acid', 'amount': WATER}] + extra})
    write(D / 'recipe/pressure_reaction/sulfur_dioxide_from_air.json', {
        'type': NS + 'pressure_reaction', 'heat_requirement': 'heated',
        'ingredients': [{'tag': 'c:dusts/sulfur'}, fluid(NS + 'air_gas', BURNING_AIR)],
        'results': [{'id': NS + 'sulfur_dioxide', 'amount': BURNT_SULFUR_DIOXIDE}, {'id': NS + 'nitrogen_gas', 'amount': BURNING_AIR - BURNT_SULFUR_DIOXIDE}]})


def translations():
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / f'{language}.json'
        strings = json.loads(path.read_text())
        for key, text in TEXTS.items(): strings[key] = text[index]
        path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    recipes(); translations()
    print('Generated sulfur burnt in air, the iron-catalysed dilute sulfuric acid recipes and the sulfurous acid name')
