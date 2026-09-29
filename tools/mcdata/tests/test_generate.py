"""Tests for the parts of generation that do not need an asset tree."""

import unittest

from mcdata import generate, models


class ConstantTintTest(unittest.TestCase):

    def test_multiplies_each_channel(self):
        self.assertEqual("#4B0000", generate.apply_tint("#FEFEFE", (0x4C, 0x00, 0x00)))
        self.assertEqual("#FFFFFF", generate.apply_tint("#FFFFFF", (0xFF, 0xFF, 0xFF)))
        self.assertEqual("#000000", generate.apply_tint("#FFFFFF", (0x00, 0x00, 0x00)))

    def test_accepts_a_color_without_its_hash(self):
        self.assertEqual("#804000", generate.apply_tint("FF8000", (0x80, 0x80, 0x00)))

    def test_redstone_wire_is_tinted_rather_than_left_white(self):
        # Its texture is grayscale and the game tints it by power level, which a map has no notion of. Without the
        # baked tint a redstone line renders white.
        tint = models.CONSTANT_TINTS["minecraft:redstone_wire"]
        self.assertEqual("#4B0000", generate.apply_tint("#FEFEFE", tint))


if __name__ == "__main__":
    unittest.main()
