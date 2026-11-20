#!/usr/bin/env python3
"""Native block models, 16 px textures, armor and a pressure infrastructure Ponder structure."""
from PIL import Image, ImageDraw
from generate_world_liquids import ASSETS, NS, write
import generate_petrochemical_ponder as ponder

TEXTURES = ASSETS / 'textures'


def tile(name, colors):
    shadow, edge, bevel, base, highlight = colors
    img = Image.new('RGB', (16, 16), shadow)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 14, 14), fill=edge)
    d.rectangle((1, 1, 13, 13), fill=base)
    d.line((1, 1, 13, 1), fill=bevel)
    d.line((1, 1, 1, 13), fill=bevel)
    d.line((2, 12, 13, 12), fill=edge)
    for x, y in [(3, 4), (10, 5), (6, 8), (4, 10)]:
        d.line((x, y, x+2, y), fill=highlight)
    for x, y in [(2, 2), (12, 2), (2, 12), (12, 12)]:
        d.point((x, y), fill=bevel)
    img.save(TEXTURES / 'block' / (name + '.png'))


def cuboid(lo, hi, material):
    x, y, z = lo
    xx, yy, zz = hi
    uv = {'north': [16-xx, 16-yy, 16-x, 16-y], 'south': [x, 16-yy, xx, 16-y],
          'up': [x, z, xx, zz], 'down': [x, 16-zz, xx, 16-z],
          'east': [16-zz, 16-yy, 16-z, 16-y], 'west': [z, 16-yy, zz, 16-y]}
    return {'from': lo, 'to': hi, 'faces': {side: {'uv': bounds, 'texture': '#' + material} for side, bounds in uv.items()}}


def model(name, elements):
    write(ASSETS / f'models/block/{name}.json', {
        'parent': 'minecraft:block/block',
        'textures': {'particle': NS+'block/pressure_pump_iron', 'iron': NS+'block/pressure_pump_iron',
                     'brass': NS+'block/pressure_pump_brass', 'copper': NS+'block/pressure_pump_copper',
                     'rubber': NS+'block/pressure_pump_rubber'},
        'elements': elements,
    })


def flange(front=True):
    parts = [([3, 3, 0], [13, 13, 2], 'brass'), ([4, 4, 2], [12, 12, 5], 'copper'),
             ([5, 5, -.05], [11, 11, .25], 'rubber')]
    if not front:
        return [cuboid([a[0], a[1], 16-b[2]], [b[0], b[1], 16-a[2]], mat) for a,b,mat in parts]
    return [cuboid(a,b,mat) for a,b,mat in parts]


def models():
    rotor = [cuboid([6, 11, 6], [10, 16, 10], 'brass'), cuboid([2, 11, 6], [14, 13, 10], 'copper'),
             cuboid([6, 11, 3], [10, 13, 14], 'brass')]
    model('pressure_pump_spindle', rotor)
    model('high_pressure_pump', [
        cuboid([0, 0, 2], [16, 3, 16], 'iron'), cuboid([1, 3, 4], [16, 11, 16], 'iron'),
        cuboid([0, 9, 4], [16, 10, 16], 'brass'), cuboid([0, 11, 10], [16, 14, 16], 'iron'),
        cuboid([0, 11, 4], [2, 14, 10], 'iron'), cuboid([13, 11, 4], [16, 14, 10], 'iron'),
    ] + flange())
    bottom = [cuboid([0, 0, 2], [16, 3, 14], 'rubber'), cuboid([0, 3, 1], [16, 6, 15], 'iron'),
              cuboid([1, 6, 2], [16, 15, 14], 'iron'), cuboid([0, 13, 1], [16, 15, 15], 'brass')]
    model('pressure_pump_intake', bottom + flange(False))
    model('pressure_pump_lower', bottom)
    model('pressure_pump_upper', [cuboid([0,0,1],[16,11,15],'iron'),
         cuboid([0,9,1],[16,11,15],'brass'),cuboid([0,11,2],[16,14,14],'copper'),
         cuboid([0,14,3],[16,15,13],'iron')])
    model('hose_connector', [cuboid([3,3,12],[13,13,16],'iron'), cuboid([5,5,2],[11,11,12],'copper'),
         cuboid([4,4,0],[12,12,2],'brass'), cuboid([5,5,-.05],[11,11,.2],'rubber')])

    yaws = [('north',0),('east',90),('south',180),('west',270)]
    def variant(name, yaw, pitch=0):
        return {'model': NS+'block/'+name, **({'x':pitch} if pitch else {}), **({'y':yaw} if yaw else {})}

    for name in ('high_pressure_pump','pressure_pump_intake'):
        write(ASSETS/f'blockstates/{name}.json', {'variants': {
            'facing='+face:variant(name,yaw) for face,yaw in yaws
        } | {'facing=up':variant(name,0),'facing=down':variant(name,0)}})
    write(ASSETS/'blockstates/pressure_pump_casing.json', {'variants': {
        f'facing={face},part={part}':variant('pressure_pump_'+group,yaw)
        for face,yaw in yaws
        for group,parts in [('lower',['receive','lower_front','lower_back']),('upper',['feed','upper_front','upper_back'])]
        for part in parts
    }})
    write(ASSETS/'blockstates/hose_connector.json', {'variants': {
        'facing='+face:variant('hose_connector',yaw) for face,yaw in yaws
    } | {'facing=up':variant('hose_connector',0,270),'facing=down':variant('hose_connector',0,90)}})
    for name in ('high_pressure_pump','hose_connector'):
        write(ASSETS/f'models/item/{name}.json', {'parent': NS+'block/'+name})


def icons():
    def icon(name, painter):
        image = Image.new('RGBA',(16,16))
        painter(ImageDraw.Draw(image))
        image.save(TEXTURES/f'item/{name}.png')
        write(ASSETS/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':NS+'item/'+name}})

    def hose(d):
        d.ellipse((1,3,14,13),outline='#202624',width=3)
        d.ellipse((2,4,13,12),outline='#555b51')
        d.arc((2,4,13,12),200,337,fill='#ba974d',width=1)
        d.line((4,12,3,15),fill='#756248',width=2)
        d.line((12,8,15,6),fill='#b5975b',width=2)
    icon('flexible_rubber_hose',hose)

    for name, shape in {
        'helmet':[(2,6),(4,3),(11,3),(13,6),(13,13),(2,13)],
        'chestplate':[(2,4),(5,2),(11,2),(14,4),(15,9),(12,9),(12,14),(3,14),(3,9),(1,9)],
        'leggings':[(3,2),(12,2),(13,14),(9,14),(8,7),(7,14),(2,14)],
        'boots':[(3,4),(7,4),(7,10),(9,10),(9,4),(12,4),(13,11),(15,13),(15,14),(1,14),(1,11),(3,10)],
    }.items():
        def draw(d, part=name, points=shape):
            d.polygon(points,fill='#b49851',outline='#393936')
            d.line((4,4,11,4),fill='#e2c270')
            if part=='helmet':
                d.rectangle((3,6,12,9),fill='#303b3a')
                d.line((4,7,11,7),fill='#7daba5')
                d.rectangle((7,10,11,12),fill='#4b514b')
            if part=='chestplate':
                d.line((7,4,7,13),fill='#5a5345')
                d.line((4,10,11,10),fill='#343a37')
            if part=='leggings':
                d.line((3,3,12,3),fill='#363b36')
            if part=='boots':
                d.line((2,13,14,13),fill='#333736',width=2)
        icon('hazard_'+name,draw)


def armor():
    folder = TEXTURES/'models/armor'
    for layer in (1,2):
        img = Image.new('RGBA',(64,32))
        d = ImageDraw.Draw(img)
        def piece(x,y,w,h):
            d.rectangle((x,y,x+w-1,y+h-1),fill='#bca05b')
            d.line((x,y,x,y+h-1),fill='#d1b36a')
            d.line((x+w-1,y+1,x+w-1,y+h-1),fill='#9c813f')
        if layer == 1:
            for x in range(0,32,8): piece(x,8,8,8)
            for x in (8,16): piece(x,0,8,8)
            d.rectangle((8,9,15,13),fill='#303938')
            d.rectangle((9,10,14,12),fill='#628b87')
            d.line((9,10,13,10),fill='#a5c4bb')
            d.rectangle((10,13,13,15),fill='#434843')
            for x in range(0,32,8): d.line((x,15,x+7,15),fill='#343636')
            for x,w in [(16,4),(20,8),(28,4),(32,8),(40,4),(44,4),(48,4),(52,4)]:
                piece(x,20,w,12)
            for x,w in [(20,8),(28,8),(44,4),(48,4)]: piece(x,16,w,4)
            d.line((23,21,23,31),fill='#696047')
            d.rectangle((34,23,37,30),fill='#4d534c')
            d.line((35,24,35,29),fill='#8b9587')
            d.rectangle((40,29,55,31),fill='#383d39')
            d.rectangle((0,20,15,31),fill='#b69750')
            d.rectangle((0,28,15,31),fill='#363b36')
            d.line((4,28,7,28),fill='#777e77')
        else:
            for x in (0,4,8,12): piece(x,20,4,12)
            for x in (4,8): piece(x,16,4,4)
            for x in (16,20,28,32): piece(x,20,4 if x in (16,28) else 8,12)
            d.rectangle((16,20,39,22),fill='#3a3e36')
            d.line((0,31,15,31),fill='#504e3a')
        img.save(folder/f'chemical_hazard_layer_{layer}.png')


def structure():
    ponder.OUT=ASSETS/'ponder/plant'
    scene=ponder.Structure(11,7)
    scene.block(3,2,5,NS+'high_pressure_pump',facing='north',waterlogged='false')
    scene.block(3,1,6,NS+'pressure_pump_intake',facing='north',waterlogged='false')
    for x,y,z,part in [(3,1,5,'receive'),(3,2,6,'feed'),(4,1,5,'lower_front'),(4,1,6,'lower_back'),
                        (4,2,5,'upper_front'),(4,2,6,'upper_back')]:
        scene.block(x,y,z,NS+'pressure_pump_casing',facing='north',part=part)
    scene.block(3,3,5,'create:shaft',axis='y',waterlogged='false')
    scene.block(3,4,5,'create:creative_motor',facing='down')
    scene.block(3,1,8,'create:fluid_tank')
    scene.block(3,1,7,'create:fluid_pipe',north='true',south='true',east='false',west='false',up='false',down='false',waterlogged='false')
    scene.block(8,2,3,NS+'hose_connector',facing='north')
    scene.block(8,2,4,'create:fluid_tank')
    scene.block(8,1,4,'minecraft:smooth_stone')
    scene.save('pressure_infrastructure')
    ponder.OUT = ASSETS.parents[1] / 'data/create_refinery_n_reactors/structure'
    long_test = ponder.Structure(1, 1, floor=False)
    long_test.size = (140, 10, 18)
    long_test.save('test_pressure_infrastructure')


if __name__ == '__main__':
    tile('pressure_pump_iron',('#222727','#3d4544','#777f78','#515955','#5e6762'))
    tile('pressure_pump_brass',('#412e24','#6e5034','#c2a265','#8d7249','#a48955'))
    tile('pressure_pump_copper',('#472c25','#6f4136','#b57c63','#8e5948','#9c6451'))
    tile('pressure_pump_rubber',('#181b1a','#242827','#535751','#303431','#383d38'))
    Image.new('RGB',(16,16),'#ffffff').save(TEXTURES/'block/rubber_hose_mesh.png')
    models()
    icons()
    armor()
    structure()
