#!/usr/bin/env python3
"""A two-wide ordinary vessel, native pipes and a Create tank; no new mixing machine."""
from generate_petrochemical_ponder import Structure, string, named, integer, DIRECTIONS
from generate_reactor_assets import A, NS
import generate_petrochemical_ponder as p


def generate():
    s = Structure(12, 6)
    for y in (1, 2):
        for x in (2, 3):
            for z in (3, 4):
                data = [string('id', NS + 'lidded_vessel'), named(1, 'Windows', b'\x01')]
                data += [named(3, 'Size', integer(2)), named(3, 'Height', integer(2))] if (x, y, z) == (2, 1, 3) else [named(11, 'Controller', b''.join(integer(v) for v in (3, 2, 1, 3)))]
                s.block(x, y, z, NS + 'lidded_vessel', nbt=data, drive='false', axis='y', open='false', facing='north', bottom=str(y == 1).lower(),
                    hatch=('s' if z == 3 else 'n') + ('e' if x == 2 else 'w') if y == 2 else 'none', ladder='false',
                    north=str(z == 3).lower(), south=str(z == 4).lower(), west=str(x == 2).lower(), east=str(x == 3).lower())
    for x in (4, 6, 7, 8):
        s.block(x, 1, 3, 'create:fluid_pipe', waterlogged='false', **{side: str(side in ('east', 'west')).lower() for side in DIRECTIONS})
    s.block(5, 1, 3, 'create:mechanical_pump', facing='east', waterlogged='false')
    s.block(5, 1, 4, 'create:cogwheel', axis='x', waterlogged='false');s.block(4, 1, 4, 'create:creative_motor', facing='east')
    for y in (1, 2): s.block(9, y, 3, 'create:fluid_tank', bottom=str(y == 1).lower(), top=str(y == 2).lower(), shape='window')
    s.block(9, 1, 4, 'create:display_link', facing='south', powered='false')
    for x in range(7, 10): s.block(x, 1, 6, 'create:display_board', facing='north', up='false', down='false', left=str(x != 7).lower(), right=str(x != 9).lower())
    p.OUT = A / 'ponder/plant';s.save('chemical_mixtures')


if __name__ == '__main__': generate()
