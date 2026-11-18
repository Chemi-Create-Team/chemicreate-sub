#!/usr/bin/env python3
"""A one-wide, three-layer separator with the same ports as the playable drum."""
from generate_reactor_assets import NS
from generate_petrochemical_ponder import Structure, string, named, integer

s = Structure(7, 7)
for y in (2, 3, 4):
    tags = [string('id', NS+'ammonia_separator'), string('Front', 'north')]
    if y == 2: tags += [named(3, 'Size', integer(1)), named(3, 'Height', integer(3))]
    else: tags += [named(11, 'Controller', b''.join(integer(v) for v in (3, 2, 2, 2)))]
    s.block(2, y, 2, NS+'ammonia_separator', nbt=tags, bottom=str(y == 2).lower(), top=str(y == 4).lower(), shape='plain', facing='north')


def pipe(x, y, z, *sides):
    s.block(x, y, z, 'create:fluid_pipe', **{d: str(d in sides).lower() for d in ('north', 'south', 'east', 'west', 'up', 'down')}, waterlogged='false')


pipe(3, 4, 2, 'east', 'west')
s.block(4, 4, 2, 'create:mechanical_pump', facing='west', waterlogged='false')
pipe(5, 4, 2, 'east', 'west')
s.block(6, 4, 2, 'create:fluid_tank', bottom='true', top='true', shape='window')
s.block(4, 4, 3, 'create:cogwheel', axis='x', waterlogged='false')
s.block(3, 4, 3, 'create:creative_motor', facing='east')
pipe(2, 5, 2, 'up', 'down')
pipe(2, 1, 2, 'up', 'down')
s.save('ammonia_separator')
