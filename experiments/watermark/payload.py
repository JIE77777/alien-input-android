"""Experimental offline payload: 1–4 static v6.12.4 glyphs in 75 bits."""
import binascii
import re

MAGIC = '10110110'
VERSION = '01'
BIT_COUNT = 75


def checksum(body):
    # The body is 59 bits; zero padding at the right is part of this protocol.
    data = int(body + '00000', 2).to_bytes(8, 'big')
    return format(binascii.crc_hqx(data, 0xffff), '016b')


def pack(groups):
    if not groups or any(not group for group in groups):
        raise ValueError('Expected nonempty glyph groups')
    glyphs = [glyph for group in groups for glyph in group]
    if not 1 <= len(glyphs) <= 4:
        raise ValueError('Prototype supports 1–4 static glyphs; no truncation')
    ids = []
    for glyph in glyphs:
        if not isinstance(glyph, str) or not re.fullmatch(r'F\d{4}', glyph):
            raise ValueError('Only static F0001–F1344 glyphs are supported')
        number = int(glyph[1:])
        if not 1 <= number <= 1344:
            raise ValueError('Unknown static glyph: ' + glyph)
        ids.append(number)
    breaks = [0, 0, 0]
    end = 0
    for group in groups[:-1]:
        end += len(group)
        breaks[end - 1] = 1
    body = MAGIC + VERSION + format(len(ids) - 1, '02b')
    body += ''.join(format(number, '011b') for number in ids + [0] * (4 - len(ids)))
    body += ''.join(map(str, breaks))
    assert len(body) == 59
    return body + checksum(body)


def unpack(bits):
    if not isinstance(bits, str) or len(bits) != BIT_COUNT or set(bits) - {'0', '1'}:
        raise ValueError('Wrong payload length or alphabet')
    if bits[:8] != MAGIC or bits[8:10] != VERSION:
        raise ValueError('Not a supported alien watermark')
    if checksum(bits[:59]) != bits[59:]:
        raise ValueError('Payload checksum failed')
    count = int(bits[10:12], 2) + 1
    numbers = [int(bits[12 + i * 11:23 + i * 11], 2) for i in range(4)]
    breaks = bits[56:59]
    if any(not 1 <= number <= 1344 for number in numbers[:count]):
        raise ValueError('Invalid glyph identity')
    if any(numbers[count:]) or '1' in breaks[max(0, count - 1):]:
        raise ValueError('Noncanonical padding')
    groups, group = [], []
    for i, number in enumerate(numbers[:count]):
        group.append(f'F{number:04d}')
        if i == count - 1 or breaks[i] == '1':
            groups.append(group)
            group = []
    return groups


def code(groups):
    return 'ALIEN1|v6.12.4|' + ';'.join(','.join(group) for group in groups)
