#!/usr/bin/env python3
"""Native block models, contracts and Ponder structures for dispatch and the horizontal tank boiler."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from generate_petrochemical_ponder import Structure, OUT
from generate_horizontal_tank import SHAPES, TURNS

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
MOD = 'create_refinery_n_reactors'
A = RES / 'assets' / MOD
D = RES / 'data' / MOD
NS = MOD + ':'

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')

def cube(lo, hi, texture, uv=None):
    return {'from': lo, 'to': hi, 'faces': {side: {'texture': texture, 'uv': uv or [0, 0, 16, 16]} for side in ('up','down','north','south','east','west')}}

# Shared Create steel and copper are intentional common materials, not duplicated images.
textures = {'steel': 'create:block/industrial_iron_block', 'copper': 'create:block/copper_casing', 'paper': NS+'block/industrial_dispatch/paper', 'particle': 'create:block/industrial_iron_block'}
for pressed in (False, True):
    parts = [cube([1,1,13],[15,15,16],'#steel'), cube([3,3,12.5],[13,14,13],'#paper'), cube([6,1,10],[10,3,14],'#copper'), cube([7,3 if pressed else 5,10],[9,5 if pressed else 7,13],'#steel')]
    write(A / f'models/block/industrial_dispatch_{"stamped" if pressed else "idle"}.json', {'parent':'minecraft:block/block','textures':textures,'elements':parts})
variants = {}
for facing, rotation in [('north',0),('east',90),('south',180),('west',270)]:
    for pressed in (False,True):
        variants[f'facing={facing},triggered={str(pressed).lower()}'] = {'model':NS+f'block/industrial_dispatch_{"stamped" if pressed else "idle"}', 'y':rotation}
write(A/'blockstates/industrial_dispatch_board.json', {'variants':variants})
write(A/'models/item/industrial_dispatch_board.json', {'parent':NS+'block/industrial_dispatch_idle'})
im=Image.new('RGBA',(16,16),(184,178,151,255));p=ImageDraw.Draw(im)
p.rectangle((0,0,15,15),outline=(83,81,66));p.line((1,1,14,1),fill=(226,219,189));p.rectangle((3,3,11,4),fill=(77,91,87))
for y,end in [(7,12),(9,10),(11,12),(13,7)]:p.line((3,y,end,y),fill=(111,111,92))
path=A/'textures/block/industrial_dispatch/paper.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)

for name in ('industrial_dispatch_board',):
    write(D/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(D/'recipe/industrial_dispatch_board.json',{'type':'minecraft:crafting_shaped','pattern':['PPP','BCB',' I '],'key':{'P':{'item':'minecraft:paper'},'B':{'item':'create:brass_sheet'},'C':{'item':'create:precision_mechanism'},'I':{'item':'create:iron_sheet'}},'result':{'id':NS+'industrial_dispatch_board'}})
orders=[('fertilizer','ammonium_nitrate',128,4),('polymer','polyethylene',128,4),('rubber','synthetic_rubber',64,3),('construction','industrial_concrete',128,2),('alloy','stainless_steel_ingot',64,5)]
for name,item,count,reward in orders:
    write(D/f'recipe/industrial_delivery/{name}.json',{'type':NS+'industrial_delivery','ingredients':[{'item':NS+item}]*count,'results':[{'id':'minecraft:emerald','count':reward}],'processing_time':200})
write(D/'recipe/industrial_delivery/fuel.json',{'type':NS+'industrial_delivery','ingredients':[{'type':'neoforge:single','fluid':'createdieselgenerators:diesel','amount':8000}],'results':[{'id':'minecraft:emerald','count':4}],'processing_time':200})

texts={
'block.create_refinery_n_reactors.industrial_dispatch_board':('Industrial Dispatch Board','工業製品の出荷票'),
'industrial_dispatch.vault':('Attach the board to a loaded Create item vault','Createのアイテム保管庫の側面に取り付けてください'),
'industrial_dispatch.orders':('No valid delivery orders','有効な注文がありません'),
'industrial_dispatch.supplies':('Waiting for the ordered cargo','注文された製品の到着待ち'),
'industrial_dispatch.output':('Collect the payment to continue','報酬を搬出すると再開します'),
'industrial_dispatch.running':('Checking and dispatching the cargo','検品・出荷中'),
'industrial_dispatch.delivered':('Shipment complete','出荷完了'),
'industrial_dispatch.paused':('Paused by redstone','レッドストーンで停止中'),
'industrial_dispatch.order_items':('Order: %s × %s','注文: %s個 × %s'),
'industrial_dispatch.order_fluid':('Order: %s mB of %s','注文: %s mBの%s'),
'industrial_dispatch.completed':('Completed shipments: %s','累計出荷: %s件'),
'jei.create_refinery_n_reactors.helium_refrigeration':('Cryogenic Liquefaction','極低温液化'),
'jei.create_refinery_n_reactors.note.helium_refrigeration':('Requires shaft work and circulating water; drain both outputs','回転動力と循環水が必要・液体と温排水を両方搬出'),
'jei.create_refinery_n_reactors.industrial_delivery':('Industrial deliveries','工業製品の出荷'),
'jei.create_refinery_n_reactors.note.industrial_delivery':('Check the current order; %s ticks per shipment','現在の注文を確認・1件%s tick'),
'display.create_refinery_n_reactors.dispatch_completed':('Completed shipments','累計出荷件数'),
'display.create_refinery_n_reactors.dispatch_order':('Current delivery order','現在の出荷注文'),
'display.create_refinery_n_reactors.cold_box_kelvin':('Cold head temperature (K)','冷凍ヘッド温度 (K)'),
'display.create_refinery_n_reactors.cold_box_power':('Refrigerator shaft power (kW)','冷凍機の軸動力 (kW)'),
'display.create_refinery_n_reactors.boiler_heat':('Tank boiler heat (kW)','タンクボイラー伝熱量 (kW)'),
'display.create_refinery_n_reactors.boiler_outlet':('Tank boiler steam (°C)','タンクボイラー蒸気温度 (°C)'),
}
for key,en,ja in [('structure','The tank has not formed or is not loaded','タンクが形成されていないか、読み込まれていません'),('heat','Heat the bottom with lit Blaze Burners','底面を点火したブレイズバーナーで加熱してください'),('water','Fill it with water to boil','沸かす水を入れてください'),('engines','Steam engines attached: it works as Create\'s boiler','蒸気エンジンが付いているので、Createのボイラーとして働きます'),('output','Steam outlet full: connect a pump above this section','蒸気出口が満杯: この区間の上面へポンプを接続してください')]:texts['tubular_boiler.'+key]=(en,ja)
texts['tubular_boiler.outlet']=('Steam: %s °C / %s bar','蒸気: %s °C / %s bar')
texts['tubular_boiler.contents']=('Steam: %s mB / heat: %s kW','蒸気: %s mB / 伝熱量: %s kW')

lessons=[]
s=Structure(9,5)
s.block(4,1,4,'create:item_vault',axis='x',large='false')
s.block(4,1,3,NS+'industrial_dispatch_board',facing='north',triggered='false')
s.block(3,1,4,'create:andesite_funnel',facing='west',extracting='false')
s.block(4,2,4,'create:packager',facing='north',powered='false',linked='false')
s.save('industrial_dispatch');dest=A/'ponder/plant/industrial_dispatch.nbt';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((OUT/'industrial_dispatch.nbt').read_bytes());(OUT/'industrial_dispatch.nbt').unlink()
lessons.append({'id':'industrial_dispatch','size':9,'height':5,'components':['industrial_dispatch_board'],'steps':[{'target':[4,1,3],'key':'step_1'},{'target':[3,1,4],'key':'step_2','item':NS+'polyethylene'},{'target':[4,1,3],'key':'step_3'},{'target':[4,2,4],'key':'step_4'}]})
for width in (1,2,3):
    ident='tubular_boiler'+(f'_{width}x{width}' if width>1 else '')
    s=Structure(9,6)
    for x in range(2,5):
        for z in range(3,3+width):
            s.block(x,1,z,'create:blaze_burner',blaze='seething')
            for y in range(2,2+width):
                s.block(x,y,z,NS+'horizontal_fluid_tank',axis='x',bottom=str(x==2).lower(),top=str(x==4).lower(),shape='plain')
    s.block(1,2,3,'create:mechanical_pump',facing='east',waterlogged='false')
    s.block(5,2,3,'create:mechanical_pump',facing='east',waterlogged='false')
    s.save(ident);(A/f'ponder/plant/{ident}.nbt').write_bytes((OUT/f'{ident}.nbt').read_bytes());(OUT/f'{ident}.nbt').unlink()
    lessons.append({'id':ident,'size':9,'height':6,'components':['horizontal_fluid_tank'],'steps':[{'target':[3,2,3],'key':'step_1'},{'target':[1,2,3],'key':'step_2'},{'target':[3,1,3],'key':'step_3'},{'target':[5,2,3],'key':'step_4'}]})
for ident,title,ja,steps in [
 ('industrial_dispatch','Deliver industrial products','工業製品を出荷する',[
 ('Mount the dispatch board on a Create item vault. Right-click to choose the next order; sneak-click for the previous one. Read the goggles or a Display Link.','Createのアイテム保管庫へ出荷票を取り付けます。右クリックで次の注文、スニーク右クリックで前の注文。ゴーグルやDisplay Linkで内容を確認します。'),
 ('Funnels, trains and unpackagers fill the vault normally. Fluid orders receive bulk fuel through the board\'s standard pipe connection.','ファンネル・列車・開梱機から通常どおり保管庫へ納品します。液体の注文は、出荷票へ通常パイプで燃料を供給します。'),
 ('Only a complete cargo is shipped. Extract the emerald payment from the board with a funnel; a full payment slot pauses shipments without consuming cargo.','全量が揃ってから出荷します。報酬のエメラルドは出荷票からファンネルで搬出。報酬欄が満杯なら製品を消費せず停止します。'),
 ('Keep supplies coming by train or package. Orders advance after dispatch; redstone pauses them.','列車やパッケージで補給を続けます。出荷後に次の注文へ進み、赤石で停止できます。')]),
 ('tubular_boiler','Boil steam in a horizontal tank','横型タンクで蒸気を作る',[
 ('Lay a horizontal tank with a 1×1 to 3×3 section, any length, and put Blaze Burners under it.','横型タンクを断面1×1～3×3、好きな長さで置き、下にブレイズバーナーを並べます。'),
 ('Feed water at an end or side. The last section of each bottom row boils it with the heat of the burners under its row.','端面か側面から給水します。下段の各列の最後の区間が、その列のバーナーの熱で水を沸かします。'),
 ('Lit burners give 350 °C steam; superheated burners give 650 °C steam at 20 bar.','加熱なら350 °C、超加熱なら650 °C・20 barの蒸気が出ます。'),
 ('Draw steam from the top or outer end of that section and pump it to a turbine. A steam engine switches it to Create boiler mode.','その区間の上面か外側の端から、ポンプで蒸気をタービンへ送ります。蒸気エンジンを付けるとCreateのボイラーになります。')])]:
    texts[f'{MOD}.ponder.shared.guide.{ident}.title']=(title,ja)
    texts[f'{MOD}.ponder.plant_{ident}.header']=(title,ja)
    for i,pair in enumerate(steps,1):texts[f'{MOD}.ponder.shared.guide.{ident}.step_{i}']=pair
for width in (2,3):
    ident=f'tubular_boiler_{width}x{width}'
    pair=(f'{width}x{width} horizontal tank boiler',f'断面{width}×{width}の横型タンクのボイラー')
    texts[f'{MOD}.ponder.shared.guide.{ident}.title']=pair
    texts[f'{MOD}.ponder.plant_{ident}.header']=pair
    for step in range(1,5):texts[f'{MOD}.ponder.shared.guide.{ident}.step_{step}']=texts[f'{MOD}.ponder.shared.guide.tubular_boiler.step_{step}']
write(ROOT/'tools/data/industrial_expansion_lang.json',{lang:{key:value[index] for key,value in texts.items()} for index,lang in enumerate(('en_us','ja_jp'))})
write(ROOT/'tools/data/industrial_expansion_tutorials.json',lessons)
