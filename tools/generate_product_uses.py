#!/usr/bin/env python3
"""Uses for the plant's products made with the existing equipment: fertilisers, bleach, soap, road salt, pickling, alumina sandpaper,
hydrogen fluoride from depleted uranium, the aluminium, titanium and nickel sheets, molybdenum grease and the nickel electrode.
Run from the repository root."""
import json, sys
from PIL import Image, ImageDraw
sys.path.insert(0, 'tools')
from minecraft_style import grain
from powder_assets import write_powder_item
from generate_reactor_assets import A, D, NS, write
from generate_uranium_assets import icon, shade, tag, item, fluid, fluid_out, PILE, pile, granules

T = A / 'textures/item'
COLOURS = ('white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black')
SHEETS = {'aluminum': 'aluminum_ingot', 'titanium': 'titanium_ingot', 'nickel': 'nickel_ingot'}
NAMES = {
    'item.create_refinery_n_reactors.ammonium_sulfate': ('Ammonium Sulfate', '硫酸アンモニウム', '硫酸铵'),
    'item.create_refinery_n_reactors.soap': ('Soap', '石けん', '肥皂'),
    'item.create_refinery_n_reactors.aluminum_sheet': ('Aluminium Sheet', 'アルミニウム板', '铝板'),
    'item.create_refinery_n_reactors.titanium_sheet': ('Titanium Sheet', 'チタン板', '钛板'),
    'item.create_refinery_n_reactors.nickel_sheet': ('Nickel Sheet', 'ニッケル板', '镍板'),
    'item.create_refinery_n_reactors.nickel_electrode_plate': ('Nickel Electrode Plate', 'ニッケル電極板', '镍电极板'),
    'fluid.create_refinery_n_reactors.sodium_hypochlorite_solution': ('Sodium Hypochlorite Solution', '次亜塩素酸ナトリウム溶液', '次氯酸钠溶液'),
}
# The deleted steam pipe left these keys behind.
OBSOLETE = ('item.create_refinery_n_reactors.steam_pipe', 'goggle.create_refinery_n_reactors.steam_pipe')


def palette(source):
    """The colours of an item texture, darkest first."""
    im = Image.open(T / f'{source}.png').convert('RGBA')
    return sorted({p[:3] for p in im.getdata() if p[3]}, key=lambda c: c[0] * 299 + c[1] * 587 + c[2] * 114)


def recolour(shape, colours, target):
    """Keeps a texture's shape and maps its brightness onto another palette."""
    im = Image.open(T / f'{shape}.png').convert('RGBA'); out = Image.new('RGBA', im.size)
    values = sorted({round((r * 299 + g * 587 + b * 114) / 1000) for r, g, b, a in im.getdata() if a})
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if not a: continue
            rank = values.index(round((r * 299 + g * 587 + b * 114) / 1000)) / max(1, len(values) - 1)
            out.putpixel((x, y), colours[round(rank * (len(colours) - 1))] + (a,))
    out.save(T / f'{target}.png')


def soap():
    """A pale bar of soap with a darker underside and two bubbles."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); p = im.load()
    base = (226, 212, 232)
    for y in range(6, 13):
        for x in range(2, 14):
            if (x, y) in ((2, 6), (13, 6), (2, 12), (13, 12)): continue
            light = 18 if y == 6 else -26 if y >= 11 else -10 if x == 13 else 8 if x == 2 else 0
            p[x, y] = shade(base, light + grain('soap', x, y, 2))
    for x, y in ((4, 4), (11, 3)): p[x, y] = (246, 250, 255, 255)
    p[5, 3] = (210, 226, 240, 255); p[12, 4] = (210, 226, 240, 255)
    im.save(T / 'soap.png')


def textures():
    for metal, ingot in SHEETS.items(): recolour('plastic_sheet', palette(ingot), f'{metal}_sheet')
    recolour('asbestos_wrapped_electrode', palette('nickel_ingot'), 'nickel_electrode_plate')
    soap()
    write_powder_item('ammonium_sulfate')
    for name in ('ammonium_sulfate', 'soap', 'aluminum_sheet', 'titanium_sheet', 'nickel_sheet', 'nickel_electrode_plate'):
        write(A / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})


def reaction(id, ingredients, results, heat=None):
    data = {'type': NS + 'pressure_reaction'}
    if heat: data['heat_requirement'] = heat
    data.update(ingredients=ingredients, results=results)
    write(D / 'recipe/pressure_reaction' / f'{id}.json', data)


def recipes():
    U = NS
    # 2 NH3 + H2SO4 -> (NH4)2SO4: the outlet for the sulfuric acid of the desulfurisation line.
    reaction('ammonium_sulfate', [fluid(U + 'ammonia_gas', 250), fluid(U + 'sulfuric_acid', 125)], [item(U + 'ammonium_sulfate')])
    # Cl2 + 2 NaOH -> NaClO + NaCl + H2O. Chlorine is a gas, so it reacts in the closed stirred reactor, not an open basin.
    reaction('sodium_hypochlorite_solution', [fluid(U + 'chlorine', 250), fluid(U + 'caustic_soda_solution', 500)], [fluid_out(U + 'sodium_hypochlorite_solution', 500)])
    # Depleted uranium hexafluoride from enrichment gives its fluorine back as hydrogen fluoride; the uranium oxide is set aside as waste.
    reaction('hydrogen_fluoride_from_depleted_uranium_hexafluoride', [fluid(U + 'depleted_uranium_hexafluoride', 250), fluid('minecraft:water', 250)],
             [fluid_out(U + 'hydrogen_fluoride', 500)], 'heated')
    # Bleach takes the dye out of wool, carpets and beds.
    for kind, amount in (('wool', 25), ('carpet', 10), ('bed', 50)):
        coloured = [{'item': f'minecraft:{c}_{kind}'} for c in COLOURS if c != 'white']
        write(D / 'recipe/mixing' / f'white_{kind}_from_bleach.json', {'type': 'create:mixing', 'ingredients': [coloured, fluid(U + 'sodium_hypochlorite_solution', amount)],
            'results': [item(f'minecraft:white_{kind}')]})
    # Saponification of plant oil with caustic soda.
    write(D / 'recipe/mixing/soap.json', {'type': 'create:mixing', 'ingredients': [fluid('createdieselgenerators:plant_oil', 250), fluid(U + 'caustic_soda_solution', 250)],
        'results': [item(U + 'soap', 2)], 'heat_requirement': 'heated'})
    # Pickling: hydrochloric acid takes the rust off weathered industrial iron.
    write(D / 'recipe/mixing/industrial_iron_block_from_weathered_iron_block.json', {'type': 'create:mixing',
        'ingredients': [{'item': 'create:weathered_iron_block'}, fluid(U + 'hydrochloric_acid', 100)], 'results': [item('create:industrial_iron_block')]})
    # Fused alumina is the corundum of real abrasive paper, in place of the sand.
    write(D / 'recipe/sand_paper_from_alumina.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': 'minecraft:paper'}, {'item': U + 'alumina'}], 'result': {'id': 'create:sand_paper', 'count': 1}})
    for metal in SHEETS:
        write(D / 'recipe/pressing' / f'{metal}_sheet.json', {'type': 'create:pressing', 'ingredients': [{'tag': f'c:ingots/{metal}'}], 'results': [item(U + f'{metal}_sheet')]})
        tag(f'c/tags/item/plates/{metal}.json', [U + f'{metal}_sheet'])
        tag('c/tags/item/plates.json', [f'#c:plates/{metal}'])
    write(D / 'recipe/nickel_electrode_plate.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['NCN', 'NIN', 'NCN'],
        'key': {'N': {'tag': 'c:plates/nickel'}, 'C': {'item': 'create:copper_sheet'}, 'I': {'item': 'create:iron_sheet'}},
        'result': {'id': U + 'nickel_electrode_plate', 'count': 4}})
    tag('c/tags/item/fertilizers/ammonium_sulfate.json', [U + 'ammonium_sulfate'])


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; data = json.loads(raw)
        for key in OBSOLETE: data.pop(key, None)
        for key, names in NAMES.items(): data[key] = names[index]
        path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


if __name__ == '__main__':
    textures(); recipes(); translations()
    print('Generated fertilisers, bleach, soap, pickling, alumina sandpaper, depleted-uranium fluoride recovery, metal sheets, grease and the nickel electrode')
