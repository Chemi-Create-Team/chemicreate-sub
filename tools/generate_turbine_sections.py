"""Crafted octagonal sections for the high- and low-pressure stages."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/create_refinery_n_reactors'
D=ROOT/'src/main/resources/data/create_refinery_n_reactors'
NS='create_refinery_n_reactors:'


def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')


patterns={
 'high':['CCECC','CBSBC','EBSBE','CBSBC','CCECC'],
 'low':['CCCECCC','CBBBBBC','CBSBSBC','EBBSBBE','CBSBSBC','CBBBBBC','CCCECCC']}
for stage in ('high','low'):
 item=stage+'_pressure_turbine_unit'
 pressure='low' if stage=='low' else 'high'
 recipe={'type':'create:mechanical_crafting','accept_mirrored':True,'category':'misc',
  'pattern':patterns[stage],
  'key':{'C':{'item':NS+pressure+'_pressure_turbine_casing'},'B':{'item':NS+pressure+'_pressure_turbine_blade'},
         'E':{'item':NS+('low_pressure_turbine_end' if stage=='low' else 'tarbin_end')},'S':{'item':'create:shaft'}},
  'result':{'id':NS+item,'count':1}}
 write(D/'recipe'/f'{item}.json',recipe)
 for component in (item, pressure+'_pressure_turbine_casing', 'low_pressure_turbine_end' if stage=='low' else 'tarbin_end'):
  write(A/'models/item'/f'{component}.json',{'parent':'minecraft:builtin/entity','gui_light':'side',
   'textures':{'particle':NS+'block/turbine/high_pressure_casing'},'display':{
    'gui':{'rotation':[25,135,0],'scale':[.85,.85,.85]},
    'ground':{'translation':[0,3,0],'scale':[.5,.5,.5]},
    'fixed':{'rotation':[0,90,0],'scale':[.75,.75,.75]},
    'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.5,.5,.5]},
    'firstperson_righthand':{'rotation':[0,45,0],'scale':[.65,.65,.65]}}})

texts={
 'en_us':[
  'Turbine sections: build the length you need',
  'Craft the high-pressure section in a 5×5 Mechanical Crafter and the low-pressure section in a 7×7.',
  'One item places one block of depth: HP 3×3, LP 9×9. Place the bottom center on a foundation.',
  'Use a matching section on an end, casing or top face to extend the same shaft. Casing clicks extend the nearest exposed end without changing height or axis. Exactly 5 HP or 10 LP sections are required.',
  'Align the shafts. Pipe HP exhaust into LP. Central shaft faces are mechanical outputs.',
  'Pump 30 °C condensate from the LP underside. Drain before recovering individual sections with Shift + wrench.'],
 'ja_jp':[
  '断面を並べて作る蒸気タービン',
  'メカニカルクラフターは高圧断面が5×5、低圧断面が7×7です。',
  '1個で奥行き1ブロック分を置きます。高圧3×3、低圧9×9です。底の中央を台に置きます。',
  '同じ断面を端面・側面・上面へ使うと、軸と高さをそろえて延長します。側面・上面ではプレイヤーに近い端へ追加し、高圧5断面・低圧10断面で稼働します。',
  '軸をそろえ、高圧の排気を低圧へ配管します。中心は回転出力です。',
  '低圧の下から30 ℃の復水をポンプで出します。中身を抜いてからShift＋レンチで1断面ずつ回収します。'],
 'zh_cn':[
  '逐段拼接的蒸汽轮机',
  '高压截面使用5×5机械合成，低压截面使用7×7机械合成。',
  '每个物品放置一格深的截面：高压3×3、低压9×9。将底部中心放在基础上。',
  '在端面、侧面或顶面使用同类截面，即可保持轴向和高度延长。点击侧面或顶面时会加到离玩家最近的外端，高压需5段、低压需10段才能运行。',
  '对齐轴心，用管道将高压排汽送入低压。中央轴面输出旋转动力。',
  '用泵从低压底部排出30 ℃凝结水。排空后用潜行＋扳手逐段回收。']}
for lang,lines in texts.items():
 p=A/'lang'/f'{lang}.json';data=json.loads(p.read_text())
 names={'ja_jp':('高圧タービン断面','低圧タービン断面'), 'en_us':('High-pressure Turbine Section','Low-pressure Turbine Section'), 'zh_cn':('高压汽轮机截面','低压汽轮机截面')}[lang]
 for stage,name in zip(('high','low'),names):data['item.'+NS.replace(':','.')+stage+'_pressure_turbine_unit']=name
 data.pop('block.'+NS.replace(':','.')+'intermediate_pressure_steam_turbine',None)
 data.pop('item.'+NS.replace(':','.')+'intermediate_pressure_turbine_unit',None)
 for key,line in zip(('title','factory','place','connect','pipes','recover'),lines):
  data['ponder.create_refinery_n_reactors.turbine_multiblock.'+key]=line
  data['create_refinery_n_reactors.ponder.shared.turbine_multiblock.'+key]=line
 data['create_refinery_n_reactors.ponder.turbine_multiblock.header']=lines[0]
 data['turbine.create_refinery_n_reactors.sections']={'ja_jp':'断面の不足：%s / %s','en_us':'Sections required: %s / %s','zh_cn':'截面数量：%s / %s'}[lang]
 thermal={
  'ja_jp':('二段蒸気タービン：圧力と軸出力','高圧を5断面・低圧を10断面並べ、台の高さで軸をそろえて普通のシャフトでつなぎます。','中心は回転出力、外周は配管です。高圧→低圧へ蒸気を送ります。','分離器の蒸気を高圧・低圧タービンへ、水を戻り配管へ送ります。'),
  'en_us':('Two-stage steam turbines: pressure and shaft work','Place 5 HP and 10 LP sections, align their centers with foundations, and connect ordinary shafts.','Central hubs carry shaft power; outer ports carry fluids. Pipe HP exhaust into LP.','Send separator steam through HP and LP turbines, and separated water to the return header.'),
  'zh_cn':('两级蒸汽轮机：压力与轴功','排列5个高压和10个低压截面，用基础对齐中心，并连接普通传动杆。','中心输出旋转动力，外围连接管道。蒸汽依次经过高压、低压级。','分离出的蒸汽依次送入高压、低压汽轮机，水送入回水管。')
 }[lang]
 for key,line in zip(('turbine_operation.title','turbine_operation.step_1','turbine_operation.step_2','turbine_cycle.step_1'),thermal):
  data['create_refinery_n_reactors.ponder.shared.guide.'+key]=line
 data['create_refinery_n_reactors.ponder.plant_turbine_operation.header']=thermal[0]
 write(p,data)
p=A/'ponder/tutorials.json' ;data=json.loads(p.read_text())
for tutorial in data:
 if tutorial['id'].startswith('turbine_'):
  tutorial['components']=[v for v in tutorial['components'] if v!='intermediate_pressure_turbine_unit']
  tutorial.update(size=25,height=11,scale=.31)
  for step in tutorial['steps']:step['reveal']=[0,1,0,24,10,24]
write(p,data)
