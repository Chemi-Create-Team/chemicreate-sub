#!/usr/bin/env python3
"""Names for the carrier, bounded goggles and the standard Display Link source."""
import json
from pathlib import Path
TEXT = {
 'fluid.create_refinery_n_reactors.chemical_mixture': ('Chemical Mixture', '混合液', '混合液'),
 'mixture.create_refinery_n_reactors.total': ('Mixture: %s mB', '混合液：%s mB', '混合液：%s mB'),
 'mixture.create_refinery_n_reactors.component': ('%s: %s mB (%s%%)', '%s：%s mB（%s%%）', '%s：%s mB（%s%%）'),
 'mixture.create_refinery_n_reactors.more': ('And %s more; use a Display Link', 'ほか%s成分：Display Linkで確認', '另有%s种成分，请用显示链接查看'),
 'create_refinery_n_reactors.display_source.mixture': ('Mixture composition', '混合液の成分', '混合液成分'),
}
def generate():
 root = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/lang'
 for i, locale in enumerate(('en_us', 'ja_jp', 'zh_cn')):
  p = root / (locale + '.json');data = json.loads(p.read_text());data.update({key: values[i] for key, values in TEXT.items()});p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
if __name__ == '__main__': generate()
