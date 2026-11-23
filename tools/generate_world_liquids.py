#!/usr/bin/env python3
"""World-liquid containers, carbonized wood and the two matching Ponder lessons."""
import hashlib
import json
import re
import struct
import zlib
from pathlib import Path

from generate_petrochemical_ponder import Structure, OUT

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
DATA = ROOT / 'src/main/resources/data'
NS = 'create_refinery_n_reactors:'
PREFIX = 'create_refinery_n_reactors.ponder.shared.'
REGISTRY = ROOT / 'src/main/java/net/createplant/registry'


def fluid_entries():
    source = '\n'.join(path.read_text() for path in REGISTRY.glob('*.java'))
    colors = {name: int(color, 16) for name, color in re.findall(
        r'new PlantFluid\([^";]*"([a-z_]+)",\s*(0x[\da-fA-F]+)', source)}
    colors.update({name: int(color, 16) for name, color in re.findall(
        r'stream\("([a-z_]+)",\s*(0x[\da-fA-F]+)', source)})
    colors.update({name: int(color, 16) for name, color in re.findall(
        r'liquid\("([a-z0-9_]+)",\s*(0x[0-9A-Fa-f]+),\s*\d+\)', source)})
    colors.update({name: int(color, 16) for name, color in re.findall(
        r'fluid\("([a-z_]+)",\s*(?:true|false),\s*-?\d+,\s*\d+,\s*(0x[\da-fA-F]+)', source)})
    colors.update({name: int(color, 16) for name, color in re.findall(
        r'slurry\("([a-z_]+)",\s*(0x[\da-fA-F]+)', source)})
    for name in re.findall(r'= stream\("([a-z_]+)"\)', source):
        colors[name] = (0xFF382E24 if 'crude' in name else 0xFFCAC3A0 if 'naphtha' in name
            else 0xFFDBD8BC if 'molten_' in name else 0xFFD9C77E if 'diesel' in name or 'hydrotreater' in name
            else 0xFF3A3026 if 'heavy_oil' in name else 0xFFB9A05A if 'hydrocracker' in name
            else 0xFF6F7357 if name == 'wastewater' else 0xBBD4E4EA if name == 'liquid_nitrogen'
            else 0xEE5FD38A if name == 'antifreeze' else 0xEEE6EEF0 if 'glycol' in name else 0xBBD3DFDB)
    hazards = {name: (acid == 'true', gas == 'true') for name, acid, gas in re.findall(
        r'entry\("([a-z_]+)",\s*[\d.]+,\s*[\d.]+,\s*(true|false),\s*(true|false)\)',
        (ROOT / 'src/main/java/net/createplant/transport/FluidHazards.java').read_text())}
    return [{'name': name, 'color': color, 'corrosive': hazards.get(name, (False, False))[0], 'gas': hazards.get(name, (False, False))[1]}
            for name, color in sorted(colors.items())]


def liquid_entries():
    return [entry for entry in fluid_entries() if not entry['gas']]


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def bucket_pixels(color=None, alloy=False, name='', stainless=False):
    """The vanilla bucket; a liquid takes the shading of vanilla water, below the inner rim, in its own colour."""
    template = json.loads((ROOT / 'tools/vanilla_bucket_template.json').read_text())
    mask = set(template['content_mask'])
    pixels = [tuple(value) for value in template['pixels']]
    if alloy or stainless:
        for i, pixel in enumerate(pixels):
            if pixel[3] and i not in mask:
                pixels[i] = steel(pixel[0], i // 16) if stainless else tinted(pixel[0])
    if color is not None:
        luma = lambda pixel: .299 * pixel[0] + .587 * pixel[1] + .114 * pixel[2]
        water = [tuple(pixel) for pixel in template['water']]
        surface = luma(max(set(water), key=water.count))
        # A few levels of difference keep liquids of the same colour apart.
        signature = hashlib.sha256(name.encode()).digest()
        channels = tuple(((color >> shift) & 255) + signature[k] % 5 - 2 for k, shift in enumerate((16, 8, 0)))
        for i, pixel in zip(template['content_mask'], water):
            if pixel[0] == pixel[1] == pixel[2]:
                pixels[i] = tinted(pixel[0]) if alloy else pixel
            else:
                shade = round(luma(pixel) - surface)
                pixels[i] = tuple(max(0, min(255, channel + shade)) for channel in channels) + (255,)
    return pixels


def tinted(value):
    """The corrosion-resistant alloy's cool grey for a vanilla bucket grey."""
    return (max(0, value - 12), min(255, value + 1), min(255, value + 7), 255)


def steel(value, y):
    """Stainless steel for a vanilla bucket grey, as the stainless ingot and sheet: neutral grey, a faint cool cast and brushed rows."""
    v = 40 + value * 0.58 - (8 if y % 2 else 0)
    return tuple(max(0, min(255, round(c))) for c in (v, v + 3, v + 9)) + (255,)


def bucket_image(name, color=None, alloy=False, stainless=False):
    path = ASSETS / 'textures/item' / f'{name}.png'
    png(path, bucket_pixels(color, alloy, name, stainless))
    write(ASSETS / 'models/item' / f'{name}.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})


def png(path, pixels):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    rows = b''.join(b'\0' + bytes(channel for pixel in pixels[y*16:(y+1)*16] for channel in pixel) for y in range(16))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


def wood_texture(kind, stage):
    pixels = [(0, 0, 0, 255)] * 256
    wood = ((78, 57, 35), (103, 76, 44), (124, 93, 54), (145, 111, 66))
    charcoal = ((21, 23, 23), (31, 34, 33), (43, 46, 44), (58, 60, 55))
    for y in range(16):
        for x in range(16):
            cluster = ((x // 3) * 3 + (y // 4) * 5) % 7
            burnt = cluster < stage * 2 + 1
            palette = charcoal if burnt else wood
            if kind == 'log_top':
                ring = max(abs(x - 7), abs(y - 7)) // 2
                index = (ring + x // 5) % 4
            elif kind == 'log_side':
                index = ((x // 2) * 3 + (y // 5)) % 4
            else:
                index = (y // 4 + x // 5) % 4
            pixels[y*16+x] = palette[index] + (255,)
    def line(x1, y1, x2, y2, color):
        for y in range(y1, y2+1):
            for x in range(x1, x2+1):
                pixels[y*16+x] = color + (255,)
    if kind == 'planks':
        for y in (3, 7, 11, 15):
            line(0, y, 15, y, (19, 22, 21) if stage > 1 else (48, 36, 24))
        for x, y in ((5, 0), (11, 4), (3, 8), (9, 12)):
            line(x, y, x, y+2, (18, 20, 20) if stage > 1 else (58, 43, 26))
    elif kind == 'log_side':
        for x, y in ((2, 2), (7, 6), (12, 10)):
            line(x, y, x, y+4, (16, 19, 19))
            line(x+1, y+2, x+2, y+2, (68, 68, 60) if stage > 1 else (153, 116, 71))
    return pixels


def carbonized_wood():
    variants = {}
    for stage in range(4):
        for kind in ('log_side', 'log_top', 'planks'):
            path = ASSETS / 'textures/block/charred_wood' / f'{kind}_{stage}.png'
            png(path, wood_texture(kind, stage))
        log = f'charred_wood/log_{stage}'
        plank = f'charred_wood/planks_{stage}'
        write(ASSETS / 'models/block' / f'{log}.json', {'parent': 'minecraft:block/cube_column', 'textures': {
            'end': NS + f'block/charred_wood/log_top_{stage}', 'side': NS + f'block/charred_wood/log_side_{stage}'}})
        write(ASSETS / 'models/block' / f'{plank}.json', {'parent': 'minecraft:block/cube_all',
              'textures': {'all': NS + f'block/charred_wood/planks_{stage}'}})
        for axis in ('x', 'y', 'z'):
            model = {'model': NS + 'block/' + log}
            if axis == 'x': model.update(x=90, y=90)
            elif axis == 'z': model.update(x=90)
            variants[f'axis={axis},logs=true,stage={stage}'] = model
            variants[f'axis={axis},logs=false,stage={stage}'] = {'model': NS + 'block/' + plank}
    write(ASSETS / 'blockstates/charred_wood.json', {'variants': variants})
    write(ASSETS / 'models/item/charred_wood.json', {'parent': NS + 'block/charred_wood/planks_3'})
    write(DATA / 'create_refinery_n_reactors/loot_table/blocks/charred_wood.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1,
            'conditions': [{'condition': 'minecraft:block_state_property', 'block': NS + 'charred_wood',
                            'properties': {'stage': '3'}}],
            'entries': [{'type': 'minecraft:item', 'name': 'minecraft:charcoal', 'functions': [
                {'function': 'minecraft:set_count', 'count': 4, 'conditions': [
                    {'condition': 'minecraft:block_state_property', 'block': NS + 'charred_wood', 'properties': {'logs': 'true'}}]}]}]}]})
    path = DATA / 'minecraft/tags/block/mineable/axe.json'
    tag = json.loads(path.read_text()) if path.exists() else {'replace': False, 'values': []}
    if NS + 'charred_wood' not in tag['values']:
        tag['values'].append(NS + 'charred_wood')
    write(path, tag)


CHINESE = {
    'aluminium_sulfate_solution': '硫酸铝溶液', 'antifreeze': '防冻液', 'brine': '盐水',
    'butanol': '丁醇', 'butyraldehyde': '丁醛', 'caustic_soda_solution': '氢氧化钠溶液',
    'cooled_hydrocracker_effluent': '冷却加氢裂化产物', 'cooled_hydrotreater_effluent': '冷却加氢精制产物',
    'cooled_olefins': '冷却烯烃混合物', 'dissolved_spent_fuel': '溶解乏燃料',
    'ethylene_dichloride': '二氯乙烷', 'ethylene_glycol': '乙二醇',
    'heavy_oil': '重油',
    'high_level_raffinate': '高放射性萃余液', 'hot_hydrocracker_effluent': '高温加氢裂化产物',
    'hot_hydrotreater_effluent': '高温加氢精制产物', 'hydrochloric_acid': '盐酸',
    'liquid_ammonia': '液氨', 'liquid_chlorine': '液氯', 'liquid_helium': '液氦',
    'liquid_nitrogen': '液氮', 'liquid_oxygen': '液氧', 'liquid_propylene': '液态丙烯',
    'liquid_sulfur_dioxide': '液态二氧化硫', 'liquid_vinyl_chloride': '液态氯乙烯',
    'low_sulfur_diesel': '低硫柴油', 'lubricating_oil': '润滑油', 'molten_polyethylene': '熔融聚乙烯',
    'molten_polypropylene': '熔融聚丙烯', 'naphtha': '石脑油', 'nitric_acid': '硝酸',
    'phosphoric_acid': '磷酸', 'plutonium_nitrate_solution': '硝酸钚溶液',
    'sodium_aluminate_solution': '铝酸钠溶液', 'sodium_hypochlorite_solution': '次氯酸钠溶液',
    'sulfuric_acid': '硫酸', 'tbp_extraction_solvent': '磷酸三丁酯萃取剂',
    'titanyl_sulfate_solution': '硫酸氧钛溶液', 'tributyl_phosphate': '磷酸三丁酯',
    'uranyl_nitrate_solution': '硝酸铀酰溶液',
    'warm_water': '温水', 'wastewater': '洗涤废水', 'zirconium_sulfate_solution': '硫酸锆溶液',
}


LESSONS = {
    'world_liquids': {
        'title': ('Buckets and placeable liquids', 'バケツと設置できる液体', '流体桶与液体放置'),
        'alloy': ('Craft a corrosion-resistant bucket from nickel, chromium and molybdenum. The recipe is shown in JEI.',
            'ニッケル・クロム・モリブデンから耐食合金バケツを作ります。配合はJEIで確認します。',
            '用镍、铬和钼制作耐腐蚀合金桶，配比见JEI。'),
        'fill': ('Use an alloy bucket for corrosive fluids. Create spouts and item drains can fill and empty it.',
            '腐食性流体には合金バケツを使います。Createの注液口・アイテム排液口でも出し入れできます。',
            '腐蚀性流体使用合金桶。也可通过Create注液器和分液池装卸。'),
        'stainless': ('A stainless steel bucket is made like a bucket, from stainless steel ingots. It holds the liquids a stainless tank holds.',
            'ステンレスバケツは、鉄の代わりにステンレス鋼インゴットでバケツと同じように作ります。ステンレスタンクに入る液体を入れられます。',
            '不锈钢桶的做法与桶相同，只是把铁锭换成不锈钢锭。它能装不锈钢储罐可装的液体。'),
        'stainless_fill': ('Acids, halogens and bases up to very hot fit it, but no gas. Emptied, it stays a stainless bucket.',
            '酸・ハロゲン・塩基を高温まで入れられますが、気体は入りません。空にしてもステンレスバケツに戻ります。',
            '可装高温以下的酸、卤素和碱，但不能装气体。倒空后仍是不锈钢桶。'),
        'pour': ('Use a liquid bucket on the ground to pour it. Gas buckets release a cloud instead.',
            '液体のバケツは地面へ注げます。気体は放出されます。',
            '液体桶可向地面倾倒，气体桶则释放气体。'),
        'danger': ('Acids and alkalis damage creatures that touch them. Keep the flow away from paths and animals.',
            '酸やアルカリに触れるとダメージを受けます。通路や動物のいる場所へ流さないようにします。',
            '接触酸碱会受到伤害，避免让液流经过通道或动物所在处。'),
        'recover': ('Pipes preserve the fluid type and grade. Collect poured liquid with its matching bucket.',
            '配管は流体の種類と等級を保ちます。流した液体は対応するバケツで回収します。',
            '管道保持流体种类与等级，用对应的桶回收倾倒的液体。'),
    },
}


def translations(entries):
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = ASSETS / 'lang' / f'{language}.json'
        original = path.read_bytes().decode('utf-8')
        newline = '\r\n' if '\r\n' in original else '\n'
        strings = json.loads(original)
        for entry in entries:
            name = entry['name']
            fluid_name = (CHINESE.get(name) if language == 'zh_cn' else None) or next((strings[key] for key in (
                kind + '.' + NS.replace(':', '.') + name for kind in ('fluid', 'fluid_type', 'block')) if key in strings), None)
            assert fluid_name, (language, name)
            strings['block.' + NS.replace(':', '.') + name] = fluid_name
            alloy_only = entry['corrosive']
            suffix = (' Corrosion-Resistant Bucket' if alloy_only else ' Bucket') if language == 'en_us' else (
                '入り耐食合金バケツ' if alloy_only else '入りバケツ') if language == 'ja_jp' else (
                '耐蚀合金桶' if alloy_only else '桶')
            strings['item.' + NS.replace(':', '.') + name + '_bucket'] = fluid_name + suffix
        for key, values in {
            'item.create_refinery_n_reactors.corrosion_resistant_bucket': ('Corrosion-Resistant Alloy Bucket', '耐食合金バケツ', '耐蚀合金桶'),
            'item.create_refinery_n_reactors.stainless_steel_bucket': ('Stainless Steel Bucket', 'ステンレスバケツ', '不锈钢桶'),
            'block.create_refinery_n_reactors.charred_wood': ('Carbonized Wood', '炭化木材', '炭化木材'),
            'death.attack.create_refinery_n_reactors.corrosive_contact': ('%1$s was dissolved by corrosive chemicals', '%1$sは腐食性の薬液に溶かされた', '%1$s被腐蚀性化学液体侵蚀致死'),
            'death.attack.create_refinery_n_reactors.corrosive_contact.player': ('%1$s was dissolved by corrosive chemicals while escaping %2$s', '%1$sは%2$sから逃れようとして腐食性の薬液に溶かされた', '%1$s在逃离%2$s时被腐蚀性化学液体侵蚀致死'),
        }.items():
            strings[key] = values[index]
        for name, texts in LESSONS.items():
            for key, values in texts.items():
                strings[PREFIX + name + '.' + key] = values[index]
            strings['create_refinery_n_reactors.ponder.' + name + '.header'] = texts['title'][index]
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


def ponder_structures():
    scene = Structure(9, 5)
    for x in range(3, 8):
        for z in range(1, 6):
            if x in (3, 7) or z in (1, 5):
                scene.block(x, 1, z, 'minecraft:smooth_stone')
    scene.block(1, 1, 6, 'create:fluid_tank')
    scene.block(1, 2, 6, 'create:fluid_tank')
    scene.block(5, 1, 3, NS + 'sulfuric_acid', level='0')
    scene.block(4, 1, 3, NS + 'sulfuric_acid', level='1')
    scene.block(6, 1, 3, NS + 'sulfuric_acid', level='1')
    scene.save('world_liquids')
    target = ASSETS / 'ponder/plant/world_liquids.nbt'
    target.parent.mkdir(parents=True, exist_ok=True)
    (OUT / 'world_liquids.nbt').replace(target)


def generate():
    entries = liquid_entries()
    bucket_image('corrosion_resistant_bucket', alloy=True)
    bucket_image('stainless_steel_bucket', stainless=True)
    write(ASSETS / 'models/item/state_stainless_bucket.json', {'parent': 'minecraft:item/generated',
          'textures': {'layer0': NS + 'item/stainless_steel_bucket', 'layer1': NS + 'item/state_liquid_fill'}})
    bucket_image('water_gas_bucket', 0xBBD3DFDB)
    for entry in entries:
        name = entry['name']
        bucket = name + '_bucket'
        bucket_image(bucket, entry['color'], entry['corrosive'] or name == 'brine')
        path = ASSETS / 'blockstates' / f'{name}.json'
        if not path.exists():
            write(path, {'variants': {'': {'model': 'minecraft:block/water'}}})
    write(DATA / 'create_refinery_n_reactors/recipe/corrosion_resistant_bucket.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'tag': 'c:ingots/nickel'}] * 3 + [{'tag': 'c:ingots/chromium'}, {'tag': 'c:ingots/molybdenum'}],
        'result': {'id': NS + 'corrosion_resistant_bucket', 'count': 1}})
    write(DATA / 'create_refinery_n_reactors/recipe/stainless_steel_bucket.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['# #', ' # '],
        'key': {'#': {'tag': 'c:ingots/stainless_steel'}},
        'result': {'id': NS + 'stainless_steel_bucket', 'count': 1}})
    carbonized_wood()
    translations(entries)
    ponder_structures()
    print(f'Generated world assets for {len(entries)} liquids and carbonized wood.')


if __name__ == '__main__':
    generate()
