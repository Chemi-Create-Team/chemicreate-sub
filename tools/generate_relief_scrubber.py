#!/usr/bin/env python3
"""Generate the dedicated one-block relief scrubber assets and its three-language labels."""
import json
import struct
import zlib
from pathlib import Path
from minecraft_style import box, pixels

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/create_refinery_n_reactors'
DATA=ROOT/'src/main/resources/data'
NS='create_refinery_n_reactors:'


def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def png(path,image):
    def chunk(kind,data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    path.parent.mkdir(parents=True,exist_ok=True)
    rows=b''.join(b'\0'+bytes(channel for pixel in row for channel in pixel) for row in image)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))
        +chunk(b'IDAT',zlib.compress(rows,9))+chunk(b'IEND',b''))


for name,image in {
    'steel':pixels('stainless',(190,199,202)),
    'frame':pixels('cast_iron',(105,114,119)),
    'brass':pixels('brass',(190,148,55)),
    'sight':pixels('sight_glass',(116,151,154),'glass'),
    'packing':pixels('vent',(151,162,167),'vent'),
}.items():
    png(ASSETS/f'textures/block/relief_scrubber/{name}.png',image)

textures={name:NS+f'block/relief_scrubber/{name}' for name in ('steel','frame','brass','sight','packing')}
textures['particle']=textures['steel']


def elements(active):
    result=[]
    box(result,[1,0,1],[15,2,15],'frame')
    box(result,[5,0,5],[11,3,11],'steel')
    box(result,[2,2,2],[14,13,14],'steel')
    box(result,[1,2,1],[15,4,15],'brass')
    box(result,[1,11,1],[15,13,15],'brass')
    box(result,[3,4,3],[13,11,13],'steel')
    for x,z in ((1,1),(12,1),(1,12),(12,12)):
        box(result,[x,4,z],[x+3,11,z+3],'frame')
    # Authored facing north: the broad side nozzle is the only caustic service port.
    box(result,[5,6,0],[11,10,3],'brass')
    box(result,[6,7,0],[10,9,1],'packing')
    # The opposite broad pane identifies the wash vessel without pretending to show a live liquid level.
    box(result,[4,5,13],[12,10,14],'frame',{'south':'sight'})
    box(result,[5,13,5],[11,15,11],'steel')
    box(result,[7,14,7],[9,16,9],'brass')
    y=15 if active else 14
    box(result,[3,y,3],[13,y+1,13],'frame')
    return result


for state in ('idle','active'):
    write(ASSETS/f'models/block/relief_scrubber/{state}.json',{
        'parent':'minecraft:block/block','textures':textures,'elements':elements(state=='active')})

variants={}
for facing,rotation in (('north',0),('east',90),('south',180),('west',270)):
    for active in (False,True):
        value={'model':NS+'block/relief_scrubber/'+('active' if active else 'idle')}
        if rotation:value['y']=rotation
        variants[f'facing={facing},active={str(active).lower()}']=value
write(ASSETS/'blockstates/relief_scrubber.json',{'variants':variants})
write(ASSETS/'models/item/relief_scrubber.json',{'parent':NS+'block/relief_scrubber/idle'})
write(DATA/'create_refinery_n_reactors/loot_table/blocks/relief_scrubber.json',{
    'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+'relief_scrubber','functions':[
        {'function':'minecraft:copy_components','source':'block_entity','include':['minecraft:block_entity_data']}]}],
    'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

for tag in ('mineable/pickaxe','needs_iron_tool'):
    path=DATA/f'minecraft/tags/block/{tag}.json'
    data=json.loads(path.read_text()) if path.exists() else {'replace':False,'values':[]}
    data['values']=list(dict.fromkeys(data['values']+[NS+'relief_scrubber']))
    write(path,data)

translations={
    'en_us':{
        'block.create_refinery_n_reactors.relief_scrubber':'Gas Scrubber',
        'scrubber.create_refinery_n_reactors.status.scrubbing':'Neutralising piped gas',
        'scrubber.create_refinery_n_reactors.status.disconnected':'Pipe acidic gas into the bottom',
        'scrubber.create_refinery_n_reactors.status.blocked':'The top is blocked: no gas is accepted',
        'scrubber.create_refinery_n_reactors.status.empty':'Waiting for caustic soda solution',
        'scrubber.create_refinery_n_reactors.status.ready':'Ready to scrub gas',
        'scrubber.create_refinery_n_reactors.caustic':'Caustic soda: %s / %s mB',
    },
    'ja_jp':{
        'block.create_refinery_n_reactors.relief_scrubber':'排ガススクラバー',
        'scrubber.create_refinery_n_reactors.status.scrubbing':'排ガスを中和中',
        'scrubber.create_refinery_n_reactors.status.disconnected':'下面から酸性ガスを配管で送ってください',
        'scrubber.create_refinery_n_reactors.status.blocked':'上面が塞がっているため、ガスを受け入れません',
        'scrubber.create_refinery_n_reactors.status.empty':'苛性ソーダ液を補給してください',
        'scrubber.create_refinery_n_reactors.status.ready':'排ガス洗浄の準備完了',
        'scrubber.create_refinery_n_reactors.caustic':'苛性ソーダ液：%s / %s mB',
    },
    'zh_cn':{
        'block.create_refinery_n_reactors.relief_scrubber':'废气洗涤器',
        'scrubber.create_refinery_n_reactors.status.scrubbing':'正在中和管道排气',
        'scrubber.create_refinery_n_reactors.status.disconnected':'请从底部用管道送入酸性气体',
        'scrubber.create_refinery_n_reactors.status.blocked':'顶部被堵住，不接收气体',
        'scrubber.create_refinery_n_reactors.status.empty':'请补充烧碱溶液',
        'scrubber.create_refinery_n_reactors.status.ready':'已准备洗涤排气',
        'scrubber.create_refinery_n_reactors.caustic':'烧碱溶液：%s / %s mB',
    },
}
for language,additions in translations.items():
    path=ASSETS/f'lang/{language}.json';data=json.loads(path.read_text());data.update(additions);write(path,data)

print('Generated relief scrubber models, 16px textures, loot, tags and translations.')
