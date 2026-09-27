"""Merge the S2 machines-and-storage lang keys into en_us.json / zh_cn.json.

Chinese comes from reference/legacy-1.12/lang/zh_cn.json whenever the original has the string
(LEGACY), so it is never retyped through the Windows console. Strings the original does not have
are written as explicit \\u escapes, with every character verified to occur in the legacy file.
Run from the worktree root.
"""
import io
import json

REF = json.load(io.open('reference/legacy-1.12/lang/zh_cn.json', encoding='utf-8'))
REF_EN = json.load(io.open('reference/legacy-1.12/lang/en_us.json', encoding='utf-8'))


def legacy(key):
    return ('LEGACY', key)


# key -> (english, chinese or legacy(key))
KEYS = {
    # --- storage ---
    # 源质储罐: the legacy Chinese file translates essentia as 源质 throughout and has no
    # character for "reservoir", so the name is built from its own vocabulary.
    'block.technom.essentia_reservoir': ('Quantum Reservoir', '源质储罐'),
    'block.technom.creative_jar': (REF_EN['tile.techno:creativejar.name'], legacy('tile.techno:creativejar.name')),
    # --- essentia fusor ---
    'block.technom.essentia_fusor': (REF_EN['tile.techno:fusor.name'], legacy('tile.techno:fusor.name')),
    # 不能融合 / 清空后才能更改 / 输出：%s，每次 %s 能量
    'technom.fusor.refused': ('Those aspects do not combine', '不能融合'),
    'technom.fusor.not_empty': ('Empty this fusor before changing it', '清空后才能更改'),
    'technom.fusor.recipe': ('Output: %s (%s FE per fusion)', '输出：%s，每次 %s 能量'),
    'tc.research_name.technom:ESSENTIAFUSOR': (REF_EN['tc.research_name.essentiafusor'],
                                               legacy('tc.research_name.essentiafusor')),
    'tc.research_text.technom:ESSENTIAFUSOR': (REF_EN['tc.research_text.essentiafusor'],
                                               legacy('tc.research_text.essentiafusor')),
    'technom.research_page.ESSENTIAFUSOR.1': (REF_EN['techno.research_page.essentiafusor.1'],
                                              legacy('techno.research_page.essentiafusor.1')),
    'technom.research_page.ESSENTIAFUSOR.2': (REF_EN['techno.research_page.essentiafusor.2'],
                                              legacy('techno.research_page.essentiafusor.2')),
    # --- processing ---
    'block.technom.processor_tc': (REF_EN['tile.techno:processortc.name'], legacy('tile.techno:processortc.name')),
    'item.technom.pure_iron': (REF_EN['techno:pureiron.name'], legacy('techno:pureiron.name')),
    'item.technom.pure_gold': (REF_EN['techno:puregold.name'], legacy('techno:puregold.name')),
    'item.technom.pure_copper': (REF_EN['techno:purecopper.name'], legacy('techno:purecopper.name')),
    # 纯度 %s
    'technom.tooltip.purity': ('Purity: %s', '纯度 %s'),
    # 已加工
    'technom.tooltip.processed_by': ('Processed by', '已加工'),
    # "%s %s/%s 次"; the original printed the module's mod name untranslated, and so does this.
    'technom.tooltip.passes': ('%s %s/%s', '%s %s/%s 次'),
    'technom.processing.module.thaumcraft': ('Thaumcraft', 'Thaumcraft'),
    # 燃料 %s/%s
    'technom.processor.fuel': ('Fuel: %s/%s', '燃料 %s/%s'),
    'tc.research_name.technom:PROCESSOR': (REF_EN['tc.research_name.processor'], legacy('tc.research_name.processor')),
    'tc.research_text.technom:PROCESSOR': (REF_EN['tc.research_text.processor'], legacy('tc.research_text.processor')),
    'technom.research_page.PROCESSOR.1': (REF_EN['techno.research_page.processor.1'],
                                          legacy('techno.research_page.processor.1')),
}


def resolve(value):
    if isinstance(value, tuple) and value[0] == 'LEGACY':
        return REF[value[1]]
    return value


LEGACY_TEXT = ''.join(REF.values())
for english, chinese in KEYS.values():
    text = resolve(chinese)
    for char in text:
        if ord(char) > 0x2E80 and char not in LEGACY_TEXT:
            raise SystemExit('unverified CJK character %r in %r' % (char, text))

for lang, index in (('en_us', 0), ('zh_cn', 1)):
    path = 'src/main/resources/assets/technom/lang/%s.json' % lang
    data = json.load(io.open(path, encoding='utf-8'))
    for key, pair in KEYS.items():
        data[key] = resolve(pair[index])
    data = dict(sorted(data.items()))
    with io.open(path, 'w', encoding='utf-8', newline='\n') as out:
        json.dump(data, out, indent=2, ensure_ascii=False)
        out.write('\n')
    print(lang, len(data))
