"""腐食性薬液の接触ダメージと耐性タグを生成する。"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/data"
NAMESPACE = "create_refinery_n_reactors"
IDENTIFIER = f"{NAMESPACE}:corrosive_contact"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def generate():
    write(ROOT / NAMESPACE / "damage_type/corrosive_contact.json", {
        "message_id": f"{NAMESPACE}.corrosive_contact",
        "scaling": "never",
        "exhaustion": 0.0,
    })
    for name in ("bypasses_armor", "no_knockback"):
        path = ROOT / "minecraft/tags/damage_type" / f"{name}.json"
        value = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
        if IDENTIFIER not in value["values"]:
            value["values"].append(IDENTIFIER)
        write(path, value)


if __name__ == "__main__":
    generate()
