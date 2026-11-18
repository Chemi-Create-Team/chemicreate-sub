"""Assets of the asbestos-wrapped electrode plate: the item texture and model, the recipe, and the plate texture drawn inside the basins.

Usage: python generate_electrolysis_plate.py <resources dir>
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

MOD = "create_refinery_n_reactors"


def item_texture():
    image = Image.new("RGBA", (16, 16), (64, 60, 52, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((3, 1, 12, 14), fill=(176, 166, 148, 255), outline=(98, 90, 76, 255))
    draw.rectangle((4, 2, 11, 6), fill=(196, 188, 170, 255))
    draw.rectangle((5, 7, 10, 12), fill=(150, 140, 124, 255))
    draw.line((3, 4, 12, 4), fill=(98, 90, 76, 255))
    draw.rectangle((6, 0, 9, 1), fill=(120, 108, 84, 255))
    draw.rectangle((6, 9, 9, 13), fill=(122, 132, 146, 255), outline=(70, 78, 90, 255))
    draw.point((7, 10), fill=(190, 200, 214, 255))
    return image


def plate_texture():
    image = Image.new("RGBA", (16, 16), (190, 182, 164, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 15), outline=(104, 96, 82, 255))
    for x in range(2, 15, 3):
        draw.line((x, 1, x, 14), fill=(214, 206, 188, 255))
    return image


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main(resources):
    resources = Path(resources)
    assets = resources / f"assets/{MOD}"
    (assets / "textures/item").mkdir(parents=True, exist_ok=True)
    item_texture().save(assets / "textures/item/asbestos_wrapped_electrode.png")
    plate_texture().save(assets / "textures/block/electrolysis_plate.png")
    write(assets / "models/item/asbestos_wrapped_electrode.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/asbestos_wrapped_electrode"}})
    write(resources / f"data/{MOD}/recipe/asbestos_wrapped_electrode.json", {
        "type": "minecraft:crafting_shaped", "pattern": ["WCW", "WIW", "WCW"],
        "key": {"W": {"item": "minecraft:gray_wool"}, "C": {"item": "create:copper_sheet"}, "I": {"item": "create:iron_sheet"}},
        "result": {"id": f"{MOD}:asbestos_wrapped_electrode", "count": 4}})


if __name__ == "__main__":
    main(sys.argv[1])
