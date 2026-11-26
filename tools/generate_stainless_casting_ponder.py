#!/usr/bin/env python3
"""Generate the stainless mixing, pumping and casting Ponder template."""
from pathlib import Path

import generate_petrochemical_ponder as serializer
from generate_petrochemical_ponder import Structure

serializer.OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/ponder/plant'
mod = 'create_refinery_n_reactors:'
scene = Structure(12, 8)
scene.block(3, 2, 5, 'create:blaze_burner', blaze='seething')
scene.block(3, 3, 5, 'create:basin', facing='down')
scene.block(3, 5, 5, 'create:mechanical_mixer')
scene.block(3, 5, 6, 'create:cogwheel', axis='y', waterlogged='false')
scene.block(3, 6, 6, 'create:creative_motor', facing='down')
for x in (4, 5, 7, 8):
    scene.block(x, 3, 5, mod+'stainless_steel_pipe', north='false', south='false',
                east='true', west='true', up='false', down='false', waterlogged='false')
scene.block(6, 3, 5, mod+'stainless_steel_pump', facing='east', waterlogged='false')
scene.block(6, 2, 5, 'create:cogwheel', axis='x', waterlogged='false')
scene.block(7, 2, 5, 'create:creative_motor', facing='west')
scene.block(9, 3, 5, 'create:basin', facing='down')
scene.block(9, 5, 5, 'create:mechanical_press', facing='east')
scene.block(10, 5, 5, 'create:creative_motor', facing='west')
scene.block(9, 2, 5, 'minecraft:hopper', facing='down', enabled='true')
scene.block(9, 1, 5, 'minecraft:chest', facing='south', type='single', waterlogged='false')
scene.save('stainless_casting')
