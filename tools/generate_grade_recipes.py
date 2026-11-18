#!/usr/bin/env python3
"""Type conversions in the existing Create mixer and lidded vessel."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/data/create_refinery_n_reactors/recipe'
NS = 'create_refinery_n_reactors:'


def fluid(name, amount):
    return {'type': 'neoforge:single', 'fluid': name if ':' in name else NS + name, 'amount': amount}


def product(name, amount):
    return {'id': name if ':' in name else NS + name, 'amount': amount}


def write(name, recipe):
    path = OUT / (name + '.json')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(recipe, indent=2) + '\n')


for name, source, target, amount, result in (
    ('preheat_crude_oil', 'createdieselgenerators:crude_oil', 'preheated_crude_oil', 1000, 1000),
    ('evaporate_ammonia', 'liquid_ammonia', 'ammonia_gas', 1, 934),
):
    write('mixing/' + name, {'type': 'create:mixing', 'ingredients': [fluid(source, amount)],
                           'results': [product(target, result)], 'heat_requirement': 'heated', 'processing_time': 100})

write('pressure_reaction/freeze_water', {'type': NS + 'pressure_reaction',
      'ingredients': [fluid('minecraft:water', 1000), fluid('liquid_nitrogen', 50)],
      'results': [{'id': 'minecraft:ice'}, product('nitrogen_gas', 32300)], 'processing_time': 100})

for source in ('minecraft:water', 'warm_water'):
    write('pressure_reaction/preheat_' + source.split(':')[-1], {'type': NS + 'pressure_reaction',
          'ingredients': [fluid(source, 1000)], 'results': [product('pressurised_water', 1000)],
          'heat_requirement': 'heated', 'processing_time': 100})
