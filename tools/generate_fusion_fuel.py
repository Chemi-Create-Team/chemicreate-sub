#!/usr/bin/env python3
"""Only fusion-fuel recipes, new sprites/models and independent Ponder fragments."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
import generate_petrochemical_ponder as ponder

ROOT = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors:'
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
F = ROOT / 'tools/data'
LANG = {x: {} for x in ('en_us', 'ja_jp', 'zh_cn')}
CHINESE = json.loads((A / 'lang/zh_cn.json').read_text())


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def tr(key, en, ja, zh=None):
    if zh is None and '.ponder.' in key:
        zh = CHINESE.get(key)
    for locale, text in zip(LANG, (en, ja, zh or en)):
        LANG[locale][key] = text


def fluid(name, amount): return {'type': 'neoforge:single', 'amount': amount, 'fluid': name}
def output(name, amount): return {'id': name, 'amount': amount}

def recipe(name, ingredients, results, heat='heated'):
    write(D / 'recipe/pressure_reaction' / (name + '.json'), {'type': NS + 'pressure_reaction', 'heat_requirement': heat, 'ingredients': ingredients, 'results': results, 'processing_time': 200})


def shaped(name, pattern, keys):
    write(D / 'recipe' / (name + '.json'), {'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': {k: {'item': v} for k, v in keys.items()}, 'result': {'id': NS + name}})


def assets():
    shaped('lithium_target_rod', [' S ', ' B ', ' S '], {'S': NS + 'stainless_steel_sheet', 'B': NS + 'lithium_lead_blanket_cartridge'})
    shaped('cryogenic_regenerator', ['CNC', 'THT', 'CNC'], {'C': 'create:copper_sheet', 'N': NS + 'nickel_sheet', 'T': NS + 'cryogenic_tank', 'H': NS + 'jacketed_pipe'})
    shaped('cryogenic_cold_box_drive', ['SCS', 'PMH', 'SGS'], {'S': NS + 'stainless_steel_sheet', 'C': 'create:copper_sheet', 'P': 'create:precision_mechanism', 'M': 'create:shaft', 'H': NS + 'jacketed_pipe', 'G': 'create:cogwheel'})
    recipe('extract_irradiated_lithium', [{'item': NS + 'irradiated_lithium_target_rod'}], [output(NS + 'tritium_gas', 1000), output(NS + 'helium_gas', 2000), {'id': NS + 'stainless_steel_sheet'}], 'superheated')
    recipe('recover_geological_helium', [{'item': NS + 'uraninite'}, fluid('minecraft:water', 250)], [output(NS + 'helium_recovery_feed', 16000), {'id': NS + 'uranium_trioxide'}], 'superheated')
    recipe('reuse_deuterium_depleted_water', [fluid(NS + 'deuterium_depleted_water', 1000)], [output('minecraft:water', 1000)], 'none')
    # New rod sprites preserve the established tall assembly silhouette, with end collars and a lithium index band.
    for name, band in (('lithium_target_rod', (135, 154, 111)), ('irradiated_lithium_target_rod', (176, 146, 80))):
        im = Image.new('RGBA', (16, 16)); p = ImageDraw.Draw(im)
        p.rectangle((6, 1, 9, 14), fill=(51, 56, 59));p.rectangle((7, 2, 8, 13), fill=(146, 153, 152))
        p.line((7, 2, 7, 13), fill=(195, 197, 182));p.rectangle((5, 1, 10, 3), fill=(76, 82, 82));p.line((5, 1, 10, 1), fill=(178, 184, 172))
        p.rectangle((5, 12, 10, 14), fill=(76, 82, 82));p.line((5, 12, 9, 12), fill=(178, 184, 172));p.rectangle((6, 6, 9, 8), fill=band)
        path = A / 'textures/item' / (name + '.png');path.parent.mkdir(parents=True, exist_ok=True);im.save(path)
        write(A / 'models/item' / (name + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})
    for name, colour in (('helium_recovery_feed', (179, 175, 143)),):
        template = json.loads((ROOT / 'tools/vanilla_bucket_template.json').read_text())
        im = Image.new('RGBA', (16, 16));im.putdata([tuple(v) for v in template['pixels']])
        for index in template['content_mask']:
            x, y = index % 16, index // 16
            im.putpixel((x, y), tuple(min(255, max(0, c + (12 if y < 4 else -12 if x > 9 else 0))) for c in colour) + (255,))
        im.save(A / 'textures/item' / (name + '_bucket.png'))
        write(A / 'models/item' / (name + '_bucket.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name + '_bucket'}})
    # Explicit collars, jacket ribs and copper manifold: geometry explains regenerator vs drive.
    for name, colour, inward in (('cryogenic_cold_box_inlet', (102, 151, 159), True), ('cryogenic_cold_box_outlet', (175, 137, 87), False)):
        im = Image.new('RGBA', (16, 16), (82, 91, 94));p = ImageDraw.Draw(im)
        p.rectangle((0, 0, 15, 15), outline=(44, 52, 54));p.line((1, 1, 14, 1), fill=(157, 165, 157));p.line((1, 1, 1, 14), fill=(157, 165, 157));p.rectangle((3, 3, 12, 12), fill=(49, 61, 65))
        if inward: p.rectangle((7, 4, 8, 9), fill=colour);p.polygon(((4, 8), (11, 8), (8, 12)), fill=colour)
        else: p.rectangle((7, 7, 8, 12), fill=colour);p.polygon(((4, 7), (11, 7), (8, 3)), fill=colour)
        path = A / 'textures/block' / (name + '.png');path.parent.mkdir(parents=True, exist_ok=True);im.save(path)
    for name in ('cryogenic_cold_box_drive', 'cryogenic_regenerator'):

        im = Image.new('RGBA', (16, 16), (87, 95, 96));p = ImageDraw.Draw(im)
        p.rectangle((0, 0, 15, 15), outline=(51, 58, 60));p.line((1, 1, 14, 1), fill=(161, 169, 159));p.line((1, 1, 1, 14), fill=(161, 169, 159));p.line((14, 2, 14, 14), fill=(64, 70, 72));p.line((2, 14, 14, 14), fill=(64, 70, 72))
        for y in (4, 8, 12):
            p.rectangle((3, y, 7, y + 1), fill=(99, 108, 107));p.rectangle((9, y - 1, 12, y), fill=(73, 82, 83))
        if name.endswith('drive'):
            p.rectangle((4, 4, 11, 11), outline=(173, 131, 85));p.rectangle((5, 5, 10, 10), fill=(53, 68, 72));p.line((7, 6, 7, 9), fill=(132, 180, 184))
        else:
            for y in range(3, 14, 3):
                p.line((3, y, 12, y), fill=(179, 133, 87));p.line((3, y + 1, 12, y + 1), fill=(99, 72, 52))
        path = A / 'textures/block' / (name + '.png');path.parent.mkdir(parents=True, exist_ok=True);im.save(path)
        texture = NS + 'block/' + name
        def cube(a, b, tex): return {'from': a, 'to': b, 'faces': {face: {'texture': tex, 'uv': [0, 0, 16, 16]} for face in ('north', 'south', 'east', 'west', 'up', 'down')}}
        parts = [cube([1, 1, 1], [15, 15, 15], '#body')]
        if name.endswith('drive'):
            parts += [cube([4, 4, 0], [12, 12, 2], '#body'), cube([0, 6, 6], [2, 10, 10], '#output'), cube([14, 6, 6], [16, 10, 10], '#input'), cube([6, 14, 6], [10, 16, 10], '#input'), cube([6, 0, 6], [10, 2, 10], '#output')]
        else:
            parts += [cube([0, y, 0], [16, y + 1, 16], '#body') for y in (2, 6, 10, 14)]
        write(A / 'models/block' / (name + '.json'), {'parent': 'minecraft:block/block', 'textures': {'body': texture, 'port': 'create:block/industrial_iron_block', 'input': NS + 'block/cryogenic_cold_box_inlet', 'output': NS + 'block/cryogenic_cold_box_outlet', 'particle': texture}, 'elements': parts})
        variants = {f'facing={face}': {'model': NS + 'block/' + name, 'y': turn} for face, turn in (('north', 0), ('east', 90), ('south', 180), ('west', 270))} if name.endswith('drive') else {'': {'model': NS + 'block/' + name}}
        write(A / 'blockstates' / (name + '.json'), {'variants': variants})
        write(A / 'models/item' / (name + '.json'), {'parent': NS + 'block/' + name})
        write(D / 'loot_table/blocks' / (name + '.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}], 'entries': [{'type': 'minecraft:item', 'name': NS + name}]}]})


def texts():
    for name, en, ja, zh in [('heavy_water', 'Heavy Water', '重水', '重水'), ('deuterium_depleted_water', 'Deuterium-depleted Water', '重水素を除いた水', '贫氘水'), ('helium_recovery_feed', 'Helium Recovery Feed', 'ヘリウム回収原料ガス', '氦回收原料气')]:
        for key in ('fluid.', 'block.'):
            tr(key + NS[:-1] + '.' + name, en, ja, zh)
        tr('item.' + NS[:-1] + '.' + name + '_bucket', en + ' Bucket', ja + '入りバケツ', zh + '桶')
    for name, en, ja, zh in [('lithium_target_rod', 'Lithium Irradiation Target', 'リチウム照射ターゲット棒', '锂辐照靶棒'), ('irradiated_lithium_target_rod', 'Irradiated Lithium Target', '照射済みリチウムターゲット棒', '已辐照锂靶棒')]:
        tr('item.' + NS[:-1] + '.' + name, en, ja, zh)
    for name, en, ja, zh in [('cryogenic_cold_box_drive', 'Cryogenic Cold Box Drive', '極低温コールドボックス駆動部', '低温冷箱驱动器'), ('cryogenic_regenerator', 'Cryogenic Regenerator', '極低温再生熱交換器', '低温回热器')]:
        tr('block.' + NS[:-1] + '.' + name, en, ja, zh)
    tr('reactor_plant.' + NS[:-1] + '.process.concentrate_heavy_water', 'Heavy-water concentration', '重水の濃縮', '重水浓缩')
    tr('reactor_plant.' + NS[:-1] + '.process.recover_helium', 'Helium recovery', 'ヘリウム回収', '氦回收')
    tr('cold_box.' + NS[:-1] + '.temperature', 'Cold head: %s K', '冷凍ヘッド: %s K', '冷端: %s K')
    tr('cold_box.' + NS[:-1] + '.output', 'Liquid He: %s mB / shaft: %s kW', '液体He: %s mB / 軸入力: %s kW', '液氦: %s mB / 轴功率: %s kW')
    tr('fusion_fuel.create_refinery_n_reactors.targets', 'Targets: %s exposed / %s ready / %s%%', 'ターゲット: 照射中%s本 / 完了%s本 / %s%%', '靶棒: 辐照%s根 / 完成%s根 / %s%%')
    states = {
        'structure': ('Needs a 3x3x3 steel-block jacket and central regenerator', '鋼鉄ブロック3×3×3の断熱箱と中央の再生熱交換器が必要'),
        'unloaded': ('Cold box chunk unloaded', 'コールドボックスのチャンクが未ロード'),
        'speed': ('Supply at least 32 RPM and enough stress capacity', '32 RPM以上の回転と応力容量が必要'),
        'redstone': ('Stopped by redstone', 'レッドストーンで停止中'),
        'cycle': ('Prime the closed cycle with 1000 mB helium', '閉サイクルの初期充填にHe 1000 mBが必要'),
        'water': ('Supply cool water and drain the warm-water outlet', '冷却水を供給し、温水出口を空けてください'),
        'feed': ('Supply helium gas through the top', '上面からヘリウムガスを供給してください'),
        'power': ('Not enough shaft work for one liquid mB', '液体1 mBを作る軸仕事が不足'),
        'output': ('Drain liquid helium from the bottom', '底面から液体ヘリウムを搬出してください'),
        'running': ('Liquefying helium at 4.2 K', '4.2 Kでヘリウムを液化中'),
        'cooldown': ('Cooling down the regenerator', '再生熱交換器を冷却中'),
    }
    for key, (en, ja) in states.items(): tr('cold_box.' + NS[:-1] + '.status.' + key, en, ja)


def ponder_scenes():
    ponder.OUT = A / 'ponder/plant'
    catalog = []
    def save(id, scene, components, title, steps, scale=None):
        scene.save(id)
        entry = {'id': id, 'size': scene.size[0], 'height': scene.size[1], 'components': components, 'steps': []}
        if scale is not None: entry['scale'] = scale
        for i, (target, item, en, ja) in enumerate(steps, 1):
            entry['steps'].append({'target': target, 'item': item, 'key': f'step_{i}'})
            if id == 'helium_recovery' and i==1:entry['steps'][-1]['speed']=32
            tr(NS[:-1] + '.ponder.shared.guide.' + id + f'.step_{i}', en, ja)
        tr(NS[:-1] + '.ponder.shared.guide.' + id + '.title', *title)
        tr(NS[:-1] + '.ponder.plant_' + id + '.header', *title)
        catalog.append(entry)
    s = ponder.Structure(9, 15)
    inside = lambda x, z, size: (x + .5 - size / 2) ** 2 + (z + .5 - size / 2) ** 2 <= (size / 2) ** 2
    for x in range(9):
        for z in range(9):
            if not inside(x, z, 9): continue
            core = 2 <= x <= 6 and 2 <= z <= 6 and inside(x - 2, z - 2, 5)
            if core:
                for y in range(2, 5): s.block(x, y, z, NS + 'reactor_feed_bundle')
                for y in range(5, 8): s.block(x, y, z, NS + 'reactor_fuel_channel')
                for y in range(8, 12): s.block(x, y, z, NS + 'reactor_fuel_channel_tube')
                s.block(x, 12, z, NS + 'reactor_top_cap')
            else:
                for y in range(2, 13): s.block(x, y, z, NS + 'biological_shield')
    save('lithium_target_irradiation', s, ['lithium_target_rod', 'irradiated_lithium_target_rod', 'fusion_tritium_port', 'tritium_gas_bucket'], ('Starting the tritium fuel supply', '初回の三重水素を作る'), [
        ([4, 12, 4], NS + 'lithium_target_rod', 'Build a complete operating RBMK, including coolant and controls. Insert a lithium target through a top cap into an empty fuel position. Control-rod holes do not take targets.', '冷却・制御を含むRBMK炉を組みます。上蓋から空いた燃料位置へリチウムターゲット棒を入れてください。制御棒の孔には入りません。'),
        ([4, 6, 4], NS + 'lithium_target_rod', 'Run the reactor to irradiate the target. It takes 600 rated-power seconds and pauses when stopped.', '炉を運転して照射します。定格換算600秒で完了し、停止中は進みません。'),
        ([4, 12, 4], NS + 'irradiated_lithium_target_rod', 'A funnel extracts completed targets from the same cap. Stopping midway preserves the exposure through saving and reloading.', '照射済みターゲットは同じ蓋からファンネルなどで取り出します。途中停止や保存・再ロードでも照射量を保持します。'),
        ([4, 12, 4], NS + 'tritium_gas_bucket', 'Superheat an irradiated target in the existing stirred vessel to extract T2 and helium. Reserve the first T2 for fusion startup; the fusion blanket already breeds replacement fuel.', '照射済みターゲットを既存の撹拌槽で超加熱し、T₂とヘリウムを抽出します。最初のT₂を核融合炉の始動に使い、運転後は既存ブランケットの増殖を利用します。')], .7)
    s = ponder.Structure(13, 7);s.reactor(3, 2, 3, 'stirred', 1, ((1, 0),));s.reactor(9, 2, 3, 'centrifuge', 1)
    s.pipe(3,3,3,'down','east');s.pipe(4,3,3,'east','west');s.pipe(5,3,3,'east','west');s.pipe(6,3,3,'west','down')
    s.pipe(6,2,3,'up','east');s.pump(7,2,3,'east');s.pipe(8,2,3,'east','west')
    s.pipe(9,4,3,'down','up');s.pump(9,5,3,'up');s.block(9,6,3,'create:fluid_tank')
    s.pipe(10,2,3,'east','west');s.pump(11,2,3,'east');s.block(12,2,3,'create:fluid_tank')
    for x,y,z,axis,dx,dy,facing in ((7,1,3,'x',1,0,'west'),(10,5,3,'y',0,-1,'up'),(11,1,3,'x',1,0,'west')):
        s.block(x,y,z,'create:cogwheel',axis=axis,waterlogged='false');s.block(x+dx,y+dy,z,'create:creative_motor',facing=facing)
    save('helium_recovery', s, ['helium_recovery_feed_bucket', 'helium_gas_bucket', 'fusion_ash_port', 'fusion_helium_outlet'], ('Recovering helium for the first startup', '初回のヘリウムを回収する'), [
        ([3, 2, 3], NS + 'uraninite', 'Superheat uranium ore and water in a stirred vessel to obtain helium-recovery gas and uranium trioxide.', 'ウラン鉱石と水を撹拌槽で超加熱し、ヘリウム回収ガスと三酸化ウランを取り出します。'),
        ([9, 2, 3], NS + 'helium_recovery_feed_bucket', 'The two-block-tall gas centrifuge takes recovery gas at its lower side. Helium leaves the upper top face; nitrogen leaves a separate side pipe. Keep both outlets clear.', '高さ2ブロックのガス遠心分離機の下半分へ回収ガスを入れます。ヘリウムは最上段の上面、窒素は別の側面へ搬出します。両方の出口を空けてください。'),
        ([9, 2, 3], NS + 'helium_gas_bucket', 'During fusion operation, collect helium ash and magnet boil-off separately and return them to the cold-box inlet. They are clean helium streams and need no new purification recipe.', '核融合の運転後はHe灰と磁石の蒸発ガスを回収し、コールドボックス入口へ戻します。どちらも純ヘリウムなので再精製は不要です。')])
    write(F / 'fusion_fuel_ponder.json', catalog)


if __name__ == '__main__':
    F.mkdir(parents=True, exist_ok=True);assets();texts();ponder_scenes()
    write(F / 'fusion_fuel_lang.json', LANG)
