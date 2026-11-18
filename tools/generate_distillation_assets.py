"""Square stacked fractionator cells; existing machine artwork is left intact."""
from pathlib import Path
from PIL import Image
import json,math
from minecraft_style import grain
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/create_refinery_n_reactors';D=R/'src/main/resources/data/create_refinery_n_reactors';NS='create_refinery_n_reactors:'
SHAPES={}
SECTIONS=('single','base','service','body','top')

def write(p,data):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def textures():
    for name,base in {'steel':(101,111,117),'frame':(65,75,82),'port':(121,132,138)}.items():
        im=Image.new('RGBA',(16,16));p=im.load()
        for y in range(16):
            for x in range(16):
                v=grain('distillation_'+name,x,y,1.4)
                if name in ('steel','frame'):
                    v+=(19,8,-3,-9,2,8,4,-4)[x%8]
                if name=='frame' and x in (0,15):v+=24 if x==0 else -22
                color=tuple(max(0,min(255,c+v)) for c in base)
                if name=='port':
                    radius=math.hypot(x-7.5,y-7.5);color=(42,47,50) if radius<4 else (172,181,185) if radius<5 else color
                p[x,y]=(*color,255)
        dest=A/'textures/block/fractionating_column'/f'{name}.png';dest.parent.mkdir(parents=True,exist_ok=True);im.save(dest)
    for name,base in {'plate':(90,101,109),'edge':(145,155,162),'deck':(69,79,86)}.items():
        im=Image.new('RGBA',(16,16));p=im.load()
        for y in range(16):
            for x in range(16):
                v=grain('column_'+name,x,y,1.3)
                if name=='deck':v+=-23 if x%4 in (1,2) and y%4 in (1,2) else 8
                p[x,y]=tuple(max(0,min(255,c+v)) for c in base)+(255,)
        im.save(A/'textures/block/fractionating_column'/f'{name}.png')
    for i,color in enumerate(((91,80,66),(155,127,81),(176,161,114)),1):
        im=Image.new('RGBA',(16,16),(*color,255));p=im.load()
        for y in range(16):
            for x in range(16):
                if x in (0,15) or y in (0,15):p[x,y]=(70,78,81,255)
                if y in (5,9) and 3<=x<3+i*2:p[x,y]=(49,55,58,255)
        im.save(A/'textures/block/fractionating_column'/f'cut_{i}.png')

def element(b,t):
    x,y,z,X,Y,Z=b
    uv={'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],'west':[z,16-Y,Z,16-y],'east':[16-Z,16-Y,16-z,16-y],'up':[x,z,X,Z],'down':[x,16-Z,X,16-z]}
    return {'from':b[:3],'to':b[3:],'faces':{f:{'uv':[min(16,max(0,v)) for v in u],'texture':'#'+t} for f,u in uv.items()}}
# Each part grows a hair more than the one before it, so coplanar faces of overlapping parts never fight over the same depth.
def grow(b,e):return [b[0]-e,b[1]-e,b[2]-e,b[3]+e,b[4]+e,b[5]+e]
def model(name,parts):
    write(A/'models/block/fractionating_column'/f'{name}.json',{'parent':'minecraft:block/block','textures':{**{t:NS+'block/fractionating_column/'+t for b,t in parts},'particle':NS+'block/fractionating_column/steel'},'elements':[element(grow(b,.001*i),t) for i,(b,t) in enumerate(parts)]})

def port(side,label=None):
    boxes=[([5,4,15,11,12,16],'steel'),([4,5,15,12,11,16],'steel'),([6,6,14,10,10,16],'port')]
    if label:boxes.append(([5,12,15,11,15,16],label))
    turn={'south':0,'west':1,'north':2,'east':3}[side]
    for _ in range(turn):boxes=[([16-b[5],b[1],b[0],16-b[2],b[4],b[3]],t) for b,t in boxes]
    return boxes

def assembly(w,section):
    W=w*16;base=section in ('single','base');top=section in ('single','top')
    parts=[([3,6 if base else 0,3,W-3,12 if top else 16,W-3],'plate')]
    def ring(y,Y,inset,thick,material):
        parts.extend([([inset,y,inset,W-inset,Y,inset+thick],material),([inset,y,W-inset-thick,W-inset,Y,W-inset],material),
                      ([inset,y,inset+thick,inset+thick,Y,W-inset-thick],material),([W-inset-thick,y,inset+thick,W-inset,Y,W-inset-thick],material)])
    # Only the four outside corners are columns; joined cells have no frames or caps.
    for xx in (0,W-3):
        for zz in (0,W-3):
            parts.extend([([xx,0,zz,xx+3,16,zz+3],'frame'),([xx,0,zz,xx+1,16,zz+1],'edge')])
    if w>1:
        parts.extend([([W/2-1,0,0,W/2+1,13,3],'frame'),([W/2-1,0,W-3,W/2+1,13,W],'frame'),
                      ([W/2-1,0,0,W/2,13,1],'steel')])
    if base:
        ring(0,3,0,3,'frame');ring(3,5,2,2,'steel')
    if not top:
        ring(13,16,0,2,'frame');ring(13,14,0,1,'steel')
    if top:
        parts.extend([([3,11,3,W-3,13,W-3],'steel'),([4,13,4,W-4,14,W-4],'deck'),
                      ([W/2-4,14,W/2-4,W/2+4,15,W/2+4],'frame'),([W/2-3,15,W/2-3,W/2+3,16,W/2+3],'steel')])
        ring(15,16,0,1,'edge')
        for a in range(8,W,16):
            parts.extend([([a-1,12,0,a,16,1],'frame'),([a-1,12,W-1,a,16,W],'frame'),
                          ([0,12,a-1,1,16,a],'frame'),([W-1,12,a-1,W,16,a],'frame')])
    if not base:
        parts.extend([([0,0,W-11,1,16,W-10],'edge'),([0,0,W-5,1,16,W-4],'edge')])
        for yy in (2,6,10,14):parts.append(([0,yy,W-11,1,yy+1,W-4],'steel'))
    if section=='service' or section=='body' and w>1:
        cx=W*.75 if w>1 else W/2
        parts.extend([([cx-4,4,2,cx+4,13,3],'frame'),([cx-5,5,2,cx+5,12,3],'frame'),
                      ([cx-3,5,1,cx+3,12,2],'steel'),([cx-4,6,1,cx+4,11,2],'steel'),
                      ([cx-2,8,0,cx+2,9,1],'edge')])
    return parts

def cell(w,x,z,section):
    result=[]
    for b,t in assembly(w,section):
        clipped=[max(b[0],x*16),b[1],max(b[2],z*16),min(b[3],x*16+16),b[4],min(b[5],z*16+16)]
        if clipped[0]>=clipped[3] or clipped[2]>=clipped[5]:continue
        local=[clipped[0]-x*16,b[1],clipped[2]-z*16,clipped[3]-x*16,b[4],clipped[5]-z*16]
        result.append((local,t))
    return result

def main():
    textures();multipart=[]
    for w in range(1,4):
        for c in range(9):
            x=min(c%3,w-1);z=min(c//3,w-1)
            for section in SECTIONS:
                key=f'{w}_{z*3+x}_{section}'
                if key not in SHAPES:
                    parts=cell(w,x,z,section);model(key,parts);SHAPES[key]=[b for b,t in parts]
                multipart.append({'when':{'width':w,'cell':c,'section':section},'apply':{'model':NS+'block/fractionating_column/'+key}})
    for cut in range(1,4):
        model('outlet_'+str(cut),port('north','cut_'+str(cut)))
        multipart.append({'when':{'cut':cut},'apply':{'model':NS+'block/fractionating_column/outlet_'+str(cut)}})
    for inlet in range(1,2):
        model('inlet_'+str(inlet),port('east','cut_'+str(inlet)))
        multipart.append({'when':{'inlet':inlet},'apply':{'model':NS+'block/fractionating_column/inlet_'+str(inlet)}})
    write(A/'blockstates/fractionating_column.json',{'multipart':multipart})
    write(A/'models/item/fractionating_column.json',{'parent':NS+'block/fractionating_column/1_0_single'})
    SHAPES['outlet']=[b for b,t in port('north','cut_1')]
    SHAPES['inlet']=[b for b,t in port('east','cut_1')]
    write(A/'fractionating_column_shapes.json',SHAPES)
    ids=('fractionating_column',)
    for name in ids:write(D/'loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for path in (R/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json',R/'src/main/resources/data/create/tags/block/non_movable.json'):
        data=json.loads(path.read_text()) if path.exists() else {'replace':False,'values':[]};data['values']=list(dict.fromkeys(data['values']+[NS+n for n in ids]));write(path,data)
    en={'size':'2×2 tower: %s×%s×%s; selected feed needs %s layers','performance':'Feed: up to %s mB air per %s ticks','progress':'Separation %s / %s ticks','ports':'East face: layer 3 compressed cooled air in. North face: layer 1 liquid oxygen out, layer 10 crude argon out, layer 14 nitrogen out','buffer':'%s: %s / %s mB','draw':'Layer %s outlet: %s','inlet':'Layer %s inlet: %s'}
    ja={'size':'2×2専用 %s×%s×%s・選択原料は%s段必要','performance':'原料：空気 最大%s mB / %s tick','progress':'分離 %s / %s tick','ports':'東面：3段目 圧縮・冷却した空気の搬入。北面：1段目 液化酸素、10段目 粗アルゴン、14段目 窒素の搬出','buffer':'%s：%s / %s mB','draw':'%s段目の搬出口：%s','inlet':'%s段目の搬入口：%s'}
    statuses={'width':('Use a complete 2×2 cross-section; other widths cannot operate','断面を2×2にしてください。ほかの幅では運転できません'),'height':('Too short for the selected feed; see its required height','選択原料に必要な高さが足りません'),'structure':('Incomplete column cross-section','塔の断面が完成していません'),'overhang':('Remove irregular extensions; leave a gap between independent columns','断面外の部品を取り除いてください。別の塔とは間隔を空けます'),'unloaded':('A required neighbouring chunk is unloaded','必要な周辺チャンクが読み込まれていません'),'idle':('Column ready','蒸留塔の準備完了'),'redstone':('Stopped by redstone','レッドストーン信号で停止中'),'running':('Separating air','空気を分離中'),'products_full':('An outlet buffer is full; drain every product','搬出側のバッファが満杯です。各生成物を搬出してください'),'feed':('Supply compatible feed to the east inlet; air needs 14 layers','東面へ原料を供給してください。空気は14段必要です')}
    for lang,index in [('en_us',0),('ja_jp',1)]:
        path=A/'lang'/f'{lang}.json';data=json.loads(path.read_text())
        data['block.create_refinery_n_reactors.fractionating_column']=('Fractionation Tower','分留塔')[index]
        for k in [k for k in data if k.startswith('distillation.create_refinery_n_reactors.')]:del data[k]
        for k,v in (en if index==0 else ja).items():data['distillation.create_refinery_n_reactors.'+k]=v
        for k,v in statuses.items():data['distillation.create_refinery_n_reactors.status.'+k]=v[index]
        write(path,data)
    print('Generated square column assets, 16px dark steel textures, collision shapes, recipe and bilingual text')
if __name__=='__main__':main()
