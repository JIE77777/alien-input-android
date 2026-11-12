# rime-pure integration

Upstream: https://github.com/SivanLaai/rime-pure
Pinned commit: 93601c6774385e6830ed3e3120b4e63888efc983.

The complete tongwenfeng_plus theme, Clover nine-key scheme and dictionaries
are upstream resources, not a newly designed keyboard. Keep LICENSE plus the
GPL-3.0-or-later header of the theme. upstream.json records downloaded hashes.

`theme.normalized.json` is a mechanical `Psych.load_file` → JSON snapshot of
the upstream theme. This resolves YAML anchors and repeated keys with last-key
wins, matching conventional loading, before the stricter Trime parser reads it.
Regenerate with Ruby Psych 3.1.0 and review changes when changing the pin.
`tools/prepare_rime_pure.py` contains the small compatibility patch:
modern liquid-keyboard/clipboard commands, corrected language-switch labels,
explicit ASCII return behavior, legacy alien_t9 alias and nine-key key labels.
No local redesign of the upstream key layout.

Optional emoji dictionary dependency is disabled: the complete symbol/emoji
panel is still supplied by the theme. Existing full pinyin and flypy remain.

The compatibility generator also expands upstream list-index patches (unsupported
by the host source-theme decoder), keeps config_version at 3.0, removes four
unreferenced actions targeting absent pages, and routes the toolbar language
button through the upstream Chinese/English keyboard pair.

Device validation additionally required explicit deployment of the legacy
alien_t9 schema and preserving inline hexadecimal colors (including transparent
zero) in toolbar/key overrides. Both are covered by the integration checks.
