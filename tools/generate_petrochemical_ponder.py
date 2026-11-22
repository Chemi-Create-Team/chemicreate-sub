#!/usr/bin/env python3
"""Ponder structure writer: vanilla structure NBT for the model-scale plant scenes, and the shared block helpers."""
from pathlib import Path
import gzip
import struct

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/create_refinery_n_reactors/ponder/chemistry'
NS = 'create_refinery_n_reactors:'
DIRECTIONS = {'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0),
    'down': (0, -1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1)}




def text(value):
    data = value.encode('utf8')
    return struct.pack('>H', len(data)) + data


def named(kind, name, data): return bytes([kind]) + text(name) + data
def integer(value): return struct.pack('>i', value)
def string(name, value): return named(8, name, text(value))
def ints(name, values): return named(9, name, b'\x03' + integer(len(values)) + b''.join(integer(v) for v in values))
def compound(fields): return b''.join(fields) + b'\x00'
def compounds(name, values): return named(9, name, b'\x0a' + integer(len(values)) + b''.join(values))


class Structure:
    def __init__(self, width, height, floor=True):
        self.size = width, height, width
        self.states, self.blocks, self.positions = [], [], {}
        if floor:
            for x in range(width):
                for z in range(width):
                    self.block(x, 0, z, 'minecraft:smooth_stone')

    def block(self, block_x, block_y, block_z, name, nbt=None, **properties):
        """nbt: the encoded fields of the block entity's data, for blocks whose look depends on it."""
        assert all(0 <= c < limit for c, limit in zip((block_x, block_y, block_z), self.size))
        assert (block_x, block_y, block_z) not in self.positions, (block_x, block_y, block_z)
        self.positions[(block_x, block_y, block_z)] = len(self.blocks)
        self.blocks.append(self.encode(block_x, block_y, block_z, name, nbt, properties))

    def replace(self, block_x, block_y, block_z, name, nbt=None, **properties):
        """Puts a block where one already stands, such as the floor."""
        self.blocks[self.positions[(block_x, block_y, block_z)]] = self.encode(block_x, block_y, block_z, name, nbt, properties)

    def encode(self, block_x, block_y, block_z, name, nbt, properties):
        key = name, tuple(sorted(properties.items()))
        if key not in self.states:
            self.states.append(key)
        fields = [ints('pos', (block_x, block_y, block_z)), named(3, 'state', integer(self.states.index(key)))]
        if nbt is not None: fields.append(named(10, 'nbt', compound(nbt)))
        return compound(fields)

    def machine(self, x, y, z, name, **extra):
        if name=='cooler_radiator': self.block(x,y,z,NS+name,facing='north')
        else: self.block(x, y, z, NS + name, facing='north', lit='false', **extra)

    def pump(self, x, y, z, facing):
        self.block(x, y, z, 'create:mechanical_pump', facing=facing, waterlogged='false')

    def pipe(self, x, y, z, *sides):
        self.block(x, y, z, NS + 'ceramic_pipe', **{side: str(side in sides).lower() for side in DIRECTIONS}, waterlogged='false')

    def filter(self, x, y, z, facing):
        self.block(x, y, z, 'create:smart_fluid_pipe', face='floor', facing=facing, waterlogged='false')

    def burner(self, x, y, z, heat='kindled'):
        self.block(x, y, z, 'create:blaze_burner', blaze=heat)

    def reactor(self, x, y, z, kind, sections, burners=(), heat='kindled', windows=False):
        """
        A stirred stainless tank, two or more layers high, over its stirrer under the bottom centre; a cogwheel beside the stirrer, on a creative motor
        in the floor, turns it as a mechanical mixer is driven.
        """
        if kind == 'centrifuge':
            self.centrifuge(x, y, z, sections)
            return
        assert kind == 'stirred', kind
        sections = max(2, sections)
        burners = tuple((-1, -1) if pos in ((0, 0), (0, -1)) else pos for pos in burners)
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if (dx, dz) == (0, 0): self.block(x, y - 1, z, NS + 'vessel_stirrer')
                elif (dx, dz) == (0, -1): self.block(x, y - 1, z - 1, 'create:cogwheel', axis='y', waterlogged='false')
                elif (dx, dz) in burners: self.burner(x + dx, y - 1, z + dz, heat)
                else: self.block(x + dx, y - 1, z + dz, 'create:andesite_casing')
        motor = (x, y - 2, z - 1)
        if motor in self.positions: self.replace(*motor, 'create:creative_motor', facing='up')
        else: self.block(*motor, 'create:creative_motor', facing='up')
        origin = (x - 1, y, z - 1)
        for i in range(sections):
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    pos = (x + dx, y + i, z + dz)
                    nbt = [string('id', NS + 'lidded_vessel')]
                    nbt += [named(3, 'Size', integer(3)), named(3, 'Height', integer(sections))] if pos == origin else [named(11, 'Controller', b''.join(integer(c) for c in (3, *origin)))]
                    if pos == origin and windows: nbt.append(named(3, 'Windows', integer(1)))
                    if pos == origin: nbt.append(named(1, 'Stirrer', b'\x01'))
                    hatch = 'full' if i == sections - 1 and (dx, dz) == (0, 0) else 'none'
                    self.block(*pos, NS + 'lidded_vessel', nbt=nbt, drive='true', axis='y', open='false', facing='north', hatch=hatch, ladder=str((dx, dz) == (-1, -1)).lower(), bottom=str(i == 0).lower(),
                               north=str(dz == -1).lower(), south=str(dz == 1).lower(), west=str(dx == -1).lower(), east=str(dx == 1).lower())

    def tower(self, x, y, z, layers=3, burners=((-1, -1),), heat='kindled', load=None):
        """A reaction tower round the given bottom centre with a reaction tray in every layer, over burners; load: an item lying on each tray."""
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if (dx, dz) in burners: self.burner(x + dx, y - 1, z + dz, heat)
                else: self.block(x + dx, y - 1, z + dz, 'create:andesite_casing')
        origin = (x - 1, y, z - 1)
        for i in range(layers):
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    pos = (x + dx, y + i, z + dz)
                    nbt = [string('id', NS + 'reaction_tower')]
                    nbt += [named(3, 'Size', integer(3)), named(3, 'Height', integer(layers))] if pos == origin else [named(11, 'Controller', b''.join(integer(c) for c in (3, *origin)))]
                    if (dx, dz) == (0, 0):
                        nbt.append(named(1, 'Basket', b'\x01'))
                        if i == 0: nbt.append(string('Front', 'north'))
                        if load: nbt.append(named(10, 'TrayLoad', compound([string('id', load), named(3, 'count', integer(8))])))
                    side = (dx == 0) != (dz == 0)
                    self.block(*pos, NS + 'reaction_tower', nbt=nbt, north=str(dz == -1).lower(), south=str(dz == 1).lower(), west=str(dx == -1).lower(),
                               east=str(dx == 1).lower(), bottom=str(i == 0).lower(), top=str(i == layers - 1).lower(), shape='plain', window=str(side).lower())

    def centrifuge(self, x, y, z, count, columns=4):
        for i in range(count):
            dx, dz = i % columns, i // columns
            self.block(x+dx, y-1, z+dz, 'create:creative_motor', facing='up')
            for half, dy in (('lower', 0), ('upper', 1)):
                self.block(x+dx, y+dy, z+dz, NS+'gas_centrifuge', half=half, lit='false',
                    north=str(i>=columns and half=='upper').lower(), south=str(i+columns<count and half=='upper').lower(),
                    west=str(dx>0 and half=='upper').lower(), east=str(dx<columns-1 and i+1<count and half=='upper').lower())

    def tank(self, x, y, z):
        if y > 1:
            self.block(x, y-1, z, 'minecraft:smooth_stone')
        self.block(x, y, z, 'create:fluid_tank')

    def save(self, name):
        palette = []
        for block_name, properties in self.states:
            fields = [string('Name', block_name)]
            if properties:
                fields.append(named(10, 'Properties', compound([string(k, v) for k, v in properties])))
            palette.append(compound(fields))
        root = compound([named(3, 'DataVersion', integer(3955)), ints('size', self.size),
            compounds('palette', palette), compounds('blocks', self.blocks), compounds('entities', [])])
        OUT.mkdir(parents=True, exist_ok=True)
        path = OUT / (name + '.nbt')
        payload = bytearray(gzip.compress(named(10, '', root), mtime=0))
        # Python versions disagree on the gzip OS byte even with a fixed mtime.
        # Keep generated Ponder structures byte-for-byte stable across environments.
        if len(payload) >= 10 and payload[:2] == b'\x1f\x8b': payload[9] = 255
        path.write_bytes(payload)
        from update_pulsation_lessons import normalize
        normalize(path)
        print(f'Generated {path.relative_to(ROOT)}: {len(self.blocks)} blocks.')
