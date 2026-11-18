"""Stage 2 pressure assemblies. Only pressure-specific artwork and tutorials are generated."""
import json,gzip,struct,zlib,sys
from pathlib import Path
from minecraft_style import pixels
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/create_refinery_n_reactors';D=R/'src/main/resources/data';NS='create_refinery_n_reactors:'
def write(p,data):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def png(name,px):
 def chunk(k,d):return struct.pack('>I',len(d))+k+d+struct.pack('>I',zlib.crc32(k+d)&0xffffffff)
 p=A/'textures/block/pressure'/f'{name}.png';p.parent.mkdir(parents=True,exist_ok=True)
 p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(b'\0'+bytes(c for p in row for c in p) for row in px),9))+chunk(b'IEND',b''))
for role,base in [('steel','stainless'),('frame','cast_iron'),('bronze','brass')]:png(role,pixels(base,(180,188,191)))
dial=pixels('stainless',(190,198,202));arrow=pixels('cast_iron',(100,110,120))
for y in range(4,12):
 for x in range(4,12):dial[y][x]=(230,229,207,255)
for x,y in [(5,6),(6,5),(8,4),(10,5),(11,6),(8,8),(9,7),(10,6)]:dial[y][x]=(65,72,75,255)
for y in range(6,10):
 for x in range(3,12):arrow[y][x]=(205,171,84,255) if (x<8 or abs(y-7.5)<=11-x) else arrow[y][x]
png('dial',dial);png('arrow',arrow)
textures={r:NS+'block/pressure/'+r for r in ['steel','frame','bronze','dial','arrow']};textures['particle']=textures['steel']
def box(b,t):return {'from':b[:3],'to':b[3:],'faces':{f:{'texture':'#'+t} for f in ['north','south','east','west','up','down']}}
def model(name,parts):write(A/'models/block/pressure'/f'{name}.json',{'parent':'minecraft:block/block','textures':textures,'elements':[box(b,t) for b,t in parts]})
def own(name,parts,kind='facing'):
 model(name,parts);variants={}
 for facing,rot in [('north',0),('east',90),('south',180),('west',270)]:variants['facing='+facing]={'model':NS+'block/pressure/'+name,'y':rot}
 if kind=='pump':
  variants.update({'facing=up':{'model':NS+'block/pressure/'+name,'x':90},'facing=down':{'model':NS+'block/pressure/'+name,'x':270}})
 write(A/'blockstates'/f'{name}.json',{'variants':{'':{'model':NS+'block/pressure/'+name}} if kind=='static' else variants})
 write(A/'models/item'/f'{name}.json',{'parent':NS+'block/pressure/'+name})
chamber=[([1,0,1,15,2,15],'frame'),([3,2,3,13,5,13],'steel'),([4,5,4,12,14,12],'steel'),([3,12,3,13,15,13],'bronze'),([5,15,5,11,16,11],'steel')]
for x in [2,12]:
 for z in [2,12]:chamber.append(([x,1,z,x+2,4,z+2],'bronze'))
chamber += [([5,6,3,11,11,4],'dial')]
own('pressure_chamber',chamber,'static')
pump=[([3,3,3,13,13,13],'steel'),([2,2,0,14,14,2],'frame'),([2,2,14,14,14,16],'frame'),([4,4,0,12,12,16],'steel'),([4,0,4,12,4,12],'frame'),([4,13,4,12,15,12],'bronze'),([5,6,2,11,11,3],'dial')]
own('high_pressure_pump',pump,'pump')
for name,back in [('pressure_reducing_valve',False)]:
 parts=[([4,4,0,12,12,16],'steel'),([2,3,0,14,13,2],'frame'),([2,3,14,14,13,16],'frame'),([3,3,5,13,12,11],'steel'),([5,0,5,11,4,11],'frame'),([5,12,5,11,15,11],'bronze'),([3,15,3,13,16,13],'frame'),([2.9,5,6,3,10,10],'dial'),([13,5,6,13.1,10,10],'arrow')]
 for y in [12,13,14]:parts.append(([4.5,y,4.5,11.5,y+.5,11.5],'steel' if back else 'bronze'))
 own(name,parts)
recipes={
 'pressure_chamber':(['SCS','S S','PPP'],{'S':{'item':'create:iron_sheet'},'C':{'item':'create:fluid_tank'},'P':{'item':'minecraft:iron_ingot'}}),
 'high_pressure_pump':(['ABA','PMP','ASA'],{'A':{'item':'create:iron_sheet'},'B':{'item':'create:precision_mechanism'},'P':{'item':NS+'stainless_steel_pipe'},'M':{'item':'create:mechanical_pump'},'S':{'item':'create:shaft'}}),
 'pressure_reducing_valve':([' B ','PVP',' S '],{'B':{'item':'create:brass_sheet'},'P':{'item':NS+'stainless_steel_pipe'},'V':{'item':'create:fluid_valve'},'S':{'item':'create:iron_sheet'}})}
for n,(pattern,key) in recipes.items():
 if n not in ('pressure_reducing_valve','stainless_steel_pipe'):write(D/'create_refinery_n_reactors/recipe'/f'{n}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':key,'result':{'id':NS+n,'count':2 if n=='stainless_steel_pipe' else 1}})
 write(D/'create_refinery_n_reactors/loot_table/blocks'/f'{n}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+n}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for tag in ['mineable/pickaxe','needs_iron_tool']:
 path=D/'minecraft/tags/block'/f'{tag}.json';data=json.loads(path.read_text()) if path.exists() else {'replace':False,'values':[]};data['values']=list(dict.fromkeys([v for v in data['values'] if v!=NS+'steam_pipe']+[NS+n for n in recipes]));write(path,data)
# Remove obsolete registry assets and migrate tutorial palette IDs without altering geometry of other devices.
for p in [A/'blockstates/steam_pipe.json',A/'models/item/steam_pipe.json',D/'create_refinery_n_reactors/recipe/steam_pipe.json',D/'create_refinery_n_reactors/loot_table/blocks/steam_pipe.json']:
 if p.exists():p.unlink()
import shutil
shutil.rmtree(A/'models/block/steam_pipe',ignore_errors=True)
for p in (A/'textures').rglob('*steam_pipe*.png'):p.unlink()
for p in (A/'ponder').rglob('*.nbt'):
 raw=gzip.decompress(p.read_bytes());old=NS+'steam_pipe';new=NS+'stainless_steel_pipe'
 if old.encode() not in raw:continue
 raw=raw.replace(struct.pack('>H',len(old))+old.encode(),struct.pack('>H',len(new))+new.encode());p.write_bytes(gzip.compress(raw,mtime=0))
langs={l:json.loads((A/'lang'/f'{l}.json').read_text()) for l in ['ja_jp','en_us']}
names=[('pressure_reducing_valve','減圧弁','Pressure-Reducing Valve'),('stainless_steel_pipe','ステンレス鋼パイプ','Stainless Steel Pipe')]
translations={
 'target':('目標圧力（絶対圧）','Target absolute pressure'),
 'incomplete':('下に圧力室を設置してください','Place a pressure chamber underneath'),
 'valve_setting':('設定 %s bar（絶対圧）','Set %s bar absolute'),
 'implosion':('外圧による圧壊の危険','Risk of external-pressure collapse'),
 'recovery':('回収配管へ排出中','Recovering vapour'),
 'venting':('大気へ放出中','Venting to atmosphere'),
 'scrubbing':('スクラバーで中和中','Neutralising in scrubber'),
 'partial_scrubbing':('中和しきれず、残りを大気へ放出中','Scrubber short: venting the rest'),
 'blocked':('回収・放出先が使用不能','Recovery and exhaust blocked')}
for lang,index in [('ja_jp',0),('en_us',1)]:
 data=langs[lang]
 for n,ja,en in names:data['block.'+NS.replace(':','')+'.'+n]=(ja,en)[index]
 data.pop('block.'+NS.replace(':','')+'.steam_pipe',None)
 for k,pair in translations.items():data['pressure.'+NS.replace(':','')+'.'+k]=pair[index]
 for key,ja,en in [('pressure_setpoint','設定絶対圧（bar）','Set pressure/bar'),('pump_power','ポンプ供給動力（W）','Pump power/W'),('section_pressure','配管区間圧力（bar）','Line pressure/bar')]:
  data['display.'+NS.replace(':','')+'.'+key]=(ja,en)[index]
 write(A/'lang'/f'{lang}.json',data)
# Other locales use English fallbacks for newly added labels.
p=A/'lang/zh_cn.json';data=json.loads(p.read_text());data.pop('block.'+NS.replace(':','')+'.steam_pipe',None);data['pressure.'+NS.replace(':','')+'.scrubbing']='洗涤器中和中';data['pressure.'+NS.replace(':','')+'.partial_scrubbing']='未能全部中和，其余排入大气';write(p,data)
print('Generated the pressure valves, recipes, 16px alloys and translations.')
