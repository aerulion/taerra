import unittest

import numpy as np

from mcdata import colormap


class ColormapTest(unittest.TestCase):

    def test_recovers_a_quadratic_surface(self):
        expected = {
            "red": [10.0, 20.0, 30.0, 40.0, 50.0, 60.0],
            "green": [200.0, -30.0, 5.0, 12.0, -8.0, 3.0],
            "blue": [90.0, 0.0, 0.0, 0.0, 0.0, 0.0],
        }
        rows, columns = np.mgrid[0:256, 0:256]
        design = colormap.terms(columns / 255, rows / 255)
        image = np.stack([design @ np.asarray(expected[c]) for c in colormap.CHANNELS], axis=-1)
        fitted = colormap.fit(image)
        for channel in colormap.CHANNELS:
            np.testing.assert_allclose(fitted[channel], expected[channel], atol=1e-4)

    def test_evaluates_back_to_the_image(self):
        rows, columns = np.mgrid[0:256, 0:256]
        image = np.stack([columns * 0.5 + 20, rows * 0.4 + 60, np.full((256, 256), 128.0)], axis=-1)
        rendered = colormap.evaluate(colormap.fit(image))
        sampled = rows >= columns
        self.assertLessEqual(np.abs(rendered[sampled].astype(int) - np.rint(image[sampled])).max(), 1)

    def test_terms_match_the_order_colormaps_java_uses(self):
        self.assertEqual(((0, 0), (0, 1), (0, 2), (1, 0), (1, 1), (2, 0)), colormap.TERMS)


if __name__ == "__main__":
    unittest.main()
