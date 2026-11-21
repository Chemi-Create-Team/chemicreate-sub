#!/usr/bin/env python3
"""Reciprocating compressor Ponder: a cylinder with an engine on its side and the crank shaft, with English/Japanese captions."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS, write
from generate_petrochemical_ponder import Structure

s = Structure(7, 5)
s.block(2, 2, 3, NS + 'reciprocating_cylinder')
s.block(3, 2, 3, NS + 'reciprocating_compressor', face='wall', facing='east', waterlogged='false')
s.block(5, 2, 3, NS + 'reciprocating_drive_shaft', axis='z', waterlogged='false')
for z in (1, 2, 4, 5):
    s.block(5, 2, z, 'create:shaft', axis='z', waterlogged='false')


def pipe(x, y, z, *sides):
    s.block(x, y, z, NS+'reinforced_pipe', **{d: str(d in sides).lower() for d in ('north', 'south', 'east', 'west', 'up', 'down')}, waterlogged='false')


pipe(2, 3, 3, 'up', 'down')
pipe(2, 4, 3, 'down', 'west')
pipe(2, 1, 3, 'up', 'west')
s.save('reciprocating_compressor')

texts = {
    'title': ('Reciprocating compressor', '往復式コンプレッサー', '往复式压缩机'),
    'cylinder': ('The reciprocating cylinder is the gas chamber. Gas comes in at the top and the compressed gas leaves from the bottom.', '往復式シリンダーが気体の部屋です。気体は上面から入り、圧縮された気体は下面から出ます。', '往复气缸储存气体，气体从顶面进入，压缩后从底面排出。'),
    'engine': ('Attach the compressor drive to the cylinder.', 'シリンダーに圧縮機の駆動部を取り付けます。', '将压缩机驱动端安装在气缸上。'),
    'shaft': ('Put a Create shaft two blocks out from the engine, across its crank. It turns into a driven shaft, and its rotation moves the piston.', '駆動部から2ブロック先に、クランクと直角にCreateのシャフトを置きます。駆動シャフトに置き換わり、その回転がピストンを動かします。', '在驱动部件外两格放置Create轴，与曲柄轴向一致。它会变为受驱动轴，其转动带动活塞。'),
    'pipes': ('Feed gas at the top and collect compressed gas below. Cool it afterwards if needed.', '上面へ気体を入れ、下面から圧縮ガスを取り出します。必要なら後で冷却します。', '顶部供气，底部取压缩气体，需要时再冷却。'),
    'rotate': ('Drive at least 16 RPM. If stopped, check power, temperature and pressure.', '16 RPM以上で回します。止まったら動力・温度・圧力を確認します。', '输入至少16 RPM，停机时检查动力、温度和压力。'),
    'setting': ('Hold the wrench on the drive to set the target pressure. Engineer’s Goggles show the pressures and the status.', '駆動部をレンチで長押しして目標圧力を設定します。ゴーグルで圧力と状態を確認できます。', '用扳手长按驱动部件设置目标压力，工程师护目镜可查看压力和状态。'),
}
for lang, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
    p = A / 'lang' / f'{lang}.json'
    raw = p.read_bytes().decode('utf-8')
    newline = '\r\n' if '\r\n' in raw else '\n'
    data = json.loads(raw)
    for key, text in texts.items():
        data['create_refinery_n_reactors.ponder.shared.reciprocating_compressor.' + key] = text[index]
    data['create_refinery_n_reactors.ponder.reciprocating_compressor.header'] = texts['title'][index]
    p.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
