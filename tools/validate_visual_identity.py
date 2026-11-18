#!/usr/bin/env python3
"""Audit finished items and exact vanilla liquid-bucket metal, without Pillow."""
import ast,collections,hashlib,json,re,struct,zlib
from pathlib import Path
from powder_assets import validate_assets as validate_powders
from generate_world_liquids import liquid_entries,bucket_pixels
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/create_refinery_n_reactors';NS='create_refinery_n_reactors:'

def rgba(path):
 b=path.read_bytes();assert struct.unpack('>IIBBBBB',b[16:29])==(16,16,8,6,0,0,0),path
 compressed=b'';i=8
 while i<len(b):
  n=struct.unpack('>I',b[i:i+4])[0]
  if b[i+4:i+8]==b'IDAT':compressed+=b[i+8:i+8+n]
  i+=n+12
 raw=zlib.decompress(compressed);assert len(raw)==16*65,path
 result=[];previous=bytearray(64)
 for y in range(16):
  kind=raw[y*65];assert 0<=kind<=4,path
  row=bytearray(raw[y*65+1:y*65+65])
  for x in range(64):
   left=row[x-4] if x>=4 else 0;above=previous[x];corner=previous[x-4] if x>=4 else 0
   estimate=left+above-corner
   paeth=min((left,above,corner),key=lambda value:abs(estimate-value))
   row[x]=(row[x]+(0,left,above,(left+above)//2,paeth)[kind])&255
  result.extend(tuple(row[x:x+4]) for x in range(0,64,4))
  previous=row
 return result

template=json.loads((R/'tools/vanilla_bucket_template.json').read_text());mask=set(template['content_mask'])
bucket_sources='\n'.join(path.read_text() for path in (R/'src/main/java/net/createplant/registry').glob('*.java'))
registered_buckets={name:kind.rsplit('.',1)[-1] for name,kind in re.findall(r'ITEMS\.register\(\s*"([a-z_]+_bucket)",\s*\(\)\s*->\s*new\s+([\w.]+)\(',bucket_sources)}
liquids=liquid_entries()
registered_buckets.update({entry['name']+'_bucket':'ProcessBucketItem' for entry in liquids})
from generate_process_gas_buckets import gas_entries
registered_buckets.update({entry['name']+'_bucket':'GasBucketItem' for entry in gas_entries()})
assert set(registered_buckets.values())<={'BucketItem','ProcessBucketItem','AlloyBucketItem','GasBucketItem'}
buckets=sorted((A/'models/item').glob('*bucket.json'));assert registered_buckets and {f.stem for f in buckets}==set(registered_buckets)
liquid_buckets={name for name,kind in registered_buckets.items() if kind!='GasBucketItem'}
alloy_buckets={entry['name']+'_bucket' for entry in liquids if entry['corrosive']}|{'corrosion_resistant_bucket'}
alloy_body=bucket_pixels(alloy=True)
for f in buckets:
 m=json.loads(f.read_text());assert m['textures']['layer0']==NS+'item/'+f.stem
 px=rgba(A/'textures/item'/(f.stem+'.png'))
 if f.stem in liquid_buckets:
  body=alloy_body if f.stem in alloy_buckets else template['pixels']
  assert all(p==tuple(body[i]) for i,p in enumerate(px) if i not in mask),f'Container metal changed: {f}'
  assert all(p[3]==template['pixels'][i][3] for i,p in enumerate(px)),f'Vanilla bucket silhouette changed: {f}'
assert len({tuple(rgba(A/'textures/item'/(f.stem+'.png'))) for f in buckets})==len(buckets)

def resolve(path,seen=()):
 assert path not in seen,'Model parent cycle'
 m=json.loads(path.read_text(encoding='utf-8-sig'));parent=m.get('parent','');base={}
 if parent.startswith(NS):base=resolve(A/'models'/(parent[len(NS):]+'.json'),seen+(path,))
 return base|m|{'textures':base.get('textures',{})|m.get('textures',{})}

item_tints={name.lower():int(color,16) for color,name in re.findall(r'event\.register\(\(stack, layer\) -> (0x[0-9A-Fa-f]+), net\.createplant\.registry\.CEUranium\.([A-Z_]+)\.get\(\)\);',(R/'src/main/java/net/createplant/ElectrolysisClient.java').read_text())}
generator=ast.parse((R/'tools/generate_uranium_assets.py').read_text())
tinted_powders=next(ast.literal_eval(node.value) for node in generator.body if isinstance(node,ast.Assign) and any(isinstance(target,ast.Name) and target.id=='TINTED_POWDERS' for target in node.targets))
assert set(tinted_powders)<=item_tints.keys(),'Missing runtime item tints'
powders=validate_powders(rgba)
unfinished_items={name for source in (R/'src/main/java/net/createplant/registry').glob('*.java') for name in re.findall(r'ITEMS\.register\(\s*"([a-z_]+)",\s*\(\)\s*->\s*new SequencedAssemblyItem\(',source.read_text())}
signatures=collections.defaultdict(list)
for f in sorted((A/'models/item').glob('*.json')):
 if f.stem in unfinished_items:continue
 m=resolve(f)
 # Entity-rendered items share a placeholder model; their geometry is checked in the live client.
 if m.get('parent')=='minecraft:builtin/entity':continue
 t=m.get('textures',{})
 # Expand aliases and content hashes, ignore unused material declarations.
 def texture(k):
  if k.startswith('#'):return texture(t[k[1:]])
  return hashlib.sha256((A/'textures'/(k[len(NS):]+'.png')).read_bytes()).hexdigest() if k.startswith(NS) else k
 used={v['texture'] for e in m.get('elements',[]) for v in e['faces'].values()}
 if not used:used={v for k,v in t.items() if k!='particle'}
 render={'elements':m.get('elements'), 'textures':sorted(texture(v) for v in used)}
 if f.stem in item_tints:render['tint']=item_tints[f.stem]
 if not render['elements']:render['parent']=m.get('parent')
 signatures[json.dumps(render,sort_keys=True)].append(f.stem)
# Restored originals share these mechanisms, housings and unmarked containers.
shared_parts=[{'condenser_cooling_inlet','condenser_cooling_outlet'},
 {'cooling_tower_basin','cooling_tower_shell'},
 {'cooling_tower_cold_outlet','cooling_tower_hot_inlet','cooling_tower_makeup_inlet'},
 {'empty_waste_canister','vitrified_waste_canister'}]
shared_groups=[g for g in signatures.values() if len(g)>1 and set(g) in shared_parts]
duplicates=[g for g in signatures.values() if len(g)>1 and set(g) not in shared_parts]
assert not duplicates,duplicates
for name in ('iron_catalyst','ammonium_nitrate','sulfur_dust'):
 m=resolve(A/'models/item'/(name+'.json'));assert m['textures']['layer0']==NS+'item/'+name
for name in ('stainless','cast_iron','brass','copper'):
 px=rgba(A/'textures/block/chemical'/(name+'.png'))
 assert len(set(px[y*16+x] for y in range(5,10) for x in range(4,12)))>=8,'Flat metal surface: '+name
refractory=rgba(A/'textures/block/zirconia_refractory_bricks.png')
assert len(set(refractory))>=8,'Zirconia refractory bricks need visible ceramic grain'
assert sum(sum(pixel[:3]) for pixel in refractory)/(len(refractory)*3)>165,'Zirconia refractory bricks must remain pale'
assert len(set(refractory[3*16:4*16]))>=2 and sum(sum(pixel[:3]) for pixel in refractory[3*16:4*16])/48<150, \
 'Zirconia refractory mortar course must remain visible'
report={'finished_items':sum(map(len,signatures.values())),'identical_finished_item_groups':duplicates,
 'powder_items':powders,
 'intentional_shared_item_groups':shared_groups,'unfinished_assembly_items':sorted(unfinished_items),
 'vanilla_body_buckets':len(liquid_buckets-alloy_buckets),'alloy_body_buckets':len(alloy_buckets),'gas_containers':len(buckets)-len(liquid_buckets),'grain_materials':['stainless','cast_iron','brass','copper'],
 'zirconia_refractory':'pale ceramic grain with offset dark mortar courses',
 'intentional_shared_materials':'Base alloys, turbine submesh surfaces, glass, standardized bucket bodies and Create shafts.'}
print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':
 (R/'docs/quality/texture-audit-after.json').write_text(json.dumps(report,indent=2)+'\n')
