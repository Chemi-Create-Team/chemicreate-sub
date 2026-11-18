#!/usr/bin/env python3
"""A white native tank with three integrated water-sealed telescopic bells; no external guides."""
import json
from generate_petrochemical_ponder import Structure,OUT,NS,string,named,integer
from generate_reactor_assets import A
s=Structure(13,17)
for y in range(2,5):
 for x in range(4,7):
  for z in range(4,7):
   data=[string('id',NS+'gas_holder')]
   if (x,y,z)==(4,2,4):data += [named(3,'Size',integer(3)),named(3,'Height',integer(3)),named(1,'Window',b'\x01')]
   else:data += [named(11,'Controller',b''.join(integer(v) for v in (3,4,2,4)))]
   shape='window' if abs((x-4)-(z-4))==1 and not (x==5 and z==5) else 'plain'
   s.block(x,y,z,NS+'gas_holder',nbt=data,top=str(y==4).lower(),bottom=str(y==2).lower(),shape=shape)
s.block(1,1,5,'create:fluid_tank',top='true',bottom='true',shape='window')
s.pump(2,1,5,'east')
for x in (3,4):s.block(x,1,5,'create:fluid_pipe',east=str(x==3).lower(),west='true',up=str(x==4).lower(),down='false',north='false',south='false',waterlogged='false')
s.save('gas_bell');(A/'ponder/plant/gas_bell.nbt').write_bytes((OUT/'gas_bell.nbt').read_bytes());(OUT/'gas_bell.nbt').unlink()
TEXT={
 'title':('A telescopic water-sealed gas holder','水封式の3段伸縮ガスホルダー','水封式三段伸缩储气柜'),
 'build':('Build a 1×1 to 7×7 holder, two to eight layers high. Place on top to add a matching layer. No external rails are needed.',
 '幅1×1～7×7、高さ2～8段に並べます。上面へ置くと同じ幅で増設でき、外付けレールは不要です。',
 '搭建1×1～7×7、2～8层储气柜，对顶面放置可整层加高，无需外接导轨。'),
 'seal':('The sleeves rise as gas fills the holder and nest inside when empty.',
 '筒はガスが増えると伸び、空になると外槽へ収まります。',
 '气量增加时套筒升起，排空后收回外罐。'),
 'pipes':("Connect pipes and pumps to the fixed outer shell's sides or bottom, not the top.",
 '固定された外槽の側面・下面へパイプとポンプをつなぎます。上面は配管口ではありません。',
 '管道和动力泵接固定外罐侧面或底面，顶部不是接口。'),
 'first':('Filling raises the innermost sleeve first. No rotational power is required.',
 'ガスを入れると内側の筒から上がります。回転動力は不要です。',
 '充气时最内层套筒先升起，无需旋转动力。'),
 'second':('More gas raises each sleeve in turn.',
 'ガスが増えると、次の筒も順に上がります。',
 '气量继续增加时，其余套筒依次升起。'),
 'full':('Leave room above for extension. An obstruction stops both rising and filling.',
 '上には伸びる分の空間を空けます。障害物があると上昇と搬入が止まります。',
 '上方留足伸展空间，遇到障碍会停止上升与进气。'),
 'empty':('Draining lowers the sleeves back into the outer shell.',
 'ガスを抜くと筒が下がり、外槽へ戻ります。',
 '抽出气体后套筒下降并收回外罐。'),
 'contaminate':('Even 1 mB of another gas contaminates the entire charge. Waste keeps combustible and toxic hazards. Pump it to a lit flare; drain to exactly zero before changing gas.',
 '別のガスが1 mBでも残ると全量が汚染混合ガスになります。可燃性・毒性は残ります。点火したフレアへ送り、気体の切替前には完全に0 mBまで抜きます。',
 '即使只残留1 mB的另一种气体，也会污染整柜。废气保留可燃性和毒性。请排往已点燃的火炬，更换气体前必须完全排空至0 mB。'),
 'reaction':('Mixing flammable gas with oxygen or air causes an explosion. Keep the lines separate.',
 '可燃性ガスと酸素・空気を混ぜると爆発します。配管を分けます。',
 '可燃气体混入氧气或空气会爆炸，须分开管线。'),
 'purge':('Sneak-right-click with a wrench to purge the contents. Normal wrench use toggles windows.',
 'Shift＋レンチ右クリックで中身をパージします。通常のレンチ操作は窓の切り替えです。',
 '潜行持扳手右键可吹扫清空，普通扳手操作切换观察窗。')}
for lang,i in [('en_us',0),('ja_jp',1),('zh_cn',2)]:
 p=A/f'lang/{lang}.json';raw=p.read_bytes().decode();nl='\r\n' if '\r\n' in raw else '\n';data=json.loads(raw)
 for key in list(data):
  if key.startswith('create_refinery_n_reactors.ponder.shared.gas_bell.'):del data[key]
 for key,value in TEXT.items():data['create_refinery_n_reactors.ponder.shared.gas_bell.'+key]=value[i]
 data['create_refinery_n_reactors.ponder.gas_bell.header']=TEXT['title'][i]
 p.write_bytes((json.dumps(data,ensure_ascii=False,indent=2).replace('\n',nl)+nl).encode())
