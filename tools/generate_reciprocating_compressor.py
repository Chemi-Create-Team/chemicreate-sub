"""Models and 16 px materials for the reciprocating compressor."""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
MOD = 'create_refinery_n_reactors:'
FOLDER = 'block/reciprocating_compressor/'
MATERIALS = {name: MOD + 'block/gas_compressor/' + name for name in ('cast', 'steel', 'brass', 'fins', 'base', 'dial')}
MATERIALS['particle'] = MATERIALS['cast']
SHELL = {name: MOD + 'block/gas_compressor/' + name for name in ('painted', 'satin', 'pane')}
SHELL.update({name: MATERIALS[name] for name in ('cast', 'steel', 'base')})
SHELL['particle'] = SHELL['painted']
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


def model(name, elements, textures=MATERIALS, **options):
    write(ASSETS / 'models' / (FOLDER + name + '.json'), {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements, **options})
    return MOD + FOLDER + name


def octagon(y0, y1, radius, material):
    cut = radius * .4142
    return [box((8-cut, y0, 8-radius, 8+cut, y1, 8-cut), material),
            box((8-radius, y0, 8-cut, 8+radius, y1, 8+cut), material),
            box((8-cut, y0, 8+cut, 8+cut, y1, 8+radius), material)]


def opposed_textures():
    folder = ASSETS / 'textures/block/gas_compressor'
    for name, shades in {
        'painted': ((169, 181, 185), (179, 191, 195), (155, 167, 172)),
        'satin': ((128, 143, 151), (148, 166, 172), (108, 124, 133)),
    }.items():
        surface = Image.new('RGB', (16, 16), shades[0])
        draw = ImageDraw.Draw(surface)
        draw.line((0, 0, 15, 0), shades[1])
        draw.line((0, 15, 15, 15), shades[2])
        for x, y, length in ((2, 3, 6), (10, 5, 3), (3, 10, 4), (8, 12, 5)):
            draw.line((x, y, x + length, y), shades[(x + y) % 2 + 1])
        surface.save(folder / f'{name}.png')
    glass = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(glass)
    draw.rectangle((2, 2, 13, 13), outline=(121, 159, 167, 255))
    draw.line((3, 8, 8, 3), (191, 221, 220, 255))
    draw.line((10, 12, 12, 10), (153, 187, 195, 255))
    glass.save(folder / 'pane.png')


def opposed_drive():
    body = [box((0, 0, 0, 16, 2, 16), 'base'), box((1, 2, 1, 15, 3, 15), 'satin')]
    body += [box((1, 3, .5, 15, 14, 2), 'painted'), box((1, 3, 14, 15, 14, 15.5), 'painted')]
    body += [box((0, 3, 1, 2, 14, 3), 'painted'), box((14, 3, 1, 16, 14, 3), 'painted'),
             box((0, 3, 13, 2, 14, 15), 'painted'), box((14, 3, 13, 16, 14, 15), 'painted')]
    for x in (0, 14):
        body += [box((x, 3, 3, x + 2, 5, 13), 'satin'), box((x, 11, 3, x + 2, 14, 13), 'painted')]
    body += [box((5, 5, 0, 11, 11, 1), 'satin'), box((6, 6, 14, 10, 10, 16), 'steel')]
    body += [box((0, 14, 0, 16, 16, 2), 'painted'), box((0, 14, 14, 16, 16, 16), 'painted'),
             box((0, 14, 2, 2, 16, 14), 'painted'), box((14, 14, 2, 16, 16, 14), 'painted')]
    model('opposed_closed', body + [box((2, 14, 2, 14, 16, 14), 'painted')], SHELL)
    pane = box((2, 15.625, 2, 14, 15.875, 14), 'pane', (2, 2, 14, 14))
    pane['faces'] = {key: pane['faces'][key] for key in ('up', 'down')}
    model('opposed_window', body + [pane], SHELL, render_type='minecraft:cutout')
    guide = [box((0, 3, 3, 16, 4, 13), 'satin'), box((0, 12, 3, 16, 13, 13), 'painted'),
             box((0, 4, 3, 16, 12, 4), 'satin'), box((0, 4, 12, 16, 12, 13), 'satin')]
    model('opposed_guide', guide, SHELL)
    crank = [box((4, 6, 4.5, 12, 10, 6.5), 'cast'), box((4, 6, 9.5, 12, 10, 11.5), 'cast'),
             box((4.5, 7, 5, 6.5, 9, 8), 'steel'), box((9.5, 7, 8, 11.5, 9, 11), 'steel')]
    model('opposed_crank', crank, SHELL)
    model('opposed_rod', [box((0, -1, -1, 12, 1, 1), 'steel')], SHELL)
    model('opposed_piston_rod', [box((0, -1, -1, 20, 1, 1), 'steel')], SHELL)
    model('opposed_crosshead', [box((-2.5, -2, -3, 2.5, 2, 3), 'cast'),
          box((-1.5, -1, -3.25, 1.5, 1, 3.25), 'satin')], SHELL)
    piston_mesh()


def piston_mesh():
    # Diameter 8 px. The chamfer offsets give eight straight faces at 45 degree intervals.
    chamfer = math.sqrt(2) - 1
    profile = [(1, chamfer), (chamfer, 1), (-chamfer, 1), (-1, chamfer),
               (-1, -chamfer), (-chamfer, -1), (chamfer, -1), (1, -chamfer)]
    rings = [(-2.5, 3.5), (-2, 4), (-1.15, 4), (-1, 3.75), (-.85, 4),
             (.85, 4), (1, 3.75), (1.15, 4), (2, 4), (2.5, 3.5)]
    rows = ['mtllib opposed_piston.mtl', 'usemtl steel']
    for x, radius in rings:
        rows += [f'v {x / 16:.6f} {y * radius / 16:.6f} {z * radius / 16:.6f}' for y, z in profile]
    rows += [f'v {x / 16:.6f} 0 0' for x in (rings[0][0], rings[-1][0])]
    rows += ['vt 0.25 0.25', 'vt 0.75 0.25', 'vt 0.75 0.75', 'vt 0.25 0.75']
    for ring in range(len(rings) - 1):
        for i in range(8):
            j = (i + 1) % 8
            indices = (ring*8+i+1, ring*8+j+1, (ring+1)*8+j+1, (ring+1)*8+i+1)
            rows.append('f ' + ' '.join(f'{vertex}/{uv}' for uv, vertex in enumerate(indices, 1)))
    for i in (0, 2, 4, 6):
        end = (len(rings) - 1)*8
        for indices in ((len(rings)*8 + 1, (i+2)%8 + 1, i+2, i+1),
                        (len(rings)*8 + 2, end+i+1, end+i+2, end+(i+2)%8+1)):
            rows.append('f ' + ' '.join(f'{vertex}/{uv}' for uv, vertex in enumerate(indices, 1)))
    folder = ASSETS / 'models' / FOLDER
    (folder / 'opposed_piston.obj').write_text('\n'.join(rows) + '\n')
    (folder / 'opposed_piston.mtl').write_text('newmtl steel\nmap_Kd #steel\n')
    write(folder / 'opposed_piston.json', {'parent': 'minecraft:block/block', 'textures': SHELL,
        'loader': 'neoforge:obj', 'model': MOD + 'models/' + FOLDER + 'opposed_piston.obj', 'automatic_culling': False, 'flip_v': True})


def main():
    opposed_textures()
    opposed_drive()
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
                variants[f'opposed=false,face={face},facing={facing},waterlogged={water}'] = {'model': MOD + FOLDER + 'block', 'x': x, 'y': y}
    for facing, angle in {'north': 0, 'east': 90, 'south': 180, 'west': 270}.items():
        for window, suffix in (('false', 'closed'), ('true', 'window')):
            variants[f'opposed=true,facing={facing},window={window}'] = {'model': MOD + FOLDER + f'opposed_{suffix}', 'y': angle}
    write(ASSETS / 'blockstates/reciprocating_compressor.json', {'variants': variants})
    write(ASSETS / 'models/item/reciprocating_compressor.json', {'parent': MOD + FOLDER + 'opposed_closed'})
    (ASSETS / 'models' / FOLDER / 'item.json').unlink(missing_ok=True)
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
    cylinder = octagon(0, 2, 4, 'satin') + octagon(14, 16, 4, 'satin')
    cylinder += octagon(2, 4, 7.5, 'satin') + octagon(12, 14, 7.5, 'satin')
    for x in (2, 12):
        for z in (2, 12):
            cylinder += [box((x, 4, z, x+2, 12, z+2), 'painted'), box((x, 13.75, z, x+2, 14.75, z+2), 'cast')]
    for i in (2, 13):
        cylinder += [box((i, 4, 4, i+1, 5, 12), 'satin'), box((i, 11, 4, i+1, 12, 12), 'satin'),
                     box((4, 4, i, 12, 5, i+1), 'satin'), box((4, 11, i, 12, 12, i+1), 'satin')]
    path = model('cylinder', cylinder, SHELL)
    write(ASSETS / 'blockstates/reciprocating_cylinder.json', {'variants': {'': {'model': path}}})
    write(ASSETS / 'models/item/reciprocating_cylinder.json', {'parent': path})
    # The original white recolours are no longer used by the compressor.
    for name in ('reciprocating_engine', 'reciprocating_linkage', 'reciprocating_cylinder'):
        (ASSETS / 'textures/block' / (name + '.png')).unlink(missing_ok=True)
    (ASSETS / 'models/block/reciprocating_cylinder.json').unlink(missing_ok=True)
    print('Generated reciprocating compressor housings, cylinders and moving models')


if __name__ == '__main__':
    main()
