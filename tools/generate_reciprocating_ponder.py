#!/usr/bin/env python3
"""Build the three-row opposed compressor Ponder structure."""
import struct
from generate_petrochemical_ponder import Structure, NS, named, string

scene = Structure(11, 5)
for x in range(1, 10):
    for z in range(1, 10):
        if x in (3, 7) and 4 <= z <= 6:
            continue
        scene.block(x, 1, z, 'minecraft:smooth_stone')

for z in (4, 5, 6):
    scene.block(5, 2, z, NS + 'reciprocating_compressor',
                opposed='true', window='false', face='floor', facing='north', waterlogged='false')
engine_pos = (5 << 38) | (6 << 12) | 2
scene.block(5, 2, 7, NS + 'reciprocating_drive_shaft', axis='z', waterlogged='false',
            nbt=[string('id', NS + 'reciprocating_drive_shaft'), named(4, 'Engine', struct.pack('>q', engine_pos))])
scene.block(5, 2, 8, 'create:creative_motor', facing='north')


for x in (3, 7):
    for z in (4, 5, 6):
        scene.block(x, 2, z, NS + 'reciprocating_cylinder')
        scene.pump(x, 3, z, 'down')
        scene.pump(x, 1, z, 'down')
scene.save('reciprocating_compressor')
