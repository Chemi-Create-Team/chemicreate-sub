"""Merged view of the core, atomics and logistics resource roots (first root wins for files)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = 'create_refinery_n_reactors'
ROOTS = [ROOT / base / 'src/main/resources/assets' / NAMESPACE for base in ('.', 'atomics', 'logistics')]


def find(relative):
    return next((root / relative for root in ROOTS if (root / relative).is_file()), ROOTS[0] / relative)


def load_json(relative):
    merged = None
    for root in ROOTS:
        if not (root / relative).is_file():
            continue
        data = json.loads((root / relative).read_text())
        if merged is None:
            merged = data
        elif isinstance(data, list):
            merged = merged + data
        else:
            merged = {**merged, **data}
    assert merged is not None, relative
    return merged


def glob(relative_dir, pattern):
    found = {}
    for root in ROOTS:
        for file in (root / relative_dir).glob(pattern):
            found.setdefault(file.name, file)
    return sorted(found.values())


def rglob(relative_dir, pattern):
    return sorted({file for root in ROOTS for file in (root / relative_dir).rglob(pattern)})
