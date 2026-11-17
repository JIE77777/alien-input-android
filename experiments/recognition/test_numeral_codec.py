import random
import unittest
from numeral_codec import encode_integer,integer_candidate,numeric_candidates

class NumeralCodecTests(unittest.TestCase):
    def test_known_production_sequences(self):
        self.assertEqual(encode_integer(50),['F1305','F1302'])
        self.assertEqual(encode_integer(123456),['N1P6','N2P4','F1314','F1341','F1318','F1324'])
        self.assertEqual(encode_integer(-123456),['F0098','N1P6','N2P4','F1314','F1341','F1318','F1324'])
        self.assertEqual(encode_integer(558545864083284007),['N1P21'])
    def test_integer_range_and_round_trip(self):
        rng=random.Random(20261119)
        for value in [0,1,-1,1234,-1234,123456,10**18-1,-10**18+1]+[rng.randrange(-10**18+1,10**18) for _ in range(1000)]:
            self.assertEqual(integer_candidate(encode_integer(value)),str(value))
        for value in [10**18,-10**18,'1.2','abc','']:
            with self.assertRaises(ValueError):encode_integer(value)
    def test_no_guessed_group_boundaries(self):
        self.assertEqual(numeric_candidates(['F1302','F1302']),[])
        candidate=numeric_candidates(['F0835','F1305','F1302','F1047'])[0]
        self.assertEqual((candidate['start'],candidate['end'],candidate['decimal']),(1,3,'50'))
        self.assertTrue(candidate['requires_group_confirmation'])
    def test_invalid_number_sequences(self):
        for seq in [[],['F1301','F1302'],['F1302','F1305'],['F0098','F1301'],['N6P21'],['F0098'],['N1P22']]:
            self.assertIsNone(integer_candidate(seq))

if __name__=='__main__':unittest.main()
