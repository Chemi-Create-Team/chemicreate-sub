#!/usr/bin/env python3
"""Additional uses for argon, fluorite and industrial glass. Run from the repository root."""
import sys

sys.path.insert(0, 'tools')
from generate_reactor_assets import D, NS, write


def fluid(name, amount):
    return {'type': 'neoforge:single', 'amount': amount, 'fluid': NS + name}


def tagged(name, count):
    return [{'tag': name} for _ in range(count)]


def mixing():
    # AOD refining needs argon, which Create's open basins refuse, and ten metal inputs, more than a stirred reactor recipe takes;
    # it belongs to the converter (docs/specs/oxygen-converter.md).
    for old in ('mixing', 'pressure_reaction'): (D / f'recipe/{old}/stainless_steel_ingot_aod.json').unlink(missing_ok=True)
    from generate_carbon_steel import recipes as carbon_steel_recipes
    carbon_steel_recipes()


def gauges():
    for name, instrument in (('pipe_pressure_gauge', {'item': 'minecraft:compass'}),
                             ('pipe_thermometer', {'item': 'minecraft:compass'}),
                             ('pipe_flow_meter', {'item': 'create:propeller'})):
        material = {'item': 'create:iron_sheet'} if name == 'pipe_pressure_gauge' else {'item': 'minecraft:copper_ingot'} if name == 'pipe_thermometer' else {'item': 'minecraft:compass'}
        write(D / f'recipe/{name}.json', {'type': 'minecraft:crafting_shapeless',
            'ingredients': [instrument, {'item': 'create:fluid_pipe'}, material, {'item': NS + 'industrial_glass'}],
            'result': {'id': NS + name, 'count': 1}})


if __name__ == '__main__':
    mixing(); gauges()
    print('Generated fluorite-flux and industrial-glass recipes')
