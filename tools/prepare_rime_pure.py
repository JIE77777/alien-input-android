#!/usr/bin/env python3
"""Build the pinned complete rime-pure theme with isolated Trime compatibility fixes."""
from pathlib import Path
import copy,hashlib,json,shutil,sys
root=Path(__file__).resolve().parents[1];vendor=root/'vendor/rime-pure';shared=root/'trime/app/src/main/assets/shared'
theme_only='--theme-only' in sys.argv
if not theme_only:
 provenance=json.loads((vendor/'upstream.json').read_text())
 for entry in provenance['files']:
  path=vendor/entry['file']
  if hashlib.sha256(path.read_bytes()).hexdigest()!=entry['sha256']:raise SystemExit('Changed upstream resource: '+path.name)
normalized=(vendor/'theme.normalized.json').read_bytes()
if not theme_only and hashlib.sha256(normalized).hexdigest()!=provenance['normalized_theme_sha256']:raise SystemExit('Changed normalized theme snapshot')
theme=json.loads(normalized)
# Helper mappings only defined YAML anchors; the snapshot has already resolved those.
sections={'config_version','name','author','style','preedit','window','fallback_colors','preset_color_schemes','liquid_keyboard','tool_bar','preset_keyboards','preset_keys'}
theme={k:v for k,v in theme.items() if k in sections}
theme['name']='同文风增强版 · 四叶草九宫格'
theme['config_version']='3.0'
# Preserve integer color literals after conversion from YAML to JSON.
for scheme in theme['preset_color_schemes'].values():
 for k,val in scheme.items():
  if isinstance(val,int) and not isinstance(val,bool):scheme[k]=f'0x{val:08x}'
# Inline colors also occur in toolbar backgrounds and individual key overrides.
# Decimal strings such as "0" are not valid ColorUtils colors on Android.
def normalize_inline_colors(value):
 if isinstance(value,dict):
  for key,child in list(value.items()):
   if isinstance(child,int) and not isinstance(child,bool) and ('color' in key or key in ('normal','highlight')):
    value[key]=f'0x{child:08x}'
   else:normalize_inline_colors(child)
 elif isinstance(value,list):
  for child in value:normalize_inline_colors(child)
normalize_inline_colors(theme)
keys=theme['preset_keys'];kb=theme['preset_keyboards']
# These four upstream actions are unused and refer to absent pages.
for unused in ('Keyboard_move','Keyboard_bqrw7','Keyboard_bqrw8','Keyboard_bqfh4'):keys.pop(unused,None)
for action in keys.values():
 if action.get('toggle') and action.get('send')=='Mode_switch':action['send']='SWITCH_CHARSET'
keys['liquid_keyboard_switch']={'label':'符号','send':'FUNCTION','command':'liquid_keyboard','option':'tabs'}
keys['liquid_keyboard_clipboard']={'label':'剪贴板','send':'FUNCTION','command':'clipboard_window','option':'0'}
keys['liquid_keyboard_exit']={'label':'返回','send':'FUNCTION','command':'liquid_keyboard','option':'-1'}
keys['K_jiugongLetter']['label']='En'
keys['K_jiugongDefault'].update(label='中文',select='.default')
kb['luna_jiugong'].update(ascii_keyboard='default_jiugong_letter',reset_ascii_mode=True)
kb['default_jiugong_letter']['reset_ascii_mode']=True
# The toolbar ASCII toggle uses .ascii in either direction; establish a proper return route.
kb['default_jiugong_letter']['ascii_keyboard']='luna_jiugong'
kb['letter']['ascii_keyboard']='default'
keys['Clover_switch_keyboard']={'label':'中/En','send':'Eisu_toggle','select':'.ascii'}
def toolbar_routes(value):
 if isinstance(value,dict):
  if value.get('action')=='Mode_switch':value['action']='Clover_switch_keyboard'
  for child in value.values():toolbar_routes(child)
 elif isinstance(value,list):
  for child in value:toolbar_routes(child)
toolbar_routes(theme['tool_bar'])
kb['alien_t9']={'__include':'/preset_keyboards/luna_jiugong'}
# Numeric key events otherwise override ABC labels while composing in Trime.
for key in kb['luna_jiugong']['keys']:
 n=str(key.get('click',''))
 if n in '23456789' and len(n)==1:
  name='Clover_digit_'+n;keys[name]={'label':key['label'],'send':n,'functional':False};key['click']=name
# The host decoder supports same-file includes but not list-index patch paths.
# Expand the upstream keyboard inheritance without changing the resulting key layout.
def expand_keyboard(name,trail=()):
 if name in trail:raise ValueError('Cyclic keyboard include')
 source=kb[name];inc=source.get('__include')
 base=expand_keyboard(inc.rsplit('/',1)[-1],trail+(name,)) if inc else {}
 for key,val in source.items():
  if key not in ('__include','__patch'):base[key]=copy.deepcopy(val)
 for path,val in source.get('__patch',{}).items():
  target=base;segments=path.split('/')
  for segment in segments[:-1]:target=target[int(segment[1:])] if segment.startswith('@') else target[segment]
  final=segments[-1];target[int(final[1:]) if final.startswith('@') else final]=copy.deepcopy(val)
 return base
theme['preset_keyboards']={name:expand_keyboard(name) for name in kb}
for name in ('trime.yaml','tongwenfeng.trime.yaml','tongwenfeng_plus.trime.yaml'):
 (shared/name).write_text('# Generated from pinned GPL-3.0-or-later rime-pure theme; see vendor/rime-pure.\n'+json.dumps(theme,ensure_ascii=False,indent=2)+'\n')
if theme_only:raise SystemExit(0)
for name in ('clover_jiugong.schema.yaml','clover.dict.yaml','clover.base.dict.yaml','huayu.dict.yaml','zh-hans-t-huayu-v7-bgw.gram'):
 shutil.copyfile(vendor/name,shared/name)
# A non-enabled __include-only schema is not deployed by Rime. Build a complete
# legacy schema and explicitly deploy it as Clover's compatibility dependency.
legacy=(vendor/'clover_jiugong.schema.yaml').read_text()
legacy=legacy.replace('schema_id: clover_jiugong','schema_id: alien_t9',1)
legacy=legacy.replace('name: 🍀️四叶草九宫格','name: 四叶草九宫格（兼容旧选择）',1)
legacy=legacy.replace('dependencies:\n    - emoji','dependencies: []',1)
(shared/'alien_t9.schema.yaml').write_text(legacy)
shutil.copyfile(vendor/'LICENSE',shared/'rime-pure-LICENSE.txt')
# Do not overwrite user-learned phrases with the upstream personal phrase list.
print('Prepared complete upstream Clover keyboard, dictionary and grammar')
