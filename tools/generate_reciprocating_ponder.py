#!/usr/bin/env python3
"""Build the assembled opposed compressor Ponder structure."""
from generate_petrochemical_ponder import Structure, NS

scene = Structure(9, 5)
scene.block(4, 2, 3, NS + 'reciprocating_compressor',
            opposed='true', window='false', face='floor', facing='north', waterlogged='false')
scene.block(4, 2, 4, NS + 'reciprocating_drive_shaft', axis='z', waterlogged='false')
scene.block(4, 2, 5, 'create:creative_motor', facing='north')

for x in range(1, 8):
    for z in range(2, 6):
        if z == 3 and x in (2, 6):
            continue
        scene.block(x, 1, z, 'minecraft:smooth_stone')
for x in (2, 6):
    scene.block(x, 2, 3, NS + 'reciprocating_cylinder')
    scene.pump(x, 3, 3, 'down')
    scene.pump(x, 1, 3, 'down')
scene.save('reciprocating_compressor')
