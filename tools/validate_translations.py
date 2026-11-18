#!/usr/bin/env python3
"""Require complete language key sets and matching printf arguments in all shipped locales."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/lang'
ARGUMENT = re.compile(r'%%|%(?:\d+\$)?(?:\d+)?(?:\.\d+)?[sdf]')

def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        assert key not in result, f'Duplicate translation: {key}'
        assert isinstance(value, str) and value.strip(), f'Empty translation: {key}'
        result[key] = value
    return result

reference = json.loads((ROOT / 'en_us.json').read_text(), object_pairs_hook=unique_pairs)
for locale in ('ja_jp', 'zh_cn'):
    translated = json.loads((ROOT / (locale + '.json')).read_text(), object_pairs_hook=unique_pairs)
    assert reference.keys() == translated.keys(), f'{locale}: missing {sorted(reference.keys() - translated.keys())}; extra {sorted(translated.keys() - reference.keys())}'
    for key, value in translated.items():
        assert [arg for arg in ARGUMENT.findall(reference[key]) if arg != '%%'] == [arg for arg in ARGUMENT.findall(value) if arg != '%%'], f'{locale}: argument mismatch in {key}'
print(f'PASS: {len(reference)} keys in English, Japanese and Chinese; no duplicates, blanks or printf mismatches')
