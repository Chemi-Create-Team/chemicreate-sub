#!/usr/bin/env python3
"""Keep machine subtitles and the real-world sound-operation Ponder note reproducible."""
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors'
TEXTS = {
    'reactor_circulation': ('Reactor hums', '原子炉の運転音'),
    'fusion_drive': ('Fusion reactor hums', '核融合炉の運転音'),
    'turbine_high': ('High-pressure turbine spins', '高圧タービンが回転する'),
    'turbine_low': ('Low-pressure turbine spins', '低圧タービンが回転する'),
    'compressor': ('Compressor runs', '圧縮機が作動する'),
    'agitator': ('Agitator or centrifuge spins', '撹拌・遠心機構が回転する'),
    'hot_process': ('Heated process runs', '高温処理設備が作動する'),
    'fluid_process': ('Fluid process runs', '流体処理設備が作動する'),
    'blower': ('Cooling fans spin', '冷却ファンが回転する'),
    'crane_drive': ('Crane drive moves', 'クレーンの駆動部が動く'),
}
NOTE = ('Adjust running sounds with the Blocks volume setting.',
        '駆動音は「ブロック」の音量設定で調整できます。',
        '运行声音可在“方块”音量中调整。')
for locale, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
    path = ASSETS / 'lang' / (locale + '.json')
    raw = path.read_bytes().decode('utf-8')
    newline = '\r\n' if '\r\n' in raw else '\n'
    values = json.loads(raw)
    if index < 2:
        for name, pair in TEXTS.items(): values['subtitles.create_refinery_n_reactors.machine.' + name] = pair[index]
    values['create_refinery_n_reactors.ponder.shared.turbine_multiblock.operating_sound'] = NOTE[index]
    path.write_bytes((json.dumps(values, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
