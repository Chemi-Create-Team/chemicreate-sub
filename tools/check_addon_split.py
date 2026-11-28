#!/usr/bin/env python3
"""Prints addon content still present in core (src/main) resources. Exit code 1 on violations.
Needs nbtlib (PYTHONPATH) to check .nbt files. Addon ids are derived from the registrations in atomics/ and logistics/ Java code."""
import os,re,sys,json,glob
R=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(R)
NS='create_refinery_n_reactors'
lit=re.compile(r'"([a-z][a-z0-9_]*)"')
call=re.compile(r'\b\w+\(\s*"([a-z][a-z0-9_]*)"')
def stems():
    s=set()
    for m in ('src/main','atomics/src/main','logistics/src/main'):
        A=f'{R}/{m}/resources/assets/{NS}/'.replace('main/resources','main/resources')
        for sub in ('blockstates','models/item','textures/item'):
            if os.path.isdir(A+sub):
                for f in os.listdir(A+sub):
                    if os.path.isfile(A+sub+'/'+f): s.add(f.rsplit('.',1)[0])
        L=f'{R}/{m}/resources/data/{NS}/loot_table/blocks'
        if os.path.isdir(L): s.update(f[:-5] for f in os.listdir(L))
    return s
def files(root):
    for d,_,fs in os.walk(os.path.join(R,root)):
        for f in fs:
            if f.endswith('.java'): yield os.path.join(d,f)
def reg(root,regonly=True):
    s=set()
    for p in files(root):
        if p.endswith('CEMachineSounds.java'): continue
        t=open(p,encoding='utf-8').read()
        if 'Deferred' in t: s|=set(call.findall(t))
    return s
def alllit(root):
    s=set()
    for p in files(root): s|=set(lit.findall(open(p,encoding='utf-8').read()))
    return s

def derive_ids():
    st=stems(); core=reg('src/main/java'); corelit=alllit('src/main/java'); out={}
    for n in ('atomics','logistics'):
        root=f'{n}/src/main/java'
        regs=reg(root)
        i=(regs|(alllit(root)&st))-core
        i-={x for x in i if x in corelit and x not in regs}
        out[n]={x for x in i if not (x.endswith('_bucket') and x[:-7] in corelit)}-{'converter'}
    return out
ids=derive_ids()
allids={i:a for a in ids for i in ids[a]}
def stem_owner(stem):
    p=stem.split('_')
    for n in range(len(p),0,-1):
        if '_'.join(p[:n]) in allids: return allids[ '_'.join(p[:n])]
# scene names
scenes={}
for a in ids:
    for p in glob.glob(f'{a}/src/main/resources/assets/{NS}/ponder/**/*.nbt',recursive=True):
        scenes[os.path.basename(p)[:-4]]=a
for a in ids:
    t=json.load(open(f'{a}/src/main/resources/assets/{NS}/ponder/tutorials.json'))
    for e in t: scenes[e['id']]=a
# advancement ids
adv={}
for a in ids:
    for p in glob.glob(f'{a}/src/main/resources/data/{NS}/advancement/**/*.json',recursive=True):
        adv[os.path.relpath(p,f'{a}/src/main/resources/data/{NS}/advancement')[:-5].replace('/','.')]=a
ATOM_PREFIX=['reactor_control_console','reactor_core','fusion_reactor','fusion_motor_generator','fusion_console','converter','continuous_factory','cubic_press','centrifuge','fuel_pool','large_press','radiation']
LOG_PREFIX=['arm','relay_computer','industrial_dispatch']
def owner(k):
    m=re.match(r'^(.*?)\.?'+NS+r'\.(.*)$',k)
    if not m:
        if k.startswith('radiation.') or k.startswith('plant_systems.fuel_cycle.'): return 'atomics'
        if k.startswith('industrial_dispatch.'): return 'logistics'
        return None
    pre,rest=m.groups(); seg=rest.split('.')
    if pre=='' and seg[0]=='ponder':
        n=seg[1]
        if n=='shared': n=seg[2] if seg[2]!='guide' else seg[3]
        n=re.sub(r'^plant_','',n)
        return scenes.get(n)
    if pre in('block','item','fluid','fluid_type','entity'): return stem_owner(seg[0])
    if pre=='advancement':
        key=rest; 
        if seg[0]=='fluid': return stem_owner(seg[1]) if len(seg)>1 else None
        name='.'.join(seg); name=re.sub(r'\.description$','',name).replace('facility.','facilities.',1)
        return adv.get(name)
    if pre=='crane':
        if rest.startswith('quarry') or rest.startswith('status.quarry') or rest.startswith('status.mining'): return 'logistics'
        if seg[0] in('range_length','range_width'): return None
        return 'atomics'
    if pre in ATOM_PREFIX: return 'atomics'
    if pre in LOG_PREFIX: return 'logistics'
    if pre=='' and seg[0]=='crane': return 'atomics'
    if pre in('radiation','plant_systems.fuel_cycle') or (pre=='plant_systems' and seg[0]=='fuel_cycle'): return 'atomics'
    if pre=='industrial_dispatch': return 'logistics'
    if pre=='plant_systems.fuel_cycle': return 'atomics'
    if pre in('control_rod_link','control_rod_controller'): return 'atomics'
    if pre=='death.attack' and 'radiation' in rest: return 'atomics'
    if pre=='gui.assembly.exception' and re.match(r'(converter|torpedo)',rest): return 'atomics'
    if pre=='facility' and seg[0] in('rbmk','fusion'): return 'atomics'
    if pre=='display' and re.match(r'(reactor_|rod_|crane_|pool_|fusion_|converter_|console_|cascade_)',rest): return 'atomics'
    if pre=='jei' and seg[0] in('coil_winder',) or (pre=='jei' and rest in('note.coil_winder','note.converter','note.stands','note.rpm_tolerance','note.centrifuge_stages','note.spent_control','note.spent_divertor')): return 'atomics'
    if pre=='process' and rest in('recipe.enrich_uranium','recipe.enrich_compact_uranium'): return 'atomics'
    if pre=='plant_systems' and rest in('status.extracting','status.vitrifying'): return 'atomics'
    if pre=='production' and rest in('limit.stages','limit.vacuum','limit.cryogenic','limit.magnets','limit.maintenance','limit.safety','limit.reactor_load','limit.upright','limit.tap'): return 'atomics'
    if pre=='jei':
        n=seg[0]
        if n in('vitrification','rotary_kiln','rolling_mill','large_pressing','cubic_pressing','gas_centrifuge','fusion_service','irradiation_products','solvent_extraction'): return 'atomics'
        if n in('arm_assembly','arm_step','industrial_delivery'): return 'logistics'
        if n=='note':
            n2=seg[1]
            if n2.startswith(('vitrification','spent_fuel','rotary_kiln','rolling_mill','large_pre','cubic','gas_centrifuge','fusion','irradiat','solvent')): return 'atomics'
            if n2 in('arm_assembly','industrial_delivery'): return 'logistics'
    if pre=='subtitles' and seg[0]=='machine':
        n=seg[1]
        return {'fusion_drive':'atomics','fusion_crane':'atomics','crane_drive':'atomics','centrifuge':'atomics','cubic_press':'atomics','converter':'atomics','waste_melter':'atomics','solvent_extractor':'atomics','quarry':'logistics','dispatch':'logistics'}.get(n)
    if pre=='subtitles' and seg[0]=='relay': return 'logistics'
    if pre=='display' and seg[0] in('dispatch_completed','dispatch_order'): return 'logistics'
    return None

ref=re.compile(NS+r':((?:block/|item/)?)([a-z0-9_/]+)')
key=re.compile(r'^(?:[a-z_]+\.)?'+NS+r'\.(.*)$')
def addon_of_stem(stem):
    parts=stem.split('_'); 
    for n in range(len(parts),0,-1):
        i='_'.join(parts[:n])
        if i in allids: return allids[i]
def tree(root):
    for d,_,fs in os.walk(root):
        for f in fs: yield os.path.join(d,f)
def strings(path):
    if path.endswith('.nbt'):
        try:
            import nbtlib
            f=nbtlib.load(path)
            out=[]
            def walk(x):
                if isinstance(x,dict): [walk(v) for v in x.values()]
                elif isinstance(x,list): [walk(v) for v in x]
                elif isinstance(x,str): out.append(x)
            walk(f); return out
        except Exception: return []
    if path.endswith(('.json','.mcmeta')):
        try: return [open(path,encoding='utf-8').read()]
        except Exception: return []
    return []
addonfiles={}
for a in ids:
    base=f'{R}/{a}/src/main/resources/'
    for p in tree(base): addonfiles[os.path.relpath(p,base)]=a
# lang ownership rules (shared with the splitter): any core key owned by an addon is a violation
def lang_viol(rel,path):
    return [k for k in json.load(open(path,encoding='utf-8')) if owner(k)]
EXEMPT=set()
def bare(x):
    if isinstance(x,dict):
        for k,v in x.items(): yield k; yield from bare(v)
    elif isinstance(x,list):
        for v in x: yield from bare(v)
    elif isinstance(x,str): yield x
viol=[]
base=R+'/src/main/resources/'
for p in tree(base):
    rel=os.path.relpath(p,base)
    parts=rel.split('/')
    if len(parts)>3 and parts[0] in('assets','data') and parts[1]==NS:
        stem=parts[-1].split('.')[0]
        if parts[2] in('blockstates','loot_table','models','textures','recipe','advancement') or rel.endswith('.nbt'):
            a=addon_of_stem(stem) if (parts[2] in('blockstates','loot_table','recipe') or parts[3:4]==['item'] or (parts[2]=='models' and len(parts)==5)) else None
            if a and not rel.startswith('data/'+NS+'/advancement/guide') : viol.append((rel,'file named for '+a+' id'))
    if rel.endswith('/lang/en_us.json') or rel.endswith('/lang/ja_jp.json') or rel.endswith('/lang/zh_cn.json'):
        for k in lang_viol(rel,p): viol.append((rel,'lang '+k))
        continue
    if rel in EXEMPT: continue
    if rel.endswith('.json') and '/lang/' not in rel and 'mixins' not in rel:
        try:
            for t in set(bare(json.load(open(p,encoding='utf-8')))):
                if t in allids: viol.append((rel,'bare id '+t+' ('+allids[t]+')'))
        except Exception: pass
    for s in strings(p):
        for m in ref.finditer(s):
            path=m.group(2); first=path.split('/')[0]
            a=allids.get(first) if not m.group(1) else None
            if m.group(1):
                f1=('assets/%s/models/%s%s.json'%(NS,m.group(1),path),'assets/%s/textures/%s%s.png'%(NS,m.group(1),path))
                a=next((addonfiles[f] for f in f1 if f in addonfiles),None)
                if not a: a=allids.get(path) 
            if a: viol.append((rel,'ref '+m.group(0)+' ('+a+')'))
        if p.endswith('.nbt') and False: pass
seen=set()
for v in viol:
    if v not in seen: seen.add(v); print(*v)
print(len(seen),'violations')
sys.exit(1 if seen else 0)
