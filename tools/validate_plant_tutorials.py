#!/usr/bin/env python3
"""Validate additive Ponder registration, bilingual captions and every model-scale target."""
import gzip
import io
import json
from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
PREFIX = 'create_refinery_n_reactors.ponder.shared.guide.'


def read_structure(path):
    stream = io.BytesIO(gzip.decompress(path.read_bytes()))
    def number(fmt):
        return struct.unpack(fmt, stream.read(struct.calcsize(fmt)))[0]
    def string():
        return stream.read(number('>H')).decode('utf8')
    def value(kind):
        if kind == 1: return number('>b')
        if kind == 2: return number('>h')
        if kind == 3: return number('>i')
        if kind == 5: return number('>f')
        if kind == 6: return number('>d')
        if kind == 4: return number('>q')
        if kind == 8: return string()
        if kind == 11: return [number('>i') for _ in range(number('>i'))]
        if kind == 9:
            child, length = number('>B'), number('>i')
            assert 0 <= length <= 100000
            return [value(child) for _ in range(length)]
        if kind == 10:
            result = {}
            while (child := number('>B')) != 0:
                key = string()
                assert key not in result
                result[key] = value(child)
            return result
        raise AssertionError(f'Unexpected structure NBT tag {kind}')
    assert number('>B') == 10 and string() == ''
    data = value(10)
    assert stream.read() == b'' and data['DataVersion'] == 3955
    return data


if __name__ == '__main__':
    catalogue = json.loads((ASSETS/'ponder/tutorials.json').read_text())
    manufacturing_only = '--manufacturing' in sys.argv
    if manufacturing_only: catalogue = [tutorial for tutorial in catalogue if tutorial.get('family')]
    languages = {language: json.loads((ASSETS/'lang'/(language+'.json')).read_text())
                 for language in ('en_us', 'ja_jp')}
    assert len(catalogue) >= 24
    assert len({t['id'] for t in catalogue}) == len(catalogue)
    components, captions = set(), set()
    for tutorial in catalogue:
        name = tutorial['id']
        assert re.fullmatch('[a-z0-9_]+', name)
        assert tutorial['components'] and (2 <= len(tutorial['steps']) <= 12 if tutorial.get('family') else 3 <= len(tutorial['steps']) <= 8)
        assert len(set(tutorial['components'])) == len(tutorial['components'])
        for component in tutorial['components']:
            assert re.fullmatch('(?:[a-z0-9_]+:)?[a-z0-9_]+', component), \
                'Component identifiers must be valid registry names'
            components.add(component)
        data = read_structure(ASSETS/'ponder/plant'/(name+'.nbt'))
        if tutorial.get('family'):
            processes = [step['process'] for step in tutorial['steps']]
            assert data['size'] == [max(p['offset'] + p['width'] for p in processes),
                max(p['height'] for p in processes), max(p['width'] for p in processes)]
        else:
            assert data['size'] == [tutorial.get('size',7), tutorial.get('height',7), tutorial.get('size',7)]
        positions = {}
        states = data['palette']
        for block in data['blocks']:
            pos = tuple(block['pos'])
            assert pos not in positions and all(0 <= n < bound for n,bound in zip(pos,data['size']))
            assert 0 <= block['state'] < len(data['palette'])
            positions[pos] = states[block['state']]
        if name.startswith('open_tank_size_'):
            diameter = int(name.rsplit('_', 1)[1])
            floor = {pos for pos, state in positions.items() if pos[1] == 1 and state['Name'] == 'create_refinery_n_reactors:tank_casing'}
            wall = {pos for pos, state in positions.items() if pos[1] == 2 and state['Name'] == 'create_refinery_n_reactors:tank_casing'}
            expected_floor = set()
            expected_wall = set()
            for x in range(diameter):
                for z in range(diameter):
                    dx = 2*x - (diameter - 1)
                    dz = 2*z - (diameter - 1)
                    if dx*dx + dz*dz > diameter*diameter:
                        continue
                    expected_floor.add((x + 1, 1, z + 1))
                    if any((2*(x + ox) - (diameter - 1))**2 + (2*(z + oz) - (diameter - 1))**2 > diameter*diameter
                           for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                        expected_wall.add((x + 1, 2, z + 1))
            assert floor == expected_floor, f'{name}: floor does not use the tank circle rule'
            assert wall == expected_wall, f'{name}: wall does not use the tank circle rule'
        keys = {PREFIX+name+'.title'}
        for step in tutorial['steps']:
            target = list(step['target'])
            target[0] += step.get('process',{}).get('offset',0)
            if name.startswith('turbine_'):
                # TurbineUnitPonderScenes builds these assemblies at runtime.
                x,y,z=target
                assert (15<=x<=19 and y==5 and z in (10,12)) or (2<=x<=11 and 1<=y<=9 and z in (10,14)) or (12<=x<=14 and y==5 and z==10), f'{name}: target misses the runtime turbine assembly'
            else:
                assert tuple(target) in positions, f'{name}: a caption must point at a model position'
            assert PREFIX+name+'.'+step['key'] not in keys
            keys.add(PREFIX+name+'.'+step['key'])
            if 'reveal' in step:
                box = step['reveal']
                assert len(box) == 6 and all(0 <= low <= high < bound for low, high, bound in zip(box[:3],box[3:],data["size"]))
            if 'item' in step and step['item'].startswith('create_refinery_n_reactors:'):
                assert (ASSETS/'models/item'/(step['item'].split(':')[1]+'.json')).is_file()
        for language, strings in languages.items():
            for key in keys:
                assert strings.get(key), (language,key)
        captions.update(keys)
    assert len(captions) >= 120
    if manufacturing_only:
        families={}
        for tutorial in catalogue:
            previous=families.setdefault(tutorial['family'],tutorial['components'])
            assert previous==tutorial['components'], 'Family chapters must share every intermediate entrypoint'
        print('PASS:',len(catalogue),'manufacturing chapters,',len(families),'families,',len(captions),'caption keys and all structure targets')
        raise SystemExit(0)
    for diameter in range(4, 33):
        assert f'open_tank_size_{diameter}' in {t['id'] for t in catalogue}
    for required in ('gas_receiver', 'lidded_vessel',
                     'reactor_impeller', 'gas_centrifuge', 'ceramic_pipe',
                     'large_flywheel', 'tank_casing'):
        assert required in components, f'Critical equipment is missing Ponder coverage: {required}'
    print(f'Validated {len(catalogue)} additional Ponder scenes, {len(captions)} paired captions, '
          f'{len(components)} component links and all structure targets')

    for path in (ASSETS/'ponder').rglob('*.nbt'):
        payload = gzip.decompress(path.read_bytes())
        assert b'create_plant:' not in payload, f'Legacy block namespace in {path}'
        assert not re.search(rb'create_refinery_n_reactors:(?:small_compressor|large_compressor|compressor_mechanism)(?![a-z_])',payload), f'Removed compressor in {path}'
        assert b'reactor_rotor_column' not in payload, f'Retired centrifuge internal in {path}'

        assert not re.search(rb'create_refinery_n_reactors:reactor_(?:vessel|stirred|separator|centrifuge)(?![a-z_])', payload), \
            f'Removed reaction-plant drive in {path}'
    print('All shipped Ponder structures are free of obsolete namespaces and removed reaction-plant drives')

    # Expansion lessons must visibly add functional parts, not just rename captions.
    for basic,expanded,part in [
        ('module_cooling','module_cooling_expansion','cooling_fan')]:
        def count(scene):
            data=read_structure(ASSETS/'ponder/plant'/(scene+'.nbt'))
            return sum(data['palette'][b['state']]['Name']=='create_refinery_n_reactors:'+part for b in data['blocks'])
        assert count(expanded)>count(basic)>0, f'{expanded}: expansion must add real {part}'
        for suffix in ('', '_expansion', '_integration'):
            assert basic+suffix in {t['id'] for t in catalogue}
    print('Validated staged equipment lessons and visible functional expansion')

    families={}
    for tutorial in catalogue:
        if not tutorial.get('family'):continue
        previous=families.setdefault(tutorial['family'],tutorial['components'])
        assert previous==tutorial['components'], 'Family chapters must share every intermediate entrypoint'
        data=read_structure(ASSETS/'ponder/plant'/(tutorial['id']+'.nbt'))
        blocks={tuple(block['pos']):data['palette'][block['state']] for block in data['blocks']}
        for step in tutorial['steps']:
            process=step['process']
            if process['kind']!='pressing':continue
            x,y,z=process['machine'];x+=process['offset']
            assert blocks[(x,y,z)]['Name']=='create:mechanical_press'
            assert blocks[(x,y-2,z)]['Name']=='create:depot'
            assert blocks[(x-1,y,z)]['Name']=='create:creative_motor'
    print('Validated',len(families),'manufacturing families and connected press/depot drives')
