#!/usr/bin/env python3
"""Game fuel line using existing fans, tray/stirred vessels, centrifuges and presses."""
import json
from pathlib import Path
import generate_petrochemical_ponder as p

A=Path(__file__).resolve().parents[1]/'src/main/resources/assets/create_refinery_n_reactors'
NS='create_refinery_n_reactors:'
LABELS={
 'item.create_refinery_n_reactors.reactor_separator_core':('Reaction Trays','反応棚段','反应塔板'),
 'jei.create_refinery_n_reactors.tray_reactor':('Heated Tray Reactions','加熱棚段反応','加热塔板反应'),
 'jei.create_refinery_n_reactors.note.tray_reactor':('%s ticks; one batch per tray layer, no rotation. Burner heat: %s','%s tick、棚段1段につき1バッチ。回転不要。バーナーの熱：%s','%s tick；每层塔板1批，无需旋转。燃烧室热量：%s'),
}


def pipe(scene,x,y,z,*sides):
    scene.block(x,y,z,NS+'stainless_steel_pipe',waterlogged='false',**{side:str(side in sides).lower() for side in p.DIRECTIONS})


def feed(scene,x,y,z,solids=True):
    pipe(scene,x-2,y,z,'east','west');scene.pump(x-3,y,z,'east')
    scene.block(x-3,y-1,z,'create:cogwheel',axis='x',waterlogged='false')
    scene.block(x-4,y-1,z,'create:creative_motor',facing='east')
    if solids: scene.block(x,y,z-2,'create:andesite_funnel',facing='north',extracting='false',powered='false')


def preparation():
    scene=p.Structure(15,7)
    scene.reactor(4,2,3,'absorber',2,((-1,-1),),windows=True);feed(scene,4,2,3)
    scene.reactor(10,2,3,'stirred',1,((-1,-1),),windows=True);feed(scene,10,2,3)
    scene.reactor(10,2,10,'absorber',2,((-1,-1),),heat='seething',windows=True);feed(scene,10,2,10)
    scene.block(3,3,7,'create:creative_motor',facing='south');scene.block(3,3,8,'create:encased_fan',facing='south')
    scene.block(3,3,9,'minecraft:lava',level='0');scene.block(3,2,9,'minecraft:stone_bricks')
    scene.block(2,3,9,'minecraft:iron_bars',east='false',west='false',north='true',south='true',waterlogged='false')
    scene.block(4,3,9,'minecraft:iron_bars',east='false',west='false',north='true',south='true',waterlogged='false')
    scene.block(3,2,11,'create:depot',waterlogged='false')
    for x,z in ((4,3),(10,3)):
        scene.block(x+2,2,z,'create:andesite_funnel',facing='east',extracting='true',powered='false')
    for x,y,z in ((4,3,5),(10,2,5),(10,3,12)):
        scene.block(x,y,z,'create:fluid_valve', facing='up', axis_along_first='true', enabled='true', waterlogged='false')
    scene.pump(11,4,10,'up');pipe(scene,11,5,10,'up','down')
    scene.block(11,6,10,NS+'gas_receiver',bottom='true',top='true',shape='plain')
    scene.block(12,4,10,'create:cogwheel',axis='y',waterlogged='false');scene.block(12,3,10,'create:creative_motor',facing='up')
    p.OUT=A/'ponder/plant';scene.save('uranium_preparation')


def finishing():
    scene=p.Structure(15,8)
    scene.centrifuge(3,2,3,8,columns=2)
    pipe(scene,2,2,3,'east','west');scene.pump(1,2,3,'east')
    scene.block(1,1,3,'create:cogwheel',axis='x',waterlogged='false');scene.block(0,1,3,'create:creative_motor',facing='east')
    scene.pump(3,4,3,'up');pipe(scene,3,5,3,'up','down')
    scene.block(3,6,3,NS+'gas_receiver',bottom='true',top='true',shape='plain')
    scene.block(3,4,2,'create:cogwheel',axis='y',waterlogged='false');scene.block(3,3,2,'create:creative_motor',facing='up')
    pipe(scene,2,3,3,'east','west');scene.block(1,3,3,NS+'gas_receiver',bottom='true',top='true',shape='plain')
    scene.reactor(10,2,3,'stirred',1,windows=True);feed(scene,10,2,3,solids=False)
    for dx,dz,sides in ((0,-2,('north','south')),(2,0,('east','west'))):pipe(scene,10+dx,2,3+dz,*sides)
    scene.block(10,2,5,'create:andesite_funnel',facing='south',extracting='true',powered='false')
    scene.block(4,2,10,'create:depot',waterlogged='false');scene.block(4,4,10,'create:mechanical_press',facing='north')
    scene.block(3,4,10,'create:creative_motor',facing='east')
    scene.block(9,2,10,'minecraft:furnace',facing='north',lit='true')
    scene.block(11,3,7,'create:creative_motor',facing='south');scene.block(11,3,8,'create:encased_fan',facing='south')
    scene.burner(11,3,9);scene.block(11,2,11,'create:depot',waterlogged='false')
    p.OUT=A/'ponder/plant';scene.save('uranium_fuel_finishing')


SCENES={
 'uranium_preparation':{
  'title':('From uranium ore to centrifuge feed','ウラン鉱石から濃縮前の気体を作る','从铀矿石到离心机原料'),
  'components':['uranium_ore','deepslate_uranium_ore','uraninite','yellowcake','uranium_dioxide','uranium_tetrafluoride','reactor_separator_core'],
  'steps':[
   ([3,2,11],{'speed':32,'item':NS+'uraninite'},(
    'Crush uranium ore with Create Crushing Wheels to obtain Raw Uranium. Blow a fan through lava onto the raw pieces on this depot to roast them into yellowcake.',
    'ウラン鉱石をCreateのクラッシングホイールで粉砕し、ウラン原石にします。溶岩を通したファンの風をデポ上の原石へ当て、焙焼したイエローケーキにします。',
    '用Create粉碎轮将铀矿石粉碎为粗铀矿。风扇吹过熔岩，对置物台上的原料进行焙烧，得到黄饼。')),
   ([4,3,3],{'item':NS+'reactor_separator_core'},(
    'Fit trays in a 3×3 vessel two to four layers high. Feed roasted ore and hydrogen, then heat below. No rotation is needed.',
    '3×3・2～4段の槽へ棚段を入れます。焙焼物と水素を送り、下のバーナーで加熱します。回転は不要です。',
    '3×3、高2～4层槽内装棚板，供给焙烧物和氢气，从下方加热，无需旋转动力。')),
   ([10,2,3],{'item':NS+'uranium_tetrafluoride'},(
    'Feed uranium dioxide and hydrogen fluoride to a 3×3 impeller vessel. Heat below and drive its bottom centre at 32 RPM or more.',
    '撹拌翼付き3×3槽へ二酸化ウランとフッ化水素を入れます。下から加熱し、底面中央へ32 RPM以上を入力します。',
    '二氧化铀和氟化氢送入3×3叶轮槽，底部加热，底部中心输入至少32 RPM。')),
   ([10,3,10],{'item':NS+'gas_receiver'},(
    'Feed green salt and fluorine into a tray vessel and superheat with blue flames. Take gas from the top beside the hatch.',
    '棚段付き槽へグリーンソルトとフッ素を送り、青い炎で超加熱します。生成ガスは上面のハッチ横から取ります。',
    '绿盐和氟气送入棚板槽，用蓝焰超加热，产气从顶面检修口旁取出。')),
   ([10,3,12],{},(
    'Close the hatch, use fluoride-resistant pipes, fit relief valves and keep outlets clear.',
    'ハッチを閉じ、フッ化物には耐食配管を使います。安全弁を付け、出口を空けます。',
    '关盖、使用耐氟化物管道、安装安全阀，并保持出口畅通。')),
  ]},
 'uranium_fuel_finishing':{
  'title':('Enriching gas and firing fuel pellets','濃縮気体を燃料ペレットにする','将浓缩气体制成燃料芯块'),
  'components':['gas_centrifuge','enriched_uranium_dioxide','compact_uranium_dioxide','green_uranium_pellet','uranium_pellet','green_compact_uranium_pellet','compact_uranium_pellet','fuel_rod','compact_enriched_fuel_rod'],
  'steps':[
   ([3,2,3],{'speed':32,'item':NS+'gas_centrifuge'},(
    'Place four centrifuges for uranium or eight for compact fuel; drive every bottom shaft at 32 RPM or more. Feed at a side, collect enriched gas above and depleted gas at another side.',
    'ウラン用に4台、小型炉燃料用に8台を横につなぎ、各台の底面を32 RPM以上で回します。原料は側面、濃縮物は上面、劣化物は別の側面です。',
    '铀浓缩并排连接4台，小型堆燃料连接8台，每台底部输入至少32 RPM，侧面进料、顶部取浓缩气、另一侧取贫化气。')),
   ([10,2,3],{'item':NS+'enriched_uranium_dioxide'},(
    'Mix enriched UF6, water and hydrogen in an impeller vessel at 32 RPM or more without heating. Collect UO2 powder and hydrogen fluoride.',
    '濃縮UF6・水・水素を撹拌翼付き槽へ入れます。加熱せず32 RPM以上で混ぜ、UO2粉末とフッ化水素を回収します。',
    '浓缩UF6、水、氢气送入叶轮槽，不加热并以至少32 RPM搅拌，回收UO2粉末和氟化氢。')),
   ([4,2,10],{'item':NS+'green_uranium_pellet'},(
    'Place powder on a depot and press it with a Mechanical Press to make green pellets.',
    '粉末をデポに載せ、メカニカルプレスで押すと未焼結ペレットになります。',
    '粉末放在置物台上，用动力冲压机压成生坯颗粒。')),
   ([11,2,11],{'item':NS+'uranium_pellet'},(
    'Fire green pellets with burner-fan airflow or a furnace. Combine seven pellets with two cladding tubes for a rod.',
    '未焼結ペレットをバーナーの風かかまどで焼きます。完成品7個と被覆管2個で燃料棒にします。',
    '用燃烧器风道或熔炉烧结生坯，再用七个颗粒和两根包壳管组装燃料棒。')),
   ([3,6,3],{'item':NS+'compact_enriched_fuel_rod'},(
    'For compact fuel, enrich the gas once more, then use the same powder, press and firing steps. These rods fit three-layer cores only.',
    '小型炉用は濃縮ガスをもう一段遠心分離し、同じ粉末化・プレス・焼成を行います。燃料部3段専用です。',
    '小型炉燃料须将浓缩气再离心一次，再按相同流程制粉、冲压、烧结，仅用于三层燃料部。')),
  ]},
}


def catalogue_and_text():
    path=A/'ponder/tutorials.json';raw=path.read_bytes().decode();nl='\r\n' if '\r\n' in raw else '\n';catalog=json.loads(raw)
    for name,scene in SCENES.items():
        catalog=[entry for entry in catalog if entry['id']!=name]
        catalog.append({'id':name,'size':15,'height':8 if name.endswith('finishing') else 7,'scale':.5,'components':scene['components'],
                        'steps':[{'target':target,'key':f'step_{i+1}',**extra} for i,(target,extra,_) in enumerate(scene['steps'])]})
    path.write_bytes((json.dumps(catalog,ensure_ascii=False,indent=2).replace('\n',nl)+nl).encode())
    for index,locale in enumerate(('en_us','ja_jp','zh_cn')):
        path=A/f'lang/{locale}.json';raw=path.read_bytes().decode();nl='\r\n' if '\r\n' in raw else '\n';strings=json.loads(raw)
        for key,words in LABELS.items():strings[key]=words[index]
        for name,scene in SCENES.items():
            prefix=f'create_refinery_n_reactors.ponder.shared.guide.{name}.'
            strings[prefix+'title']=scene['title'][index]
            strings[f'create_refinery_n_reactors.ponder.plant_{name}.header']=scene['title'][index]
            for i,(_,_,captions) in enumerate(scene['steps']):strings[prefix+f'step_{i+1}']=captions[index]
        path.write_bytes((json.dumps(strings,ensure_ascii=False,indent=2).replace('\n',nl)+nl).encode())


if __name__=='__main__':preparation();finishing();catalogue_and_text()
