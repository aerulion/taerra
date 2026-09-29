"""Model resolution tests covering the blockstate and geometry rules, without needing an asset tree."""

import unittest

from mcdata import models


class VariantSelectionTest(unittest.TestCase):

    def test_prefers_default_state(self):
        blockstate = {"variants": {"snowy=true": {"model": "a"}, "snowy=false": {"model": "b"}}}
        self.assertEqual(["b"], [p.model_ref for p in models.canonical_model_refs(blockstate)])

    def test_prefers_mature_crops(self):
        blockstate = {"variants": {f"age={age}": {"model": f"stage{age}"} for age in range(8)}}
        self.assertEqual(["stage7"], [p.model_ref for p in models.canonical_model_refs(blockstate)])

    def test_face_keyed_variants_are_unioned(self):
        # A mushroom block describes a cube as one model per face; any single variant is a flat sheet.
        blockstate = {"variants": {"north=true": {"model": "face"}, "up=true": {"model": "face", "x": 270}}}
        placements = models.canonical_model_refs(blockstate)
        self.assertEqual(2, len(placements))

    def test_multipart_unions_every_part(self):
        blockstate = {"multipart": [
            {"apply": {"model": "post"}, "when": {"up": "true"}},
            {"apply": {"model": "side"}, "when": {"north": "low"}},
        ]}
        self.assertEqual({"post", "side"}, {p.model_ref for p in models.canonical_model_refs(blockstate)})

    def test_takes_first_of_a_weighted_variant_list(self):
        blockstate = {"variants": {"": [{"model": "a"}, {"model": "b", "y": 90}]}}
        self.assertEqual(["a"], [p.model_ref for p in models.canonical_model_refs(blockstate)])


class RotationTest(unittest.TestCase):

    def test_y_rotation_moves_a_north_face_to_the_side(self):
        elements = [{"from": [0, 0, 0], "to": [16, 16, 0]}]
        rotated = models.rotate_elements(elements, 0, 90)
        self.assertEqual([16, 0, 0], rotated[0]["from"])
        self.assertEqual([16, 16, 16], rotated[0]["to"])

    def test_rotation_is_identity_without_angles(self):
        elements = [{"from": [0, 0, 0], "to": [16, 16, 16]}]
        self.assertIs(elements, models.rotate_elements(elements, 0, 0))

    def test_bounds_stay_ordered_after_rotation(self):
        rotated = models.rotate_elements([{"from": [0, 0, 0], "to": [4, 8, 16]}], 90, 180)
        for axis in range(3):
            self.assertLessEqual(rotated[0]["from"][axis], rotated[0]["to"][axis])


class TextureValueTest(unittest.TestCase):

    def test_plain_string_texture(self):
        self.assertEqual("block/stone", models._texture_value("block/stone"))

    def test_object_texture_uses_sprite(self):
        # Recent versions wrap some textures in an object, as glass does with force_translucent.
        self.assertEqual("block/glass", models._texture_value({"force_translucent": True, "sprite": "block/glass"}))

    def test_unknown_texture_shape_is_ignored(self):
        self.assertIsNone(models._texture_value(42))


if __name__ == "__main__":
    unittest.main()
