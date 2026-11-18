#!/usr/bin/env python3
"""Small material-only Create conduit family, not a duplicate fluid network."""
from generate_quality_assets import A, ROOT, NS, write, png
from minecraft_style import pixels,box

for name,color,collar in [('reinforced_pipe',(135,148,145),'brass')]:
    png('block/transport/'+name,pixels(name,color))
    textures={'body':NS+'block/transport/'+name,'collar':NS+'block/chemical/'+collar,'particle':NS+'block/transport/'+name}
    def model(part,e):write(A/'models/block'/name/(part+'.json'),{'parent':'minecraft:block/block','textures':textures,'elements':e})
    e=[];box(e,[4,4,4],[12,12,12],'body');model('core',e)
    inventory=list(e);parts=[{'apply':{'model':NS+'block/'+name+'/core'}}]
    for direction,a,b in [('north',[4,4,0],[12,12,4]),('south',[4,4,12],[12,12,16]),('west',[0,4,4],[4,12,12]),('east',[12,4,4],[16,12,12]),('down',[4,0,4],[12,4,12]),('up',[4,12,4],[12,16,12])]:
        e=[];box(e,a,b,'body')
        ca=[max(0,c-1) for c in a];cb=[min(16,c+1) for c in b]
        axis=next(i for i in range(3) if a[i]==0 or b[i]==16)
        ca[axis]=2 if a[axis]==0 else 12;cb[axis]=4 if a[axis]==0 else 14
        box(e,ca,cb,'collar');model(direction,e)
        parts.append({'when':{direction:'true'},'apply':{'model':NS+'block/'+name+'/'+direction}})
        if direction in ('north','south'):inventory+=e
    model('inventory',inventory)
    write(A/'models/item'/(name+'.json'),{'parent':NS+'block/'+name+'/inventory'})
    write(A/'blockstates'/(name+'.json'),{'multipart':parts})
    data=ROOT/'src/main/resources/data/create_refinery_n_reactors'
    write(data/'loot_table/blocks'/(name+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    write(data/'recipe'/(name+'.json'),{'type':'minecraft:crafting_shaped','category':'misc','pattern':[' I ','IPI',' I '],
        'key':{'I':{'tag':'c:plates/iron'} if name=='reinforced_pipe' else {'item':'minecraft:brick'},'P':{'item':'create:fluid_pipe' if name=='reinforced_pipe' else NS+'reinforced_pipe'}},'result':{'id':NS+name,'count':2 if name=='reinforced_pipe' else 1}})
    # Explicit pickaxe tags for the new own IDs; don't rewrite foreign tag files.
tag=ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
import json
d=json.loads(tag.read_text());d['values']=list(dict.fromkeys(d['values']+[NS+'reinforced_pipe']));write(tag,d)
print('Generated the reinforced native Create pipe, its recipe and loot.')
