#!/usr/bin/env python3
"""Ore recovery: native crushing, wet leaching and clarification and three-ingot electrowinning."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from generate_world_liquids import bucket_image, liquid_entries, translations as liquid_translations
R=Path(__file__).resolve().parents[1]; A=R/'src/main/resources/assets/create_refinery_n_reactors'; D=R/'src/main/resources/data/create_refinery_n_reactors';NS='create_refinery_n_reactors:'
METALS={'iron':('minecraft:iron_ingot',('#d8e0b7','#a1b689','#6b835e','#3c5141'),('Iron','鉄','铁')),
        'copper':('minecraft:copper_ingot',('#a9dcf2','#61a5c6','#35739e','#214760'),('Copper','銅','铜')),
        'zinc':('create:zinc_ingot',('#f2efe2','#c9cebd','#939f91','#606f69'),('Zinc','亜鉛','锌')),
        'tin':(NS+'tin_ingot',('#e4f2ef','#b5d0d2','#7b9fa7','#456a79'),('Tin','錫','锡'))}

def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def fluid(name,n):return {'type':'neoforge:single','fluid':name,'amount':n}
def out(name,n):return {'id':name,'amount':n}
def item(name,n=1):return {'id':name,'count':n}
def append_tag(path,values):
 data=json.loads(path.read_text())if path.exists()else{'replace':False,'values':[]};data['values']=list(dict.fromkeys(data['values']+values));write(path,data)

def ore_recipes():
 for family in ('block','item'):
  append_tag(R/f'src/main/resources/data/c/tags/{family}/ores/tin.json',[NS+'cassiterite_ore',NS+'deepslate_cassiterite_ore'])
 for metal, (ingot,_,_)in METALS.items():
  crushed=NS+'crushed_raw_tin'if metal=='tin'else'create:crushed_raw_'+metal
  for old in (f'{metal}_pressure_leaching',f'{metal}_from_{metal}_sulfate_solution'):(D/f'recipe/pressure_reaction/{old}.json').unlink(missing_ok=True)
 for old in ('gold_leaching','gold_pressure_leaching','gold_from_gold_chloride_solution'):(D/f'recipe/pressure_reaction/{old}.json').unlink(missing_ok=True)
 write(D/'recipe/mixing/dilute_sulfuric_acid.json',{'type':'create:mixing','ingredients':[fluid(NS+'sulfuric_acid',250),fluid('minecraft:water',750)],'results':[out(NS+'dilute_sulfuric_acid',1000)]})
 for name in ('cassiterite','crushed_raw_tin'):
  write(D/f'recipe/smelting/{name}.json',{'type':'minecraft:smelting','category':'misc','ingredient':{'item':NS+name},'result':item(NS+'tin_ingot'),'experience':.7,'cookingtime':200})
 write(D/'recipe/crushing/raw_tin.json',{'type':'create:crushing','ingredients':[{'tag':'c:raw_materials/tin'}],'processing_time':200,'results':[item(NS+'crushed_raw_tin')]})
 write(D/'recipe/splashing/crushed_tin.json',{'type':'create:splashing','ingredients':[{'item':NS+'crushed_raw_tin'}],'results':[item(NS+'tin_ingot')]})
 append_tag(R/'src/main/resources/data/c/tags/item/ingots/tin.json',[NS+'tin_ingot'])
 append_tag(R/'src/main/resources/data/c/tags/item/ingots.json',['#c:ingots/tin'])
 append_tag(R/'src/main/resources/data/c/tags/item/raw_materials/tin.json',[NS+'cassiterite'])
 append_tag(R/'src/main/resources/data/c/tags/item/raw_materials.json',['#c:raw_materials/tin'])

def icon(name,rows,palette):
 im=Image.new('RGBA',(16,16));px=im.load()
 for y,row in enumerate(rows):
  for x,ch in enumerate(row):
   if ch!='.':px[x,y]=tuple(int(palette[int(ch)][i:i+2],16)for i in (1,3,5))+(255,)
 path=A/f'textures/item/{name}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
 write(A/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':NS+'item/'+name}})

def tin_assets():
 palette=('#dce3df','#a3b0ac','#637570','#344841')
 icon('cassiterite',['................','................','......333.......','....332113......','...32100113.....','..3210111123....','..32112221123...','.321122321123...','.322233211123...','..3232111223....','..321112233.....','...321223.......','....333.........','................','................','................'],palette)
 icon('crushed_raw_tin',['................','................','................','.....33.........','....3113..33....','...3100133113...','..321112321013..','..322223321123..','.3211333212233..','.3212332123313..','..323321133123..','...3321112333...','....332233......','......33........','................','................'],palette)
 icon('tin_ingot',['................','................','................','................','......33333.....','.....3000003....','....300011113...','...30001111113..','..3000111111113.','.30001111111223.','.31111111112223.','..322222222223..','...3333333333...','................','................','................'],palette)
 icon('tailings_mud',['................','................','................','................','................','.....3333.......','...3322223......','..3221122233....','.321122222223...','.3222222112223..','..322211222223..','...3222222233...','....3333333.....','................','................','................'],('#a2a08c','#7e8169','#595f4b','#373e32'))
 for prefix in ('','deepslate_'):
  im=Image.new('RGBA',(16,16));draw=ImageDraw.Draw(im)
  for x,y in ((2,3),(9,2),(5,9),(11,11)):
   draw.polygon([(x,y+1),(x+1,y),(x+3,y),(x+4,y+2),(x+2,y+3),(x,y+2)],fill=palette[3]);draw.rectangle((x+1,y+1,x+2,y+2),fill=palette[1]);draw.point((x+1,y),fill=palette[0])
  path=A/f'textures/block/{prefix}cassiterite_ore_overlay.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)

TEXTS={
 'item.create_refinery_n_reactors.tin_ingot':('Tin Ingot','錫インゴット','锡锭'),
 'item.create_refinery_n_reactors.crushed_raw_tin':('Crushed Raw Tin','砕いた錫の原石','粉碎锡矿石'),
 'item.create_refinery_n_reactors.cassiterite':('Raw Tin (Cassiterite)','錫の原石（錫石）','粗锡（锡石）'),
 'item.create_refinery_n_reactors.tailings_mud':('Ore Tailings Sludge','鉱滓スラッジ','矿渣污泥'),
 'block.create_refinery_n_reactors.cassiterite_ore':('Cassiterite Ore','錫石鉱石','锡石矿石'),
 'block.create_refinery_n_reactors.deepslate_cassiterite_ore':('Deepslate Cassiterite Ore','深層錫石鉱石','深层锡石矿石'),
 'fluid.create_refinery_n_reactors.dilute_sulfuric_acid':('Dilute Sulfuric Acid','希硫酸','稀硫酸'),
 'electrolysis.metal_rate':('Metal %s/t / acid %s mB/t / %s plates / %s%%','金属%s個/t・再生酸%s mB/t・極板%s枚・%s%%','金属%s个/t・再生酸%s mB/t・极板%s片・%s%%'),
 'electrolysis.metal_storage':('Feed %s mB / metal %s / acid %s mB','原料%s mB・金属%s個・再生酸%s mB','原料%s mB・金属%s个・再生酸%s mB'),
 'jei.create_refinery_n_reactors.note.electrowinning':('3 ingots + 250 mB recycled acid\n%s kJ per raw ore', '原石1個→インゴット3個\n%s kJ・希硫酸250 mBを再生', '粗矿1份→金属锭3个\n%s kJ・再生稀硫酸250 mB'),
 'display.create_refinery_n_reactors.electrolysis_metal':('Metal production (items/t)','金属生産（個/t）','金属产量（个/t）'),
 'display.create_refinery_n_reactors.electrolysis_recovered_acid':('Regenerated acid (mB/t)','再生酸（mB/t）','再生酸（mB/t）'),
 'display.create_refinery_n_reactors.electrolysis_metal_storage':('Stored recovered metal','回収金属在庫','回收金属库存'),
}
def translations():
 for metal,(_,_,names)in METALS.items():
  TEXTS[f'fluid.create_refinery_n_reactors.{metal}_sulfate_solution']=(f'Purified {names[0]} Solution',f'精製{names[1]}溶液',f'精制{names[2]}溶液')
 for index,lang in enumerate(('en_us','ja_jp','zh_cn')):
  p=A/f'lang/{lang}.json';data=json.loads(p.read_text());data={k:v for k,v in data.items()if'gold_chloride_solution'not in k}
  for key,values in TEXTS.items():data[key]=values[index]
  write(p,data)
 entries=[entry for entry in liquid_entries()if entry['name'].startswith(('tin_sulfate','dilute_sulfuric'))]
 for entry in entries:
  bucket_image(entry['name']+'_bucket',entry['color'],entry['corrosive'])
  write(A/f'blockstates/{entry["name"]}.json',{'variants':{'':{'model':'minecraft:block/water'}}})
 # Container labels use the same vanilla-shaped bucket generator as all other liquids.
 liquid_translations(liquid_entries())
 for rel in ('blockstates/gold_chloride_solution.json','models/item/gold_chloride_solution_bucket.json','textures/item/gold_chloride_solution_bucket.png'):(A/rel).unlink(missing_ok=True)

if __name__=='__main__':
 ore_recipes();tin_assets();translations()
