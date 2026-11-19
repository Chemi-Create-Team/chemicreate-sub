#!/usr/bin/env python3
"""Native cuboid item models with their own pixel sheets, recipes and a small Ponder scene."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
NS='create_refinery_n_reactors'
A=ROOT/'src/main/resources/assets'/NS
D=ROOT/'src/main/resources/data'/NS

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')

TEXTURE=A/'textures/item/portable_sprayers'
SIDES=('north','south','east','west','up','down')
# Four tones per metal: lit edge, face, shaded edge, seam. Brass, andesite alloy and copper are Create's casing colours.
BRASS=('#F7CB6C','#CEA05A','#9E6947','#724731');ANDESITE=('#9AA49D','#828784','#60635F','#505351')
COPPER=('#E3826C','#C26B4C','#9A5038','#78363B');STEEL=('#6C6F73','#484B4F','#303337','#1E2023')
RUBBER=('#3A3C3E','#26282A','#17191A','#17191A');RED=('#D8523E','#B5342A','#8A2420','#5E1A19')
ALLOY=('#CCD9DF','#9CA9AF','#8A979D','#667379')
SILVER,SILVER_SHADE,WHITE,GREEN,YELLOW,BLUE,BLUE_SHADE='#D9DDE0','#A9AEB2','#E8E8E2','#5C9A4A','#D9B032','#5E96B8','#3F6F8E'
SHEET=64
# Slot view of each tool: (scale, offset), fitted so the whole tool fills the slot.
GUI={'fire_extinguisher':(.75,[0,-.8,0]),'flamethrower':(.66,[-2.2,.2,0])}

def sheet(name,metals,sprites,target=TEXTURE):
    """A 64 px sheet for one tool: a 16 px cell per metal lit from the upper left, then the tool's own sprites.
    Returns the pixel origin of every cell."""
    from PIL import Image,ImageDraw
    image=Image.new('RGBA',(SHEET,SHEET),(0,0,0,0));draw=ImageDraw.Draw(image);cells={}
    for index,(metal,(light,base,dark,seam)) in enumerate(metals.items()):
        x,y=index%4*16,index//4*16;cells[metal]=(x,y)
        draw.rectangle((x,y,x+15,y+15),fill=dark);draw.rectangle((x,y,x+14,y+14),fill=light);draw.rectangle((x+1,y+1,x+14,y+14),fill=base)
        for sx,sy,length in ((3,3,3),(10,5,2),(4,11,2),(11,12,3)):
            draw.rectangle((x+sx,y+sy,x+sx+length-1,y+sy),fill=dark);draw.rectangle((x+sx+1,y+sy+1,x+sx+length,y+sy+1),fill=light)
        draw.rectangle((x+13,y+2,x+13,y+3),fill=seam)
    sprites(lambda x0,y0,x1,y1,colour:draw.rectangle((x0,y0,x1,y1),fill=colour))
    target.mkdir(parents=True,exist_ok=True);image.save(target/f'{name}.png')
    return cells

def box(cells,name,start,end,metal,rotation=None,sides=SIDES,flat=(),sprites=None):
    """A box showing its metal at one pixel per model pixel: lit faces take the cell's upper-left corner, shaded faces its
    lower-right. Flat faces show the plain face colour, for caps that overlap; sprites give a face its own area of the sheet."""
    size=[b-a for a,b in zip(start,end)];x,y=cells[metal];faces={}
    for side in sides:
        u,v={'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}[side]
        w,h=min(16,size[u]),min(16,size[v])
        if sprites and side in sprites:area=(sprites[side][0],sprites[side][1],sprites[side][0]+w,sprites[side][1]+h)
        elif side in flat:area=(x+7.25,y+7.25,x+7.75,y+7.75)
        elif side in ('north','west','up'):area=(x,y,x+w,y+h)
        else:area=(x+16-w,y+16-h,x+16,y+16)
        faces[side]={'texture':'#sheet','uv':[round(c*16/SHEET,4) for c in area]}
    element={'name':name,'from':list(start),'to':list(end),'faces':faces}
    if rotation:element['rotation']=rotation
    return element

def octagon(cells,name,axis,centre,apothem,low,high,metal,caps=(),side=None,upright=None):
    """An eight-sided cylinder along Y or Z from two crossed boxes and the same pair turned 45 degrees; only outer faces are drawn."""
    half=round(apothem*.41421356,3);a,b=centre;parts=[]
    for turn in (0,45):
        for wide in (True,False):
            if turn and not wide:continue
            ra,rb=(apothem,half) if wide else (half,apothem)
            if axis=='y':
                start,end=(a-ra,low,b-rb),(a+ra,high,b+rb);outer=('east','west') if wide else ('north','south');origin=[a,(low+high)/2,b]
            else:
                start,end=(a-ra,b-rb,low),(a+ra,b+rb,high);outer=('east','west') if wide else ('up','down');origin=[a,b,(low+high)/2]
            art={face:upright if upright and face in ('up','down') else side for face in outer} if side else None
            for angle in ((turn,-turn) if turn else (0,)):
                parts.append(box(cells,f'{name} {len(parts)+1}',start,end,metal,{'origin':origin,'axis':axis,'angle':angle} if angle else None,outer+tuple(caps),caps,art))
    return parts

def extinguisher(pressed):
    def sprites(fill):
        for x in (0,4):
            fill(x,32,x+2,41,RED[1]);fill(x,34,x+2,34,SILVER);fill(x,35,x+2,35,SILVER_SHADE)
        fill(5,37,5,39,YELLOW);fill(4,39,6,39,YELLOW);fill(5,38,5,38,WHITE)
        fill(8,32,9,33,WHITE);fill(9,32,9,32,GREEN);fill(9,33,9,33,RED[1]);fill(8,33,8,33,RUBBER[2])
        fill(12,32,14,34,BRASS[1]);fill(12,32,14,32,BRASS[0]);fill(13,33,13,33,(0,0,0,0));fill(14,34,14,34,BRASS[2])
    cells=sheet('fire_extinguisher',{'red':RED,'rubber':RUBBER,'brass':BRASS,'steel':ANDESITE},sprites)
    # The top cap closes the ring between the cylinder and the narrower shoulder.
    parts=octagon(cells,'cylinder','y',(8,8),3.5,1,11,'red',caps=('up',),side=(0,32))
    parts[1]['faces']['north']['uv']=[round(c*16/SHEET,4) for c in (4,32,4+2.9,42)]
    parts+=octagon(cells,'shoulder','y',(8,8),2.5,11,12,'red',caps=('up',))
    parts+=octagon(cells,'foot ring','y',(8,8),4,0,1,'rubber',caps=('up','down'))
    parts+=[
        box(cells,'valve neck',(7,12,7),(9,13.5,9),'brass',sides=('north','south','east','west')),
        box(cells,'valve head',(6.5,13.5,6.5),(9.5,15,9.5),'brass'),
        box(cells,'carrying handle',(7.25,13.5,9.5),(8.75,14.25,14.5),'rubber'),
        box(cells,'squeeze lever',(7.25,15,8),(8.75,15.6,14.5),'steel',None if pressed else {'origin':[8,15.3,8],'axis':'x','angle':-22.5}),
        box(cells,'safety pin',(9.5,14.2,7.7),(10.6,14.7,8.3),'brass',sides=('north','south','east','up','down')),
        box(cells,'pin ring',(10.6,12.6,8),(13.6,15.6,8),'brass',sides=('north','south'),sprites={'north':(12,32),'south':(12,32)}),
        box(cells,'pressure gauge',(7,13.2,5.7),(9,15.2,6.5),'steel',sides=('north','east','west','up','down'),sprites={'north':(8,32)}),
        box(cells,'hose outlet',(4.3,13.8,7.5),(6.5,14.8,8.5),'rubber',sides=('north','south','up','down')),
        box(cells,'hose bend',(3.8,10.2,7.5),(4.8,14.3,8.5),'rubber',{'origin':[4.3,14.3,8],'axis':'z','angle':-22.5}),
        box(cells,'hose',(2.3,4,7.5),(3.3,10.6,8.5),'rubber',sides=('north','south','east','west')),
        box(cells,'hose clip',(2.1,6,7.3),(4.6,6.8,8.7),'steel'),
        box(cells,'nozzle',(2.1,2,7.3),(3.5,4,8.7),'steel'),
        box(cells,'nozzle horn',(1.8,1,7),(3.8,2,9),'rubber')]
    return 'fire_extinguisher',parts,(8,14.2,12),True

def flamethrower(pressed):
    def sprites(fill):
        fill(16,32,23,34,ANDESITE[1]);fill(16,32,23,32,ANDESITE[0]);fill(16,34,23,34,ANDESITE[2])
        fill(26,32,28,39,ANDESITE[1]);fill(26,32,26,39,ANDESITE[0]);fill(28,32,28,39,ANDESITE[2])
        for n in range(1,8,3):fill(16+n,33,17+n,33,STEEL[3]);fill(27,32+n,27,33+n,STEEL[3])
        fill(16,36,22,37,STEEL[1]);fill(16,36,22,36,STEEL[0]);fill(30,32,31,38,STEEL[1]);fill(30,32,30,38,STEEL[0])
        for n in (2,4):fill(16+n,36,16+n,37,YELLOW);fill(30,32+n,31,32+n,YELLOW)
        fill(34,32,36,34,STEEL[1]);fill(34,32,36,32,STEEL[0]);fill(35,33,35,33,STEEL[3]);fill(36,33,36,34,STEEL[2])
    cells=sheet('flamethrower',{'brass':BRASS,'andesite':ANDESITE,'copper':COPPER,'steel':STEEL,'rubber':RUBBER},sprites)
    shroud={'east':(16,32),'west':(16,32),'up':(26,32),'down':(26,32)}
    parts=[
        box(cells,'barrel',(7.25,9,-9),(8.75,10.5,1),'steel',sides=('east','west','up','down')),
        box(cells,'heat shroud',(6.75,8.5,-7),(9.25,11,1),'andesite',sprites=shroud),
        box(cells,'burner mouth',(6.5,8.25,-11),(9.5,11.25,-9),'steel',sprites={'north':(34,32)}),
        box(cells,'igniter',(7.5,7.25,-10.5),(8.5,8.25,-7.5),'copper',sides=('north','south','east','west','down')),
        box(cells,'receiver',(6.5,8,1),(9.5,11.5,9),'andesite'),
        box(cells,'valve block',(7,11.5,3),(9,12.5,7),'brass',sides=('north','south','east','west','up')),
        box(cells,'rear cap',(6.75,8.25,9),(9.25,11.25,10),'brass',sides=('east','west','up','down')),
        box(cells,'pistol grip',(7,3,6.5),(9,8,8.5),'rubber',{'origin':[8,8,7.5],'axis':'x','angle':-22.5},sides=('north','south','east','west','down')),
        box(cells,'trigger',(7.6,6,4.6),(8.4,8,5.4),'brass',{'origin':[8,8,5],'axis':'x','angle':-22.5} if pressed else None,sides=('north','south','east','west','down')),
        box(cells,'trigger guard',(7.5,5.4,3.6),(8.5,5.9,6.4),'andesite'),
        box(cells,'trigger guard front',(7.5,5.9,3.6),(8.5,8,4.1),'andesite',sides=('north','south','east','west')),
        box(cells,'fore grip',(7.1,4.5,-3.5),(8.9,8.5,-1.7),'rubber',sides=('north','south','east','west','down')),
        box(cells,'fuel pipe',(7.5,12.5,5),(8.5,13.3,12.5),'copper'),
        box(cells,'fuel pipe drop',(7.5,11.7,11.5),(8.5,12.5,12.5),'copper',sides=('north','south','east','west'))]
    parts+=octagon(cells,'fuel tank','z',(8,9.5),2.25,10,17,'steel',caps=('north','south'),side=(16,36),upright=(30,32))
    if pressed:
        for name,start,end,sides in (('pilot flame',(6.8,7.3,-11.6),(9.2,10.3,-11.6),('north','south')),('pilot flame side',(8,7.3,-12.8),(8,10.3,-10.4),('east','west'))):
            parts.append({'name':name,'from':list(start),'to':list(end),'faces':{side:{'texture':'#flame','uv':[0,0,16,16]} for side in sides}})
    return 'flamethrower',parts,(8,5.5,8),False

def chemical_sprayer(pressed):
    """Built like Create's potato cannon: a pipe barrel with a muzzle ring, a boxed body, a chamber set on its corner under it
    and a rear handle. Here the pipe and chamber are corrosion-resistant alloy and the body carries a hand pump."""
    def sprites(fill):
        def strip(x,y,upright):return lambda along,across,colour:fill(x+across,y+along,x+across,y+along,colour) if upright else fill(x+along,y+across,x+along,y+across,colour)
        for dot,length,rows in ((strip(16,32,False),8,3),(strip(28,32,True),8,3)):
            for along in range(length):
                for across in range(rows):dot(along,across,COPPER[1] if along in (1,length-2) else ALLOY[0] if across==0 else ALLOY[2] if across==rows-1 else ALLOY[1])
        for dot in (strip(16,36,False),strip(32,32,True)):
            for along in range(6):
                for across in range(3):dot(along,across,(BLUE if across<2 else BLUE_SHADE) if along in (2,3) else ALLOY[0] if across==0 else ALLOY[2] if across==2 else ALLOY[1])
        for dot,rows in ((strip(16,40,False),4),(strip(36,32,True),4)):
            for along in range(5):
                for across in range(rows):dot(along,across,COPPER[1] if along in (0,4) else COPPER[2] if across==rows-1 else ANDESITE[0] if across==0 else ANDESITE[1])
        for x,inner in ((24,RUBBER[2]),(24+5,ANDESITE[1])):
            fill(x,40,x+3,43,COPPER[1]);fill(x,40,x+3,40,COPPER[0]);fill(x+3,41,x+3,43,COPPER[2]);fill(x+1,41,x+2,42,inner)
    cells=sheet('chemical_sprayer',{'alloy':ALLOY,'copper':COPPER,'andesite':ANDESITE,'rubber':RUBBER,'brass':BRASS},sprites)
    pipe={'east':(16,32),'west':(16,32),'up':(28,32),'down':(28,32)}
    chamber={'east':(16,36),'west':(16,36),'up':(32,32),'down':(32,32)}
    body={'east':(16,40),'west':(16,40),'up':(36,32),'down':(36,32),'north':(29,40),'south':(29,40)}
    parts=[
        box(cells,'barrel',(6.5,7,-1.5),(9.5,10,6.5),'alloy',sides=('east','west','up','down'),sprites=pipe),
        box(cells,'muzzle ring',(6,6.5,-2.5),(10,10.5,-1.5),'copper',sprites={'north':(24,40)}),
        box(cells,'fan nozzle',(6.5,8,-3.5),(9.5,9,-2.5),'brass',sides=('north','east','west','up','down')),
        box(cells,'collar',(6,6.5,6.5),(10,10.5,7.5),'copper'),
        box(cells,'neck',(6.5,7,7.5),(9.5,10,10.5),'andesite',sides=('east','west','up','down')),
        box(cells,'body',(6,6.5,10.5),(10,10.5,15.5),'andesite',sprites=body),
        box(cells,'tank',(6.5,4.5,9.75),(9.5,7.5,15.75),'alloy',{'origin':[8,6,12.5],'axis':'z','angle':-45},sprites=chamber),
        box(cells,'handle',(7,5,15.5),(9,9.5,17.5),'rubber'),
        box(cells,'squeeze lever',(7.5,10.5,13),(8.5,11,17.5),'andesite',None if pressed else {'origin':[8,10.75,13],'axis':'x','angle':-22.5}),
        box(cells,'pump barrel',(7.25,10.5,10.75),(8.75,12,12.25),'copper',sides=('north','south','east','west','up')),
        box(cells,'pump rod',(7.5,12,11),(8.5,13.5,12),'andesite',sides=('north','south','east','west')),
        box(cells,'pump handle',(6.25,13.5,10.75),(9.75,14.5,12.25),'rubber'),
        box(cells,'hose',(7.5,5.5,7.6),(8.5,6.5,9.75),'rubber',sides=('north','east','west','up','down')),
        box(cells,'hose riser',(7.5,6.5,7.6),(8.5,7,8.6),'rubber',sides=('north','south','east','west'))]
    return 'chemical_sprayer',parts,(8,7.2,16.5),False

# Create's own display of the potato cannon, whose size and build the chemical sprayer shares.
CANNON={'gui':{'rotation':[64,47,-47],'translation':[.25,-.25,0],'scale':[.86]*3},
    'ground':{'rotation':[0,0,90],'translation':[0,-1.3,0],'scale':[.76914]*3},
    'fixed':{'rotation':[0,90,0],'translation':[.5,.5,-1],'scale':[.72]*3},
    'firstperson':{'rotation':[5,5,5],'translation':[.25,5,.75]},'aiming':{'rotation':[0,-15,0],'translation':[0,0,-4]}}

def display(name,grip,upright,aiming):
    """Held with the tip along -Z, as Create's potato cannon is. A hanging arm points -Z at the ground, so third person turns
    the tool a quarter forward; the two guns are aimed with the arm raised while spraying and then keep the cannon's own turn.
    Either way the grip is brought to the hand. Left hands take the same values, which the game mirrors itself."""
    if name=='chemical_sprayer':
        held=CANNON['aiming'] if aiming else {'rotation':[90,0,0],'translation':[0,grip[2]-8,8-grip[1]]}
        return {'gui':CANNON['gui'],'ground':CANNON['ground'],'fixed':CANNON['fixed'],'firstperson_righthand':CANNON['firstperson'],
            'firstperson_lefthand':CANNON['firstperson'],'thirdperson_righthand':held,'thirdperson_lefthand':held}
    scale=.6 if upright else .7
    if aiming:held={'rotation':[0,-15,0],'translation':[0,round(-scale*(grip[1]-8),2),round(-scale*(grip[2]-8),2)],'scale':[scale]*3}
    else:held={'rotation':[90,0,0],'translation':[0,round(scale*(grip[2]-8),2),round(-scale*(grip[1]-8),2)],'scale':[scale]*3}
    first={'rotation':[5,5,5],'translation':[.25,3 if upright else 5,.75],'scale':[.7]*3}
    return {'gui':{'rotation':[30,225 if upright else -135,0],'translation':GUI[name][1],'scale':[GUI[name][0]]*3},
        'ground':{'translation':[0,3,0],'scale':[.4]*3},
        'fixed':{'rotation':[0,180,0],'scale':[.7]*3} if upright else {'rotation':[0,90,0],'scale':[.5]*3},
        'firstperson_righthand':first,'firstperson_lefthand':first,'thirdperson_righthand':held,'thirdperson_lefthand':held}

def models():
    for old in TEXTURE.glob('*.png'):old.unlink()
    for tool in (extinguisher,flamethrower,chemical_sprayer):
        for pressed in (False,True):
            name,parts,grip,upright=tool(pressed)
            sheet_path=NS+':item/portable_sprayers/'+name
            model={'parent':'minecraft:block/block','textures':{'sheet':sheet_path,'particle':sheet_path},'display':display(name,grip,upright,pressed and not upright),'elements':parts}
            if pressed and name=='flamethrower':model['textures']['flame']='minecraft:block/fire_0'
            if not pressed:model['overrides']=[{'predicate':{NS+':spraying':1},'model':NS+':item/'+name+'_active'}]
            write(A/'models/item'/f'{name}{"_active" if pressed else ""}.json',model)

def recipes():
    write(D/'recipe/fire_extinguisher.json',{'type':'minecraft:crafting_shaped','pattern':[' SL','PTR',' S '],
        'key':{'S':{'item':NS+':stainless_steel_sheet'},'L':{'item':'minecraft:lever'},'P':{'item':'create:fluid_pipe'},
            'T':{'item':'create:fluid_tank'},'R':{'item':'minecraft:red_dye'}},'result':{'id':NS+':fire_extinguisher','count':1}})
    for name,centre in [('flamethrower','minecraft:flint_and_steel'),('chemical_sprayer','create:mechanical_pump')]:
        write(D/'recipe'/f'{name}.json',{'type':'create:mechanical_crafting','pattern':[' T T ',' SBS ','PMIMP','  G  ','  R  '],
            'key':{'T':{'item':'create:fluid_tank'},'S':{'item':NS+':stainless_steel_sheet'},'B':{'item':'create:brass_casing'},
                'P':{'item':'create:fluid_pipe'},'M':{'item':'create:precision_mechanism'},'I':{'item':centre},
                'G':{'item':'create:cogwheel'},'R':{'item':NS+':synthetic_rubber'}},
            'result':{'id':NS+':'+name,'count':1},'accept_mirrored':True})
    write(D/'tags/fluid/spray_fuels.json',{'replace':False,'values':[NS+':naphtha','createdieselgenerators:diesel','createdieselgenerators:gasoline',{'id':'createdieselgenerators:ethanol','required':False}]})

def ponder():
    from generate_petrochemical_ponder import Structure,OUT
    s=Structure(9,5)
    for x,kind in ((1,'create:fluid_tank'),(4,NS+':stainless_steel_tank'),(7,'create:fluid_tank')):
        s.block(x,1,2,kind,bottom='true',top='true',shape='window')
    for x in (2,4,6):
        s.block(x,1,6,'minecraft:campfire',facing='north',lit='true',signal_fire='false',waterlogged='false')
    s.block(4,1,4,'create:depot',waterlogged='false')
    s.block(4,3,4,'create:spout')
    s.save('portable_sprayers')
    path=A/'ponder/plant/portable_sprayers.nbt';path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes((OUT/'portable_sprayers.nbt').read_bytes());(OUT/'portable_sprayers.nbt').unlink()

def acid_burn():
    """The icon and names of the burn an acid spray leaves: three drops eating into the skin."""
    from PIL import Image
    image=Image.new('RGBA',(18,18),(0,0,0,0));p=image.load()
    for cx,cy,r in ((5,6,2.6),(12,5,2.2),(9,12,3.2)):
        for y in range(18):
            for x in range(18):
                d=((x-cx)**2+(y-cy)**2)**.5
                if d<=r:p[x,y]=(206,214,92,255) if d<r-1.2 else (150,160,52,255)
    for x,y in ((9,15),(10,16),(4,9),(13,8)):p[x,y]=(110,118,40,255)
    path=A/'textures/mob_effect';path.mkdir(parents=True,exist_ok=True);image.save(path/'acid_burn.png')
    for language,name in (('en_us','Acid Burn'),('ja_jp','薬傷'),('zh_cn','酸灼伤')):
        lang=A/'lang'/f'{language}.json';strings=json.loads(lang.read_text())
        strings['effect.create_refinery_n_reactors.acid_burn']=name
        lang.write_text(json.dumps(strings,ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':
    models();recipes();ponder();acid_burn()
    print('Generated three portable spray tools, active models, recipes, fuel tag and Ponder structure')
