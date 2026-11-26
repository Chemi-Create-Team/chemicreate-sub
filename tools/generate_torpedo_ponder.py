#!/usr/bin/env python3
"""Generate the rail-side steelworks Ponder template."""
from pathlib import Path

import generate_petrochemical_ponder as serializer
from generate_petrochemical_ponder import Structure

serializer.OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/ponder/plant'
mod = 'create_refinery_n_reactors:'
scene = Structure(13, 9)
for x in range(1, 12):
    scene.block(x, 1, 6, 'create:track', shape='xo', turn='false', waterlogged='false')
for x in (3, 7):
    scene.block(x, 2, 6, 'create:small_bogey', axis='x', waterlogged='false')
for x in range(2, 9):
    scene.block(x, 3, 6, 'create:metal_girder')
for x in range(3, 8):
    scene.block(x, 4, 6, mod+'torpedo_tank', axis='x', bottom=str(x==3).lower(), top=str(x==7).lower(), shape='plain')
scene.block(5, 4, 5, 'create:portable_fluid_interface', facing='north')
scene.block(5, 4, 2, 'create:portable_fluid_interface', facing='south')
scene.block(5, 4, 1, mod+'stainless_steel_pipe', north='true', south='true', east='false', west='false', up='false', down='false', waterlogged='false')
scene.block(2, 1, 8, 'create:track_station', assembling='false', waterlogged='false')
scene.block(8, 3, 3, 'create:blaze_burner', blaze='seething')
scene.block(8, 4, 3, 'create:basin')
scene.block(8, 6, 3, 'create:mechanical_press', axis='x', waterlogged='false')
scene.block(11, 4, 3, 'create:basin')
scene.block(11, 6, 3, 'create:mechanical_press', axis='x', waterlogged='false')
scene.block(11, 4, 4, mod+'stainless_steel_pipe', north='true', south='true', east='false', west='false', up='false', down='false', waterlogged='false')
scene.save('torpedo_car')
