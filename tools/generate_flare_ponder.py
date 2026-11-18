#!/usr/bin/env python3
"""A minimal eight-block flare chimney and a real comparator/reference/link circuit."""
import json
from generate_reactor_assets import A, NS
from generate_petrochemical_ponder import Structure, OUT, named, integer, string, compound

s=Structure(11,28)
def pipe(x,y,z,*sides):
 s.block(x,y,z,NS+'gas_pipe',**{d:str(d in sides).lower() for d in ('east','west','north','south','up','down')},waterlogged='false')
s.block(1,1,4,'create:fluid_tank',bottom='true',top='true',shape='window')
s.block(2,1,4,NS+'pipe_pressure_gauge',axis='x',nbt=[named(3,'ScrollValue',integer(0))])
pipe(3,1,4,'west','east','south')
s.block(4,1,4,'create:fluid_valve',facing='up',axis_along_first='false',enabled='false',waterlogged='false')
s.block(5,1,4,'create:fluid_tank',bottom='true',top='true',shape='window')
s.block(3,1,5,'create:fluid_valve',facing='up',axis_along_first='true',enabled='true',waterlogged='false')
pipe(3,1,6,'north','east')
for x in range(4,8):pipe(x,1,6,'east','west')
pipe(8,1,6,'west','up')
for y in range(2,10):pipe(8,y,6,'down','up')
s.block(8,10,6,NS+'flare_tip',pilot='false',waterlogged='false')
for y in range(1,10):
 for x,z in ((7,5),(9,5),(7,7)):s.block(x,y,z,'create:metal_girder',axis='y',waterlogged='false',top='false',bottom='false')
 s.block(9,y,7,'create:industrial_iron_block')
 s.block(9,y,8,'minecraft:ladder',facing='south',waterlogged='false')
s.block(2,1,3,'minecraft:comparator',facing='south',mode='compare',powered='false')
for x in range(3,6):s.block(x,1,3,'minecraft:redstone_wire',east='side',west='side',north='none',south='none',power=str(18-x))
s.block(6,1,3,'minecraft:redstone_block')
s.block(2,1,2,'minecraft:redstone_wire',north='side',south='side',east='none',west='none',power='0')
freq=[named(10,'FrequencyFirst',compound([string('id','minecraft:amethyst_shard'),named(3,'count',integer(1))])),named(10,'FrequencyLast',compound([string('id','minecraft:prismarine_crystals'),named(3,'count',integer(1))]))]
s.block(2,1,1,'create:redstone_link',nbt=freq+[named(1,'Transmitter',b'\x01')],facing='up',receiver='false',powered='false')
s.block(2,1,5,'create:andesite_casing')
s.block(2,2,5,'create:redstone_link',nbt=freq+[named(1,'Transmitter',b'\x00')],facing='up',receiver='true',powered='false')
s.save('flare_stack');(A/'ponder/plant/flare_stack.nbt').write_bytes((OUT/'flare_stack.nbt').read_bytes());(OUT/'flare_stack.nbt').unlink()
texts={
 'title':('Relieving gas pressure with a flare','フレアでガス圧を逃がす','用火炬释放气体压力'),
 'pressure':('Pressure rises when demand stops. Add a branch to send excess gas to the flare.',
 '消費先が止まると圧力が上がります。分岐を作り、余ったガスをフレアへ逃がします。',
 '用气停止时压力会上升，设置分支将多余气体送往火炬。'),
 'bypass':('Branch a T before the stopped consumer. Put the redstone relief valve in this bypass, leading upwards to an elevated flare tip. Without a signal the valve stays closed.',
 '停止した消費先の手前にT字の分岐を作ります。上向きのバイパスへ信号式逃がし弁を入れ、高所のフレア先端へ接続します。信号がなければ弁は閉じます。',
 '在停止的用气设备前设置T形支路。将红石泄压阀装在通往高处火炬头的旁路上。无信号时阀门关闭。'),
 'circuit':('Set the gauge to 16 bar. Use a comparator circuit to open the valve at signal 13 or higher.',
 '圧力計を16 barに設定します。比較器を使い、信号13以上で弁が開く回路を作ります。',
 '压力计设为16 bar，用比较器电路使信号13及以上时开阀。'),
 'links':('Give both native Redstone Links the same two item frequencies. The transmitter takes the comparator output; the receiver on the support beside the relief valve opens it. When pressure falls, it closes again.',
 '2つのレッドストーンリンクに同じ2種類のアイテムを設定。送信側で比較器の出力を受け、弁の横の支柱上の受信側で開きます。圧力が下がれば閉じます。',
 '为两个红石链路设置相同的两种物品频率。发射端接收比较器输出，阀门旁支架上的接收端开启阀门。压力下降后会再次关闭。'),
 'chimney':('Build the chimney with ordinary metal supports and connected pipes. The tip needs at least three vertical pipes immediately below it and an unobstructed sky above. Extend the chimney to keep flames far from the factory.',
 '通常の金属支柱と配管で煙突を組みます。先端の直下には縦につながった配管3本以上、上には開けた空が必要です。工場から火を離すため煙突を延長できます。',
 '用普通金属支架和连通的管道搭建烟囱。火炬头正下方至少需要3格竖直管道，上方必须无遮挡。加高烟囱以使火焰远离工厂。'),
 'pilot':('Right-click the tip with flint and steel or a fire charge to light its pilot. Empty-hand right-click, rain or water extinguishes it. Check the pilot before opening the gas line.',
 '先端に火打石と打ち金かファイヤーチャージを右クリックして種火を点けます。素手の右クリック、雨、水で消えます。ガスを流す前に種火を確認してください。',
 '用打火石或火焰弹右击点燃引火火焰。空手右击、雨水或浸水会使它熄灭。供气前确认引火火焰已点燃。'),
 'burn':('Rising pressure opens the valve and burns excess gas. Without gas, only the pilot stays lit.',
 '圧力が上がると弁が開き、余ったガスを燃やします。ガスがない間は種火だけになります。',
 '升压时阀门开启并燃烧余气，无气体时仅保留种火。'),
 'hazards':('An unlit flare releases unburned gas. Keep ignition sources away and use a scrubber for toxic gases.',
 '種火が消えると未燃ガスが漏れます。火気を近づけず、有毒ガスにはスクラバーも使います。',
 '种火熄灭会泄漏未燃气体，远离火源，有毒气体还需洗涤器。')}
for lang,i in [('en_us',0),('ja_jp',1),('zh_cn',2)]:
 p=A/f'lang/{lang}.json';raw=p.read_bytes().decode('utf-8');nl='\r\n' if '\r\n' in raw else '\n';data=json.loads(raw)
 for key,variants in texts.items():data['create_refinery_n_reactors.ponder.shared.flare_stack.'+key]=variants[i]
 data['create_refinery_n_reactors.ponder.flare_stack.header']=texts['title'][i]
 # Directly connected vessels now retain a static pressure reading.
 data['create_refinery_n_reactors.ponder.shared.pipe_gauges.stale']=[
  'Without flow, temperature becomes unmeasured. Pressure gauges directly on vessels still work.',
  '流れが止まると温度は未測定になります。容器へ直結した圧力計は停止中も使えます。',
  '停止流动后温度显示未测量，直接连接容器的压力计仍能读数。'][i]
 if lang=='zh_cn':
  gauge={
   'title':'用仪表读取管道状态',
   'place':'管道温度计、压力表和流量计可沿任意轴线装入管道，流体直接从中通过。',
   'reading':'让流体流经仪表，即可读取温度和管线压力。',
   'scale':'温度计量程0～1000 °C，压力计量程0～300 bar。',
   'gas':'这些仪表也能用于气体管道。',
   'display':'工程师护目镜显示读数和警报。显示链接可读取流体温度、管道区段压力及净流量。',
   'alarm_set':'手持扳手在表面长按可设置警报：选择高于或低于阈值时输出信号，再设置阈值。',
   'alarm_signal':'满足设定条件时，仪表从非管道面输出红石信号。',
   'flow':'流量计显示管道流量，范围0～1000 mB/t。'}
  for key,value in gauge.items():data['create_refinery_n_reactors.ponder.shared.pipe_gauges.'+key]=value
  data['create_refinery_n_reactors.ponder.pipe_gauges.header']=gauge['title']
 p.write_bytes((json.dumps(data,ensure_ascii=False,indent=2).replace('\n',nl)+nl).encode('utf-8'))
