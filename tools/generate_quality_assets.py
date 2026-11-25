#!/usr/bin/env python3
"""Final, deterministic 16px surface pass. Run after the geometry generators.

No image library needed. The bucket template records the vanilla metal pixels;
only vanilla's water-content mask changes between all sixteen containers.
"""
import json, struct, zlib
from pathlib import Path
from minecraft_style import pixels, PALETTE, grain, metal_pixel
from powder_assets import write_powder_item

ROOT = Path(__file__).resolve().parents[1]
A = ROOT/'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'

def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False)+'\n')

def png(name, px):
    def chunk(k,d): return struct.pack('>I',len(d))+k+d+struct.pack('>I',zlib.crc32(k+d)&0xffffffff)
    path=A/'textures'/(name+'.png');path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+
        chunk(b'IDAT',zlib.compress(b''.join(b'\0'+bytes(c for p in row for c in p) for row in px),9))+chunk(b'IEND',b''))

def item(name, px):
    png('item/'+name,px)
    write(A/'models/item'/(name+'.json'),{'parent':'minecraft:item/generated','textures':{'layer0':NS+'item/'+name}})

def blank(): return [[(0,0,0,0) for _ in range(16)] for _ in range(16)]

def rect(p,x,y,w,h,c):
    for yy in range(y,y+h):
        for xx in range(x,x+w):p[yy][xx]=c+(255,) if len(c)==3 else c

def metal_rect(p,x,y,w,h,material='stainless'):
    for yy in range(y,y+h):
        for xx in range(x,x+w):
            p[yy][xx]=metal_pixel(material,PALETTE[material],xx,yy)

template=json.loads((ROOT/'tools/vanilla_bucket_template.json').read_text())
BUCKETS={
 'crude_oil':((48,37,29),'viscous'),
 'gasoline':((211,204,153),'clear'), 'diesel':((184,158,91),'oil'),
 'hydrogen_gas':((220,225,224),'gas'),
 'oxygen_gas':((204,218,222),'gas'), 'nitrogen_gas':((218,218,210),'gas'),
 'ammonia_gas':((217,225,204),'gas'),
 'nitric_oxide':((201,211,208),'gas'), 'nitrogen_dioxide':((153,89,49),'gas'),
 'ethylene':((211,220,213),'gas'), 'propylene':((217,212,203),'gas'), 'converter_gas':((174,183,169),'gas'),
 'argon_gas':((214,200,226),'gas'),
}
for number,(name,(color,kind)) in enumerate(BUCKETS.items()):
    p=[[tuple(template['pixels'][y*16+x]) for x in range(16)] for y in range(16)]
    for i in template['content_mask']:
        x,y=i%16,i//16
        shade=18 if y==4 else -22 if y>=7 else 0
        if kind=='viscous' and (x+y)%5==0:shade=-12
        if (x,y) in ((5+number%3,5),(6+number%3,5)):shade=34
        if kind=='gas' and y==6 and x in (6+number%3,10-number%2):shade=28
        p[y][x]=tuple(max(0,min(255,c+shade)) for c in color)+(255,)
    item(name+'_bucket',p)

# Shaped catalyst granules retain their manufactured form.
for name,color,centers in [
 ('iron_catalyst',(115,121,115),[(4,5),(9,4),(6,10),(11,10)])]:
    p=blank()
    for x,y in centers:
        rect(p,x-1,y-1,4,3,(58,62,56));rect(p,x,y-1,2,2,color)
        rect(p,x,y-1,1,1,tuple(min(255,c+28) for c in color))
    if name=='iron_catalyst':rect(p,3,13,10,1,(149,155,144))
    item(name,p)

for name in ('ammonium_nitrate', 'sulfur_dust'):
    write_powder_item(name)

# Components have distinct manufactured shapes, not a recolored copy of a pellet.
for name,kind in [('polypropylene','rods'),('polymerization_catalyst','honeycomb')]:
    p=blank()
    if kind=='rods':
        for x,y in [(3,5),(7,3),(10,8),(4,11)]:
            rect(p,x,y,3,4,(74,83,79));rect(p,x,y,2,3,(205,212,196));rect(p,x,y,1,2,(233,234,216))
    else:
        rect(p,3,2,10,12,(65,74,66));rect(p,4,3,8,10,(155,162,128))
        for y in (4,7,10):
            for x in (5,8,11):rect(p,x,y,1,2,(71,82,68))
        rect(p,5,1,6,1,(181,183,154))
    item(name,p)

# Distinct front / side / top skins for every modular machine. The same folded
# steel material can be shared, while the complete functional panel cannot.
roles={'gas_compressor':0,'process_heater':1,'reaction_vessel':3,'fraction_separator':5,'distillation_controller':6,'catalyst_unit':7,'process_chamber':8,'column_tray':9}
for role,n in roles.items():
    # The retired gas compressor controller keeps its slot so the other panels keep their variation.
    if role=='gas_compressor':continue
    for face in ['front','side','top']:
        p=pixels('stainless',PALETTE['stainless'])
        if face=='front':
            rect(p,4,4,8,8,(78,90,99));metal_rect(p,5,5,6,6)
            if role=='process_heater':
                for y in (5,8,11):rect(p,5,y,6,1,(62,70,69))
                if role=='process_heater':metal_rect(p,5,10,6,1,'copper')
            elif role=='fraction_separator':
                for x in (5,8,11):rect(p,x,5,1,6,(231,238,235))
            elif role=='distillation_controller':
                rect(p,6,5,4,3,(213,210,184));rect(p,8,6,1,2,(60,72,71))
                for x in (6,9):metal_rect(p,x,10,1,1,'brass')
            elif role=='reaction_vessel':
                rect(p,6,5,4,6,(60,76,75));rect(p,7,6,1,4,(169,193,187))
            elif role=='catalyst_unit':
                for y in (5,8,11):
                    for x in (5,8,11):metal_rect(p,x,y,1,1,'brass')
            elif role=='process_chamber':metal_rect(p,6,6,4,4,'paint_blue')
            else:
                for y in (5,7,9,11):rect(p,5,y,6,1,(68,76,74))
        elif face=='side':
            metal_rect(p,4,4,8,8,'paint_blue');rect(p,5,5,6,1,(237,244,242))
            # Small material / service marking, consistently placed on the hatch.
            rect(p,5+n%3,8,1,3,(93,107,116));rect(p,8,8+n%2,3,1,(93,107,116))
        else:
            metal_rect(p,4,4,8,8,'paint_blue')
            for x in range(5,12,2):rect(p,x,5,1,6,(73,83,81))
            metal_rect(p,5+n%5,12,2,1,'brass')
        # Grain crosses the panel, hatch and seams, not just unused base pixels.
        # Dark slots and bright screw heads remain legible at native resolution.
        for yy in range(16):
            for xx in range(16):
                v=grain(role+'_'+face,xx,yy,.65)
                p[yy][xx]=tuple(max(0,min(255,c+v)) for c in p[yy][xx][:3])+(255,)
        png('block/panels/'+role+'_'+face,p)
    for suffix in ['', '_active'] if role in roles[:8] else ['']:
        path=A/'models/block'/(role+suffix+'.json');m=json.loads(path.read_text())
        for face in ['front','side','top']:m['textures'][face]=NS+'block/panels/'+role+'_'+face
        # The largest body receives role-specific surfaces; collars/gauges stay shared.
        e=max(m['elements'],key=lambda e:__import__('math').prod(b-a for a,b in zip(e['from'],e['to'])))
        for face,v in e['faces'].items():v.update(texture='#'+('front' if face=='north' else 'top' if face=='up' else 'side'),uv=[0,0,16,16])
        write(path,m)

# Give inherited vanilla construction surfaces their own material treatment.
materials={'minecraft:block/iron_block':('steel_panel','stainless','metal'),
 'minecraft:block/redstone_block':('power_terminal','cast_iron','metal'),
 'minecraft:block/coal_block':('graphite','cast_iron','mesh'),
 'minecraft:block/gray_wool':('diaphragm','ceramic','mesh'),
 'minecraft:block/polished_granite':('granite_lining','refractory','ceramic'),
 'minecraft:block/copper_block':('copper_flange','copper','metal'),
 'create:block/pipes':('steam_jacket','stainless','metal'),
 'create:block/pipes_connected':('steam_joints','cast_iron','metal')}
for source,(name,color,style) in materials.items():png('block/plant/'+name,pixels(color,PALETTE[color],style))
for path in (A/'models/block').rglob('*.json'):
    if 'large_flywheel' in str(path) or path.name=='empty.json':continue
    m=json.loads(path.read_text(encoding='utf-8-sig'));changed=False
    for k,v in m.get('textures',{}).items():
        if v in materials:m['textures'][k]=NS+'block/plant/'+materials[v][0];changed=True
    if changed:write(path,m)

# Loose blade components retain their individual sprites; housings and ends use the placed section mesh.
for number,name in enumerate(['turbine_blade','high_pressure_turbine_blade','low_pressure_turbine_blade']):
    p=blank()
    rect(p,6,6,4,4,(74,83,81));rect(p,7,7,2,2,(180,188,176))
    for x,y,w,h in ([(7,1,2,5),(10,7,5,2),(7,10,2,5),(1,7,5,2)] if number!=1 else [(6,2,4,4),(10,6,4,4),(6,10,4,4),(2,6,4,4)]):
        rect(p,x,y,w,h,(147,160,154));rect(p,x,y,1,h,(202,209,196))
    if number==2:rect(p,7,2,1,3,(174,145,89));rect(p,11,7,3,1,(174,145,89))
    item(name,p)

print('Unified 16 vanilla-body buckets; distinct material icons, turbine parts and 30 module surfaces.')
