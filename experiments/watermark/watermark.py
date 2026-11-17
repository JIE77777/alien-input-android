"""TrustMark proof of concept; decode reads pixels only, never PNG metadata."""
import argparse
import hashlib
import json
import importlib
import os
import time
from pathlib import Path

from PIL import Image
from payload import code, pack, unpack


def model(variant='Q', device='cpu'):
    import torch
    from trustmark import TrustMark
    # Upstream resolves weights beside its package. Keep model storage outside
    # the repository and verify its published MD5 values before loading.
    cache = os.environ.get('ALIEN_WATERMARK_MODELS')
    if cache:
        module = importlib.import_module('trustmark.trustmark')
        target_dir = Path(module.__file__).parent / 'models'
        target_dir.mkdir(exist_ok=True)
        for name in [f'trustmark_{variant}.yaml', f'encoder_{variant}.ckpt', f'decoder_{variant}.ckpt']:
            source = Path(cache).resolve() / name
            if not source.exists() or hashlib.md5(source.read_bytes()).hexdigest() != module.MODEL_CHECKSUMS[name]:
                raise ValueError('Missing or mismatched official model: ' + str(source))
            target = target_dir / name
            if not target.exists():
                target.symlink_to(source)
            elif hashlib.md5(target.read_bytes()).hexdigest() != module.MODEL_CHECKSUMS[name]:
                raise ValueError('Existing model differs; not overwriting: ' + str(target))
    torch.set_num_threads(4)
    torch.manual_seed(20261117)
    # Avoid loading watermark removal or region-detection models.
    return TrustMark(model_type=variant, device=device, verbose=False,
                     encoding_type=TrustMark.Encoding.BCH_3,
                     loadRemover=False, loadBBoxDetector=False)


def load_rgb(path):
    with Image.open(path) as image:
        if image.width * image.height > 16_000_000:
            raise ValueError('Image exceeds prototype size limit')
        if image.mode in ('RGBA', 'LA') or 'transparency' in image.info:
            rgba = image.convert('RGBA')
            image = Image.alpha_composite(Image.new('RGBA', rgba.size, 'black'), rgba)
        rgb = image.convert('RGB')
        rgb.info.clear()
        return rgb.copy()


def encode(tm, image, groups, strength=1.0):
    if not 0 < strength <= 3:
        raise ValueError('Strength must be in (0, 3]')
    marked = tm.encode(image, pack(groups), MODE='binary', WM_STRENGTH=strength)
    marked.info.clear()
    return marked


def decode(tm, image):
    bits, detected, schema = tm.decode(image, MODE='binary')
    if not detected:
        return {'status': 'unreadable', 'reason': 'No ECC-valid watermark'}
    if int(schema) != 3:
        return {'status': 'unreadable', 'reason': 'Unexpected ECC schema'}
    try:
        groups = unpack(bits)
    except ValueError as error:
        return {'status': 'unreadable', 'reason': str(error)}
    return {'status': 'decoded', 'groups': groups, 'code': code(groups)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['encode', 'decode'])
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--groups', help='Example: F0902;F0834;F1305,F1302')
    parser.add_argument('--variant', choices=['Q', 'P'], default='Q')
    parser.add_argument('--device', default='cpu')
    parser.add_argument('--strength', type=float, default=1.0)
    args = parser.parse_args()
    groups = None
    if args.action == 'encode':
        if not args.groups or args.output is None:
            parser.error('encode requires --groups and --output')
        if args.input.resolve() == args.output.resolve():
            parser.error('Use a different output path')
        groups = [group.split(',') for group in args.groups.split(';')]
        pack(groups)
    image = load_rgb(args.input)
    tm = model(args.variant, args.device)
    started = time.perf_counter()
    if args.action == 'encode':
        marked = encode(tm, image, groups, args.strength)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        marked.save(args.output, format='PNG')
        result = decode(tm, load_rgb(args.output))
        if result.get('groups') != groups:
            result = {'status': 'verification-failed', 'decode': result}
        result['output'] = str(args.output)
        result['sha256'] = hashlib.sha256(args.output.read_bytes()).hexdigest()
    else:
        result = decode(tm, image)
    result.update(variant=args.variant, seconds=round(time.perf_counter() - started, 3))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result['status'] == 'decoded' else 2)


if __name__ == '__main__':
    main()
