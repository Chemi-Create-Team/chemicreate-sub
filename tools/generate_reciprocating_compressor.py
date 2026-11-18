"""Native cast-iron compressor geometry. Uses the plant's existing 16 px cast, steel and brass materials."""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
MOD = 'create_refinery_n_reactors:'
FOLDER = 'block/reciprocating_compressor/'
MATERIALS = {name: MOD + 'block/gas_compressor/' + name for name in ('cast', 'steel', 'brass', 'fins', 'base', 'dial')}
MATERIALS['particle'] = MATERIALS['cast']
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def box(bounds, material, uv=None):
    dx, dy, dz = [min(16, bounds[i+3]-bounds[i]) for i in range(3)]
    faces = {}
    for side in SIDES:
        w, h = (dx, dz) if side in ('up', 'down') else (dx, dy) if side in ('north', 'south') else (dz, dy)
        face_uv = list(uv) if uv is not None else [(16-w)/2, (16-h)/2, (16+w)/2, (16+h)/2]
        faces[side] = {'texture': '#' + material, 'uv': face_uv}
    return {'from': list(bounds[:3]), 'to': list(bounds[3:]), 'faces': faces}


def model(name, elements):
    write(ASSETS / 'models' / (FOLDER + name + '.json'), {'parent': 'minecraft:block/block', 'textures': MATERIALS, 'elements': elements})
    return MOD + FOLDER + name


def octagon(y0, y1, radius, material):
    cut = radius * .4142
    return [box((8-cut, y0, 8-radius, 8+cut, y1, 8-cut), material),
            box((8-radius, y0, 8-cut, 8+radius, y1, 8+cut), material),
            box((8-cut, y0, 8+cut, 8+cut, y1, 8+radius), material)]


def main():
    frame = [box((0, 0, 0, 16, 2, 16), 'base'), box((1, 2, 1, 15, 4, 15), 'cast')]
    for x in (1, 12):
        for z in (2, 12):
            frame += [box((x, 4, z, x+3, 24, z+2), 'cast'), box((x+.6, 5, z+.25, x+2.4, 23, z+.6), 'steel')]
    for y in (4, 22):
        frame += [box((1, y, 2, 15, y+2, 4), 'steel'), box((1, y, 12, 15, y+2, 14), 'steel')]
    for x in (1, 13):
        for z in (1, 13):
            frame.append(box((x, 2, z, x+2, 3, z+2), 'brass', (6, 6, 10, 10)))
    frame += octagon(3, 5, 4, 'steel')
    frame += [box((4, 4, 4, 12, 5, 12), 'cast')]
    model('block', frame)
    variants = {}
    for face in ('floor', 'ceiling', 'wall'):
        for facing, angle in {'north': 0, 'east': 90, 'south': 180, 'west': 270}.items():
            x = 180 if face == 'ceiling' else 90 if face == 'wall' else 0
            y = (angle+180) % 360 if face == 'ceiling' else angle
            for water in ('true', 'false'):
                variants[f'face={face},facing={facing},waterlogged={water}'] = {'model': MOD + FOLDER + 'block', 'x': x, 'y': y}
    write(ASSETS / 'blockstates/reciprocating_compressor.json', {'variants': variants})
    # An item shows a complete stationary example without borrowing another mod's moving models.
    item = frame + [box((7, -1, 7, 9, 16, 9), 'steel'), box((5, 14, 4, 11, 18, 12), 'brass')]
    model('item', item)
    write(ASSETS / 'models/item/reciprocating_compressor.json', {'parent': MOD + FOLDER + 'item',
          'display': {'gui': {'rotation': [25, 225, 0], 'translation': [0, -2, 0], 'scale': [.5, .5, .5]}}})
    piston = [box((5.5, -2, 4.5, 10.5, 2, 11.5), 'steel'), box((7, -17, 7, 9, -2, 9), 'steel'),
              box((4.5, -1.2, 6.5, 11.5, 1.2, 9.5), 'brass'), box((5, -1, 3.5, 11, 1, 4.5), 'cast'), box((5, -1, 11.5, 11, 1, 12.5), 'cast')]
    # Keep JSON coordinates in the legal [-16,32] interval; piston pin is y=1 px.
    for element in piston:
        element['from'][1] += 1
        element['to'][1] += 1
    model('piston', piston)
    rod = [box((7, 0, 7, 9, 24, 9), 'steel'), box((6, 0, 6, 10, 3, 10), 'brass'), box((5, 21, 5, 11, 24, 11), 'brass'),
           box((7.4, 3, 6.5, 8.6, 21, 7), 'cast'), box((4, 21.5, 6, 12, 23.5, 10), 'steel')]
    model('linkage', rod)
    crank = [box((1, 6, 6, 15, 10, 10), 'steel'), box((4, 4, 4, 6, 12, 12), 'cast'),
             box((10, 4, 4, 12, 12, 12), 'cast'), box((5, 6.5, 10.5, 11, 9.5, 13.5), 'brass')]
    def disc(x0, x1, radius, material):
        for y in range(math.floor(8-radius), math.ceil(8+radius)):
            half = round(math.sqrt(max(0, radius*radius-(y+.5-8)**2)))
            if half:
                crank.append(box((x0, y, 8-half, x1, y+1, 8+half), material))
    for x in (3.5, 11.5):
        disc(x, x+1, 6, 'cast')
        disc(x-.2, x, 4.8, 'steel')
        disc(x+1, x+1.2, 4.8, 'steel')
        for y in (5, 10):
            for z in (5, 10):
                crank += [box((x-.4, y, z, x-.15, y+1, z+1), 'brass'),
                          box((x+1.15, y, z, x+1.4, y+1, z+1), 'brass')]
    model('crank', crank)
    cylinder = [box((0, 0, 0, 16, 2, 16), 'base')]
    cylinder += octagon(2, 14, 6, 'cast')
    for y in (3, 5, 7, 9, 11): cylinder += octagon(y, y+.7, 7, 'fins')
    cylinder += octagon(13.5, 16, 7.5, 'steel')
    for x in (2, 12):
        for z in (2, 12): cylinder.append(box((x, 15.4, z, x+2, 16, z+2), 'brass', (6, 6, 10, 10)))
    cylinder += [box((6, 15, 6, 10, 16, 10), 'brass'), box((6, 0, 6, 10, 2.5, 10), 'steel')]
    path = model('cylinder', cylinder)
    write(ASSETS / 'blockstates/reciprocating_cylinder.json', {'variants': {'': {'model': path}}})
    write(ASSETS / 'models/item/reciprocating_cylinder.json', {'parent': path})
    # The original white recolours are no longer used by the compressor.
    for name in ('reciprocating_engine', 'reciprocating_linkage', 'reciprocating_cylinder'):
        (ASSETS / 'textures/block' / (name + '.png')).unlink(missing_ok=True)
    (ASSETS / 'models/block/reciprocating_cylinder.json').unlink(missing_ok=True)
    print('Generated native compressor housing, finned cylinder, crosshead, connecting rod and crank')


if __name__ == '__main__':
    main()
