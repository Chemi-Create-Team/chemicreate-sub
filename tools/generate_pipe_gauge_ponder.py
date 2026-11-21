#!/usr/bin/env python3
"""Pipe thermometer, pressure gauge and flow meter Ponder: a pumped liquid line and a gas pipe line, with English/Japanese captions."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS
from generate_petrochemical_ponder import Structure, OUT

SIDES = ('east', 'west', 'north', 'south', 'up', 'down')


def pipe(s, x, y, z, name, *sides):
    s.block(x, y, z, name, **{side: str(side in sides).lower() for side in SIDES}, waterlogged='false')


def tank(s, x, y, z):
    s.block(x, y, z, 'create:fluid_tank', bottom='true', top='true', shape='window')


s = Structure(7, 3)
# Liquid line: tank, pump, thermometer, flow meter, pressure gauge, tank.
tank(s, 0, 1, 2)
s.pump(1, 1, 2, 'east')
s.block(2, 1, 2, NS + 'pipe_thermometer', axis='x')
s.block(3, 1, 2, NS + 'pipe_flow_meter', axis='x')
s.block(4, 1, 2, NS + 'pipe_pressure_gauge', axis='x')
tank(s, 5, 1, 2)
s.block(4, 2, 2, 'minecraft:redstone_lamp', lit='false')
# Gas line: two tanks joined by gas pipes with a pressure gauge between them.
tank(s, 0, 1, 5)
pipe(s, 1, 1, 5, NS + 'reinforced_pipe', 'east', 'west')
pipe(s, 2, 1, 5, NS + 'reinforced_pipe', 'east', 'west')
s.block(3, 1, 5, NS + 'pipe_pressure_gauge', axis='x')
pipe(s, 4, 1, 5, NS + 'reinforced_pipe', 'east', 'west')
tank(s, 5, 1, 5)
s.save('pipe_gauges')
(A / 'ponder/plant/pipe_gauges.nbt').write_bytes((OUT / 'pipe_gauges.nbt').read_bytes())
(OUT / 'pipe_gauges.nbt').unlink()

texts = {
    'title': ('Reading pipes with gauges', '配管の温度・圧力・流量を測る'),
    'place': ('The pipe thermometer, pressure gauge and flow meter go into a pipe run, in any direction. What flows passes straight through them.',
        '配管温度計・配管圧力計・配管流量計は、どの向きでも配管の途中に置けます。流体はそのまま通り抜けます。'),
    'reading': ('Run fluid through the gauges to read its temperature and line pressure.',
        '流体を流すと、その場所の温度や配管内の圧力を読み取れます。'),
    'scale': ('The thermometer reads 0–1000 °C; the pressure gauge reads 0–300 bar.',
        '温度計は0～1000 °C、圧力計は0～300 barを表示します。'),
    'flow': ('The flow meter shows pipe throughput from 0 to 1000 mB/t.',
        '流量計は、配管を通る量を0～1000 mB/tで表示します。'),
    'stale': ('Without flow, temperature becomes unmeasured. Pressure gauges directly on vessels still work.',
        '流れが止まると温度は未測定になります。容器へ直結した圧力計は停止中も使えます。'),
    'gas': ('These gauges also work on gas pipes.',
        '各計器は気体パイプにも使えます。'),
    'alarm_set': ('Hold a wrench on a gauge face to set its alarm contact: the row chooses a signal above or below, the column the threshold.',
        '計器の面をレンチで長押しすると警報接点を設定できます。行で「超えたら出力」か「下回ったら出力」を、列でしきい値を選びます。'),
    'alarm_signal': ('When the condition is met, the gauge emits redstone through its non-pipe faces.',
        '設定条件を満たすと、配管以外の面へレッドストーン信号を出します。'),
    'display': ('Engineer\'s Goggles show the reading and the alarm. A Display Link shows the reading as Pipe fluid, Line pressure or Net flow.',
        'ゴーグルで値と警報を確認できます。ディスプレイリンクでは、値を「流体温度」「配管区間圧力」「正味流量」として表示できます。'),
}
for lang, index in (('en_us', 0), ('ja_jp', 1)):
    p = A / 'lang' / f'{lang}.json'
    raw = p.read_bytes().decode('utf-8')
    newline = '\r\n' if '\r\n' in raw else '\n'
    strings = json.loads(raw)
    for key, text in texts.items():
        strings[f'create_refinery_n_reactors.ponder.shared.pipe_gauges.{key}'] = text[index]
    strings['create_refinery_n_reactors.ponder.pipe_gauges.header'] = texts['title'][index]
    p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
