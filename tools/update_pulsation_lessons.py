#!/usr/bin/env python3
"""Normalize authored teaching structures after replacing cube receivers with axial tubes.

This changes Ponder assets only. Game-world save migration is deliberately absent.
"""
from pathlib import Path
import gzip
import nbtlib

NS='create_refinery_n_reactors:'
OFFSETS=((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))


def normalize(path):
    scene=nbtlib.load(path);palette=scene.get('palette',[]);blocks=scene.get('blocks',[])
    cells={tuple(int(n) for n in b['pos']):b for b in blocks};changed=False
    def state(at):
        b=cells.get(at);return palette[int(b['state'])] if b is not None else None
    for at,b in cells.items():
        old=state(at);name=str(old['Name']);replacement=None
        if name==NS+'ultra_high_pressure_pipe':
            replacement=nbtlib.Compound(old);replacement['Name']=nbtlib.String(NS+'stainless_steel_pipe')
            if 'nbt' in b:b['nbt']['id']=nbtlib.String(NS+'stainless_steel_pipe')
        elif name in (NS+'liquid_tank',NS+'water_tank'):
            replacement=nbtlib.Compound(old);replacement['Name']=nbtlib.String('create:fluid_tank')
        elif name==NS+'gas_receiver':
            properties=old.get('Properties',{})
            if 'axis' in properties:continue
            replacement=nbtlib.Compound(old);properties=nbtlib.Compound(properties)
            stacked=any(state((at[0],at[1]+dy,at[2])) is not None and str(state((at[0],at[1]+dy,at[2]))['Name'])==name for dy in (-1,1))
            if stacked:
                replacement['Name']=nbtlib.String(NS+'pressure_tank')
                if 'nbt' in b:b['nbt']['id']=nbtlib.String(NS+'pressure_tank')
            else:
                axis='y'
                for delta in OFFSETS:
                    neighbor=state(tuple(a+d for a,d in zip(at,delta)))
                    if neighbor is not None and any(word in str(neighbor['Name']) for word in ('pipe','pump','compressor','fuel_cell')):
                        axis='xyz'[next(i for i,d in enumerate(delta) if d)];break
                properties['axis']=nbtlib.String(axis);properties['shape']=nbtlib.String('plain')
            replacement['Properties']=properties
        if replacement is None:continue
        try:index=palette.index(replacement)
        except ValueError:index=len(palette);palette.append(replacement)
        b['state']=nbtlib.Int(index);changed=True
    if changed:
        used=sorted({int(b['state']) for b in blocks});remap={old:new for new,old in enumerate(used)}
        scene['palette']=nbtlib.List[nbtlib.Compound]([palette[index] for index in used])
        for b in blocks:b['state']=nbtlib.Int(remap[int(b['state'])])
        scene.save(path,gzipped=True);path.write_bytes(gzip.compress(gzip.decompress(path.read_bytes()),mtime=0))
    return changed


if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]
    paths=list((root/'src/main/resources/assets/create_refinery_n_reactors/ponder').rglob('*.nbt'))+list((root/'tools/data/manufacturing').glob('*.nbt'))
    for path in paths:
        if normalize(path):print('Updated:',path.relative_to(root))
