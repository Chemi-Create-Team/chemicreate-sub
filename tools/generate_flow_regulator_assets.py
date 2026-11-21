"""Bake the regulator's meter, wrench cap and hit boxes from the same geometry."""
import json
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NAMESPACE = 'create_refinery_n_reactors:'
DIRECTIONS = {
    'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0),
    'down': (0, -1, 0), 'south': (0, 0, 1), 'north': (0, 0, -1),
}
FACES = {vector: name for name, vector in DIRECTIONS.items()}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def move(elements, rotate):
    def point(position):
        turn = rotate(*(value - 8 for value in position))
        return [round(value + 8, 5) for value in turn]

    parts = []
    for original in elements:
        part = deepcopy(original)
        part.pop('rotation', None)
        first, last = point(part['from']), point(part['to'])
        part['from'] = [min(a, b) for a, b in zip(first, last)]
        part['to'] = [max(a, b) for a, b in zip(first, last)]
        part['faces'] = {FACES[rotate(*DIRECTIONS[face])]: value for face, value in part['faces'].items()}
        parts.append(part)
    return parts


def cap(name, start, end, texture):
    return {'name': name, 'from': start, 'to': end,
            'faces': {face: {'texture': texture, 'uv': [5, 5, 11, 11]} for face in DIRECTIONS}}


def generate_flow_regulator(assets, namespace, writer):
    models = assets / 'models/block'
    body = json.loads((models / 'pipe_gauge/body.json').read_text())
    head = json.loads((models / 'pipe_gauge/flow_head.json').read_text())
    for part in head['elements']:
        part['from'][0] -= .5
        part['to'][0] -= .5
    textures = body['textures'] | head['textures'] | {
        'adjuster': 'create:block/brass_block', 'socket': 'create:block/zinc_block',
    }
    knob = [cap('WrenchCollar', [5, 14, 5], [11, 15, 11], '#socket'),
            cap('WrenchCap', [6, 15, 6], [10, 16, 10], '#adjuster')]
    layouts = {
        'x': (lambda x, y, z: (y, z, x), ('north', 'south')),
        'y': (lambda x, y, z: (x, y, z), ('east', 'south', 'west')),
        'z': (lambda x, y, z: (x, z, -y), ('east', 'west')),
    }
    meters = {
        'east': lambda x, y, z: (x, y, z),
        'south': lambda x, y, z: (-z, y, x),
        'west': lambda x, y, z: (-x, y, -z),
        'north': lambda x, y, z: (z, y, -x),
    }
    shape_path = assets / 'heating_shapes.json'
    shapes = json.loads(shape_path.read_text())
    variants = {}
    for axis, (body_rotation, faces) in layouts.items():
        parts = move(body['elements'], body_rotation)
        parts += move(knob, layouts['z'][0] if axis == 'y' else layouts['y'][0])
        for face in faces:
            parts += move(head['elements'], meters[face])
        path = 'flow_regulator/' + axis
        writer(models / f'{path}.json', {'parent': 'block/block', 'textures': textures, 'elements': parts})
        variants['axis=' + axis] = {'model': namespace + 'block/' + path}
        shapes['regulator/' + axis] = [part['from'] + part['to'] for part in parts if all(
            low < high for low, high in zip(part['from'], part['to']))]
    writer(shape_path, shapes)
    writer(assets / 'blockstates/flow_regulator.json', {'variants': variants})
    writer(assets / 'models/item/flow_regulator.json', {'parent': namespace + 'block/flow_regulator/y'})


if __name__ == '__main__':
    generate_flow_regulator(ASSETS, NAMESPACE, write)
