#!/usr/bin/env python3
"""Seasonal rate labels, missing fluid-name aliases and acquisition notes for world processes."""
import json
from pathlib import Path
A=Path(__file__).resolve().parents[1]/'src/main/resources/assets/create_refinery_n_reactors'
TEXT={
 'seasonal.create_refinery_n_reactors.heating':('Seasonal heating: %s%%','季節による加熱効率：%s%%','季节加热效率：%s%%'),
 'seasonal.create_refinery_n_reactors.cooling':('Seasonal cooling: %s%%','季節による冷却効率：%s%%','季节冷却效率：%s%%'),
 'jei.create_refinery_n_reactors.irradiation_products':('Reactor Irradiation and Removal','原子炉での使用・取り出し','反应堆使用与取出'),
 'jei.create_refinery_n_reactors.fusion_service':('Fusion Operation and Replacement','核融合炉の運転・交換','聚变堆运行与更换'),
 'jei.create_refinery_n_reactors.forging_byproducts':('Belt Forging Byproducts','ベルト鍛造の副産物','传送带锻造副产物'),
 'jei.create_refinery_n_reactors.note.spent_fuel':('Burn the fuel in an operating RBMK.\nRemove the spent rod from its channel.','RBMKで燃料を燃焼させる\n燃焼終了後、燃料チャンネルから回収','RBMK中燃烧燃料\n燃尽后从燃料通道取出'),
 'jei.create_refinery_n_reactors.note.spent_control':('Use the control rod in the reactor.\nWithdraw it after neutron exposure.','制御棒を炉内で使用\n中性子照射後、引き抜いて回収','在堆芯中使用控制棒\n中子照射后拔出回收'),
 'jei.create_refinery_n_reactors.note.irradiated_target':('Irradiate a lithium target in an RBMK.\nRemove the fully irradiated target.','RBMKでリチウム標的を照射\n照射完了後に取り出す','在RBMK中照射锂靶\n照射完成后取出'),
 'jei.create_refinery_n_reactors.note.irradiated_cartridge':('Install the cartridge in the blanket.\nRecover it after fusion irradiation.','ブランケットにカートリッジを装着\n核融合の照射後に回収','将卡匣装入包层\n聚变照射后回收'),
 'jei.create_refinery_n_reactors.note.spent_divertor':('Run the tungsten divertor in fusion.\nRemove it when worn.','ダイバータを核融合炉で使用\n摩耗したカセットを取り出す','在聚变堆中使用钨偏滤器\n取出磨损的卡匣'),
 'jei.create_refinery_n_reactors.note.forging_byproducts':('Press, reheat and fold three times.\nSteel always; slag 25% at completion.','プレス・再加熱・折り返しを3周\n鋼鉄は確定、スラグは完成時25%','压制、再加热、折叠三轮\n钢锭必得，完成时25%获得炉渣'),
}
ALIASES={
 'hydrogen_gas':('Hydrogen','水素','氢气'),'oxygen_gas':('Oxygen','酸素','氧气'),'nitrogen_gas':('Nitrogen','窒素','氮气'),'argon_gas':('Argon','アルゴン','氩气'),
 'converter_gas':('Converter Gas','転炉ガス','转炉煤气'),'air_gas':('Air','空気','空气'),'ammonia_gas':('Ammonia','アンモニア','氨气'),
 'nitric_acid':('Nitric Acid','硝酸','硝酸')}
CDG={'crude_oil':('Crude Oil','原油','原油'),'gasoline':('Gasoline','ガソリン','汽油'),'diesel':('Diesel','軽油','柴油'),'ethanol':('Ethanol','エタノール','乙醇'),'biodiesel':('Biodiesel','バイオディーゼル','生物柴油'),'plant_oil':('Plant Oil','植物油','植物油')}
COLOURS={'white':('White','白色','白色'),'orange':('Orange','橙色','橙色'),'magenta':('Magenta','赤紫色','品红色'),'light_blue':('Light Blue','空色','淡蓝色'),'yellow':('Yellow','黄色','黄色'),'lime':('Lime','黄緑色','黄绿色'),'pink':('Pink','桃色','粉红色'),'gray':('Gray','灰色','灰色'),'light_gray':('Light Gray','薄灰色','淡灰色'),'cyan':('Cyan','青緑色','青色'),'purple':('Purple','紫色','紫色'),'blue':('Blue','青色','蓝色'),'brown':('Brown','茶色','棕色'),'green':('Green','緑色','绿色'),'red':('Red','赤色','红色'),'black':('Black','黒色','黑色')}
for name,values in ALIASES.items():TEXT['fluid.create_refinery_n_reactors.'+name]=values
for name,values in CDG.items():TEXT['fluid.createdieselgenerators.'+name]=values
for name,values in COLOURS.items():TEXT['fluid.createdieselgenerators.'+name+'_cement']=(values[0]+' Liquid Concrete',values[1]+'の液状コンクリート',values[2]+'液态混凝土')
def generate():
 for i,lang in enumerate(('en_us','ja_jp','zh_cn')):
  p=A/f'lang/{lang}.json';d=json.loads(p.read_text());d.update({k:v[i]for k,v in TEXT.items()});p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
if __name__=='__main__':generate()
