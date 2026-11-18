#!/usr/bin/env python3
"""Condenser connected to the current ten-section LP unit, built natively by Ponder."""
from pathlib import Path
import generate_petrochemical_ponder as ponder


def generate():
    scene=ponder.Structure(25,11)
    for depth in range(7):
        for dx in range(-1,2):
            for dy in range(-1,2):
                name='condenser_cooling_inlet' if (depth,dx,dy)==(0,-1,0) else 'condenser_cooling_outlet' if (depth,dx,dy)==(0,1,0) else 'condenser_waterbox' if (depth,dx,dy)==(0,0,0) else 'condenser_tube_bundle' if 0<depth<6 and dx==dy==0 else 'condenser_shell'
                facing='west' if name=='condenser_cooling_inlet' else 'east' if name=='condenser_cooling_outlet' else 'north'
                scene.block(8+dx,5+dy,15+depth,ponder.NS+name,**({'facing':facing} if name in ('condenser_cooling_inlet','condenser_cooling_outlet','condenser_waterbox') else {}))
    for x in (6,10):scene.pipe(x,5,15,'east','west')
    for x in (5,11):
        scene.pump(x,5,15,'east')
        scene.block(x,5,16,'create:cogwheel',axis='x',waterlogged='false')
        scene.block(x-1,5,16,'create:creative_motor',facing='east')
    for x in (4,12):scene.block(x,5,15,'create:fluid_tank',bottom='true',top='true',shape='window')
    scene.block(12,5,10,'create:shaft',axis='x')
    scene.block(24,10,24,'minecraft:polished_andesite')
    old=ponder.OUT
    ponder.OUT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/create_refinery_n_reactors/ponder/plant'
    try:scene.save('condenser_loop')
    finally:ponder.OUT=old


if __name__=='__main__':generate()
