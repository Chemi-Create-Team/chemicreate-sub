"""Bake the closed turbine shells; only shafts, blades and windows need BERs."""
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
RING = {-2: 3, -1: 0, 0: 1, 1: 2, 2: 4}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def box(start, end):
    return {'from': start, 'to': end,
            'faces': {side: {'texture': '#shell', 'uv': [0, 0, 16, 8]}
                      for side in ('down', 'up', 'north', 'south', 'west', 'east')}}


def model(name, pressure, elements):
    write(ASSETS / f'models/block/turbine/unit/{name}.json', {
        'textures': {'shell': NS + f'block/turbine/{pressure}_pressure_casing', 'particle': '#shell'},
        'elements': elements})
    return NS + 'block/turbine/unit/' + name


parts = []
for radius, pressure in ((1, 'high'), (2, 'low')):
    for x in range(-radius, radius + 1):
        for y in range(-radius, radius + 1):
            if max(abs(x), abs(y)) != radius:
                continue
            elements = []
            if abs(x) == radius:
                elements.append(box([0, 0, 0 if x < 0 else 14], [16, 16, 2 if x < 0 else 16]))
            if abs(y) == radius:
                elements.append(box([0, 0 if y < 0 else 14, 0], [16, 2 if y < 0 else 16, 16]))
            # Models rotate +90 about Y for Z, so ring-X signs reverse.
            path = model(f'{pressure}_side_{RING[x]}_{RING[y]}', pressure, elements)
            for axis in ('x', 'z'):
                parts.append({'when': {'section': 'false', 'attached': 'true', 'casing': 'true', 'window': 'false',
                                       'axis': axis, 'ring_x': str(RING[x if axis == 'x' else -x]),
                                       'ring_y': str(RING[y])},
                              'apply': {'model': path, **({'y': 90} if axis == 'z' else {})}})
    ends = []
    for positive in (False, True):
        x0, x1 = 0, 16
        for center in (False, True):
            elements = [box([x0, 0, 0], [x1, 16, 16])] if not center else [
                box([x0, 0, 0], [x1, 6, 16]), box([x0, 10, 0], [x1, 16, 16]),
                box([x0, 6, 0], [x1, 10, 6]), box([x0, 6, 10], [x1, 10, 16])]
            path = model(f'{pressure}_end_{int(positive)}_{int(center)}', pressure, elements)
            for axis in ('x', 'z'):
                for ring_x in range(-radius, radius + 1):
                    for ring_y in range(-radius, radius + 1):
                        if ((ring_x == 0 and ring_y == 0) != center):
                            continue
                        ends.append({'when': {'axis': axis, 'positive': str(positive).lower(),
                                              'ring_x': str(RING[ring_x]), 'ring_y': str(RING[ring_y])},
                                     'apply': {'model': path, **({'y': 90} if axis == 'z' else {})}})
    write(ASSETS / f'blockstates/{pressure}_pressure_turbine_end.json', {'multipart': ends})
write(ASSETS / 'blockstates/high_pressure_turbine_part.json', {'multipart': parts})
