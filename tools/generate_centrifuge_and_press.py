#!/usr/bin/env python3
"""Gas centrifuge and cubic press: 16 px steel textures drawn pixel by pixel, their models, and the data and lessons around them."""
import json
from pathlib import Path
from PIL import Image
from generate_petrochemical_ponder import Structure, OUT
R=Path(__file__).resolve().parents[1];A=R/'atomics/src/main/resources/assets/create_refinery_n_reactors';D=R/'atomics/src/main/resources/data/create_refinery_n_reactors';NS='create_refinery_n_reactors:'

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')

# Bright casing steel and dark tool steel, each as lit face, body, shaded face and seam.
STEEL = {'L': '#c6cdce', 'B': '#a7afb1', 'S': '#868f92', 'D': '#646d71', 'K': '#464e53'}
TOOL = {'l': '#6a7277', 'b': '#4b5257', 's': '#363c40', 'k': '#23282b'}
LAMP = {'o': '#3f5a52', 'p': '#2c4039', 'G': '#b4f76a', 'g': '#6fc344'}
HOT = {'W': '#fff8e0', 'Y': '#ffd98a', 'O': '#ff9d45'}
COLOURS = {**STEEL, **TOOL, **LAMP, **HOT, ' ': '#00000000'}


def paint(name, rows):
    """Each character is one pixel; light comes from the upper left."""
    assert len(rows) == 16 and all(len(row) == 16 for row in rows), name
    image = Image.new('RGBA', (16, 16))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            image.putpixel((x, y), tuple(int(COLOURS[key][i:i + 2], 16) for i in (1, 3, 5)) + (0 if key == ' ' else 255,))
    assert len(set(image.get_flattened_data())) <= 24, name
    path = A / 'textures/block' / (name + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def textures():
    # Four facets of the casing, each lit at its left edge; a welded seam rings the shell and wear sits in two-pixel patches.
    facet = ['LBBS', 'LBBS', 'LBBS', 'LLBS', 'LLBS', 'LBBS', 'LBBS', 'KKKK', 'LLLB', 'LBBS', 'LBSS', 'LBSS', 'LBBS', 'LBBS', 'LBBD', 'LBBD']
    paint('gas_centrifuge/shell', [row + row + row + row for row in facet])
    paint('gas_centrifuge/plinth', [
        'LLLLLLLLLLLLLLLS', 'LBBBBBBBBBBBBBBD', 'LBLSBBBBBBBBLSBD', 'LBSDBBSSSSBBSDBD', 'LBBBBSDDDDSBBBBD', 'LBBBSDKKKKDSBBBD',
        'LBBSDKKKKKKDSBBD', 'LBBSDKKKKKKDSBBD', 'LBBSDKKKKKKDSBBD', 'LBBSDKKKKKKDSBBD', 'LBBBSDKKKKDSBBBD', 'LBBBBSDDDDSBBBBD',
        'LBLSBBSSSSBBLSBD', 'LBSDBBBBBBBBSDBD', 'LBBBBBBBBBBBBBBD', 'SDDDDDDDDDDDDDDD'])
    paint('gas_centrifuge/bearing', [
        'SSSSSSSSSSSSSSSD', 'SDDDDDDDDDDDDDDK', 'SDDDDDDDDDDDDDDK', 'SDDDDDDDDDDDDDDK', 'SDDDDlllllsDDDDK', 'SDDDlbbbbbbsDDDK',
        'SDDDlbkkkkbsDDDK', 'SDDDlbkkkkbsDDDK', 'SDDDlbkkkkbsDDDK', 'SDDDlbkkkkbsDDDK', 'SDDDlbbbbbbsDDDK', 'SDDDDsssssskDDDK',
        'SDDDDDDDDDDDDDDK', 'SDDDDDDDDDDDDDDK', 'SDDDDDDDDDDDDDDK', 'DKKKKKKKKKKKKKKK'])
    # The clamp band in the top two rows, then the lamp unlit and lit.
    band = ['llbblllbbsllbbls', 'bssbbsskbsbsskbs']
    paint('gas_centrifuge/band', band + ['kkkkkkkkkkkkkkkk', 'kkkkkkkkkkkkkkkk', 'oo  GG          ', 'op  Gg          '] + ['                '] * 10)
    paint('gas_centrifuge/cap', [
        '                ', '                ', '                ', '   LLLLLLLLLS   ', '   LBBBBBBBBD   ', '   LBLLLLLSBD   ', '   LBLBBBBDBD   ', '   LBLBSSBDBD   ',
        '   LBLBSDBDBD   ', '   LBLBBBBDBD   ', '   LBSDDDDDBD   ', '   LBBBBBBBBD   ', '   SDDDDDDDDD   ', '                ', '                ', '                '])
    # A header pipe runs along the top four rows; its flange and the port's side fill the rest.
    paint('gas_centrifuge/header', [
        'LLLLLLLLLLLLLLLL', 'BBBBLBBBBBBBLBBB', 'BBSSBBBBSSBBBBBB', 'DDDDDDDDDDDDDDDD', 'KKKKKKKKKKKKKKKK', 'LLLLLS  LLLLLS  ', 'LBBBBD  LBBBBD  ', 'LBSSBD  LBBSBD  ',
        'LBSSBD  LBSSBD  ', 'LBBBBD  LBBBBD  ', 'SDDDDD  SDDDDD  ', '                ', '                ', '                ', '                ', '                '])

    paint('cubic_press/frame', [
        'LLLLLLLLLLLLLLLS', 'LBBBBBBBBBBBBBBD', 'LBDSBBBBBBBBDSBD', 'LBSLLLLLLLLLLDBD', 'LBBLBBBBBBBBSDBD', 'LBBLBLLLLLSBSDBD',
        'LBBLBLBBBBDBSDBD', 'LBBLBLBSSBDBSDBD', 'LBBLBLBSSBDBSDBD', 'LBBLBLBBBBDBSDBD', 'LBBLBSDDDDDBSDBD', 'LBBLBBBBBBBBSDBD',
        'LBDSSSSSSSSSSDBD', 'LBSLBBBBBBBBDSBD', 'LBBBBBBBBBBBBBBD', 'SDDDDDDDDDDDDDDD'])
    paint('cubic_press/anvil', [
        'llllllbbllllllbs', 'lbbbbsbslbbbbsbs', 'lbbbbsbslbbbbsbs', 'lbbssslslbbsssbs', 'lbbssslslbbsssbs', 'lsssssbslsssssbs', 'bbbbbbbsbbbbbbbs', 'sssssssksssssssk',
        'llllllbbllllllbs', 'lbbbbsbslbbbbsbs', 'lbbbbsbslbbbbsbs', 'lbbssslslbbsssbs', 'lbbssslslbbsssbs', 'lsssssbslsssssbs', 'bbbbbbbsbbbbbbbs', 'sssssssksssssssk'])
    paint('cubic_press/sample', ['WWYYWWYYWWYYWWYY', 'WWYYWWYYWWYYWWYY', 'YYOOYYOOYYOOYYOO', 'YYOOYYOOYYOOYYOO'] * 4)


def box(start, end, texture, uv=None, rotation=None, skip=(), light=False):
    """A cuboid whose faces take the texels they cover unless a face is given its own."""
    x0, y0, z0 = start
    x1, y1, z1 = end
    faces = {}
    for side in ('north', 'south', 'east', 'west', 'up', 'down'):
        if side in skip: continue
        cover = [x0, 16 - y1, x1, 16 - y0] if side in ('north', 'south') else [z0, 16 - y1, z1, 16 - y0] if side in ('east', 'west') else [x0, z0, x1, z1]
        chosen = uv.get(side) if uv else None
        if chosen is None and uv and 'all' in uv:
            # A shared patch is cut down to the face, so a thin edge takes a strip rather than a squeezed copy.
            u0, v0, u1, v1 = uv['all']
            chosen = [u0, v0, min(u1, u0 + cover[2] - cover[0]), min(v1, v0 + cover[3] - cover[1])]
        faces[side] = {'texture': '#' + (texture[side] if isinstance(texture, dict) else texture), 'uv': chosen or cover}
    element = {'from': list(start), 'to': list(end), 'faces': faces}
    if rotation: element['rotation'] = {'angle': rotation, 'axis': 'y', 'origin': [8, 8, 8]}
    if light: element |= {'shade': False, 'neoforge_data': {'block_light': 15, 'sky_light': 15}}
    return element


def octagon(radius, side, y0, y1, texture, caps=None):
    """Two crossed slabs and the same pair turned 45 degrees: an eight-sided column. An odd side straddles the centre on half pixels."""
    half = side / 2
    half = int(half) if half == int(half) else half
    elements = []
    for turn, ua in ((None, 0), (45, 4)):
        v = [16 - y1, 16 - y0]
        ends, flanks = [ua, v[0], ua + side, v[1]], [8 - radius, v[0], 8 + radius, v[1]]
        faces = {'up': caps or texture, 'down': caps or texture} | {s: texture for s in ('north', 'south', 'east', 'west')}
        elements.append(box((8 - half, y0, 8 - radius), (8 + half, y1, 8 + radius), faces, {'north': ends, 'south': ends, 'east': flanks, 'west': flanks}, turn))
        elements.append(box((8 - radius, y0, 8 - half), (8 + radius, y1, 8 + half), faces, {'east': ends, 'west': ends, 'north': flanks, 'south': flanks}, turn))
    return elements


def centrifuge_models():
    T = NS + 'block/gas_centrifuge/'
    textures_ = {'shell': T + 'shell', 'plinth': T + 'plinth', 'bearing': T + 'bearing', 'band': T + 'band', 'cap': T + 'cap', 'header': T + 'header', 'particle': T + 'shell'}
    lower = [box((0, 0, 0), (16, 3, 16), {'north': 'plinth', 'south': 'plinth', 'east': 'plinth', 'west': 'plinth', 'up': 'plinth', 'down': 'bearing'},
                 {side: [0, 0, 16, 3] for side in ('north', 'south', 'east', 'west')})]
    lower += octagon(5, 4, 3, 16, 'shell')
    upper = octagon(5, 4, 0, 14, 'shell', caps='cap')
    for element in upper:
        for side in ('up', 'down'): element['faces'][side]['uv'] = [4, 4, 12, 12] if element['from'][0] != 6 or 'rotation' in element else element['faces'][side]['uv']
    # The clamp band stands a pixel proud of the shell, its upper edge three pixels below the casing's end.
    for element in octagon(6, 5, 9, 11, 'band'):
        for side, face in element['faces'].items():
            face['uv'] = [0, 2, 5, 3] if side in ('up', 'down') else [0, 0, face['uv'][2] - face['uv'][0], 2]
        upper.append(element)
    upper.append(box((5, 14, 5), (11, 16, 11), {'north': 'header', 'south': 'header', 'east': 'header', 'west': 'header', 'up': 'header', 'down': 'header'},
                     {'all': [0, 5, 6, 7], 'up': [0, 5, 6, 11], 'down': [0, 5, 6, 11]}))

    def lamps(lit):
        u = 4 if lit else 0
        parts = []
        for start, end in (((7, 9, 1), (9, 11, 2)), ((7, 9, 14), (9, 11, 15)), ((1, 9, 7), (2, 11, 9)), ((14, 9, 7), (15, 11, 9))):
            parts.append(box(start, end, 'band', {'all': [u, 4, u + 2, 6]}, light=lit))
        return parts

    # Towards the north neighbour: the pipe from the port to the block's edge, ending in half a flange.
    header = [box((6, 12, 1), (10, 16, 5), 'header', {'east': [0, 0, 4, 4], 'west': [0, 0, 4, 4], 'up': [0, 0, 4, 4], 'down': [0, 0, 4, 4]}, skip=('north', 'south')),
              box((5, 11, 0), (11, 16, 1), 'header', {'all': [8, 5, 14, 10], 'up': [8, 5, 14, 6], 'down': [8, 10, 14, 11], 'east': [8, 5, 9, 10], 'west': [13, 5, 14, 10]})]
    base = {'parent': 'minecraft:block/block', 'textures': textures_}
    M = A / 'models/block/gas_centrifuge'
    write(M / 'lower.json', base | {'elements': lower})
    write(M / 'upper.json', base | {'elements': upper})
    write(M / 'lamp.json', base | {'elements': lamps(False)})
    write(M / 'lamp_lit.json', base | {'elements': lamps(True)})
    write(M / 'header.json', base | {'elements': header})
    raised = json.loads(json.dumps(upper + lamps(False)))
    for element in raised:
        element['from'][1] += 16;element['to'][1] += 16
        if 'rotation' in element: element['rotation']['origin'][1] += 16
    write(M / 'item.json', base | {'elements': lower + raised, 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, -3.25, 0], 'scale': [.4, .4, .4]},
        'ground': {'translation': [0, 1, 0], 'scale': [.2, .2, .2]},
        'fixed': {'translation': [0, -3, 0], 'scale': [.35, .35, .35]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 1, 0], 'scale': [.25, .25, .25]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 1, 0], 'scale': [.3, .3, .3]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 1, 0], 'scale': [.3, .3, .3]}}})
    block = NS + 'block/gas_centrifuge/'
    parts = [{'when': {'half': 'lower'}, 'apply': {'model': block + 'lower'}},
             {'when': {'half': 'upper'}, 'apply': {'model': block + 'upper'}},
             {'when': {'half': 'upper', 'lit': 'false'}, 'apply': {'model': block + 'lamp'}},
             {'when': {'half': 'upper', 'lit': 'true'}, 'apply': {'model': block + 'lamp_lit'}}]
    for side, turn in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        parts.append({'when': {'half': 'upper', side: 'true'}, 'apply': {'model': block + 'header', 'y': turn}})
    write(A / 'blockstates/gas_centrifuge.json', {'multipart': parts})


def press_models():
    T = NS + 'block/cubic_press/'
    textures_ = {'frame': T + 'frame', 'anvil': T + 'anvil', 'sample': T + 'sample', 'particle': T + 'frame'}
    frame = []
    for a in (0, 13):
        for b in (0, 13):
            frame.append(box((a, 0, b), (a + 3, 16, b + 3), 'frame', {side: [0, 0, 3, 16] for side in ('north', 'south', 'east', 'west')}))
            frame.append(box((3, a, b), (13, a + 3, b + 3), 'frame', {side: [3, 0, 13, 3] for side in ('north', 'south', 'up', 'down')}, skip=('east', 'west')))
            frame.append(box((a, b, 3), (a + 3, b + 3, 13), 'frame', {side: [3, 0, 13, 3] for side in ('east', 'west', 'up', 'down')}, skip=('north', 'south')))
    # Each face is open but for a one-pixel guide of tool steel, in which its anvil slides.
    for axis in range(3):
        for near in (0, 15):
            def place(u0, v0, u1, v1):
                start, end = [0, 0, 0], [0, 0, 0]
                start[axis], end[axis] = near, near + 1
                others = [i for i in range(3) if i != axis]
                start[others[0]], end[others[0]], start[others[1]], end[others[1]] = u0, u1, v0, v1
                return tuple(start), tuple(end)
            for bar in ((3, 3, 13, 4), (3, 12, 13, 13), (3, 4, 4, 12), (12, 4, 13, 12)):
                frame.append(box(*place(*bar), 'anvil', {'all': [0, 6, 8, 8] if bar[2] - bar[0] > 1 else [7, 0, 8, 8]}))
    base = {'parent': 'minecraft:block/block', 'textures': textures_}
    M = A / 'models/block/cubic_press'
    write(M / 'frame.json', base | {'elements': frame})
    # The anvil of the north face, drawn back: a stepped wedge of tool steel narrowing to a two-pixel tip. The renderer turns it onto every face.
    anvil = [box((4, 4, 0), (12, 12, 2), 'anvil', {'all': [0, 0, 8, 2], 'north': [0, 0, 8, 8], 'south': [0, 0, 8, 8]}),
             box((5, 5, 2), (11, 11, 4), 'anvil', {'all': [8, 1, 14, 3], 'north': [8, 0, 14, 6], 'south': [8, 0, 14, 6]}),
             box((6, 6, 4), (10, 10, 5), 'anvil', {'all': [1, 9, 5, 10], 'north': [1, 9, 5, 13], 'south': [1, 9, 5, 13]}),
             box((7, 7, 5), (9, 9, 6), 'anvil', {'all': [9, 9, 11, 10], 'north': [9, 9, 11, 11], 'south': [11, 11, 13, 13]})]
    write(M / 'anvil.json', base | {'elements': anvil})
    write(M / 'sample.json', base | {'elements': [box((7, 7, 7), (9, 9, 9), 'sample', {'all': [0, 0, 2, 2]}, light=True)]})
    # The idle item includes the six static anvil poses; in-world motion uses the same partial.
    item = list(frame)
    for axis, sign in ((2, 1), (2, -1), (0, 1), (0, -1), (1, 1), (1, -1)):
        for element in anvil:
            corners = []
            for corner in (element['from'], element['to']):
                v = list(corner)
                if axis == 0: v = [v[2], v[1], v[0]]
                if axis == 1: v = [v[0], v[2], v[1]]
                if sign < 0: v[axis] = 16 - v[axis]
                corners.append(v)
            lo, hi = [min(a, b) for a, b in zip(*corners)], [max(a, b) for a, b in zip(*corners)]
            wide = hi[(axis + 1) % 3] - lo[(axis + 1) % 3]
            item.append(box(tuple(lo), tuple(hi), 'anvil', {'all': [0, 0, wide, wide]}))
    write(M / 'item.json', base | {'elements': item})
    write(A / 'blockstates/cubic_press.json', {'variants': {f'axis={axis}': {'model': NS + 'block/cubic_press/frame'} for axis in ('x', 'z')}})


textures();centrifuge_models();press_models()
for name in ('gas_centrifuge','cubic_press'):
    write(A/f'models/item/{name}.json',{'parent':NS+f'block/{name}/item'})
    write(D/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]+([{'condition':'minecraft:block_state_property','block':NS+name,'properties':{'half':'lower'}}]if name=='gas_centrifuge'else[])}]})
    write(D/f'recipe/{name}.json',{'type':'create:mechanical_crafting','pattern':['SSS','TMT','SHS'],
        'key':{'S':{'tag':'c:plates/stainless_steel'},'T':({'tag':'c:ingots/titanium'}if name=='gas_centrifuge'else{'item':NS+'steel_block'}),'M':{'item':'create:precision_mechanism'if name=='gas_centrifuge'else'create:mechanical_press'},'H':{'item':'create:shaft'}},'result':{'id':NS+name},'accept_mirrored':True})
for catalyst,chance,ticks in (('nickel',.75,400),('iron',.5,600)):
    write(D/f'recipe/cubic_pressing/synthetic_diamond{"_iron"if catalyst=="iron"else""}.json',{'type':NS+'cubic_pressing','ingredients':[{'item':NS+'reactor_graphite'},{'tag':'c:ingots/'+catalyst}],
        'results':[{'id':'minecraft:diamond'},{'id':NS+'nickel_ingot'if catalyst=='nickel'else'minecraft:iron_ingot','chance':chance}],'processing_time':ticks,'heat_requirement':'superheated'})
pickaxe=R/'atomics/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json';data=json.loads(pickaxe.read_text());data['values']=list(dict.fromkeys(data['values']+[NS+'gas_centrifuge',NS+'cubic_press']));write(pickaxe,data)
# All support shafts and burners are ordinary Create parts in the teaching structures.
s=Structure(8,5)
for i in range(8):
    x,z=2+i%4,3+i//4
    for half,y in (('lower',2),('upper',3)):s.block(x,y,z,NS+'gas_centrifuge',half=half,lit='false',north=str(half=='upper' and i>=4).lower(),south=str(half=='upper' and i<4).lower(),east=str(half=='upper' and i%4<3).lower(),west=str(half=='upper' and i%4>0).lower())
    s.block(x,1,z,'create:creative_motor',facing='up')
s.save('gas_centrifuge');(A/'ponder/plant/gas_centrifuge.nbt').write_bytes((OUT/'gas_centrifuge.nbt').read_bytes());(OUT/'gas_centrifuge.nbt').unlink()
p=Structure(7,5);p.block(3,2,3,NS+'cubic_press',axis='x');p.block(3,1,3,'create:blaze_burner',blaze='smouldering');p.block(4,2,3,'create:shaft',axis='x',waterlogged='false');p.block(5,2,3,'create:creative_motor',facing='west')
p.block(3,2,2,'create:andesite_funnel',facing='north',extracting='false',waterlogged='false');p.save('cubic_press');(A/'ponder/plant/cubic_press.nbt').write_bytes((OUT/'cubic_press.nbt').read_bytes());(OUT/'cubic_press.nbt').unlink()
TEXTS={
'block.create_refinery_n_reactors.gas_centrifuge':('Gas Centrifuge','ガス遠心分離機','气体离心机'),
'block.create_refinery_n_reactors.cubic_press':('Cubic Press','キュービックプレス','六面顶压机'),
'centrifuge.create_refinery_n_reactors.cascade':('Cascade: %s units / %s stages (need %s)','カスケード：%s台・%s段（必要%s段）','级联：%s台・%s级（需要%s级）'),
'centrifuge.create_refinery_n_reactors.process':('Process: %s / %s ticks','工程：%s・%s tick','工艺：%s・%s tick'),
'centrifuge.create_refinery_n_reactors.drive':('Drive: %s RPM / %s SU','回転：%s RPM・%s SU','转速：%s RPM・%s SU'),
'cubic_press.create_refinery_n_reactors.drive':('Drive: %s RPM / %s SU','回転：%s RPM・%s SU','转速：%s RPM・%s SU'),
'cubic_press.create_refinery_n_reactors.progress':('Progress: %s%%','進捗：%s%%','进度：%s%%'),
'cubic_press.create_refinery_n_reactors.superheated':('Heat: superheated','熱：超高温','热：超高温'),
'cubic_press.create_refinery_n_reactors.cold':('Requires a superheated Blaze Burner below','直下のブレイズバーナーを超高温にしてください','正下方需要超高温烈焰燃烧室'),
'centrifuge.create_refinery_n_reactors.idle':('Idle','待機中','待机'),
'display.create_refinery_n_reactors.cascade_units':('Cascade units','カスケード台数','级联台数'),
'display.create_refinery_n_reactors.cascade_stages':('Powered stages','稼働段数','运行级数'),
'display.create_refinery_n_reactors.cascade_required':('Required stages','必要段数','所需级数'),
'display.create_refinery_n_reactors.process_name':('Process','実行工程','当前工艺'),
'display.create_refinery_n_reactors.stress':('Stress (SU)','応力（SU）','应力（SU）'),
'display.create_refinery_n_reactors.superheated':('Superheated','超高温','超高温'),
'jei.create_refinery_n_reactors.cubic_pressing':('Cubic Pressing','キュービックプレス加工','六面顶压'),
'jei.create_refinery_n_reactors.note.cubic_pressing':('%s ticks at 64 RPM; superheated. Catalyst return: %s%% (to the catalyst slot).','64 RPMで%s tick・超高温。触媒を%s%%で触媒スロットへ返却。','64 RPM下%s tick・超高温。催化剂以%s%%概率返回催化剂槽。'),
'jei.create_refinery_n_reactors.note.centrifuge_stages':('%s ticks / minimum %s stages; each unit requires 32 RPM.','%s tick・最低%s段。各台32 RPM以上。','%s tick・至少%s级，每台32 RPM以上。')}
STATUSES={'feed':('No feed','原料なし','无原料'),'materials':('No materials','材料なし','无材料'),'speed':('Rotation too slow','回転不足','转速不足'),'stages':('Not enough powered stages','段数不足','运行级数不足'),'output':('Product storage full','製品満杯','产物已满'),'unloaded':('Cascade not loaded','構造を読み込めません','级联未加载'),'limit':('Cascade limit: 16 units','接続上限：16台','级联上限16台'),'running':('Running','運転中','运行中'),'heat':('Not superheated','超高温が必要','需要超高温')}
for prefix in ('centrifuge','cubic_press'):
    for key,values in STATUSES.items():TEXTS[prefix+'.create_refinery_n_reactors.status.'+key]=values
for i,language in enumerate(('en_us','ja_jp','zh_cn')):
    path=A/'lang'/(language+'.json');data=json.loads(path.read_text())
    for key,values in TEXTS.items():data[key]=values[i]
    write(path,data)
print('Generated gas centrifuges and cubic presses')
