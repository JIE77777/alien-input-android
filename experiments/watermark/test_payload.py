import itertools
import unittest
from payload import pack, unpack


class PayloadTests(unittest.TestCase):
    def test_all_static_glyphs(self):
        for number in range(1, 1345):
            groups = [[f'F{number:04d}']]
            self.assertEqual(unpack(pack(groups)), groups)

    def test_counts_and_every_boundary(self):
        for count in range(1, 5):
            for breaks in itertools.product([False, True], repeat=count - 1):
                groups, group = [], []
                for i in range(count):
                    group.append(f'F{i+1:04d}')
                    if i == count - 1 or breaks[i]:
                        groups.append(group)
                        group = []
                self.assertEqual(unpack(pack(groups)), groups)

    def test_every_single_bit_error_rejected(self):
        bits = pack([['F0902'], ['F0834'], ['F1305', 'F1302']])
        for i in range(len(bits)):
            changed = bits[:i] + str(1 - int(bits[i])) + bits[i+1:]
            with self.assertRaises(ValueError):
                unpack(changed)

    def test_reject_overflow_unknown_and_empty(self):
        for groups in [[], [[]], [['F0000']], [['F1345']], [['N1P4']], [['F0001']]*5,
                       [['F0001'], []], [['F1']], [['你好']]]:
            with self.assertRaises(ValueError):
                pack(groups)

    def test_foreign_or_incomplete_payload(self):
        for bits in ['', '0'*75, '1'*75, 'x'*75, pack([['F0001']])[:-1]]:
            with self.assertRaises(ValueError):
                unpack(bits)


if __name__ == '__main__':
    unittest.main()
