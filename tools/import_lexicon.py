#!/usr/bin/env python3
"""Compile semantic lookup data. Shape decomposition is deliberately never imported."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import quote

SHAPE_ALIASES = {
    'F1090': {'角线', 'L'}, 'F0552': {'方框', '框'},
    'F0547': {'两横', '双横'}, 'F1143': {'上尖角', '上尖'},
    'F1154': {'下尖角', '下尖'},
}


def variants(text):
    yield text
    parts = re.split(r'[/／]', text)
    # Keep mathematical fractions and qualified expressions intact.
    if len(parts) > 1 and all(re.fullmatch(r'[A-Za-z\u3400-\u9fff ]+', p) for p in parts):
        yield from parts


def compile_lexicon(upstream, supplements, output):
    raw = (upstream / 'data/ime.json').read_bytes()
    ime = json.loads(raw)
    standard_raw = (upstream / 'data/standard.json').read_bytes()
    standard = json.loads(standard_raw)
    glyphs = {g['id']: g for g in standard['glyphs']}
    source = 'alien-input/v6.12.4'
    entries, terms, excluded = {}, set(), []

    def add_entry(id, label, sequence, origin):
        if id in entries or not label or not sequence or any(g not in glyphs for g in sequence):
            raise ValueError('Invalid or duplicate entry: ' + id)
        entries[id] = {'label': label, 'glyphs': sequence, 'note': '', 'source': origin}
        for word in variants(label):
            terms.add(('exact', word, id, origin))

    for id, g in glyphs.items():
        add_entry(id, g['label'], [id], source + '/data/standard.json#glyphs')
    for p in standard['wordLayer']['phrases']:
        add_entry(p['id'], p['label'], p['glyphs'], source + '/data/standard.json#wordLayer')
    for id, aliases in ime['structures']['search_aliases'].items():
        if id not in entries:
            raise ValueError('Unknown alias target: ' + id)
        for alias in aliases:
            if alias in SHAPE_ALIASES.get(id, set()):
                excluded.append({'id': id, 'alias': alias, 'reason': 'shape-only'})
                continue
            for word in variants(alias):
                terms.add(('alias', word, id, source + '/data/ime.json#search_aliases'))

    overlay_raw = supplements.read_bytes()
    overlay = json.loads(overlay_raw)
    if overlay['schema'] != 1:
        raise ValueError('Unsupported supplements schema')
    for patch in overlay['entries']:
        id = patch['id']
        if id not in entries:
            raise ValueError('Unknown supplement target: ' + id)
        if patch.get('note'):
            entries[id]['note'] = patch['note']
        for kind in ('alias', 'related'):
            for term in patch.get(kind, []):
                if not term.strip():
                    raise ValueError('Empty term')
                terms.add((kind, term, id, overlay['source']))
    output.mkdir(parents=True, exist_ok=True)
    lines = ['# alien-lexicon-v1']
    enc = lambda value: quote(str(value), safe='')
    for id, e in sorted(entries.items()):
        lines.append('\t'.join(map(enc, ['E', id, e['label'], ','.join(e['glyphs']), e['note'], e['source']])))
    for t in sorted(terms):
        lines.append('\t'.join(map(enc, ['T', *t])))
    compiled = ('\n'.join(lines) + '\n').encode()
    (output / 'lexicon.tsv').write_bytes(compiled)
    metadata = {
        'schema': 1, 'entries': len(entries), 'terms': len(terms),
        'source': source, 'source_sha256': hashlib.sha256(raw).hexdigest(),
        'standard_sha256': hashlib.sha256(standard_raw).hexdigest(),
        'supplements_sha256': hashlib.sha256(overlay_raw).hexdigest(),
        'lexicon_sha256': hashlib.sha256(compiled).hexdigest(),
        'excluded_shape_aliases': excluded,
    }
    (output / 'manifest.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
    print(f'Lexicon: {len(entries)} entries, {len(terms)} terms, {len(excluded)} shape aliases excluded')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('upstream', type=Path)
    parser.add_argument('--supplements', type=Path, default=Path(__file__).resolve().parents[1] / 'lexicon/supplements.json')
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'lexicon/generated')
    args = parser.parse_args()
    compile_lexicon(args.upstream, args.supplements, args.output)
