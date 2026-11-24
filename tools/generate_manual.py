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
    ("Refinery 'n' Reactors\nPlant Manual\n\nBuild plants with Create's machines that give you more metal from every ore, far more rotation, faster farms, mining charges and goods to sell.",
     "Refinery 'n' Reactors\n工場の説明書\n\nCreateの機械で工場を組むと、鉱石から取れる金属が増え、回転力・農業・採掘・出荷が強くなります。",
     "Refinery 'n' Reactors\n工厂说明书\n\n用机械动力的机器搭建工厂，可从矿石获得更多金属，并强化旋转动力、农业、采矿和出货。"),
    ("Contents\n\n1 Looking things up\n2 First: steel, stainless, vessel\n3 More metal from ores\n4 More rotation\n5 Faster farms\n6 Mining charges\n7 Selling products\n8 Better materials\n9 Grades and pipes\n10 When it stops",
     "目次\n1 調べ方\n2 最初に：鋼鉄・ステンレス・槽\n3 鉱石から金属を増やす\n4 回転力を増やす\n5 農業を速くする\n6 発破で掘る\n7 製品を出荷する\n8 よい素材を作る\n9 等級と配管\n10 止まったとき",
     "目录\n1 查阅方法\n2 首先：钢、不锈钢与带盖槽\n3 从矿石增产金属\n4 增加旋转动力\n5 加快农业\n6 爆破采矿\n7 出货产品\n8 制作更好的材料\n9 等级与管道\n10 停机时"),
    ("1 Looking things up\n\nHold a part and press W to open its Ponder, a moving guide.\nJEI shows every recipe and its amounts.\nLook at a running machine with Create's goggles to see its grades and why it stopped.",
     "1 調べ方\n\n部品を持ってWを長押しすると、動く説明（Ponder）が開きます。\n材料と量はJEIで調べます。\n動いている設備はCreateのゴーグルで見ると、等級と止まった理由がわかります。",
     "1 查阅方法\n\n手持部件按住W，打开会动的说明（思索）。\n配方和用量在JEI中查看。\n用机械动力的护目镜看运行中的设备，可了解等级和停机原因。"),
    ("Your path\n\nThe advancement screen (L) has this mod's path. Each one you earn says what it gives you and the next step.\n\nFirst set up Create's basics: rotation, a blaze burner, a basin with a mixer, and a press.",
     "進め方\n\n実績画面（Lキー）に、このmodの道筋があります。取った実績の説明に、得られるものと次にやることが書いてあります。\n\nまずCreateの基本（回転・ブレイズバーナー・鉢とミキサー・プレス）を用意します。",
     "推进方式\n\n进度界面（L键）中有本模组的路线。每获得一项进度，说明里都会写出它带来的好处和下一步。\n\n先准备好机械动力的基础：旋转动力、烈焰人燃烧室、工作盆与搅拌器、压机。"),
    ("2 First: steel\n\nCrush coal or charcoal into carbon powder.\nSuperheat 4 iron ingots + 1 carbon powder under a mixer: 4 steel workpieces.\nPress each five times: 4 steel ingots in total.",
     "2 最初に：鋼鉄\n\n石炭・木炭を粉砕して炭素粉にします。\nバーナーを超加熱し、ミキサーで鉄インゴット4個と炭素粉1個から中間素材4個を作ります。\nそれぞれ5回プレスすると鋼鉄計4個。",
     "2 首先：钢\n\n粉碎煤或木炭得到碳粉。\n超级加热燃烧室，用搅拌器将4个铁锭与1份碳粉混成4份中间材料。\n每份压制5次，共得4个钢锭。"),
    ("Stainless steel\n\nAll in a basin. Heat 2 pentlandite: nickel oxide. Superheat it with carbon powder: nickel. Superheat 2 chromite and 1 carbon powder: ferrochrome.\nSuperheat 6 iron, 2 ferrochrome, 1 nickel: 10 stainless.",
     "ステンレス鋼\n\nすべて鉢で作ります。ペントランド鉱2個を加熱して酸化ニッケル、炭素粉と超加熱してニッケル。クロム鉄鉱2個と炭素粉1個を超加熱してフェロクロム。\n鉄6・フェロクロム2・ニッケル1を超加熱してステンレス鋼10個。",
     "不锈钢\n\n全部在工作盆中制作。加热2个镍黄铁矿得氧化镍，与碳粉超级加热得镍。超级加热2个铬铁矿与1份碳粉得铬铁。\n超级加热6铁、2铬铁、1镍得10个不锈钢。"),
    ("The lidded vessel\n\nMake it from stainless steel plates, an iron trapdoor and a basin. Set 3 x 3, stack 1 to 4 high, put a stirrer in each layer, turn the bottom centre at 32 RPM and heat from below.",
     "ふた付き槽\n\nステンレス鋼板・鉄のトラップドア・鉢で作り、3×3に並べて1～4段に積みます。各段に撹拌翼を入れ、底の中央を32 RPM以上で回し、下から加熱します。",
     "带盖槽\n\n用不锈钢板、铁活板门和工作盆制作，摆成3×3，叠1～4层。每层放入搅拌叶，底部中心以32 RPM以上驱动，从下方加热。"),
    ("Fluids go in and out by pipe, solids by funnel. Keep the hatch closed.\nLook the vessel up in JEI to see what it makes.\n\nPonder: Building a stirred reactor",
     "流体はパイプ、固体はファンネルで出し入れします。ハッチは閉じます。\nJEIで槽を調べると作れる物がわかります。\nPonder：撹拌反応槽を組み立てる",
     "流体用管道、固体用漏斗进出。舱口保持关闭。\n在JEI中查带盖槽可看到全部产物。\n思索：建造搅拌反应槽"),
    ("3 More metal from ores\n\nIron, copper, zinc, tin; no gold.\nAt 64 RPM, a large leaching tank mixes 3 crushed ores + 750 mB dilute acid + 250 mB compressed air or oxygen into 4000 mB slurry.",
     "3 鉱石から金属を増やす\n\n鉄・銅・亜鉛・錫が対象。金は対象外。\n大型浸出攪拌槽を64 RPMで回し、砕いた鉱石3個＋希硫酸750 mB＋圧搾空気か酸素250 mBからスラリー4000 mBを作ります。",
     "3 从矿石增产金属\n\n支持铁、铜、锌、锡，不含金。\n大型浸出槽64 RPM运行，将3份粉碎矿石＋750 mB稀酸＋250 mB压缩空气或氧气变为4000 mB矿浆。"),
    ("Clarify and recover\n\n1–4 RPM: 1000 mB slurry gives 750 mB solution + 2 mud.\nElectrolysis: 1000 mB solution gives 3 ingots + 250 mB acid for 20 kJ. Dirt raises this work.\nPonder: Wet processing",
     "沈降と金属回収\n\n沈殿池を1〜4 RPMで回し、スラリー1000 mBを精製液750 mBと尾鉱泥2個へ。\n電解では精製液1000 mBから金属3個と再生酸250 mBを回収。通常20 kJで、濁りがあると電力が増えます。\nPonder：湿式浸出と低速沈降",
     "沉降与回收\n\n沉淀池1–4 RPM：1000 mB矿浆分为750 mB清液和2份尾矿泥。\n电解1000 mB清液得3个锭和250 mB回用酸，耗能20 kJ，杂质会增加能耗。\n思索：湿法浸出与低速沉降"),
    ("Hydrogen\n\nPower comes from Create: Electro Energetics generators and wires.\nCells, electrode heads and a controller split water into H2 and O2.\n\nPonder: Making hydrogen by water electrolysis",
     "水素\n\n電気はCreate: Electro Energeticsの発電機と配線を使います。\n電解セルに電極ヘッドと制御盤を付けて水を入れると、水素と酸素が別々に出ます。\n\nPonder：水の電解で水素を作る",
     "氢\n\n电力使用Create: Electro Energetics的发电机和布线。\n搭建电解槽、电极吊架和控制器并加水，可分别得到氢和氧。\n\n思索：电解水制取氢气"),
    ("Sulfuric acid\n\nBurn sulfur in a heated vessel with air from an open pipe end. Stir the gas into water with air and an iron nugget: dilute acid, no power.\nUse ceramic pipe.\nPonder: From sulfur and recovered gas to sulfuric acid",
     "硫酸\n\nネザーラックを粉砕した硫黄粉を、加熱した槽で空気と燃やします。空気は配管の口から吸います。その気体を撹拌槽で水・空気・鉄ナゲットと混ぜると希硫酸。電気は要りません。\n酸は粘土を焼いたセラミック管で送ります。\nPonder：硫黄と回収ガスから硫酸を作る",
     "硫酸\n\n将粉碎下界岩得到的硫粉在加热的槽中用空气燃烧，空气从管口吸入。再在搅拌槽中与水、空气和铁粒混合即得稀硫酸，无需电力。\n酸用黏土烧制的陶瓷管输送。\n思索：从硫与回收气体制取硫酸"),
    ("4 More rotation\n\nGoal: per blaze burner, turbines give up to twice a steam engine's rotation.\nA horizontal tank over burners makes high-pressure steam when heated, superheated when superheated.\nRefined diesel also runs diesel engines.",
     "4 回転力を増やす\n\n目標：ブレイズバーナー1台あたり、最大で蒸気エンジンの約2倍の回転力。\nバーナーの上の横型タンクは、加熱で高圧蒸気、超加熱で過熱蒸気を作ります。\n精製した軽油はディーゼルエンジンにも使えます。",
     "4 增加旋转动力\n\n目标：每个烈焰人燃烧室最多可获得约为蒸汽引擎两倍的旋转动力。\n燃烧室上方的卧式储罐加热产生高压蒸汽，超级加热产生过热蒸汽。\n精制柴油也可用于柴油发动机。"),
    ("Feed the high-pressure turbine, pump its exhaust to the low-pressure one, and cool that with a condenser.\nRBMK gives far more steam: uranium, enrichment, pellets, fuel rods. Fusion irradiates cartridges into netherite scrap.",
     "高圧タービンに通し、排気を低圧タービンへ送り、復水器で冷やします。\nRBMK炉はけた違いの蒸気を出します：ウラン鉱石→濃縮→ペレット→燃料棒。その先の核融合炉では、照射カートリッジからネザライトの欠片を作れます。",
     "先送入高压汽轮机，再把排汽泵送至低压汽轮机，并用冷凝器冷却。\nRBMK反应堆可产生多得多的蒸汽：铀矿石→浓缩→芯块→燃料棒。更远处的聚变反应堆可用辐照组件盒制取下界合金碎片。"),
    ("5 Faster farms\n\nGoal: one fertilizer grows the 3 x 3 crops around the one you use it on; deployers spread it too.\nCompress air, separate nitrogen, make ammonia, then nitric acid and ammonium nitrate.",
     "5 農業を速くする\n\n目標：肥料1個で、使った作物の周囲3×3がまとめて育つ。デプロイヤーでも使えます。\n空気を圧縮して窒素を分け、アンモニア→硝酸→硝酸アンモニウム。",
     "5 加快农业\n\n目标：1个肥料让所用作物周围3×3一起生长，机械手也能使用。\n压缩空气分离氮，制氨→硝酸→硝酸铵。"),
    ("Ammonium nitrate grows each crop as far as two bone meals; ammonium sulfate as far as one.\n\nPonder: From ammonia to nitric acid and fertilizers",
     "硝酸アンモニウムは骨粉2個分、硫酸アンモニウムは1個分ずつ育てます。\n\nPonder：アンモニアから硝酸と肥料を作る",
     "硝酸铵使每株作物生长相当于2个骨粉，硫酸铵相当于1个。\n\n思索：从氨制取硝酸与肥料"),
    ("6 Mining charges\n\nGoal: dig wide areas without a pickaxe.\nMix ammonium nitrate with diesel into ANFO and craft blasting charges. A quarry drive digs a marker's area; its side belt brings the ore back to the drive.",
     "6 発破で掘る\n\n目標：つるはしを使わず広い範囲を掘る。\n硝酸アンモニウムと軽油でANFOを作り、発破薬にします。クァーリー駆動部はマーカーの範囲を掘り、側面のベルトで鉱石を駆動部の横まで戻します。",
     "6 爆破采矿\n\n目标：不用镐开采大片区域。\n用硝酸铵与柴油制成ANFO，再做成采矿炸药。采石场驱动器开采标记器划定的区域，用侧面传送带把矿石送回驱动器旁。"),
    ("7 Selling products\n\nGoal: emeralds and experience.\nAttach the dispatch docket to a Create item vault. It orders fuel, alloys, rubber, fertilizer, building materials and plastics; full cargo is paid for.\nPonder: Deliver industrial products",
     "7 製品を出荷する\n\n目標：エメラルドと経験値。\n出荷票をCreateのアイテム保管庫に付けます。燃料・合金・ゴム・肥料・建材・樹脂の注文が届き、そろえて出荷すると報酬が出ます。\nPonder：工業製品を出荷する",
     "7 出货产品\n\n目标：绿宝石和经验。\n把出货单装在机械动力的物品保险库上。它会订购燃料、合金、橡胶、肥料、建材和树脂，凑齐出货即可获得报酬。\n思索：交付工业产品"),
    ("8 Better materials\n\nSix new ores give metals for tougher pipes and tanks. Crush them first; each metal has its own Ponder.\nMolybdenum: roast with oxygen, reduce with hydrogen; nickel can be made this way too.",
     "8 よい素材を作る\n\n6種類の新しい鉱石から、丈夫な配管やタンク用の金属を作ります。まず粉砕します。金属ごとにPonderがあります。\nモリブデン：酸素で焙焼し水素で還元。ニッケルもこの方法で作れます。",
     "8 制作更好的材料\n\n六种新矿石可制成更坚固的管道和储罐用金属。先粉碎，每种金属都有思索。\n钼：与氧焙烧，再用氢还原。镍也可用此法制取。"),
    ("Aluminium, titanium, zirconium: dissolve in acid to an oxide, then use the molten salt electrolysis furnace. Make zirconia first: the furnace needs it.\nPure chromium: thermite with aluminium.",
     "アルミ・チタン・ジルコニウム：酸で溶かして酸化物にし、溶融塩電解炉で金属にします。炉に使うジルコニアを先に作ります。\n純粋なクロム：アルミニウムでテルミット。",
     "铝、钛、锆：用酸溶成氧化物，再用熔盐电解炉炼成金属。先制取电解炉需要的氧化锆。\n纯铬：用铝进行铝热还原。"),
    ("Oil: preheat crude, split it in a 2 x 2 column, crack naphtha, polymerise ethylene and press plastic sheets for gaskets.",
     "石油：原油を予熱し、2×2の分留塔で分け、ナフサを分解し、エチレンを重合してプラスチック板（ガスケット用）にします。",
     "石油：预热原油，在2×2分馏塔中分离，裂解石脑油，聚合乙烯并压成塑料板（用于垫片）。"),
    ("9 Grades and pipes\n\nChoose pipes and tanks by heat grade, pressure grade and corrosion resistance. Incompatible fluids are refused. Pumps also move gases.\nPonder: Pressure, the basics",
     "9 等級と配管\n\n配管とタンクは温度等級・圧力等級・耐食で選びます。合わない流体は搬入できません。気体もポンプで送ります。\nPonder：圧力の基本",
     "9 等级与管道\n\n按温度等级、压力等级和耐腐蚀性选择管道与储罐。不合适的流体会被拒收，气体也由泵输送。\n思索：压力基础"),
    ("10 When it stops\n\nLook at it with goggles: they say why. Common causes:\n- an outlet is full\n- not hot enough or not turning fast enough\n- a hatch is open\n- wrong amounts (check JEI)",
     "10 止まったとき\n\nゴーグルで見ると理由がわかります。よくある原因：\n・出口が満杯\n・温度や回転が足りない\n・ハッチが開いている\n・材料の量が違う（JEIで確認）",
     "10 停机时\n\n用护目镜查看即可知道原因。常见原因：\n·出口已满\n·温度或转速不足\n·舱口打开\n·材料用量不对（查看JEI）"),
]

# Guide advancements: id, parent, icon, frame, criterion items, and the title and next-step description in each language.
# Goals (more metal, rotation, farms, mining, selling, fusion) are framed; the steps leading to them are tasks.
STEPS = [
    ('root', None, f'{NS}:lidded_vessel', 'task', None,
     ("Refinery 'n' Reactors", "Refinery 'n' Reactors", "Refinery 'n' Reactors"),
     ("Read the manual you were given. First, make steel.", "もらった説明書を読もう。まずは鋼鉄を作ろう。", "阅读获得的说明书。先来炼钢。")),
    ('dispatch', 'root', f'{NS}:industrial_dispatch_board', 'goal', [f'{NS}:industrial_dispatch_board'],
     ("Open for business", "出荷開始", "开张营业"),
     ("Put it on an item vault: orders pay emeralds and experience.", "アイテム保管庫に付けよう。注文の品で、エメラルドと経験値がもらえる。", "装在物品保险库上：完成订单可获得绿宝石和经验。")),
    ('steel', 'root', f'{NS}:steel_ingot', 'task', [f'{NS}:steel_ingot'],
     ("Steel", "鋼鉄", "钢"),
     ("Next: roast pentlandite in a basin and reduce it with carbon to nickel.", "次：ペントランド鉱を鉢で焙焼し、炭素で還元してニッケルにしよう。", "下一步：在工作盆中焙烧镍黄铁矿，再用碳还原成镍。")),
    ('nickel', 'steel', f'{NS}:nickel_ingot', 'task', [f'{NS}:nickel_ingot'],
     ("Nickel", "ニッケル", "镍"),
     ("Next: ferrochrome from chromite and carbon, then stainless steel.", "次：クロム鉄鉱と炭素でフェロクロムを作り、ステンレス鋼へ。", "下一步：用铬铁矿和碳制铬铁，再炼不锈钢。")),
    ('stainless_steel', 'nickel', f'{NS}:stainless_steel_ingot', 'task', [f'{NS}:stainless_steel_ingot'],
     ("Stainless steel", "ステンレス鋼", "不锈钢"),
     ("Next: a stainless lidded vessel with a stirrer, turbines, or oil.", "次：ステンレス鋼のふた付き槽と撹拌翼、タービン、石油へ。", "下一步：不锈钢带盖槽与搅拌叶、汽轮机或石油。")),
    ('vessel', 'stainless_steel', f'{NS}:lidded_vessel', 'task', [f'{NS}:lidded_vessel'],
     ("The heart of the plant", "工場の心臓", "工厂的心脏"),
     ("Next: electrolysis cells split water into hydrogen and oxygen.", "次：電解セルで水を水素と酸素に分けよう。", "下一步：用电解槽把水分解成氢和氧。")),
    ('electrolysis', 'vessel', f'{NS}:electrolysis_cell', 'task', [f'{NS}:electrolysis_cell'],
     ("Splitting water", "水を分ける", "分解水"),
     ("Next: sulfuric acid, to get more metal from your ores.", "次：硫酸を作り、鉱石から取れる金属を増やそう。", "下一步：制造硫酸，从矿石获得更多金属。")),
    ('sulfuric_acid', 'electrolysis', f'{NS}:sulfuric_acid_bucket', 'task', [f'{NS}:sulfuric_acid_bucket'],
     ("Sulfuric acid", "硫酸", "硫酸"),
     ("Next: leach crushed ore in a heated vessel.", "次：大型浸出攪拌槽と円形沈殿池で鉱石を処理しよう。", "下一步：在加热槽中浸出粉碎矿石。")),
    ('leaching', 'sulfuric_acid', f'{NS}:copper_sulfate_solution_bucket', 'goal',
     [f'{NS}:iron_sulfate_solution_bucket', f'{NS}:copper_sulfate_solution_bucket', f'{NS}:zinc_sulfate_solution_bucket', f'{NS}:tin_sulfate_solution_bucket'],
     ("More metal", "金属が増える", "金属增产"),
     ("Leach directly into purified solution, then recover three ingots by electrolysis.", "浸出で精製溶液を作り、電解でインゴット3個と再生酸を回収。", "浸出直接得到精制液，再电积回收3锭及再生酸。")),
    ('zirconia', 'sulfuric_acid', f'{NS}:zirconia', 'task', [f'{NS}:zirconia'],
     ("Zirconia", "ジルコニア", "氧化锆"),
     ("Next: build the molten salt electrolysis furnace.", "次：溶融塩電解炉を組もう。", "下一步：搭建熔盐电解炉。")),
    ('aluminum', 'zirconia', f'{NS}:aluminum_ingot', 'task', [f'{NS}:aluminum_ingot', f'{NS}:titanium_ingot'],
     ("Furnace metals", "電解炉の金属", "电解炉金属"),
     ("Aluminium or titanium from the furnace. Next: chromium by thermite.", "電解炉でアルミかチタンを得た。次：テルミットでクロム。", "从电解炉得到铝或钛。下一步：铝热法制铬。")),
    ('power', 'stainless_steel', f'{NS}:high_pressure_turbine_unit', 'goal', [f'{NS}:high_pressure_turbine_unit', f'{NS}:fuel_cell'],
     ("More rotation", "回転力が増える", "旋转动力增加"),
     ("Up to twice a steam engine per burner. Next: uranium fuel.", "バーナー1台で蒸気エンジンの最大約2倍。次：ウラン燃料。", "每个燃烧室最多约为蒸汽引擎的两倍。下一步：铀燃料。")),
    ('nuclear', 'power', f'{NS}:fuel_rod', 'goal', [f'{NS}:fuel_rod'],
     ("Fuel rod", "燃料棒", "燃料棒"),
     ("Load it into the RBMK reactor for far more steam. Beyond lies fusion.", "RBMK炉に入れればけた違いの蒸気。その先は核融合。", "装入RBMK反应堆可得到多得多的蒸汽。更远处是聚变。")),
    ('fusion', 'nuclear', f'{NS}:fusion_reactor_controller', 'challenge', [f'{NS}:fusion_reactor_controller'],
     ("The final plant", "最後の工場", "最终工厂"),
     ("Build the tokamak; its neutrons turn irradiation cartridges into netherite scrap.", "トカマクを組もう。中性子で照射カートリッジがネザライトの欠片になる。", "搭建托卡马克，其中子可把辐照组件盒变成下界合金碎片。")),
    ('plastic', 'stainless_steel', f'{NS}:plastic_sheet', 'task', [f'{NS}:plastic_sheet'],
     ("Plastics", "プラスチック", "塑料"),
     ("Next: rubber gaskets for the compressor.", "次：コンプレッサー用のゴムのガスケット。", "下一步：为压缩机制作橡胶垫片。")),
    ('compressor', 'plastic', f'{NS}:reciprocating_compressor', 'task', [f'{NS}:reciprocating_compressor'],
     ("Under pressure", "圧縮", "加压"),
     ("Next: separate air, then ammonia and nitric acid.", "次：空気を分離し、アンモニアと硝酸へ。", "下一步：分离空气，再制造氨和硝酸。")),
    ('fertilizer', 'compressor', f'{NS}:ammonium_nitrate', 'goal', [f'{NS}:ammonium_nitrate', f'{NS}:ammonium_sulfate'],
     ("Faster farms", "農業が速くなる", "农业加速"),
     ("One fertilizer grows the 3 x 3 crops around it. Next: ANFO.", "肥料1個で周囲3×3の作物が育つ。次：ANFO。", "1个肥料让周围3×3作物生长。下一步：ANFO。")),
    ('mining', 'fertilizer', f'{NS}:blasting_charge', 'goal', [f'{NS}:blasting_charge'],
     ("Mining charges", "発破", "爆破采矿"),
     ("Blast wide areas, or quarry them with a quarry drive.", "広い範囲を発破するか、クァーリー駆動部で採掘しよう。", "爆破大片区域，或用采石场驱动器开采。")),
    # Further advancements across the plant, each one on the first piece of equipment or product of its kind.
    ('air_separation', 'compressor', f'{NS}:fractionating_column', 'task', [f'{NS}:fractionating_column'],
     ("Air, split", "空気を分ける", "分离空气"),
     ("Cooled, compressed air separates into nitrogen, oxygen and argon.", "冷やして圧縮した空気が、窒素・酸素・アルゴンに分かれる。", "冷却压缩后的空气分离成氮、氧和氩。")),
    ('separator', 'compressor', f'{NS}:ammonia_separator', 'task', [f'{NS}:ammonia_separator'],
     ("Gas from liquid", "気液を分ける", "气液分离"),
     ("A stream that is part gas and part liquid goes in at the top side; liquid leaves below, gas above.", "気液が混ざった流れを上の側面から入れる。液体は下、気体は上から出る。", "气液混合流从上侧面进入：液体从下方、气体从上方流出。")),
    ('ammonia', 'separator', f'{NS}:ammonia_gas_bucket', 'task', [f'{NS}:ammonia_gas_bucket'],
     ("Ammonia", "アンモニア", "氨"),
     ("Next: burn it over a catalyst for nitric acid.", "次：触媒で酸化して硝酸を作ろう。", "下一步：用催化剂氧化制硝酸。")),
    ('nitric_acid', 'ammonia', f'{NS}:nitric_acid_bucket', 'task', [f'{NS}:nitric_acid_bucket'],
     ("Nitric acid", "硝酸", "硝酸"),
     ("Next: neutralise it with ammonia for fertilizer.", "次：アンモニアで中和して肥料にしよう。", "下一步：用氨中和制成肥料。")),
    ('gas_storage', 'compressor', f'{NS}:gas_holder', 'task', [f'{NS}:gas_holder', f'{NS}:gas_receiver', f'{NS}:gas_tank_container'],
     ("Storing gas", "気体の貯蔵", "储存气体"),
     ("Holders, receivers and containers keep gas at pressure.", "ガスホルダー、レシーバー、タンクコンテナで、気体を圧力ごと貯める。", "气体储罐、受槽和容器可保压储气。")),
    ('reaction_tower', 'vessel', f'{NS}:reaction_tower', 'task', [f'{NS}:reaction_tower'],
     ("Reaction tower", "反応塔", "反应塔"),
     ("Catalysts on trays turn feed gas into products.", "棚段の触媒が、原料ガスを製品に変える。", "塔板上的催化剂把原料气变成产品。")),
    ('stirred', 'vessel', f'{NS}:vessel_stirrer', 'task', [f'{NS}:vessel_stirrer'],
     ("Stirred tank", "撹拌槽", "搅拌槽"),
     ("A stirrer in a stainless vessel mixes reactants and dissolves solids.", "ステンレス槽の撹拌翼で、材料を混ぜ、固体を溶かす。", "不锈钢槽中的搅拌叶可混合反应物并溶解固体。")),
    ('absorption', 'vessel', f'{NS}:large_absorption_tower', 'task', [f'{NS}:large_absorption_tower'],
     ("Absorption tower", "吸収塔", "吸收塔"),
     ("Wash gases with liquid to pull out one component.", "ガスを液体で洗い、成分を1つ取り出す。", "用液体洗涤气体，分离出某一成分。")),
    ('gasifier', 'vessel', f'{NS}:steam_gasifier', 'task', [f'{NS}:steam_gasifier'],
     ("Steam gasifier", "水蒸気ガス化炉", "水蒸气气化炉"),
     ("Coke and steam make carbon monoxide and hydrogen.", "コークスと水蒸気から、一酸化炭素と水素を作る。", "焦炭与水蒸气生成一氧化碳和氢。")),
    ('oil_products', 'vessel', f'{NS}:naphtha_bucket', 'task', [f'{NS}:naphtha_bucket', f'{NS}:low_sulfur_diesel_bucket'],
     ("Oil products", "石油製品", "石油产品"),
     ("Distil crude oil, then desulfurise it.", "原油を分留し、脱硫する。", "蒸馏原油并脱硫。")),
    ('methanol', 'gasifier', f'{NS}:methanol_bucket', 'task', [f'{NS}:methanol_bucket'],
     ("Methanol", "メタノール", "甲醇"),
     ("Synthesis gas over a catalyst.", "合成ガスを触媒に通す。", "合成气通过催化剂。")),
    ('hydrogen', 'electrolysis', f'{NS}:hydrogen_gas_bucket', 'task', [f'{NS}:hydrogen_gas_bucket'],
     ("Hydrogen", "水素", "氢气"),
     ("Keep it away from flames: a leak can explode.", "火気厳禁。漏れると爆発する。", "远离明火：泄漏会爆炸。")),
    ('chlorine', 'electrolysis', f'{NS}:chlorine_bucket', 'task', [f'{NS}:chlorine_bucket', f'{NS}:caustic_soda_solution_bucket'],
     ("Chlor-alkali", "クロルアルカリ", "氯碱"),
     ("Brine gives chlorine and caustic soda. Chlorine is toxic: wear a gas mask.", "食塩水から塩素と苛性ソーダ。塩素は有毒なのでガスマスクを。", "盐水生成氯和烧碱。氯有毒：请戴防毒面具。")),
    ('pvc', 'plastic', f'{NS}:pvc_resin', 'task', [f'{NS}:pvc_resin'],
     ("PVC", "塩化ビニル樹脂", "聚氯乙烯"),
     ("From ethylene and chlorine.", "エチレンと塩素から作る。", "由乙烯和氯制成。")),
    ('pipes', 'stainless_steel', f'{NS}:stainless_steel_pipe', 'task', [f'{NS}:stainless_steel_pipe', f'{NS}:nickel_alloy_pipe', f'{NS}:zirconium_pipe', f'{NS}:ceramic_pipe'],
     ("Process piping", "工程配管", "工艺管道"),
     ("Each material has its own temperature, pressure and corrosion limits.", "材質ごとに、温度・圧力・耐食の限界がある。", "每种材料有各自的温度、压力和耐腐蚀极限。")),
    ('pumps', 'pipes', f'{NS}:electric_pump', 'task', [f'{NS}:electric_pump', f'{NS}:high_pressure_pump'],
     ("Pumps", "ポンプ", "泵"),
     ("Electric pumps and high-pressure pumps move fluid through the plant.", "電動ポンプと高圧ポンプで、工場に流体を巡らせる。", "电动泵和高压泵让流体在工厂中流动。")),
    ('tanks', 'stainless_steel', f'{NS}:stainless_steel_tank', 'task', [f'{NS}:stainless_steel_tank', f'{NS}:horizontal_fluid_tank', f'{NS}:cryogenic_tank', f'{NS}:pressure_tank'],
     ("Storage tanks", "貯蔵タンク", "储罐"),
     ("Pick a tank that rates for your fluid's temperature and pressure.", "流体の温度・圧力に耐える定格のタンクを選ぼう。", "选择额定温度和压力足够的储罐。")),
    ('boiler', 'tanks', f'{NS}:stainless_boiler', 'task', [f'{NS}:stainless_boiler'],
     ("Boiler", "ボイラー", "锅炉"),
     ("Heat the tank from below to make steam.", "タンクを下から加熱して蒸気を作る。", "从下方加热储罐产生蒸汽。")),
    ('instruments', 'pipes', f'{NS}:pipe_thermometer', 'task', [f'{NS}:pipe_thermometer', f'{NS}:pipe_pressure_gauge', f'{NS}:pipe_flow_meter', f'{NS}:flow_regulator', f'{NS}:pid_controller'],
     ("Instruments", "計器", "仪表"),
     ("Measure temperature, pressure and flow, then control them with a PID loop.", "温度・圧力・流量を測り、PID制御で保つ。", "测量温度、压力和流量，并用PID回路控制。")),
    ('gas_safety', 'stainless_steel', f'{NS}:gas_mask', 'task', [f'{NS}:gas_mask', f'{NS}:gas_detector', f'{NS}:relief_scrubber'],
     ("Gas safety", "ガスの安全", "气体安全"),
     ("A mask filters poison; a detector warns; a scrubber neutralises vented gas.", "マスクは毒をろ過し、検知器は知らせ、除害装置は排気を中和する。", "面具过滤毒气，探测器报警，洗涤器中和排放气体。")),
    ('converter', 'steel', f'{NS}:converter_shell', 'task', [f'{NS}:converter_shell'],
     ("Converter", "転炉", "转炉"),
     ("Oxygen blown through hot metal; the gas it makes is poisonous.", "溶けた金属に酸素を吹き込む。出る転炉ガスは有毒。", "向熔融金属吹氧；产生的转炉煤气有毒。")),
    ('cooling', 'power', f'{NS}:natural_draft_cooling_tower', 'task', [f'{NS}:natural_draft_cooling_tower', f'{NS}:cooler_radiator', f'{NS}:condenser_shell'],
     ("Cooling", "冷却", "冷却"),
     ("Cooling towers, radiators and condensers turn waste heat back into water.", "冷却塔・ラジエーター・復水器で、廃熱を水に戻す。", "冷却塔、散热器和冷凝器把废热变回水。")),
    ('batteries', 'power', f'{NS}:lead_acid_battery_casing', 'task', [f'{NS}:lead_acid_battery_casing', f'{NS}:lithium_ion_battery_casing'],
     ("Batteries", "蓄電池", "蓄电池"),
     ("Store electricity in lead-acid or lithium-ion banks.", "鉛蓄電池かリチウムイオン電池に電気をためる。", "用铅酸或锂离子电池储存电力。")),
    ('flywheel', 'power', f'{NS}:large_flywheel', 'task', [f'{NS}:large_flywheel'],
     ("Flywheel", "大型フライホイール", "大型飞轮"),
     ("Smooths out rotation for heavy machines.", "大型機械の回転を安定させる。", "为重型机械稳定旋转。")),
    ('transmission', 'power', f'{NS}:transmission_tower', 'task', [f'{NS}:transmission_tower'],
     ("Transmission", "送電", "输电"),
     ("Carry electricity over long distances.", "電気を遠くまで運ぶ。", "远距离输送电力。")),
    ('steam_tools', 'stainless_steel', f'{NS}:steam_gun', 'task', [f'{NS}:steam_gun', f'{NS}:steam_hammer'],
     ("Steam tools", "蒸気の道具", "蒸汽工具"),
     ("Steam pressure, in your hands.", "蒸気の圧力を手に持つ。", "把蒸汽压力握在手中。")),
    ('centrifuge', 'power', f'{NS}:gas_centrifuge', 'task', [f'{NS}:gas_centrifuge'],
     ("Enrichment", "濃縮", "浓缩"),
     ("Spin uranium hexafluoride to raise the fissile share.", "六フッ化ウランを遠心分離して、核分裂する割合を上げる。", "离心六氟化铀以提高易裂变成分。")),
    ('radiation', 'nuclear', f'{NS}:survey_meter', 'task', [f'{NS}:survey_meter', f'{NS}:personal_dosimeter'],
     ("Radiation", "放射線", "辐射"),
     ("Measure the dose, and shield what you cannot avoid.", "線量を測り、避けられないものは遮蔽する。", "测量剂量，无法避开的就加以屏蔽。")),
    ('reactor_console', 'nuclear', f'{NS}:reactor_control_console', 'task', [f'{NS}:reactor_control_console'],
     ("Control room", "制御室", "控制室"),
     ("Run the reactor from its console; AZ-5 stops it.", "操作卓から炉を運転する。AZ-5で停止。", "在控制台操作反应堆；AZ-5紧急停堆。")),
    ('fuel_pool', 'nuclear', f'{NS}:fuel_pool', 'task', [f'{NS}:fuel_pool', f'{NS}:spent_fuel_rod'],
     ("Spent fuel", "使用済み燃料", "乏燃料"),
     ("Cool spent rods in the pool before they are reprocessed.", "使用済み燃料棒は、再処理の前にプールで冷やす。", "乏燃料棒在后处理前要在水池中冷却。")),
    ('mox', 'fuel_pool', f'{NS}:mox_fuel_rod', 'task', [f'{NS}:mox_fuel_rod'],
     ("MOX fuel", "MOX燃料", "MOX燃料"),
     ("Recycle plutonium back into fuel.", "プルトニウムを燃料に戻す。", "把钚循环制成燃料。")),
    ('vitrification', 'fuel_pool', f'{NS}:vitrified_waste_canister', 'goal', [f'{NS}:vitrified_waste_canister'],
     ("Waste, sealed", "廃棄物の固化", "废物固化"),
     ("Melt high-level waste into glass in a canister.", "高レベル廃棄物をガラスにして缶に封じる。", "把高放废物熔成玻璃封入罐中。")),
    ('fusion_fuel', 'nuclear', f'{NS}:deuterium_gas_bucket', 'task', [f'{NS}:deuterium_gas_bucket', f'{NS}:tritium_gas_bucket'],
     ("Fusion fuel", "核融合燃料", "聚变燃料"),
     ("Deuterium from heavy water; tritium from lithium.", "重水素は重水から、三重水素はリチウムから。", "氘来自重水，氚来自锂。")),
    ('quarry', 'mining', f'{NS}:quarry_drive', 'task', [f'{NS}:quarry_drive'],
     ("Quarry", "採掘場", "采石场"),
     ("Drill out a whole area, down to bedrock.", "広い範囲を岩盤まで掘る。", "一直挖穿整片区域直到基岩。")),
    ('crane', 'mining', f'{NS}:crane_drive', 'task', [f'{NS}:crane_drive'],
     ("Gantry crane", "ガントリークレーン", "龙门吊"),
     ("Lift and place heavy parts.", "重い部品を持ち上げて運ぶ。", "起吊并安放重型部件。")),
    ('constructor', 'root', f'{NS}:facility_constructor', 'task', [f'{NS}:facility_constructor'],
     ("Facility constructor", "施設建設機", "设施建造机"),
     ("Choose a facility and let the constructor build it from a preview.", "設備を選び、プレビューどおりに建設してもらう。", "选择设施，按预览自动建造。")),
    ('vault_hatch', 'dispatch', f'{NS}:vault_hatch', 'task', [f'{NS}:vault_hatch'],
     ("Vault hatch", "保管庫ハッチ", "保险库舱口"),
     ("Open an item vault like a chest.", "アイテム保管庫をチェストのように開く。", "像箱子一样打开物品保险库。")),
    ('sprayer', 'fertilizer', f'{NS}:agricultural_sprayer', 'task', [f'{NS}:agricultural_sprayer'],
     ("Sprayer", "散布機", "喷洒机"),
     ("Spread fertilizer over a field.", "畑に肥料をまく。", "向田地喷洒肥料。")),
    # Milestones with a flavour of their own: a step in industrial history, a famous process, or a lesson learned the hard way.
    ('industrial_revolution', 'boiler', f'{NS}:low_pressure_turbine_unit', 'goal', [f'{NS}:low_pressure_turbine_unit'],
     ("Industrial Revolution", "産業革命", "工业革命"),
     ("Steam turned a wheel, and the wheel never stopped. Next: feed it from a reactor.", "蒸気が車輪を回し、その車輪は二度と止まらなかった。次：炉から蒸気を送ろう。", "蒸汽推动了车轮，而车轮再也没有停下。下一步：用反应堆供汽。")),
    ('bread_from_air', 'nitric_acid', f'{NS}:ammonium_nitrate', 'goal', [f'{NS}:ammonium_nitrate'],
     ("Bread from Air", "空気からパンを", "空气变面包"),
     ("Nitrogen from the sky now feeds the field.", "空の窒素が、畑を肥やす。", "天上的氮，如今养活了田地。")),
    ('black_gold', 'vessel', 'createdieselgenerators:crude_oil_bucket', 'task', ['createdieselgenerators:crude_oil_bucket'],
     ("Black Gold", "黒い黄金", "黑色黄金"),
     ("Crude oil. Everything else in this plant starts as a barrel of it.", "原油。この工場の多くは、この一杯から始まる。", "原油。这座工厂的许多东西，都从这一桶开始。")),
    ('transmutation', 'fusion', f'{NS}:irradiated_cartridge', 'challenge', [f'{NS}:irradiated_cartridge'],
     ("The Philosopher's Neutron", "賢者の中性子", "贤者之中子"),
     ("Alchemy, at last: a star turned one thing into another.", "ついに錬金術が完成した。星の力が、物質を別の物質に変えた。", "炼金术终于完成：恒星之力把一种物质变成了另一种。")),
    ('cold_as_ice', 'air_separation', f'{NS}:cryogenic_cold_box_drive', 'task', [f'{NS}:cryogenic_cold_box_drive'],
     ("Cold as Ice", "氷点下の世界", "冰点之下"),
     ("Air, cooled until it becomes a liquid.", "空気を、液体になるまで冷やす。", "把空气冷却到变成液体。")),
    ('chain_reaction', 'nuclear', f'{NS}:reactor_fuel_channel', 'goal', [f'{NS}:reactor_fuel_channel'],
     ("Chain Reaction", "連鎖反応", "链式反应"),
     ("One neutron leads to the next. Keep a hand near AZ-5.", "中性子が次の中性子を呼ぶ。AZ-5から手を離さないこと。", "一个中子引出下一个。手别离开AZ-5。")),
    ('uranium_fever', 'steel', f'{NS}:uranium_ore', 'task', [f'{NS}:uranium_ore'],
     ("Uranium Fever", "ウラン・フィーバー", "铀矿热"),
     ("A rock that glows in the dark. Handle with care.", "暗闇で光る石。取り扱いには注意を。", "会在黑暗中发光的石头。小心处理。")),
    ('eternal_flame', 'gas_safety', f'{NS}:flare_tip', 'task', [f'{NS}:flare_tip'],
     ("The Eternal Flame", "不夜城", "不夜城"),
     ("What cannot be used is burned, and the night stays bright.", "使えないものは燃やす。夜も明るいまま。", "用不上的就烧掉，夜晚依旧明亮。")),
    ('suit_up', 'gas_safety', f'{NS}:protective_coat', 'task', [f'{NS}:protective_coat', f'{NS}:protective_cap'],
     ("Suit Up", "完全装備", "全副武装"),
     ("Coat, cap and a mask: the look of someone who plans to go home.", "防護服、帽子、マスク。無事に帰る人の装い。", "防护服、帽子和面具：打算平安回家之人的装束。")),
    ('pressure_cooker', 'pumps', f'{NS}:pressure_tank', 'task', [f'{NS}:pressure_tank'],
     ("Pressure Cooker", "圧力鍋", "高压锅"),
     ("Everything gets faster under pressure. So do accidents.", "圧力をかければ何でも速くなる。事故も。", "压力之下一切都会加快。事故也是。")),
    ('first_breath', 'root', 'minecraft:potion', 'task', ['effect:create_refinery_n_reactors:toxic_gas'],
     ("Not That Breath", "その息、ちょっと待って", "这口气先别吸"),
     ("You inhaled something poisonous. A gas mask would have helped.", "毒のあるものを吸ってしまった。ガスマスクがあれば…。", "你吸入了有毒气体。要是有防毒面具就好了。")),
    ('sour_experience', 'sulfuric_acid', 'minecraft:potion', 'task', ['effect:create_refinery_n_reactors:acid_burn'],
     ("A Sour Experience", "酸っぱい経験", "酸涩的经历"),
     ("Acid burns. Now you know.", "酸は焼ける。身をもって知った。", "酸会灼伤。现在你知道了。")),
    ('seeing_stars', 'steam_tools', 'minecraft:potion', 'task', ['effect:create_refinery_n_reactors:dazed'],
     ("Seeing Stars", "目から火花", "眼冒金星"),
     ("Steam hits harder than it looks.", "蒸気は、見た目より強く殴る。", "蒸汽比看上去更有力。")),
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
    for old in folder.glob('*.json'): old.unlink()
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
        strings = {k: v for k, v in strings.items() if not k.startswith((f'book.{NS}.manual', f'advancement.{NS}.guide.'))}
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
