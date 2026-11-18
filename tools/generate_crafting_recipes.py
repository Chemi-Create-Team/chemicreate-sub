#!/usr/bin/env python3
"""Crafting recipes of the plant equipment, in three tiers (docs/reports/crafting-recipe-review.md).

- Basic: crafting table, no refined metal. Empty since the fluid-handling machines became stainless steel; the metals up to stainless steel
  come from Create's basin.
- Middle: crafting table with this mod's materials, the six metals and stainless steel.
- Advanced: Create's mechanical crafter, 5 x 5 or 7 x 7.

The nuclear reactor and the gantry crane are left out. This script is the only writer of these recipe files.
"""
import json
from pathlib import Path

NS = 'create_refinery_n_reactors:'
R = Path(__file__).resolve().parents[1] / 'src/main/resources/data/create_refinery_n_reactors/recipe'


def item(id): return {'item': id if ':' in id else NS + id}
def tag(id): return {'tag': id}


STEEL = item('steel_block'); STEEL_INGOT = item('steel_ingot'); STAINLESS = tag('c:plates/stainless_steel'); PRECISION = item('create:precision_mechanism')
TUBE = item('create:electron_tube'); GASKET = item('rubber_gasket'); BARS = item('minecraft:iron_bars'); IRON_SHEET = item('create:iron_sheet')
AL, NI, TI, ZR, CR, MO = (tag('c:ingots/' + m) for m in ('aluminum', 'nickel', 'titanium', 'zirconium', 'chromium', 'molybdenum'))

BASIC = {}
MIDDLE = {
    # Stainless tray storeys with sight glasses.
    'large_absorption_tower': (['SSS', 'G G', 'SSS'], {'S': STAINLESS, 'G': item('minecraft:glass')}, 1),
    'reciprocating_compressor': (['THT', 'GCG', 'TPT'], {'T': STEEL, 'H': item('create:shaft'), 'G': GASKET, 'C': item('create:brass_casing'), 'P': PRECISION}, 1),
    'reciprocating_cylinder': (['IGI', 'SCS', 'IGI'], {'I': item('minecraft:iron_ingot'), 'G': GASKET, 'S': STAINLESS, 'C': item('create:copper_casing')}, 1),
    # Aluminium fins on copper tubes.
    # Aluminium fins on copper tubes, as in real fin-fan coolers.
    'cooler_radiator': (['ACA', 'CPC', 'ACA'], {'A': tag('c:plates/aluminum'), 'C': tag('c:plates/copper'), 'P': item('create:fluid_pipe')}, 8),
    # A copper pipe inside a sheet-iron jacket.
    'jacketed_pipe': (['ICI', 'I I', 'ICI'], {'I': IRON_SHEET, 'C': item('create:copper_sheet')}, 3),
    # A gas receiver's shell in a steel container frame.
    'gas_tank_container': (['SIS', 'IGI', 'SIS'], {'S': STEEL_INGOT, 'I': IRON_SHEET, 'G': item('gas_receiver')}, 4),
    'relief_scrubber': (['SPS', 'GTG', 'SVS'], {'S': STAINLESS, 'P': item('create:fluid_pipe'), 'G': item('industrial_glass'),
        'T': item('create:fluid_tank'), 'V': item('create:fluid_valve')}, 1),
    'flow_regulator': ([' R ', 'PMP', 'S S'], {'R': item('industrial_resin'), 'S': STAINLESS, 'P': item('create:fluid_pipe'), 'M': PRECISION}, 1),
    'pressure_reducing_valve': ([' B ', 'PVP', ' S '], {'B': item('create:brass_sheet'), 'P': item('stainless_steel_pipe'), 'V': item('create:fluid_valve'), 'S': STAINLESS}, 1),
    'ammonia_separator': (['SPS', 'PTP', 'SPS'], {'S': STAINLESS, 'P': item('create:fluid_pipe'), 'T': item('create:fluid_tank')}, 1),
    'reaction_tower': (['SFS', 'GMG', 'SFS'], {'S': STAINLESS, 'F': item('minecraft:furnace'), 'G': item('minecraft:glass_pane'), 'M': MO}, 1),
    # The two tall process shells share the same corrosion-resistant structural recipe.
    'reactor_separator_core': (['SBS', 'BBB', 'SBS'], {'S': STAINLESS, 'B': BARS}, 1),
    'fractionating_column': (['SSS', 'ITI', 'SSS'], {'S': STAINLESS, 'I': BARS, 'T': item('create:fluid_tank')}, 2),
    # Catalysts after their real ones: zeolite (silica and alumina), cobalt- or nickel-molybdenum on alumina, Ziegler-Natta (titanium and aluminium).
    'cracking_catalyst': (['Q Q', 'AIA', 'Q Q'], {'Q': item('minecraft:amethyst_shard'), 'A': AL, 'I': item('iron_catalyst')}, 1),
    'hydrotreating_catalyst': ([' M ', 'AIA', ' N '], {'M': MO, 'A': AL, 'I': item('iron_catalyst'), 'N': NI}, 1),
    'hydrocracking_catalyst': ([' M ', 'NHN', ' Q '], {'M': MO, 'N': NI, 'H': item('hydrotreating_catalyst'), 'Q': item('minecraft:amethyst_shard')}, 1),
    'polymerization_catalyst': ([' T ', 'AIA', ' T '], {'T': TI, 'A': AL, 'I': item('iron_catalyst')}, 1),
    'high_pressure_turbine_casing': (['SCS', 'MIM', 'SCS'], {'S': STEEL, 'C': CR, 'M': MO, 'I': IRON_SHEET}, 4),
    'low_pressure_turbine_casing': (['SIS', 'IAI', 'SIS'], {'S': STAINLESS, 'I': item('minecraft:iron_ingot'), 'A': item('create:andesite_alloy')}, 4),
    'low_pressure_turbine_end': (['C C', ' S ', 'C C'], {'C': STAINLESS, 'S': item('create:shaft')}, 2),
    'tarbin_end': ([' C ', 'CSC', ' C '], {'C': STAINLESS, 'S': item('create:shaft')}, 2),
    # Zircaloy: zirconium with a little tin, iron, chromium and nickel; it hardly absorbs neutrons.
    'zircaloy_cladding_tube': ([' Z ', 'ZNZ', ' Z '], {'Z': ZR, 'N': NI}, 4),
}
ADVANCED = {
    'large_flywheel': ([' SSS ', 'SIIIS', 'SIFIS', 'SIIIS', ' SSS '], {'S': IRON_SHEET, 'I': STEEL, 'F': item('create:flywheel')}, 1),
    # Nickel superalloy with chromium for the hot stages, titanium for the long low-pressure blades.
    'high_pressure_turbine_blade': (['  N  ', ' NCN ', 'NCACN', ' NCN ', '  N  '], {'N': NI, 'C': CR, 'A': item('create:andesite_alloy')}, 6),
    'low_pressure_turbine_blade': (['T   T', ' TST ', '  A  ', ' TST ', 'T   T'], {'T': tag('c:plates/titanium'), 'S': STAINLESS, 'A': item('create:andesite_alloy')}, 6),
    'cooling_fan': ([' AAA ', 'AHPHA', 'APFPA', 'AHPHA', ' AAA '], {'A': AL, 'H': item('create:shaft'), 'P': item('create:propeller'), 'F': item('create:encased_fan')}, 1),
}


def write(name, data):
    path = R / f'{name}.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def steelmaking():
    (R / 'sequenced_assembly/industrial_steel_stock.json').unlink(missing_ok=True)
    (R / 'sequenced_assembly/industrial_iron_block.json').unlink(missing_ok=True)
    from generate_carbon_steel import recipes as carbon_steel_recipes
    carbon_steel_recipes()
    create_recipes = R.parents[1] / 'create/recipe'
    for name in ('industrial_iron_block_from_ingots_iron_stonecutting',
                 'weathered_iron_block_from_ingots_iron_stonecutting'):
        path = create_recipes / f'{name}.json'
        path.unlink(missing_ok=True)


def check(recipes):
    for name, (pattern, key, _) in recipes.items():
        assert len({len(row) for row in pattern}) == 1, name
        assert set(''.join(pattern)) - {' '} == set(key), name


def main():
    for tier in (BASIC, MIDDLE, ADVANCED): check(tier)
    assert not set(BASIC) & set(MIDDLE) and not set(MIDDLE) & set(ADVANCED) and not set(BASIC) & set(ADVANCED)
    metals = [AL, NI, TI, ZR, CR, MO, STAINLESS]
    for name, (_, key, _) in BASIC.items(): assert not any(value in metals for value in key.values()), f'{name} is needed to refine the metals'
    for name, (pattern, key, count) in {**BASIC, **MIDDLE}.items():
        write(name, {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key, 'result': {'id': NS + name, 'count': count}})
    for name, (pattern, key, count) in ADVANCED.items():
        write(name, {'type': 'create:mechanical_crafting', 'accept_mirrored': True, 'category': 'misc', 'pattern': pattern, 'key': key,
                     'result': {'id': NS + name, 'count': count}, 'show_notification': True})
    steelmaking()
    print(f'Wrote {len(BASIC)} basic, {len(MIDDLE)} middle and {len(ADVANCED)} mechanical crafting recipes')


if __name__ == '__main__':
    main()
