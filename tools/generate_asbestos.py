"""Asbestos item texture: a supplied picture of loose white and grey fibres, reduced to a 16x16 pixel icon on a transparent background."""
import json,sys
from pathlib import Path
from PIL import Image
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/create_refinery_n_reactors';NS='create_refinery_n_reactors:'

def icon(source):
    im=Image.open(source).convert('RGB');px=im.load()
    box=(128,160,896,864)
    crop=im.crop(box);side=max(crop.size);square=Image.new('RGB',(side,side),(30,30,30));square.paste(crop,((side-crop.width)//2,(side-crop.height)//2))
    big=square.resize((64,64),Image.BOX);out=Image.new('RGBA',(16,16),(0,0,0,0));src=big.load();dst=out.load()
    for y in range(16):
        for x in range(16):
            cells=[src[x*4+i,y*4+j] for i in range(4) for j in range(4)]
            lit=[c for c in cells if max(c)>85]
            if len(lit)*3<len(cells):continue
            r=sum(c[0] for c in lit)//len(lit);g=sum(c[1] for c in lit)//len(lit);b=sum(c[2] for c in lit)//len(lit)
            dst[x,y]=tuple(max(0,min(255,int((c-130)*1.3+140))) for c in (r,g,b))+(255,)
    return out

def write(p,data):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

if __name__=='__main__':
    icon(sys.argv[1]).save(A/'textures/item/asbestos.png')
    write(A/'models/item/asbestos.json',{'parent':'minecraft:item/generated','textures':{'layer0':NS+'item/asbestos'}})
    for lang,name in (('en_us','Asbestos'),('ja_jp','アスベスト'),('zh_cn','石棉')):
        path=A/'lang'/f'{lang}.json';raw=path.read_bytes().decode('utf8');nl='\r\n' if '\r\n' in raw else '\n'
        d=json.loads(raw);d['item.create_refinery_n_reactors.asbestos']=name
        path.write_bytes((json.dumps(d,ensure_ascii=False,indent=2).replace('\n',nl)+nl).encode('utf8'))
