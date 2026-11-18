#!/usr/bin/env python3
"""Native 16 px steel/copper motor pump, recipes and short translated Ponder lessons."""
import json, math
from pathlib import Path
from PIL import Image
from minecraft_style import grain
from generate_petrochemical_ponder import Structure, string, named, integer

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
FOLDER = 'block/electric_utilities/'
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')

def texture(name, base):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            edge = 14 if x == 0 or y == 0 else -22 if x == 15 or y == 15 else 0
            value = round(grain('electric_'+name, x, y, 3)) + edge
            if name == 'steel':
                value += 10 if y in (3, 4) and 3 <= x <= 12 else -8 if y in (11, 12) else 0
            if name == 'copper': value += 17 if x % 6 == 1 else -16 if x % 6 == 4 else 0
            im.putpixel((x, y), tuple(max(0, min(255, c+value)) for c in base) + (255,))
    if name == 'direction':
        for pos in ((7,6),(8,6),(6,7),(7,7),(8,7),(9,7),(7,8),(8,8),(7,9),(8,9)): im.putpixel(pos,(211,207,184,255))
    target = A/'textures'/FOLDER/(name+'.png');target.parent.mkdir(parents=True, exist_ok=True);im.save(target)

MATERIALS = {n: NS+FOLDER+n for n in ('steel', 'copper', 'ceramic', 'dark', 'direction')}
MATERIALS['particle'] = MATERIALS['steel']

def box(bounds, material):
    sizes = [bounds[i+3]-bounds[i] for i in range(3)]
    faces = {}
    for side in SIDES:
        w, h = (sizes[0], sizes[2]) if side in ('up', 'down') else (sizes[0], sizes[1]) if side in ('north', 'south') else (sizes[2], sizes[1])
        faces[side] = {'texture': '#'+material, 'uv': [(16-w)/2, (16-h)/2, (16+w)/2, (16+h)/2]}
    return {'from': list(bounds[:3]), 'to': list(bounds[3:]), 'faces': faces}

def model(name, elements):
    write(A/'models'/FOLDER/(name+'.json'), {'parent': 'minecraft:block/block', 'textures': MATERIALS, 'elements': elements})
    return NS+FOLDER+name


def assets():
    for name, colour in {'steel': (75, 80, 87), 'copper': (167, 94, 56), 'ceramic': (194, 186, 159), 'dark': (32, 36, 41),
                         'direction': (57, 63, 70)}.items(): texture(name, colour)
    pump = [box((2, 0, 2, 14, 2, 14), 'steel'), box((5, 5, 0, 11, 11, 16), 'copper'),
            box((3, 3, 4, 13, 13, 12), 'steel'), box((2, 5, 5, 4, 11, 11), 'copper'),
            box((10, 4, 4, 15, 12, 12), 'dark'), box((14, 5, 5, 15.5, 11, 11), 'copper')]
    for z in (0, 14): pump.append(box((4, 4, z, 12, 12, z+2), 'steel'))
    for x in (10, 12, 14):
        pump += [box((x, 3, 4, x+.75, 4.5, 12), 'steel'), box((x, 11.5, 4, x+.75, 13, 12), 'steel'),
                 box((x, 4, 3, x+.75, 12, 4.5), 'steel'), box((x, 4, 11.5, x+.75, 12, 13), 'steel')]
    for x in (3, 11): pump += [box((x, 12, 10, x+2, 15, 12), 'ceramic'),box((x+.5, 14, 10.5, x+1.5, 15.5, 11.5), 'copper')]
    pump.append(box((6,13,4,10,13.5,8), 'direction'))
    model('pump',pump)
    blades = [box((15.4, 7, 7, 15.9, 9, 9), 'copper')]
    for i in range(6):
        angle=i*math.pi/3
        y,z=8+2.2*math.cos(angle),8+2.2*math.sin(angle)
        blades.append(box((15.4, y-.55, z-.55, 15.9, y+.55, z+.55),'steel'))
    model('pump_fan',blades)
    model('pump_item',pump+blades)
    variants={}
    for facing, (x,y) in {'north':(0,0),'east':(0,90),'south':(0,180),'west':(0,270),'up':(270,0),'down':(90,0)}.items():
        for water in ('true','false'): variants[f'facing={facing},waterlogged={water}']={'model':NS+FOLDER+'pump','x':x,'y':y}
    write(A/'blockstates/electric_pump.json',{'variants':variants})
    write(A/'models/item/electric_pump.json',{'parent':NS+FOLDER+'pump_item'})
    for name in ('electric_pump',):
        write(D/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    write(D/'recipe/electric_pump.json',{'type':'minecraft:crafting_shaped','pattern':['CSC','NPN','CSC'],
        'key':{'N':{'item':NS+'nickel_ingot'},'C':{'item':'minecraft:copper_ingot'},'P':{'item':'create:mechanical_pump'},'S':{'item':NS+'steel_ingot'}},'result':{'id':NS+'electric_pump','count':1}})
    for tag in ('mineable/pickaxe','needs_iron_tool'):
        p=D.parent/f'minecraft/tags/block/{tag}.json';data=json.loads(p.read_text())
        data['values']=list(dict.fromkeys(data['values']+[NS+'electric_pump']));write(p,data)

TEXT = {
 'block.create_refinery_n_reactors.electric_pump':('Electric Pump','電動ポンプ','电动泵'),
 'electric_utility.create_refinery_n_reactors.state':('Status: %s','状態：%s','状态：%s'),
 'electric_utility.create_refinery_n_reactors.running':('Running','稼働中','运行中'),
 'electric_utility.create_refinery_n_reactors.disabled':('Stopped','停止','已停止'),
 'electric_utility.create_refinery_n_reactors.power':('Electrical input: %s kW','入力電力：%s kW','输入电功率：%s kW'),
 'electric_utility.create_refinery_n_reactors.voltage':('Terminal voltage: %s V','端子電圧：%s V','端电压：%s V'),
 'electric_utility.create_refinery_n_reactors.motor':('Motor reserve: %s J','モーター蓄積仕事：%s J','电机储能：%s J'),
 'electric_utility.create_refinery_n_reactors.rpm':('Internal speed: %s RPM','内部回転数：%s RPM','内部转速：%s RPM'),
 'electric_utility.create_refinery_n_reactors.power_low':('Insufficient power','電力不足','电力不足'),
}
LESSONS = {
 'electric_pump':{
  'title':('A pump driven by electricity','電気で動かすポンプ','电力驱动的泵'),
  'place':('Connect the two ends to Create pipes and tanks.','両端をCreateのパイプとタンクにつなぎます。','两端连接Create流体管道和储罐。'),
  'wire':('Connect both copper terminals to an Electro Energetics supply. About 400 V gives about 125 RPM.','銅端子2本をElectro Energeticsの電源につなぎます。約400 Vで約125 RPMです。','两个铜接线端接入Electro Energetics电源，约400 V时约125 RPM。'),
  'heat':('Wrench the pump to reverse flow. Its enclosed motor cannot drive external shafts.','レンチで流れの向きを変えます。内蔵モーターから外部の軸は回せません。','用扳手改变流向，内置电机不能带动外部传动轴。'),
  'stop':('Redstone or a power outage stops the motor. Pressure and pipe resistance still limit flow.','レッドストーンや停電で停止します。流量は圧力や配管抵抗にも制限されます。','红石信号或断电会停止电机，流量仍受压力和管道阻力限制。')}
}

def lessons():
    for name in LESSONS:
        s=Structure(9,7)
        data=[string('id',NS+name)]
        s.block(4,1,4,NS+name,nbt=data,facing='east',waterlogged='false')
        for x in (1,7):s.block(x,1,4,'create:fluid_tank',top='true',bottom='true',shape='window')
        for x in (2,3,5,6):s.block(x,1,4,'create:fluid_pipe',east='true',west='true',north='false',south='false',up='false',down='false',waterlogged='false')
        s.block(4,1,7,'electroenergetics:creative_battery')
        # Structure writer's output is relative to its plant directory.
        s.save('electric_'+name)
        from generate_petrochemical_ponder import OUT
        target=A/'ponder/plant'/(name+'.nbt');target.write_bytes((OUT/('electric_'+name+'.nbt')).read_bytes());(OUT/('electric_'+name+'.nbt')).unlink()
    for locale,index in [('en_us',0),('ja_jp',1),('zh_cn',2)]:
        p=A/'lang'/(locale+'.json');raw=p.read_bytes().decode('utf-8');nl='\r\n' if '\r\n' in raw else '\n';data=json.loads(raw)
        data.update({k:v[index] for k,v in TEXT.items()})
        for scene,captions in LESSONS.items():
            for key,value in captions.items():data[f'create_refinery_n_reactors.ponder.shared.{scene}.{key}']=value[index]
            data[f'create_refinery_n_reactors.ponder.{scene}.header']=captions['title'][index]
        p.write_bytes((json.dumps(data,indent=2,ensure_ascii=False).replace('\n',nl)+nl).encode('utf-8'))

if __name__=='__main__':assets();lessons()
