#!/usr/bin/env python3
"""The player's manual and the guide advancements. Run from the repository root.

The manual is a vanilla written book whose pages are translated text, so it reads in the player's language and needs no screen of
its own. The guide's root advancement is granted on joining and hands every player the book once; a book and an iron ingot craft
another. Each guide advancement says what to do next and which Ponder lesson shows it.

Page sizes are checked against the book's 114 x 14-line page with conservative glyph widths, so no page overflows."""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors'
LANGUAGES = ('en_us', 'ja_jp', 'zh_cn')
TITLE = ('Refinery \'n\' Reactors Manual', 'Refinery \'n\' Reactors 説明書', 'Refinery \'n\' Reactors 说明书')

PAGES = [
    ("Refinery 'n' Reactors\nPlant Manual\n\nBuild plants with Create's machines that give you more metal from every ore, far more rotation, faster farms, mining charges and goods to sell.", "Refinery 'n' Reactors\n工場の説明書\n\nCreateの機械で工場を組むと、鉱石から取れる金属が増え、回転力・農業・採掘・出荷が強くなります。", "Refinery 'n' Reactors\n工厂说明书\n\n用机械动力的机器搭建工厂，可从矿石获得更多金属，并强化旋转动力、农业、采矿和出货。"),
    ('Contents\n\n1 Looking things up\n2 First: steel, stainless, vessel\n3 More metal from ores\n4 More rotation\n5 Faster farms\n6 Mining charges\n7 Selling products\n8 Better materials\n9 Grades and pipes\n10 When it stops', '目次\n1 調べ方\n2 最初に：鋼鉄・ステンレス・槽\n3 鉱石から金属を増やす\n4 回転力を増やす\n5 農業を速くする\n6 発破で掘る\n7 製品を出荷する\n8 よい素材を作る\n9 等級と配管\n10 止まったとき', '目录\n1 查阅方法\n2 首先：钢、不锈钢与带盖槽\n3 从矿石增产金属\n4 增加旋转动力\n5 加快农业\n6 爆破采矿\n7 出货产品\n8 制作更好的材料\n9 等级与管道\n10 停机时'),
    ("1 Looking things up\n\nHold a part and press W to open its Ponder, a moving guide.\nJEI shows every recipe and its amounts.\nLook at a running machine with Create's goggles to see its grades and why it stopped.", '1 調べ方\n\n部品を持ってWを長押しすると、動く説明（Ponder）が開きます。\n材料と量はJEIで調べます。\n動いている設備はCreateのゴーグルで見ると、等級と止まった理由がわかります。', '1 查阅方法\n\n手持部件按住W，打开会动的说明（思索）。\n配方和用量在JEI中查看。\n用机械动力的护目镜看运行中的设备，可了解等级和停机原因。'),
    ("Your path\n\nPress L: thick-framed advancements are the main path, from 1. Hover an unearned one to see how to make it.\n\nFirst set up Create's basics: rotation, a blaze burner, a basin and mixer, a press.", '進め方\n\n実績画面（Lキー）の太い枠の実績が本筋で、1から番号順です。まだ取っていない実績にカーソルを合わせると、作り方と開くPonderが出ます。\n\nまずCreateの基本（回転・ブレイズバーナー・鉢とミキサー・プレス）を用意します。', '推进方式\n\n进度界面（L键）中粗框进度是主线，从1开始编号。悬停在未获得的进度上，可看到做法和要打开的思索。\n\n先准备好机械动力的基础：旋转动力、烈焰人燃烧室、工作盆与搅拌器、压机。'),
    ('2 First: steel\n\nCrush coal or charcoal into carbon powder.\nSuperheat 4 iron ingots + 1 carbon powder under a mixer: 4 steel workpieces.\nPress each five times: 4 steel ingots in total.', '2 最初に：鋼鉄\n\n石炭・木炭を粉砕して炭素粉にします。\nバーナーを超加熱し、ミキサーで鉄インゴット4個と炭素粉1個から中間素材4個を作ります。\nそれぞれ5回プレスすると鋼鉄計4個。', '2 首先：钢\n\n粉碎煤或木炭得到碳粉。\n超级加热燃烧室，用搅拌器将4个铁锭与1份碳粉混成4份中间材料。\n每份压制5次，共得4个钢锭。'),
    ('Stainless steel\n\nRoast 2 pentlandite in a heated basin, superheat it with carbon powder: nickel. Superheat 2 chromite and 1 carbon powder: ferrochrome.\nSuperheat 6 iron, 2 ferrochrome, 1 nickel: 600 mB of molten stainless.', 'ステンレス鋼\n\nペントランド鉱2個を加熱の鉢で焙焼し、炭素粉と超加熱してニッケル。クロム鉄鉱2個と炭素粉1個を超加熱してフェロクロム。\n鉄6・フェロクロム2・ニッケル1を超加熱すると溶融ステンレス600 mB。', '不锈钢\n\n在加热的工作盆中焙烧2个镍黄铁矿，与碳粉超级加热得镍。超级加热2个铬铁矿与1份碳粉得铬铁。\n超级加热6铁、2铬铁、1镍得600 mB熔融不锈钢。'),
    ('Casting and rolling\n\nPump 800 mB of molten stainless into a basin under a press: one slab.\nRoll the slab hot on a tandem rolling mill built from steel: 8 ingots. Ingots are made no other way.\nPonder: Tandem Rolling Mill', '鋳造と圧延\n\n溶融ステンレス800 mBをプレスの下の鉢へ送ると、スラブ1個。\n鋼鉄で組んだ圧延ラインでスラブを熱間圧延すると、インゴット8個。インゴットは圧延でしか作れません。\nPonder：多段連続圧延ライン', '铸造与轧制\n\n把800 mB熔融不锈钢泵入压机下的工作盆，得到1块坯。\n在用钢搭建的连续轧机上热轧，得到8个锭。不锈钢锭只能轧制得到。\n思索：多机架连续轧机'),
    ('The stirred tank\n\nMechanical Crafter: 1 Stainless Tank, 8 Steel Ingots and 1 Shaft.\nBuild 3×3, 2–3 layers high. Mount the stirrer on top and drive its cogwheel at 32 RPM or more. Heat from below.', '撹拌槽\n\nメカニカルクラフターでステンレスタンク1個、鋼鉄インゴット8個、シャフト1本から作ります。\n3×3で2～3段に組み、天面中央に撹拌機を載せます。横の歯車を32 RPM以上で回し、下から加熱します。', '搅拌槽\n\n用动力合成器将1个不锈钢罐、8个钢锭和1根传动杆合成为搅拌槽。\n搭成3×3、两到三层，在顶部中央安装搅拌器。侧面齿轮以至少32 RPM旋转，从下面加热。'),
    ('Fluids go in and out by pipe, solids by funnel. Keep the hatch closed.\nLook the vessel up in JEI to see what it makes.\n\nPonder: Building a stirred reactor', '流体はパイプ、固体はファンネルで出し入れします。ハッチは閉じます。\nJEIで槽を調べると作れる物がわかります。\nPonder：撹拌反応槽を組み立てる', '流体用管道、固体用漏斗进出。舱口保持关闭。\n在JEI中查带盖槽可看到全部产物。\n思索：建造搅拌反应槽'),
    ('3 More metal from ores\n\nIron, copper, zinc, tin; no gold.\nIn a heated stirred tank (32 RPM or more), 3 crushed ores + 750 mB dilute acid + 250 mB compressed air or oxygen make 4000 mB slurry.', '3 鉱石から金属を増やす\n\n鉄・銅・亜鉛・錫が対象。金は対象外。\n撹拌槽を下から加熱し、32 RPM以上で回します。砕いた鉱石3個＋希硫酸750 mB＋圧搾空気か酸素250 mBからスラリー4000 mB。', '3 从矿石增产金属\n\n支持铁、铜、锌、锡，不含金。\n在加热的搅拌槽中以32 RPM以上运转，3份粉碎矿石＋750 mB稀酸＋250 mB压缩空气或氧气变为4000 mB矿浆。'),
    ('Clarify and recover\n\n1–4 RPM: 1000 mB slurry gives 750 mB solution + 2 mud.\nElectrolysis: 1000 mB solution gives 3 ingots + 250 mB acid for 20 kJ. Dirt raises this work.\nPonder: Wet processing', '沈降と金属回収\n\n沈殿池を1〜4 RPMで回し、スラリー1000 mBを精製液750 mBと尾鉱泥2個へ。\n電解では精製液1000 mBから金属3個と再生酸250 mBを回収。通常20 kJで、濁りがあると電力が増えます。\nPonder：湿式浸出と低速沈降', '沉降与回收\n\n沉淀池1–4 RPM：1000 mB矿浆分为750 mB清液和2份尾矿泥。\n电解1000 mB清液得3个锭和250 mB回用酸，耗能20 kJ，杂质会增加能耗。\n思索：湿法浸出与低速沉降'),
    ('Hydrogen\n\nPower comes from Create: Electro Energetics generators and wires.\nCells, electrode heads and a controller split water into H2 and O2.\n\nPonder: Making hydrogen by water electrolysis', '水素\n\n電気はCreate: Electro Energeticsの発電機と配線を使います。\n電解セルに電極ヘッドと制御盤を付けて水を入れると、水素と酸素が別々に出ます。\n\nPonder：水の電解で水素を作る', '氢\n\n电力使用Create: Electro Energetics的发电机和布线。\n搭建电解槽、电极吊架和控制器并加水，可分别得到氢和氧。\n\n思索：电解水制取氢气'),
    ('Sulfuric acid\n\nBurn sulfur in a heated vessel with air from an open pipe end. Stir the gas into water with air and an iron nugget: dilute acid, no power.\nUse ceramic pipe.\nPonder: From sulfur and recovered gas to sulfuric acid', '硫酸\n\nネザーラックを粉砕した硫黄粉を、加熱した槽で空気と燃やします。空気は配管の口から吸います。その気体を撹拌槽で水・空気・鉄ナゲットと混ぜると希硫酸。電気は要りません。\n酸は粘土を焼いたセラミック管で送ります。\nPonder：硫黄と回収ガスから硫酸を作る', '硫酸\n\n将粉碎下界岩得到的硫粉在加热的槽中用空气燃烧，空气从管口吸入。再在搅拌槽中与水、空气和铁粒混合即得稀硫酸，无需电力。\n酸用黏土烧制的陶瓷管输送。\n思索：从硫与回收气体制取硫酸'),
    ("4 More rotation\n\nGoal: per blaze burner, turbines give up to twice a steam engine's rotation.\nA horizontal tank over burners makes high-pressure steam when heated, superheated when superheated.\nRefined diesel also runs diesel engines.", '4 回転力を増やす\n\n目標：ブレイズバーナー1台あたり、最大で蒸気エンジンの約2倍の回転力。\nバーナーの上の横型タンクは、加熱で高圧蒸気、超加熱で過熱蒸気を作ります。\n精製した軽油はディーゼルエンジンにも使えます。', '4 增加旋转动力\n\n目标：每个烈焰人燃烧室最多可获得约为蒸汽引擎两倍的旋转动力。\n燃烧室上方的卧式储罐加热产生高压蒸汽，超级加热产生过热蒸汽。\n精制柴油也可用于柴油发动机。'),
    ('Feed the high-pressure turbine, pump its exhaust to the low-pressure one, and cool that with a condenser.\nRBMK gives far more steam: uranium, enrichment, pellets, fuel rods. Fusion irradiates cartridges into netherite scrap.', '高圧タービンに通し、排気を低圧タービンへ送り、復水器で冷やします。\nRBMK炉はけた違いの蒸気を出します：ウラン鉱石→濃縮→ペレット→燃料棒。その先の核融合炉では、照射カートリッジからネザライトの欠片を作れます。', '先送入高压汽轮机，再把排汽泵送至低压汽轮机，并用冷凝器冷却。\nRBMK反应堆可产生多得多的蒸汽：铀矿石→浓缩→芯块→燃料棒。更远处的聚变反应堆可用辐照组件盒制取下界合金碎片。'),
    ('5 Faster farms\n\nGoal: one fertilizer grows the 3 x 3 crops around the one you use it on; deployers spread it too.\nCompress air, separate nitrogen, make ammonia, then nitric acid and ammonium nitrate.', '5 農業を速くする\n\n目標：肥料1個で、使った作物の周囲3×3がまとめて育つ。デプロイヤーでも使えます。\n空気を圧縮して窒素を分け、アンモニア→硝酸→硝酸アンモニウム。', '5 加快农业\n\n目标：1个肥料让所用作物周围3×3一起生长，机械手也能使用。\n压缩空气分离氮，制氨→硝酸→硝酸铵。'),
    ('Ammonium nitrate grows each crop as far as two bone meals; ammonium sulfate as far as one.\n\nPonder: From ammonia to nitric acid and fertilizers', '硝酸アンモニウムは骨粉2個分、硫酸アンモニウムは1個分ずつ育てます。\n\nPonder：アンモニアから硝酸と肥料を作る', '硝酸铵使每株作物生长相当于2个骨粉，硫酸铵相当于1个。\n\n思索：从氨制取硝酸与肥料'),
    ("6 Mining charges\n\nGoal: dig wide areas without a pickaxe.\nMix ammonium nitrate with diesel into ANFO and craft blasting charges. A quarry drive digs a marker's area; its side belt brings the ore back to the drive.", '6 発破で掘る\n\n目標：つるはしを使わず広い範囲を掘る。\n硝酸アンモニウムと軽油でANFOを作り、発破薬にします。クァーリー駆動部はマーカーの範囲を掘り、側面のベルトで鉱石を駆動部の横まで戻します。', '6 爆破采矿\n\n目标：不用镐开采大片区域。\n用硝酸铵与柴油制成ANFO，再做成采矿炸药。采石场驱动器开采标记器划定的区域，用侧面传送带把矿石送回驱动器旁。'),
    ('7 Selling products\n\nGoal: emeralds and experience.\nAttach the dispatch docket to a Create item vault. It orders fuel, alloys, rubber, fertilizer, building materials and plastics; full cargo is paid for.\nPonder: Deliver industrial products', '7 製品を出荷する\n\n目標：エメラルドと経験値。\n出荷票をCreateのアイテム保管庫に付けます。燃料・合金・ゴム・肥料・建材・樹脂の注文が届き、そろえて出荷すると報酬が出ます。\nPonder：工業製品を出荷する', '7 出货产品\n\n目标：绿宝石和经验。\n把出货单装在机械动力的物品保险库上。它会订购燃料、合金、橡胶、肥料、建材和树脂，凑齐出货即可获得报酬。\n思索：交付工业产品'),
    ('8 Better materials\n\nSix new ores give metals for tougher pipes and tanks. Crush them first; each metal has its own Ponder.\nMolybdenum: roast with oxygen, reduce with hydrogen; nickel can be made this way too.', '8 よい素材を作る\n\n6種類の新しい鉱石から、丈夫な配管やタンク用の金属を作ります。まず粉砕します。金属ごとにPonderがあります。\nモリブデン：酸素で焙焼し水素で還元。ニッケルもこの方法で作れます。', '8 制作更好的材料\n\n六种新矿石可制成更坚固的管道和储罐用金属。先粉碎，每种金属都有思索。\n钼：与氧焙烧，再用氢还原。镍也可用此法制取。'),
    ('Aluminium, titanium, zirconium: dissolve in acid to an oxide, then use the molten salt electrolysis furnace. Make zirconia first: the furnace needs it.\nPure chromium: thermite with aluminium.', 'アルミ・チタン・ジルコニウム：酸で溶かして酸化物にし、溶融塩電解炉で金属にします。炉に使うジルコニアを先に作ります。\n純粋なクロム：アルミニウムでテルミット。', '铝、钛、锆：用酸溶成氧化物，再用熔盐电解炉炼成金属。先制取电解炉需要的氧化锆。\n纯铬：用铝进行铝热还原。'),
    ('Oil: preheat crude, split it in a 2 x 2 column, crack naphtha, polymerise ethylene and press plastic sheets for gaskets.', '石油：原油を予熱し、2×2の分留塔で分け、ナフサを分解し、エチレンを重合してプラスチック板（ガスケット用）にします。', '石油：预热原油，在2×2分馏塔中分离，裂解石脑油，聚合乙烯并压成塑料板（用于垫片）。'),
    ('9 Grades and pipes\n\nChoose pipes and tanks by heat grade, pressure grade and corrosion resistance. Incompatible fluids are refused. Pumps also move gases.\nPonder: Pressure, the basics', '9 等級と配管\n\n配管とタンクは温度等級・圧力等級・耐食で選びます。合わない流体は搬入できません。気体もポンプで送ります。\nPonder：圧力の基本', '9 等级与管道\n\n按温度等级、压力等级和耐腐蚀性选择管道与储罐。不合适的流体会被拒收，气体也由泵输送。\n思索：压力基础'),
    ('10 When it stops\n\nLook at it with goggles: they say why. Common causes:\n- an outlet is full\n- not hot enough or not turning fast enough\n- a hatch is open\n- wrong amounts (check JEI)', '10 止まったとき\n\nゴーグルで見ると理由がわかります。よくある原因：\n・出口が満杯\n・温度や回転が足りない\n・ハッチが開いている\n・材料の量が違う（JEIで確認）', '10 停机时\n\n用护目镜查看即可知道原因。常见原因：\n·出口已满\n·温度或转速不足\n·舱口打开\n·材料用量不对（查看JEI）'),
]

# Guide advancements: id, parent, icon, frame, criterion items, and the title and next-step description in each language.
# Goals (more metal, rotation, farms, mining, selling, fusion) are framed; the steps leading to them are tasks.
STEPS = [
    ('root', None, 'create_refinery_n_reactors:lidded_vessel', 'task', None, ("Refinery 'n' Reactors", "Refinery 'n' Reactors", "Refinery 'n' Reactors"), ('Thick-framed advancements are the main path: earn them from 1 in order. Hover one to see how.', '太い枠の実績が本筋。1から順に取ろう。カーソルを合わせると作り方が出る。', '粗框进度是主线：从1开始依次完成。悬停可查看做法。')),
    ('steel', 'root', 'create_refinery_n_reactors:steel_ingot', 'goal', ['create_refinery_n_reactors:steel_ingot'], ('1. Steel', '1 鋼鉄', '1 钢'), ('Crush coal into carbon powder, superheat it with 4 iron in a basin and press each piece five times. Ponder: Steel Ingot', '石炭を砕いた炭素粉と鉄4個を超加熱の鉢で混ぜ、5回プレスする。Ponder：鋼鉄インゴット', '把煤粉碎成碳粉，与4个铁在超级加热的工作盆中混合，再压5次。思索：钢锭')),
    ('nickel', 'steel', 'create_refinery_n_reactors:nickel_ingot', 'goal', ['create_refinery_n_reactors:nickel_ingot'], ('2. Nickel', '2 ニッケル', '2 镍'), ('Roast 2 pentlandite in a heated basin, then superheat the oxide with carbon powder. Ponder: Nickel Ingot', 'ペントランド鉱2個を加熱の鉢で焙焼し、炭素粉と超加熱する。Ponder：ニッケルインゴット', '在加热的工作盆中焙烧2个镍黄铁矿，再与碳粉超级加热。思索：镍锭')),
    ('ferrochrome', 'nickel', 'create_refinery_n_reactors:ferrochrome', 'goal', ['create_refinery_n_reactors:ferrochrome'], ('3. Ferrochrome', '3 フェロクロム', '3 铬铁'), ('Superheat 2 chromite and 1 carbon powder in a basin. Ponder: Ferrochrome', 'クロム鉄鉱2個と炭素粉1個を超加熱の鉢で混ぜる。Ponder：フェロクロム', '在超级加热的工作盆中混合2个铬铁矿和1份碳粉。思索：铬铁')),
    ('rolling_mill', 'ferrochrome', 'create_refinery_n_reactors:rolling_mill', 'goal', ['create_refinery_n_reactors:rolling_mill'], ('4. Rolling Mill', '4 圧延ライン', '4 轧机'), ('Build a tandem rolling mill from steel. Stainless ingots are only made on it. Ponder: Tandem Rolling Mill', '鋼鉄で多段連続圧延ラインを作る。ステンレスのインゴットはこれでしか作れない。Ponder：多段連続圧延ライン', '用钢制作多机架连续轧机。不锈钢锭只能由它轧制。思索：多机架连续轧机')),
    ('stainless_steel', 'rolling_mill', 'create_refinery_n_reactors:stainless_steel_ingot', 'goal', ['create_refinery_n_reactors:stainless_steel_ingot'], ('5. Stainless Steel', '5 ステンレス鋼', '5 不锈钢'), ('Melt 6 iron, 2 ferrochrome and 1 nickel, cast the melt into a slab and roll it. Ponder: Stainless Steel Ingot', '鉄6・フェロクロム2・ニッケル1を溶かし、スラブに鋳造して圧延する。Ponder：ステンレス鋼インゴット', '熔化6铁、2铬铁和1镍，浇铸成坯再轧制。思索：不锈钢锭')),
    ('vessel', 'stainless_steel', 'create_refinery_n_reactors:lidded_vessel', 'goal', ['create_refinery_n_reactors:lidded_vessel'], ("6. The Plant's Heart", '6 工場の心臓', '6 工厂之心'), ('Craft a stainless tank from stainless sheet, an iron trapdoor and a basin. Ponder: Stainless Tank', 'ステンレス板・鉄のトラップドア・鉢でステンレスタンクを作る。Ponder：ステンレスタンク', '用不锈钢板、铁活板门和工作盆制作不锈钢罐。思索：不锈钢罐')),
    ('electrolysis', 'vessel', 'create_refinery_n_reactors:electrolysis_cell', 'goal', ['create_refinery_n_reactors:electrolysis_cell'], ('7. Splitting Water', '7 水を分ける', '7 分解水'), ('Build an electrolysis cell with an electrode head and controller, and power it. Ponder: Electrolysis Cell', '電解セルに電極ヘッドと制御盤を付けて電気を通す。Ponder：電解セル', '给电解单元装上电极头和控制器并通电。思索：电解单元')),
    ('sulfuric_acid', 'electrolysis', 'create_refinery_n_reactors:sulfuric_acid_bucket', 'goal', ['create_refinery_n_reactors:dilute_sulfuric_acid_bucket', 'create_refinery_n_reactors:sulfuric_acid_bucket'], ('8. Sulfuric Acid', '8 硫酸', '8 硫酸'), ('Burn sulfur powder with air in a heated tank, then absorb the gas in water: dilute acid. Ponder: Sulfuric Acid Corrosion-Resistant Bucket', '硫黄粉を加熱した槽で空気と燃やし、撹拌槽で水に吸わせて希硫酸に。Ponder：硫酸入り耐食合金バケツ', '在加热的罐中用空气燃烧硫粉，再在搅拌罐中用水吸收。思索：硫酸耐蚀合金桶')),
    ('leaching', 'sulfuric_acid', 'create_refinery_n_reactors:copper_sulfate_solution_bucket', 'goal', ['create_refinery_n_reactors:iron_sulfate_solution_bucket', 'create_refinery_n_reactors:copper_sulfate_solution_bucket', 'create_refinery_n_reactors:zinc_sulfate_solution_bucket', 'create_refinery_n_reactors:tin_sulfate_solution_bucket'], ('9. More Metal', '9 金属が増える', '9 金属增产'), ('Leach crushed ore in dilute acid in a heated stirred tank, then settle it in a clarifier. Ponder: Clarifier Center Drive', '加熱した撹拌タンクで砕いた鉱石を希硫酸に溶かし、沈殿池で精製液に。Ponder：円形沈殿池のセンタードライブ', '用稀硫酸浸出粉碎的矿石，在沉淀池中得到精制液。思索：圆形沉淀池中心驱动器')),
    ('power', 'leaching', 'create_refinery_n_reactors:high_pressure_turbine_unit', 'goal', ['create_refinery_n_reactors:high_pressure_turbine_unit', 'create_refinery_n_reactors:fuel_cell'], ('10. More Rotation', '10 回転力が増える', '10 更多旋转动力'), ('Assemble a high-pressure turbine section and drive it with boiler steam. Ponder: High-pressure Turbine Section', '高圧タービン断面を組み立て、ボイラーの蒸気で回す。Ponder：高圧タービン断面', '组装高压汽轮机截面，用锅炉蒸汽驱动。思索：高压汽轮机截面')),
    ('nuclear', 'power', 'create_refinery_n_reactors:fuel_rod', 'goal', ['create_refinery_n_reactors:fuel_rod'], ('11. Fuel Rod', '11 燃料棒', '11 燃料棒'), ('Enrich uranium, press it into pellets and seal them in zircaloy tubes. Ponder: Fuel Rod', 'ウランを濃縮してペレットにし、ジルカロイ被覆管に詰める。Ponder：燃料棒', '浓缩铀并压成芯块，封入锆合金包壳管。思索：燃料棒')),
    ('fusion', 'nuclear', 'create_refinery_n_reactors:fusion_reactor_controller', 'challenge', ['create_refinery_n_reactors:fusion_reactor_controller'], ('12. The Last Plant', '12 最後の工場', '12 最后的工厂'), ('Build a tokamak around its magnet feeder. Ponder: Tokamak Magnet Feeder', 'トカマク磁石給電盤を中心に核融合炉を組む。Ponder：トカマク磁石給電盤', '以托卡马克磁体馈电器为中心建造聚变堆。思索：托卡马克磁体馈电器')),
    ('dispatch', 'root', 'create_refinery_n_reactors:industrial_dispatch_board', 'task', ['create_refinery_n_reactors:industrial_dispatch_board'], ('Open for business', '出荷開始', '开张营业'), ('Craft the Industrial Dispatch Board and place it on an item vault.', '工業製品の出荷票をクラフトし、アイテム保管庫に付けよう。', '合成工业调度板，装在物品保险库上。')),
    ('zirconia', 'sulfuric_acid', 'create_refinery_n_reactors:zirconia', 'task', ['create_refinery_n_reactors:zirconia'], ('Zirconia', 'ジルコニア', '氧化锆'), ('Heat zirconyl sulfate to superheated in a pressure reactor to get zirconia. Ponder: Zirconia', '硫酸ジルコニルを加圧反応器で過熱して作る。Ponder：ジルコニア', '在压力反应器中把硫酸氧锆过热加热。思索：氧化锆')),
    ('aluminum', 'zirconia', 'create_refinery_n_reactors:aluminum_ingot', 'task', ['create_refinery_n_reactors:aluminum_ingot', 'create_refinery_n_reactors:titanium_ingot'], ('Furnace metals', '電解炉の金属', '电解炉金属'), ('Smelt aluminium or titanium in the molten salt electrolysis furnace. Ponder: Aluminum Ingot', '溶融塩電解炉でアルミかチタンを作る。Ponder：アルミニウムインゴット', '用熔盐电解炉制取铝或钛。思索：铝锭')),
    ('plastic', 'stainless_steel', 'create_refinery_n_reactors:plastic_sheet', 'task', ['create_refinery_n_reactors:plastic_sheet'], ('Plastics', 'プラスチック', '塑料'), ('Press polyethylene or polypropylene into a plastic sheet. Ponder: Plastic Sheet', 'ポリエチレンかポリプロピレンをプレスして板にする。Ponder：プラスチック板', '把聚乙烯或聚丙烯压成塑料板。思索：塑料板')),
    ('compressor', 'plastic', 'create_refinery_n_reactors:reciprocating_compressor', 'task', ['create_refinery_n_reactors:reciprocating_compressor'], ('Under pressure', '圧縮', '加压'), ('Craft a reciprocating compressor. Ponder: Reciprocating Compressor', '往復式コンプレッサーをクラフトしよう。Ponder：往復式コンプレッサー', '合成往复式压缩机。思索：往复式压缩机')),
    ('fertilizer', 'compressor', 'create_refinery_n_reactors:ammonium_nitrate', 'task', ['create_refinery_n_reactors:ammonium_nitrate', 'create_refinery_n_reactors:ammonium_sulfate'], ('Faster farms', '農業が速くなる', '农业加速'), ('React ammonia with nitric acid (ammonium nitrate) or sulfuric acid (ammonium sulfate). Ponder: Ammonium Nitrate', 'アンモニアを硝酸か硫酸と反応させる。Ponder：硝酸アンモニウム', '让氨与硝酸或硫酸反应。思索：硝酸铵')),
    ('mining', 'fertilizer', 'create_refinery_n_reactors:blasting_charge', 'task', ['create_refinery_n_reactors:blasting_charge'], ('Mining charges', '発破', '爆破采矿'), ('Craft a blasting charge from ANFO, paper and redstone. Ponder: Blasting Charge', 'ANFO・紙・レッドストーンで発破薬をクラフトする。Ponder：発破薬', '用ANFO、纸和红石合成爆破药包。思索：爆破药包')),
    ('air_separation', 'compressor', 'create_refinery_n_reactors:fractionating_column', 'task', ['create_refinery_n_reactors:fractionating_column'], ('Air, split', '空気を分ける', '分离空气'), ('Craft a fractionation tower and build it to split cooled air. Ponder: Fractionation Tower', '分留塔をクラフトして組み、冷やした空気を分ける。Ponder：分留塔', '合成并搭建分馏塔，分离冷却后的空气。思索：分馏塔')),
    ('separator', 'compressor', 'create_refinery_n_reactors:ammonia_separator', 'task', ['create_refinery_n_reactors:ammonia_separator'], ('Gas from liquid', '気液を分ける', '气液分离'), ('Craft a gas-liquid separator. Ponder: Gas-Liquid Separator', '気液分離器をクラフトしよう。Ponder：気液分離器', '合成气液分离器。思索：气液分离器')),
    ('ammonia', 'separator', 'create_refinery_n_reactors:ammonia_gas_bucket', 'task', ['create_refinery_n_reactors:ammonia_gas_bucket'], ('Ammonia', 'アンモニア', '氨'), ('Compress hydrogen and nitrogen over iron catalyst in a reaction tower, then separate. Ponder: Ammonia Bucket', '水素と窒素を反応塔の鉄触媒で反応させ、分離する。Ponder：アンモニア入りバケツ', '在反应塔用铁催化剂反应氢和氮，再分离。思索：氨桶')),
    ('nitric_acid', 'ammonia', 'create_refinery_n_reactors:nitric_acid_bucket', 'task', ['create_refinery_n_reactors:nitric_acid_bucket'], ('Nitric acid', '硝酸', '硝酸'), ('Oxidise ammonia, then absorb the NO2 in water in an absorption tower. Ponder: Nitric Acid Corrosion-Resistant Bucket', 'アンモニアを酸化し、NO2を吸収塔で水に吸収させる。Ponder：硝酸入り耐食合金バケツ', '氧化氨，再用吸收塔以水吸收NO2。思索：硝酸耐蚀合金桶')),
    ('gas_storage', 'compressor', 'create_refinery_n_reactors:gas_holder', 'task', ['create_refinery_n_reactors:gas_holder', 'create_refinery_n_reactors:gas_receiver', 'create_refinery_n_reactors:gas_tank_container'], ('Storing gas', '気体の貯蔵', '储存气体'), ('Craft a gas holder, receiver or gas tank container. Ponder: Telescopic Gas Holder', 'ガスホルダー・レシーバー・タンクコンテナのどれかをクラフト。Ponder：伸縮式ガスホルダー', '合成储气柜、受槽或储气罐容器之一。思索：伸缩式储气柜')),
    ('reaction_tower', 'vessel', 'create_refinery_n_reactors:reaction_tower', 'task', ['create_refinery_n_reactors:reaction_tower'], ('Reaction tower', '反応塔', '反应塔'), ('Craft a reaction tower (stainless plates, furnace, molybdenum). Ponder: Reaction Tower', 'ステンレス板・かまど・モリブデンで反応塔をクラフト。Ponder：反応塔', '用不锈钢板、熔炉和钼合成反应塔。思索：反应塔')),
    ('stirred', 'vessel', 'create_refinery_n_reactors:vessel_stirrer', 'task', ['create_refinery_n_reactors:vessel_stirrer'], ('Stirred tank', '撹拌槽', '搅拌槽'), ('Craft a tank stirrer: cogwheel, impeller, stainless plates, andesite casing. Ponder: Tank Stirrer', '歯車・撹拌翼・ステンレス板・安山岩ケーシングで撹拌機を作る。Ponder：撹拌機', '用齿轮、叶轮、不锈钢板和安山岩机壳合成搅拌器。思索：搅拌器')),
    ('absorption', 'vessel', 'create_refinery_n_reactors:large_absorption_tower', 'task', ['create_refinery_n_reactors:large_absorption_tower'], ('Absorption tower', '吸収塔', '吸收塔'), ('Craft an absorption tower from stainless plates and glass. Ponder: Absorption Tower Storey', 'ステンレス板とガラスで吸収塔を作る。Ponder：吸収塔の段', '用不锈钢板和玻璃合成吸收塔。思索：吸收塔塔节')),
    ('gasifier', 'vessel', 'create_refinery_n_reactors:steam_gasifier', 'task', ['create_refinery_n_reactors:steam_gasifier'], ('Steam gasifier', '水蒸気ガス化炉', '水蒸气气化炉'), ('Craft a steam gasifier from iron sheets and bricks. Ponder: Steam Gasifier', '鉄板とレンガで水蒸気ガス化炉をクラフトする。Ponder：水蒸気ガス化炉', '用铁板和砖块合成蒸汽气化炉。思索：蒸汽气化炉')),
    ('oil_products', 'vessel', 'create_refinery_n_reactors:naphtha_bucket', 'task', ['create_refinery_n_reactors:naphtha_bucket', 'create_refinery_n_reactors:low_sulfur_diesel_bucket'], ('Oil products', '石油製品', '石油产品'), ('Fractionate crude oil into naphtha, or hydrotreat diesel in a reaction tower. Ponder: Naphtha Bucket', '原油を分留してナフサを、反応塔で脱硫して軽油を得る。Ponder：ナフサ入りバケツ', '分馏原油得石脑油，或在反应塔加氢脱硫柴油。思索：石脑油桶')),
    ('methanol', 'gasifier', 'create_refinery_n_reactors:methanol_bucket', 'task', ['create_refinery_n_reactors:methanol_bucket'], ('Methanol', 'メタノール', '甲醇'), ('Pass synthesis gas over a catalyst to make methanol. Ponder: Methanol Bucket', '合成ガスを触媒に通してメタノールを作る。Ponder：メタノール入りバケツ', '让合成气通过催化剂制得甲醇。思索：甲醇桶')),
    ('hydrogen', 'electrolysis', 'create_refinery_n_reactors:hydrogen_gas_bucket', 'task', ['create_refinery_n_reactors:hydrogen_gas_bucket'], ('Hydrogen', '水素', '氢气'), ('Electrolyse water in an electrolysis cell row; hydrogen leaves a long side. Ponder: Hydrogen Bucket', '電解セルで水を電気分解し、長辺から水素を取り出す。Ponder：水素入りバケツ', '用电解槽电解水，从长边取出氢气。思索：氢桶')),
    ('chlorine', 'electrolysis', 'create_refinery_n_reactors:chlorine_bucket', 'task', ['create_refinery_n_reactors:chlorine_bucket', 'create_refinery_n_reactors:caustic_soda_solution_bucket'], ('Chlor-alkali', 'クロルアルカリ', '氯碱'), ('Electrolyse brine in the electrolysis cell: chlorine and caustic soda. Ponder: Bucket of Chlorine', '電解セルで食塩水を電気分解し、塩素と苛性ソーダを得る。Ponder：塩素入りバケツ', '在电解槽电解盐水，得到氯和烧碱。思索：氯气桶')),
    ('pvc', 'plastic', 'create_refinery_n_reactors:pvc_resin', 'task', ['create_refinery_n_reactors:pvc_resin'], ('PVC', '塩化ビニル樹脂', '聚氯乙烯'), ('Heat vinyl chloride in a pressure reactor to polymerise it. Ponder: PVC Resin', '塩化ビニルを加圧反応器で加熱して重合させる。Ponder：塩化ビニル樹脂', '在压力反应器中加热氯乙烯使其聚合。思索：聚氯乙烯树脂')),
    ('pipes', 'stainless_steel', 'create_refinery_n_reactors:stainless_steel_pipe', 'task', ['create_refinery_n_reactors:stainless_steel_pipe', 'create_refinery_n_reactors:nickel_alloy_pipe', 'create_refinery_n_reactors:zirconium_pipe', 'create_refinery_n_reactors:ceramic_pipe'], ('Process piping', '工程配管', '工艺管道'), ('Craft a stainless steel, nickel alloy, zirconium or ceramic pipe. Ponder: Stainless Steel Pipe', 'ステンレス・ニッケル合金・ジルコニウム・セラミックの管を作る。Ponder：ステンレス鋼管', '合成不锈钢、镍合金、锆或陶瓷管道。思索：不锈钢流体管道')),
    ('pumps', 'pipes', 'create_refinery_n_reactors:electric_pump', 'task', ['create_refinery_n_reactors:electric_pump', 'create_refinery_n_reactors:high_pressure_pump'], ('Pumps', 'ポンプ', '泵'), ('Craft an electric pump or a high-pressure pump. Ponder: Electric Pump', '電動ポンプか高圧ポンプをクラフトしよう。Ponder：電動ポンプ', '合成电动泵或高压泵。思索：电动泵')),
    ('tanks', 'stainless_steel', 'create_refinery_n_reactors:stainless_steel_tank', 'task', ['create_refinery_n_reactors:stainless_steel_tank', 'create_refinery_n_reactors:horizontal_fluid_tank', 'create_refinery_n_reactors:cryogenic_tank', 'create_refinery_n_reactors:pressure_tank'], ('Storage tanks', '貯蔵タンク', '储罐'), ('Craft a stainless steel, horizontal, cryogenic or pressure tank. Ponder: Stainless Steel Tank', 'ステンレス・横型・保冷・耐圧のいずれかのタンクを作る。Ponder：ステンレス鋼タンク', '合成不锈钢、卧式、保冷或耐压储罐。思索：不锈钢储罐')),
    ('boiler', 'tanks', 'create_refinery_n_reactors:stainless_boiler', 'task', ['create_refinery_n_reactors:stainless_boiler'], ('Boiler', 'ボイラー', '锅炉'), ('Craft a stainless boiler. Ponder: Stainless Boiler', 'ステンレスボイラーをクラフトしよう。Ponder：ステンレスボイラー', '合成不锈钢锅炉。思索：不锈钢锅炉')),
    ('instruments', 'pipes', 'create_refinery_n_reactors:pipe_thermometer', 'task', ['create_refinery_n_reactors:pipe_thermometer', 'create_refinery_n_reactors:pipe_pressure_gauge', 'create_refinery_n_reactors:pipe_flow_meter', 'create_refinery_n_reactors:flow_regulator', 'create_refinery_n_reactors:pid_controller'], ('Instruments', '計器', '仪表'), ('Craft a pipe thermometer, pressure gauge, flow meter, flow regulator or PID controller. Ponder: Pipe Thermometer', '配管の温度計・圧力計・流量計、流量調節器、PID調節器を作る。Ponder：配管温度計', '合成管道温度计、压力表、流量计、调节器或PID控制器。思索：管道温度计')),
    ('gas_safety', 'stainless_steel', 'create_refinery_n_reactors:gas_mask', 'task', ['create_refinery_n_reactors:gas_mask', 'create_refinery_n_reactors:gas_detector', 'create_refinery_n_reactors:relief_scrubber'], ('Gas safety', 'ガスの安全', '气体安全'), ('Craft a gas mask, gas detector or relief scrubber. Ponder: Gas Mask', 'ガスマスク・ガス検知器・排ガススクラバーのどれかを作る。Ponder：ガスマスク', '合成防毒面具、气体探测器或排气洗涤器。思索：防毒面具')),
    ('converter', 'steel', 'create_refinery_n_reactors:converter_shell', 'task', ['create_refinery_n_reactors:converter_shell'], ('Converter', '転炉', '转炉'), ('Craft a converter shell and build the converter. Ponder: Converter Shell', '転炉の炉殻をクラフトして転炉を組む。Ponder：転炉の炉殻', '合成转炉外壳并搭建转炉。思索：转炉外壳')),
    ('cooling', 'power', 'create_refinery_n_reactors:natural_draft_cooling_tower', 'task', ['create_refinery_n_reactors:natural_draft_cooling_tower', 'create_refinery_n_reactors:cooler_radiator', 'create_refinery_n_reactors:condenser_shell'], ('Cooling', '冷却', '冷却'), ('Craft a cooling tower, cooler radiator or condenser shell. Ponder: Natural Draft Cooling Tower', '冷却塔・冷却器ラジエーター・復水器外殻のどれかを作る。Ponder：自然通風冷却塔制御部', '合成冷却塔、冷却器散热器或冷凝器外壳。思索：自然通风冷却塔')),
    ('batteries', 'power', 'create_refinery_n_reactors:lead_acid_battery_casing', 'task', ['create_refinery_n_reactors:lead_acid_battery_casing', 'create_refinery_n_reactors:lithium_ion_battery_casing'], ('Batteries', '蓄電池', '蓄电池'), ('Craft battery casings from stainless sheet, redstone and lead (or lithium carbonate). Ponder: Lead-Acid Battery Casing', '鉛かリチウムの電池ケーシングをクラフトする。Ponder：鉛蓄電池ケーシング', '用不锈钢板、红石和铅（或碳酸锂）合成电池外壳。思索：铅酸蓄电池外壳')),
    ('flywheel', 'power', 'create_refinery_n_reactors:large_flywheel', 'task', ['create_refinery_n_reactors:large_flywheel'], ('Flywheel', '大型フライホイール', '大型飞轮'), ('Mechanical-craft a Large Flywheel from iron sheets, steel blocks and a Create flywheel. Ponder: Large Flywheel', '鉄板・鋼鉄ブロック・フライホイールで大型フライホイールを機械クラフト。Ponder：大型フライホイール', '用铁板、钢块和飞轮机械合成大型飞轮。思索：大型飞轮')),
    ('transmission', 'power', 'create_refinery_n_reactors:transmission_tower', 'task', ['create_refinery_n_reactors:transmission_tower'], ('Transmission', '送電', '输电'), ('Craft a Transmission Tower with a glass insulator and a precision mechanism. Ponder: Transmission Tower', '碍子と精密機構で送電鉄塔をクラフトする。Ponder：送電鉄塔', '用绝缘子和精密构件合成输电铁塔。思索：输电铁塔')),
    ('steam_tools', 'stainless_steel', 'create_refinery_n_reactors:steam_gun', 'task', ['create_refinery_n_reactors:steam_gun', 'create_refinery_n_reactors:steam_hammer'], ('Steam tools', '蒸気の道具', '蒸汽工具'), ('Mechanical-craft a Steam Gun or Steam Hammer from a gas receiver and precision mechanism. Ponder: Steam Gun', 'ガスレシーバーと精密機構で蒸気銃か蒸気ハンマーを機械クラフト。Ponder：蒸気銃', '用气体接收器和精密构件机械合成蒸汽枪或蒸汽锤。思索：蒸汽枪')),
    ('centrifuge', 'power', 'create_refinery_n_reactors:gas_centrifuge', 'task', ['create_refinery_n_reactors:gas_centrifuge'], ('Enrichment', '濃縮', '浓缩'), ('Mechanical-craft a Gas Centrifuge from stainless plates, titanium and a precision mechanism. Ponder: Gas Centrifuge', 'ステンレス板・チタン・精密機構でガス遠心分離機を機械クラフト。Ponder：ガス遠心分離機', '用不锈钢板、钛和精密构件机械合成气体离心机。思索：气体离心机')),
    ('radiation', 'nuclear', 'create_refinery_n_reactors:survey_meter', 'task', ['create_refinery_n_reactors:survey_meter', 'create_refinery_n_reactors:personal_dosimeter'], ('Radiation', '放射線', '辐射'), ('Craft a survey meter or personal dosimeter from brass, glass and an electron tube. Ponder: Radiation survey meter', '真鍮・ガラス・電子管で線量計をクラフトする。Ponder：放射線サーベイメーター', '用黄铜、玻璃和电子管合成测量仪。思索：辐射巡测仪')),
    ('reactor_console', 'nuclear', 'create_refinery_n_reactors:reactor_control_console', 'task', ['create_refinery_n_reactors:reactor_control_console'], ('Control room', '制御室', '控制室'), ('Craft a Reactor Control Console from redstone, a precision mechanism, brass sheets and a brass casing.', 'レッドストーン・精密機構・真鍮板・真鍮ケーシングで制御盤をクラフトする。', '用红石、精密构件、黄铜板和黄铜外壳合成反应堆控制台。')),
    ('fuel_pool', 'nuclear', 'create_refinery_n_reactors:fuel_pool', 'task', ['create_refinery_n_reactors:fuel_pool', 'create_refinery_n_reactors:spent_fuel_rod'], ('Spent fuel', '使用済み燃料', '乏燃料'), ('Craft a Fuel Pool Rack from iron sheets and steel ingots. Ponder: Fuel Pool Rack', '鉄板と鋼鉄インゴットで燃料プールラックをクラフトする。Ponder：燃料プールラック', '用铁板和钢锭合成燃料池格架。思索：燃料池格架')),
    ('mox', 'fuel_pool', 'create_refinery_n_reactors:mox_fuel_rod', 'task', ['create_refinery_n_reactors:mox_fuel_rod'], ('MOX fuel', 'MOX燃料', 'MOX燃料'), ('Craft a MOX fuel rod from MOX pellets and zircaloy cladding tubes.', 'MOXペレットとジルカロイ被覆管でMOX燃料集合体をクラフトする。', '用MOX芯块和锆合金包壳管合成MOX燃料棒。')),
    ('vitrification', 'fuel_pool', 'create_refinery_n_reactors:vitrified_waste_canister', 'task', ['create_refinery_n_reactors:vitrified_waste_canister'], ('Waste, sealed', '廃棄物の固化', '废物固化'), ('Melt high-level raffinate with borosilicate frit and an empty waste canister in the electric vitrification melter.', '高レベル廃液・ガラスフリット・空の缶をガラス固化炉で溶かして固める。', '在电熔玻璃固化炉中用高放废液、硼硅玻璃料和空罐制成固化体。')),
    ('fusion_fuel', 'nuclear', 'create_refinery_n_reactors:deuterium_gas_bucket', 'task', ['create_refinery_n_reactors:deuterium_gas_bucket', 'create_refinery_n_reactors:tritium_gas_bucket'], ('Fusion fuel', '核融合燃料', '聚变燃料'), ('Make a Bucket of Deuterium (see its recipe) or tritium from an irradiated lithium target. Ponder: Bucket of Deuterium', '重水素バケツを作るか、照射済みリチウムから三重水素を取る。Ponder：重水素入りバケツ', '制取氘桶，或从辐照锂靶中提取氚。思索：氘桶')),
    ('quarry', 'mining', 'create_refinery_n_reactors:quarry_drive', 'task', ['create_refinery_n_reactors:quarry_drive'], ('Quarry', '採掘場', '采石场'), ('Craft a Quarry Drive from a gantry carriage, drill, precision mechanism and andesite casing. Ponder: Quarry Drive', 'ガントリー台車・ドリル・精密機構・安山岩ケーシングで駆動部をクラフト。Ponder：クァーリー駆動部', '用龙门滑块、动力钻头、精密构件和安山岩外壳合成。思索：采石场驱动器')),
    ('crane', 'mining', 'create_refinery_n_reactors:crane_drive', 'task', ['create_refinery_n_reactors:crane_drive'], ('Gantry crane', 'ガントリークレーン', '龙门吊'), ('Craft a Crane Drive from a gantry carriage, rope pulley, precision mechanism and brass casing. Ponder: RBMK Fuel Crane Drive', 'ガントリー台車・ロープ滑車・精密機構・真鍮ケーシングで駆動部をクラフト。Ponder：RBMK燃料交換クレーン駆動部', '用龙门滑块、绳索滑轮、精密构件和黄铜外壳合成。思索：RBMK燃料换料起重机驱动器')),
    ('constructor', 'root', 'create_refinery_n_reactors:facility_constructor', 'task', ['create_refinery_n_reactors:facility_constructor'], ('Facility constructor', '施設建設機', '设施建造机'), ('Craft a Facility Constructor from a schematicannon, steel ingots and a precision mechanism. Ponder: Facility Constructor', '概略砲・鋼鉄インゴット・精密機構で施設建設機をクラフトする。Ponder：施設建設機', '用蓝图加农炮、钢锭和精密构件合成。思索：设施建造机')),
    ('vault_hatch', 'dispatch', 'create_refinery_n_reactors:vault_hatch', 'task', ['create_refinery_n_reactors:vault_hatch'], ('Vault hatch', '保管庫ハッチ', '保险库舱口'), ('Craft an Item Vault Hatch from steel ingots, an iron trapdoor and andesite alloy. Ponder: Item Vault Hatch', '鋼鉄・鉄のトラップドア・安山岩合金で保管庫ハッチをクラフト。Ponder：アイテム保管庫ハッチ', '用钢锭、铁活板门和安山合金合成。思索：物品保险库舱口')),
    ('sprayer', 'fertilizer', 'create_refinery_n_reactors:agricultural_sprayer', 'task', ['create_refinery_n_reactors:agricultural_sprayer'], ('Sprayer', '散布機', '喷洒机'), ('Craft an Agricultural Sprayer from brass, fluid pipes and an andesite casing. Ponder: Agricultural Sprayer', '真鍮板・流体パイプ・安山岩ケーシングで散布機をクラフトする。Ponder：農薬散布機', '用黄铜板、流体管道和安山外壳合成。思索：农用喷雾机')),
    ('industrial_revolution', 'boiler', 'create_refinery_n_reactors:low_pressure_turbine_unit', 'task', ['create_refinery_n_reactors:low_pressure_turbine_unit'], ('Industrial Revolution', '産業革命', '工业革命'), ('Mechanical-craft a Low-pressure Turbine Section from casings, blades and ends. Ponder: Low-pressure Turbine Section', 'タービンのケーシング・ブレード・エンドを並べて低圧タービン断面を機械クラフト。Ponder：低圧タービン断面', '用汽轮机外壳、叶片和端件机械合成低压汽轮机截面。思索：低压汽轮机截面')),
    ('bread_from_air', 'nitric_acid', 'create_refinery_n_reactors:ammonium_nitrate', 'task', ['create_refinery_n_reactors:ammonium_nitrate'], ('Bread from Air', '空気からパンを', '空气变面包'), ('React ammonia gas with nitric acid in a pressure reactor to get ammonium nitrate. Ponder: Ammonium Nitrate', '圧力反応でアンモニアと硝酸を反応させ、硝酸アンモニウムを得る。Ponder：硝酸アンモニウム', '在压力反应中让氨气与硝酸反应得到硝酸铵。思索：硝酸铵')),
    ('black_gold', 'vessel', 'createdieselgenerators:crude_oil_bucket', 'task', ['createdieselgenerators:crude_oil_bucket'], ('Black Gold', '黒い黄金', '黑色黄金'), ('Get a bucket of crude oil (Create Diesel Generators).', '原油のバケツを手に入れる（Create Diesel Generators）。', '获取一桶原油（Create Diesel Generators）。')),
    ('transmutation', 'fusion', 'create_refinery_n_reactors:irradiated_cartridge', 'challenge', ['create_refinery_n_reactors:irradiated_cartridge'], ("The Philosopher's Neutron", '賢者の中性子', '贤者之中子'), ('Irradiate a cartridge in the fusion reactor to get an Irradiated Cartridge.', '核融合炉で照射用カートリッジを照射し、照射済みカートリッジを得る。', '在聚变堆中辐照照射卡带，得到辐照后的卡带。')),
    ('cold_as_ice', 'air_separation', 'create_refinery_n_reactors:cryogenic_cold_box_drive', 'task', ['create_refinery_n_reactors:cryogenic_cold_box_drive'], ('Cold as Ice', '氷点下の世界', '冰点之下'), ('Craft a Cold Box Drive from stainless and copper sheets, precision mechanism, jacketed pipe. Ponder: Cryogenic Cold Box Drive', 'ステンレス・銅板・精密機構・ジャケット配管で駆動部をクラフト。Ponder：極低温コールドボックス駆動部', '用不锈钢板、铜板、精密构件和夹套管合成。思索：低温冷箱驱动器')),
    ('chain_reaction', 'nuclear', 'create_refinery_n_reactors:reactor_fuel_channel', 'task', ['create_refinery_n_reactors:reactor_fuel_channel'], ('Chain Reaction', '連鎖反応', '链式反应'), ('Craft a Fuel Channel Block from two fluid pipes and reactor graphite. Ponder: Fuel Channel Block', '流体パイプ2本と原子炉用黒鉛で燃料チャネルをクラフト。Ponder：燃料チャネルブロック', '用两根流体管道和反应堆石墨合成燃料通道。思索：燃料通道方块')),
    ('uranium_fever', 'steel', 'create_refinery_n_reactors:uranium_ore', 'task', ['create_refinery_n_reactors:uranium_ore'], ('Uranium Fever', 'ウラン・フィーバー', '铀矿热'), ('Find and mine Uranium Ore underground. Ponder: Uranium Ore', '地下でウラン鉱石を探して掘る。Ponder：ウラン鉱石', '在地下寻找并开采铀矿石。思索：铀矿石')),
    ('eternal_flame', 'gas_safety', 'create_refinery_n_reactors:flare_tip', 'task', ['create_refinery_n_reactors:flare_tip'], ('The Eternal Flame', '不夜城', '不夜城'), ('Craft a Flare Tip from iron bars, brass, a stainless pipe and flint and steel. Ponder: Flare Tip', '鉄格子・真鍮・ステンレス管・火打ち石でフレア先端部をクラフト。Ponder：フレアスタック先端部', '用铁栏杆、黄铜、不锈钢管和打火石合成。思索：火炬头')),
    ('suit_up', 'gas_safety', 'create_refinery_n_reactors:protective_coat', 'task', ['create_refinery_n_reactors:protective_coat', 'create_refinery_n_reactors:protective_cap'], ('Suit Up', '完全装備', '全副武装'), ('Craft the protective coat (wool, blue dye, plastic sheet) and the protective cap.', '羊毛・青色染料・プラスチック板で防護服を、さらに帽子もクラフトする。', '用羊毛、蓝色染料和塑料板合成防护服，再做防护帽。')),
    ('pressure_cooker', 'pumps', 'create_refinery_n_reactors:pressure_tank', 'task', ['create_refinery_n_reactors:pressure_tank'], ('Pressure Cooker', '圧力鍋', '高压锅'), ('Craft a Pressure Tank from stainless plates, brass plates and stainless pipes. Ponder: Pressure Tank', 'ステンレス板・真鍮板・ステンレス管で耐圧タンクをクラフトする。Ponder：耐圧タンク', '用不锈钢板、黄铜板和不锈钢管合成。思索：耐压储罐')),
    ('first_breath', 'root', 'minecraft:potion', 'task', ['effect:create_refinery_n_reactors:toxic_gas'], ('Not That Breath', 'その息、ちょっと待って', '这口气先别吸'), ('Breathe toxic gas leaking from pipes or tanks, without a gas mask on.', 'ガスマスクなしで、配管やタンクから漏れた毒ガスを吸う。', '不戴防毒面具吸入管道或储罐泄漏的毒气。')),
    ('sour_experience', 'sulfuric_acid', 'minecraft:potion', 'task', ['effect:create_refinery_n_reactors:acid_burn'], ('A Sour Experience', '酸っぱい経験', '酸涩的经历'), ('Fall into an acid or alkali vessel, or get sprayed with acid.', '酸やアルカリの槽に落ちるか、酸を浴びる。', '掉进酸碱容器，或被酸液喷到。')),
    ('seeing_stars', 'steam_tools', 'minecraft:potion', 'task', ['effect:create_refinery_n_reactors:dazed'], ('Seeing Stars', '目から火花', '眼冒金星'), ('Get hit by a Steam Hammer, and see stars.', '蒸気ハンマーで殴られると、目から火花が出る。', '被蒸汽锤击中，就会眼冒金星。')),
]

# Glyph widths in book pixels, with the gap after each: Minecraft's font draws most letters 6 wide, narrow ones less, and CJK 9.
NARROW = {'i': 2, 'l': 3, 'I': 4, 't': 4, 'f': 5, 'k': 5, ' ': 4, '.': 2, ',': 2, ':': 2, ';': 2, '!': 2, "'": 2, '(': 5, ')': 5, '[': 4, ']': 4}


def width(text):
    total = 0
    for c in text:
        if ord(c) >= 0x2E80 or c in '…→・·×～': total += 9
        else: total += NARROW.get(c, 7 if c in '@~' else 6)
    return total


def lines(page):
    """Lines a page takes: Latin text wraps at spaces, a run without spaces (as CJK text is) wherever it reaches the edge."""
    count = 0
    for paragraph in page.split('\n'):
        used, rows = 0, 1
        for word in paragraph.split(' '):
            size = width(word)
            if used and used + NARROW[' '] + size <= 114: used += NARROW[' '] + size; continue
            if used: rows += 1
            rows += max(0, math.ceil(size / 114) - 1)
            used = size - 114 * max(0, math.ceil(size / 114) - 1)
        count += rows
    return count


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def book():
    """The written book's components: translated pages and name, its title only the inner book's own label. Text in item data is
    written as JSON in a string."""
    return {'minecraft:written_book_content': {'title': "Refinery 'n' Reactors", 'author': "Refinery 'n' Reactors", 'resolved': True,
                                               'pages': [json.dumps({'translate': f'book.{NS}.manual.page_{i}'}) for i in range(len(PAGES))]},
            'minecraft:custom_name': json.dumps({'translate': f'book.{NS}.manual', 'italic': False})}


def criterion(item):
    """An item held, or (written effect:id) a status effect gained."""
    if item.startswith('effect:'):
        return {'trigger': 'minecraft:effects_changed', 'conditions': {'effects': {item[7:]: {}}}}
    return {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': item}]}}


def data():
    for page in PAGES:
        for index, text in enumerate(page):
            assert lines(text) <= 14, (LANGUAGES[index], lines(text), text)
    write(D / 'loot_table/gameplay/manual.json', {'type': 'minecraft:advancement_reward', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': 'minecraft:written_book', 'functions': [{'function': 'minecraft:set_components', 'components': book()}]}]}]})
    write(D / 'recipe/manual.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                                     'ingredients': [{'item': 'minecraft:book'}, {'item': 'minecraft:iron_ingot'}],
                                     'result': {'id': 'minecraft:written_book', 'count': 1, 'components': book()}})
    folder = D / 'advancement/guide'
    # Only the steps below are written; guide advancements made by other generators stay.
    for name, parent, icon, frame, items, _, _ in STEPS:
        display = {'icon': {'id': icon}, 'title': {'translate': f'advancement.{NS}.guide.{name}'},
                   'description': {'translate': f'advancement.{NS}.guide.{name}.description'}, 'frame': frame,
                   'show_toast': True, 'announce_to_chat': False}
        advancement = {'display': display}
        if parent is None:
            display['background'] = f'{NS}:textures/block/steel_block.png'
            advancement['criteria'] = {'joined': {'trigger': 'minecraft:tick'}}
            advancement['rewards'] = {'loot': [f'{NS}:gameplay/manual']}
        else:
            advancement['parent'] = f'{NS}:guide/{parent}'
            advancement['criteria'] = {item.split(':')[-1]: criterion(item) for item in items}
            advancement['requirements'] = [[item.split(':')[-1] for item in items]]
        write(folder / f'{name}.json', advancement)


def language():
    for index, code in enumerate(LANGUAGES):
        path = A / 'lang' / f'{code}.json'
        strings = json.loads(path.read_text())
        # Guide keys of other generators (e.g. industrial_fertilizers) stay; this file's steps are overwritten below.
        strings = {k: v for k, v in strings.items() if not k.startswith(f'book.{NS}.manual')}
        strings[f'book.{NS}.manual'] = TITLE[index]
        for i, page in enumerate(PAGES): strings[f'book.{NS}.manual.page_{i}'] = page[index]
        for name, _, _, _, _, title, description in STEPS:
            strings[f'advancement.{NS}.guide.{name}'] = title[index]
            strings[f'advancement.{NS}.guide.{name}.description'] = description[index]
        write(path, strings)


if __name__ == '__main__':
    data()
    language()
    print(f'Generated the manual ({len(PAGES)} pages) and {len(STEPS)} guide advancements')
