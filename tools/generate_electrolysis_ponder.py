#!/usr/bin/env python3
"""Working 2x2 filter-press assemblies and captions shared by the equipment and material guides."""
import json
from pathlib import Path
import generate_petrochemical_ponder as p
from generate_electrolysis_cell import write

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'


def pipe(s, x, y, z, *sides):
    s.block(x, y, z, NS + 'stainless_steel_pipe', waterlogged='false', **{side: str(side in sides).lower() for side in p.DIRECTIONS})


def pump(s, x, y, z, facing):
    s.pump(x, y, z, facing)
    s.block(x, y, z + 1, 'create:cogwheel', axis='x', waterlogged='false')
    s.block(x - 1, y, z + 1, 'create:creative_motor', facing='east')


def unit(length=4, width=11, origin=(4, 2, 4)):
    """A row of cells on an iron plinth, the panel at its north end; feed rises from below, the gases leave the two long sides."""
    s = p.Structure(width, 6)
    x, y, z = origin
    zz = z + 1 if length > 1 else z
    for n in range(length):
        joins = {'down': False, 'up': True, 'west': False, 'east': False, 'north': True, 'south': n < length - 1}
        s.block(x, y, z + n, NS + 'electrolysis_cell', axis='z', **{k: str(v).lower() for k, v in joins.items()})
        s.block(x, y + 1, z + n, NS + 'electrolysis_electrode_head', axis='z')
        if z + n != zz: s.block(x, y - 1, z + n, 'create:industrial_iron_block')
    for part in range(2): s.block(x, y + part, z - 1, NS + 'electrolysis_cell_controller', facing='north', part=str(part))
    # Feed from below, hydrogen-family gas west, oxygen-family gas east, caustic soda or recovered acid out of the far end.
    s.block(x - 3, y - 1, zz, 'create:fluid_tank', bottom='true', top='true', shape='window')
    pump(s, x - 2, y - 1, zz, 'east');pipe(s, x - 1, y - 1, zz, 'west', 'east');pipe(s, x, y - 1, zz, 'west', 'up')
    for px, facing in ((x - 1, 'west'), (x + 1, 'east')):
        pipe(s, px, y, zz, 'east', 'west')
        pump(s, px - 1 if facing == 'west' else px + 1, y, zz, facing)
        s.block(px - 2 if facing == 'west' else px + 2, y, zz, NS + 'gas_receiver', bottom='true', top='true', shape='plain')
    end = z + length
    pipe(s, x, y, end, 'north', 'south');s.pump(x, y, end + 1, 'south')
    s.block(x + 1, y, end + 1, 'create:cogwheel', axis='z', waterlogged='false');s.block(x + 1, y, end + 2, 'create:creative_motor', facing='north')
    s.block(x, y, end + 2, 'create:fluid_tank', bottom='true', top='true', shape='window')
    s.block(x - 2, y - 1, z - 2, 'electroenergetics:creative_battery')
    return s, (x, y, z - 1), (x, y - 1, zz), (x, y, zz)


TEXT = {
    'title': ('Filter-press electrolysis cells', 'フィルタープレス型電解槽', '压滤式电解槽'),
    'shape': ('Place an electrolysis cell. Cells set in a row lose their inner walls and become one long trough; a single cell works on its own.', '電解セルを置きます。一列に並べたセルは内壁が消えて1本の長い槽になり、1マスから運転できます。', '放置电解单元。排成一列的单元会移除内壁连成一条长槽，一格即可运行。'),
    'extend': ('Extend the row up to eight blocks along X or Z. Another cell beside the row, a gap or a second panel stops formation.', '同じ向きに最大8マスまで一列に延長できます。X・Z両方向に対応し、横に並ぶ別のセル、欠け、二つ目の制御盤があると形成しません。', '沿X或Z方向最多延伸8格。旁边的其他单元、缺块或第二组控制器会阻止成型。'),
    'panel': ('Stack two controllers at one end: the load dial below, the meters and START/STOP above.', '片端にコントローラーを2個縦に積みます。下が稼働率ダイヤル、上が計器とSTART/STOPです。', '在一端竖向叠放两个控制器：下为负载旋钮，上为仪表和START/STOP。'),
    'head': ('Set an electrode head on every cell. Each head takes the direction of the cell below it.', '各セルの上に電極ヘッドを載せます。向きは下のセルに合わせて置かれます。', '在每个电解单元上放一个电极吊架，方向自动对齐下方单元。'),
    'plates': ('While a head is raised, right-click it with electrode plates, up to sixteen per head; funnels on top or at the sides work too. Sneak with an empty hand to take one back.', 'ヘッドが上がっている間に、電極板を持って右クリックで入れます。1個につき16枚まで、上面や側面のファンネルも使えます。しゃがんで素手で右クリックすると1枚戻ります。', '吊架升起时手持电极右键装入，每个最多16片，也可从顶部或侧面用漏斗。潜行空手右键取回一片。'),
    'rotation': ("The heads need no rotation: START lowers every head's plates into the cells by itself, and STOP raises them.", 'ヘッドに回転動力は要りません。STARTで全部のヘッドの電極板がひとりでに槽へ下り、STOPで上がります。', '吊架无需旋转动力：按START各吊架的电极自动降入槽内，按STOP升起。'),
    'feed': ('Feed from below or into the far end. Caustic soda and recovered acid leave the far end.', '原料は底面か奥の端面から入れます。苛性ソーダや再生酸は奥の端面から出ます。', '原料从底部或远端端面送入，苛性钠和再生酸从远端端面排出。'),
    'wire': ('Wire the two large insulated terminals on top of the upper panel unit to CEE. At 400 V each plate needs 1 kW, or 700 W for nickel.', '上段の制御盤の上に立つ2本の大きな碍子の端子をCEE回路へ配線します。400 V時、極板1枚に通常1 kW、ニッケルなら700 Wです。', '将上段控制器顶部两个大型绝缘子端子接入CEE电路。400 V时每块普通极板需1 kW，镍极板需700 W。'),
    'run': ('Press START on the upper unit. Current flows once every plate is down; adjust the load with the dial below.', '上の制御盤のSTARTで起動します。電極板が下りきると電流が流れます。稼働率は下のダイヤルで調整します。', '按上方控制器的START启动，电极全部降下后电流导通，下方旋钮调节负荷。'),
    'gas': ('Cathode and anode plates alternate in one trough. For a Z-axis row the low-X side gives hydrogen-family gas and the high-X side oxygen-family gas; swap X/Z for X-axis rows.', '陰極板と陽極板は1本の槽の中に交互に並びます。Z向きなら低いX側の側面から水素系、高いX側から酸素系が出ます。X向きではX/Zを入れ替えます。', '阴极板与阳极板在同一槽内交替排列。Z轴机组低X侧出氢类气体，高X侧出氧类；X轴机组交换X/Z。'),
    'controls': ("Click the dial's right half for +1％, left for −1％. Sneak for 10％ steps; STOP shuts it down.", 'ダイヤルの右側で1％増、左側で1％減です。スニークなら10％刻み、STOPで停止します。', '点旋钮右半边加1％、左半边减1％，潜行为10％步进，STOP停止。'),
    'blocked': ('A head that is not fully down or a full output stops the stack. Lower it or remove the products to resume.', '下りきっていないヘッドや満杯の出口があると止まります。ヘッドを下ろすか製品を回収すると再開します。', '吊架未完全降下或产物出口已满会停机，降下吊架或取走产物后恢复。'),
}


def main():
    old = p.OUT;p.OUT = A / 'ponder/plant'
    unit(8, 13, (5, 2, 2))[0].save('electrolysis_cell')
    p.OUT = old
    for lang, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / (lang + '.json');data = json.loads(path.read_text())
        data = {k: v for k, v in data.items() if not ('electrolyzer.' in k or 'basin_electrolysis' in k or k == f'block.{NS[:-1]}.electrolyzer_lid'
                                                   or k.startswith(f'{NS[:-1]}.ponder.shared.electrolysis_cell.hatch'))}
        for key, text in TEXT.items(): data[f'{NS[:-1]}.ponder.shared.electrolysis_cell.{key}'] = text[index].replace('%', '％')
        data[f'{NS[:-1]}.ponder.electrolysis_cell.header'] = TEXT['title'][index]
        write(path, data)
    obsolete = A / 'ponder/chemistry/electrolyzer.nbt'
    if obsolete.exists(): obsolete.unlink()


if __name__ == '__main__': main()
