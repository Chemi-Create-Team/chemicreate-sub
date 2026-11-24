#!/usr/bin/env python3
"""Two more routes to sulfuric acid: sulfur dioxide oxidised in water by an iron catalyst, and sulfurous acid split in the electrolysis cell.
Sulfur also burns in air, as in a real sulfur burner, so the cold route needs no electrolysis oxygen: air is a mixture
(MixtureHelper.airContents), so the vessel takes its oxygen and lets its nitrogen and other gases through to the gas outlet.

The first runs in a lidded vessel with an impeller: iron dissolved in the water carries oxygen to the dissolved sulfur dioxide, 2 SO2 + O2 + 2 H2O ->
2 H2SO4. The iron stays in the acid as iron sulfate, so a nugget or a pinch of iron oxide goes in with every batch. Air works as well as oxygen;
its nitrogen bubbles out through the vessel's gas outlet. The second is the hybrid sulfur cycle: the absorption tower makes sulfurous acid, which the cell splits into hydrogen and
sulfuric acid (its absorption and cell recipes are in ProcessRecipe and ElectrolysisProcess). Run from the repository root.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
REACTION_HEAT = json.loads((ROOT / 'tools/data/reaction_heat.json').read_text(encoding='utf-8'))
NS = 'create_refinery_n_reactors:'
# One batch: as much acid as the dilute acid mixed from 250 mB of sulfuric acid.
WATER, SULFUR_DIOXIDE, OXYGEN = 1000, 250, 125
# Burning one sulfur dust takes 500 mB of oxygen: a closed tank needs superheat, a heated impeller vessel stirs it in, so air burns it as well.
BURNT_SULFUR_DIOXIDE, BURNING_OXYGEN = 500, 500
TEXTS = {
    'fluid.create_refinery_n_reactors.sulfurous_acid': ('Sulfurous Acid', '亜硫酸', '亚硫酸'),
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def fluid(id, amount): return {'type': 'neoforge:single', 'fluid': id, 'amount': amount}


def recipes():
    iron = [{'item': 'minecraft:iron_nugget'}, {'item': NS + 'iron_oxide_red'}]
    write(D / 'recipe/pressure_reaction/dilute_sulfuric_acid_iron_catalysed_oxygen.json', {
        'type': NS + 'pressure_reaction', 'processing_time': 200,
        'ingredients': [iron, fluid('minecraft:water', WATER), fluid(NS + 'sulfur_dioxide', SULFUR_DIOXIDE), fluid(NS + 'oxygen_gas', OXYGEN)],
        'results': [{'id': NS + 'dilute_sulfuric_acid', 'amount': WATER}]})
    write(D / 'recipe/pressure_reaction/sulfur_dioxide.json', {
        'type': NS + 'pressure_reaction', 'module': 'none', 'heat_requirement': 'superheated',
        'ingredients': [{'tag': 'c:dusts/sulfur'}, fluid(NS + 'oxygen_gas', BURNING_OXYGEN)],
        'results': [{'amount': BURNT_SULFUR_DIOXIDE, 'id': NS + 'sulfur_dioxide'}], 'reaction_heat': REACTION_HEAT['sulfur_dioxide']})
    write(D / 'recipe/pressure_reaction/sulfur_dioxide_stirred.json', {
        'type': NS + 'pressure_reaction', 'heat_requirement': 'heated',
        'ingredients': [{'tag': 'c:dusts/sulfur'}, fluid(NS + 'oxygen_gas', BURNING_OXYGEN)],
        'results': [{'id': NS + 'sulfur_dioxide', 'amount': BURNT_SULFUR_DIOXIDE}], 'reaction_heat': REACTION_HEAT['sulfur_dioxide_stirred']})
    for retired in ('dilute_sulfuric_acid_iron_catalysed_air', 'sulfur_dioxide_from_air'):
        (D / f'recipe/pressure_reaction/{retired}.json').unlink(missing_ok=True)


def translations():
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / f'{language}.json'
        strings = json.loads(path.read_text())
        for key, text in TEXTS.items(): strings[key] = text[index]
        path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    recipes(); translations()
    print('Generated sulfur burnt in air, the iron-catalysed dilute sulfuric acid recipes and the sulfurous acid name')
