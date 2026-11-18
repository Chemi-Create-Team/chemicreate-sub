"""Gas pipe (a copy of the nickel alloy pipe models) and the air gas bucket: textures, models, loot, recipes and translations."""
import json
from pathlib import Path
from PIL import Image
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/create_refinery_n_reactors';D=R/'src/main/resources/data/create_refinery_n_reactors';NS='create_refinery_n_reactors:'

def write(p,data):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def recolor(src,dest,shift):
    im=Image.open(src).convert('RGBA');p=im.load()
    for y in range(im.height):
        for x in range(im.width):
            r,g,b,a=p[x,y];p[x,y]=(max(0,min(255,r+shift[0])),max(0,min(255,g+shift[1])),max(0,min(255,b+shift[2])),a)
    im.save(dest)

def main():
    for src in (A/'models/block/nickel_alloy_pipe').glob('*.json'):
        # The pipe wears the texture of the steam exhaust flue: its steel for the body, its frame for the collars.
        text=src.read_text(encoding='utf8').replace('block/transport/nickel_alloy_pipe',NS+'block/steam/steel').replace(NS+NS,NS).replace('block/chemical/cast_iron','block/steam/frame').replace('nickel_alloy_pipe','gas_pipe')
        dest=A/'models/block/gas_pipe'/src.name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(text,encoding='utf8')
    (A/'blockstates/gas_pipe.json').write_text((A/'blockstates/nickel_alloy_pipe.json').read_text(encoding='utf8').replace('nickel_alloy_pipe','gas_pipe'),encoding='utf8')
    write(A/'models/item/gas_pipe.json',{'parent':NS+'block/gas_pipe/inventory'})
    write(A/'blockstates/air_gas.json',json.loads((A/'blockstates/oxygen_gas.json').read_text(encoding='utf8')))
    write(A/'models/item/air_gas_bucket.json',{'parent':'minecraft:item/generated','textures':{'layer0':NS+'item/air_gas_bucket'}})
    recolor(A/'textures/item/nitrogen_gas_bucket.png',A/'textures/item/air_gas_bucket.png',(-10,6,14))
    for name in ('gas_pipe',):
        write(D/'loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    write(D/'recipe/gas_pipe.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':[' I ','CPC',' I '],'key':{'I':{'tag':'c:plates/iron'},'C':{'tag':'c:plates/copper'},'P':{'item':'create:fluid_pipe'}},'result':{'id':NS+'gas_pipe','count':4}})
    pickaxe=R/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json';data=json.loads(pickaxe.read_text(encoding='utf8'))
    data['values']=list(dict.fromkeys(data['values']+[NS+'gas_pipe']));pickaxe.write_text(json.dumps(data,indent=2)+'\n',encoding='utf8')
    names={'block.create_refinery_n_reactors.gas_pipe':('Gas Pipe','気体パイプ','气体管道'),
        'fluid_type.create_refinery_n_reactors.air_gas':('Air','空気','空气'),'item.create_refinery_n_reactors.air_gas_bucket':('Air Bucket','空気入りバケツ','空气桶'),
        'gas_pipe.create_refinery_n_reactors.pressure':('Pressure: %s bar','圧力：%s bar','压力：%s bar')}
    for i,lang in enumerate(('en_us','ja_jp','zh_cn')):
        path=A/'lang'/f'{lang}.json';raw=path.read_bytes().decode('utf8');nl='\r\n' if '\r\n' in raw else '\n'
        data=json.loads(raw)
        for k,v in names.items():data[k]=v[i]
        path.write_bytes((json.dumps(data,ensure_ascii=False,indent=2).replace('\n',nl)+nl).encode('utf8'))
    print('Generated gas pipe and air bucket assets')

if __name__=='__main__':main()
