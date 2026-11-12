# Attribution and license scope

The MIT license applies to the original application code, build scripts, tests,
and project documentation in this repository.

[Message From Aliens](https://store.steampowered.com/app/1858720/) is developed
and published by **Artless Games**. The game's glyph designs, in-game text,
title, and trademarks remain the property of their respective rights holders.
They are not relicensed under this repository's MIT license. The project does
not claim ownership of those materials or grant additional rights to them.

The included glyph bitmaps are cropped glyph forms used by this independent
writing tool. Research glosses, component input codes, and inferred grammar
are not official translations or an official keyboard specification. The
proper name **Artless** is retained.

This is a community tool, not an official product or endorsement by the game
developer. Raw game screenshots, game executables, private documents, and
research capture collections are not distributed here.

The optional clock uses a fixed nominal orbital-period parameter from the
NASA Exoplanet Archive and Agol et al., DOI
[10.3847/PSJ/abd022](https://doi.org/10.3847/PSJ/abd022). Its Unix origin is a
project convention, not a claim about the game's canonical calendar epoch.

## Trime-based development build

The Trime fork under `trime/` is GPL-3.0-or-later, pinned to v3.3.13 commit b5cad1a49095a3652e9188982de400ca3a653f37. The MIT scope above does not relicense Trime or its dependencies. Distribution of the combined derived APK must satisfy the GPL source obligations. New standalone semantic and Android adapter modules are MIT.

Native libraries and shared data are extracted from the matching official v3.3.13 arm64 APK, with SHA256 verification; no unrelated JNI build is substituted. Rime and its bundled dependencies retain their upstream notices. `vendor/rime-double-pinyin` is pinned to 01a13287cbd27819be1c34fa1ddc1b3643d5001b and retains its upstream license. See docs/implementation-notes.md for build provenance.

## rime-pure Clover nine-key integration

The complete nine-key layout, numeric/English/editing/symbol pages, Clover
schema, dictionary and language model are from SivanLaai/rime-pure at commit
93601c6774385e6830ed3e3120b4e63888efc983. Repository license: MIT (Copyright
2021 Sivan). The tongwenfeng_plus theme retains its own Rime community
GPL-3.0-or-later header. Original files, license, checksums and mechanical
normalization snapshot are in vendor/rime-pure. Compatibility modifications
are isolated in tools/prepare_rime_pure.py. The upstream personal custom_phrase
file is archived as provenance but is not installed into a user's dictionary.

Production glyphs use the complete alien-input v6.12.4 set with uniform alpha
and antialiasing processing. The three reviewed original game textures
(big / you / food) remain local research references and are not substituted
into this release. Full mapping to the game textures is still pending.
Glyph designs retain their original rights and are not covered by the code
licenses above. See docs/game-resource-analysis.md and research/game/glyph-map.json.
