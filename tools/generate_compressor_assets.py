"""Shared cast-fitting textures, pressure gauge recipe and synthesis-gas mixing recipe."""
from pathlib import Path
import json
import math
import struct
import zlib
from minecraft_style import PALETTE, METAL_REFLECTION, grain

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/create_refinery_n_reactors'
D=ROOT/'src/main/resources/data/create_refinery_n_reactors'
NS='create_refinery_n_reactors:'
T=NS+'block/gas_compressor/'


def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')


def textures():
    alloys={'steel':'stainless','cast':'cast_iron','base':'cast_iron','brass':'brass','panel':'cast_iron'}
    colors={name:PALETTE[alloy] for name,alloy in alloys.items()}
    colors.update({'cast':(100,108,113),'base':(97,108,114),'fins':(168,179,185),'panel':(51,62,69),'dial':(211,205,177)})
    bands=(0,15,28,38,25,10,0,-9,-16,-10,-2,8,20,12,0,-20)
    ticks={(round(7.5+math.sin(math.radians(a))*5.5),round(7.5-math.cos(math.radians(a))*5.5)) for a in range(-135,136,45)}
    def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
    for name,color in colors.items():
        raw=b''
        for y in range(16):
            row=[]
            for x in range(16):
                pixel=color
                if name in alloys:
                    alloy=alloys[name]
                    shade=bands[(x+y//5)%16]+grain(alloy,x,y,1.8 if name=='cast' else .9)
                    shade+=(5 if y<6 else -4 if y>10 else 0)+(-2,1,0,2)[y%4]
                    rivets=()
                    if name in ('steel','cast','brass'):
                        if x==0 or y==0:shade=-37
                        if x==1 or y==1:shade=58
                        if x==15 or y==15:shade=-46
                        if x==14 or y==14:shade=-14
                        if name=='cast':
                            if y==12 and 3<=x<=12:shade-=18
                            rivets=((3,3),(11,3),(3,13),(11,13))
                        elif name=='steel':rivets=((3,13),(11,13))
                    elif name=='base':
                        if y in (0,8):shade=-32
                        if y in (1,9):shade=52
                        if y in (6,14):shade=-22
                        if y in (7,15):shade=-56
                        rivets=((3,3),(11,11))
                    elif name=='panel':
                        shade//=3
                        if x==0 or y==0:shade=38
                        if x==15 or y==15:shade=-52
                    for rx,ry in rivets:
                        if (x,y)==(rx,ry):shade=62
                        elif (x,y)==(rx+1,ry):shade=16
                        elif (x,y) in ((rx,ry+1),(rx+1,ry+1)):shade=-41
                    shadow,highlight=METAL_REFLECTION[alloy]
                    target=highlight if shade>=0 else shadow
                    pixel=tuple(round(c+(t-c)*min(abs(shade)/64,1)) for c,t in zip(color,target))
                elif name=='fins':
                    pixel=((226,233,235),(168,183,192),(105,123,135),(49,64,75))[x%4]
                    if y in (0,15):pixel=tuple(max(0,c-15) for c in pixel)
                if name=='dial':
                    radius=math.hypot(x-7.5,y-7.5)
                    pixel=[58,65,61] if radius>7 or (x,y) in ticks else [160,126,66] if radius>6 else [211,205,177]
                row.extend([*pixel,255])
            raw+=b'\0'+bytes(row)
        path=A/'textures/block/gas_compressor'/f'{name}.png';path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))


def survival():
    recipes={
        'pressure_gauge':([' B ','BGB',' I '],{'B':{'tag':'c:nuggets/brass'},'G':{'item':'minecraft:glass_pane'},'I':{'tag':'c:plates/iron'}},1),
    }
    for name,(pattern,key,count) in recipes.items():
        write(D/f'recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':key,'result':{'id':NS+name,'count':count}})
        write(D/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+name}],
                                                                                    'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    write(D/'recipe/mixing/synthesis_gas.json',{'type':'create:mixing','ingredients':[{'type':'neoforge:single','amount':750,'fluid':NS+'hydrogen_gas'},
          {'type':'neoforge:single','amount':250,'fluid':NS+'nitrogen_gas'}],'results':[{'amount':1000,'id':NS+'synthesis_gas'}]})


if __name__=='__main__':
    textures();survival()
    print('Generated the shared compressor textures and the synthesis gas mixing recipe')
