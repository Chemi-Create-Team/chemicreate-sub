"""Gantry crane: legs, drive, the drawn bridge, trolley and grapple, the spent absorber item, data, translations and Ponder."""
import json
from pathlib import Path
from minecraft_style import grain
from generate_reactor_assets import A, D, NS, write
from generate_petrochemical_ponder import Structure, OUT, string, named, integer
from generate_rbmk_assets import shade, save, box, face, IRON, STEEL, WARNING

T = A / 'textures/block/crane'
PAINT = (174, 154, 75)
FRAME = (102, 117, 115)
MAST = (192, 199, 181)
RAIL = (132, 83, 57)
DARK = (64, 68, 72)
ANDESITE = (110, 119, 112)
QUARRY_RED = (139, 67, 48)
QUARRY_DARK_RED = (83, 43, 38)
QUARRY_STEEL = (58, 62, 66)
QUARRY_BRASS = (174, 132, 62)


def tex(name): return NS + 'block/crane/' + name


def paint(x, y, name, edge=True):
    """Ochre machinery paint on steel: muted, with worn edges showing the metal."""
    v = grain(name, x, y, 1.0) + (-2, 1, 0, 2)[y % 4]
    if edge and (x in (0, 15) or y in (0, 15)): return shade(IRON, -30)
    if edge and (x == 1 or y == 1): return shade(PAINT, 18)
    if edge and (x == 14 or y == 14): return shade(PAINT, -20)
    return shade(PAINT, v)


def girder(x, y):
    """Side of the bridge's box girder, with black and yellow ends left to the end trucks."""
    if y in (0, 1): return shade(IRON, 10 if y == 1 else -30)
    if y in (14, 15): return shade(IRON, -24 if y == 15 else 6)
    if x % 8 == 3 and y in (4, 11): return shade(ANDESITE, 20)
    return paint(x, y, 'crane_girder', edge=False)


def girder_top(x, y):
    if y in (0, 15): return shade(IRON, -30)
    return shade(IRON, grain('crane_girder_top', x, y, 1.2) + (12 if y == 1 else -10 if y == 14 else 0))


def truck(x, y):
    if y >= 11:
        r = ((x % 8) - 3.5) ** 2 + (y - 12.5) ** 2
        if r < 8: return shade(DARK, 20 if r < 2 else -10)
    if y in (9, 10): return shade(WARNING[(x + y) // 2 % 2], 0)
    return paint(x, y, 'crane_truck')


def reactor_carriage(x, y):
    """An andesite pinion housing in the restrained ochre reactor-service palette."""
    if x in (0, 15) or y in (0, 15): return shade(IRON, -30)
    if x in (1, 14) or y in (1, 14): return shade(PAINT, 14 if x == 1 or y == 1 else -12)
    radius = (x - 7.5) ** 2 + (y - 7.5) ** 2
    if radius < 2.5: return shade((181, 144, 58), 12 if x + y < 15 else -12)
    if radius < 15: return shade(DARK, 18 if y < 7 else -20)
    if radius < 27: return shade(ANDESITE, -24 if (x + y) % 3 else 4)
    if (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)): return shade(STEEL, -12)
    return shade(FRAME, grain('reactor_carriage', x, y, 1.0) + (8 if x < 8 else -8))


def trolley(x, y):
    if y in (0, 1, 14, 15): return shade(STEEL, 10 if y in (1, 14) else -25)
    if x in (0, 15): return shade(DARK, -12)
    return shade((113, 123, 84), grain('crane_actuator', x, y, .8) + (14 if x <= 2 else -16 if x >= 13 else 0))


def grapple(x, y):
    if y <= 2: return shade(WARNING[(x + y) // 2 % 2], 0)
    return shade(MAST, (12, 3, -5, -14)[x % 4] + grain('crane_grapple', x, y, .8))


def mast(x, y):
    if y in (0, 1, 14, 15):
        if x in (3, 12) and y in (1, 14): return shade(DARK, -10)
        return shade(STEEL, 12 if y in (1, 14) else -20)
    if x in (0, 15): return shade(MAST, -30)
    if x in (1, 2): return shade(MAST, 12)
    if x in (13, 14): return shade(MAST, -15)
    if x == 10 and 5 <= y <= 11: return shade(IRON, -15)
    return shade(MAST, grain('crane_ivory_mast', x, y, .7))


def blue_frame(x, y):
    if x in (0, 15) or y in (0, 15): return shade(FRAME, -24)
    if x in (1, 14) or y == 1: return shade(FRAME, 18)
    if x in (3, 12) and y in (3, 12): return shade(STEEL, -15)
    return shade(FRAME, grain('crane_frame_plate', x, y, .9))


def drive_side(x, y):
    """The cab side: painted housing with a sight window onto the hoist drum and a running lamp."""
    if 4 <= x <= 11 and 3 <= y <= 8:
        if x in (4, 11) or y in (3, 8): return shade(IRON, -26)
        return shade((120, 150, 160), 20 if x + y < 12 else -10)
    if 11 <= y <= 12 and 6 <= x <= 9: return shade((90, 120, 70), 0)
    return paint(x, y, 'crane_drive')


def drive_end(x, y):
    if 6 <= x <= 9 and 6 <= y <= 9: return None
    if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 4.4 ** 2: return shade(PAINT, 24 if y < x else -18)
    return paint(x, y, 'crane_drive_end')


def drive_top(x, y):
    if 1 < x < 14 and y in (2, 3, 12, 13): return shade(WARNING[(x + y) // 2 % 2], 0)
    return paint(x, y, 'crane_drive_top')


def quarry_girder(x, y):
    """Red-oxide structural steel with one repeated diagonal brace per bridge section."""
    if y in (0, 15) or x in (0, 15): return shade(QUARRY_DARK_RED, -18)
    if y in (1, 14) or x in (1, 14): return shade(QUARRY_RED, 20 if y == 1 else -8)
    diagonal = abs(y - (15 - x)) <= 1
    if diagonal: return shade(QUARRY_RED, 18 if y < 8 else -4)
    if (x, y) in ((3, 3), (12, 12)): return shade(QUARRY_BRASS, 8)
    return shade(QUARRY_DARK_RED, grain('quarry_girder', x, y, 1.4) + (8 if y < 7 else -6))


def quarry_girder_top(x, y):
    if x in (0, 15) or y in (0, 15): return shade(QUARRY_DARK_RED, -22)
    if x in (1, 14) or y in (1, 14): return shade(QUARRY_RED, 18 if x in (1, 14) else 8)
    if x in (4, 11) and y in (4, 11): return shade(QUARRY_BRASS, -2)
    return shade(QUARRY_RED, grain('quarry_girder_top', x, y, 1.3) + (5 if x < 8 else -8))


def quarry_truck(x, y):
    if y <= 7:
        if x in (0, 15) or y == 0: return shade(QUARRY_DARK_RED, -18)
        if abs(y - (8 - abs(x - 7.5) // 2)) <= .5: return shade(QUARRY_RED, 12)
        return shade(QUARRY_DARK_RED, grain('quarry_truck_frame', x, y, 1.2))
    wheel = min((x - 4) ** 2, (x - 11) ** 2) + (y - 11.5) ** 2
    if wheel < 10: return shade(QUARRY_STEEL, 18 if wheel < 2 else -24 if wheel > 7 else grain('quarry_wheel', x, y, 1.3))
    if y in (14, 15): return shade(QUARRY_DARK_RED, -24 if y == 15 else 8)
    return shade(QUARRY_STEEL, grain('quarry_truck', x, y, 1.2) - 8)


def quarry_carriage(x, y):
    """A dark pinion housing with a red-oxide frame, matching Create's readable carriage silhouette."""
    if x in (0, 15) or y in (0, 15): return shade(QUARRY_DARK_RED, -24)
    if x in (1, 14) or y in (1, 14): return shade(QUARRY_RED, 12 if x == 1 or y == 1 else -8)
    radius = (x - 7.5) ** 2 + (y - 7.5) ** 2
    if radius < 2.5: return shade(QUARRY_BRASS, 8 if x + y < 15 else -12)
    if radius < 16: return shade(QUARRY_STEEL, 22 if y < 7 else -18)
    if radius < 27: return shade(QUARRY_STEEL, -28 if (x + y) % 3 else -10)
    if (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)): return shade(QUARRY_BRASS, -2)
    return shade((45, 48, 51), grain('quarry_carriage', x, y, 1.3))


def quarry_trolley(x, y):
    if x in (0, 15) or y in (0, 15): return shade(QUARRY_STEEL, -30)
    if x in (1, 14) or y == 1: return shade(QUARRY_STEEL, 16)
    if 4 <= x <= 11 and 5 <= y <= 11:
        if x in (4, 11) or y in (5, 11): return shade(QUARRY_BRASS, -8)
        return shade((45, 48, 51), grain('quarry_hoist_drum', x, y, 1.5) + (8 if y % 3 == 0 else -8))
    if x in (3, 12): return shade(QUARRY_RED, 8 if x == 3 else -10)
    return shade(QUARRY_STEEL, grain('quarry_trolley', x, y, 1.6))


def quarry_drill_stem(x, y):
    """A rigid square kelly stem: four steel rails, red collars and a dark central drive shaft."""
    if y in (0, 1, 14, 15): return shade(QUARRY_RED, 14 if y in (1, 14) else -22)
    if x in (3, 4): return shade(QUARRY_STEEL, 18 if x == 3 else -12)
    if x in (11, 12): return shade(QUARRY_STEEL, 8 if x == 11 else -22)
    if 6 <= x <= 9: return shade((40, 43, 46), 12 if x <= 7 else -16)
    if x in (2, 13): return shade(QUARRY_DARK_RED, grain('quarry_drill_stem', x, y, .8))
    return None


def quarry_grapple(x, y):
    if y <= 2: return shade(QUARRY_RED, 10 if y == 1 else -20)
    return shade(QUARRY_STEEL, (16, 4, -8, -18)[x % 4] + grain('quarry_grapple', x, y, 1.2))


def quarry_drive_side(x, y):
    if x in (0, 15) or y in (0, 15): return shade(QUARRY_DARK_RED, -24)
    if x in (1, 14) or y == 1: return shade(QUARRY_RED, 20)
    if 3 <= x <= 12 and 4 <= y <= 10:
        if x in (3, 12) or y in (4, 10): return shade(QUARRY_STEEL, -20)
        if y in (6, 8): return shade((35, 38, 41), -20)
        return shade(QUARRY_STEEL, grain('quarry_drive_vent', x, y, 1.5) - 8)
    if (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)): return shade(QUARRY_BRASS, 2)
    return shade(QUARRY_RED, grain('quarry_drive_side', x, y, 1.3))


def quarry_drive_end(x, y):
    if 6 <= x <= 9 and 6 <= y <= 9: return None
    radius = (x - 7.5) ** 2 + (y - 7.5) ** 2
    if radius < 4.6 ** 2: return shade(QUARRY_STEEL, 18 if y < x else -22)
    return quarry_girder_top(x, y)


def quarry_drive_top(x, y):
    if 3 <= x <= 12 and 3 <= y <= 12:
        if x in (3, 12) or y in (3, 12): return shade(QUARRY_STEEL, -20)
        return shade((43, 46, 49), grain('quarry_drive_hatch', x, y, 1.4))
    return quarry_girder_top(x, y)


def textures():
    save('girder', girder, T)
    save('girder_top', girder_top, T)
    save('truck', truck, T)
    save('reactor_carriage', reactor_carriage, T)
    save('trolley', trolley, T)
    save('mast', mast, T)
    save('frame', blue_frame, T)
    save('railing', lambda x, y: shade(RAIL, grain('crane_railing', x, y, .7) + (14 if x < 6 else -15 if x > 11 else 0)), T)
    save('grapple', grapple, T)
    save('drive_side', drive_side, T)
    save('drive_end', drive_end, T)
    save('drive_top', drive_top, T)
    save('drive_inner', lambda x, y: shade(DARK, grain('crane_inner', x, y, 1)), T)
    save('quarry_girder', quarry_girder, T)
    save('quarry_girder_top', quarry_girder_top, T)
    save('quarry_truck', quarry_truck, T)
    save('quarry_carriage', quarry_carriage, T)
    save('quarry_trolley', quarry_trolley, T)
    save('quarry_drill_stem', quarry_drill_stem, T)
    save('quarry_grapple', quarry_grapple, T)
    save('quarry_drive_side', quarry_drive_side, T)
    save('quarry_drive_end', quarry_drive_end, T)
    save('quarry_drive_top', quarry_drive_top, T)



def all_faces(t, uv=(0, 0, 16, 16)):
    return {f: face(list(uv), t) for f in ('north', 'south', 'east', 'west', 'up', 'down')}


def model(name, textures, elements, render='minecraft:cutout'):
    data = {'parent': 'minecraft:block/block', 'textures': textures, 'render_type': render, 'elements': elements}
    write(A / 'models/block/crane' / f'{name}.json', data)
    return NS + 'block/crane/' + name


def cuboid(b, side, top):
    """A box whose faces show the matching part of the side and top textures."""
    x1, y1, z1, x2, y2, z2 = b
    return box(b, {'north': face([x1, 16 - y2, x2, 16 - y1], side), 'south': face([x1, 16 - y2, x2, 16 - y1], side),
                   'east': face([z1, 16 - y2, z2, 16 - y1], side), 'west': face([z1, 16 - y2, z2, 16 - y1], side),
                   'up': face([x1, z1, x2, z2], top), 'down': face([x1, z1, x2, z2], top)})


def models():
    # The drive: shaft along Z, end plates with a 4 px opening for the shaft, the cab on the other four faces.
    t = {'side': tex('drive_side'), 'end': tex('drive_end'), 'top': tex('drive_top'), 'inner': tex('drive_inner'), 'particle': tex('drive_top')}
    elements = [box((0, 0, 2, 16, 16, 14), {'east': face([0, 0, 16, 16], 'side', 'east'), 'west': face([0, 0, 16, 16], 'side', 'west'),
                                            'up': face([0, 0, 16, 16], 'top', 'up'), 'down': face([0, 0, 16, 16], 'top', 'down'),
                                            'north': face([6, 6, 10, 10], 'inner'), 'south': face([6, 6, 10, 10], 'inner')})]
    for z, Z, end in ((0, 2, 'north'), (14, 16, 'south')):
        for x, y, X, Y in ((0, 0, 16, 6), (0, 10, 16, 16), (0, 6, 6, 10), (10, 6, 16, 10)):
            elements.append(box((x, y, z, X, Y, Z), {
                end: face([16 - X, 16 - Y, 16 - x, 16 - y] if end == 'north' else [x, 16 - Y, X, 16 - y], 'end', end),
                'east': face([16 - Z, 16 - Y, 16 - z, 16 - y], 'side' if X == 16 else 'inner', 'east' if X == 16 else None),
                'west': face([z, 16 - Y, Z, 16 - y], 'side' if x == 0 else 'inner', 'west' if x == 0 else None),
                'up': face([x, z, X, Z], 'top' if Y == 16 else 'inner', 'up' if Y == 16 else None),
                'down': face([x, 16 - Z, X, 16 - z], 'top' if y == 0 else 'inner', 'down' if y == 0 else None)}))
    drive = model('drive', t, elements)
    quarry_drive = model('quarry_drive', {
        'side': tex('quarry_drive_side'), 'end': tex('quarry_drive_end'), 'top': tex('quarry_drive_top'),
        'inner': tex('drive_inner'), 'particle': tex('quarry_drive_top')}, elements)
    # Partial models drawn by the renderer; the girder and trucks run along X, across the rails.
    girder_parts = [
        box((0, 2, 0, 16, 10, 16), {'north': face([0, 4, 16, 12], 'side'), 'south': face([0, 4, 16, 12], 'side'),
                                    'up': face([0, 0, 16, 16], 'top'), 'down': face([0, 0, 16, 16], 'top'),
                                    'east': face([0, 2, 16, 10], 'top'), 'west': face([0, 2, 16, 10], 'top')}),
        box((0, 10, -2, 16, 11, 18), all_faces('top'))]
    model('girder', {'side': tex('girder'), 'top': tex('girder_top'), 'particle': tex('girder')}, girder_parts)
    model('quarry_girder', {'side': tex('quarry_girder'), 'top': tex('quarry_girder_top'), 'particle': tex('quarry_girder')}, girder_parts)
    truck_parts = [
        box((0, 0, -2, 16, 11, 18), {'north': face([0, 5, 16, 16], 'side'), 'south': face([0, 5, 16, 16], 'side'),
                                    'east': face([1, 5, 15, 16], 'side'), 'west': face([1, 5, 15, 16], 'side'),
                                    'up': face([0, 1, 16, 15], 'top'), 'down': face([0, 1, 16, 15], 'top')})]
    carriage_wheels = []
    for x in (2, 11):
        for z in (1, 9):
            carriage_wheels.append(box((x, -13, z, x + 3, -5, z + 6), {
                'east': face([5, 4, 11, 12], 'carriage'), 'west': face([5, 4, 11, 12], 'carriage'),
                'north': face([2, 4, 5, 12], 'dark'), 'south': face([11, 4, 14, 12], 'dark'),
                'up': face([2, 1, 5, 7], 'dark'), 'down': face([11, 9, 14, 15], 'dark')}))
    carriage_parts = [
        box((2, -5, 2, 14, 0, 14), all_faces('frame', (2, 2, 14, 14))),
        box((2, -7, 2, 5, -4, 14), all_faces('dark', (2, 2, 14, 14))),
        box((11, -7, 2, 14, -4, 14), all_faces('dark', (2, 2, 14, 14)))] + carriage_wheels
    model('end_truck', {
        'side': tex('truck'), 'top': tex('girder_top'), 'frame': tex('girder_top'),
        'dark': tex('drive_inner'), 'carriage': tex('reactor_carriage'), 'particle': tex('truck')}, truck_parts + carriage_parts)
    model('quarry_end_truck', {
        'side': tex('quarry_truck'), 'top': tex('quarry_girder_top'), 'frame': tex('quarry_girder_top'),
        'dark': tex('quarry_trolley'), 'carriage': tex('quarry_carriage'), 'particle': tex('quarry_truck')}, truck_parts + carriage_parts)
    def part(bounds, texture):
        x, y, z, X, Y, Z = bounds
        return box(bounds, {
            'up': face([x, z, X, Z], texture), 'down': face([x, z, X, Z], texture),
            'north': face([x, 16 - (y % 16 + Y - y), X, 16 - y % 16], texture),
            'south': face([x, 16 - (y % 16 + Y - y), X, 16 - y % 16], texture),
            'east': face([z, 16 - (y % 16 + Y - y), Z, 16 - y % 16], texture),
            'west': face([z, 16 - (y % 16 + Y - y), Z, 16 - y % 16], texture)})
    trolley_parts = [part((0, 0, 0, 16, 3, 16), 'paint'), part((0, 27, 0, 16, 29, 16), 'frame'), part((5, 3, 5, 11, 16, 11), 'mast'), part((5, 16, 5, 11, 27, 11), 'actuator')]
    for x in (1, 13):
        for z in (1, 13):
            for y, Y in ((3, 16), (16, 27)):
                trolley_parts.append(part((x, y, z, x + 2, Y, z + 2), 'frame'))
            trolley_parts.append(box((x, 29, z, x + 1, 32, z + 1), all_faces('rail', (0, 0, 1, 3))))
    for z in (1, 14): trolley_parts.append(box((1, 31, z, 15, 32, z + 1), all_faces('rail', (0, 0, 14, 1))))
    model('trolley', {'frame': tex('frame'), 'paint': tex('girder'), 'mast': tex('mast'), 'actuator': tex('trolley'), 'rail': tex('railing'), 'particle': tex('frame')}, trolley_parts)
    quarry_trolley_parts = [box((1, 14, -4, 15, 17, 20), all_faces('red')), box((3, -6, 3, 13, 2, 13), all_faces('dark'))]
    for z,Z in ((-4,0),(16,20)): quarry_trolley_parts.append(box((1, 0, z, 15, 14, Z), all_faces('dark')))
    for x in (1, 11):
        for z in (-4, 16): quarry_trolley_parts.append(box((x, 9, z, x + 4, 14, z + 4), all_faces('dark')))
    model('quarry_trolley', {'red': tex('quarry_girder'), 'dark': tex('quarry_trolley'), 'particle': tex('quarry_trolley')}, quarry_trolley_parts)
    model('quarry_drill_stem_segment', {'stem': tex('quarry_drill_stem'), 'particle': tex('quarry_drill_stem')}, [
        part((3, 0, 3, 5, 16, 5), 'stem'), part((11, 0, 3, 13, 16, 5), 'stem'),
        part((3, 0, 11, 5, 16, 13), 'stem'), part((11, 0, 11, 13, 16, 13), 'stem'),
        part((7, 0, 7, 9, 16, 9), 'stem'),
        part((3, 0, 3, 13, 2, 13), 'stem'), part((3, 14, 3, 13, 16, 13), 'stem')])
    model('mast_segment', {'m': tex('mast'), 'particle': tex('mast')}, [part((5, 0, 5, 11, 16, 11), 'm')])
    model('mast_lower_segment', {'m': tex('mast'), 'particle': tex('mast')}, [part((3, 0, 3, 13, 16, 13), 'm')])
    model('mast_head', {'m': tex('mast'), 'p': tex('girder'), 's': tex('grapple'), 'particle': tex('mast')}, [
        part((3, 3, 3, 13, 14, 13), 'm'), part((2, 13, 2, 14, 16, 14), 'p'),
        part((4, 0, 4, 12, 4, 12), 's')])
    grapple_parts = [
        box((4, 10, 4, 12, 16, 12), all_faces('g', (4, 0, 12, 6))),
        box((3, 0, 6, 5, 10, 10), all_faces('g', (0, 3, 2, 13))),
        box((11, 0, 6, 13, 10, 10), all_faces('g', (0, 3, 2, 13))),
        box((6, 0, 3, 10, 10, 5), all_faces('g', (0, 3, 2, 13))),
        box((6, 0, 11, 10, 10, 13), all_faces('g', (0, 3, 2, 13)))]
    model('grapple', {'g': tex('grapple'), 'particle': tex('grapple')}, grapple_parts)
    model('quarry_grapple', {'g': tex('quarry_grapple'), 'particle': tex('quarry_grapple')}, grapple_parts)
    # Both crane drives share the model; the facing only says which way the rails run.
    rotation = {'z': {}, 'x': {'y': 90}, 'y': {'x': 90}}
    for id in ('crane_drive', 'fusion_crane_drive'):
        write(A / 'blockstates' / f'{id}.json', {'variants': {f'axis={axis},facing={facing}': {'model': drive, **rotation[axis]}
                                                              for axis in 'xyz' for facing in ('north', 'east', 'south', 'west')}})
        write(A / 'models/item' / f'{id}.json', {'parent': drive})
    quarry_blockstate()
    write(A / 'models/item/quarry_drive.json', {'parent': quarry_drive})


def data():
    for id in ('crane_drive', 'fusion_crane_drive', 'quarry_drive'):
        write(D / 'loot_table/blocks' / f'{id}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + id}],
                                                                                             'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    tag = A.parents[1] / 'data/minecraft/tags/block/mineable/pickaxe.json'; values = json.loads(tag.read_text(encoding='utf8'))
    for id in ('crane_drive', 'fusion_crane_drive', 'quarry_drive'):
        if NS + id not in values['values']: values['values'].append(NS + id)
    write(tag, values)
    r = D / 'recipe'
    write(r / 'crane_drive.json', {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'create:gantry_carriage'}, {'item': 'create:rope_pulley'},
                                                                                         {'item': 'create:precision_mechanism'}, {'item': 'create:brass_casing'}],
                                   'result': {'id': NS + 'crane_drive', 'count': 1}})
    write(r / 'fusion_crane_drive.json', {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'create:gantry_carriage'}, {'item': 'create:rope_pulley'},
                                                                                                {'item': 'create:precision_mechanism'}, {'item': NS + 'fusion_reinforced_casing'}],
                                          'result': {'id': NS + 'fusion_crane_drive', 'count': 1}})
    write(r / 'quarry_drive.json', {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'create:gantry_carriage'}, {'item': 'create:mechanical_drill'},
                                                                                          {'item': 'create:precision_mechanism'}, {'item': 'create:andesite_casing'}],
                                    'result': {'id': NS + 'quarry_drive', 'count': 1}})


NAMES = {
    'block.create_refinery_n_reactors.crane_drive': ('RBMK Fuel Crane Drive', 'RBMK燃料交換クレーン駆動部'),
    'block.create_refinery_n_reactors.fusion_crane_drive': ('Fusion Maintenance Crane Drive', '核融合炉保守クレーン駆動部'),
    'block.create_refinery_n_reactors.quarry_drive': ('Quarry Drive', 'クァーリー駆動部'),
}
CRANE = {
    'frame_missing': ('Missing: metal girders %s', '不足：メタルガーダー %s'),
    'crane_length': ('Rail length, forward from the drive', 'レールの長さ（正面方向）'),
    'crane_width': ('Span to the right of the rails', 'スパン（右側へ）'),
    'status.frame_build': ('Building the configured quarry frame', '設定した範囲の枠組みを建設中'),
    'status.frame_supplies': ('Out of metal girders for the next part', '次の部材のメタルガーダーが足りない'),
    'status.frame_blocked': ('Something where the frame goes cannot be cleared', '枠組みの位置にあるブロックを取り除けない'),
    'frame_progress': ('Frame: %s of %s parts built', '枠組み：%s / %s 個を建設'),
    'status.structure': ('Not a complete gantry crane', '門型クレーンが完成していない'),
    'status.rails': ('Two parallel rails of horizontal metal girders are needed, the drive in line with one', '横向きのメタルガーダーの平行なレール2本が必要。駆動部は片方のレールの延長上に置く'),
    'status.length': ('Rails must be 5 to 64 blocks long', 'レールの長さは5～64ブロック'),
    'status.legs': ('Each rail end needs a leg of upright metal girders down to solid ground, or must rest on a solid block', 'レールの両端は、地面まで届く縦向きのメタルガーダーの脚で支えるか、固いブロックの上に載せる'),
    'status.redstone': ('Stopped: give the drive a redstone signal to run', '停止中：駆動部にレッドストーン信号を入れると動く'),
    'status.speed': ('The drive is not turning', '駆動部が回転していない'),
    'status.drill': ('Fit a Create mechanical drill to quarry', '採掘にはCreateのメカニカルドリルを取り付ける'),
    'status.moving': ('Moving', '移動中'),
    'status.mining': ('Mining', '採掘中'),
    'status.refuelling': ('Exchanging fuel rods', '燃料棒を交換中'),
    'status.renewing': ('Renewing control rod absorbers', '制御棒吸収体を交換中'),
    'status.waiting': ('Waiting: nothing to service', '待機中：保守する箇所が無い'),
    'status.pool_full': ('Pool full: free a rack place', 'プールが満杯：ラックに空きを作る'),
    'status.pool_unavailable': ('Pool unavailable: repair it, fill it and let it cool down', 'プール未準備：構造・水位を確認し、冷えるまで冷却する'),
    'status.pool_fuel': ('Waiting for fresh fuel in the pool', 'プールの新燃料を待機中'),
    'status.clearance': ('Align reactor caps and pool water exactly 15 blocks below the rails', '炉のキャップとプール水面をレールの15マス下にそろえる'),
    'status.reactor': ('No reachable formed reactor below the crane', 'クレーンの下に到達可能な形成済み原子炉が無い'),
    'frame_overhead': ('%s x %s span on beams; %s with %s', '梁支持、長さ%s×幅%s：%s（%s）'),
    'status.full': ('Head bin full: clear the end of the side belt', 'ヘッドの箱が満杯：側面ベルトの出口を空けてください'),
    'status.belt_blocked': ('Something unbreakable stands in the side belt\'s line', '側面ベルトの位置に壊せない物があります'),
    'status.belt_supplies': ('Put a belt connector into the drive for the side belt', '側面ベルト用にベルトコネクターを駆動部へ入れてください'),
    'quarry_frame': ('%s × %s span, legs up to %s high', '長さ%s×幅%s、脚は最大%s段'),
    'quarry_flow': ('Head bin %s items, %s parcels on the way', 'ヘッドの箱 %s個、搬送中 %s便'),
    'status.unloaded': ('Part of the work area is unloaded', '作業範囲の一部が読み込まれていない'),
    'status.quarry_done': ('Quarry finished', '採掘完了'),
    'status.idle': ('Ready', '準備完了'),
    'fault': ('Check X %s, Y %s, Z %s', '確認位置 X %s, Y %s, Z %s'),
    'frame': ('%s × %s span, legs %s high; %s with %s', '長さ%s×幅%s、脚%s段：%s（%s）'),
    'grapple': ('grapple', '吊り具'),
    'hoist': ('Hoist %s blocks down; quarry layer Y %s', '吊り具の深さ %s ブロック、採掘層 Y %s'),
    'store': ('Supplies %s, taken out %s items', '補給品 %s、搬出待ち %s 個'),
}
DISPLAY = {'crane_depth': ('Crane hoist depth', 'クレーン吊り具の深さ'), 'crane_supplies': ('Crane supplies', 'クレーンの補給品')}
PONDER = ('Gantry crane', '門型クレーン', [
    ('legs', 'Stand four legs of Create metal girders, all the same height, at the corners of the area to work.', '作業範囲の四隅に、Createの鉄骨を同じ高さに積んで脚を立てます。'),
    ('rails', 'Lay two parallel gantry-shaft rails: length 5–64, spacing 4–65 blocks. Reactor caps and pool water go 15 blocks below.', '平行なガントリー軸を2本敷きます。長さ5～64、間隔4～65マス。炉のキャップと池面は15マス下です。'),
    ('drive', 'Place the drive in line with a rail. Supply rotation and a redstone signal.',
     '駆動部をレールの延長上へ置き、回転とレッドストーン信号を入れます。'),
    ('service', 'The crane moves between the reactor and pool to exchange fuel rods.',
     'クレーンが炉とプールを移動し、燃料棒を交換します。'),
])


REFUEL = ('Fuel transfer over a reactor and pool', '炉とプールを結ぶ燃料交換', [
    ('layout', 'Build a reactor and an open fuel pool beside each other, under the same pair of rails. Support all four rail ends on building beams or Create metal girders.', '原子炉と開放型燃料プールを隣接させ、共通の走行レールで覆います。レールの四隅は建屋の梁やCreateの鉄骨で支持できます。'),
    ('prepare', 'Align caps and pool water 15 blocks below the rails. Keep the pool 22 blocks deep, with 22,000 mB per rack below 90 °C.', 'キャップと池面をレールの15マス下にそろえます。池は深さ22マス、水はラック1枚22,000 mB以上・90 °C未満です。'),
    ('take', 'With rotation and redstone, reactor service lifts a spent rod from a fuel channel and returns it to an empty rack place in the pool.', '回転とレッドストーン信号で原子炉保守を開始します。燃料列から使用済み燃料を1本ずつ引き抜き、プールの空きラックへ戻します。'),
    ('load', 'The crane takes a fresh rod from the pool and loads the selected cap position.', 'プールの新しい燃料棒を取り、選ばれたキャップの位置へ装荷します。'),
    ('wait', 'A full or unready pool pauses transfer with the rod retained. Read the reason with goggles or a Display Link, repair or cool the pool, and the crane resumes.', 'プールの満杯・未準備時は燃料を保持して待機します。ゴーグルやディスプレイリンクで原因を確認し、修復・冷却すると再開します。'),
])


_quarry_lessons = json.loads((Path(__file__).resolve().parents[1] / 'tools/data/ponder_lessons.json').read_text())
QUARRY_FRAME = (_quarry_lessons['quarry_frame.title']['en_us'], _quarry_lessons['quarry_frame.title']['ja_jp'], [
    (key.removeprefix('quarry_frame.'), text['en_us'], text['ja_jp'])
    for key, text in _quarry_lessons.items() if key.startswith('quarry_frame.') and key != 'quarry_frame.title'
])


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; data = json.loads(raw)
        for key, values in NAMES.items(): data[key] = values[index]
        for key, values in CRANE.items(): data['crane.create_refinery_n_reactors.' + key] = values[index]
        for key, values in DISPLAY.items(): data['display.create_refinery_n_reactors.' + key] = values[index]
        title = PONDER[index]
        data['create_refinery_n_reactors.ponder.shared.gantry_crane.title'] = title
        data['create_refinery_n_reactors.ponder.gantry_crane.header'] = title
        for key, en, ja in PONDER[2]: data['create_refinery_n_reactors.ponder.shared.gantry_crane.' + key] = (en, ja)[index]
        data['create_refinery_n_reactors.ponder.shared.quarry_frame.title'] = QUARRY_FRAME[index]
        data['create_refinery_n_reactors.ponder.quarry_frame.header'] = QUARRY_FRAME[index]
        for key, en, ja in QUARRY_FRAME[2]: data['create_refinery_n_reactors.ponder.shared.quarry_frame.' + key] = (en, ja)[index]
        data['create_refinery_n_reactors.ponder.shared.crane_refuelling.title'] = REFUEL[index]
        data['create_refinery_n_reactors.ponder.crane_refuelling.header'] = REFUEL[index]
        for key, en, ja in REFUEL[2]: data['create_refinery_n_reactors.ponder.shared.crane_refuelling.' + key] = (en, ja)[index]
        path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


def ponder():
    """Rails at y = 14 along x = 2 to 8, at z = 1 and z = 7; legs under their ends; the drive at x = 1, a motor at x = 0."""
    s = Structure(9, 17)
    for x in (2, 8):
        for z in (1, 7):
            for y in range(1, 14): s.block(x, y, z, 'create:metal_girder', axis='y', x='false', z='false', top='true', bottom='true', waterlogged='false')
    for z in (1, 7):
        for x in range(2, 9):
            s.block(x, 14, z, 'create:metal_girder', axis='x', x='true', z='false', top='false', bottom='true' if x in (2, 8) else 'false', waterlogged='false')
    s.block(1, 14, 1, NS + 'crane_drive', axis='x', facing='east')
    s.block(0, 14, 1, 'create:creative_motor', facing='east')
    for x, z, block in ((4, 3, 'minecraft:stone'), (5, 4, 'minecraft:iron_ore'), (6, 3, 'minecraft:stone'), (5, 5, 'minecraft:dirt')):
        s.block(x, 1, z, block)
    structure_save(s, 'gantry_crane')
    s = Structure(17, 49)
    lower, core_bottom, core_top, cap_y = 2, 9, 15, 30
    for x in range(3, 10):
        for z in range(3, 10):
            core = 5 <= x <= 7 and 5 <= z <= 7
            if not core:
                for y in range(lower, cap_y + 1): s.block(x, y, z, NS + 'biological_shield')
                continue
            for y in range(lower, core_bottom): s.block(x, y, z, NS + 'reactor_feed_bundle')
            for y in range(core_bottom, core_top + 1): s.block(x, y, z, NS + ('reactor_fuel_channel' if (x, z) == (5, 5) else 'reactor_graphite'))
            for y in range(core_top + 1, cap_y): s.block(x, y, z, NS + 'reactor_fuel_channel_tube')
            s.block(x, cap_y, z, NS + 'reactor_top_cap')
    for x in range(10, 14):
        for z in range(5, 9):
            if x in (10, 13) or z in (5, 8):
                for y in range(8, cap_y + 1): s.block(x, y, z, NS + 'steel_block')
            else: s.block(x, 8, z, NS + 'fuel_pool')
    for x in (2, 15):
        for z in (2, 12):
            for y in range(1, 45): s.block(x, y, z, 'create:industrial_iron_block')
    for z in (2, 12):
        for x in range(2, 16): s.block(x, 45, z, 'create:metal_girder', axis='x', x='true', z='false', top='false', bottom='true' if x in (2, 15) else 'false', waterlogged='false')
    s.block(1, 45, 2, NS + 'crane_drive', axis='x', facing='east')
    s.block(0, 45, 2, 'create:creative_motor', facing='east')
    s.block(1, 46, 2, 'minecraft:redstone_block')
    structure_save(s, 'crane_refuelling')
    quarry_frame()


def quarry_frame():
    """The quarry storyboard: the quarry drive at x = 3, its shaft along z, and its first rail at x = 4, z = 2 extending south over eight
    by five blocks of ground. The side belt the drive lays along z = 1 is stored too, for the scene to show once it stands."""
    s = Structure(13, 8)
    for x in range(4, 12):
        for z in range(3, 8):
            s.block(x, 1, z, 'minecraft:stone')
            s.block(x, 2, z, 'minecraft:stone')
            s.block(x, 3, z, 'minecraft:dirt')
    # Legs of upright girders reach the ground under each rail end; the rails are girders lying east.
    for x in (4, 11):
        for z in (2, 8):
            for y in range(1, 4): s.block(x, y, z, 'create:metal_girder', axis='y', x='false', z='false', top='true', bottom='true', waterlogged='false')
    for z in (2, 8):
        for x in range(4, 12):
            s.block(x, 4, z, 'create:metal_girder', axis='x', x='true', z='false', top='false', bottom='true' if x in (4, 11) else 'false', waterlogged='false')
    s.block(3, 4, 2, NS + 'quarry_drive', axis='z', facing='south')
    s.block(3, 4, 3, 'create:creative_motor', facing='north')
    s.block(3, 5, 2, 'create:chute', facing='down', shape='normal', waterlogged='false')
    s.block(3, 6, 2, 'minecraft:chest', facing='north', type='single', waterlogged='false')
    s.block(3, 3, 2, 'minecraft:lever', face='ceiling', facing='north', powered='false')
    for x in range(3, 12):
        s.block(x, 4, 0, 'create:encased_chain_drive', axis='z', axis_along_first='true', part='start' if x == 3 else 'end' if x == 11 else 'middle')
        part = 'start' if x == 11 else 'end' if x == 3 else 'middle'
        belt = [string('id', 'create:belt'), string('BeltFacing', 'west'), named(11, 'Controller', b''.join(integer(v) for v in (3, 11, 4, 1))),
                named(3, 'Length', integer(9)), named(3, 'Index', integer(11 - x))]
        s.block(x, 4, 1, 'create:belt', nbt=belt, casing='false', facing='west', part=part, slope='horizontal', waterlogged='false')
    structure_save(s, 'quarry_frame')



def structure_save(structure, id):
    structure.save(id)
    source = OUT / f'{id}.nbt'; dest = A / 'ponder/crane' / f'{id}.nbt'
    dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(source.read_bytes()); source.unlink()


def cleanup_obsolete():
    for path in (T / 'quarry_chain.png', A / 'models/block/crane/quarry_chain_segment.json'):
        if path.exists(): path.unlink()


def quarry_blockstate():
    variants = {}
    for direction, angle in [('south', 0), ('west', 90), ('north', 180), ('east', 270)]:
        variants['facing=' + direction] = {'model': NS[:-1] + ':block/crane/quarry_drive', **({'y': angle} if angle else {})}
    write(A / 'blockstates/quarry_drive.json', {'variants': variants})


def quarry_language():
    names = {
        'status.frame_build': ('Building the configured quarry frame', '設定した範囲の枠組みを建設中', '正在建造设定范围的框架'),
        'range_length': ('Length into the opposite side', '長さ（この面の反対側へ）', '长度（朝此面的反方向）'),
        'range_width': ('Width into the opposite side', '幅（この面の反対側へ）', '宽度（朝此面的反方向）'),
        'quarry_frame_missing': ('Missing: metal girders %s, drill %s', '不足：メタルガーダー %s・ドリル %s', '缺少：金属梁%s、钻头%s'),
        'quarry_belt_missing': ('Belts %s, shafts %s, chain drives %s', 'ベルト %s・シャフト %s・チェーンドライブ %s', '传送带%s、杆%s、链传动%s'),
        'status.belt_supplies': ('Supply belt connectors, shafts and chain drives', 'ベルトコネクター・シャフト・チェーンドライブを補給', '请补给传送带连接器、传动杆和链传动箱'),
        'status.belt_build': ('Building the belt and drive line', 'ベルトと動力伝達列を建設中', '正在建造传送带和传动线路'),
        'status.belt_layout': ('Recover mismatched old belts before rebuilding', '範囲と合わない既存ベルトを回収してください', '请先回收不符合新范围的旧传送带'),
        'status.belt_power': ('Reconnect the encased chain drive row', 'ケース入りチェーンドライブの接続を確認', '请检查封装链传动箱的连接'),
        'status.frame_dimensions': ('Recover frame parts outside the selected range', '設定範囲と合わない旧枠を回収してください', '请回收不符合新范围的旧框架'),
        'range_progress': ('Range %s × %s | frame %s/%s', '範囲 %s×%s｜枠 %s/%s', '范围%s×%s｜框架%s/%s'),
    }
    for index, code in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / (code + '.json'); data = json.loads(path.read_text())
        for key in list(data):
            if 'quarry_marker' in key or key.startswith('create_refinery_n_reactors.ponder.shared.quarry_frame.marker') or key in (
                'crane.create_refinery_n_reactors.status.frame_marker', 'crane.create_refinery_n_reactors.status.belt_length',
                'crane.create_refinery_n_reactors.quarry_short', 'crane.create_refinery_n_reactors.frame_short'):
                data.pop(key)
        for key, values in names.items(): data['crane.create_refinery_n_reactors.' + key] = values[index]
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    import sys
    if '--bridge-only' in sys.argv:
        selected={'girder.json','quarry_girder.json','end_truck.json','quarry_end_truck.json','quarry_trolley.json'}
        original_write=write
        def write(path,data):
            if path.name in selected: original_write(path,data)
        models()
        from generate_quarry_assets import chute
        chute()
        print('Generated crane bridge and quarry conveyor models')
    elif '--quarry-only' in sys.argv:
        previous = {code: list(json.loads((A / 'lang' / (code + '.json')).read_text())) for code in ('en_us', 'ja_jp', 'zh_cn')}
        quarry_frame(); quarry_blockstate(); quarry_language()
        from generate_ponder_language import generate
        generate()
        for code, keys in previous.items():
            path = A / 'lang' / (code + '.json'); data = json.loads(path.read_text())
            ordered = {key: data[key] for key in keys if key in data}
            ordered.update({key: value for key, value in data.items() if key not in ordered})
            path.write_text(json.dumps(ordered, ensure_ascii=False, indent=2) + '\n')
    else:
        cleanup_obsolete(); textures(); models(); data(); translations(); quarry_language(); ponder()
        print('Generated the gantry crane: textures, models, recipes, language and Ponder')
