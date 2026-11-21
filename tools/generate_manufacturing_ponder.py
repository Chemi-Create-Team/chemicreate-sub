#!/usr/bin/env python3
"""Author complete manufacturing families with recipe-backed, full-scale Ponder equipment."""
from copy import deepcopy
from pathlib import Path
import gzip
import json
import struct
import nbtlib
import generate_petrochemical_ponder as ponder

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
TEMPLATE_DATA = ROOT / 'tools/data/manufacturing'

# Equipment tutorials remain the source of these working assemblies.
TEMPLATES = {
    'vessel': ('template/process_vessel', (4, 2, 4), (3, 2, 4), (7, 4, 4)),
    'catalytic': ('plant/reaction_tower', (4, 2, 4), (3, 2, 4), (5, 2, 4)),
    'centrifuge': ('plant/gas_centrifuge', (2, 2, 3), (2, 2, 3), (2, 3, 3)),
    'separator': ('chemistry/ammonia_separator', (2, 2, 2), (5, 4, 2), (2, 1, 2)),
    'furnace': ('plant/electrolysis_furnace', (5, 3, 2), (5, 3, 2), (5, 3, 2)),
    'converter': ('plant/converter', (1, 6, 6), (4, 7, 6), (5, 4, 6)),
    'carbonization': ('template/wood_carbonization', (2, 1, 2), (1, 1, 2), (4, 1, 2)),
    'cold_box': ('template/helium_cold_box', (4, 2, 3), (4, 3, 3), (4, 1, 3)),
}
RETIRED = {
    'acid_products', 'rubber_mixing', 'industrial_resin', 'synthetic_leather', 'industrial_glass',
    'sulfuric_acid', 'metal_refining', 'chlor_alkali', 'blasting', 'steelmaking',
    'chlor_alkali_products', 'ammonia_refrigeration', 'chilled_brine', 'antifreeze',
    'fusion_deuterium',
}
RETIRED_CUSTOM = {'building_materials', 'water_treatment', 'wood_carbonization'}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = path.read_bytes().decode('utf-8') if path.exists() else ''
    newline = '\r\n' if '\r\n' in raw else '\n'
    path.write_bytes((json.dumps(value, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


def packed_position(value, delta):
    x = value >> 38
    z = (value >> 12) & 0x3ffffff
    y = value & 0xfff
    if z >= 0x2000000: z -= 0x4000000
    if y >= 0x800: y -= 0x1000
    return ((x + delta) & 0x3ffffff) << 38 | (z & 0x3ffffff) << 12 | (y & 0xfff)


def relocate(tag, offset):
    for key, value in list(tag.items()):
        if key == 'Controller' and isinstance(value, nbtlib.IntArray):
            tag[key] = nbtlib.IntArray([int(value[0]) + offset, int(value[1]), int(value[2])])
        elif key in ('PowerInput', 'Controller') and isinstance(value, nbtlib.Long):
            packed = packed_position(int(value), offset)
            tag[key] = nbtlib.Long(packed if packed < 2 ** 63 else packed - 2 ** 64)
        elif isinstance(value, nbtlib.Compound):
            relocate(value, offset)


def copy_unit(source, destination, offset):
    palette = destination['palette']
    for block in source['blocks']:
        state = source['palette'][int(block['state'])]
        try: state_index = palette.index(state)
        except ValueError:
            state_index = len(palette);palette.append(deepcopy(state))
        block = deepcopy(block)
        block['pos'][0] = nbtlib.Int(int(block['pos'][0]) + offset)
        block['state'] = nbtlib.Int(state_index)
        if 'nbt' in block: relocate(block['nbt'], offset)
        destination['blocks'].append(block)


def pipe(scene, x, y, z, *sides):
    scene.block(x, y, z, NS + 'stainless_steel_pipe', waterlogged='false',
                **{side: str(side in sides).lower() for side in ponder.DIRECTIONS})


def motor_pump(scene, x, y, z, facing, drive_forward=False):
    scene.pump(x, y, z, facing)
    if facing in ('north', 'south'):
        scene.block(x + 1, y, z, 'create:cogwheel', axis='z', waterlogged='false')
        scene.block(x + 1, y, z + (1 if drive_forward else -1), 'create:creative_motor', facing='north' if drive_forward else 'south')
    else:
        scene.block(x, y, z + 1, 'create:cogwheel', axis='x', waterlogged='false')
        scene.block(x - 1, y, z + 1, 'create:creative_motor', facing='east')


def simple_units():
    units = {}
    scene = ponder.Structure(9, 9)
    from generate_absorption_tower import ring
    for y in range(1, 7): ring(scene, 4, y, 4)
    units['absorber'] = (scene, (4, 1, 4), (5, 1, 4), (3, 1, 4))

    scene = ponder.Structure(11, 9)
    scene.reactor(5, 2, 5, 'stirred', 3, burners=((0, 0),), windows=True)
    scene.block(3, 2, 5, 'create:andesite_funnel', facing='west', extracting='false', waterlogged='false')
    scene.block(7, 2, 5, 'create:andesite_funnel', facing='east', extracting='true', waterlogged='false')
    scene.block(8, 1, 5, 'create:depot')
    pipe(scene, 4, 2, 3, 'north', 'south')
    motor_pump(scene, 4, 2, 2, 'south', drive_forward=True)
    scene.block(4, 2, 1, 'create:fluid_tank', bottom='true', top='true', shape='window')
    pipe(scene, 4, 5, 4, 'up', 'down')
    scene.pump(4, 6, 4, 'up')
    scene.block(3, 6, 4, 'create:cogwheel', axis='y', waterlogged='false')
    scene.block(3, 5, 4, 'create:creative_motor', facing='up')
    scene.block(4, 7, 4, NS + 'gas_receiver', axis='y', bottom='true', top='true', shape='plain')
    units['resin_vessel'] = (scene, (4, 2, 4), (3, 2, 5), (7, 2, 5))

    scene = ponder.Structure(11, 7)
    scene.reactor(5, 2, 5, 'stirred', 3, windows=True)
    scene.block(3, 2, 5, 'create:andesite_funnel', facing='west', extracting='false', waterlogged='false')
    scene.block(7, 2, 5, 'create:andesite_funnel', facing='east', extracting='true', waterlogged='false')
    scene.block(8, 1, 5, 'create:depot')
    scene.tank(5, 2, 1)
    motor_pump(scene, 5, 2, 2, 'south', drive_forward=True)
    pipe(scene, 5, 2, 3, 'north', 'south')
    scene.filter(5, 2, 7, 'south')
    motor_pump(scene, 5, 2, 8, 'south')
    scene.tank(5, 2, 9)
    units['liquid_vessel'] = (scene, (4, 2, 4), (3, 2, 5), (5, 2, 7))

    scene = ponder.Structure(11, 9)
    for y in range(1, 7): ring(scene, 5, y, 5)
    pipe(scene, 7, 1, 5, 'east', 'west')
    motor_pump(scene, 8, 1, 5, 'west')
    scene.block(9, 1, 5, NS + 'gas_receiver', axis='x', bottom='true', top='true', shape='plain')
    pipe(scene, 3, 1, 5, 'east', 'west')
    motor_pump(scene, 2, 1, 5, 'west')
    scene.block(1, 1, 5, 'create:fluid_tank', bottom='true', top='true', shape='window')
    pipe(scene, 5, 6, 3, 'north', 'south')
    motor_pump(scene, 5, 6, 2, 'south', drive_forward=True)
    scene.block(5, 6, 1, 'create:fluid_tank', bottom='true', top='true', shape='window')
    units['resin_absorber'] = (scene, (5, 1, 5), (7, 1, 5), (3, 1, 5))

    scene = ponder.Structure(11, 7)
    scene.block(5, 2, 5, NS + 'lidded_vessel', bottom='true', hatch='none', open='false', drive='false', axis='y', facing='north', ladder='true', north='true', south='true', east='true', west='true')
    scene.burner(5, 1, 5)
    units['heating'] = (scene, (5, 2, 5), (5, 2, 5), (5, 3, 5))

    scene = ponder.Structure(11, 8)
    scene.burner(5, 1, 5)
    scene.block(5, 2, 5, 'create:basin', facing='down')
    scene.block(5, 4, 5, 'create:mechanical_mixer')
    scene.block(4, 4, 5, 'create:cogwheel', axis='y', waterlogged='false')
    scene.block(4, 5, 5, 'create:creative_motor', facing='down')
    scene.block(5, 2, 4, 'create:andesite_funnel', facing='north', extracting='false', powered='false')
    pipe(scene, 4, 2, 5, 'east', 'west');motor_pump(scene, 3, 2, 5, 'east')
    scene.block(2, 2, 5, 'create:fluid_tank', bottom='true', top='true', shape='window')
    units['mixing'] = (scene, (5, 2, 5), (5, 2, 4), (5, 2, 5))

    scene = ponder.Structure(11, 6)
    scene.block(5, 1, 5, 'create:depot', waterlogged='false')
    scene.block(5, 3, 5, 'create:mechanical_press', facing='north')
    scene.block(4, 3, 5, 'create:creative_motor', facing='east')
    units['pressing'] = (scene, (5, 3, 5), (5, 1, 5), (5, 1, 5))

    scene = ponder.Structure(11, 6)
    for x in (4, 6):
        scene.block(x, 2, 5, 'create:crushing_wheel', axis='z')
        scene.block(x, 2, 4, 'create:creative_motor', facing='south')
    scene.block(5, 3, 5, 'create:chute', facing='down', shape='normal', waterlogged='false')
    scene.block(5, 1, 5, 'create:depot', waterlogged='false')
    units['crushing'] = (scene, (4, 2, 5), (5, 4, 5), (5, 1, 5))

    scene = ponder.Structure(11, 5)
    scene.block(5, 2, 5, 'create:millstone')
    scene.block(5, 1, 5, 'create:creative_motor', facing='up')
    scene.block(7, 1, 5, 'create:depot', waterlogged='false')
    units['milling'] = (scene, (5, 2, 5), (5, 3, 5), (7, 1, 5))

    scene = ponder.Structure(11, 4)
    scene.block(5, 1, 5, 'minecraft:crafting_table')
    scene.block(3, 1, 5, 'create:depot', waterlogged='false')
    scene.block(7, 1, 5, 'create:depot', waterlogged='false')
    units['crafting'] = (scene, (5, 1, 5), (3, 1, 5), (7, 1, 5))

    scene = ponder.Structure(11, 6)
    scene.block(5, 1, 5, 'create:depot', waterlogged='false')
    scene.block(5, 3, 5, 'create:deployer', facing='down', axis_along_first='true')
    scene.block(6, 3, 5, 'create:creative_motor', facing='west')
    units['deploying'] = (scene, (5, 3, 5), (5, 1, 5), (5, 1, 5))

    from generate_electrolysis_ponder import unit
    units['electrolyzer'] = unit()


    scene = ponder.Structure(11, 7)
    radiator(scene, 5, 4)
    motor_pump(scene, 5, 3, 2, 'south');motor_pump(scene, 5, 3, 6, 'south', drive_forward=True)
    for z in (1, 7): scene.block(5, 3, z, NS + 'stainless_steel_tank', bottom='true', top='true', shape='window')
    units['cooling'] = (scene, (5, 3, 3), (5, 3, 2), (5, 3, 6))

    scene = ponder.Structure(7, 6)
    compressor(scene, 2, 2, 3)
    pipe(scene, 2, 3, 3, 'up', 'down');pipe(scene, 2, 4, 3, 'west', 'down')
    motor_pump(scene, 1, 4, 3, 'east')
    scene.block(0, 4, 3, 'create:nozzle', facing='west')
    pipe(scene, 2, 1, 3, 'up', 'west');motor_pump(scene, 1, 1, 3, 'west')
    scene.block(0, 1, 3, NS + 'gas_receiver', axis='x', bottom='true', top='true', shape='plain')
    units['compressor'] = (scene, (3, 2, 3), (2, 3, 3), (2, 1, 3))

    from generate_refrigeration import exchanger
    scene = ponder.Structure(11, 7)
    exchanger(scene, 4, 1, 5)
    # The tube side through the end faces at the bottom, the shell side down into the top of each end.
    pipe(scene, 3, 1, 5, 'east', 'west');pipe(scene, 7, 1, 5, 'east', 'west')
    for x, sides in ((4, ('down', 'west')), (3, ('east', 'west')), (6, ('down', 'east')), (7, ('east', 'west'))):
        scene.block(x, 3, 5, NS + 'stainless_steel_pipe', waterlogged='false', **{side: str(side in sides).lower() for side in ponder.DIRECTIONS})
    for y in (1, 3):
        for x in (2, 8): motor_pump(scene, x, y, 5, 'east')
        for x in (1, 9): scene.block(x, y, 5, NS + ('cryogenic_tank' if y == 3 else 'stainless_steel_tank'), bottom='true', top='true', shape='window')
    units['heat_exchanger'] = (scene, (4, 1, 5), (3, 1, 5), (7, 1, 5))

    return units


def compressor(scene, x, y, z):
    scene.block(x, y, z, NS + 'reciprocating_cylinder')
    scene.block(x + 1, y, z, NS + 'reciprocating_compressor', face='wall', facing='east', waterlogged='false')
    for dz in (-1, 0, 1): scene.block(x + 3, y, z + dz, NS + 'reciprocating_drive_shaft' if dz == 0 else 'create:shaft', axis='z', waterlogged='false')
    scene.block(x + 3, y, z - 2, 'create:creative_motor', facing='south')


def radiator(scene, x, z):
    from generate_process_tutorials import shroud
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            for y in range(2, 5):
                scene.block(x + dx, y, z + dz, NS + 'cooler_radiator', facing='south' if dx == 0 and dz == 1 and y == 3 else 'north',
                    section=('bottom', 'middle', 'top')[y - 2], drive=str(dx == 0 and dz == 0).lower(), outlet=str(y == 2).lower(),
                    port=str(y == 3 and dx == 0 and abs(dz) == 1).lower())
    scene.block(x, 1, z, 'create:creative_motor', facing='up')
    scene.block(x, 5, z, NS + 'cooling_fan')
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz: scene.block(x + dx, 5, z + dz, NS + 'cooling_fan_shroud', **shroud(-dx, -dz))


def load_units():
    units = {}
    scene = ponder.Structure(11, 7)
    scene.block(5, 2, 5, NS + 'lidded_vessel', bottom='true', hatch='none', open='false', drive='false', axis='y', facing='north', ladder='true', north='true', south='true', east='true', west='true')
    scene.burner(5, 1, 5)
    units['heating'] = (scene, (5, 2, 5), (5, 2, 5), (5, 3, 5))

    for kind, (path, machine, input_at, output_at) in TEMPLATES.items():
        source = TEMPLATE_DATA / (path[9:] + '.nbt') if path.startswith('template/') else ASSETS / 'ponder' / (path + '.nbt')
        units[kind] = (nbtlib.load(source), machine, input_at, output_at)
    for kind, (scene, machine, input_at, output_at) in simple_units().items():
        old = ponder.OUT;ponder.OUT = TEMPLATE_DATA;scene.save(kind);ponder.OUT = old
        units[kind] = (nbtlib.load(TEMPLATE_DATA / (kind + '.nbt')), machine, input_at, output_at)
        (TEMPLATE_DATA / (kind + '.nbt')).unlink()
    heights={'operation:air_separation':14,'process:separate_nitric_oxide':2,
        'process:distill_crude':5,'process:separate_olefins':2,'process:separate_hydrotreater_effluent':3,
        'process:separate_hydrocracker_effluent':4,'process:recover_carbon_monoxide':2,'process:distill_slime_coolant':2}
    for recipe,height in heights.items():
        scene=column_unit(height,recipe=='operation:air_separation',recipe=='process:recover_carbon_monoxide')
        old=ponder.OUT;ponder.OUT=TEMPLATE_DATA;scene.save('_column');ponder.OUT=old
        units['column:'+recipe]=(nbtlib.load(TEMPLATE_DATA/'_column.nbt'),(5,1,5),(7,min(3,height),5),(5,height,4))
        (TEMPLATE_DATA/'_column.nbt').unlink()
    return units


def column_unit(height,air,single_cut=False):
    scene=ponder.Structure(11,height+2)
    layers=(1,10,14) if air else (height,) if single_cut else tuple(range(1,height+1))
    for y in range(1,height+1):
        for x in range(2):
            for z in range(2):
                tags=[ponder.string('id',NS+'fractionating_column'),ponder.named(3,'Width',ponder.integer(2)),
                    ponder.named(3,'Height',ponder.integer(height))]
                if x or z or y!=1:tags.append(ponder.named(4,'Controller',struct.pack('>q',(5<<38)|(5<<12)|1)))
                scene.block(5+x,y,5+z,NS+'fractionating_column',nbt=tags,width='2',cell=str(z*3+x),cut='0',
                    inlet=str(int(x==1 and z==0 and y==min(3,height))),
                    section='base' if y==1 else 'top' if y==height else 'service' if y==2 else 'body')
    for index,y in enumerate(layers):
        scene.filter(5,y,4,'north');motor_pump(scene,5,y,3,'north')
        scene.filter(5,y,2,'north')
        z=1 if index%2==0 else 0
        if z==0:scene.filter(5,y,1,'north')
        scene.block(5,y,z,'create:fluid_tank',bottom='true',top='true',shape='window')
    y=min(3,height)
    pipe(scene,7,y,5,'east','west');motor_pump(scene,8,y,5,'west')
    scene.block(10,y,5,'create:fluid_tank',bottom='true',top='true',shape='window')
    pipe(scene,9,y,5,'east','west')
    return scene


def unit_key(stage):
    if stage['recipe'] == NS + 'pressure_reaction/flame_retardant_pellet':
        return 'resin_vessel'
    if stage['recipe'] in (NS + 'pressure_reaction/trimethyl_phosphite_rearrangement', NS + 'pressure_reaction/methylphosphonate_filtration',
                           NS + 'pressure_reaction/prepare_slime_coolant'):
        return 'liquid_vessel'
    if stage['recipe'] == 'process:wash_resin_process_vapor':
        return 'resin_absorber'
    return 'column:'+stage['recipe'] if stage['kind']=='column' else stage['kind']


ROUTES = []


def step(kind, recipe, english=None, japanese=None, chinese=None, item=None, feed=None, target=None, input_at=None, output_at=None):
    if recipe and ':' not in recipe: recipe = NS + recipe
    return {'kind': kind, 'recipe': recipe, 'text': (english, japanese, chinese), 'item': item, 'feed': feed,
        'target': target, 'input': input_at, 'output': output_at}


def route(name, titles, components, steps, family=None):
    captions = json.loads((ROOT / 'tools/data/ponder_lessons.json').read_text())
    for index, stage in enumerate(steps, 1):
        text = captions.get('guide.manufacturing_' + name + '.step_' + str(index))
        if text: stage['text'] = tuple(text[code].split('\n%s → %s')[0] for code in ('en_us', 'ja_jp', 'zh_cn'))
    ROUTES.append({'id': 'manufacturing_' + name, 'family': family or name, 'titles': titles, 'components': components, 'steps': steps})


def generate(selected=None):
    entries = [entry for entry in ROUTES if not selected or entry["id"] in selected]
    if selected and len(entries) != len(selected): raise ValueError("Unknown manufacturing chapter")
    units = load_units()
    catalog_path = ASSETS / 'ponder/tutorials.json'
    original = json.loads(catalog_path.read_text())
    catalog = [entry for entry in original if entry['id'] not in {route['id'] for route in entries} and (selected or entry['id'] not in RETIRED and not entry['id'].startswith('manufacturing_'))]
    locales = {name: json.loads((ASSETS / 'lang' / (name + '.json')).read_text()) for name in ('en_us', 'ja_jp', 'zh_cn')}
    for language in ([] if selected else locales.values()):
        for key in list(language):
            if any(key.startswith('create_refinery_n_reactors.ponder.shared.guide.' + old + '.') or key == 'create_refinery_n_reactors.ponder.plant_' + old + '.header' for old in RETIRED): language.pop(key)
            if any(key.startswith('create_refinery_n_reactors.ponder.shared.' + old + '.') or key == 'create_refinery_n_reactors.ponder.' + old + '.header' for old in RETIRED_CUSTOM): language.pop(key)
    # Components of chapters in one family deliberately open the same ordered set of chapters.
    family_components = {}
    for entry in ROUTES: family_components.setdefault(entry['family'], set()).update(entry['components'])
    for entry in entries:
        kinds = list(dict.fromkeys(unit_key(stage) for stage in entry['steps']))
        offsets = {kind: index * 32 for index, kind in enumerate(kinds)}
        width = max(offsets[kind] + int(units[kind][0]['size'][0]) for kind in kinds)
        depth = max(int(units[kind][0]['size'][2]) for kind in kinds)
        height = max(int(units[kind][0]['size'][1]) for kind in kinds)
        structure = nbtlib.File({'DataVersion': nbtlib.Int(3955), 'size': nbtlib.List[nbtlib.Int]([width, height, depth]),
            'palette': nbtlib.List[nbtlib.Compound](), 'blocks': nbtlib.List[nbtlib.Compound](), 'entities': nbtlib.List[nbtlib.Compound]()})
        for kind in kinds: copy_unit(units[kind][0], structure, offsets[kind])
        path = ASSETS / 'ponder/plant' / (entry['id'] + '.nbt');structure.save(path, gzipped=True)
        with gzip.open(path, 'rb') as stream: payload = stream.read()
        path.write_bytes(gzip.compress(payload, mtime=0))
        tutorial = {'id': entry['id'], 'family': entry['family'], 'size': int(units[kinds[0]][0]['size'][0]), 'height': height,
            'components': sorted(family_components[entry['family']]), 'steps': []}
        for index, stage in enumerate(entry['steps']):
            unit=unit_key(stage)
            source, machine, input_at, output_at = units[unit]
            key = 'step_' + str(index + 1)
            tutorial['steps'].append({'key': key, 'target': list(stage['target'] or machine), **({'item': stage['item'] if ':' in stage['item'] else NS + stage['item']} if stage['item'] else {}),
                'process': {'kind': stage['kind'], **({'feed': stage['feed'] if ':' in stage['feed'] else NS + stage['feed']} if stage['feed'] else {}), 'recipe': stage['recipe'], 'offset': offsets[unit], 'width': int(source['size'][0]),
                    'height': int(source['size'][1]), 'machine': list(machine), 'input': list(stage['input'] or input_at), 'output': list(stage['output'] or output_at)}})
            for language_index, language in enumerate(locales.values()):
                caption = stage['text'][language_index]
                if stage['recipe']: caption += '\n%s → %s'
                language['create_refinery_n_reactors.ponder.shared.guide.' + entry['id'] + '.' + key] = caption
        catalog.append(tutorial)
        for language_index, language in enumerate(locales.values()):
            language['create_refinery_n_reactors.ponder.shared.guide.' + entry['id'] + '.title'] = entry['titles'][language_index]
            language['create_refinery_n_reactors.ponder.plant_' + entry['id'] + '.header'] = entry['titles'][language_index]
    for old in ([] if selected else RETIRED | RETIRED_CUSTOM):
        path = ASSETS / 'ponder/plant' / (old + '.nbt')
        if path.exists(): path.unlink()
    if selected:
        positions = {entry['id']: index for index, entry in enumerate(original)}
        catalog.sort(key=lambda entry: positions.get(entry['id'], len(positions)))
    write_json(catalog_path, catalog)
    for locale, strings in locales.items(): write_json(ASSETS / 'lang' / (locale + '.json'), strings)
    from generate_ponder_language import generate as generate_language
    generate_language()
    print('Generated', len(entries), 'manufacturing storyboards in', len({entry['family'] for entry in entries}), 'families')
# Shared utilities and bulk chemicals.
route('air', ('Separating industrial gases from air', '空気から工業用ガスを分離する', '从空气分离工业气体'),
    ['stainless_steel_pump', 'air_gas_bucket', 'nitrogen_gas_bucket', 'oxygen_gas_bucket', 'liquid_oxygen_bucket', 'argon_gas_bucket', 'liquid_nitrogen_bucket'], [
    step('crafting', 'stainless_steel_pump', 'Fit a Create Nozzle facing open air and run a pump into the top of the compressor.', 'Createのノズルを空気に向け、回転するポンプで圧縮機の上面へ空気を送ります。', '让 Create 喷嘴的开口朝向空气，再用泵从压缩机顶部送入空气。'),
    step('compressor', None, 'Mount the engine on the cylinder and drive its crank shaft two blocks out. Air enters the cylinder top and compressed gas leaves the bottom. Set pressure with a wrench.', 'シリンダーに駆動部を付け、2マス先のクランク軸へ回転を入力します。空気はシリンダー上面から入り、圧縮後は下面へ出ます。レンチで圧力を設定します。', '把驱动部装在气缸上，驱动两格外的曲轴。空气从气缸顶面进入，压缩后从底面排出，用扳手设定压力。', 'air_gas_bucket'),
    step('compressor', None, 'Cool the hot compressor discharge in a driven radiator. The fractionation feed must remain at least 6 bar and no warmer than 40 °C.', '圧縮後の熱い空気を駆動したラジエーターで冷やします。分留塔へ6 bar以上・40℃以下で送ります。', '用有动力的散热器冷却压缩后的空气，以至少6 bar、最高40℃送入分馏塔。', 'air_gas_bucket'),
    step('column', 'operation:air_separation', 'Build 2×2×14 and feed the east side of layer three. North outlets: liquid oxygen at 1, Ar at 10, N2 at 14. Amounts are gas-equivalent.', '2×2・14段に組み、東面3段目から給気します。北面出口は1段目が液体酸素、10段目がAr、14段目がN2です。量は気体換算です。', '搭建2×2×14塔，第三层东面进气，北面第一层出液氧、第十层Ar、第十四层N2，数量为气体当量。'),
    step('heat_exchanger', 'operation:exchange_olefins_oxygen', 'Warm the collected liquid oxygen in a heat exchanger when gaseous oxygen is required. Store cold products in cryogenic tanks and pipes. Keep every tower outlet available.', '酸素の気体が必要なら、液体酸素を熱交換器で温めて気化させます。低温の製品は極低温タンクと配管へ送り、分留塔の全出口を確保します。', '需要氧气时用换热器加热液氧使其气化，低温产品使用低温储罐与管道，保持塔的全部出口畅通。', 'oxygen_gas_bucket'),
    step('cold_box', 'operation:liquefy_nitrogen', 'Run nitrogen and cold He through the tube side and the shell side of the exchanger. Use cryogenic pipes for liquid nitrogen and return the He for recovery.', '熱交換器の管側と胴側へ窒素と低温Heを分けて流します。液体窒素は保冷配管へ、Heは回収側へ戻します。', '换热器管程与壳程分别通氮气和低温He，液氮用低温管线回收，He送回回收侧。', 'liquid_nitrogen_bucket'),
])

route('hydrogen', ('Making hydrogen by water electrolysis', '水の電解で水素を作る', '电解水制取氢气'), ['hydrogen_gas_bucket', 'warm_water_bucket'], [
    step('electrolyzer', None, 'Build vertical 2x2 electrolysis cells, one to eight blocks long, with a 2x2 controller at one end. Load electrodes through the tops of its upper units, close the cell hatches and wire both upper-right terminals.', '縦2×2の電解セルを長さ1～8マスに組み、片端に2×2制御盤を付けます。制御盤上段の上面から極板を入れ、セルのハッチを閉じて右上の2端子へ給電します。', '搭建竖直2×2、长1～8格的电解单元，在一端安装2×2控制器。从控制器上排顶部装入电极，关闭单元检修口并给右上两个端子供电。', 'asbestos_wrapped_electrode'),
    step('electrolyzer', 'operation:water_electrolysis', 'Supply water at the lower side, close the hatches and press START. Amounts below are per plate over 100 ticks.', '下段側面へ給水し、STARTを押します。ハッチは閉じます。下の量は極板1枚・100 tick分です。', '下层侧面供水，关盖后按START，下方数量按每块极板100 tick计算。'),
    step('electrolyzer', None, 'The upper low-X column gives hydrogen, and the opposite column oxygen. Use separate pumped pipes and receivers. A full output buffer stops consumption of feed and electricity.', '上段のX座標が小さい列から水素、反対の列から酸素を回収します。別々のポンプと貯槽につなぎ、製品が満杯なら材料と電力の消費を止めます。', '上层较小X列输出氢气，另一列输出氧气。分别接泵与储罐；产物满载时停止消耗原料和电力。', 'hydrogen_gas_bucket'),
])

route('ammonia', ('From hydrogen and nitrogen to ammonia', '水素と窒素からアンモニアを作る', '由氢氮制取氨'),
    ['iron_catalyst', 'ammonia_gas_bucket', 'liquid_ammonia_bucket'], [
    step('vessel', 'pressure_reaction/synthesis_gas', 'Install impellers in every layer of a 3×3 vessel, close the hatch and drive only its bottom centre at 32 RPM or more. Combine the hydrogen and nitrogen supplies.', '3×3槽の各段に撹拌翼を入れ、ハッチを閉じ、底面中央へ32 RPM以上を入力します。水素と窒素を合成ガスにします。', '在3×3槽每层装入搅拌叶并关盖，仅从底部中心输入至少32 RPM，将氢氮混合成合成气。'),
    step('heating', None, 'Compress, cool and preheat synthesis gas. Check temperature and pressure on the gauges.', '合成ガスを圧縮・冷却・予熱し、計器で温度と圧力を確認します。', '合成气依次压缩、冷却、预热，用仪表检查温度和压力。', None),
    step('catalytic', 'operation:haber_bosch', "Open a reaction tower's roof hatch, fill its baskets with iron catalyst and close it. Pump the compressed feed into a bottom-layer side, heat it from below and collect the hot mixture from the other bottom side.", '反応塔の天井ハッチを開けてバスケットに鉄触媒を入れ、閉じます。加圧原料を最下段の側面へ送り、下から加熱して、反対側の最下段側面から高温混合ガスを取り出します。', '打开反应塔顶部舱口，向篮中装入铁催化剂后关闭。将加压原料泵入底层侧面，从下方加热，并从另一侧底层侧面取出高温混合气。', 'iron_catalyst'),
    step('cooling', None, 'Cool the hot synthesis mixture before separation. Ammonia formation depends on the real bed composition, temperature and pressure. Supply fresh feed and recycle the unreacted gases.', '高温の合成混合ガスを分離前に冷却します。アンモニアの割合は触媒床の組成・温度・圧力で決まります。新しい原料を補い、未反応ガスを循環します。', '分离前冷却高温合成混合气，氨比例由床层实际组成和温压决定，补充新料并循环未反应气体。', None),
    step('separator', 'process:separate_ammonia', 'Use a 1×1 gas-liquid separator at least two layers high. Pump liquid ammonia from below and recycle gas from the top.', '1×1で2段以上の気液分離器で分けます。底面から液体アンモニア、上面から循環ガスをポンプで回収します。', '用1×1、至少两层的气液分离器处理。从底部泵出液氨，从顶部回收循环气。'),
])

route('nitric_fertilizer', ('From ammonia to nitric acid and fertilizers', 'アンモニアから硝酸と肥料を作る', '从氨制取硝酸与肥料'),
    ['nitric_oxide_bucket', 'nitrogen_dioxide_bucket', 'nitric_acid_bucket', 'ammonium_nitrate', 'ammonium_sulfate'], [
    step('catalytic', 'process:oxidize_ammonia', "Load an oxidation catalyst into a reaction tower's baskets. Feed ammonia and oxygen into a bottom-layer side; collect the hot mixture from the bottom layer.", '反応塔のバスケットに酸化触媒を入れます。アンモニアと酸素を最下段の側面へ送り、高温混合物を最下段から回収します。', '向反应塔的篮中装入氧化催化剂，从底层侧面送入氨和氧，从底层回收高温混合物。'),
    step('cooling', None, 'Cool that mixture in a driven radiator before feeding the tower. Collect the cooled mixture from the opposite side outlet.', '混合物を駆動したラジエーターで冷やして分留塔へ送ります。冷却後の混合物を反対側の側面出口へ取り出します。', '用有动力的散热器冷却混合物后送塔，从对侧侧面出口取出冷却后的混合物。', None),
    step('column', 'process:separate_nitric_oxide', 'A 2×2, 2-layer tower can separate this mixture into nitric oxide and water. This example keeps the existing 14-layer tower. Route the nitric oxide to its next oxidation step.', '一酸化窒素と水の分離には2×2・最低2段あれば十分です。図は既設の14段塔です。一酸化窒素を次の酸化工程へ送ります。', '一氧化氮和水需2×2截面、最低2层即可分离，图示沿用现有14层塔。将一氧化氮送入下一氧化工序。'),
    step('catalytic', 'process:oxidize_nitric_oxide', 'Feed nitric oxide and oxygen into a reaction tower to obtain nitrogen dioxide. It needs the baskets but no catalyst.', '反応塔で一酸化窒素と酸素から二酸化窒素を作ります。バスケットは要りますが、触媒は要りません。', '在反应塔中用一氧化氮与氧气制取二氧化氮。需要篮，但不需要催化剂。'),
    step('absorber', 'process:absorb_nitric_acid', 'Stack an absorption tower four to eight storeys high: NO2 in at the bottom, water at the top. Collect acid at the bottom; return the NO from the top to oxidation.', '吸収塔を4～8段積み、NO2を下、水を上から入れます。硝酸は下から回収し、上から出るNOを酸化側へ戻します。', '吸收塔堆叠4～8节，NO2从下、水从上送入。硝酸从下部回收，顶部排出的NO返回氧化工序。'),
    step('vessel', 'pressure_reaction/ammonium_nitrate', 'Mix the recovered nitric acid with ammonia in an impeller vessel to make ammonium nitrate. Extract the solid through a funnel.', '回収した硝酸を撹拌翼入り槽でアンモニアと反応させ、硝酸アンモニウムをファンネルで取り出します。', '在搅拌槽中用硝酸与氨制取硝酸铵，经漏斗取出固体。'),
    step('vessel', 'pressure_reaction/ammonium_sulfate', 'Sulfuric acid gives the alternative ammonium sulfate fertilizer in the same kind of vessel. Empty the previous batch before switching feeds. Use either one on a crop to grow the 3×3 crops around it; a deployer spreads it the same way.', '同じ形式の槽で、硫酸を使うと硫酸アンモニウム肥料になります。原料を切り替える前に前のバッチを回収します。どちらの肥料も、作物に使うと周囲3×3の作物がまとめて育ちます。デプロイヤーでも同じように使えます。', '同类槽中改用硫酸可制硫酸铵肥料，换料前排空上一批。两种肥料用在作物上会使周围3×3作物一起生长，机械手也能同样使用。'),
])

route('sulfuric_acid', ('From sulfur and recovered gas to sulfuric acid', '硫黄と回収ガスから硫酸を作る', '从硫与回收气体制取硫酸'),
    ['sulfur_dust', 'sulfuric_acid_bucket', 'charred_wood'], [
    step('crushing', 'create:crushing/netherrack', 'Crush netherrack and collect sulfur dust with the other outputs.', 'ネザーラックを粉砕し、ほかの生成物と一緒に硫黄粉を回収します。', '粉碎下界岩，将硫粉与其他产物一并回收。'),
    step('vessel', 'pressure_reaction/sulfur_dioxide_stirred', 'Feed sulfur and oxygen into a heated impeller vessel.', '硫黄と酸素を、加熱した撹拌翼入り槽へ供給します。', '将硫与氧气送入加热的搅拌槽。'),
    step('catalytic', 'process:oxidize_sulfur_dioxide', 'Send the gas to a reaction tower holding an oxidation catalyst. It needs no rotation.', '気体を酸化触媒入りの反応塔へ送ります。回転は要りません。', '把气体送入装有氧化催化剂的反应塔，无需旋转。'),
    step('absorber', 'process:absorb_sulfuric_acid', 'Absorb the sulfur trioxide into water in an absorption tower: gas at the bottom, water at the top. Recover the acid into a corrosion-resistant tank.', '吸収塔で三酸化硫黄を水に吸収させます。気体は下、水は上から入れます。硫酸は耐食タンクへ回収します。', '在吸收塔中用水吸收三氧化硫：气体从下、水从上进入。硫酸收集到耐腐蚀储罐。'),
    step('vessel', 'pressure_reaction/sulfur_dioxide_from_hydrogen_sulfide', 'Hydrogen sulfide recovered from fuel refining can supply this line too. Oxidize one share to sulfur dioxide.', '燃料精製から回収した硫化水素も原料になります。その一部を酸化して二酸化硫黄にします。', '燃料精制回收的硫化氢也可作原料，将其中一部分氧化为二氧化硫。'),
    step('vessel', 'pressure_reaction/sulfur_from_hydrogen_sulfide', 'React it with the remaining hydrogen sulfide to reclaim sulfur. Send the sulfur back to the acid or rubber line.', '残りの硫化水素と反応させて硫黄を回収し、硫酸やゴムの製造へ戻します。', '与剩余硫化氢反应回收硫，再送回硫酸或橡胶生产线。'),
    step('carbonization', None, 'Apply sulfuric acid to the wood and collect the carbonized block after the reaction. The acid supply comes from the same manufacturing family.', '同じ製造系列で用意した硫酸を木材へ接触させ、反応後に炭化木材を回収します。', '用同一制造系列制得的硫酸接触木材，反应后回收炭化木材。', 'charred_wood', feed='sulfuric_acid_bucket'),
    step('vessel', 'pressure_reaction/dilute_sulfuric_acid_iron_catalysed_oxygen', 'Without a catalyst bed: blow sulfur dioxide and oxygen, or air, into water with an iron nugget or iron oxide in an impeller vessel. The dissolved iron carries the oxygen, and dilute sulfuric acid forms cold.', '触媒床を使わない方法：撹拌翼入り槽の水に鉄ナゲットか酸化鉄を入れ、二酸化硫黄と酸素（または空気）を吹き込みます。溶けた鉄が酸素を運び、加熱なしで希硫酸になります。', '不用催化剂床的方法：在搅拌槽的水中放入铁粒或氧化铁，通入二氧化硫和氧气（或空气）。溶解的铁传递氧，无需加热即生成稀硫酸。'),
    step('absorber', 'process:absorb_sulfurous_acid', 'Or absorb the sulfur dioxide itself into water in an absorption tower to make sulfurous acid.', 'または吸収塔で二酸化硫黄そのものを水に吸収させ、亜硫酸にします。', '或在吸收塔中让二氧化硫直接溶于水，制成亚硫酸。'),
    step('electrolyzer', 'operation:sulfurous_electrolysis', 'Electrolyse the sulfurous acid: hydrogen leaves the cathode side and sulfuric acid the far end, on two fifths of the power water needs. Amounts are per plate over 100 ticks.', '亜硫酸を電気分解します。水素は陰極側、硫酸は奥の端面から出て、電力は水の電解の4割で済みます。量は極板1枚・100 tickあたりです。', '电解亚硫酸：氢气从阴极侧排出，硫酸从远端排出，耗电仅为电解水的四成。数量为每片电极100 tick。'),
])

route('chlor_alkali', ('From salt to chlorine, soda and cleaning products', '塩から塩素・ソーダと洗浄剤を作る', '从盐制取氯、烧碱与清洁用品'),
    ['halite_ore', 'deepslate_halite_ore', 'salt', 'brine_bucket', 'caustic_soda_solution_bucket', 'hydrochloric_acid_bucket', 'sodium_hypochlorite_solution_bucket', 'soap', 'asbestos'], [
    step('crushing', 'crushing/halite_ore', 'Crush halite ore into salt. A sunlit open tank can also evaporate water to collect salt.', '岩塩鉱石を粉砕して塩にします。日光が当たる開放タンクで水を蒸発させても塩を集められます。', '粉碎岩盐矿石取得盐，也可在日照下的开放槽中蒸发水收盐。'),
    step('mixing', 'mixing/brine', 'Dissolve the salt in water with a Mechanical Mixer over a basin.', '鉢とメカニカルミキサーで、塩を水に溶かします。', '用工作盆和机械搅拌器把盐溶于水。'),
    step('electrolyzer', 'operation:brine_electrolysis', 'Feed brine below, wire the controller and press START. Collect hydrogen and chlorine above, caustic soda below. Amounts are per plate over 100 ticks.', '下段へ塩水を送り、配線してSTARTを押します。上段から水素・塩素、下段から苛性ソーダを回収します。量は極板1枚・100 tick分です。', '下部供盐水，接线后按START，上部收氢氯、下部收苛性钠，数量按每块极板100 tick计算。'),
    step('crushing', 'crushing/diorite_to_asbestos', 'Crush diorite for asbestos. Insert the diaphragm through an open cell hatch to recover a quarter more soda; one asbestos lasts for 10000 mB brine.', '閃緑岩を粉砕してアスベストを得ます。電解セルの開いたハッチへ隔膜を入れると苛性ソーダが4分の1増え、塩水10000 mBごとに1個を消費します。', '粉碎闪长岩得到石棉。从打开的电解单元检修口装入隔膜可增产四分之一烧碱，每10000 mB盐水消耗一份石棉。'),
    step('vessel', 'pressure_reaction/hydrogen_chloride', 'Combine the separated hydrogen and chlorine in an impeller vessel.', '分離した水素と塩素を撹拌翼入り槽で反応させます。', '在搅拌槽中使分离出的氢与氯反应。'),
    step('absorber', 'process:absorb_hydrochloric_acid', 'Absorb the hydrogen chloride into water in an absorption tower: gas at the bottom, water at the top. Keep the acid in suitable pipes and tanks.', '吸収塔で塩化水素を水に吸収させ、塩酸にします。気体は下、水は上から入れます。対応する材質の配管とタンクへ送ります。', '在吸收塔中用水吸收氯化氢制取盐酸：气体从下、水从上进入。使用适合材质的管道和储罐。'),
    step('vessel', 'pressure_reaction/sodium_hypochlorite_solution', 'Another branch combines chlorine with soda to make bleach. The existing basin recipes bleach wool, carpets and beds.', '別の分岐では塩素と苛性ソーダから漂白液を作ります。既存の鉢レシピで羊毛・カーペット・ベッドを漂白できます。', '另一支路用氯与烧碱制漂白液，已有工作盆配方可漂白羊毛、地毯和床。'),
    step('mixing', 'mixing/soap', 'Heat plant oil and soda under a Mechanical Mixer to make soap.', '鉢を加熱し、植物油と苛性ソーダをメカニカルミキサーで混ぜて石けんを作ります。', '加热工作盆，用机械搅拌器混合植物油与烧碱制皂。'),
])

route('petrochemical_feedstocks', ('Polymers 1 — oil to separated feedstocks', '樹脂の製造1　原油から原料ガスまで', '树脂制造1　原油到分离原料'),
    ['createdieselgenerators:crude_oil_bucket', 'createdieselgenerators:gasoline_bucket', 'naphtha_bucket', 'lubricating_oil_bucket', 'naphtha_bucket', 'cooled_olefins_bucket', 'ethylene_bucket', 'propylene_bucket'], [
    step('heating', None, 'Heat crude oil in a lidded vessel before distillation. The tower accepts crude at 620 K or above.', 'ふた付き槽で原油を予熱します。分留塔は620 K以上の原油を受け入れます。', '在带盖槽内预热原油，分馏塔接受620 K以上的原油。', 'createdieselgenerators:crude_oil_bucket'),
    step('column', 'process:distill_crude', 'Crude oil needs a 2×2 column at least five layers high. In this 14-layer example, feed east at layer three and collect each north outlet separately.', '原油は2×2・最低5段で分留します。図の14段塔では東面3段目へ送り、北面の各出口を別タンクへつなぎます。', '原油使用2×2、至少五层塔。图示十四层塔从第三层东面供料，北侧各出口分别接罐。'),
    step('heating', None, 'Heat ordinary naphtha in a lidded vessel to 720 K. Supply cracking water separately.', '通常のナフサをふた付き槽で720 Kまで加熱し、分解用の水を別配管から供給します。', '将普通石脑油在带盖槽内加热至720 K，另一路供给裂解所需的水。', 'naphtha_bucket'),
    step('catalytic', 'process:crack_naphtha', 'Load a cracking catalyst into a reaction tower. Feed hot naphtha and water into a bottom-layer side; recover the hot olefin mixture from the bottom layer.', '反応塔に分解触媒を入れます。高温ナフサと水を最下段の側面へ送り、高温オレフィン混合物を最下段から回収します。', '向反应塔装入裂化催化剂，从底层侧面送入热石脑油和水，从底层回收热烯烃混合物。'),
    step('cooling', None, 'Cool the hot mixture before the next separation. Collect the cooled stream from the opposite side port and send it to the tower.', '高温混合物を次の分離前に冷やします。反対側の側面口から冷却後の流れを取り出し、分留塔へ送ります。', '下一次分离前冷却热混合物，从对侧侧面口回收冷却流并送入分馏塔。', 'cooled_olefins_bucket'),
    step('column', 'process:separate_olefins', 'Separate the cold olefins into ethylene and propylene. These feeds continue to the second chapter of this polymer family.', '冷却済みのオレフィンをエチレンとプロピレンへ分けます。この系列の次の章で、樹脂と成形品へ進みます。', '把冷烯烃分为乙烯和丙烯，接着在本系列下一章制成树脂和成型品。'),
], family='polymers')

route('polymers', ('Polymers 2 — pellets, resin and finished products', '樹脂の製造2　樹脂粒から成形品まで', '树脂制造2　树脂粒到成品'),
    ['molten_polyethylene_bucket', 'molten_polypropylene_bucket', 'polyethylene', 'polypropylene', 'plastic_sheet', 'industrial_resin', 'industrial_glass'], [
    step('heating', None, 'Heat ordinary ethylene or propylene in a lidded vessel to 480 K before polymerization.', '通常のエチレンかプロピレンをふた付き槽で480 Kまで加熱して重合します。', '在带盖槽内将普通乙烯或丙烯加热至480 K后聚合。', None),
    step('catalytic', 'process:polymerize_ethylene', "Load a polymerization catalyst into a reaction tower's baskets and collect molten polyethylene from the bottom layer.", '反応塔のバスケットに重合触媒を入れ、溶融ポリエチレンを最下段から回収します。', '向反应塔的篮中装入聚合催化剂，从底层回收熔融聚乙烯。'),
    step('catalytic', 'process:polymerize_propylene', 'The matching propylene feed gives molten polypropylene in the same type of equipment. Keep the two production batches separate.', '対応するプロピレン原料では、同じ形式の設備で溶融ポリプロピレンを作れます。2種類の製造バッチを分けて扱います。', '丙烯原料可在同类设备中制得熔融聚丙烯，两种批次分别处理。'),
    step('mixing', 'mixing/polyethylene_from_melt', 'Quench the polyethylene melt with water in a basin and recover the warm water alongside the solid polymer.', '鉢でポリエチレンの溶融物を水と混ぜ、固形の樹脂と温排水を回収します。', '在工作盆中用水冷却聚乙烯熔体，回收固体树脂与温排水。'),
    step('mixing', 'mixing/polypropylene_from_melt', 'Process polypropylene melt through its matching quench recipe.', 'ポリプロピレンの溶融物も、対応する冷却レシピで樹脂粒にします。', '聚丙烯熔体也用相应冷却配方制成树脂粒。'),
    step('pressing', 'pressing/plastic_sheet_from_polyethylene', 'Place the solid polymer on a depot under a powered Mechanical Press. Either polymer has its own plastic-sheet recipe.', '樹脂粒を駆動したメカニカルプレスの下のデポへ載せます。どちらの樹脂からも、対応レシピでプラスチック板を作れます。', '把树脂粒放在有动力的机械压力机下方置物台，两种树脂各有塑料板配方。'),
    step('mixing', 'mixing/industrial_resin', 'Combine both solid polymers with lubricating oil to make industrial resin.', '2種類の固形樹脂と潤滑油を混ぜ、工業用樹脂にします。', '将两种固体树脂与润滑油混合制成工业树脂。'),
    step('crafting', 'industrial_glass', 'Use industrial resin with glass to make industrial glass. Plastic sheets from this family also supply coated floors.', '工業用樹脂とガラスから工業用ガラスを作ります。この系列のプラスチック板は防食床にも使います。', '用工业树脂和玻璃制造工业玻璃，本系列的塑料板也用于防腐地板。'),
], family='polymers')

route('rubber', ('Making rubber and flexible products', '合成ゴムから柔らかい製品を作る', '合成橡胶与柔性制品'),
    ['synthetic_rubber', 'rubber_gasket'], [
    step('vessel', 'pressure_reaction/synthetic_rubber', 'Send separated ethylene, propylene and sulfur to a heated impeller vessel. Drive its bottom centre and extract the cured rubber through a funnel.', '分離したエチレン・プロピレンと硫黄を、加熱した撹拌槽へ送ります。底面中央を駆動し、合成ゴムをファンネルで取り出します。', '将分离出的乙烯、丙烯与硫送入加热搅拌槽，驱动底部中心，经漏斗取出合成橡胶。'),
    step('crafting', 'rubber_gasket', 'Turn the solid rubber into gaskets for the existing fluid equipment.', '固形のゴムをガスケットに加工し、既存の流体設備へ使います。', '把固体橡胶加工成流体设备所用密封垫。'),
    step('crafting', 'belt_connector_from_synthetic_rubber', 'The rubber also supplies Create belt connectors, connecting this production line to ordinary factory transport.', '合成ゴムからCreateのベルトコンベアも製作でき、工場の搬送設備へつなげられます。', '合成橡胶也能制作Create传送带，供工厂运输使用。'),
    step('crafting', 'leather_from_polymers', 'Combine the plastic sheet, rubber and string through the existing leather recipe. This finishes the shared plastic/rubber material chain.', '既存の革レシピで、プラスチック板・ゴム・糸を組み合わせます。樹脂とゴムの製造ラインが革につながります。', '按已有皮革配方组合塑料板、橡胶与线，完成塑料和橡胶到皮革的制造链。'),
])

route('pvc', ('From chlorine and ethylene to PVC', '塩素とエチレンからPVCを作る', '从氯和乙烯制成PVC'),
    ['ethylene_dichloride_bucket', 'pvc_resin', 'hazard_chestplate'], [
    step('vessel', 'pressure_reaction/ethylene_dichloride', 'Bring ethylene from the oil line and chlorine from brine electrolysis to an impeller vessel.', '石油ラインのエチレンと、塩水電解の塩素を撹拌槽へ送ります。', '把石油线的乙烯与盐水电解的氯送入搅拌槽。'),
    step('vessel', 'pressure_reaction/vinyl_chloride', 'Superheat the next batch to produce vinyl chloride. Recover hydrogen chloride through the second product path and send it to acid production.', '次のバッチを超加熱して塩化ビニルを作ります。副生する塩化水素も別の生成物経路で回収し、塩酸製造へ送ります。', '超加热下一批以制氯乙烯，另一产物流回收氯化氢并送往盐酸生产。'),
    step('vessel', 'pressure_reaction/pvc_resin', 'Use the heated polymerization recipe in an impeller vessel and extract the solid PVC resin.', '撹拌槽で加熱重合のレシピを進め、固形のPVC樹脂を取り出します。', '在搅拌槽中执行加热聚合配方，取出固体PVC树脂。'),
    step('crafting', 'hazard_chestplate', 'Craft the recovered resin into hazard suit parts that keep corrosive splashes off.', '回収した樹脂から、腐食性の飛沫を防ぐ防護服を作ります。', '用回收的树脂制作防腐蚀飞溅的防护服。'),
])

route('fuel_refining', ('Refining diesel and cracking heavy oil', '軽油を脱硫し重油から燃料を回収する', '柴油脱硫与重油加氢裂化'),
    ['createdieselgenerators:diesel_bucket', 'heavy_oil_bucket', 'createdieselgenerators:diesel_bucket', 'hot_hydrotreater_effluent_bucket', 'cooled_hydrotreater_effluent_bucket', 'low_sulfur_diesel_bucket', 'heavy_oil_bucket', 'hot_hydrocracker_effluent_bucket', 'cooled_hydrocracker_effluent_bucket'], [
    step('heating', None, 'Heat ordinary diesel to 623 K or heavy oil to 673 K in a lidded vessel.', '通常の軽油は623 K、重油は673 Kまでふた付き槽で加熱します。', '在带盖槽内将普通柴油加热至623 K，或将重油加热至673 K。', 'createdieselgenerators:diesel_bucket'),
    step('catalytic', 'process:hydrotreat_diesel', 'Load the hydrotreating catalyst into a reaction tower and supply high-pressure hydrogen with the diesel.', '反応塔に水素化処理触媒を入れ、軽油と高圧水素を供給します。', '向反应塔装入加氢处理催化剂，与柴油一起供给高压氢。'),
    step('cooling', None, 'Cool the hot hydrotreater effluent before fractionation. Keep cooling and all product outlets available.', '高温の脱硫反応液を分留前に冷やし、冷却と全生成物の出口を維持します。', '分馏前冷却高温脱硫反应液，保持冷却与全部产物出口畅通。', 'cooled_hydrotreater_effluent_bucket'),
    step('column', 'process:separate_hydrotreater_effluent', 'The tower recovers low-sulfur diesel, hydrogen sulfide and unused hydrogen. Return hydrogen to the feed and sulfur gas to the sulfur-recovery family.', '分留塔で低硫黄軽油・硫化水素・未反応水素を回収します。水素は原料へ、硫化水素は硫黄回収の系列へ戻します。', '分馏塔回收低硫柴油、硫化氢和未反应氢，氢返回进料，硫化氢送至硫回收系列。'),
    step('catalytic', 'process:hydrocrack_heavy_oil', 'For heavy oil, change to the hydrocracking catalyst and supply hydrogen at 50 bar or more.', '重油には水素化分解触媒を使い、50 bar以上の水素を供給します。', '重油使用加氢裂化催化剂，并供给至少50 bar的氢。'),
    step('cooling', None, 'Cool the hydrocracker effluent for the separation tower.', '水素化分解反応液を冷やして分留塔へ送ります。', '冷却加氢裂化反应液后送往分馏塔。', 'cooled_hydrocracker_effluent_bucket'),
    step('column', 'process:separate_hydrocracker_effluent', 'Recover extra low-sulfur diesel and naphtha. Route naphtha into the polymer feedstock family and recover both gas products.', '追加の低硫黄軽油とナフサを回収します。ナフサは樹脂原料の系列へ送り、2種類の気体も回収します。', '回收更多低硫柴油与石脑油，石脑油送入树脂原料系列，同时回收两种气体产物。'),
])

route('antifreeze', ('Coolants 1 — making antifreeze', '冷媒の製造1　不凍液を作る', '冷媒制造1　制造防冻液'),
    ['ethylene_glycol_bucket', 'antifreeze_bucket'], [
    step('catalytic', 'process:oxidize_ethylene', 'Supply preheated ethylene and oxygen to a reaction tower holding an oxidation catalyst.', '予熱済みのエチレンと酸素を、酸化触媒入りの反応塔へ供給します。', '向装有氧化催化剂的反应塔供给预热乙烯与氧气。'),
    step('vessel', 'pressure_reaction/ethylene_glycol', 'Heat ethylene oxide with water in an impeller vessel to obtain ethylene glycol.', '撹拌槽で酸化エチレンと水を加熱し、エチレングリコールを作ります。', '在搅拌槽中加热环氧乙烷与水，制得乙二醇。'),
    step('mixing', 'mixing/antifreeze', 'Mix glycol with water. Collect the finished antifreeze for the cooling circuit.', 'グリコールを水と混ぜます。完成した不凍液を冷却回路へ回収します。', '乙二醇与水混合，收集防冻液供冷却回路使用。'),
], family='coolants')

route('refrigeration', ('Coolants 2 — refrigeration and ice', '冷媒の製造2　冷凍サイクルと製氷', '冷媒制造2　制冷循环与制冰'),
    ['liquid_chlorine_bucket', 'liquid_sulfur_dioxide_bucket', 'liquid_vinyl_chloride_bucket', 'liquid_propylene_bucket'], [
    step('cooling', 'operation:cool_ammonia', 'Compress ammonia, reject the compression heat and condense it in the radiator. Observe the actual high-side pressure with a gauge.', 'アンモニアを圧縮し、圧縮熱を捨ててラジエーターで液化します。高圧側の実際の圧力を計器で確認します。', '压缩氨，排出压缩热并在散热器中液化，用仪表查看高压侧实际压力。', 'liquid_ammonia_bucket'),
    step('heat_exchanger', 'operation:exchange_olefins_ammonia', 'Pass the liquid through the pressure-reducing valve. Expansion lowers its boiling point. The cold side evaporates in a heat exchanger while cooling the separate antifreeze loop.', '液体を減圧弁へ通すと、圧力低下で沸点が下がります。冷媒を熱交換器で蒸発させ、別系統の不凍液から熱を奪います。', '液体经减压阀降压后沸点下降，在换热器中蒸发，吸收独立防冻液回路的热量。', 'cooled_olefins_bucket'),
    step('cooling', 'operation:cool_ammonia', 'Return ammonia vapour to the compressor and warm antifreeze to the exchanger. Use separate pipes so the two fluids keep their identities.', 'アンモニア蒸気を圧縮機へ、温まった不凍液を熱交換器へ戻します。2つの流体は別配管で循環させます。', '氨蒸气返回压缩机，升温的防冻液返回换热器，两种流体分别循环。', 'liquid_ammonia_bucket'),
    step('heat_exchanger', 'operation:exchange_chlorine_ammonia', 'Send cold antifreeze through one side of the exchanger and the gas through the other. If it does not liquefy, check pressure and coolant temperature.', '冷やした不凍液を熱交換器の片側へ、液化したいガスをもう片側へ流します。液化しなければ圧力と冷媒温度を確認します。', '换热器一侧通冷防冻液，另一侧通待液化气，未液化时检查压力与冷媒温度。', 'liquid_chlorine_bucket'),
    step('vessel', 'pressure_reaction/freeze_water', 'Cool a water-filled stirred vessel with a cooling source to make ice.', '水入りの撹拌槽を、冷熱源で冷やして氷を作ります。', '用冷源冷却盛水搅拌槽以制冰。'),
], family='coolants')

route('aluminum', ('From bauxite to aluminum', 'ボーキサイトからアルミニウムを作る', '从铝土矿制取铝'),
    ['bauxite_ore', 'deepslate_bauxite_ore', 'bauxite', 'aluminium_sulfate_solution_bucket', 'sodium_aluminate_solution_bucket', 'alumina', 'aluminum_ingot', 'aluminum_sheet'], [
    step('crushing', 'crushing/bauxite_ore', 'Crush the ore to collect the mineral feed.', '鉱石を粉砕し、製錬用の鉱物を集めます。', '粉碎矿石以取得冶炼原料。'),
    step('vessel', 'pressure_reaction/aluminium_sulfate_solution', 'The sulfate route heats the mineral with sulfuric acid in an impeller vessel.', '硫酸法では、撹拌槽で鉱物と硫酸を加熱します。', '硫酸路线在搅拌槽中加热矿物与硫酸。'),
    step('vessel', 'pressure_reaction/alumina', 'Superheat the sulfate solution to obtain alumina. Recover sulfur trioxide into the sulfuric-acid line.', '硫酸塩溶液を超加熱してアルミナを得ます。三酸化硫黄を硫酸の系列へ回収します。', '超加热硫酸盐溶液得到氧化铝，三氧化硫回收到硫酸系列。'),
    step('vessel', 'pressure_reaction/sodium_aluminate_solution', 'The alternative Bayer route uses caustic soda and also returns the iron as nuggets.', '別のバイヤー法では苛性ソーダを使い、鉄も鉄塊として回収します。', '另一条拜耳路线使用烧碱，并以铁粒回收铁。'),
    step('vessel', 'pressure_reaction/alumina_from_sodium_aluminate_solution', 'Superheat the aluminate solution, collect alumina and send the recovered soda back to the previous batch.', 'アルミン酸塩溶液を超加熱し、アルミナと苛性ソーダを回収します。ソーダを前の工程へ戻します。', '超加热铝酸盐溶液，收集氧化铝并把回收烧碱返回前一工序。'),
    step('furnace', 'operation:reduce_alumina', 'Build the 5×5×3 electrolysis furnace with its anode and busbar. Feed oxide and carbon through the busbar and connect its two electrical terminals. Wait for the molten working temperature.', '5×5×3の電解炉に陽極と母線を組み、母線から酸化物と炭素を搬入します。2端子へ結線し、浴が動作温度になるまで加熱します。', '组装带阳极和母线的5×5×3电解炉，经母线送入氧化物与碳，连接两个电气端子并等熔池达到工作温度。'),
    step('pressing', 'pressing/aluminum_sheet', 'Extract the metal through a bottom face, then press ingots into sheets for factory components.', '底面から金属を取り出し、インゴットをプレスして設備用の板にします。', '从底面取出金属，再把锭压成设备用板。'),
])

route('titanium', ('From ilmenite to titanium and white pigment', 'チタン鉄鉱からチタンを作る', '从钛铁矿制取钛'),
    ['ilmenite_ore', 'deepslate_ilmenite_ore', 'ilmenite', 'titanyl_sulfate_solution_bucket', 'titanium_dioxide', 'titanium_ingot', 'titanium_sheet', 'titanium_block'], [
    step('crushing', 'crushing/ilmenite_ore', 'Crush the ore and collect ilmenite.', '鉱石を粉砕してチタン鉄鉱を集めます。', '粉碎矿石收集钛铁矿。'),
    step('vessel', 'pressure_reaction/titanyl_sulfate_solution', 'Heat the mineral with sulfuric acid in an impeller vessel, keeping the recovered iron nuggets.', '撹拌槽で鉱物と硫酸を加熱し、回収される鉄塊も取り出します。', '在搅拌槽中加热矿物与硫酸，同时取出回收的铁粒。'),
    step('vessel', 'pressure_reaction/titanium_dioxide', 'Superheat the solution to obtain titanium dioxide and recover the sulfur trioxide.', '溶液を超加熱して二酸化チタンを得て、三酸化硫黄も回収します。', '超加热溶液取得二氧化钛并回收三氧化硫。'),
    step('vessel', 'pressure_reaction/synthetic_rutile', 'The hydrochloric-acid route gives another way to obtain the same oxide. Choose a route according to the acids already produced by the factory.', '塩酸を使う工程でも同じ酸化物を得られます。工場で生産している酸に合わせて工程を選びます。', '盐酸路线也能取得相同氧化物，可按工厂现有酸源选择路线。'),
    step('furnace', 'operation:reduce_titanium_dioxide', 'Send oxide and carbon to a powered, molten electrolysis furnace and recover titanium through a bottom face.', '酸化物と炭素を通電・溶融した電解炉へ送り、底面からチタンを回収します。', '将氧化物与碳送入通电熔融的电解炉，从底面回收钛。'),
    step('pressing', 'pressing/titanium_sheet', 'Press the titanium ingot into sheets. Finished metal can also be assembled into its existing storage block.', 'チタンインゴットを板にプレスします。金属は既存のチタンブロックにもまとめられます。', '把钛锭压成板，也可按已有配方合成钛块。'),
])

route('zirconium', ('From zircon to zirconia and zirconium', 'ジルコンからジルコニアとジルコニウムを作る', '从锆石制取氧化锆与锆'),
    ['zircon_ore', 'deepslate_zircon_ore', 'zircon', 'zirconium_sulfate_solution_bucket', 'zirconia', 'zirconium_ingot', 'zirconia_refractory_bricks', 'zirconium_pipe'], [
    step('crushing', 'crushing/zircon_ore', 'Crush zircon ore into the mineral feed.', 'ジルコン鉱石を粉砕して鉱物原料にします。', '粉碎锆石矿取得矿物原料。'),
    step('vessel', 'pressure_reaction/zirconium_sulfate_solution', 'Superheat zircon with sulfuric acid in an impeller vessel.', '撹拌槽でジルコンと硫酸を超加熱します。', '在搅拌槽中超加热锆石与硫酸。'),
    step('vessel', 'pressure_reaction/zirconia', 'Keep the solution in its own batch and superheat it to collect zirconia and recover sulfur trioxide.', '溶液を別バッチで超加熱し、ジルコニアと三酸化硫黄を回収します。', '将溶液作为下一批超加热，回收氧化锆和三氧化硫。'),
    step('furnace', 'operation:reduce_zirconia', 'Electrolyze the oxide with carbon in the powered furnace to recover zirconium metal.', '通電した炉で酸化物と炭素を電解し、ジルコニウム金属を回収します。', '在通电炉中电解氧化物与碳，回收金属锆。'),
    step('crafting', 'zirconia_refractory_bricks', 'Keep an oxide branch for refractory bricks. The metal branch supplies the existing zirconium components and pipes.', '酸化物の一部は耐火レンガに使います。金属の分岐は既存のジルコニウム部品や配管へ送ります。', '留一支氧化物用于耐火砖，金属支路供应已有锆部件和管道。'),
])

route('nickel', ('From pentlandite to nickel', 'ペントランド鉱からニッケルを作る', '从镍黄铁矿制取镍'),
    ['pentlandite_ore', 'deepslate_pentlandite_ore', 'pentlandite', 'nickel_oxide', 'nickel_ingot', 'nickel_sheet', 'nickel_alloy_pipe'], [
    step('crushing', 'crushing/pentlandite_ore', 'Crush the ore to obtain the mineral.', '鉱石を粉砕して鉱物を取り出します。', '粉碎矿石取得矿物。'),
    step('mixing', 'mixing/nickel_oxide_roasted_in_air', 'Roast two pentlandite in the air over a heated basin. Nickel oxide and iron nuggets stay; the sulfur leaves as gas.', '加熱した鉢で、ペントランド鉱2個を空気中で焙焼します。酸化ニッケルと鉄塊が残り、硫黄は気体になって抜けます。', '在加热的工作盆中把2个镍黄铁矿在空气中焙烧。留下氧化镍和铁粒，硫以气体逸出。'),
    step('mixing', 'mixing/nickel_ingot_reduced_with_carbon', 'Superheat the nickel oxide with carbon powder in the basin to reduce it to nickel.', '超加熱した鉢で、酸化ニッケルを炭素粉と混ぜてニッケルに還元します。', '在超级加热的工作盆中把氧化镍与碳粉混合，还原成镍。'),
    step('vessel', 'pressure_reaction/nickel_oxide', 'Once you have a stainless impeller vessel, roast with oxygen instead and send the sulfur dioxide to acid production.', 'ステンレス鋼の撹拌槽ができたら、酸素で焙焼して二酸化硫黄を硫酸製造へ回収できます。', '有了不锈钢搅拌槽后，可改用氧气焙烧，并把二氧化硫回收至制酸。'),
    step('vessel', 'pressure_reaction/nickel_ingot', 'Reducing with hydrogen needs no carbon and gives back water.', '水素で還元すると炭素が要らず、水も回収できます。', '用氢还原无需碳，还能回收水。'),
    step('pressing', 'pressing/nickel_sheet', 'Press the nickel into sheets for electrodes, alloys and fluid equipment.', 'ニッケルを板にプレスし、電極・合金・流体設備へ使います。', '将镍压成板，供电极、合金与流体设备使用。'),
])

route('chromium', ('From chromite to chromium', 'クロム鉄鉱からクロムを作る', '从铬铁矿制取铬'),
    ['chromite_ore', 'deepslate_chromite_ore', 'chromite', 'ferrochrome', 'chromium_ingot'], [
    step('crushing', 'crushing/chromite_ore', 'Crush chromite ore into the mineral feed.', 'クロム鉄鉱石を粉砕して鉱物原料にします。', '粉碎铬铁矿石取得矿物原料。'),
    step('mixing', 'mixing/ferrochrome', "Superheat two chromite with one carbon powder in a basin. The ore's iron stays in the metal, giving ferrochrome.", '超加熱した鉢で、クロム鉄鉱2個と炭素粉1個を混ぜます。鉱石の鉄は金属に残り、フェロクロムになります。', '在超级加热的工作盆中混合2个铬铁矿与1份碳粉。矿石中的铁留在金属里，得到铬铁。'),
    step('mixing', 'mixing/stainless_steel_from_ferrochrome', 'Superheat six iron ingots, two ferrochrome and one nickel into ten stainless steel ingots.', '鉄インゴット6個・フェロクロム2個・ニッケル1個を超加熱し、ステンレス鋼インゴット10個にします。', '超级加热6个铁锭、2个铬铁和1个镍，得到10个不锈钢锭。'),
    step('vessel', 'pressure_reaction/chromium_ingot', 'For parts that need pure chromium, use thermite with aluminium in a superheated stainless impeller vessel. Recover chromium, iron and alumina.', '純粋なクロムが要る部品には、ステンレス鋼の撹拌槽でアルミニウムと超加熱するテルミット法を使います。クロム・鉄・アルミナを回収します。', '需要纯铬的部件，在不锈钢搅拌槽中与铝超级加热进行铝热还原，回收铬、铁和氧化铝。'),
    step('mixing', 'mixing/stainless_steel_ingot', 'Pure chromium also makes stainless steel. The steel family also shows the converter route.', '純粋なクロムからもステンレス鋼を作れます。鋼の系列では転炉を使う工程も示します。', '纯铬也可炼不锈钢。钢的系列中也展示转炉工序。'),
])

route('molybdenum', ('From molybdenite to molybdenum', '輝水鉛鉱からモリブデンを作る', '从辉钼矿制取钼'),
    ['molybdenite_ore', 'deepslate_molybdenite_ore', 'molybdenite', 'molybdenum_trioxide', 'molybdenum_ingot'], [
    step('crushing', 'crushing/molybdenite_ore', 'Crush molybdenite ore.', '輝水鉛鉱石を粉砕します。', '粉碎辉钼矿石。'),
    step('vessel', 'pressure_reaction/molybdenum_trioxide', 'Roast the mineral with oxygen and return the sulfur dioxide to the acid line.', '鉱物を酸素と焙焼し、二酸化硫黄を硫酸ラインへ回収します。', '用氧焙烧矿物，二氧化硫回收至制酸线。'),
    step('vessel', 'pressure_reaction/molybdenum_ingot', 'Reduce the oxide with hydrogen to obtain molybdenum. Keep an oxide branch for the blue pigment family.', '酸化物を水素で還元してモリブデンを得ます。酸化物の一部は青色顔料の系列へ送ります。', '用氢还原氧化物制钼，留一支氧化物供蓝色颜料系列。'),
])

route('steel', ('From iron feed to steel and stainless steel', '鉄原料から鋼鉄とステンレス鋼を作る', '从铁原料制钢与不锈钢'),
    ['carbon_powder', 'unprocessed_steel', 'heated_unfinished_steel', 'steel_ingot', 'steel_block', 'stainless_steel_ingot', 'stainless_steel_sheet', 'stainless_steel_block'], [
    step('crushing', 'crushing/carbon_powder_from_coal', 'Crush coal into carbon powder. One coal supplies the carbon for four iron ingots.', '石炭を破砕して炭素粉を作ります。石炭1個分の炭素粉で鉄インゴット4個を加工できます。', '粉碎煤得到碳粉。1份煤的碳粉可加工4个铁锭。'),
    step('crushing', 'crushing/carbon_powder_from_charcoal', 'Charcoal works the same way in Crushing Wheels: one charcoal gives one carbon powder. A Millstone turns both fuels into dye instead.', '木炭も粉砕ホイールで炭素粉1個になります。石臼では石炭・木炭とも染料になるため使えません。', '木炭同样可用粉碎轮制得1份碳粉。磨石会把煤和木炭磨成染料，不能用于此步。'),
    step('mixing', 'mixing/unprocessed_steel', 'Superheat the burner with a Blaze Cake. Mix four iron ingots and one carbon powder in the basin to obtain four steel workpieces; ordinary heat cannot run this recipe.', 'ブレイズケーキでバーナーを超加熱します。鉢で鉄インゴット4個と炭素粉1個を混ぜ、中間素材4個を作ります。通常の加熱では作れません。', '用烈焰蛋糕超级加热燃烧室，搅拌4个铁锭与1份碳粉，得到4份钢中间材料；普通加热无法加工。'),
    step('pressing', 'sequenced_assembly/steel_ingot', 'Pass each workpiece through a Mechanical Press five times. Four workpieces yield exactly four steel ingots; intermediate passes retain their assembly progress.', '中間素材をそれぞれメカニカルプレスへ5回通します。4個の中間素材から鋼鉄インゴット計4個になり、加工途中は進捗を保持します。', '每份中间材料经过动力压机五次。4份材料共得4个钢锭，加工中保留组装进度。'),
    step('crafting', 'steel_block', 'Compress steel ingots into a steel block for equipment frames and vessel walls. Craft the block back into ingots when needed; Create industrial iron remains a separate decorative material.', '鋼鉄インゴットを圧縮し、設備の骨組みや槽の壁に使う鋼鉄ブロックにします。ブロックはインゴットへ戻せ、Createの鋼材ブロックとは別の素材です。', '将钢锭压成钢块用于设备框架与容器壁，也可拆回钢锭；Create工业铁块为独立的装饰材料。'),
    step('converter', 'converter/oxygen_steel', 'For bulk steel, build the complete tilting converter, supply the shown iron charge and oxygen, and recover the steel ingots after blowing.', '大量製造では傾動できる転炉を完成させ、表示された鉄原料と酸素を供給し、吹錬後に鋼鉄インゴットを回収します。', '批量制钢时组装完整可倾转转炉，供给所示铁料与氧，吹炼后回收钢锭。'),
    step('mixing', 'mixing/stainless_steel_from_ferrochrome', 'Superheat iron, ferrochrome and nickel in a basin to make stainless steel.', '鉢で鉄・フェロクロム・ニッケルを超加熱し、ステンレス鋼を作ります。', '在工作盆中超级加热铁、铬铁和镍，制成不锈钢。'),
    step('converter', 'converter/aod_stainless_steel', 'The bulk stainless route uses the converter with its argon/oxygen blend. Keep the separate exhaust path available.', '大量のステンレス鋼は転炉とアルゴン・酸素の混合ガスで作ります。別系統の排ガス経路も確保します。', '批量不锈钢用转炉和氩氧混合气制造，同时保持独立排气通路畅通。'),
    step('pressing', 'pressing/stainless_steel_sheet', 'Press the recovered stainless steel into sheets for chemical vessels and corrosion-resistant equipment.', '回収したステンレス鋼を板にプレスし、化学槽や耐食設備へ使います。', '把回收不锈钢压成板，用于化学容器与耐腐蚀设备。'),
])

route('fluorine', ('From fluorite to fluorine', '蛍石からフッ素を作る', '从萤石制取氟气'),
    ['fluorite'], [
    step('crushing', 'crushing/calcite_to_fluorite', 'Crush calcite with Crushing Wheels to collect fluorite (50%) and bone meal. A Millstone gives only bone meal.', '粉砕ホイールで方解石を粉砕し、蛍石（50％）と骨粉を集めます。石臼では骨粉しか得られません。', '用粉碎轮粉碎方解石，获得萤石（50%）和骨粉。磨石只能得到骨粉。'),
    step('vessel', 'pressure_reaction/hydrogen_fluoride', 'Heat fluorite with sulfuric acid in an impeller vessel. Recover hydrogen fluoride gas from the top through corrosion-resistant equipment.', '撹拌槽で蛍石と硫酸を加熱し、フッ化水素を作ります。気体は上面から対応する耐食設備で回収します。', '在搅拌槽中加热萤石与硫酸，通过顶部的相应耐腐蚀设备回收氟化氢气体。', output_at=(4, 6, 4)),
    step('electrolyzer', None, 'Build the vertical 2x2 cells with a 2x2 controller at one end. Load electrodes through the controller tops, close the hatches and wire both upper-right terminals.', '縦2×2の電解セルの片端に2×2制御盤を付けます。制御盤上面から極板を入れ、ハッチを閉じて右上の2端子へ給電します。', '在竖直2×2电解单元一端安装2×2控制器，从控制器顶部装入电极，关闭检修口并给右上两个端子供电。', 'asbestos_wrapped_electrode'),
    step('electrolyzer', 'operation:fluoride_electrolysis', 'Feed hydrogen fluoride below and press START. Collect hydrogen from the upper row with lower X and fluorine from the other; amounts are per plate over 100 ticks.', '下段へフッ化水素を送り、STARTを押して、上段のXが小さい列から水素、反対側からフッ素を回収します。下の量は極板1枚・100 tick分です。', '下部供氟化氢并按START，上部低X侧收氢气，另一侧收氟气，数量按每块极板100 tick计算。'),
])

route('pigments', ('Making the mineral pigment family', '鉱物から顔料と染料を作る', '从矿物制取颜料与染料'),
    ['triiron_tetroxide_pile', 'iron_oxide_red', 'chromium_oxide', 'nickel_titanium_yellow', 'molybdenum_blue'], [
    step('mixing', 'mixing/triiron_tetroxide_pile', 'Combine iron and water in a basin to prepare the black iron-oxide pigment.', '鉢で鉄と水を混ぜ、黒い酸化鉄顔料を用意します。', '在工作盆中混合铁与水，制备黑色氧化铁颜料。'),
    step('vessel', 'pressure_reaction/iron_oxide_red', 'Heat the black oxide with oxygen to obtain red iron oxide.', '黒い酸化物を酸素と加熱し、酸化鉄赤にします。', '把黑色氧化物与氧气加热，制得氧化铁红。'),
    step('vessel', 'pressure_reaction/chromium_oxide', 'Superheat chromite with oxygen for chromium-green pigment.', 'クロム鉄鉱と酸素を超加熱し、クロムグリーンを作ります。', '超加热铬铁矿与氧气制取铬绿。'),
    step('vessel', 'pressure_reaction/nickel_titanium_yellow', 'Combine the nickel and titanium oxides from their metal families under superheat to make yellow pigment.', 'ニッケルとチタンの系列で作った酸化物を超加熱し、黄色顔料を作ります。', '超加热镍、钛制造系列取得的氧化物，制取黄色颜料。'),
    step('vessel', 'pressure_reaction/molybdenum_blue', 'Use molybdenum oxide, sulfur dioxide and water to make blue pigment and recover sulfuric acid.', 'モリブデン酸化物・二酸化硫黄・水から青色顔料を作り、硫酸も回収します。', '用钼氧化物、二氧化硫与水制取蓝色颜料并回收硫酸。'),
    step('milling', 'milling/red_dye_from_iron_oxide_red', 'Mill the prepared pigments into dyes. This red example uses the same Millstone setup as the black, green, yellow, blue and titanium-white recipes.', '作った顔料を石臼で染料にします。この赤の例と同じ配置で、黒・緑・黄・青とチタン白も加工できます。', '用磨石把制成的颜料磨成染料，黑、绿、黄、蓝及钛白均可使用此红色示例的同类配置。'),
])

route('concrete', ('Building materials 1 — cement and coated floors', '建材の製造1　セメントと防食床を作る', '建材制造1　水泥与防腐地板'),
    ['phosphoric_acid_bucket', 'gypsum', 'cement_clinker', 'ground_clinker', 'cement_powder', 'industrial_concrete', 'industrial_concrete_slab', 'industrial_concrete_stairs', 'corrosion_resistant_floor', 'corrosion_resistant_floor_slab'], [
    step('vessel', 'pressure_reaction/phosphate_acidulation', 'Heat bone meal with sulfuric acid and water to obtain the gypsum used by cement. Recover phosphoric acid as its own product.', '骨粉・硫酸・水を加熱し、セメントに使う石膏を得ます。リン酸も別の製品として回収します。', '加热骨粉、硫酸与水，取得水泥所需石膏，并单独回收磷酸。'),
    step('mixing', 'mixing/cement_clinker', 'Superheat calcite, clay, sand and the iron additive under a Mechanical Mixer to fire clinker.', 'メカニカルミキサーの下で方解石・粘土・砂・鉄の添加物を超加熱し、クリンカーを焼きます。', '在机械搅拌器下超加热方解石、黏土、砂和铁添加物，烧成熟料。'),
    step('crushing', 'crushing/ground_clinker', 'Crush the fired clinker before blending the cement powder.', '焼いたクリンカーを粉砕し、セメント粉末の配合へ送ります。', '粉碎烧成的熟料，再送往水泥粉末配料。'),
    step('mixing', 'mixing/cement_powder', 'Mix the ground clinker with the recovered gypsum.', '粉砕クリンカーに回収した石膏を混ぜます。', '将粉碎熟料与回收石膏混合。'),
    step('mixing', 'mixing/industrial_concrete', 'Mix cement with sand, gravel and water. Use standard funnels for solids and pipes for the water.', 'セメント・砂・砂利・水を混ぜます。固形物は標準ファンネル、水は配管で供給します。', '混合水泥、砂、砂砾与水，固体用标准漏斗，水用管道供给。'),
    step('deploying', 'deploying/corrosion_resistant_floor', 'A powered Deployer applies a plastic sheet to the concrete. Supply its held sheet and send concrete below it. The finished floor and its slab variants share this production family.', '駆動したデプロイヤーにプラスチック板を持たせ、下へコンクリートを送って防食床にします。完成した床やハーフブロックも同じ製造系列から確認できます。', '给有动力的机械手持塑料板，在下方送入混凝土以制防腐地板，其半砖变体也归入同一制造系列。'),
], family='construction')

route('glass', ('Building materials 2 — borax and glass frit', '建材の製造2　ホウ砂とガラス原料を作る', '建材制造2　硼砂与玻璃原料'),
    ['borax', 'borosilicate_glass_frit'], [
    step('vessel', 'pressure_reaction/borax_from_brine', 'Heat the brine batch in an impeller vessel to recover borax and salt. Keep the salt for the brine loop.', '撹拌槽で塩水のバッチを加熱し、ホウ砂と塩を回収します。塩は塩水の製造へ戻せます。', '在搅拌槽中加热盐水批次，回收硼砂和盐，盐可返回制盐水。'),
    step('mixing', 'mixing/borosilicate_glass_frit', 'Superheat borax with quartz, glass and calcite under a Mechanical Mixer to make borosilicate frit.', '鉢とメカニカルミキサーで、ホウ砂・水晶・ガラス・方解石を超加熱してホウケイ酸ガラスフリットにします。', '在机械搅拌器下超加热硼砂、石英、玻璃与方解石，制成硼硅酸玻璃熔块。'),
], family='construction')

route('washwater', ('Reclaiming wash water and sludge', '洗浄排水と汚泥を回収する', '回收洗涤废水与污泥'),
    ['wastewater_bucket', 'neutralized_wastewater_bucket', 'clarified_water_bucket', 'water_treatment_flocculant', 'water_filter_medium', 'treatment_sludge'], [
    step('mixing', 'mixing/water_treatment_flocculant', 'Prepare the flocculant from the oxide and clay feeds made by the mineral production families.', '鉱物の製造系列で用意した酸化物と粘土から、凝集剤を作ります。', '用矿物制造系列制得的氧化物与黏土配制絮凝剂。'),
    step('crafting', 'water_filter_medium', 'Make filter medium and keep it supplied to the last treatment vessel.', 'ろ材を製作し、最後の処理槽へ補給できるようにします。', '制作滤料，持续供给最后一个处理槽。'),
    step('vessel', 'pressure_reaction/washwater_neutralization', 'Collect the vessel drain wastewater and neutralize it with calcite in an impeller vessel.', '槽のドレンから洗浄排水を集め、撹拌槽で方解石を使って中和します。', '收集容器排水，在搅拌槽中用方解石中和。'),
    step('vessel', 'pressure_reaction/washwater_precipitation', 'Feed the neutralized water and flocculant into the next treatment batch.', '中和済みの水と凝集剤を次の処理バッチへ供給します。', '将中和水与絮凝剂送入下一处理批次。'),
    step('vessel', 'pressure_reaction/washwater_filtration', 'Filter the clarified water and extract the sludge separately. Recovered water can go back to factory processes.', '清澄水をろ過し、汚泥を別に取り出します。回収した水は工場の工程へ戻せます。', '过滤澄清水并单独取出污泥，回收的水可返回工厂工序。'),
    step('mixing', 'mixing/cement_clinker_with_sludge', 'The collected sludge can join the existing clinker recipe. Keep the material moving from maintenance into building supplies.', '集めた汚泥は既存のクリンカー配合へ入れられます。設備の清掃で出た素材を建材の製造へつなげます。', '收集的污泥可加入已有熟料配方，将设备清理所得材料送往建材制造。'),
], family='water')

route('catalysts', ('Making and regenerating catalyst cartridges', '触媒カートリッジを製作・再生する', '制作与再生催化剂芯盒'),
    ['iron_catalyst', 'oxidation_catalyst', 'cracking_catalyst', 'polymerization_catalyst', 'hydrotreating_catalyst', 'hydrocracking_catalyst'], [
    step('crafting', 'iron_catalyst', "Prepare the iron catalyst for ammonia synthesis and load the cartridge into a reaction tower's basket.", 'アンモニア合成用の鉄触媒を製作し、反応塔のバスケットへ装荷します。', '制作合成氨用铁催化剂，装入反应塔的篮中。'),
    step('crafting', 'oxidation_catalyst', 'Prepare the oxidation cartridge for sulfur, nitrogen and ethylene-oxide production.', '硫黄・窒素系の酸化や酸化エチレン製造に使う酸化触媒を製作します。', '制作硫、氮系氧化和环氧乙烷生产所用氧化催化剂。'),
    step('crafting', 'cracking_catalyst', 'Make the cracking cartridge for the oil-to-olefin stage.', '石油からオレフィンを作る工程用の分解触媒を製作します。', '制作石油制烯烃阶段所用裂化催化剂。'),
    step('crafting', 'polymerization_catalyst', 'Make the polymerization cartridge for the polymer melt stage.', '樹脂の溶融物を作る工程用の重合触媒を製作します。', '制作树脂熔体阶段所用聚合催化剂。'),
    step('crafting', 'hydrotreating_catalyst', 'The fuel line uses its matching hydrotreating cartridge with the metal supplied by the mineral families.', '燃料の脱硫ラインには、鉱物系列で製造した金属を使う専用触媒を用意します。', '燃料脱硫线使用相应芯盒，金属由矿物制造系列提供。'),
    step('crafting', 'hydrocracking_catalyst', 'The heavy-oil branch uses the hydrocracking cartridge. Check the recipe and pressure of the process before loading it.', '重油の分解には水素化分解触媒を使います。装荷する前に工程の原料と必要圧力を確認します。', '重油裂化支路使用加氢裂化催化剂，装入前确认工序原料与所需压力。'),
    step('crafting', 'iron_catalyst_regeneration', 'Recover a worn cartridge and use its matching regeneration recipe. The iron example here restores the cartridge for reuse.', '劣化したカートリッジを回収し、対応する再生レシピを使います。この鉄触媒の例では、再生して同じ用途へ戻せます。', '回收磨损芯盒并使用相应再生配方，此铁催化剂示例再生后可重复使用。'),
])

route('mining_explosives', ('From hydrogen and nitrogen to gunpowder', '水素・窒素から火薬と発破薬を作る', '从氢气和氮气制造火药与采矿炸药'),
    ['minecraft:gunpowder', 'ammonium_nitrate', 'anfo', 'blasting_charge'], [
    step('vessel', 'pressure_reaction/synthesis_gas', 'Mix hydrogen from water electrolysis with nitrogen from air fractionation in a 3:1 ratio.', '電解セルの水素と、空気分留塔の窒素を3：1で混ぜ、合成ガスを作ります。', '将电解水得到的氢气和空气分馏得到的氮气按3:1混合，制取合成气。'),
    step('catalytic', 'operation:haber_bosch', 'Compress synthesis feed and heat the iron-catalyst reaction tower. Cool and separate its mixture to recover liquid ammonia.', '合成原料を圧縮し、鉄触媒入りの反応塔を加熱します。生成ガスを冷却・分離して液体アンモニアを回収します。', '压缩合成原料并加热装有铁催化剂的反应塔，将产物冷却、分离后回收液氨。', 'iron_catalyst'),
    step('vessel', 'pressure_reaction/ammonium_nitrate', 'React ammonia with nitric acid to make ammonium nitrate. Nitric acid is made from ammonia, oxygen and water; follow its JEI recipe.', 'アンモニアと硝酸から硝酸アンモニウムを作ります。硝酸もアンモニア・酸素・水から作れます。詳細は硝酸のJEIで確認できます。', '氨与硝酸反应制取硝酸铵。硝酸也由氨、氧气和水制成，详见硝酸的JEI配方。'),
    step('crafting', 'gunpowder_from_ammonium_nitrate', 'Craft ammonium nitrate, charcoal and sulfur dust into gunpowder.', '硝酸アンモニウム・木炭・硫黄の粉から火薬を作ります。', '将硝酸铵、木炭和硫粉合成为火药。'),
    step('mixing', 'mixing/anfo', 'Use ammonium nitrate from the nitric-acid family with diesel in a basin. Collect the ANFO product.', '硝酸の製造系列で得た硝酸アンモニウムを、鉢で軽油と混ぜてANFOを回収します。', '将硝酸制造系列得到的硝酸铵与柴油在工作盆中混合，收集ANFO。'),
    step('crafting', 'blasting_charge', 'Combine ANFO, paper and redstone into the existing mining-charge item.', 'ANFO・紙・レッドストーンを組み合わせ、既存の発破薬アイテムを製作します。', '将ANFO、纸与红石组合为已有采矿炸药物品。'),
])

route('deuterium', ('From water to deuterium fuel', '水から重水素燃料を作る', '从水制取氘燃料'),
    ['heavy_water_bucket', 'deuterium_depleted_water_bucket', 'deuterium_gas_bucket'], [
    step('centrifuge', 'process:concentrate_heavy_water', 'Place two gas centrifuges side by side and drive each bottom shaft at 32 RPM or more. Water enters a side, the heavy-water product leaves the top, and depleted water leaves a side.', 'ガス遠心分離機を横に2台並べ、それぞれの底面を32 RPM以上で回します。側面から水を送り、上面から重水、側面から重水を減らした水を取り出します。', '横向放置两台气体离心机，各自从底部输入至少32 RPM，侧面送水，顶部取重水，侧面取贫氘水。'),
    step('electrolyzer', 'operation:heavy_water_electrolysis', 'Feed heavy water below, wire CEE and close the hatches. Collect deuterium from the upper row with lower X and oxygen from the other.', '重水を下段へ送り、CEEを配線してハッチを閉じます。上段のXが小さい列から重水素、反対側から酸素を回収します。', '重水从下部送入，接CEE并关盖，上部低X侧回收氘气，另一侧回收氧气。'),
    step('vessel', 'pressure_reaction/reuse_deuterium_depleted_water', 'Return the depleted-water branch to utility water through its existing recovery recipe.', '重水を減らした水は既存の回収レシピで工場用水へ戻します。', '贫氘水支路通过已有回收配方返回工业用水。'),
])

route('liquid_helium', ('From recovered helium to liquid-helium circulation', '回収ヘリウムから液体ヘリウムの循環へ', '从回收氦到液氦循环'),
    ['helium_gas_bucket', 'liquid_helium_bucket'], [
    step('centrifuge', 'process:recover_helium', 'Feed the recovered helium stream through a gas centrifuge driven at 32 RPM or more. Recover helium at the top and the separated nitrogen at a side.', '回収ヘリウム原料を32 RPM以上で回るガス遠心分離機へ送り、上面からヘリウム、側面から分離した窒素を回収します。', '把回收氦原料送入至少32 RPM的气体离心机，顶部取氦，侧面取分离出的氮。'),
    step('cold_box', None, 'Place a regenerator inside a 3×3×3 shell and the drive at the front centre. Connect He above, 32 RPM to the shaft, coolant left and warm water right.', '3×3×3の外殻中央へ再生器、正面中央へ駆動部を置きます。上面にHe、軸に32 RPM以上、左に冷却水、右に温水出口をつなぎます。', '3×3×3外壳中央放再生器、正面中央放驱动端，上面接He、轴接至少32 RPM、左接冷却水、右排温水。', 'helium_gas_bucket'),
    step('cold_box', None, 'Collect the liquid helium only after the cold box reaches its working temperature. Return boil-off gas through the recovery loop and keep the liquid in cryogenic pipes and tanks.', 'コールドボックスが動作温度に達してから液体ヘリウムを回収します。蒸発した気体を回収ループへ戻し、液体は極低温配管とタンクで運びます。', '冷箱达到工作温度后收集液氦，蒸发气体返回回收循环，液体使用低温管道与储罐运输。', 'liquid_helium_bucket'),
])

route('carbon_oxides', ('Recover carbon monoxide and make carbon dioxide', '一酸化炭素を回収して二酸化炭素を作る', '回收一氧化碳并制取二氧化碳'),
    ['gas_receiver', 'converter_shell', 'oxidation_catalyst'], [
    step('column', 'process:recover_carbon_monoxide', 'Recover CO from converter gas in a 2×2 column at least two layers high. In this 14-layer example, feed layer three and collect at the top.', '転炉ガスを2×2・最低2段の分留塔へ送るとCOを回収できます。図の14段塔では3段目に入れ、最上段から取ります。', '转炉煤气用2×2、至少两层分馏塔回收CO，图示十四层塔从第三层进料、顶层取出。'),
    step('catalytic', 'process:oxidize_carbon_monoxide', 'Load an oxidation catalyst into a reaction tower. Supply CO and oxygen at a bottom-layer side and collect CO2 from the bottom layer.', '反応塔に酸化触媒を入れます。COと酸素を最下段の側面へ送り、CO2を最下段から回収します。', '向反应塔装入氧化催化剂，从底层侧面供给CO和氧气，从底层回收CO2。'),
])

route('resin_water_loop', ('Making flame-retardant pellets and reusing water', '難燃ペレットを作り、水を再利用する', '制备阻燃颗粒并循环用水'),
    ['flame_retardant_pellet', 'heat_resistant_gasket', 'resin_process_vapor_bucket'], [
    step('vessel', 'pressure_reaction/flame_retardant_pellet'),
    step('absorber', 'process:wash_resin_process_vapor'),
    step('vessel', 'pressure_reaction/washwater_precipitation'),
    step('vessel', 'pressure_reaction/washwater_filtration'),
    step('pressing', 'pressing/heat_resistant_gasket'),
    step('crafting', 'high_voltage_conductor'),
])

route('methylphosphonate', ('Making dimethyl methylphosphonate', 'メチルホスホン酸ジメチルを作る', '制备甲基膦酸二甲酯'),
    ['iodine', 'trimethyl_phosphite_bucket', 'crude_methylphosphonate_bucket', 'dimethyl_methylphosphonate_bucket'], [
    step('vessel', 'pressure_reaction/trimethyl_phosphite_rearrangement'),
    step('vessel', 'pressure_reaction/methylphosphonate_filtration', output_at=(7, 2, 5)),
])

route('slime_coolant', ('Making slime coolant', 'スライム冷却液を作る', '制备史莱姆冷却液'),
    ['slime_coolant_feed_bucket', 'slime_coolant_bucket', 'warm_slime_coolant_bucket'], [
    step('vessel', 'pressure_reaction/prepare_slime_coolant'),
    step('column', 'process:distill_slime_coolant'),
])

if __name__ == '__main__':
    import sys
    generate(set(sys.argv[1:]) or None)
