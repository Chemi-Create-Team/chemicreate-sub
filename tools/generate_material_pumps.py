#!/usr/bin/env python3
"""Create's mechanical pump geometry with each pipe's existing material textures."""
import json
import os
from pathlib import Path
from zipfile import ZipFile
from generate_petrochemical_ponder import Structure, OUT

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
MATERIALS = {
    'reinforced': ('Reinforced', '耐圧鋼', '增强钢'),
    'nickel_alloy': ('Nickel Alloy', 'ニッケル合金', '镍合金'),
    'zirconium': ('Zirconium', 'ジルコニウム', '锆'),
    'stainless_steel': ('Stainless Steel', 'ステンレス鋼', '不锈钢'),
    'cryogenic': ('Cryogenic', '保冷', '低温'),
    'pvc': ('PVC', '塩ビ', 'PVC'),
    'resin': ('Resin', '樹脂', '树脂'),
    'gas': ('Gas', '気体', '气体'),
    'large_gas': ('Large Gas', '大型気体', '大型气体'),
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf8')


def main():
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
    jars = list((cache / 'caches/modules-2/files-2.1/maven.modrinth/create/UjX6dr61').rglob('*.jar'))
    if not jars:
        raise SystemExit('Resolve the configured Create 6.0.10 dependency first')
    with ZipFile(jars[0]) as jar:
        state = json.loads(jar.read('assets/create/blockstates/mechanical_pump.json'))
        item = json.loads(jar.read('assets/create/models/block/mechanical_pump/item.json'))
    # The first two elements are the end flanges; the remaining elements form the cog.
    for element in item['elements'][2:]:
        for face in element['faces'].values(): face['texture'] = '#cog'
    write(A / 'models/block/material_pump/item.json', {
        'parent': 'create:block/mechanical_pump/item',
        'textures': {'cog': 'create:block/pump'}, 'elements': item['elements']})
    for material in MATERIALS:
        name = material + '_pump'
        pipe = material + '_pipe'
        path = pipe
        if material == 'stainless_steel': texture = NS + 'block/transport/stainless_steel_pipes'
        elif material == 'resin': texture = NS + 'block/petrochemical/resin_pipe'
        elif material == 'large_gas': texture = NS + 'block/transport/large_gas_pipe_side'
        else:
            textures = json.loads((A / 'models/block' / path / 'inventory.json').read_text())['textures']
            texture = textures.get('pipe', textures.get('body', textures.get('particle')))
            while texture.startswith('#'): texture = textures[texture[1:]]
        for part in ('block', 'item'):
            write(A / f'models/block/material_pump/{name}/{part}.json', {
                'parent': 'create:block/mechanical_pump/block' if part == 'block' else NS + 'block/material_pump/item',
                'textures': {'4': texture, 'particle': texture}})
        variants = json.loads(json.dumps(state))
        for variant in variants['variants'].values(): variant['model'] = NS + f'block/material_pump/{name}/block'
        write(A / f'blockstates/{name}.json', variants)
        write(A / f'models/item/{name}.json', {'parent': NS + f'block/material_pump/{name}/item'})
        write(D / f'recipe/{name}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
            'ingredients': [{'item': 'create:mechanical_pump'}, {'item': NS + pipe}], 'result': {'id': NS + name}})
        write(D / f'loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': NS + name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    pickaxe = ROOT / 'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
    data = json.loads(pickaxe.read_text())
    data['values'] = list(dict.fromkeys(data['values'] + [NS + name + '_pump' for name in MATERIALS]))
    write(pickaxe, data)
    scene = Structure(7, 5)
    for x in (1, 5): scene.block(x, 1, 3, NS + 'pressure_tank', bottom='true', top='true', shape='plain')
    for x in (2, 4): scene.block(x, 1, 3, NS + 'reinforced_pipe', east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
    scene.block(3, 1, 3, NS + 'reinforced_pump', facing='east', waterlogged='false')
    scene.block(3, 1, 4, 'create:cogwheel', axis='x', waterlogged='false')
    scene.block(2, 1, 4, 'create:creative_motor', facing='east')
    scene.save('material_pump')
    (A / 'ponder/plant/material_pump.nbt').write_bytes((OUT / 'material_pump.nbt').read_bytes())
    (OUT / 'material_pump.nbt').unlink()
    captions = {
        'title': ('Pumps matching the pipe material', '配管と材質を揃えたポンプ', '与管材配套的泵'),
        'connect': ('Connect pipes to the two ends. These pumps use the same transport system as Create pipes and pumps.', '両端へパイプを接続します。Create標準のパイプやポンプと同じ搬送系で使えます。', '在两端连接管道。这些泵使用Create标准管道和泵的输送系统。'),
        'drive': ('Drive the pump cog from an adjacent cogwheel. Rotation controls the flow rate.', '隣の歯車からポンプの歯車へ回転を伝えます。回転速度で流量が変わります。', '通过相邻齿轮驱动泵。转速决定流量。'),
        'reverse': ('Use a wrench on the pump to reverse the flow. The drive keeps the same rotation.', 'レンチでポンプを操作すると流れる向きが反転します。駆動側の回転はそのままです。', '用扳手操作泵可反转流向，驱动转向保持不变。'),
        'stop': ('Stopping the drive stops transport. Check the pipe material if a fluid cannot pass.', '駆動を止めると搬送も止まります。流れないときは配管の材質と接続を確認します。', '停止驱动即停止输送。无法输送时检查管材和连接。'),
        'intake': ('An open gas pipe draws atmospheric air when a pump pulls on it. Connect the outlet to the compressor top inlet.', '気体パイプの開放端をポンプで吸引すると空気を取り込めます。送り先を圧縮機上面の入口につなぎます。', '用泵抽吸气体管道开放端即可吸入空气。将出口连接到压缩机顶部入口。'),
    }
    for index, lang in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / (lang + '.json'); data = json.loads(path.read_text())
        for material, names in MATERIALS.items():
            data['block.create_refinery_n_reactors.' + material + '_pump'] = names[index] + (' Pump' if index == 0 else 'ポンプ' if index == 1 else '泵')
        for key, texts in captions.items(): data['create_refinery_n_reactors.ponder.shared.material_pump.' + key] = texts[index]
        data['create_refinery_n_reactors.ponder.material_pump.header'] = captions['title'][index]
        write(path, data)


if __name__ == '__main__': main()
