#!/usr/bin/env python3
"""One complete source set, one alpha curve, no partially mapped game overlays."""
from pathlib import Path
import argparse
import hashlib
import json
from PIL import Image, __version__ as pillow_version

ROOT = Path(__file__).resolve().parents[1]
BLACK, WHITE = 24, 160
LUT = [max(0, min(255, round((a - BLACK) * 255 / (WHITE - BLACK)))) for a in range(256)]

def prepare(output):
    manifest = json.loads((ROOT / 'research/game/glyph-map.json').read_text())
    sources = ROOT / 'app/src/main/assets/glyphs'
    expected = {f'F{i:04d}' for i in range(1, 1345)}
    assert {row['id'] for row in manifest['glyphs']} == expected
    output.mkdir(parents=True, exist_ok=True)
    records = []
    for row in manifest['glyphs']:
        source = sources / (row['id'] + '.png')
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        if digest != row['fallback_sha256']:
            raise ValueError('Original source changed; review before packaging: ' + row['id'])
        with Image.open(source) as original:
            assert original.size == (100, 100), row['id']
            alpha = original.convert('RGBA').getchannel('A')
        # Supersample the coverage curve so diagonals retain antialiasing at 100 px.
        normalized = alpha.resize((400, 400), Image.Resampling.BICUBIC).point(LUT).resize((100, 100), Image.Resampling.LANCZOS)
        # Same geometry; only sharpen coverage, never infer or redraw strokes.
        assert normalized.getbbox() is not None, row['id']
        assert normalized.getextrema()[1] >= 250, row['id']
        assert all(b >= 128 for a, b in zip(alpha.getdata(), normalized.getdata()) if a >= 128), row['id']
        rgba = Image.new('RGBA', (100, 100), (255, 255, 255, 0))
        rgba.putalpha(normalized)
        target = output / source.name
        rgba.save(target)
        records.append({'id': row['id'], 'source_sha256': digest,
                        'output_sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
    report = {'style': 'uniform-alpha-v1', 'source': 'alien-input v6.12.4, all 1344 glyphs',
              'pillow': pillow_version, 'alpha_levels': [BLACK, WHITE], 'supersampling': 4,
              'game_texture_overlay': False, 'glyphs': records}
    (output.parent / 'glyph-style.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(f'Prepared {len(records)} uniform glyphs; source hashes, coverage and nonempty masks verified')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    prepare(parser.parse_args().output)
