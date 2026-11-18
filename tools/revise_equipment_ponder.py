"""Build staged equipment lessons from current structures; retain equipment models."""
import copy,json
from pathlib import Path
from validate_plant_tutorials import read_structure
from generate_petrochemical_ponder import Structure
from generate_ponder_language import generate,SHARED
A=Path(__file__).resolve().parents[1]/'src/main/resources/assets/create_refinery_n_reactors'
NS='create_refinery_n_reactors:'
cat=json.loads((A/'ponder/tutorials.json').read_text())
lang={k:json.loads((A/'lang'/f'{k}.json').read_text()) for k in ('en_us','ja_jp')}

def blocks(id):
 d=read_structure(A/'ponder/plant'/f'{id}.nbt')
 return {tuple(b['pos']):copy.deepcopy(d['palette'][b['state']]) for b in d['blocks'] if b['pos'][1]>0}

def save(id,data,size=17,height=8):
 s=Structure(size,height)
 for (x,y,z),v in data.items():s.block(x,y,z,v['Name'],**v.get('Properties',{}))
 s.save(id);p=A/'ponder/chemistry'/f'{id}.nbt';(A/'ponder/plant'/f'{id}.nbt').write_bytes(p.read_bytes());p.unlink()

# Parts store their formed look; a lesson that builds them shows them as placed until its 'formed' step.
FORMED_PARTS=set()
FORMED_AT={'module_cooling':'step_1','module_cooling_expansion':'step_1'}
def lesson(id,components,title,steps,data,size=17,height=8):
 for value in data.values():
  if value['Name'] in FORMED_PARTS:value.setdefault('Properties',{})['formed']='true'
 save(id,data,size,height)
 t={'id':id,'size':size,'height':height,'scale':.55 if size<20 else .38,'components':components,'steps':[]}
 if id in FORMED_AT:t['formed']=FORMED_AT[id]
 for i,(pos,en,ja,box) in enumerate(steps):
  step={'key':f'step_{i}','target':pos,'reveal':box}
  if i==len(steps)-1:step['speed']=64
  if id=='module_cooling' and i==2:step['wrench']=True
  t['steps'].append(step)
  for k,text in [('en_us',en),('ja_jp',ja)]:lang[k][SHARED+f'guide.{id}.step_{i}']=text
 for k,text in zip(('en_us','ja_jp'),title):lang[k][SHARED+f'guide.{id}.title']=text
 return t

def box(a,b):return list(a)+list(b)

replacements={}
# Electric heaters and heat exchangers have their own lessons.

# Direct air cooling: one actual 3x3x4 cell, then a three-cell bank.
def radiator(n,connections=False):
 d={}
 def put(p,name,**props):d[p]={'Name':name,'Properties':props}
 from generate_process_tutorials import shroud
 for i in range(n):
  z=4+3*i
  for x in range(3,6):
   for zz in range(z-1,z+2):
    for y in range(2,5):put((x,y,zz),NS+'cooler_radiator',facing=('south' if i==n-1 and zz==4+3*i+1 and x==4 and y==3 else 'north'),section=('bottom','middle','top')[y-2],drive=str(x==4 and zz==z).lower(),outlet=str(y==2).lower(),port=str(y==3 and x==4 and (i==0 and zz==3 or i==n-1 and zz==4+3*i+1)).lower())
  put((4,5,z),NS+'cooling_fan')
  for dx in (-1,0,1):
   for dz in (-1,0,1):
    if dx or dz:put((4+dx,5,z+dz),NS+'cooling_fan_shroud',**shroud(-dx,-dz))
  put((4,1,z),'create:creative_motor',facing='up')
 if connections:
  for z in (1,7):put((4,3,z),'create:fluid_tank',bottom='true',top='true',shape='plain')
  for z in (2,6):put((4,3,z),'create:mechanical_pump',facing='north',waterlogged='false')
 return d
components=next(t for t in cat if t['id']=='module_cooling')['components']
sets=[('module_cooling',1,False,('1. Radiator: direct cooling','1. ラジエーター：直接冷却'),[('Build one 3x3x4 cell: three layers of radiators, one top fan and eight shrouds.','ラジエーター3段の上にファン1個とシュラウド8個を置く、3×3×4の最小構成を作ります。'),('Drive the shaft below the fan to start cooling.','ファン真下の軸を回すと冷却します。'),('Hold a Create wrench on the formed equipment to set the target. Air cooling cannot cool below ambient.','完成設備にCreateのレンチを長押しして目標温度を設定します。外気温より低く冷やすことはできません。')]),('module_cooling_expansion',3,False,('2. Radiator: add fan cells','2. ラジエーター：ファン区画の増設'),[('Extend the complete cell in three-block increments, keeping the same width and height.','幅と高さを保ち、完成区画を3ブロックずつ同じ方向に増設します。'),('Each added cell needs its own fan, eight shrouds and driven column. Up to eight cells form one bank.','増設区画にもファン・シュラウド8個・駆動軸が必要です。最大8区画をまとめられます。'),('Add fan cells or reduce flow if cooling is insufficient.','冷え方が足りなければ、ファンを増やすか流量を下げます。')]),('module_cooling_integration',1,True,('3. Radiator: cooling circuits','3. ラジエーター：冷却ラインとの連携'),[('Connect Create tanks and pumps to the two central end ports, as shown.','図のように中央を貫く流路の両端へCreateのポンプと標準タンクを接続します。左右どちらからでも搬入できます。'),('Collect the cooled fluid. A full collection tank stops transfer.','冷えた流体を回収します。タンクが満杯になると止まります。'),('For central cooling, send cooled water to one exchanger row and return warmed water to the radiator. Products stay in the other row.','集中冷却では冷やした水を熱交換器の片側へ送り、温まった水をラジエーターへ戻します。製品は別の流路を通します。')])]
replacements['module_cooling']=[]
for id,n,c,title,lines in sets:
 steps=[([4,3,3 if i==0 else 5] if c else [4,2 if i!=1 else 5,4],e,j,box((0,1,0),(14,6,14))) for i,(e,j) in enumerate(lines)]
 replacements['module_cooling'].append(lesson(id,components,title,steps,radiator(n,c),15,7))

# Reaction vessels: show an actual extra catalyst bed, then separate process connections.
# Replace obsolete one-block cooler placeholders in the process overviews as well.
for id in ('module_haber','module_refinery','module_polymer'):
 t=next(t for t in cat if t['id']==id);data=blocks(id)
 centre=(5 if id=='module_polymer' else 7,2,5)
 for (x,y,z),v in radiator(1).items():
  value=copy.deepcopy(v)
  if value['Name']==NS+'cooler_radiator':value['Properties']['facing']='east';value['Properties']['port']='false'
  data[x-4+centre[0],y-2+centre[1],z-4+centre[2]]=value
 save(id,data,t.get('size',11),t.get('height',7))

# Correct obsolete descriptions everywhere still registered; keep captions bilingual.
for values in lang.values():
 for key,text in list(values.items()):
  if key.startswith(SHARED):
   text=text.replace('既存の工業用冷却器','ラジエーターまたは流体熱交換器').replace('完成済みの工業用冷却器','ラジエーターまたは流体熱交換器').replace('existing industrial cooler','radiator or fluid heat exchanger').replace('existing fin-fan industrial cooler','radiator or fluid heat exchanger').replace('water-fed cooler','direct-fluid radiator').replace('水を供給した冷却器','流体を直接通すラジエーター')
   values[key]=text
updates={
 'production_line.synthesis.safety':('Use goggles for a compact status summary and Display Links for detailed readings. Redstone, missing feed or full output stops processing.','ゴーグルで概要、ディスプレイリンクで詳細を確認します。信号・原料不足・出力満杯で処理を停止します。'),
 'guide.module_haber.step_4':('Pass hot mixture through a radiator, or exchange heat with a separate coolant circuit. No fixed water-consuming cooling recipe is used.','高温混合物をラジエーターへ直接通すか、独立した冷却液回路と熱交換します。水を一定量消費する冷却レシピは使いません。'),
 'guide.module_polymer.step_3':('Cool the hot polymer stream before the existing finishing step. Radiators preserve fluid identity and volume; collect finished material through native item transport.','高温ポリマー流体を冷やしてから既存の仕上げ工程へ送ります。ラジエーターは流体の種類と量を保ち、製品は標準のアイテム搬送で回収します。')}
for key,texts in updates.items():
 for k,text in zip(('en_us','ja_jp'),texts):lang[k][SHARED+key]=text
for id in ('module_haber','module_refinery','module_polymer'):
 for k,prefix,note in [('en_us','Process overview: ','This is a process overview. Build each module separately and connect its outlets with pumps and pipes; see the individual assembly lessons. '),('ja_jp','工程概略：','ここは工程の順序を示す概略図です。各設備は個別の組立手引きに従って離して作り、搬出側のポンプと配管で接続します。')]:
  title=SHARED+f'guide.{id}.title'
  if not lang[k][title].startswith(prefix):lang[k][title]=prefix+lang[k][title]
  step=SHARED+f'guide.{id}.step_1'
  if not lang[k][step].startswith(note):lang[k][step]=note+lang[k][step]
for code,note in [('en_us',' Liquid tanks, water tanks and gas receivers also place a whole layer from the top: four items for width two, nine for width three. Sneak to place singly.'),('ja_jp',' 液体タンク・冷却水タンク・ガスレシーバーも、上面から幅2なら4個、幅3なら9個を同時設置できます。スニーク中は1個ずつ設置します。')]:
 key=SHARED+'guide.passive_heat.step_5'
 if note not in lang[code][key]:lang[code][key]+=note
# Preserve tutorial order and native item links; each family now has consecutive chapters.
for family in replacements:
 cat=[t for t in cat if not t['id'].startswith(family+'_expansion') and not t['id'].startswith(family+'_integration')]
out=[]
for t in cat:out.extend(replacements.get(t['id'],[t]))
(A/'ponder/tutorials.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
for k,v in lang.items():(A/'lang'/f'{k}.json').write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
generate()
