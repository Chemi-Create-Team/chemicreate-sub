#!/usr/bin/env python3
"""Spodumene and galena ores, lead and lithium carbonate, and the lead-acid and lithium-ion battery banks: textures, models, loot, tags,
worldgen, recipes and translations. Run from the repository root."""
import json, sys
from pathlib import Path
from PIL import Image, ImageDraw
sys.path.insert(0, 'tools')
import generate_metal_ores as metal_ores
from generate_reactor_assets import A, D, NS, write
from generate_uranium_assets import loot, tag, item, fluid, fluid_out

ROOT = Path(__file__).resolve().parents[1]
# Ore: (worldgen size, count, lowest y, highest y, names of the ore, names of the mineral), each en/ja/zh.
ORES = {
    'spodumene': (6, 3, -16, 64, ('Spodumene Ore', 'リチア輝石鉱石', '锂辉石矿石'), ('Spodumene', 'リチア輝石', '锂辉石')),
    'galena': (8, 4, -32, 48, ('Galena Ore', '方鉛鉱石', '方铅矿石'), ('Galena', '方鉛鉱', '方铅矿')),
}
BATTERIES = {
    'lead_acid': ('Lead-Acid', '鉛蓄電池', '铅酸蓄电池', (58, 66, 84), (140, 150, 170)),
    'lithium_ion': ('Lithium-Ion', 'リチウムイオン電池', '锂离子电池', (36, 98, 104), (150, 214, 208)),
}
TEXT = {
    'item.create_refinery_n_reactors.lead_ingot': ('Lead Ingot', '鉛インゴット', '铅锭'),
    'item.create_refinery_n_reactors.lead_sinter': ('Lead Sinter', '鉛の焼結鉱', '铅烧结矿'),
    'item.create_refinery_n_reactors.lithium_carbonate': ('Lithium Carbonate', '炭酸リチウム', '碳酸锂'),
}
LANG = {
    'battery.create_refinery_n_reactors.terminal.positive': ('Positive terminal', '正極（＋）', '正极（+）'),
    'battery.create_refinery_n_reactors.terminal.negative': ('Negative terminal', '負極（－）', '负极（-）'),
    'battery.create_refinery_n_reactors.status.discharging': ('Discharging', '放電中', '放电中'),
    'battery.create_refinery_n_reactors.status.charging': ('Charging', '充電中', '充电中'),
    'battery.create_refinery_n_reactors.status.idle': ('Ready: no current on the terminals', '待機：端子に電流なし', '待机：端子无电流'),
    'battery.create_refinery_n_reactors.status.full': ('Full: disconnected from the charging circuit', '満充電：充電回路から切断', '已充满：已与充电电路断开'),
    'battery.create_refinery_n_reactors.status.electrolyte': ('Electrolyte not full', '電解液が満たされていません', '电解液未充满'),
    'battery.create_refinery_n_reactors.status.unwired': ('Not in a circuit: wire both terminals', '回路に接続されていません：両方の端子を配線', '未接入电路：请连接两个端子'),
    'battery.create_refinery_n_reactors.status.structure.casing': ('Put a casing of the same battery type under the terminal', '端子の真下に同じ種類のケーシングを置いてください', '请在端子正下方放置同类型的外壳'),
    'battery.create_refinery_n_reactors.status.structure.box': ('The casings must form one solid box with the terminal on its top face', 'ケーシングは中身の詰まった直方体にし、端子を上面に置いてください', '外壳须组成实心长方体，端子放在顶面'),
    'battery.create_refinery_n_reactors.status.structure.size': ('Each side of the box must be at least 2 blocks, with at most 4,096 casings', '直方体の各辺は2ブロック以上、ケーシングは4,096個までです', '长方体每边至少2格，外壳最多4096个'),
    'battery.create_refinery_n_reactors.status.structure.unloaded': ('Part of the bank is not loaded', '蓄電池の一部が読み込まれていません', '蓄电池的一部分尚未加载'),
    'battery.create_refinery_n_reactors.charge': ('Charge %s%% (%s of %s kJ)', '充電率%s%%（%s／%s kJ）', '电量%s%%（%s／%s kJ）'),
    'battery.create_refinery_n_reactors.power': ('%s kW (%s V, %s A)', '%s kW（%s V、%s A）', '%s kW（%s V、%s A）'),
    'battery.create_refinery_n_reactors.electrolyte': ('Electrolyte %s / %s mB', '電解液 %s／%s mB', '电解液 %s／%s mB'),
}


def texture_item(name, draw):
    im = Image.new('RGBA', (16, 16)); draw(ImageDraw.Draw(im)); im.save(A / 'textures/item' / f'{name}.png')
    write(A / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})


def crystal(d, x, y, dark, mid, light):
    d.polygon([(x, y + 3), (x + 1, y), (x + 3, y), (x + 4, y + 3), (x + 2, y + 5)], fill=dark)
    d.rectangle((x + 1, y + 1, x + 2, y + 3), fill=mid); d.point((x + 1, y + 1), fill=light)


def textures():
    spodumene = ((88, 100, 70), (168, 196, 140), (232, 240, 214))
    galena = ((44, 48, 58), (112, 120, 136), (196, 204, 216))
    def lump(palette):
        def draw(d):
            for x, y in ((2, 7), (7, 3), (8, 8), (4, 2)): crystal(d, x, y, *palette)
        return draw
    texture_item('spodumene', lump(spodumene)); texture_item('galena', lump(galena))
    def ingot(d):
        d.polygon([(2, 11), (4, 6), (13, 6), (14, 11)], fill=(70, 78, 98)); d.polygon([(4, 6), (13, 6), (12, 8), (3, 8)], fill=(150, 160, 184))
        d.rectangle((3, 9, 13, 10), fill=(104, 114, 138)); d.line((5, 7, 10, 7), fill=(206, 214, 232))
    texture_item('lead_ingot', ingot)
    def sinter(d):
        for x, y, c in ((2, 8, (150, 128, 70)), (7, 5, (196, 164, 82)), (8, 9, (120, 100, 58)), (4, 4, (176, 146, 76))):
            d.polygon([(x, y + 2), (x + 1, y), (x + 4, y), (x + 5, y + 3), (x + 3, y + 5), (x, y + 4)], fill=c); d.point((x + 2, y + 1), fill=(236, 214, 130))
    texture_item('lead_sinter', sinter)
    def salt(d):
        for x, y in ((3, 8), (6, 6), (9, 8), (5, 10), (8, 10), (11, 10), (7, 8)): d.rectangle((x, y, x + 2, y + 1), fill=(232, 234, 240)); d.point((x, y), fill=(255, 255, 255)); d.point((x + 2, y + 1), fill=(176, 180, 192))
    texture_item('lithium_carbonate', salt)
    for ore, palette in (('spodumene', spodumene), ('galena', galena)):
        im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
        for x, y in ((2, 3), (9, 2), (5, 9), (11, 10)): crystal(d, x, y, *palette)
        for prefix in ('', 'deepslate_'): im.save(A / 'textures/block' / f'{prefix}{ore}_ore_overlay.png')
    for kind, (_, _, _, dark, light) in BATTERIES.items():
        for part in ('casing', 'terminal'):
            im = Image.new('RGBA', (16, 16), (*dark, 255)); d = ImageDraw.Draw(im)
            d.rectangle((0, 0, 15, 15), outline=tuple(c // 2 for c in dark))
            for y in range(3, 14, 4): d.line((2, y, 13, y), fill=light)
            if part == 'terminal': d.rectangle((6, 6, 9, 9), fill=(214, 168, 70))
            im.save(A / 'textures/block' / f'{kind}_battery_{part}.png')
    Image.new('RGBA', (16, 16), (214, 168, 70, 255)).save(A / 'textures/block/battery_terminal_post.png')


def cube(texture):
    return {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + 'block/' + texture}}


def blocks():
    # The two ores reuse the engineering-metal ore generator: models, loot, crushing, tags, worldgen and biome modifiers.
    saved = dict(metal_ores.ORES); metal_ores.ORES.clear(); metal_ores.ORES.update(ORES)
    try: metal_ores.blocks()
    finally: metal_ores.ORES.clear(); metal_ores.ORES.update(saved)
    ids = []
    for kind in BATTERIES:
        casing, terminal = f'{kind}_battery_casing', f'{kind}_battery_terminal'
        write(A / 'models/block' / f'{casing}.json', cube(casing)); write(A / 'blockstates' / f'{casing}.json', {'variants': {'': {'model': NS + 'block/' + casing}}})
        write(A / 'models/item' / f'{casing}.json', {'parent': NS + 'block/' + casing}); loot(casing)
        body = {'from': [0, 0, 0], 'to': [16, 14, 16], 'faces': {f: {'texture': '#side'} for f in ('down', 'up', 'north', 'south', 'west', 'east')}}
        post = lambda x: {'from': [x, 14, 7], 'to': [x + 2, 17, 9], 'faces': {f: {'texture': '#post'} for f in ('down', 'up', 'north', 'south', 'west', 'east')}}
        write(A / 'models/block' / f'{terminal}.json', {'parent': 'minecraft:block/block', 'textures': {'particle': NS + 'block/' + terminal, 'side': NS + 'block/' + terminal, 'post': NS + 'block/battery_terminal_post'},
            'elements': [body, post(3), post(11)]})
        write(A / 'blockstates' / f'{terminal}.json', {'variants': {f'facing={f}': {'model': NS + 'block/' + terminal, **({'y': y} if y else {})}
            for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
        write(A / 'models/item' / f'{terminal}.json', {'parent': NS + 'block/' + terminal})
        write(D / 'loot_table/blocks' / f'{terminal}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + terminal,
            'functions': [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['minecraft:block_entity_data']}]}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        ids += [NS + casing, NS + terminal]
    tag('minecraft/tags/block/mineable/pickaxe.json', ids)


def recipes():
    # Lead has no furnace shortcut: galena is roasted to sinter and reduced, or smelted directly in one self-heating step.
    for kind in ('smelting', 'blasting'): (D / f'recipe/{kind}/galena.json').unlink(missing_ok=True)
    oxygen, dioxide = NS + 'oxygen_gas', NS + 'sulfur_dioxide'
    # 2 PbS + 3 O2 -> 2 PbO + 2 SO2; the sulfur leaves as sulfur dioxide for the acid line.
    write(D / 'recipe/pressure_reaction/galena_roasting.json', {'type': NS + 'pressure_reaction', 'heat_requirement': 'heated',
        'ingredients': [{'item': NS + 'galena'}, {'item': NS + 'galena'}, fluid(oxygen, 1500)], 'results': [item(NS + 'lead_sinter', 2), fluid_out(dioxide, 1000)]})
    # PbO + CO -> Pb + CO2.
    write(D / 'recipe/pressure_reaction/lead_sinter_reduction.json', {'type': NS + 'pressure_reaction', 'heat_requirement': 'superheated',
        'ingredients': [{'item': NS + 'lead_sinter'}, {'item': NS + 'lead_sinter'}, fluid(NS + 'carbon_monoxide', 1000)], 'results': [item(NS + 'lead_ingot', 2), fluid_out(NS + 'carbon_dioxide', 1000)]})
    # PbS + O2 -> Pb + SO2: the burning sulfide heats the charge, so one vessel at the lower heat does both stages.
    write(D / 'recipe/pressure_reaction/galena_direct_smelting.json', {'type': NS + 'pressure_reaction', 'heat_requirement': 'heated',
        'ingredients': [{'item': NS + 'galena'}, {'item': NS + 'galena'}, fluid(oxygen, 1000)], 'results': [item(NS + 'lead_ingot', 2), fluid_out(dioxide, 1000)]})
    tag('c/tags/item/ingots/lead.json', [NS + 'lead_ingot']); tag('c/tags/item/ingots.json', ['#c:ingots/lead'])
    # Acid roasting of spodumene with soda ash gives lithium carbonate; the aluminosilicate residue is sand.
    write(D / 'recipe/pressure_reaction/lithium_carbonate.json', {'type': NS + 'pressure_reaction', 'heat_requirement': 'superheated',
        'ingredients': [{'item': NS + 'spodumene'}, {'item': NS + 'spodumene'}, {'item': NS + 'sodium_carbonate'}, fluid(NS + 'sulfuric_acid', 250)],
        'results': [item(NS + 'lithium_carbonate', 2), item('minecraft:sand')]})
    # Casings: the active material pressed between stainless sheets; the terminal adds copper posts and a nickel bus.
    stain, lead, lithium = NS + 'stainless_steel_sheet', NS + 'lead_ingot', NS + 'lithium_carbonate'
    write(D / 'recipe/lead_acid_battery_casing.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['SLS', 'LRL', 'SLS'],
        'key': {'S': {'item': stain}, 'L': {'item': lead}, 'R': {'item': 'minecraft:redstone'}}, 'result': item(NS + 'lead_acid_battery_casing', 2)})
    write(D / 'recipe/lithium_ion_battery_casing.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['SCS', 'CGC', 'SCS'],
        'key': {'S': {'item': stain}, 'C': {'item': lithium}, 'G': {'item': NS + 'carbon_powder'}}, 'result': item(NS + 'lithium_ion_battery_casing', 2)})
    for kind in BATTERIES:
        write(D / f'recipe/{kind}_battery_terminal.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['C C', 'NBN'],
            'key': {'C': {'item': 'minecraft:copper_ingot'}, 'N': {'item': NS + 'nickel_ingot'}, 'B': {'item': NS + f'{kind}_battery_casing'}}, 'result': item(NS + f'{kind}_battery_terminal')})


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; data = json.loads(raw)
        for ore, (*_, ore_names, mineral) in ORES.items():
            data[f'block.create_refinery_n_reactors.{ore}_ore'] = ore_names[index]
            data[f'block.create_refinery_n_reactors.deepslate_{ore}_ore'] = ('Deepslate ', '深層', '深板岩')[index] + ore_names[index]
            data[f'item.create_refinery_n_reactors.{ore}'] = mineral[index]
        for key, names in {**TEXT, **LANG}.items(): data[key] = names[index]
        for kind, names in BATTERIES.items():
            data[f'block.create_refinery_n_reactors.{kind}_battery_casing'] = (names[0] + ' Battery Casing', names[1] + 'ケーシング', names[2] + '外壳')[index]
            data[f'block.create_refinery_n_reactors.{kind}_battery_terminal'] = (names[0] + ' Battery Terminal', names[1] + '端子', names[2] + '端子')[index]
        path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


def classify():
    path = ROOT / 'tools/powder_items.json'; data = json.loads(path.read_text())
    for name, reason in (('spodumene', 'Mined mineral lump/crystal, not a powder process product.'), ('galena', 'Mined mineral lump/crystal, not a powder process product.'),
                         ('lead_ingot', 'Metal ingot; retain its ingot sprite.'), ('lead_sinter', 'Roasted lead oxide sinter; retain its own sprite.'), ('lithium_carbonate', 'Refined lithium salt; retain its own sprite.')):
        data['preserved_items'][name] = reason
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    textures(); blocks(); recipes(); translations(); classify()
    print('Generated spodumene and galena, lead, lithium carbonate and the two battery banks')
