#!/usr/bin/env python3
"""Build the three-row opposed compressor Ponder structure."""
import struct
from generate_petrochemical_ponder import Structure, NS, named, string

scene = Structure(11, 6)
for x in range(1, 10):
    for z in range(1, 10):
        if x in (3, 7) and 1 <= z <= 6:
            continue
        scene.block(x, 1, z, 'minecraft:smooth_stone')

for z in (4, 5, 6):
    scene.block(5, 2, z, NS + 'reciprocating_compressor',
                opposed='true', window='false', face='floor', facing='north', waterlogged='false')
engine_pos = (5 << 38) | (6 << 12) | 2
scene.block(5, 2, 7, NS + 'reciprocating_drive_shaft', axis='z', waterlogged='false',
            nbt=[string('id', NS + 'reciprocating_drive_shaft'), named(4, 'Engine', struct.pack('>q', engine_pos))])
scene.block(5, 2, 8, 'create:creative_motor', facing='north')


def pipe(x, y, z, *sides):
    scene.block(x,y,z,NS + 'stainless_steel_pipe',
                **{d: str(d in sides).lower() for d in ('north','south','east','west','up','down')},
                waterlogged='false')


for x in (3, 7):
    pipe(x,1,1,'north','south')
    pipe(x,1,2,'north','up')
    pipe(x,2,2,'up','down')
    scene.pump(x,3,2,'up')
    pipe(x,4,2,'down','south')
    pipe(x,4,3,'north','south')
    for z in (4, 5, 6):
        scene.block(x, 2, z, NS + 'reciprocating_cylinder')
        pipe(x,3,z,'up','down')
        pipe(x,4,z,'north','down',*(['south'] if z < 6 else []))
        scene.pump(x, 1, z, 'down')
scene.save('reciprocating_compressor')
