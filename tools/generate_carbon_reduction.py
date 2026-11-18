#!/usr/bin/env python3
"""Nickel, ferrochrome and stainless steel in Create's open basin, before any stainless machine exists.

Pentlandite is roasted in the air over a heated basin; its sulfur leaves as gas. Nickel oxide and chromite are reduced with carbon
powder over a superheated basin. Carbon cannot strip chromite's iron, so it gives ferrochrome, the iron-chromium alloy real stainless
steel is made from; its iron counts towards the steel. Run from the repository root.
"""
from PIL import Image
from generate_reactor_assets import A, D, NS, write

TEXTS = {'item.create_refinery_n_reactors.ferrochrome': ('Ferrochrome', 'フェロクロム', '铬铁')}


def mixing(name, ingredients, results, heat):
    write(D / f'recipe/mixing/{name}.json', {'type': 'create:mixing', 'ingredients': ingredients, 'results': results, 'heat_requirement': heat})


def recipes():
    mixing('nickel_oxide_roasted_in_air', [{'item': NS + 'pentlandite'}] * 2,
           [{'id': NS + 'nickel_oxide'}, {'id': 'minecraft:iron_nugget', 'count': 3}], 'heated')
    mixing('nickel_ingot_reduced_with_carbon', [{'item': NS + 'nickel_oxide'}, {'item': NS + 'carbon_powder'}], [{'id': NS + 'nickel_ingot'}], 'superheated')
    mixing('ferrochrome', [{'item': NS + 'chromite'}] * 2 + [{'item': NS + 'carbon_powder'}], [{'id': NS + 'ferrochrome'}], 'superheated')
    # The same alloy as seven iron, two chromium and one nickel: two ferrochromes carry about one iron between them.
    mixing('stainless_steel_from_ferrochrome', [{'tag': 'c:ingots/iron'}] * 6 + [{'item': NS + 'ferrochrome'}] * 2 + [{'tag': 'c:ingots/nickel'}],
           [{'id': NS + 'stainless_steel_ingot', 'count': 10}], 'superheated')


def assets():
    image = Image.open(A / 'textures/item/chromium_ingot.png').convert('RGBA')
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            if a:
                shade = (r * 299 + g * 587 + b * 114) // 1000 * 3 // 4
                image.putpixel((x, y), (shade + 6, shade + 4, shade, a))
    image.save(A / 'textures/item/ferrochrome.png')
    write(A / 'models/item/ferrochrome.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/ferrochrome'}})


def translations():
    import json
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / f'{language}.json'
        strings = json.loads(path.read_text(encoding='utf-8'))
        strings.update({key: text[index] for key, text in TEXTS.items()})
        write(path, strings)


if __name__ == '__main__':
    recipes(); assets(); translations()
    print('Generated roasting in air, carbon reduction to nickel and ferrochrome, and stainless steel from ferrochrome')
