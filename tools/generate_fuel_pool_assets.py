"""Fuel pool rack, its dedicated pixel art and an open-basin Ponder structure."""
import json
from pathlib import Path
from PIL import Image
from minecraft_style import grain
from generate_petrochemical_ponder import Structure, OUT

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def models():
    image = Image.new('RGBA', (16, 16))
    ribs = (0, 7, 8, 15)
    for y in range(16):
        for x in range(16):
            rib = x in ribs or y in ribs
            color = (105, 137, 130) if rib else (51, 65, 66)
            light = (15 if x in (0, 7) or y in (0, 7) else -12 if rib else 0) + grain('fuel_pool_rack', x, y, 1.2)
            if x in ribs and y in ribs: color, light = (167, 178, 169), 0
            image.putpixel((x, y), tuple(max(0, min(255, round(c * (1 + light / 100)))) for c in color) + (255,))
    folder = A / 'textures/block/reactor/rbmk'
    folder.mkdir(parents=True, exist_ok=True)
    image.save(folder / 'fuel_pool_rack.png')
    def box(a, b):
        x, y, z = a; X, Y, Z = b
        uv = {'up': [x, z, X, Z], 'down': [x, z, X, Z], 'north': [x, 16-Y, X, 16-y], 'south': [x, 16-Y, X, 16-y], 'east': [z, 16-Y, Z, 16-y], 'west': [z, 16-Y, Z, 16-y]}
        return {'from': a, 'to': b, 'faces': {face: {'texture': '#rack', 'uv': coords} for face, coords in uv.items()}}
    elements = [box([0, 0, 0], [16, 2, 16])]
    for i in (0, 5, 10, 15):
        end = min(i + 1, 16)
        elements.extend([box([i, 2, 0], [end, 4, 16]), box([0, 2, i], [16, 4, end])])
    write(A / 'models/block/rbmk/fuel_pool.json', {'parent': 'minecraft:block/block', 'textures': {'rack': NS + 'block/reactor/rbmk/fuel_pool_rack', 'particle': NS + 'block/reactor/rbmk/fuel_pool_rack'}, 'elements': elements})
    write(A / 'blockstates/fuel_pool.json', {'variants': {'': {'model': NS + 'block/rbmk/fuel_pool'}}})
    write(A / 'models/item/fuel_pool.json', {'parent': NS + 'block/rbmk/fuel_pool'})


TEXT = {
 'block.create_refinery_n_reactors.fuel_pool': ('Fuel Pool Rack', '燃料プールラック'),
 'fuel_pool.create_refinery_n_reactors.rods': ('Fuel rods: %s fresh, %s spent / %s', '燃料棒 新%s・使用済%s／%s本'),
 'fuel_pool.create_refinery_n_reactors.water': ('Water %s / %s mB; %s °C', '冷却水 %s／%s mB・%s ℃'),
 'fuel_pool.create_refinery_n_reactors.fault': ('Check structure at %s, %s, %s', '構造を確認：%s, %s, %s'),
 'fuel_pool.create_refinery_n_reactors.status.size': ('Arrange 2–9 by 2–9 rack tiles', 'ラックを縦横2～9枚の長方形に並べてください'),
 'fuel_pool.create_refinery_n_reactors.status.wall': ('Enclose with steel blocks, 23 blocks tall', '鋼鉄ブロックで高さ23の壁を囲んでください'),
 'fuel_pool.create_refinery_n_reactors.status.blocked': ('Remove the obstruction above the racks', 'ラックの上22ブロックの障害物を除いてください'),
 'fuel_pool.create_refinery_n_reactors.status.unloaded': ('Waiting for the entire basin to load', '水槽全体のチャンク読込を待機中'),
 'fuel_pool.create_refinery_n_reactors.status.water': ('Low water: supply 22,000 mB per rack', '水位不足：ラック1枚につき22,000 mB以上必要'),
 'fuel_pool.create_refinery_n_reactors.status.hot': ('Hot water: recirculate below 90 °C', '水温上昇：90 ℃未満へ循環冷却してください'),
 'fuel_pool.create_refinery_n_reactors.status.cooling': ('Storing spent fuel; cooling water warming', '使用済み燃料を保管中・冷却水が昇温'),
 'fuel_pool.create_refinery_n_reactors.status.ready': ('Pool ready for fuel handling', '燃料プール使用可能'),
 'display.create_refinery_n_reactors.pool_fresh': ('Pool: fresh rods', '燃料プール：新燃料棒'),
 'display.create_refinery_n_reactors.pool_spent': ('Pool: spent rods', '燃料プール：使用済み燃料棒'),
 'display.create_refinery_n_reactors.pool_cells': ('Pool: rack tiles', '燃料プール：ラック枚数'),
 'display.create_refinery_n_reactors.pool_heat': ('Pool: residual heat (W)', '燃料プール：残留熱（W）'),
 'create_refinery_n_reactors.ponder.fuel_pool.header': ('An open spent fuel pool', '開放型の使用済み燃料プール'),
 'create_refinery_n_reactors.ponder.shared.fuel_pool.title': ('An open spent fuel pool', '開放型の使用済み燃料プール'),
 'create_refinery_n_reactors.ponder.shared.fuel_pool.structure': ('Lay 2–9 by 2–9 rack tiles. Enclose them in steel-block walls, 23 blocks high from the rack floor, leaving the 22 blocks above each rack empty.', 'ラックを縦横2～9枚の長方形に並べ、床と同じ高さから23段の鋼鉄ブロックで囲みます。ラックの上22ブロックは空けてください。'),
 'create_refinery_n_reactors.ponder.shared.fuel_pool.water': ('Supply water beneath the racks, at least 22,000 mB per rack.', 'ラック下面へ水を送り、1枚につき22,000 mB以上を確保します。'),
 'create_refinery_n_reactors.ponder.shared.fuel_pool.rods': ('Each rack stores four fuel assemblies. Use the crane to load and unload them.', 'ラック1枚に燃料棒を4本保管できます。クレーンで出し入れします。'),
 'create_refinery_n_reactors.ponder.shared.fuel_pool.cooling': ('Spent rods warm the coolant. Pump warm water through external cooling and return it below 90 °C. Low water or a broken wall stops loading without deleting stored rods.', '使用済み燃料の熱を外部の冷却設備へ循環して逃がします。水温90 ℃以上・水位不足・壁の破損で装荷が止まります。保管中の燃料は残ります。'),
 'create_refinery_n_reactors.ponder.shared.fuel_pool.display': ('Check rods and water with goggles. Send detailed readings through Display Links.', '燃料棒と水の状態はゴーグルで確認します。詳しい値はディスプレイリンクへ送れます。')
}


def recipes():
    write(D / 'recipe/fuel_pool.json', {'type': 'minecraft:crafting_shaped', 'pattern': ['S S', 'SSS', 'III'], 'key': {'S': {'item': 'create:iron_sheet'}, 'I': {'item': NS + 'steel_ingot'}}, 'result': {'id': NS + 'fuel_pool', 'count': 2}})


def generate():
    models()
    recipes()
    for language, index in [('en_us', 0), ('ja_jp', 1)]:
        path = A / 'lang' / (language + '.json')
        entries = json.loads(path.read_text(encoding='utf-8'))
        entries.update({key: value[index] for key, value in TEXT.items()})
        write(path, entries)
    s = Structure(9, 29)
    for x in range(2, 8):
        for z in range(2, 8):
            wall = x in (2, 7) or z in (2, 7)
            if wall:
                for y in range(4, 27): s.block(x, y, z, NS + 'steel_block')
            else: s.block(x, 4, z, NS + 'fuel_pool')
    def pipe(x, y, z, *connections):
        properties = {side: str(side in connections).lower() for side in ('north', 'south', 'east', 'west', 'up', 'down')}
        s.block(x, y, z, 'create:fluid_pipe', **properties, waterlogged='false')
    s.block(1, 1, 3, 'create:fluid_tank')
    s.pump(2, 1, 3, 'east')
    pipe(3, 1, 3, 'west', 'up')
    pipe(3, 2, 3, 'down', 'up')
    pipe(3, 3, 3, 'down', 'up')
    s.block(2, 1, 4, 'create:cogwheel', axis='x', waterlogged='false')
    s.block(1, 1, 4, 'create:creative_motor', facing='east')
    pipe(5, 3, 3, 'up', 'down')
    s.pump(5, 2, 3, 'down')
    pipe(5, 1, 3, 'up', 'east')
    s.block(6, 1, 3, 'create:fluid_tank')
    s.block(5, 2, 4, 'create:cogwheel', axis='y', waterlogged='false')
    s.block(5, 1, 4, 'create:creative_motor', facing='up')
    s.save('fuel_pool')
    target = A / 'ponder/nuclear/fuel_pool.nbt'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes((OUT / 'fuel_pool.nbt').read_bytes())
    (OUT / 'fuel_pool.nbt').unlink()


if __name__ == '__main__': generate()
