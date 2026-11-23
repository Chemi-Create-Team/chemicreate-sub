#!/usr/bin/env python3
"""Ores of the seven engineering metals: ore and mineral textures, models, loot, tags, worldgen, the stirred-reactor reactions from
ore to oxide and metal, and translations. Run from the repository root."""
import json, sys
sys.path.insert(0, 'tools')
from powder_assets import write_powder_item
from generate_reactor_assets import A, D, NS, write
from generate_uranium_assets import full_cube, tag, item, fluid, fluid_out, deep_layer

# Ore: (worldgen size, count, lowest y, highest y, names en/ja/zh of ore and mineral).
ORES = {
    'bauxite': (12, 4, 32, 112, ('Bauxite Ore', 'ボーキサイト鉱石', '铝土矿石'), ('Bauxite', 'ボーキサイト', '铝土矿')),
    'pentlandite': (6, 3, -56, 16, ('Pentlandite Ore', 'ペントランド鉱石', '镍黄铁矿石'), ('Pentlandite', 'ペントランド鉱', '镍黄铁矿')),
    'ilmenite': (8, 3, -32, 48, ('Ilmenite Ore', 'チタン鉄鉱石', '钛铁矿石'), ('Ilmenite', 'チタン鉄鉱', '钛铁矿')),
    'zircon': (5, 3, 0, 64, ('Zircon Ore', 'ジルコン鉱石', '锆石矿石'), ('Zircon', 'ジルコン', '锆石')),
    'chromite': (7, 3, -48, 32, ('Chromite Ore', 'クロム鉄鉱石', '铬铁矿石'), ('Chromite', 'クロム鉄鉱', '铬铁矿')),
    'cassiterite': (8, 4, 0, 96, ('Cassiterite Ore', '錫石鉱石', '锡石矿石'), ('Raw Tin (Cassiterite)', '錫の原石（錫石）', '粗锡（锡石）')),
    'molybdenite': (5, 2, -16, 80, ('Molybdenite Ore', '輝水鉛鉱石', '辉钼矿石'), ('Molybdenite', '輝水鉛鉱', '辉钼矿')),
}
OXIDES = {
    'alumina': ((232, 232, 226), ('Alumina', 'アルミナ', '氧化铝')),
    'titanium_dioxide': ((246, 246, 240), ('Titanium Dioxide', '二酸化チタン', '二氧化钛')),
    'zirconia': ((226, 220, 206), ('Zirconia', 'ジルコニア', '氧化锆')),
    'nickel_oxide': ((104, 134, 86), ('Nickel Oxide', '酸化ニッケル', '氧化镍')),
    'molybdenum_trioxide': ((222, 216, 168), ('Molybdenum Trioxide', '三酸化モリブデン', '三氧化钼')),
    'chromium_oxide': ((58, 100, 50), ('Chromium Oxide Green', '酸化クロム（クロムグリーン）', '氧化铬绿')),
    'iron_oxide_red': ((140, 52, 34), ('Iron Oxide Red', '酸化鉄赤（ベンガラ）', '氧化铁红')),
    'nickel_titanium_yellow': ((230, 212, 74), ('Nickel Titanium Yellow', 'ニッケルチタンイエロー', '镍钛黄')),
    'molybdenum_blue': ((42, 72, 160), ('Molybdenum Blue', 'モリブデンブルー', '钼蓝')),
}
FLUIDS = {
    'aluminium_sulfate_solution': ('Aluminium Sulfate Solution', '硫酸アルミニウム溶液', '硫酸铝溶液'),
    'titanyl_sulfate_solution': ('Titanyl Sulfate Solution', '硫酸チタニル溶液', '硫酸氧钛溶液'),
    'zirconium_sulfate_solution': ('Zirconium Sulfate Solution', '硫酸ジルコニウム溶液', '硫酸锆溶液'),
    'iron_sulfate_solution': ('Purified Iron Solution', '精製鉄溶液', '精制铁溶液'),
    'copper_sulfate_solution': ('Purified Copper Solution', '精製銅溶液', '精制铜溶液'),
    'zinc_sulfate_solution': ('Purified Zinc Solution', '精製亜鉛溶液', '精制锌溶液'),
    'tin_sulfate_solution': ('Purified Tin Solution', '精製錫溶液', '精制锡溶液'),
}


def textures():
    from mineral_reference_assets import textures as reference_textures
    reference_textures(ORES)
    from generate_metal_recovery import tin_assets
    tin_assets()
    for oxide, (color, _) in OXIDES.items():
        write_powder_item(oxide)


def blocks():
    silk = {'condition': 'minecraft:match_tool', 'predicate': {'predicates': {'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}
    stone_ids, deep_ids = [], []
    for ore, (*_, size, count, low, high, _, _) in ORES.items():
        for prefix, stone, rubble in (('', 'stone', 'minecraft:cobblestone'), ('deepslate_', 'deepslate', 'minecraft:cobbled_deepslate')):
            id = f'{prefix}{ore}_ore'; base = 'minecraft:block/' + stone
            write(A / 'models/block' / f'{id}.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout_mipped',
                'textures': {'particle': base, 'base': base, 'overlay': NS + f'block/{prefix}{ore}_ore_overlay'}, 'elements': [full_cube('#base'), full_cube('#overlay')]})
            write(A / 'blockstates' / f'{id}.json', {'variants': {'': {'model': NS + 'block/' + id}}})
            write(A / 'models/item' / f'{id}.json', {'parent': NS + 'block/' + id})
            write(D / 'loot_table/blocks' / f'{id}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:alternatives', 'children': [
                {'type': 'minecraft:item', 'name': NS + id, 'conditions': [silk]},
                {'type': 'minecraft:item', 'name': NS + ore, 'functions': [{'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'}, {'function': 'minecraft:explosion_decay'}]}]}]}]})
            write(D / 'recipe/crushing' / f'{id}.json', {'type': 'create:crushing', 'ingredients': [{'item': NS + id}], 'processing_time': 250,
                'results': [item(NS + ore), item(NS + ore, chance=0.75), item('create:experience_nugget', chance=0.75), item(rubble, chance=0.12)]})
            (deep_ids if prefix else stone_ids).append(NS + id)
        write(A / 'models/item' / f'{ore}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + ore}})
        ids = [NS + f'{ore}_ore', NS + f'deepslate_{ore}_ore']
        for kind in ('block', 'item'): tag(f'c/tags/{kind}/ores/{ore}.json', ids); tag(f'c/tags/{kind}/ores.json', [f'#c:ores/{ore}'])
        write(D / 'worldgen/configured_feature' / f'{ore}_ore.json', {'type': 'minecraft:ore', 'config': {'size': size, 'discard_chance_on_air_exposure': 0.0, 'targets': [
            {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'}, 'state': {'Name': ids[0]}},
            {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:deepslate_ore_replaceables'}, 'state': {'Name': ids[1]}}]}})
        write(D / 'worldgen/placed_feature' / f'{ore}_ore.json', {'feature': NS + f'{ore}_ore', 'placement': [{'type': 'minecraft:count', 'count': count}, {'type': 'minecraft:in_square'},
            {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': low}, 'max_inclusive': {'absolute': high}}}, {'type': 'minecraft:biome'}]})
        write(D / 'neoforge/biome_modifier' / f'{ore}_ore.json', {'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': deep_layer(f'{ore}_ore', count, low, high) if low <= -32 else NS + f'{ore}_ore', 'step': 'underground_ores'})
    for kind in ('block', 'item'):
        tag(f'c/tags/{kind}/ores_in_ground/stone.json', stone_ids); tag(f'c/tags/{kind}/ores_in_ground/deepslate.json', deep_ids)
    tag('minecraft/tags/block/mineable/pickaxe.json', stone_ids + deep_ids)
    tag('minecraft/tags/block/needs_iron_tool.json', stone_ids + deep_ids)
    for oxide in OXIDES: write(A / 'models/item' / f'{oxide}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + oxide}})


def recipes():
    """The sulfate route to the oxides that the electrolysis furnace reduces, roasting and hydrogen for nickel and molybdenum, and
    aluminothermy for chromium. Sulfur trioxide from calcining and sulfur dioxide from roasting go back to the sulfuric acid line."""
    U = NS; acid = U + 'sulfuric_acid'
    reactions = {
        'aluminium_sulfate_solution': ([{'item': U + 'bauxite'}, {'item': U + 'bauxite'}, fluid(acid, 500)], [fluid_out(U + 'aluminium_sulfate_solution', 500)], 'heated'),
        'titanyl_sulfate_solution': ([{'item': U + 'ilmenite'}, {'item': U + 'ilmenite'}, fluid(acid, 500)], [fluid_out(U + 'titanyl_sulfate_solution', 500), item('minecraft:iron_nugget', 3)], 'heated'),
        'titanium_dioxide': ([fluid(U + 'titanyl_sulfate_solution', 500)], [item(U + 'titanium_dioxide'), fluid_out(U + 'sulfur_trioxide', 500)], 'superheated'),
        # The shorter chloride route consumes its acid and returns only half the titanium dioxide of the two-stage sulfate route.
        'synthetic_rutile': ([{'item': U + 'ilmenite'}] * 3 + [fluid(U + 'hydrochloric_acid', 500)],
                             [item(U + 'titanium_dioxide'), item('minecraft:iron_nugget', 5)], 'heated'),
        'zirconium_sulfate_solution': ([{'item': U + 'zircon'}, {'item': U + 'zircon'}, fluid(acid, 500)], [fluid_out(U + 'zirconium_sulfate_solution', 500), item('minecraft:sand')], 'superheated'),
        'zirconia': ([{'item': U + 'zirconyl_sulfate'}], [item(U + 'zirconia'), fluid_out(U + 'sulfur_trioxide', 250)], 'superheated'),
        'nickel_oxide': ([{'item': U + 'pentlandite'}, {'item': U + 'pentlandite'}, fluid(U + 'oxygen_gas', 1000)], [item(U + 'nickel_oxide'), item('minecraft:iron_nugget', 3), fluid_out(U + 'sulfur_dioxide', 500)], 'heated'),
        'nickel_ingot': ([{'item': U + 'nickel_oxide'}, fluid(U + 'hydrogen_gas', 500)], [item(U + 'nickel_ingot'), fluid_out('minecraft:water', 250)], 'heated'),
        'molybdenum_trioxide': ([{'item': U + 'molybdenite'}, {'item': U + 'molybdenite'}, fluid(U + 'oxygen_gas', 1500)], [item(U + 'molybdenum_trioxide'), fluid_out(U + 'sulfur_dioxide', 1000)], 'heated'),
        'molybdenum_ingot': ([{'item': U + 'molybdenum_trioxide'}, fluid(U + 'hydrogen_gas', 750)], [item(U + 'molybdenum_ingot'), fluid_out('minecraft:water', 750)], 'heated'),
        'chromium_ingot': ([{'item': U + 'chromite'}, {'item': U + 'chromite'}, {'tag': 'c:ingots/aluminum'}], [item(U + 'chromium_ingot'), item('minecraft:iron_nugget', 4), item(U + 'alumina')], 'superheated'),
        # Roasting chromite in oxygen; the green oxide is the pigment, the iron comes out as nuggets.
        'chromium_oxide': ([{'item': U + 'chromite'}, {'item': U + 'chromite'}, fluid(U + 'oxygen_gas', 500)], [item(U + 'chromium_oxide'), item('minecraft:iron_nugget', 2)], 'superheated'),
        # Black triiron tetroxide burns on to red hematite, as in the making of iron oxide red.
        'iron_oxide_red': ([{'item': U + 'triiron_tetroxide_pile'}, fluid(U + 'oxygen_gas', 250)], [item(U + 'iron_oxide_red')], 'heated'),
        # Nickel oxide fired into the rutile lattice of titanium dioxide.
        'nickel_titanium_yellow': ([{'item': U + 'titanium_dioxide'}, {'item': U + 'titanium_dioxide'}, {'item': U + 'nickel_oxide'}], [item(U + 'nickel_titanium_yellow', 3)], 'superheated'),
        # Sulfur dioxide in water partly reduces the oxide to the blue mixed-valence oxide and is itself oxidised to sulfuric acid.
        'molybdenum_blue': ([{'item': U + 'molybdenum_trioxide'}, fluid(U + 'sulfur_dioxide', 250), fluid('minecraft:water', 250)], [item(U + 'molybdenum_blue'), fluid_out(acid, 250)], 'heated'),
    }
    for id, (ingredients, results, heat) in reactions.items():
        data = {'type': U + 'pressure_reaction'}
        if heat: data['heat_requirement'] = heat
        data.update(ingredients=ingredients, results=results)
        write(D / 'recipe/pressure_reaction' / f'{id}.json', data)
    from generate_metal_recovery import ore_recipes
    ore_recipes()
    # Pigments ground on Create's millstone, each strong enough for four dyes. Triiron tetroxide is iron oxide black as it is.
    for pigment, dye in (('titanium_dioxide', 'white_dye'), ('chromium_oxide', 'green_dye'), ('triiron_tetroxide_pile', 'black_dye'), ('iron_oxide_red', 'red_dye'),
                         ('nickel_titanium_yellow', 'yellow_dye'), ('molybdenum_blue', 'blue_dye')):
        write(D / 'recipe/milling' / f'{dye}_from_{pigment}.json', {'type': 'create:milling', 'ingredients': [{'item': U + pigment}], 'processing_time': 100,
            'results': [item('minecraft:' + dye, 4)]})


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; data = json.loads(raw)
        for ore, (*_, ore_names, mineral) in ORES.items():
            data[f'block.create_refinery_n_reactors.{ore}_ore'] = ore_names[index]
            data[f'block.create_refinery_n_reactors.deepslate_{ore}_ore'] = ('Deepslate ', '深層', '深板岩')[index] + ore_names[index]
            data[f'item.create_refinery_n_reactors.{ore}'] = mineral[index]
        for oxide, (_, names) in OXIDES.items(): data[f'item.create_refinery_n_reactors.{oxide}'] = names[index]
        for id, names in FLUIDS.items(): data[f'fluid.create_refinery_n_reactors.{id}'] = names[index]
        path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


if __name__ == '__main__':
    textures(); blocks(); recipes(); translations()
    print('Generated the seven metal ores, minerals, oxides, their reactions and translations')
