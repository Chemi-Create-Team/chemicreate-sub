#!/usr/bin/env python3
"""Axial gas receiver models and a lesson for the horizontal and vertical pipe runs."""
from pathlib import Path
import json
from PIL import Image
from generate_plant_tank_assets import A, NS, write
from generate_ammonia_separator import octagon, box
from generate_petrochemical_ponder import Structure, string, named, integer

ID='gas_receiver'
TEXTURES={key:NS+'block/gas_receiver/'+key for key in ('shell','frame','cap')}
TEXTURES['particle']=TEXTURES['shell']


def generate():
    folder=A/'textures/block/gas_receiver';folder.mkdir(parents=True,exist_ok=True)
    for kind in ('shell','frame','cap'):
        image=Image.new('RGBA',(16,16))
        for y in range(16):
            for x in range(16):
                shade=0 if kind=='shell' else 22 if kind=='cap' else -20
                if x==0 or y==0:shade+=35
                if x==15 or y==15:shade-=27
                if kind=='shell' and x in (2,13) and y in (2,13):shade+=45
                grain=(x*5+y*3)%3-1
                image.putpixel((x,y),tuple(max(0,min(255,c+shade+grain)) for c in (100,110,103))+(255,))
        image.save(folder/(kind+'.png'))
    models=A/'models/block/gas_receiver'
    for p in models.glob('*.json'):p.unlink()
    variants={}
    for bottom,top,name in ((True,True,'single'),(True,False,'bottom'),(False,False,'middle'),(False,True,'top')):
        low,high=(3 if bottom else 0),(13 if top else 16)
        elements=octagon(6,low,high,'shell',caps=False)
        elements+=octagon(6.5,low,low+1,'frame')+octagon(6.5,high-1,high,'frame')
        for end,on in ((False,bottom),(True,top)):
            if not on:continue
            for radius,a,b in ((5,13,14),(4,14,15)):
                elements+=octagon(radius,a if end else 16-b,b if end else 16-a,'cap')
            elements.append(box([5,14 if end else 0,5],[11,16 if end else 2,11],'frame'))
            elements.append(box([4,15 if end else 0,4],[12,16 if end else 1,12],'cap'))
        write(models/(name+'.json'),{'parent':'minecraft:block/block','textures':TEXTURES,'elements':elements})
        for axis,rotation in (('y',{}),('z',{'x':270}),('x',{'x':90,'y':90})):
            variants[f'axis={axis},bottom={str(bottom).lower()},top={str(top).lower()}']={'model':NS+'block/gas_receiver/'+name,**rotation}
    write(A/'blockstates/gas_receiver.json',{'variants':variants})
    write(A/'models/item/gas_receiver.json',{'parent':NS+'block/gas_receiver/single'})
    for p in (A/'textures/block/tank').glob('gas_receiver*.png'):p.unlink()
    labels={'en_us':'Pulsation Damping Tube','ja_jp':'脈動抑制管','zh_cn':'脉动缓冲管'}
    for lang,label in labels.items():
        p=A/'lang'/f'{lang}.json';data=json.loads(p.read_text());data['block.create_refinery_n_reactors.gas_receiver']=label;write(p,data)
    scene=Structure(11,8)
    for axis,origin in (('x',(3,2,4)),('y',(4,2,7)),('z',(7,2,7))):
        for n in range(3):
            at=tuple(origin[i]+(n if i=='xyz'.index(axis) else 0) for i in range(3))
            tags=[string('id',NS+ID),named(3,'Size',integer(1)),named(3,'Height',integer(3))]
            if n:tags.append(named(11,'Controller',b''.join(integer(v) for v in (3,*origin))))
            scene.block(*at,NS+ID,nbt=tags,axis=axis,bottom=str(n==0).lower(),top=str(n==2).lower(),shape='plain')
    def pipe(x,y,z,*sides):scene.block(x,y,z,'create:fluid_pipe',waterlogged='false',**{s:str(s in sides).lower() for s in ('north','south','east','west','up','down')})
    for x in (1,9):scene.block(x,2,4,NS+'pressure_tank',bottom='true',top='true',shape='window')
    for x in (2,6):scene.block(x,2,4,'create:mechanical_pump',facing='east',waterlogged='false')
    for x in (7,8):pipe(x,2,4,'east','west')
    pipe(4,1,7,'up','down');pipe(4,5,7,'up','down');pipe(7,2,6,'north','south');pipe(7,2,10,'north','south')
    scene.block(1,1,1,'create:fluid_tank',bottom='true',top='true',shape='window')
    import generate_petrochemical_ponder as p
    old=p.OUT;p.OUT=A/'ponder/plant';scene.save('pulsation_tube');p.OUT=old


if __name__=='__main__':generate()
