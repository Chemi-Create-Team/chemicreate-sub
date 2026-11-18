"""Filter-press casing and instrument hardware for the electrolyser blocks, drawn as 16px pixel art in Create's style: the
planks of Create's andesite casing framed by its rim."""
import io
import json
import math
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

MOD = 'create_refinery_n_reactors'
SIDES = ('down', 'up', 'north', 'south', 'west', 'east')
QUADRANTS = {'steel': (0, 0), 'brass': (8, 0), 'black': (0, 8), 'iron': (8, 8)}
TILES = ('casing', 'top', 'plate', 'inner', 'panel')
# Connected sheets hold one 16px tile per combination of joined edges; a tile index is the sum of these bits.
UP, DOWN, LEFT, RIGHT = 1, 2, 4, 8
# The panel's terminals are Electro Energetics' connector at this scale; ElectrolysisControllerBlock sets the wire nodes to match.
TERMINAL_SCALE = 2 / 3
GRID = {'casing': 4, 'top': 4}
MONO = '/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf'
DETAIL = 4

STEEL = {'rim': '#5C6366', 'rim_dark': '#3E4447', 'bevel': '#B4BBBE', 'bevel_low': '#A3AAAD', 'recess': '#747B7E',
         'body': '#8C9396', 'body_low': '#838A8D', 'body_high': '#989FA2', 'shine': '#CDD3D5'}
PANEL = {'rim': '#2E3235', 'rim_dark': '#1A1D1F', 'bevel': '#5A6165', 'bevel_low': '#474D50', 'recess': '#2F3437',
         'body': '#3A3F42', 'body_low': '#353A3D', 'body_high': '#40464A', 'shine': '#6C7377'}
BRASS = ('#C9A45C', '#DDBE7A', '#9C7A3E')
IRON = ('#5C6366', '#6B7275', '#4B5154')
BLACK = ('#26292B', '#303436', '#1B1E20')
PORCELAIN = ('#CBC7B3', '#C0BCA8')
HOLE = '#1B1E20'
INK = '#2B2D2C'
FACE = '#D9DCD8'
FACE_SHADE = '#B9BDB9'
LEGEND = INK
WARN = '#A6514A'
SCREEN = '#0E1011'
START = ('#4E8A5A', '#6FA57A', '#356642')
STOP = ('#A6514A', '#C4736B', '#7C3A35')
GLYPHS = {'0': '111101101101111', '1': '010110010010111', '2': '111001111100111', '3': '111001111001111',
          '5': '111100111001111', '7': '111001001001001', 'S': '111100111001111', 'T': '111010010010010',
          'A': '111101111101101', 'R': '110101110101101', 'O': '111101101101111', 'P': '111101111100100',
          'L': '100100100100111', 'D': '110101101101110', 'V': '101101101101010', '+': '000010111010000',
          '-': '000000111000000'}


def fill(image, x0, y0, x1, y1, colour):
    ImageDraw.Draw(image).rectangle((x0, y0, x1, y1), fill=colour)


def sheet(tiles, grid):
    image = Image.new('RGB', (grid * 16, grid * 16))
    for index, tile in enumerate(tiles):image.paste(tile, (index % grid * 16, index // grid * 16))
    return image


def create_texture(name, mode='RGB'):
    """A texture from the Create jar Gradle resolved for this project."""
    path = f'assets/create/textures/block/{name}.png'
    for pattern in ('maven.modrinth/create/*/*/*.jar', 'com.simibubi.create/create-1.21.1/*/*/*.jar'):
        for jar in sorted(Path.home().glob('.gradle/caches/modules-2/files-2.1/' + pattern)):
            with zipfile.ZipFile(jar) as source:
                if path in source.namelist(): return Image.open(io.BytesIO(source.read(path))).convert(mode)
    raise SystemExit('Create jar not found; run a Gradle build first')


def andesite_frame(image, joined):
    """Lays the two-pixel rim of Create's andesite casing along every edge that is not joined, rim pixels where it continues."""
    rim = create_texture('andesite_casing')
    for y in range(16):
        for x in range(16):
            sx, sy = x, y
            if joined & LEFT and x < 2: sx = x + 4
            if joined & RIGHT and x > 13: sx = x - 4
            if joined & UP and y < 2: sy = y + 4
            if joined & DOWN and y > 13: sy = y - 4
            if sx < 2 and not joined & LEFT or sx > 13 and not joined & RIGHT or sy < 2 and not joined & UP or sy > 13 and not joined & DOWN:
                image.putpixel((x, y), rim.getpixel((sx, sy)))
    return image


def silver_field(assets):
    """The grooved middle of the lidded vessel's silver plate, repeating in both directions."""
    shell = Image.open(assets / 'textures/block/reactor/silver/shell.png').convert('RGB')
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16): image.putpixel((x, y), shell.getpixel((4 + x % 8, 7 + y % 2)))
    return image


def andesite_field():
    """The plank middle of Create's andesite casing, repeating in both directions so joined faces run on."""
    casing = create_texture('andesite_casing')
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16): image.putpixel((x, y), casing.getpixel((4 + x % 8, 4 + y % 8)))
    return image


def casing_texture(assets, joined):
    image = andesite_frame(andesite_field(), joined)
    for x, y, edges in ((3, 3, UP | LEFT), (12, 3, UP | RIGHT), (3, 12, DOWN | LEFT), (12, 12, DOWN | RIGHT)):
        if not joined & edges: fill(image, x, y, x, y, STEEL['rim_dark'])
    return image


def top_texture(assets, joined):
    """The rim along the open top: the same casing, its planks across instead of along."""
    return andesite_frame(andesite_field().transpose(Image.Transpose.ROTATE_90), joined)


def plate_texture():
    image = Image.new('RGB', (16, 16), STEEL['body'])
    fill(image, 3, 1, 12, 14, STEEL['rim']);fill(image, 3, 1, 11, 13, STEEL['bevel'])
    fill(image, 4, 2, 11, 13, STEEL['body'])
    for y in range(3, 13, 3):
        fill(image, 5, y, 10, y, STEEL['recess']);fill(image, 5, y + 1, 10, y + 1, STEEL['body_high'])
    return image


def swatch(colours):
    base, high, low = colours
    image = Image.new('RGB', (16, 16), base)
    for x, y in ((5, 2), (11, 5), (2, 8), (8, 11), (13, 13)):
        fill(image, x, y, x + 1, y, low);fill(image, x, y + 1, x + 1, y + 1, high)
    return image


def materials_texture():
    image = Image.new('RGB', (32, 32))
    for name, colours in (('steel', (STEEL['body'], STEEL['body_high'], STEEL['body_low'])), ('brass', BRASS), ('black', BLACK), ('iron', IRON)):
        u, v = QUADRANTS[name];image.paste(swatch(colours), (u * 2, v * 2))
    return image


def porcelain_texture():
    image = Image.new('RGB', (16, 16), PORCELAIN[0])
    for x, y in ((6, 4), (11, 9), (3, 12)):fill(image, x, y, x + 1, y + 1, PORCELAIN[1])
    return image


def text(image, value, x, y, colour):
    for glyph in value:
        for n, bit in enumerate(GLYPHS[glyph]):
            if bit == '1':fill(image, x + n % 3, y + n // 3, x + n % 3, y + n // 3, colour)
        x += 4


def centred(image, value, x, y, colour):
    text(image, value, x - (len(value) * 4 - 1) // 2, y, colour)


def ticks(image, cx, cy, angle, inner, outer, colour):
    a = math.radians(angle)
    for n in range(round((outer - inner) * 2) + 1):
        r = inner + n / 2
        x, y = math.floor(cx + math.sin(a) * r), math.floor(cy - math.cos(a) * r)
        fill(image, x, y, x, y, colour)


def panel_textures(directory):
    panel = create_texture('andesite_block').resize((16 * DETAIL, 16 * DETAIL), Image.Resampling.NEAREST)
    for x0, unit, maximum in ((4, 'V', '500'), (32, 'A', '320')):
        fill(panel, x0, 4, x0 + 27, 31, PANEL['rim_dark']);fill(panel, x0, 4, x0 + 26, 30, PANEL['bevel_low'])
        fill(panel, x0 + 1, 5, x0 + 26, 30, BLACK[2])
        fill(panel, x0 + 1, 5, x0 + 26, 23, FACE_SHADE);fill(panel, x0 + 2, 6, x0 + 26, 23, FACE)
        for n in range(6):
            ticks(panel, x0 + 14, 21.6, -50 + 20 * n, 10.5 if n % 5 == 0 else 11.5, 12.5, INK)
        for n in range(17):
            ticks(panel, x0 + 14, 21.6, 30 + 1.25 * n, 14, 14.5, WARN)
        centred(panel, unit, x0 + 14, 13, INK)
        text(panel, '0', x0 + 3, 18, INK);text(panel, maximum, x0 + 15, 18, INK)
        fill(panel, x0 + 3, 25, x0 + 24, 30, PANEL['rim']);fill(panel, x0 + 4, 26, x0 + 24, 30, SCREEN)
    for x, title, colours in ((16, 'START', START), (48, 'STOP', STOP)):
        centred(panel, title, x, 35, LEGEND)
        fill(panel, x - 8, 42, x + 7, 57, PANEL['rim_dark']);fill(panel, x - 7, 43, x + 6, 56, colours[2])
        fill(panel, x - 7, 43, x + 5, 55, colours[1]);fill(panel, x - 6, 44, x + 5, 55, colours[0])
        fill(panel, x - 4, 46, x + 3, 53, BLACK[2])
    panel.save(directory / 'panel_2.png', optimize=True)

    panel = create_texture('andesite_block').resize((16 * DETAIL, 16 * DETAIL), Image.Resampling.NEAREST)
    for y in range(24, 49):
        for x in range(20, 45):
            distance = math.hypot(x + .5 - 32, y + .5 - 36)
            if distance <= 11.5:fill(panel, x, y, x, y, PANEL['shine'] if distance > 10.3 else BLACK[2])
    for n in range(21):
        ticks(panel, 32, 36, -135 + 13.5 * n, 12.5 if n % 5 == 0 else 14.5, 14.5, LEGEND)
    text(panel, '0', 15, 47, LEGEND);text(panel, '100', 46, 47, LEGEND)
    centred(panel, 'LOAD', 32, 13, LEGEND)
    centred(panel, '-', 10, 34, LEGEND);centred(panel, '+', 54, 34, LEGEND)
    fill(panel, 18, 52, 45, 60, PANEL['rim']);fill(panel, 19, 53, 45, 60, SCREEN)
    panel.save(directory / 'panel_3.png', optimize=True)

    chars = '0123456789.%VA'
    im = Image.new('RGBA', (1024,128));d = ImageDraw.Draw(im);font = ImageFont.truetype(MONO,88)
    for i,char in enumerate(chars):d.text((i*64+32,65),char,anchor='mm',font=font,fill=(236,225,190,255))
    im.save(directory/'readout.png', optimize=True)
    glass = Image.new('RGBA', (16, 16))
    for step in range(8):
        x, y = step * 2, step * 2
        fill(glass, x, y, x + 5, y + 1, (219, 231, 228, 40));fill(glass, x + 2, y, x + 3, y + 1, (255, 255, 243, 77))
    glass.save(directory/'glass_reflection.png', optimize=True)


def generate(assets, write):
    directory = assets / 'textures/block/electrolysis_cell'
    directory.mkdir(parents=True, exist_ok=True)
    for name, image in (('casing', sheet([casing_texture(assets, joined) for joined in range(16)], 4)), ('top', sheet([top_texture(assets, joined) for joined in range(16)], 4)),
                        ('plate', plate_texture()), ('materials', materials_texture()), ('porcelain', porcelain_texture())):
        image.save(directory / (name + '.png'), optimize=True)
    panel_textures(directory)
    for stale in ('glass', 'steel', 'sheet', 'copper', 'ceramic', 'gasket', 'panel_base', 'fittings'):
        (directory/(stale+'.png')).unlink(missing_ok=True)
    path = f'{MOD}:block/electrolysis_cell/'
    textures = {'surface': path + 'materials', 'porcelain': path + 'porcelain', 'casing': path + 'casing', 'top': path + 'top',
                'plate': path + 'plate',
                'inner': 'create:block/industrial_iron_block', 'particle': path + 'casing'}

    def face(side, a, b, material):
        if isinstance(material, dict): return material
        x,y,z = a;X,Y,Z = b
        material,index = material if isinstance(material, tuple) else (material, 0)
        if material in TILES:
            uv = {'north': [16-X,16-Y,16-x,16-y], 'south': [x,16-Y,X,16-y], 'west': [z,16-Y,Z,16-y], 'east': [16-Z,16-Y,16-z,16-y],
                  'up': [x,z,X,Z], 'down': [x,16-Z,X,16-z]}[side]
            grid = GRID.get(material, 1);origin = (index % grid * 16, index // grid * 16)
            return {'texture': '#'+material, 'uv': [(origin[n % 2] + max(0, min(16, value))) / grid for n, value in enumerate(uv)]}
        w,h = {'up': (X-x, Z-z), 'down': (X-x, Z-z), 'north': (X-x, Y-y), 'south': (X-x, Y-y), 'west': (Z-z, Y-y), 'east': (Z-z, Y-y)}[side]
        if material == 'porcelain': return {'texture': '#porcelain', 'uv': [0,0,min(16,w),min(16,h)]}
        u,v = QUADRANTS[material]
        return {'texture': '#surface', 'uv': [u,v,u+min(8,w/2),v+min(8,h/2)]}

    def cube(e, a, b, material='steel', only=SIDES, **faces):
        e.append({'from': a, 'to': b, 'faces': {side: face(side, a, b, faces.get(side, material)) for side in only}})

    def front_box(e, x0,y0,x1,y1, depth, material='black', front=None):
        cube(e, [16-x1,y0,-depth], [16-x0,y1,0], material, ('down','up','north','west','east'), north=front or material)

    def disc(e, radius, z0,z1, material):
        inset = radius * .4
        cube(e,[-radius+inset,-radius,z0],[radius-inset,radius,z1],material)
        for x0,x1 in ((-radius,-radius+inset),(radius-inset,radius)):cube(e,[x0,-radius+inset,z0],[x1,radius-inset,z1],material)

    def turn(elements, count):
        names = {'north':'east','east':'south','south':'west','west':'north','up':'up','down':'down'}
        for element in elements:
            a,b=element['from'],element['to']
            for _ in range(count):
                a,b=[16-b[2],a[1],a[0]],[16-a[2],b[1],b[0]]
                element['faces']={names[k]:v for k,v in element['faces'].items()}
            element['from'],element['to']=a,b
        return elements

    def model(name,e,**extra):
        write(assets / f'models/block/electrolysis_cell/{name}.json', {'parent':'minecraft:block/block','textures':textures,
              'render_type':'minecraft:cutout','elements':e,**extra})

    turns={'north':0,'east':1,'south':2,'west':3}
    outward=('down','up','north','west','east')
    # A wall shows the tile whose joined edges match the neighbouring cells, so the frame runs around the whole machine.
    left={'north':'east','east':'south','south':'west','west':'north'}
    def joined(state, up, down, before, after):
        return {'true': UP, 'false': 0}[state[up]] | {'true': DOWN, 'false': 0}[state[down]] | {'true': LEFT, 'false': 0}[state[before]] | {'true': RIGHT, 'false': 0}[state[after]]

    def wall(kind, tile, axis='z'):
        e=[]
        if kind=='side':cube(e,[0,0,0],[16,16,2],('casing',tile),('north','south'),south='inner')
        elif kind=='down':cube(e,[0,0,0],[16,2,16],('casing',tile),('down','up'),up='inner')
        else:
            # Rails run along the stack and caps close only the ends of a column, so the hatches of one column join into a single
            # long hatch; the frame tile still follows the joined edges like the rest of the casing.
            if axis=='z':
                rails=((([0,14,0],[3,16,16]),'east'),(([13,14,0],[16,16,16]),'west'))
                caps=((not tile&UP,([3,14,0],[13,16,3]),'south'),(not tile&DOWN,([3,14,13],[13,16,16]),'north'))
            else:
                rails=((([0,14,0],[16,16,3]),'south'),(([0,14,13],[16,16,16]),'north'))
                caps=((not tile&LEFT,([0,14,3],[3,16,13]),'east'),(not tile&RIGHT,([13,14,3],[16,16,13]),'west'))
            for (a,b),opening in rails:cube(e,a,b,('top',tile),('down','up',opening),down='inner',**{opening:'iron'})
            for present,(a,b),opening in caps:
                if present:cube(e,a,b,('top',tile),('down','up',opening),down='inner',**{opening:'iron'})
        return e

    multipart=[]
    for kind in ('side','down'):
        for tile in range(16):model(f'wall_{kind}_{tile}',wall(kind,tile))
    for axis in ('x','z'):
        for tile in range(16):model(f'wall_up_{axis}_{tile}',wall('up',tile,axis))
    for flags in range(16):
        state=['true' if flags>>n&1 else 'false' for n in range(4)]
        for side in turns:
            when={side:'false','up':state[0],'down':state[1],left[side]:state[2],left[left[left[side]]]:state[3]}
            # The rim stays along the top edge under the electrode head, so the casing does not end in bare planks.
            tile=joined(when,'up','down',left[side],left[left[left[side]]])&~UP
            multipart.append({'when':when,'apply':{'model':f'{MOD}:block/electrolysis_cell/wall_side_{tile}','y':turns[side]*90}})
        when=dict(zip(('north','south','west','east'),state))
        multipart.append({'when':{'down':'false',**when},'apply':{'model':f"{MOD}:block/electrolysis_cell/wall_down_{joined(when,'south','north','west','east')}"}})
        for axis in ('x','z'):
            multipart.append({'when':{'axis':axis,**when},
                              'apply':{'model':f"{MOD}:block/electrolysis_cell/wall_up_{axis}_{joined(when,'north','south','west','east')}"}})
    for stale in SIDES:(assets/f'models/block/electrolysis_cell/wall_{stale}.json').unlink(missing_ok=True)
    for stale in [f'wall_up_{n}' for n in range(16)]+['top_rails_x','top_rails_z']+[f'top_cap_{side}' for side in turns]:
        (assets/f'models/block/electrolysis_cell/{stale}.json').unlink(missing_ok=True)
    # Rails along the outer sides at the bottom and the top, half a pixel proud of the wall so the casing reads thick, and kept with the
    # top rim under an electrode head: each is the light upper and dark lower rim of Create's andesite casing.
    textures['andesite']='create:block/andesite_casing'
    rim=lambda v:{'texture':'#andesite','uv':[0,v,16,v+2]}
    for axis in ('x','z'):
        for side in (('north','south') if axis=='x' else ('west','east')):
            for edge in ('down','up'):
                y=0 if edge=='down' else 12
                e=[{'from':[0,y+2,-.5],'to':[16,y+4,0],'faces':{'north':rim(0),'up':rim(0),'west':rim(0),'east':rim(0)}},
                   {'from':[0,y,-.5],'to':[16,y+2,0],'faces':{'north':rim(14),'down':rim(14),'west':rim(14),'east':rim(14)}}]
                when={'axis':axis,side:'false',**({'down':'false'} if edge=='down' else {})}
                name=f'rail_{axis}_{side}_{edge}';model(name,turn(e,turns[side]));multipart.append({'when':when,'apply':{'model':f'{MOD}:block/electrolysis_cell/{name}'}})
    textures.pop('andesite')
    for axis in ('x','z'):
        for end in (('west','east') if axis=='x' else ('north','south')):
            e=[]
            for x in (0,14):
                cube(e,[x,0,-.5],[x+2,16,0],'iron',outward)
                for y in (2,12):cube(e,[x,y,-1],[x+2,y+2,-.5],'brass',outward)
            model('end_'+end,turn(e,turns[end]));multipart.append({'when':{'axis':axis,end:'false'},'apply':{'model':f'{MOD}:block/electrolysis_cell/end_{end}'}})
    write(assets/'blockstates/electrolysis_cell.json',{'multipart':multipart})
    # Up to sixteen plates a head, one pixel apart, anodes and cathodes alternating with their lugs on opposite sides.
    for count in range(1,17):
        e=[]
        for i in range(count):
            z=.25+i;cube(e,[3,1,z],[13,15,z+.5],'plate');cube(e,[3 if i%2 else 11,15,z],[5 if i%2 else 13,16,z+.5],'brass')
        for x in (2,13):cube(e,[x,1,0],[x+1,3,16],'brass')
        model('plates_'+str(count),e)
    for stale in ('hatch','hatch_north','hatch_south','hatch_both'):(assets/f'models/block/electrolysis_cell/{stale}.json').unlink(missing_ok=True)
    # The electrode head of a Z-oriented stack, turned a quarter for X, drawn like Create's mechanical mixer: an andesite-cased gearbox
    # on top whose press-top lid the spindle passes through, standing on four andesite posts on the cell rim, with glass between the posts
    # so the gas stays in while the plates show. The end panes stand half a pixel in, so the panes of two joined heads do not share a
    # plane.
    raw=lambda texture,uv=None:{'texture':'#'+texture,**({'uv':uv} if uv else {})}
    textures.update({'glass':'minecraft:block/glass','mixer':'create:block/mixer_base_side','lid':'create:block/mechanical_press_top',
                     'andesite':'create:block/andesite_casing','press':'create:block/mechanical_press_head','pole':'create:block/mechanical_press_pole'})
    # The crosshead the plates hang from, like the head of Create's press, and its rod, built like the pole of Create's mechanical mixer:
    # it runs up through the gearbox around the spindle and out of the lid, so it rises and sinks above the head as the plates move. It
    # is drawn in the plates' frame, half a block up so the model stays within its bounds: its plate sits on top of the plates' lugs.
    e=[{'from':[2,8,1],'to':[14,10,15],'faces':{side:raw('press') for side in SIDES}}]
    for y0,y1,v0,v1,top in ((10,20,6,16,False),(20,28,0,8,True)):
        e.append({'from':[5.995,y0,6],'to':[10.005,y1,10],'faces':{'east':raw('pole',[6,v0,10,v1]),'west':raw('pole',[6,v0,10,v1]),**({'up':raw('pole',[11,1,15,5])} if top else {})}})
        for z0,cap in ((5,[10,5,16,6]),(10,[10,0,16,1])):
            e.append({'from':[5,y0,z0],'to':[11,y1,z0+1],'faces':{'north':raw('pole',[0,v0,6,v1]),'south':raw('pole',[0,v0,6,v1]),'east':raw('pole',[0,v0,1,v1]),
                      'west':raw('pole',[5,v0,6,v1]),**({'up':raw('pole',cap)} if top else {})}})
    model('crosshead',e)
    e=[{'from':[0,10,0],'to':[16,16,16],'faces':{**{side:raw('mixer') for side in ('north','south','west','east')},'up':raw('lid'),'down':raw('andesite')}}]
    for x in (0,14):
        for z in (0,14):e.append({'from':[x,0,z],'to':[x+2,10,z+2],'faces':{side:raw('andesite',[0,6,2,16]) for side in ('north','south','west','east','down')}})
        e.append({'from':[x+.5,0,2],'to':[x+1.5,10,14],'faces':{'west':raw('glass',[0,0,12,10]),'east':raw('glass',[0,0,12,10])}})
    for z in (.5,14.5):e.append({'from':[2,0,z],'to':[14,10,z+1],'faces':{'north':raw('glass',[0,0,12,10]),'south':raw('glass',[0,0,12,10])}})
    model('head',e)
    for key in ('glass','mixer','lid','andesite','press','pole'):textures.pop(key)
    (assets/'models/block/electrolysis_cell/head_terminal.json').unlink(missing_ok=True)
    write(assets/'blockstates/electrolysis_electrode_head.json',{'variants':{f'axis={axis}':
        {'model':f'{MOD}:block/electrolysis_cell/head',**({'y':90} if axis=='x' else {})} for axis in ('x','z')}})
    e=[];cube(e,[0,0,0],[1,1,1],'porcelain');model('bubble',e)
    e=[];cube(e,[-.125,0,-.04],[.125,4,.04],'black');cube(e,[-.2,-.85,-.055],[.2,0,.055],'black')
    cube(e,[-.3,-.3,-.09],[.3,.3,.09],'steel');model('needle',e)
    e=[];disc(e,.8,-.45,0,'porcelain');model('push_button',e)
    e=[];disc(e,2.5,-.75,0,'black')
    cube(e,[-.25,.75,-1],[.25,2.5,-.75],'porcelain');model('dial',e)
    for stale in ('lever','estop','toggle'):(assets/f'models/block/electrolysis_cell/{stale}.json').unlink(missing_ok=True)
    for stale in ('panel_0','panel_1','nixie_e','nixie_cathodes'):(directory/(stale+'.png')).unlink(missing_ok=True)

    # Part 0 is the load dial below, part 1 the meters and START/STOP above, both in Create's andesite alloy block.
    textures['alloy']='create:block/andesite_block'
    for part,panel in ((0,3),(1,2)):
        textures['panel']=path+f'panel_{panel}'
        alloy={'texture':'#alloy'}
        e=[];cube(e,[0,0,0],[16,16,16],'casing',north='panel',south='inner',east=alloy,west=alloy,up=alloy,down=alloy)
        if part==1:
            for x0,y0,x1,y1 in ((1,8,8,15),(8,8,15,15)):
                front_box(e,x0,y0,x1,y1,.7,'black',{'texture':'#panel','uv':[x0,16-y1,x1,16-y0]})
            for xx in (4,12):
                front_box(e,xx-2,1.5,xx+2,5.5,.4,'black',{'texture':'#panel','uv':[xx-2,10.5,xx+2,14.5]})
                for x0,y0,x1,y1 in ((xx-1.5,4.5,xx+1.5,5),(xx-1.5,2,xx+1.5,2.5),(xx-1.5,2.5,xx-1,4.5),(xx+1,2.5,xx+1.5,4.5)):
                    cube(e,[16-x1,y0,-.65],[16-x0,y1,-.4],'steel',outward)
            # The stack's two power terminals stand on top: Electro Energetics' own connector, its stem, two discs and the cap the wires
            # attach to, with its texture, at two thirds of its size over each quarter of the top.
            sides=('north','south','west','east');textures['connector']='electroenergetics:block/connector'
            for x in (4,12):
                # Each part as the connector draws it about its own centre line, from its base: half width, bottom, top.
                for half,y0,y1,side,top in ((1,0,6,[14,0,16,6],None),(3,.5,2.5,[0,6,6,8],[0,9,6,15]),(3,3.5,5.5,[0,6,6,8],[0,9,6,15]),(2,6,10,[0,2,4,6],[4,2,8,6])):
                    r=half*TERMINAL_SCALE
                    e.append({'from':[x-r,16+y0*TERMINAL_SCALE,8-r],'to':[x+r,16+y1*TERMINAL_SCALE,8+r],'faces':{**{f:{'texture':'#connector','uv':side} for f in sides},
                              **({'up':{'texture':'#connector','uv':top},'down':{'texture':'#connector','uv':top}} if top else {})}})
        model('controller_'+str(part),e);textures.pop('connector',None)
    for stale in ('controller_2','controller_3'):(assets/f'models/block/electrolysis_cell/{stale}.json').unlink(missing_ok=True)
    textures.pop('panel',None);textures.pop('alloy')
    write(assets/'blockstates/electrolysis_cell_controller.json',{'variants':{f'facing={face},part={part}':
        {'model':f'{MOD}:block/electrolysis_cell/controller_{part}','y':turn} for face,turn in (('north',0),('east',90),('south',180),('west',270)) for part in range(2)}})
    e=wall('down',0)+wall('up',0,'z')
    for side in turns:e+=turn(wall('side',0),turns[side])
    model('item',e)
    for name,parent in (('electrolysis_cell','item'),('electrolysis_cell_controller','controller_1'),('electrolysis_electrode_head','head')):
        write(assets/f'models/item/{name}.json',{'parent':f'{MOD}:block/electrolysis_cell/{parent}'})
