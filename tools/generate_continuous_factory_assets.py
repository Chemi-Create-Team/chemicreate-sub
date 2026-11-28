#!/usr/bin/env python3
"""Original 16 px factory finishes, fitted block models, manufacturing recipes and Ponder stages."""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw
from generate_petrochemical_ponder import NS, OUT, Structure, string

ROOT = Path(__file__).resolve().parents[1] / 'atomics/src/main/resources'
A = ROOT / 'assets' / NS[:-1]
D = ROOT / 'data' / NS[:-1]
FACTORIES = ('rotary_kiln', 'rolling_mill', 'coil_winder')
BLOCKS = FACTORIES + ('rolling_stand', 'winder_brake', 'continuous_factory_outlet', 'continuous_factory_frame', 'rotary_kiln_exhaust')
ITEMS = ('deformed_workpiece', 'loose_winding', 'thick_steel_plate', 'steel_h_beam', 'steel_rail_stock', 'copper_foil',
         'aluminum_foil', 'steel_core_aluminum_cable', 'heavy_wire_rope')


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf8')


def textured_tile(name, rim, inside, light, dark, spots, pattern):
    atlas = A / 'textures/block/continuous_factory'
    atlas.mkdir(parents=True, exist_ok=True)
    image = Image.new('RGB', (16, 16), dark)
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), fill=inside)
    draw.line([(1, 14), (1, 1), (14, 1)], fill=light)
    draw.line([(2, 13), (13, 13), (13, 2)], fill=rim)
    rng = random.Random(name)
    for i in range(18):
        x, y = rng.randrange(2, 13), rng.randrange(2, 13)
        draw.rectangle((x, y, x + rng.randrange(2), y), fill=spots[i % len(spots)])
    for color, coords in pattern:
        draw.rectangle(coords, fill=color)
    image.save(atlas / (name + '.png'))


def textures():
    textured_tile('barrel', '#57534e', '#747779', '#96908a', '#373837', ['#795e50', '#876e5d', '#65696a', '#82827c'],
                  [('#544843',(2,2,3,3)),('#9d8269',(3,2,3,2)),('#4b4441',(11,11,12,12)),('#9e8065',(12,11,12,11))])
    textured_tile('steel', '#516067', '#636d70', '#88908e', '#30363a', ['#717c7c', '#586263', '#5b696b'],
                  [('#748485',(5,2,6,12)),('#485357',(9,2,10,12))])
    textured_tile('gear', '#44433e', '#505251', '#797b75', '#292d2d', ['#666564', '#575954', '#494542'],
                  [('#777b73',(2,2,3,3)),('#2b2c2b',(12,11,13,12))])
    textured_tile('copper', '#865737', '#a36b4a', '#cda580', '#4e382c', ['#ac7650', '#945b41', '#b28159'],
                  [('#744933',(4,2,4,13)),('#bd9160',(6,2,6,13)),('#714833',(11,3,11,12))])
    textured_tile('hot', '#d75821', '#f38733', '#ffb345', '#a43f23', ['#f67d2a', '#ff9f3d', '#ed6f27', '#f69a32'],
                  [('#ffbc50',(2,3,5,5)),('#e4632a',(9,9,12,10))])
    textured_tile('ore', '#56514b', '#78685b', '#9a8170', '#302e2d', ['#624d42', '#856557', '#8d795f'],
                  [('#6d4b3b',(3,7,5,9)),('#b29b80',(10,5,11,6))])
    textured_tile('housing', '#55595b', '#666a6a', '#8e948e', '#31373a', ['#6c6d68', '#737977', '#5b6260'],
                  [('#3e4341',(3,5,12,10)),('#7c8381',(4,6,11,6)),('#a79471',(2,2,3,3)),('#7b6a57',(12,12,13,13))])
    textured_tile('foundation', '#53595c', '#646b6d', '#818887', '#3f4447', ['#737877', '#596164', '#6a7072'],
                  [('#505759',(3,8,12,9)),('#7e8080',(4,10,11,10))])
    textured_tile('grille', '#575b59', '#545a5b', '#818582', '#303434', ['#5c6361', '#474e4f'],
                  [('#383f40',(i,4,i,11)) for i in (3,6,9,12)])


def box(lo, hi, side, **faces):
    return {'from': lo, 'to': hi, 'faces': {d: {'texture': '#' + faces.get(d, side)} for d in ('up','down','north','south','east','west')}}


def assets():
    metal = ('housing','foundation','steel','grille','gear','barrel','copper')
    low = box([0,0,0],[16,4,16],'foundation')
    trim = box([0,11,0],[16,13,16],'copper')
    geometries = {
        'rotary_kiln': [low, box([0,4,0],[16,13,16],'housing',south='grille'), trim, box([1,13,1],[15,16,15],'gear',up='grille')],
        'rolling_mill': [low, box([0,4,0],[16,14,16],'housing',south='grille'), box([0,13,0],[16,16,16],'steel',up='grille')],
        'coil_winder': [low, box([0,4,0],[16,14,16],'housing',south='copper'), box([0,14,0],[16,16,16],'grille')],
        'rolling_stand': [low, box([0,4,0],[16,13,16],'housing'), trim,
                          box([3,3,10],[13,13,16],'gear',south='steel'), box([5,5,15],[11,11,16],'copper')],
        'winder_brake': [low, box([0,4,0],[16,13,16],'gear',south='grille'), trim, box([5,5,12],[11,11,16],'copper')],
        'continuous_factory_outlet': [low, box([0,4,0],[16,16,16],'housing',up='grille',south='grille'),
                                      box([0,13,0],[16,15,16],'copper')],
        'continuous_factory_frame': [box([0,0,0],[16,16,16],'foundation')],
        'rotary_kiln_exhaust': [box([2,0,2],[14,4,14],'housing'),box([5,4,5],[11,13,11],'gear'),box([3,13,3],[13,16,13],'housing',up='grille')]
    }
    for block, elements in geometries.items():
        path = NS + 'block/continuous_factory/' + block
        write(A / f'models/block/continuous_factory/{block}.json', {'parent':'minecraft:block/block',
              'textures': {**{m:NS+'block/continuous_factory/'+m for m in metal}, 'particle':NS+'block/continuous_factory/foundation'},
              'elements': elements})
        variants = {'': {'model':path}} if block in ('continuous_factory_frame','rotary_kiln_exhaust') else {
            f'facing={d}': {'model':path,'y':angle} for d,angle in {'south':0,'west':90,'north':180,'east':270}.items()}
        write(A / f'blockstates/{block}.json', {'variants':variants})
        write(A / f'models/item/{block}.json', {'parent':path})
        write(D / f'loot_table/blocks/{block}.json', {'type':'minecraft:block','pools':[{'rolls':1,
            'entries':[{'type':'minecraft:item','name':NS+block}], 'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for name in ITEMS:
        image=Image.new('RGBA',(16,16))
        draw=ImageDraw.Draw(image)
        var = {'copper_foil':'#c38b5f','aluminum_foil':'#b7c2c5','loose_winding':'#a6734b','steel_core_aluminum_cable':'#9ba9ad'}.get(name,'#8b9699')
        if 'wire' in name or 'cable' in name or 'winding' in name:
            draw.ellipse((1,3,13,13), outline='#373b3c', width=4)
            draw.ellipse((2,3,13,12), outline=var, width=2)
            draw.ellipse((4,4,11,11), outline='#68777b', width=1)
            draw.line((10,11,14,14), fill=var, width=2)
        elif 'beam' in name or 'rail' in name:
            draw.polygon([(2,11),(9,2),(15,5),(8,14),(3,14)],fill='#495354')
            draw.line((3,11,10,3,14,5,8,13),fill=var,width=2)
            if 'beam' in name: draw.line((2,9,5,11,1,13,5,15,8,12),fill='#657175',width=2)
        else:
            draw.polygon([(1,10),(9,2),(15,6),(7,14),(1,12)],fill='#444b4b')
            draw.polygon([(1,10),(9,2),(15,6),(7,12)],fill=var)
            draw.line((1,10,9,2,14,6),fill='#d4cdc0' if name=='copper_foil' else '#ced8d8')
            if name=='deformed_workpiece': draw.line((4,8,7,7,6,10,11,7,13,7),fill='#444b4b',width=2)
        image.save(A/f'textures/item/{name}.png')
        write(A/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':NS+'item/'+name}})


def shaped(name, block, pattern, keys, count=1):
    write(D/f'recipe/{name}.json', {'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,
          'key':{k:{'item':v} for k,v in keys.items()},'result':{'id':block,'count':count}})


def recipes():
    steel = NS+'steel_ingot'
    casing = 'create:brass_casing'
    base = {'S':NS+'steel_block', 'F':NS+'continuous_factory_frame', 'B':casing, 'A':'create:shaft', 'C':'create:cogwheel'}
    shaped('continuous_factory_frame',NS+'continuous_factory_frame',['ISI','SPS','ISI'],{'I':'create:iron_sheet','S':steel,'P':base['S']},8)
    shaped('continuous_factory_outlet',NS+'continuous_factory_outlet',['FCF',' A '],{'F':base['F'],'C':'create:chute','A':base['A']})
    shaped('rotary_kiln_exhaust',NS+'rotary_kiln_exhaust',[' S ','SPS',' F '],{'S':steel,'P':NS+'stainless_steel_pipe','F':base['F']})
    shaped('rotary_kiln',NS+'rotary_kiln',['SCS','FBF',' A '],base)
    shaped('rolling_mill',NS+'rolling_mill',['FAF','SPS','FBF'],{**{k:base[k] for k in 'FASB'},'P':NS+'large_mechanical_press'})
    shaped('rolling_stand',NS+'rolling_stand',['FAF','CBC','FSF'],base)
    shaped('coil_winder',NS+'coil_winder',['FCF','BWB',' A '],{**{k:base[k] for k in 'FCBA'},'W':'minecraft:copper_block'})
    shaped('winder_brake',NS+'winder_brake',[' C ','FAF',' S '],{k:base[k] for k in 'CFAS'})

    def process(name, kind, feed, result, work=400, heat=0, thickness=.58, aux=None, damage=0):
        one = {'machine':kind, 'type':NS+'continuous_processing', 'feed':{'item':feed[0],'count':feed[1]},
               'result':{'id':result[0],'count':result[1]},'work':work,'heat':heat,'starting_thickness':thickness}
        if aux: one['auxiliary']={'item':aux[0],'count':aux[1]}
        if damage: one['minimum_damage']=damage
        write(D/f'recipe/continuous_processing/{name}.json',one)
    kiln,mill,wind=FACTORIES
    process('calcine_limestone',kiln,('minecraft:calcite',1),(NS+'quicklime',1),400,1)
    process('roast_iron_ore',kiln,('minecraft:raw_iron',1),('minecraft:iron_ingot',1),400,1)
    process('roast_pentlandite',kiln,(NS+'pentlandite',1),(NS+'nickel_oxide',1),400,1)
    for catalyst in ('iron_catalyst','oxidation_catalyst'):
        process('restore_'+catalyst,kiln,(NS+catalyst,1),(NS+catalyst,1),640,2,damage=1)
    process('thick_steel_plate',mill,(NS+'steel_block',1),(NS+'thick_steel_plate',3),400,1,.78)
    process('steel_h_beam',mill,(NS+'stainless_steel_block',1),(NS+'steel_h_beam',3),400,1,.67)
    process('steel_rail_stock',mill,(steel,3),(NS+'steel_rail_stock',6),400,1,.68)
    process('copper_foil',mill,('minecraft:copper_block',1),(NS+'copper_foil',12),400,1,.29)
    process('aluminum_foil',mill,(NS+'aluminum_ingot',4),(NS+'aluminum_foil',12),400,1,.27)
    process('wind_toroidal_coil',wind,(NS+'enamelled_copper_wire',16),(NS+'fusion_toroidal_field_coil',8),380,aux=(casing,2))
    process('wind_poloidal_coil',wind,(NS+'enamelled_copper_wire',16),(NS+'fusion_central_solenoid',8),380,aux=(NS+'steel_block',2))
    process('strand_steel_core_cable',wind,(NS+'aluminum_sheet',8),(NS+'steel_core_aluminum_cable',8),340,aux=(steel,2))
    process('strand_heavy_wire_rope',wind,(steel,8),(NS+'heavy_wire_rope',4),380,aux=('minecraft:iron_ingot',2))
    shaped('pressure_tank_from_heavy_plate',NS+'pressure_tank',['TBT','P P','TBT'],
           {'T':NS+'thick_steel_plate','B':'create:brass_sheet','P':NS+'stainless_steel_pipe'})
    shaped('rail_from_rolled_stock','minecraft:rail',['R R','RSR','R R'],{'R':NS+'steel_rail_stock','S':'minecraft:stick'},24)
    shaped('cable_from_steel_core',NS+'high_voltage_cable',[' R ','CCC',' R '],{'R':NS+'rubber_gasket','C':NS+'steel_core_aluminum_cable'},6)
    shaped('pulley_from_heavy_wire_rope','create:rope_pulley',[' A ','BRB',' S '],
           {'A':'create:shaft','B':casing,'R':NS+'heavy_wire_rope','S':steel})


def ponders():
    for name, length in zip(FACTORIES, (10, 10, 6)):
        scene=Structure(length + 6,9)
        x,y,z=4,2,2
        def frame(dx,dy,dz): scene.block(x+dx,y+dy,z+dz,NS+'continuous_factory_frame')
        def state(dx,dy,dz,block,facing, nbt=None): scene.block(x+dx,y+dy,z+dz,block,nbt=nbt,facing=facing)
        state(0,0,0,NS+name,'south',[string('id',NS+'continuous_factory')]); state(0,0,length,NS+'continuous_factory_outlet','south')
        if name=='rotary_kiln':
            for section in (1, length//2, length-2): frame(0,0,section)
            for section in (2,length-2):
                for col in (-1,0,1): frame(col,1,section)
            state(0,-1,0,'create:creative_motor','up')
            scene.block(x,y-1,z+length,'create:blaze_burner',blaze='kindled')
            scene.block(x,y+1,z,'create:chute',facing='down')
            scene.block(x,y+2,z,'create:chute',facing='down')
            scene.block(x,y+3,z,'minecraft:hopper',facing='down',enabled='true')
            scene.block(x,y+1,z+length,NS+'rotary_kiln_exhaust')
            scene.block(x,y+2,z+length,NS+'rotary_kiln_exhaust')
        elif name=='rolling_mill':
            for section in (2,5,8):
                for col in (-2,2):
                    for h in range(4):
                        if (col,h)!=(2,1): frame(col,h,section)
                for col in (-1,0,1): frame(col,3,section)
                for col in (-1,0,1): scene.block(x+col,y+4,z+section,'minecraft:cut_copper_slab',type='bottom',waterlogged='false')
                state(2,1,section,NS+'rolling_stand','east')
                state(3,1,section,'create:creative_motor','west')
            for dz in range(1, length):
                for col in (-1,0,1): scene.block(x+col,y,z+dz,'create:metal_girder',x='false',z='true',top='false',bottom='false',waterlogged='false')
            scene.block(x,y-1,z,'create:blaze_burner',blaze='kindled')
        else:
            for dx,dy,dz in ((-2,0,2),(-2,1,2),(2,0,2),(0,0,4)): frame(dx,dy,dz)
            state(2,1,2,NS+'winder_brake','east'); state(3,1,2,'create:creative_motor','west')
            state(0,-1,0,'create:creative_motor','up')
        scene.save(name)
        (OUT/f'{name}.nbt').replace(A/f'ponder/plant/{name}.nbt')


if __name__ == '__main__':
    textures()
    assets()
    recipes()
    ponders()
