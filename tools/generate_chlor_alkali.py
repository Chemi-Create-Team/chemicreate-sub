#!/usr/bin/env python3
"""The chlor-alkali line: halite ore and its worldgen, salt and PVC resin, brine, the Bayer process, hydrochloric acid and pickling, the
vinyl chloride chain to PVC, and translations. Run from the repository root."""
import json, sys
sys.path.insert(0, 'tools')
from powder_assets import write_powder_item
from generate_reactor_assets import A, D, NS, write
from generate_uranium_assets import full_cube, tag, item, fluid, fluid_out

FLUIDS = {
    'brine': ('Brine', '塩水', '盐水'),
    'chlorine': ('Chlorine', '塩素', '氯气'),
    'caustic_soda_solution': ('Caustic Soda Solution', '苛性ソーダ液', '烧碱溶液'),
    'sodium_aluminate_solution': ('Sodium Aluminate Solution', 'アルミン酸ナトリウム溶液', '铝酸钠溶液'),
    'hydrogen_chloride': ('Hydrogen Chloride', '塩化水素', '氯化氢'),
    'hydrochloric_acid': ('Hydrochloric Acid', '塩酸', '盐酸'),
    'ethylene_dichloride': ('Ethylene Dichloride', '二塩化エチレン', '二氯乙烷'),
    'vinyl_chloride': ('Vinyl Chloride', '塩化ビニル', '氯乙烯'),
}
NAMES = {
    'block.create_refinery_n_reactors.halite_ore': ('Halite Ore', '岩塩鉱石', '岩盐矿石'),
    'block.create_refinery_n_reactors.deepslate_halite_ore': ('Deepslate Halite Ore', '深層岩塩鉱石', '深板岩岩盐矿石'),
    'item.create_refinery_n_reactors.salt': ('Salt', '塩', '盐'),
    'item.create_refinery_n_reactors.pvc_resin': ('PVC Resin', '塩化ビニル樹脂', '聚氯乙烯树脂'),
    'message.create_refinery_n_reactors.tank_casing.salt': ('Salt dried from sea water: %s', '海水から取れた塩：%s個', '海水晒出的盐：%s'),
    'jei.create_refinery_n_reactors.note.electrolysis_brine_diaphragm':
        ('One plate for %s ticks, %s kJ. One asbestos per 10,000 mB brine; caustic soda is one-quarter higher.',
         '極板1枚・%s tick・%s kJ。塩水10,000 mBごとに石綿1個を使い、苛性ソーダ液は1.25倍',
         '每块电极运行%s tick、消耗%s kJ。每10,000 mB盐水消耗1个石棉，烧碱增至1.25倍'),
}


def textures():
    from mineral_reference_assets import textures as reference_textures
    reference_textures(('halite',))
    write_powder_item('salt')
    write_powder_item('pvc_resin')
    for name in ('salt', 'pvc_resin'):
        write(A / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})


def ores():
    silk = {'condition': 'minecraft:match_tool', 'predicate': {'predicates': {'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}
    ids = []
    for prefix, stone, rubble in (('', 'stone', 'minecraft:cobblestone'), ('deepslate_', 'deepslate', 'minecraft:cobbled_deepslate')):
        id = f'{prefix}halite_ore'; base = 'minecraft:block/' + stone
        write(A / 'models/block' / f'{id}.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout_mipped',
            'textures': {'particle': base, 'base': base, 'overlay': NS + f'block/{prefix}halite_ore_overlay'}, 'elements': [full_cube('#base'), full_cube('#overlay')]})
        write(A / 'blockstates' / f'{id}.json', {'variants': {'': {'model': NS + 'block/' + id}}})
        write(A / 'models/item' / f'{id}.json', {'parent': NS + 'block/' + id})
        # Rock salt breaks into several lumps of salt, like redstone or lapis.
        write(D / 'loot_table/blocks' / f'{id}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:alternatives', 'children': [
            {'type': 'minecraft:item', 'name': NS + id, 'conditions': [silk]},
            {'type': 'minecraft:item', 'name': NS + 'salt', 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}},
                {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:uniform_bonus_count', 'parameters': {'bonusMultiplier': 1}},
                {'function': 'minecraft:explosion_decay'}]}]}]}]})
        write(D / 'recipe/crushing' / f'{id}.json', {'type': 'create:crushing', 'ingredients': [{'item': NS + id}], 'processing_time': 200,
            'results': [item(NS + 'salt', 4), item(NS + 'salt', 2, chance=0.5), item('create:experience_nugget', chance=0.5), item(rubble, chance=0.12)]})
        ids.append(NS + id)
    for kind in ('block', 'item'):
        tag(f'c/tags/{kind}/ores/salt.json', ids); tag(f'c/tags/{kind}/ores.json', ['#c:ores/salt'])
        tag(f'c/tags/{kind}/ores_in_ground/stone.json', ids[:1]); tag(f'c/tags/{kind}/ores_in_ground/deepslate.json', ids[1:])
    tag('c/tags/item/dusts/salt.json', [NS + 'salt'])
    tag('minecraft/tags/block/mineable/pickaxe.json', ids)
    tag('minecraft/tags/block/needs_stone_tool.json', ids)
    # Evaporite beds: few but large bodies in the upper stone.
    write(D / 'worldgen/configured_feature/halite_ore.json', {'type': 'minecraft:ore', 'config': {'size': 20, 'discard_chance_on_air_exposure': 0.0, 'targets': [
        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'}, 'state': {'Name': ids[0]}},
        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:deepslate_ore_replaceables'}, 'state': {'Name': ids[1]}}]}})
    write(D / 'worldgen/placed_feature/halite_ore.json', {'feature': NS + 'halite_ore', 'placement': [{'type': 'minecraft:count', 'count': 3}, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': -16}, 'max_inclusive': {'absolute': 96}}}, {'type': 'minecraft:biome'}]})
    write(D / 'neoforge/biome_modifier/halite_ore.json', {'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': NS + 'halite_ore', 'step': 'underground_ores'})


def recipes():
    U = NS
    write(D / 'recipe/mixing/brine.json', {'type': 'create:mixing', 'ingredients': [{'item': U + 'salt'}, fluid('minecraft:water', 250)], 'results': [fluid_out(U + 'brine', 250)]})
    # Pickling: hydrochloric acid takes the patina off copper; the more weathered, the more acid.
    for stage, acid in (('exposed', 50), ('weathered', 100), ('oxidized', 150)):
        write(D / 'recipe/mixing' / f'copper_block_from_{stage}_copper.json', {'type': 'create:mixing', 'ingredients': [{'item': f'minecraft:{stage}_copper'}, fluid(U + 'hydrochloric_acid', acid)],
            'results': [item('minecraft:copper_block')]})
    reactions = {
        # Bayer process: hot caustic soda dissolves the alumina of bauxite and leaves the iron as red mud; cooled and seeded, the liquor gives
        # back its alumina and the soda.
        'sodium_aluminate_solution': ([{'item': U + 'bauxite'}, {'item': U + 'bauxite'}, fluid(U + 'caustic_soda_solution', 500)],
                                      [fluid_out(U + 'sodium_aluminate_solution', 500), item('minecraft:iron_nugget', 3)], 'heated'),
        'alumina_from_sodium_aluminate_solution': ([fluid(U + 'sodium_aluminate_solution', 500)], [item(U + 'alumina'), fluid_out(U + 'caustic_soda_solution', 400)], 'superheated'),
        # Hydrogen burns in chlorine to hydrogen chloride, which water takes up as hydrochloric acid.
        'hydrogen_chloride': ([fluid(U + 'hydrogen_gas', 250), fluid(U + 'chlorine', 250)], [fluid_out(U + 'hydrogen_chloride', 500)], None),
        # Ethylene and chlorine add to ethylene dichloride; cracked, it gives vinyl chloride and the hydrogen chloride back; polymerised, PVC.
        'ethylene_dichloride': ([fluid(U + 'ethylene', 250), fluid(U + 'chlorine', 250)], [fluid_out(U + 'ethylene_dichloride', 250)], None),
        'vinyl_chloride': ([fluid(U + 'ethylene_dichloride', 250)], [fluid_out(U + 'vinyl_chloride', 250), fluid_out(U + 'hydrogen_chloride', 250)], 'superheated'),
        'pvc_resin': ([fluid(U + 'vinyl_chloride', 250)], [item(U + 'pvc_resin', 2)], 'heated'),
    }
    for id, (ingredients, results, heat) in reactions.items():
        data = {'type': U + 'pressure_reaction'}
        if heat: data['heat_requirement'] = heat
        data.update(ingredients=ingredients, results=results)
        write(D / 'recipe/pressure_reaction' / f'{id}.json', data)


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; data = json.loads(raw)
        for key, names in NAMES.items(): data[key] = names[index]
        for id, names in FLUIDS.items(): data[f'fluid.create_refinery_n_reactors.{id}'] = names[index]
        path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


if __name__ == '__main__':
    textures(); ores(); recipes(); translations()
    print('Generated halite ore, salt, PVC resin, the chlor-alkali reactions and translations')
