#!/usr/bin/env python3
"""Horizontal fluid tank Ponder: a 2x2 tank four blocks long lying along X over Blaze Burners, a pipe at its end; English/Japanese
captions. The tank's controller data is set by HorizontalTankPonderScenes. Run from the repository root."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS
from generate_petrochemical_ponder import Structure, OUT

s = Structure(9, 5)
for x in range(2, 6):
    for y in (2, 3):
        for z in (3, 4):
            s.block(x, y, z, NS + 'horizontal_fluid_tank', axis='x', bottom=str(x == 2).lower(), top=str(x == 5).lower(), shape='plain')
    for z in (3, 4): s.burner(x, 1, z)
s.block(1, 2, 3, 'create:fluid_pipe', east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
s.pump(0, 2, 3, 'west')
s.pump(3, 2, 2, 'south')
s.save('horizontal_tank')
(A / 'ponder/plant/horizontal_tank.nbt').write_bytes((OUT / 'horizontal_tank.nbt').read_bytes())
(OUT / 'horizontal_tank.nbt').unlink()

TEXTS = {
    'title': ('Horizontal fluid tank', '横型流体タンク'),
    'place': ('Place along your facing direction. Place against a tank to extend it; sneak to start another direction.',
        '向いている方向に横置きします。既設タンクへ置くと延長し、スニークで別の向きにできます。'),
    'size': ('The section is 1x1 to 3x3, and the length runs up to Create\'s tank height limit. Only tanks lying along the same axis join. It has no windows.',
        '断面は1×1～3×3で、長さはCreateのタンクの高さの上限まで延ばせます。同じ向きに寝かせたタンクとだけつながります。窓はありません。'),
    'boiler': ('Add Blaze Burners below the ends of the rows to make a boiler.',
        '列の末端の下にブレイズバーナーを付けるとボイラーになります。'),
    'heating': ('The Create gauge shows how much steam the boiler is holding.',
        'Create標準の計器の針は蒸気の充填率を示します。'),
    'pipe': ('Feed water from the long side. Pump steam from the top or either end.',
        '長辺の側面から給水します。上面か両端面からポンプで蒸気を取り出します。'),
}
for lang, index in (('en_us', 0), ('ja_jp', 1)):
    p = A / 'lang' / f'{lang}.json'
    raw = p.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    strings = json.loads(raw)
    for key, text in TEXTS.items(): strings[f'create_refinery_n_reactors.ponder.shared.horizontal_tank.{key}'] = text[index]
    strings['create_refinery_n_reactors.ponder.horizontal_tank.header'] = TEXTS['title'][index]
    p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
