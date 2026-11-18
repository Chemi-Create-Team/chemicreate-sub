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
            advancement['criteria'] = {item.split(':')[1]: {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': item}]}}
                                       for item in items}
            advancement['requirements'] = [[item.split(':')[1] for item in items]]
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
