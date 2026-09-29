"""Shape classification tests.

These exercise the geometry rules directly and need no asset tree, so they run anywhere. The cases are the ones
that actually broke during development: raw volume alone cannot separate a crop from a hopper, and several solid
blocks are authored as hollow shells.
"""

import unittest

from mcdata import shape


def box(x1, y1, z1, x2, y2, z2):
    return {"from": [x1, y1, z1], "to": [x2, y2, z2]}


FULL_CUBE = [box(0, 0, 0, 16, 16, 16)]
SLAB = [box(0, 0, 0, 16, 8, 16)]
CROSS = [box(0.8, 0, 8, 15.2, 16, 8), box(8, 0, 0.8, 8, 16, 15.2)]
SHELL = [
    box(0, 15.998, 0, 16, 16, 16), box(0, 0, 0, 16, 0.002, 16),
    box(0, 0, 0, 16, 16, 0.002), box(0, 0, 15.998, 16, 16, 16),
    box(0, 0, 0, 0.002, 16, 16), box(15.998, 0, 0, 16, 16, 16),
]


class OccupancyTest(unittest.TestCase):

    def test_full_cube_fills_the_cell(self):
        self.assertEqual(1.0, shape.occupied_fraction(FULL_CUBE))

    def test_slab_fills_half_the_cell(self):
        self.assertEqual(0.5, shape.occupied_fraction(SLAB))

    def test_crossed_sheets_occupy_no_solid_volume(self):
        # Crops and flowers are sheets. Counting them by raw volume puts mature wheat above a hopper.
        self.assertEqual(0.0, shape.occupied_fraction(CROSS))

    def test_crossed_sheets_are_still_visible(self):
        _, visible = shape.measure(CROSS)
        self.assertTrue(visible)

    def test_hollow_shell_counts_as_solid(self):
        # Powder snow is a 0.002 thick box; only the sealed interior makes it read as a full block.
        self.assertEqual(1.0, shape.occupied_fraction(SHELL))

    def test_empty_model_is_not_visible(self):
        self.assertEqual((0.0, False), shape.measure([]))


class ClassifyTest(unittest.TestCase):

    def test_cube_is_full(self):
        self.assertEqual("full", shape.classify("minecraft:stone", FULL_CUBE)[0])

    def test_sheet_is_thin(self):
        self.assertEqual("thin", shape.classify("minecraft:wheat", CROSS)[0])

    def test_shell_is_full(self):
        self.assertEqual("full", shape.classify("minecraft:powder_snow", SHELL)[0])

    def test_fluids_are_full_despite_having_no_model(self):
        self.assertEqual("full", shape.classify("minecraft:water", [])[0])

    def test_block_entities_are_full_despite_having_no_model(self):
        self.assertEqual("full", shape.classify("minecraft:black_shulker_box", [])[0])

    def test_invisible_blocks_stay_none(self):
        self.assertEqual("none", shape.classify("minecraft:barrier", FULL_CUBE)[0])

    def test_fences_and_gates_are_full_despite_measuring_below_the_threshold(self):
        post = [box(6, 0, 6, 10, 16, 10)]
        self.assertLess(shape.occupied_fraction(post), shape.DEFAULT_FULL_THRESHOLD)
        self.assertEqual("full", shape.classify("minecraft:oak_fence", post)[0])
        self.assertEqual("full", shape.classify("minecraft:oak_fence_gate", post)[0])

    def test_doors_are_full_despite_measuring_below_the_threshold(self):
        door = [box(0, 0, 0, 3, 16, 16)]
        self.assertLess(shape.occupied_fraction(door), shape.DEFAULT_FULL_THRESHOLD)
        self.assertEqual("full", shape.classify("minecraft:oak_door", door)[0])


if __name__ == "__main__":
    unittest.main()
