"""Blockstate, item model and recipes of the horizontal liquid tank: the liquid tank's own models turned onto their side.

Usage: python generate_horizontal_tank.py <resources dir>
"""
import json
import sys
from pathlib import Path

MOD = "create_refinery_n_reactors"
SHAPES = ("plain", "window", "window_ne", "window_nw", "window_se", "window_sw")
# The model's top end must land on the positive end of the axis, which is where Create's TOP flag counts to.
TURNS = {"x": {"x": 270, "y": 270}, "z": {"x": 270}}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main(resources):
    resources = Path(resources)
    variants = {}
    for axis, turn in TURNS.items():
        for bottom in (False, True):
            for top in (False, True):
                part = "single" if bottom and top else "bottom" if bottom else "top" if top else "middle"
                for shape in SHAPES:
                    key = f"axis={axis},bottom={str(bottom).lower()},shape={shape},top={str(top).lower()}"
                    variants[key] = {"model": f"create:block/fluid_tank/block_{part}", **turn}
    write(resources / f"assets/{MOD}/blockstates/horizontal_fluid_tank.json", {"variants": variants})
    write(resources / f"assets/{MOD}/models/block/horizontal_tank_item.json", {'parent': 'create:block/fluid_tank/block_single',
     'display': {'gui': {'rotation': [30, 225, 90], 'scale': [0.625, 0.625, 0.625]},
                 'ground': {'rotation': [0, 0, 90], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
                 'fixed': {'rotation': [0, 0, 90], 'scale': [0.5, 0.5, 0.5]},
                 'thirdperson_righthand': {'rotation': [75, 45, 90],
                                           'translation': [0, 2.5, 0],
                                           'scale': [0.375, 0.375, 0.375]},
                 'thirdperson_lefthand': {'rotation': [75, 45, 90],
                                          'translation': [0, 2.5, 0],
                                          'scale': [0.375, 0.375, 0.375]},
                 'firstperson_righthand': {'rotation': [0, 45, 90], 'scale': [0.4, 0.4, 0.4]},
                 'firstperson_lefthand': {'rotation': [0, 225, 90], 'scale': [0.4, 0.4, 0.4]}}})
    write(resources / f"assets/{MOD}/models/item/horizontal_fluid_tank.json", {"parent": f"{MOD}:block/horizontal_tank_item"})
    loot = {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:horizontal_fluid_tank"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
    write(resources / f"data/{MOD}/loot_table/blocks/horizontal_fluid_tank.json", loot)
    for name, source, result in (("horizontal_fluid_tank", "fluid_tank", "horizontal_fluid_tank"), ("stand_fluid_tank", "horizontal_fluid_tank", "fluid_tank")):
        write(resources / f"data/{MOD}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": source if ":" in source else (f"create:{source}" if source == "fluid_tank" else f"{MOD}:{source}")}], "result": {"id": result if ":" in result else (f"create:{result}" if result == "fluid_tank" else f"{MOD}:{result}"), "count": 1}})


if __name__ == "__main__":
    main(sys.argv[1])
