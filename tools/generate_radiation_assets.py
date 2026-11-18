from pathlib import Path
import json
import generate_petrochemical_ponder as ponder
root=Path(__file__).resolve().parents[1];A=root/'src/main/resources/assets/create_refinery_n_reactors';D=root/'src/main/resources/data/create_refinery_n_reactors';NS='create_refinery_n_reactors:'
def write(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
def cube(a,b,t):return {'from':a,'to':b,'faces':{k:{'texture':'#'+t} for k in ['up','down','north','south','east','west']}}
ponder.OUT = A / 'ponder/nuclear'
measurement = ponder.Structure(9, 5)
measurement.block(6, 1, 4, 'minecraft:chest', facing='west', type='single', waterlogged='false')
for y in range(1, 4):
 for z in range(3, 6):measurement.block(4, y, z, 'minecraft:light_gray_concrete')
measurement.save('radiation_measurement')
cleanup = ponder.Structure(9, 5)
cleanup.block(6, 1, 3, 'create:fluid_tank', bottom='true', top='true', shape='plain')
cleanup.block(5, 1, 3, NS+'stainless_steel_pipe', east='true', west='true', up='false', down='false', north='false', south='false', waterlogged='false')
for x in range(1, 4):
 for z in range(5, 8):
  if x in (1, 3) or z in (5, 7):cleanup.block(x, 1, z, 'minecraft:light_gray_concrete')
  else:cleanup.block(x, 1, z, 'minecraft:water', level='0')
cleanup.save('radiation_decontamination')
for name,survey in [('personal_dosimeter',False),('survey_meter',True)]:
 elements=[cube([3,2,6],[13,14,10],'steel'),cube([3,2,5.5],[13,14,6],'dark'),cube([4,8,5],[12,13,5.5],'paper'),cube([7.6,8.5,4.8],[8.2,12,5],'dark'),cube([4,4,5],[6,6,5.5],'brass'),cube([10,4,5],[12,6,5.5],'brass'),cube([4,12,9],[12,15,10],'brass')]
 if survey:elements += [cube([12,5,7],[15,12,10],'dark'),cube([13,6,6],[15,12,7],'brass')]
 write(A/f'models/item/{name}.json',{'textures':{'steel':NS+'block/chemical/stainless','dark':NS+'block/chemical/cast_iron','brass':NS+'block/chemical/brass','paper':'minecraft:block/white_concrete','particle':NS+'block/chemical/stainless'},'elements':elements,'display':{'gui':{'rotation':[10,160,0],'scale':[.85,.85,.85]},'thirdperson_righthand':{'rotation':[0,180,0],'translation':[0,1,0],'scale':[.5,.5,.5]},'firstperson_righthand':{'rotation':[0,165,0],'translation':[0,1,0],'scale':[.45,.45,.45]},'firstperson_lefthand':{'rotation':[0,195,0],'translation':[0,1,0],'scale':[.45,.45,.45]},'ground':{'scale':[.5,.5,.5]}}})
 write(D/f'recipe/crafting/{name}.json',{'type':'minecraft:crafting_shaped','pattern':['BGB','RER',' S '],'key':{'B':{'item':'create:brass_sheet'},'G':{'item':'minecraft:glass_pane'},'R':{'item':'minecraft:redstone'},'E':{'item':'create:electron_tube'},'S':{'item':'create:andesite_alloy' if survey else 'minecraft:paper'}},'result':{'id':NS+name,'count':1}})
tags={'water':['minecraft:water'],'steel':['minecraft:iron_block','minecraft:anvil','create:industrial_iron_block','create:weathered_iron_block',NS+'steel_block',NS+'stainless_steel_block'], 'concrete':['#minecraft:concrete',NS+'industrial_concrete',NS+'industrial_concrete_slab',NS+'industrial_concrete_stairs']+[f'minecraft:{color}_concrete' for color in ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']], 'serpentinite':[NS+x for x in ['biological_shield','reactor_top_cap','biological_shield_drive','biological_shield_coolant_outlet','reactor_fuel_channel_tube']]}
for k,values in tags.items():write(D/f'tags/block/radiation_shielding/{k}.json',{'replace':False,'values':[{'id':x,'required':False} for x in values]})
text={
'item.create_refinery_n_reactors.personal_dosimeter':('Personal dosimeter','個人線量計','个人剂量计'),
'item.create_refinery_n_reactors.survey_meter':('Radiation survey meter','放射線サーベイメーター','辐射巡测仪'),
'radiation.meter.unmeasured':('Not measured yet — hold the instrument','未測定：手に持って測定してください','尚未测量：请手持仪表'),
'radiation.meter.dosimeter':('Lifetime %s mSv · Now %s mSv/h','生涯積算 %s mSv・現在 %s mSv/h','终生累计 %s mSv · 当前 %s mSv/h'),
'radiation.meter.survey':('%s window: %s mSv/h','%s 窓：%s mSv/h','%s 窗：%s mSv/h'),
'radiation.meter.committed':('Committed internal dose: %s mSv','預託内部線量：%s mSv','待积内照射剂量：%s mSv'),
'radiation.meter.risk':('Nominal cancer-risk increment: +%s%% (statistics only)','名目がんリスク増分：+%s%%（統計表示のみ）','名义癌症风险增量：+%s%%（仅统计显示）'),
'radiation.meter.toggle':('Right-click: open/close the beta window','右クリック：ベータ窓の開閉','右键：开关贝塔窗'),
 'tooltip.create_refinery_n_reactors.radiation_protection':('Beta/contamination interception: %s%%; no gamma protection','β線・汚染の遮断：%s%%。γ線は防げません','贝塔与污染拦截：%s%%；不防护伽马射线')}
lessons={
'radiation_measurement':{'title':('Measure radiation before handling fuel','燃料を扱う前に放射線を測る','处理燃料前测量辐射'),
'badge':('Hold the dosimeter to read current and lifetime dose. Taking it off never resets the lifetime record.','個人線量計を手に持つと、現在の線量率と生涯積算線量を読めます。外しても記録は消えません。','手持个人剂量计查看当前剂量率和终生累计剂量，取下仪表不会清除记录。'),
'survey':('Hold the survey meter and approach slowly. Right-click to open the beta window; faster clicks indicate a stronger field.','サーベイメーターを持って少しずつ近づきます。右クリックでβ窓を開き、クリックが増える場所を確認します。','手持巡测仪缓慢接近，右键打开贝塔窗；点击越密集，辐射场越强。'),
'shield':('Keep spent fuel underwater and move it with the crane or funnels. Water and solid shielding reduce radiation along the path; clothing does not stop gamma rays.','使用済み燃料は水中に保ち、クレーンやファンネルで動かします。水と遮蔽壁を間に置き、防護服だけで近づかないでください。','用起重机或漏斗在水下转运乏燃料，在人和燃料之间保留水与屏蔽墙；不能仅靠防护服接近。')},
'radiation_decontamination':{'title':('Contain and wash radioactive contamination','放射性汚染を広げず除染する','控制并清洗放射性污染'),
'contain':('A spill or broken radioactive container contaminates the floor. Fence it off and measure before entering.','放射性の液漏れや容器破損で床が汚染されます。立ち入りを区切り、入る前に測定します。','放射性液体泄漏或容器破损会污染地面，先隔离区域并测量。'),
'protect':('Wear clean protective clothing and a gas mask with a usable filter. Shoes carry contamination into clean areas, and removed dirty clothing remains radioactive.','清潔な防護服と、フィルターが残ったガスマスクを着用します。靴の汚れは移動先へ広がり、脱いだ服も汚染物として扱います。','穿清洁防护服和滤芯完好的防毒面具；鞋子会传播污染，脱下的脏衣仍是放射源。'),
'wash':('Use soap or enter clean water to wash your skin and clothing. Soap on a contaminated surface washes that patch into the drainage below; received dose remains recorded.','石けんを使うか清水に入り、皮膚と服を洗います。汚染面も石けんで洗えますが、排水先と脱いだ服は隔離してください。','用肥皂或清水清洗皮肤与衣物，也可用肥皂清洗污染表面；隔离排水和脱下的脏衣。')}}
for lang,i in [('en_us',0),('ja_jp',1),('zh_cn',2)]:
 p=A/f'lang/{lang}.json';d=json.loads(p.read_text())
 for k,v in text.items():d[k]=v[i]
 for scene,rows in lessons.items():
  for k,v in rows.items():d['create_refinery_n_reactors.ponder.shared.'+scene+'.'+k]=v[i]
  d['create_refinery_n_reactors.ponder.'+scene+'.header']=rows['title'][i]
 for k in list(d):
  if 'reactor_shield_leak' in k:
   del d[k];d[k.replace('reactor_shield_leak','reactor_radiation_rate')]=('Core-port radiation (mSv/h)','炉ポート線量率（mSv/h）','堆端口剂量率（mSv/h）')[i]
  if k.endswith('fuel_pool.rods'):d[k]=('Use the crane or funnels to keep spent fuel under water; never carry an unshielded assembly by hand.','使用済み燃料はクレーンかファンネルで水中搬送します。遮蔽せず手で持ち運ばないでください。','用起重机或漏斗在水下转运乏燃料，勿徒手搬运未屏蔽组件。')[i]
 write(p,d)
p=root/'tools/generate_ponder_language.py';s=p.read_text()
if "'radiation_measurement'" not in s:s=s.replace("'rbmk_core', 'rbmk_control_rods'", "'radiation_measurement', 'radiation_decontamination', 'rbmk_core', 'rbmk_control_rods'")
p.write_text(s)
p=root/'tools/generate_display_language.py'
if p.exists():
 s=p.read_text().replace("('reactor_shield_leak', '生体遮蔽漏れ率')", "('reactor_radiation_rate', '炉ポート線量率（mSv/h）')");p.write_text(s)
