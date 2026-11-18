#!/usr/bin/env python3
"""Carbon steel in native Create milling, superheated mixing and five-pass pressing."""
import json
from pathlib import Path
from powder_assets import write_powder_item
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/create_refinery_n_reactors';D=R/'src/main/resources/data';NS='create_refinery_n_reactors:'
def write(p,v):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
def recipes():
 for fuel in ('coal','charcoal'):
  for kind in ('crushing','milling'):
   target=D/('create'if kind=='milling'else'create_refinery_n_reactors')/'recipe'/kind/(fuel+'.json'if kind=='milling'else'carbon_powder_from_'+fuel+'.json')
   write(target,{'type':'create:'+kind,'ingredients':[{'item':'minecraft:'+fuel}],'processing_time':100,'results':[{'id':NS+'carbon_powder'}]})
 write(D/'create_refinery_n_reactors/recipe/mixing/unprocessed_steel.json',{'type':'create:mixing','ingredients':[{'tag':'c:ingots/iron'}for _ in range(4)]+[{'item':NS+'carbon_powder'}],'results':[{'id':NS+'unprocessed_steel','count':4}],'heat_requirement':'superheated'})
 # Each of the four ingot-sized workpieces takes five native press passes and yields one steel.
 write(D/'create_refinery_n_reactors/recipe/sequenced_assembly/steel_ingot.json',{'type':'create:sequenced_assembly','ingredient':{'item':NS+'unprocessed_steel'},'loops':5,'results':[{'id':NS+'steel_ingot'}],'sequence':[{'type':'create:pressing','ingredients':[{'item':NS+'heated_unfinished_steel'}],'results':[{'id':NS+'heated_unfinished_steel'}]}],'transitional_item':{'id':NS+'heated_unfinished_steel'}})
 for p in (D/'create_refinery_n_reactors/recipe/mixing').glob('puddled_iron*.json'):p.unlink()
def assets():
 from PIL import Image
 write_powder_item('carbon_powder')
 im=Image.open(A/'textures/item/steel_ingot.png').convert('RGBA')
 for y in range(16):
  for x in range(16):
   r,g,b,a=im.getpixel((x,y))
   if a:
    shade=(r+g+b)//3;shade=max(25,min(185,shade*3//4));im.putpixel((x,y),(shade,shade+3,shade+2,a))
 for x,y in ((5,7),(8,5),(10,9),(6,10),(12,7)):
  if im.getpixel((x,y))[3]:im.putpixel((x,y),(41,44,45,255))
 im.save(A/'textures/item/unprocessed_steel.png');write(A/'models/item/unprocessed_steel.json',{'parent':'minecraft:item/generated','textures':{'layer0':NS+'item/unprocessed_steel'}})
 for i,lang in enumerate(('en_us','ja_jp','zh_cn')):
  p=A/f'lang/{lang}.json';s=json.loads(p.read_text());s['item.create_refinery_n_reactors.carbon_powder']=('Carbon Powder','炭素粉','碳粉')[i];s['item.create_refinery_n_reactors.unprocessed_steel']=('Unprocessed Steel','鋼鉄の中間素材','未加工钢')[i];write(p,s)
if __name__=='__main__':recipes();assets()
