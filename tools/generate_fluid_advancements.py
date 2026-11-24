#!/usr/bin/env python3
"""One advancement for each fluid and gas bucket, under two library entries in the guide tab. Run from the repository root.

The buckets come from the language file and skip the retired state entries (docs/fluid-states.md), which load as their substance's bucket. Titles are the
fluid's own name, so only the descriptions are written here. Unlike generate_manual.py this script touches only advancement/fluids/ and its own language keys.
"""
import json
import re
from pathlib import Path

from generate_process_gas_buckets import gas_entries

R = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors'
A = R / 'src/main/resources/assets' / NS
FOLDER = R / 'src/main/resources/data' / NS / 'advancement/fluids'
LANGUAGES = ('en_us', 'ja_jp', 'zh_cn')
# Buckets that are not a fluid's own, and gases registered by hand rather than generated.
NOT_FLUIDS = {'corrosion_resistant', 'stainless_steel'}
HAND_GASES = {'hydrogen_gas', 'oxygen_gas', 'nitrogen_gas', 'argon_gas', 'converter_gas', 'air_gas', 'ammonia_gas', 'ethylene', 'propylene',
              'nitric_oxide', 'nitrogen_dioxide', 'deuterium_gas', 'tritium_gas', 'helium_gas', 'helium_recovery_feed'}
TEXT = {
    'liquids': (("Liquid library", "Fill a bucket with every liquid in the plant."),
                ("液体の図鑑", "工場のすべての液体を、バケツにくもう。"), ("液体图鉴", "把工厂里的每种液体都装进桶里。")),
    'gases': (("Gas library", "Bottle every gas in the plant."),
              ("気体の図鑑", "工場のすべての気体を、バケツに詰めよう。"), ("气体图鉴", "把工厂里的每种气体都装进桶里。")),
    'liquid': ("Fill a bucket with %s.", "%sをバケツにくむ。", "用桶装取%s。"),
    'gas': ("Bottle %s in a bucket.", "%sをバケツに詰める。", "把%s装进桶里。"),
}


def fluids():
    lang = json.loads((A / 'lang/en_us.json').read_text(encoding='utf-8'))
    buckets = sorted(k.split('.', 2)[2][:-7] for k in lang if k.startswith(f'item.{NS}.') and k.endswith('_bucket'))
    retired = set(re.findall(r'(?:water|phase|hot|alias)\("(\w+)"', (R / 'src/main/java/net/createplant/thermal/FluidIdentity.java').read_text()))
    gases = {entry['name'] for entry in gas_entries()} | HAND_GASES
    names = [b for b in buckets if b not in retired and b not in NOT_FLUIDS]
    for name in names: assert f'fluid.{NS}.{name}' in lang, name
    return [(n, n in gases) for n in names]


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def advancement(parent, icon, frame, title, description, bucket=None):
    result = {'display': {'icon': {'id': icon}, 'title': title, 'description': description, 'frame': frame, 'show_toast': True, 'announce_to_chat': False},
              'parent': parent}
    if bucket:
        result['criteria'] = {'bucket': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': bucket}]}}}
        result['requirements'] = [['bucket']]
    else:
        result['criteria'] = {'bucket': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': 'minecraft:water_bucket'}]}}}
    return result


def main():
    entries = fluids() + [('water', False)]
    for old in FOLDER.glob('*.json'): old.unlink()
    write(FOLDER / 'liquids.json', {**advancement(f'{NS}:guide/root', 'minecraft:water_bucket', 'goal',
                                                  {'translate': f'advancement.{NS}.fluid.liquids'}, {'translate': f'advancement.{NS}.fluid.liquids.description'}),
                                    'criteria': {'water': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': 'minecraft:water_bucket'}]}}},
                                    'requirements': [['water']]})
    write(FOLDER / 'gases.json', {**advancement(f'{NS}:guide/root', f'{NS}:nitrogen_gas_bucket', 'goal',
                                                {'translate': f'advancement.{NS}.fluid.gases'}, {'translate': f'advancement.{NS}.fluid.gases.description'}),
                                  'criteria': {'nitrogen': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': f'{NS}:nitrogen_gas_bucket'}]}}},
                                  'requirements': [['nitrogen']]})
    for name, gas in entries:
        bucket = 'minecraft:water_bucket' if name == 'water' else f'{NS}:{name}_bucket'
        kind = 'gas' if gas else 'liquid'
        title = {'translate': 'block.minecraft.water' if name == 'water' else f'fluid.{NS}.{name}'}
        description = {'translate': f'advancement.{NS}.fluid.{kind}', 'with': [title]}
        write(FOLDER / f'{name}.json', advancement(f'{NS}:fluids/{kind}s', bucket, 'task', title, description, bucket))
    for index, code in enumerate(LANGUAGES):
        path = A / 'lang' / f'{code}.json'
        kept = [line for line in path.read_text(encoding='utf-8').rstrip().splitlines() if not line.startswith(f'  "advancement.{NS}.fluid.')]
        assert kept[-1] == '}'
        pairs = {}
        for hub in ('liquids', 'gases'):
            pairs[f'advancement.{NS}.fluid.{hub}'] = TEXT[hub][index][0]
            pairs[f'advancement.{NS}.fluid.{hub}.description'] = TEXT[hub][index][1]
        pairs[f'advancement.{NS}.fluid.liquid'] = TEXT['liquid'][index]
        pairs[f'advancement.{NS}.fluid.gas'] = TEXT['gas'][index]
        body = kept[:-1]
        body[-1] = body[-1].rstrip(',') + ','
        added = [f'  {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)}' for k, v in pairs.items()]
        path.write_text('\n'.join(body + [',\n'.join(added), '}']) + '\n', encoding='utf-8')
    print(f'{len(entries)} fluid advancements ({sum(g for _, g in entries)} gases)')


if __name__ == '__main__':
    main()
