"""Shared 16px palette and integer-pixel geometry for the plant's machines."""
import math


def grain(name, x, y, roughness=1):
    """Fixed, low-contrast casting grain, with short horizontal machining marks.

    Integer pixels only: no blur, resampling, random seeds or high-res noise.
    Different alloys have different grain amplitude and surface patterns.
    """
    seed = sum((i+1)*ord(c) for i,c in enumerate(name))
    n = ((x*37 + y*71 + seed)*1103515245 + (x*y+17)*12345) & 0x7fffffff
    value = ((n >> 14) % 9) - 4
    if y in (5,10) and 4 <= x <= 11 and (x+seed)%4 < 2:
        value += 2
    return round(value*roughness)

PALETTE = {
    'stainless': (200, 208, 211), 'cast_iron': (114, 123, 128),
    'brass': (199, 158, 61), 'copper': (192, 119, 79),
    # Historical texture paths are kept, but the housings now use bare steel
    # variants rather than blue/green paint. Their low chroma survives GUI shade.
    'paint_green': (179, 185, 181), 'paint_blue': (181, 190, 194),
    'rubber': (49, 60, 67), 'vent': (168, 179, 185),
    'lamp_off': (76, 106, 107), 'lamp_on': (166, 245, 94),
    'ceramic': (206, 201, 178), 'catalyst_mesh': (199, 158, 61),
    'heat_grid': (202, 113, 58), 'sight_glass': (130, 166, 170),
    'refinery_steel': (194, 203, 207), 'teal_enamel': (180, 188, 186),
    'cream_enamel': (198, 192, 164), 'refractory': (162, 143, 115),
    'burner': (206, 113, 57), 'oil_shale': (111, 111, 115),
    'asphalt': (65, 72, 80), 'industrial_glass': (146, 225, 230),
    'resin_pipe': (192, 185, 157), 'cable_insulation': (61, 72, 77),
    'reinforced_pipe': (194, 203, 207),
}

# Reflection colors retain the alloy's hue. Broad highlights, dark folds and
# directional machining grain distinguish metal from uniformly mottled stone.
METAL_REFLECTION = {
    'brass': ((103, 70, 24), (255, 237, 164)),
    'copper': ((101, 54, 33), (250, 197, 151)),
    'catalyst_mesh': ((103, 70, 24), (255, 237, 164)),
    'cast_iron': ((51, 62, 69), (202, 212, 215)),
    **{name: ((92, 106, 115), (250, 253, 251)) for name in (
        'stainless', 'paint_green', 'paint_blue', 'refinery_steel',
        'teal_enamel', 'vent', 'reinforced_pipe')},
}


def metal_pixel(name, color, x, y):
    """A 16px brushed alloy sheet with actual light/dark reflection bands."""
    shadow, highlight = METAL_REFLECTION[name]
    bands = (0, 15, 28, 38, 25, 10, 0, -9, -16, -10, -2, 8, 20, 12, 0, -20)
    shade = bands[(x+y//5) % 16] + (5 if y < 6 else -4 if y > 10 else 0)
    shade += grain(name, x, y, 1.8 if name == 'cast_iron' else .9)
    shade += (-2, 1, 0, 2)[y % 4]
    # Machined, one-pixel bevels, not a single flat outline around every alloy.
    if x == 0 or y == 0: shade = -37
    if x == 1 or y == 1: shade = 58
    if x == 15 or y == 15: shade = -46
    if x == 14 or y == 14: shade = -14
    if y == 12 and 3 <= x <= 12: shade -= 18
    # Rivet heads have a lit upper lip and a recessed lower half.
    for rx, ry in ((3, 3), (11, 3), (3, 13), (11, 13)):
        if (x,y) == (rx,ry): shade = 62
        elif (x,y) == (rx+1,ry): shade = 16
        elif (x,y) in ((rx,ry+1),(rx+1,ry+1)): shade = -41
    target = highlight if shade >= 0 else shadow
    amount = min(abs(shade)/64, 1)
    return tuple(round(c+(t-c)*amount) for c,t in zip(color,target))+(255,)


def pixels(name, color, style='metal'):
    color = PALETTE.get(name, color)
    result = []
    for y in range(16):
        row = []
        for x in range(16):
            if style == 'metal' and name in METAL_REFLECTION:
                row.append(metal_pixel(name,color,x,y))
                continue
            # Broad edge highlights sit above a restrained, repeatable metal grain.
            shade = 12 if x == 1 or y == 1 else -20 if x == 15 or y == 15 else 0
            if style == 'metal':
                rough = 2.4 if name in ('cast_iron','refinery_steel') else 1.8 if name in ('stainless','paint_blue','paint_green') else 1.3
                shade += grain(name,x,y,rough)
                # Folded sheet edge, four rivets, and a short worn highlight.
                if y == 12 and 3 <= x <= 12: shade = -16
                if (x,y) in ((3,3),(12,3),(3,14),(12,14)): shade = -36
                if (x,y) in ((3,2),(12,2),(6,5),(7,5)): shade = 23
            base, alpha = color, 255
            if style == 'hazard':
                base = (247, 203, 71) if ((x+y)//4)%2 else (61, 74, 82)
                shade = 0
            elif style == 'gauge':
                gx, gy = x//4, y//4
                base = (248, 241, 205) if gx in (1,2) and gy in (1,2) else (79,95,103)
                if (gx,gy) == (2,1): base = (231,98,67)
                shade = 0
            elif style == 'vent':
                shade = -48 if y in (4,5,10,11) and 3 <= x <= 12 else shade
                if shade != -48: shade += grain(name,x,y,1.7)
                if name in METAL_REFLECTION:
                    c = (49,60,67,255) if shade == -48 else metal_pixel(name,color,x,y)
                    row.append(c)
                    continue
            elif style in ('ceramic', 'ore'):
                shade = -24 if y in (0,8) or (x+(8 if y<8 else 0))%16 == 0 else shade
            elif style == 'mesh':
                shade = -46 if x in (4,5,10,11) or y in (4,5,10,11) else shade
            elif style == 'hot':
                shade = 28 if x in (4,5,10,11) else -8
            elif style in ('glass', 'laminate'):
                if style == 'laminate':
                    alpha = 255 if min(x,y,15-x,15-y)<2 else 180 if x-y in (3,4) else 0
                shade = 24 if x-y in (3,4) else shade
            elif style == 'rubber':
                shade = 8 if x in (2,3) else 0
            elif style == 'asphalt':
                shade = 7 if (x//4+y//4)%2 else 0
            row.append(tuple(max(0,min(255,c+shade)) for c in base)+(alpha,))
        result.append(row)
    return result


def box(elements, a, b, material, faces=None):
    assert all(isinstance(n, int) for n in a+b), (a,b)
    x,y,z = a; xx,yy,zz = b
    uv = {'up':[x,z,xx,zz], 'down':[x,z,xx,zz],
          'north':[x,16-yy,xx,16-y], 'south':[x,16-yy,xx,16-y],
          'west':[z,16-yy,zz,16-y], 'east':[z,16-yy,zz,16-y]}
    elements.append({'from':a, 'to':b, 'faces':{
        side:{'texture':'#'+(faces or {}).get(side,material),
              'uv':[0,0,16,16] if (faces or {}).get(side) in ('gauge','indicator') else coords}
        for side,coords in uv.items()}})


def vessel(material='stainless', height=14):
    e=[]
    box(e,[2,0,2],[14,2,14],'cast_iron')
    box(e,[3,2,3],[13,height-1,13],material)
    box(e,[2,3,2],[14,5,14],'brass')
    box(e,[2,height-3,2],[14,height-1,14],'brass')
    box(e,[4,height-1,4],[12,height,12],material)
    ports(e)
    return e


def ports(e, y=7):
    box(e,[0,y-1,5],[3,y+5,11],'brass')
    box(e,[0,y,6],[1,y+4,10],'rubber')
    box(e,[13,y-1,5],[16,y+5,11],'brass')
    box(e,[15,y,6],[16,y+4,10],'rubber')


def panel(e, lit=True):
    box(e,[5,6,1],[11,12,3],'cast_iron',{'north':'gauge'})
    if lit: box(e,[11,5,2],[13,7,3],'indicator')


def column(section, count, material='stainless'):
    e=[]
    box(e,[3,0,3],[13,16 if section<count-1 else 12,13],material)
    box(e,[2,0,2],[14,2,14],'brass')
    if section<count-1:box(e,[2,14,2],[14,16,14],'brass')
    # One broad sight glass and a large ladder, no tiny guardrails or bolt fields.
    box(e,[5,4,2],[11,11,3],'sight_glass')
    for x in (4,11):box(e,[x,0,14],[x+1,16,15],'cast_iron')
    for y in (3,8,13):box(e,[5,y,14],[11,y+1,15],'brass')
    if section==0:
        box(e,[1,0,1],[15,2,15],'cast_iron');ports(e,5);panel(e)
    elif section==count-1:
        box(e,[5,12,5],[11,14,11],'brass')
        box(e,[7,14,7],[9,16,9],'copper')
        box(e,[12,7,6],[16,11,10],'brass')
    else:
        box(e,[2,7,2],[14,9,14],'paint_blue' if section%2 else 'paint_green')
    return e
