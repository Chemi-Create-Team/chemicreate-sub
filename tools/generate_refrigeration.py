#!/usr/bin/env python3
"""Ammonia refrigeration from ordinary equipment, condensing and ice making with the chilled antifreeze, pressure and freezing in pipes, and the ethylene glycol antifreeze line: recipes, translations and four Ponder
storyboards. Run from the repository root."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS, write
from generate_petrochemical_ponder import Structure, OUT
from generate_uranium_assets import fluid, fluid_out

D = A.parent.parent / 'data'
SIDES = ('east', 'west', 'north', 'south', 'up', 'down')


def pipe(s, x, y, z, name, *sides):
    s.block(x, y, z, name, **{side: str(side in sides).lower() for side in SIDES}, waterlogged='false')


def exchanger(s, x, y, z, length=3, width=2):
    """A formed shell-and-tube exchanger of tube-piped tanks, 2 x 2 or 3 x 3 across, lying east from its controller at (x, y, z): the
    tube side opens at the west and east end faces, the shell side on the top of each end (ExchangerStructure)."""
    controller, offset = ('heat_exchanger_controller', 0) if width == 2 else ('large_heat_exchanger_controller', 4)
    for index in range(length):
        segment = 0 if index == 0 else 2 if index == length - 1 else 1
        for across in range(width):
            for up in range(width):
                at = (x + index, y + up, z + across)
                if at == (x, y, z):
                    s.block(*at, NS + controller, facing='east', axis='x', lit='false')
                else:
                    s.block(*at, NS + 'tube_piped_tank', formed='true', cell=str(segment * 13 + offset + across + width * up), facing='east')


def pipe_pressure():
    s = Structure(9, 5)
    s.block(1, 1, 4, 'create:blaze_burner', blaze='kindled')
    s.block(1, 2, 4, NS + 'lidded_vessel')
    pipe(s, 2, 2, 4, 'create:fluid_pipe', 'east', 'west')
    for x in (3, 4): pipe(s, x, 2, 4, NS + 'reinforced_pipe', 'east', 'west')
    for y in (1, 2): s.block(5, y, 4, 'create:fluid_tank', bottom=str(y == 1).lower(), top=str(y == 2).lower(), shape='window')
    for x in range(2, 7): pipe(s, x, 2, 7, 'create:fluid_pipe', 'east', 'west')
    s.block(4, 1, 7, 'minecraft:magma_block')
    s.block(7, 1, 7, 'minecraft:snow_block')
    return s




SCENES = {
    'pipe_pressure': (pipe_pressure, 9, 5, ['reinforced_pipe', 'pipe_pressure_gauge'], ('Pressure and freezing in pipes', '配管の圧力と凍結'), [
        ([1, 1, 4], {'item': 'create:blaze_burner'},
         'Feed a fluid with the required pressure grade and check the pipe pressure gauge.',
         '必要な圧力等級の流体を送り、配管の圧力計で確認します。'),
        ([2, 2, 4], {},
         'Use pressure-rated pipes for hot water and liquefied gases. Check pressure with a gauge or goggles.',
         '高温の水や液化ガスには耐圧配管を使います。圧力計かゴーグルで上限を確認します。'),
        ([5, 1, 4], {},
         'Heating a sealed tank raises pressure. Fit a relief valve to vent it.',
         '密閉タンクを加熱すると圧力が上がります。安全弁を付けて排気します。'),
        ([4, 2, 7], {},
         'Water can freeze and block pipes in cold areas. Brine or antifreeze can help.',
         '寒い場所では水が凍り、配管をふさぐことがあります。塩水や不凍液も利用できます。'),
        ([4, 1, 7], {},
         'Warm a frozen pipe with a nearby burner or magma block. Cryogenic lines avoid freezing from ambient air.',
         '凍った配管の隣にバーナーやマグマブロックを置いて温めます。保冷配管なら外気で凍りません。'),
    ]),
}
NAMES = {
    'fluid.create_refinery_n_reactors.liquid_ammonia': ('Liquid Ammonia', '液体アンモニア', '液氨'),
    'fluid.create_refinery_n_reactors.ethylene_oxide': ('Ethylene Oxide', '酸化エチレン', '环氧乙烷'),
    'fluid.create_refinery_n_reactors.ethylene_glycol': ('Ethylene Glycol', 'エチレングリコール', '乙二醇'),
    'fluid.create_refinery_n_reactors.antifreeze': ('Antifreeze', '不凍液', '防冻液'),
    'fluid.create_refinery_n_reactors.liquid_chlorine': ('Liquid Chlorine', '液体塩素', '液氯'),
    'fluid.create_refinery_n_reactors.liquid_sulfur_dioxide': ('Liquid Sulfur Dioxide', '液体二酸化硫黄', '液态二氧化硫'),
    'fluid.create_refinery_n_reactors.liquid_vinyl_chloride': ('Liquid Vinyl Chloride', '液体塩化ビニル', '液态氯乙烯'),
    'fluid.create_refinery_n_reactors.liquid_propylene': ('Liquid Propylene', '液体プロピレン', '液态丙烯'),
}
OTHER = {
    'process.create_refinery_n_reactors.recipe.oxidize_ethylene': ('Catalytic oxidation of ethylene to ethylene oxide', '触媒によるエチレンの酸化（酸化エチレン）'),
    'reactor.create_refinery_n_reactors.status.evaporating': ('Evaporating the refrigerant', '冷媒が蒸発中'),
    'reactor.create_refinery_n_reactors.status.condensing': ('Condensing against the coolant', '冷媒で凝縮中'),
    'reactor.create_refinery_n_reactors.status.freezing': ('Freezing water into ice', '水を凍らせて氷を作っている'),
    'heating.create_refinery_n_reactors.ice': ('Ice: %s blocks, %s mB freezing', '氷：%s個、凍りかけ %s mB'),
    'heating.create_refinery_n_reactors.outlet_pressure': ('Outlet pressure', '出口圧力'),
    'heating.create_refinery_n_reactors.outlet': ('Outlet pressure %s bar: water boils at %s °C', '出口圧力 %s bar：水の沸点 %s °C'),
    'transport.create_refinery_n_reactors.freezing': ('%s freezing: %s%%', '%sが凍りかけ：%s%%'),
    'transport.create_refinery_n_reactors.frozen': ('%s frozen: blocked (%s%%)', '%sが凍結：詰まり（%s%%）'),
}


def recipes():
    write(D / NS[:-1] / 'recipe/pressure_reaction/ethylene_glycol.json', {'type': NS + 'pressure_reaction', 'heat_requirement': 'heated',
        'ingredients': [fluid(NS + 'ethylene_oxide', 250), fluid('minecraft:water', 250)], 'results': [fluid_out(NS + 'ethylene_glycol', 250)]})
    write(D / NS[:-1] / 'recipe/mixing/antifreeze.json', {'type': 'create:mixing',
        'ingredients': [fluid(NS + 'ethylene_glycol', 500), fluid('minecraft:water', 500)], 'results': [fluid_out(NS + 'antifreeze', 1000)]})


def ponder():
    path = A / 'ponder/tutorials.json'
    raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    catalog = [t for t in json.loads(raw) if t['id'] not in SCENES]
    for id, (layout, size, height, components, _, steps) in SCENES.items():
        layout().save(id)
        (A / 'ponder/plant' / f'{id}.nbt').write_bytes((OUT / f'{id}.nbt').read_bytes()); (OUT / f'{id}.nbt').unlink()
        catalog.append({'id': id, 'size': size, 'height': height, 'scale': .5, 'components': components,
            'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra, _, _) in enumerate(steps)]})
    path.write_bytes((json.dumps(catalog, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


def translations():
    for lang, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        p = A / 'lang' / f'{lang}.json'
        raw = p.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
        strings = json.loads(raw)
        for key, names in NAMES.items(): strings[key] = names[index]
        if index < 2:
            for key, text in OTHER.items(): strings[key] = text[index]
            for id, (_, _, _, _, title, steps) in SCENES.items():
                strings[f'create_refinery_n_reactors.ponder.shared.guide.{id}.title'] = title[index]
                strings[f'create_refinery_n_reactors.ponder.plant_{id}.header'] = title[index]
                for i, step in enumerate(steps): strings[f'create_refinery_n_reactors.ponder.shared.guide.{id}.step_{i + 1}'] = step[2 + index]
        p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


if __name__ == '__main__':
    recipes(); ponder(); translations()
