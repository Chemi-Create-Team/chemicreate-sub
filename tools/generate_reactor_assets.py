"""Only the new reaction-plant parts: sliced pressure vessels, coils and flash-column internals."""
from pathlib import Path
import json, math
from PIL import Image
from minecraft_style import PALETTE, METAL_REFLECTION, grain
ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/create_refinery_n_reactors'
D=ROOT/'src/main/resources/data/create_refinery_n_reactors'
NS='create_refinery_n_reactors:'
SHAPES={}
KINDS=('preheater',)

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def textures():
    for name,alloy in {'steel':'stainless','frame':'cast_iron','copper':'copper','brass':'brass','mesh':'stainless'}.items():
        im=Image.new('RGBA',(16,16));p=im.load();base=PALETTE[alloy];shadow,high=METAL_REFLECTION[alloy]
        for y in range(16):
            for x in range(16):
                band=(2,12,26,36,23,10,1,-10,-18,-10,0,10,19,9,0,-22)[x]
                v=band+grain('reactor_'+alloy,x,y,1.6 if name=='frame' else .85)+(-2,1,0,2)[y%4]
                if y in (0,15):v=-36
                if y==1:v=48
                if name in ('steel','frame') and (x,y) in ((3,3),(11,11)):v=55
                if name in ('steel','frame') and (x,y) in ((3,4),(12,11)):v=-42
                if name=='mesh':v=35 if x%4==0 else -44 if x%4==2 else 0
                target=high if v>=0 else shadow
                p[x,y]=(*[round(c+(t-c)*min(abs(v)/64,1)) for c,t in zip(base,target)],255)
        dest=A/'textures/block/reactor'/f'{name}.png';dest.parent.mkdir(parents=True,exist_ok=True);im.save(dest)
    im=Image.new('RGBA',(16,16),(221,217,187,255));p=im.load()
    for y in range(16):
        for x in range(16):
            r=math.hypot(x-7.5,y-7.5)
            if r>7:p[x,y]=(61,70,75,255)
            elif r>6:p[x,y]=(216,173,80,255)
    for a in range(-120,121,40):
        x=round(7.5+5*math.sin(math.radians(a)));y=round(7.5-5*math.cos(math.radians(a)));p[x,y]=(45,52,55,255)
    im.save(A/'textures/block/reactor/dial.png')
    for name,color in [('lamp',(94,107,99,255)),('lamp_on',(211,226,146,255))]:
        im=Image.new('RGBA',(16,16),color);im.save(A/'textures/block/reactor'/f'{name}.png')

def element(b,t):
    x,y,z,X,Y,Z=b
    uv={'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],'west':[z,16-Y,Z,16-y],'east':[16-Z,16-Y,16-z,16-y],'up':[x,z,X,Z],'down':[x,16-Z,X,16-z]}
    return {'from':b[:3],'to':b[3:],'faces':{f:{'uv':[v%16 if v<0 or v>16 else v for v in u],'texture':'#'+t} for f,u in uv.items()}}

def model(name,parts):
    write(A/'models/block/reactor'/f'{name}.json',{'parent':'minecraft:block/block','textures':{**{t:NS+'block/reactor/'+t for _,t in parts},'particle':NS+'block/reactor/steel'},'elements':[element(b,t) for b,t in parts]})

def slice_parts(parts,c):
    cx,cz=c%3,c//3;out=[]
    for b,t in parts:
        n=[max(b[0],cx*16)-cx*16,b[1],max(b[2],cz*16)-cz*16,min(b[3],(cx+1)*16)-cx*16,b[4],min(b[5],(cz+1)*16)-cz*16]
        if all(n[i]<n[i+3] for i in range(3)):out.append((n,t))
    return out

def octagon(y,Y,tex,inset=2,hollow=True):
    a,b=inset,48-inset
    def inside(x,z,a,b,cut):
        return a<=x<b and a<=z<b and x+z>=2*a+cut and x+z<=2*b-cut-2 and x-z<=b-a-cut-1 and z-x<=b-a-cut-1
    rows=[]
    for z in range(a,b):
        xs=[x for x in range(a,b) if inside(x,z,a,b,10) and (not hollow or not inside(x,z,a+3,b-3,8))]
        spans=[]
        for x in xs:
            if spans and spans[-1][1]==x:spans[-1][1]=x+1
            else:spans.append([x,x+1])
        rows.append((z,spans))
    boxes=[]
    for z,spans in rows:
        for x,X in spans:
            found=next((box for box,t in boxes if box[0]==x and box[3]==X and box[5]==z),None)
            if found is not None:found[5]=z+1
            else:boxes.append(([x,y,z,X,Y,z+1],tex))
    return boxes

def ring(y,Y,tex,inset=2):return octagon(y,Y,tex,inset,True)

def assembly(kind,role):
    if role=='core':
        if kind=='preheater':return [([19,0,19,29,16,29],'copper'),([17,3,17,31,5,31],'brass'),([17,11,17,31,13,31],'brass')]
        if kind=='separator':return [([17,0,17,31,2,31],'mesh'),([21,2,21,27,16,27],'steel'),([16,8,16,32,10,32],'mesh')]
        return [([18,0,18,30,16,30],'mesh'),([16,0,16,32,2,32],'brass'),([16,14,16,32,16,32],'brass')]
    inset=7 if kind=='separator' else 2
    if role=='head':
        return octagon(0,3,'steel',inset,False)+octagon(3,7,'steel',inset+4,False)+octagon(7,10,'steel',inset+8,False)+[([20,10,20,28,13,28],'brass'),([18,12,22,30,14,26],'steel'),([22,12,18,26,14,30],'steel')]
    parts=ring(1,15,'copper' if kind=='preheater' else 'steel',inset)+ring(0,2,'frame',inset)+ring(14,16,'brass' if kind=='vessel' else 'steel',inset)
    if kind=='preheater':
        for yy in (3,7,11):parts+=ring(yy,yy+2,'copper',0)
        parts += [([0,1,20,3,15,28],'brass'),([45,1,20,48,15,28],'brass')]
    elif kind=='vessel':
        parts += ring(8,10,'copper',0)+[([19,3,0,29,12,2],'frame'),([20,4,0,28,11,1],'mesh'),([0,2,21,2,14,27],'brass')]
    else:
        parts += [([10,3,9,38,5,12],'brass'),([10,11,9,38,13,12],'brass'),([12,3,8,14,13,10],'steel'),([34,3,8,36,13,10],'steel')]
    return parts

BASE=[([0,0,0,16,2,16],'frame'),([2,2,2,14,10,14],'frame'),([3,10,3,13,15,13],'steel'),([0,4,4,2,12,12],'brass'),([14,4,4,16,12,12],'steel'),([4,4,0,12,12,2],'steel'),([4,4,14,12,12,16],'steel'),([5,9,1,11,15,2],'dial'),([12,11,2,14,13,3],'lamp')]

def main():
    textures()
    for kind in KINDS:
        trim={'preheater':[([3,13,3,5,16,13],'copper'),([11,13,3,13,16,13],'copper')],
              'vessel':[([3,13,3,13,16,5],'brass'),([3,13,11,13,16,13],'brass')],
              'separator':[([4,13,4,12,16,12],'mesh')]}
        for lit in (False,True):model(kind+('_running' if lit else ''),[(b,'lamp_on' if t=='lamp' and lit else t) for b,t in BASE+trim[kind]])
        variants={}
        for axis,rot in [('z',0),('x',90),('y',0)]:
            for lit in (False,True):variants[f'axis={axis},lit={str(lit).lower()}']={'model':NS+'block/reactor/'+kind+('_running' if lit else ''),'y':rot}
        write(A/'blockstates'/f'reactor_{kind}.json',{'variants':variants})
        write(A/'models/item'/f'reactor_{kind}.json',{'parent':NS+'block/reactor/'+kind})
    SHAPES['machine/z']=[b for b,t in BASE];SHAPES['machine/y']=SHAPES['machine/z']
    SHAPES['machine/x']=[[16-b[5],b[1],b[0],16-b[2],b[4],b[3]] for b,t in BASE]
    write(A/'reactor_shapes.json',SHAPES)
    model('needle',[([-0.5,-3,-.1,.5,1,.1],'frame')])
    names={
    'reactor_preheater':('Reaction-feed preheater drive','反応原料予熱器の駆動部'),
}
    for id in names:
        write(D/'loot_table/blocks'/f'{id}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+id}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        machine=id in [f'reactor_{k}' for k in KINDS]
        ingredient='create:copper_sheet' if 'preheater' in id else 'minecraft:iron_bars' if 'separator' in id else 'create:iron_sheet'
        recipe={'type':'minecraft:crafting_shaped','pattern':[' P ','PCP',' P '],'key':{'P':{'item':ingredient},'C':{'item':'create:precision_mechanism' if machine else 'minecraft:iron_ingot'}},'result':{'id':NS+id,'count':1}}
        write(D/'recipe'/f'{id}.json',recipe)
    immovable=ROOT/'src/main/resources/data/create/tags/block/non_movable.json'
    data=json.loads(immovable.read_text()) if immovable.exists() else {'replace':False,'values':[]}
    data['values']=list(dict.fromkeys(data['values']+[NS+n for n in names]));write(immovable,data)
    mine=ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
    data=json.loads(mine.read_text());data['values']=list(dict.fromkeys(data['values']+[NS+n for n in names]));write(mine,data)
    for lang,index in [('en_us',0),('ja_jp',1)]:
        path=A/'lang'/f'{lang}.json';data=json.loads(path.read_text())
        for id,label in names.items():data['block.create_refinery_n_reactors.'+id]=label[index]
        en={'structure':'Pressure envelope: %s / %s sections; %s mB per buffer','fault':'Check component at %s, %s, %s','performance':'Speed factor: %s; parallel capacity: up to %s batches; stress: %s SU/RPM','ports':'Feed: %s; products: %s; catalyst service: underneath','batch':'Operation: %s (%s batches); %s / %s ticks','no_batch':'Waiting for feed','none':'None','catalyst':'Catalyst: %s','input':'Feed: %s — %s / %s mB','output':'Product: %s — %s / %s mB'}
        ja={'structure':'圧力容器：%s / %s 段、各バッファ %s mB','fault':'確認する部品の位置：%s、%s、%s','performance':'速度倍率：%s、並列処理能力：最大%sバッチ、応力：%s SU/RPM','ports':'原料入口：%s、生成物出口：%s、触媒の搬入出：下面','batch':'工程：%s（%sバッチ）、進捗 %s / %s tick','no_batch':'原料待ち','none':'なし','catalyst':'触媒：%s','input':'原料：%s — %s / %s mB','output':'生成物：%s — %s / %s mB'}
        for key,value in (en if index==0 else ja).items():data['reactor.create_refinery_n_reactors.'+key]=value
        statuses={'structure':('Assemble the pressure envelope','圧力容器を組み立ててください'),'unloaded':('A required chunk is unloaded','必要なチャンクが読み込まれていません'),'support':('Missing support leg','支持脚が不足しています'),'jacket':('Incomplete pressure jacket','ジャケットが閉じていません'),'head':('Close the vessel with head segments','鏡板で上部を密閉してください'),'core':('Missing functional internals (separator: at least 2 trays)','内部部品が不足しています（分離塔は棚段2段以上）'),'height':('Too tall: at most 4 sections','高さ超過：内部部品は4段までです'),'ready':('Pressure envelope complete','圧力容器の組み立て完了'),'idle':('Ready; waiting for feed','準備完了・原料待ち'),'speed':('Supply at least 32 RPM','32 RPM以上の回転動力が必要です'),'heat':('A fueled Blaze Burner is required underneath','下面のブレイズバーナーに燃料を供給してください'),'materials':('Check feed, catalyst and product buffer space','原料・触媒・生成物バッファの空きを確認してください'),'redstone':('Stopped by redstone','レッドストーン信号で停止中'),'running':('Operating','稼働中')}
        for key,values in statuses.items():data['reactor.create_refinery_n_reactors.status.'+key]=values[index]
        operations={'heat_synthesis':('Preheat compressed synthesis feed','圧縮合成原料の予熱'),'oxidize_ammonia':('Catalytic ammonia oxidation','触媒によるアンモニア酸化'),'oxidize_nitric_oxide':('Nitric oxide oxidation','一酸化窒素の酸化'),'crack_naphtha':('Catalytic naphtha cracking','触媒によるナフサ分解'),'polymerize_ethylene':('Ethylene polymerization','エチレンの重合'),'polymerize_propylene':('Propylene polymerization','プロピレンの重合'),'separate_ammonia':('Separate ammonia and recycle gas','アンモニアの分離と未反応ガスの回収'),'separate_nitric_oxide':('Separate nitric oxide and water','一酸化窒素と水の分離'),'separate_olefins':('Separate ethylene and propylene','エチレンとプロピレンの分離'),'distill_crude':('Distill heated crude oil into fractions','加熱した原油の蒸留'),'heat_naphtha':('Preheat naphtha','ナフサの予熱'),'heat_ethylene':('Preheat ethylene','エチレンの予熱'),'heat_propylene':('Preheat propylene','プロピレンの予熱')}
        for key,values in operations.items():data['process.create_refinery_n_reactors.recipe.'+key]=values[index]
        for key,values in {'west':('West','西'),'east':('East','東'),'north':('North','北'),'south':('South','南')}.items():data['direction.create_refinery_n_reactors.'+key]=values[index]
        write(path,data)
    print('Generated reaction-plant parts, 16px textures, collision geometry, recipes and translations')

if __name__=='__main__':main()
