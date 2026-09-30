from __future__ import annotations

import numpy as np

DEFAULT_BANDWIDTH = 0.055
DEFAULT_MAX_SEEDS = 96
DEFAULT_MAX_ITERATIONS = 30
DEFAULT_CONVERGENCE_THRESHOLD = 1e-4
DEFAULT_MAX_PIXELS = 4096


def srgb_to_linear(rgb: np.ndarray) -> np.ndarray:
    return np.where(rgb <= 0.04045, rgb / 12.92, ((rgb + 0.055) / 1.055) ** 2.4)


def linear_to_srgb(rgb: np.ndarray) -> np.ndarray:
    return np.where(rgb <= 0.0031308, rgb * 12.92, 1.055 * np.power(rgb, 1.0 / 2.4) - 0.055)


def linear_rgb_to_oklab(linear_rgb: np.ndarray) -> np.ndarray:
    l = 0.4122214708 * linear_rgb[:, 0] + 0.5363325363 * linear_rgb[:, 1] + 0.0514459929 * linear_rgb[:, 2]
    m = 0.2119034982 * linear_rgb[:, 0] + 0.6806995451 * linear_rgb[:, 1] + 0.1073969566 * linear_rgb[:, 2]
    s = 0.0883024619 * linear_rgb[:, 0] + 0.2817188376 * linear_rgb[:, 1] + 0.6299787005 * linear_rgb[:, 2]

    l_root = np.cbrt(np.clip(l, 0.0, None))
    m_root = np.cbrt(np.clip(m, 0.0, None))
    s_root = np.cbrt(np.clip(s, 0.0, None))

    return np.column_stack(
        (
            0.2104542553 * l_root + 0.7936177850 * m_root - 0.0040720468 * s_root,
            1.9779984951 * l_root - 2.4285922050 * m_root + 0.4505937099 * s_root,
            0.0259040371 * l_root + 0.7827717662 * m_root - 0.8086757660 * s_root,
        )
    )


def oklab_to_linear_rgb(oklab: np.ndarray) -> np.ndarray:
    l_ = oklab[0] + 0.3963377774 * oklab[1] + 0.2158037573 * oklab[2]
    m_ = oklab[0] - 0.1055613458 * oklab[1] - 0.0638541728 * oklab[2]
    s_ = oklab[0] - 0.0894841775 * oklab[1] - 1.2914855480 * oklab[2]
    return np.array(
        [
            4.0767416621 * l_**3 - 3.3077115913 * m_**3 + 0.2309699292 * s_**3,
            -1.2684380046 * l_**3 + 2.6097574011 * m_**3 - 0.3413193965 * s_**3,
            -0.0041960863 * l_**3 - 0.7034186147 * m_**3 + 1.7076147010 * s_**3,
        ],
        dtype=np.float64,
    )


def rgb_to_hex(linear_rgb: np.ndarray) -> str:
    srgb = np.clip(linear_to_srgb(np.clip(linear_rgb, 0.0, 1.0)), 0.0, 1.0)
    ints = np.rint(srgb * 255.0).astype(np.uint8)
    return f"#{ints[0]:02X}{ints[1]:02X}{ints[2]:02X}"


def _deterministic_subsample(
    points: np.ndarray, weights: np.ndarray, max_points: int
) -> tuple[np.ndarray, np.ndarray]:
    if len(points) <= max_points:
        return points, weights
    indices = np.linspace(0, len(points) - 1, num=max_points, dtype=np.int64)
    return points[indices], weights[indices]


def _mean_shift_centers(
    points: np.ndarray,
    weights: np.ndarray,
    bandwidth: float,
    max_seeds: int,
    max_iterations: int,
    convergence_threshold: float,
) -> np.ndarray:
    seed_count = min(len(points), max_seeds)
    seed_indices = np.linspace(0, len(points) - 1, num=seed_count, dtype=np.int64)

    found_centers: list[np.ndarray] = []
    center_support: list[float] = []
    for seed_index in seed_indices:
        center = points[seed_index].astype(np.float64, copy=True)
        support = 0.0
        for _ in range(max_iterations):
            distances = np.linalg.norm(points - center, axis=1)
            neighborhood = distances <= bandwidth
            if not np.any(neighborhood):
                neighborhood[int(np.argmin(distances))] = True
            local_weights = weights[neighborhood] * np.exp(-0.5 * np.square(distances[neighborhood] / bandwidth))
            support = float(np.sum(local_weights))
            shifted = np.average(points[neighborhood], axis=0, weights=local_weights)
            shift_distance = float(np.linalg.norm(shifted - center))
            center = shifted
            if shift_distance <= convergence_threshold:
                break
        found_centers.append(center)
        center_support.append(support)

    merged_centers: list[np.ndarray] = []
    merged_support: list[float] = []
    merge_distance = bandwidth * 0.5
    for center, support in sorted(zip(found_centers, center_support), key=lambda item: item[1], reverse=True):
        for index, existing in enumerate(merged_centers):
            if np.linalg.norm(center - existing) <= merge_distance:
                total = merged_support[index] + support
                merged_centers[index] = (existing * merged_support[index] + center * support) / total
                merged_support[index] = total
                break
        else:
            merged_centers.append(center.copy())
            merged_support.append(support)

    return np.vstack(merged_centers)


def dominant_color_hex(
    rgba: np.ndarray,
    alpha_threshold: int = 16,
    bandwidth: float = DEFAULT_BANDWIDTH,
    max_pixels: int = DEFAULT_MAX_PIXELS,
    max_seeds: int = DEFAULT_MAX_SEEDS,
    max_iterations: int = DEFAULT_MAX_ITERATIONS,
) -> str | None:
    flat = rgba.reshape(-1, 4)
    alpha = flat[:, 3]
    mask = alpha >= alpha_threshold
    if not np.any(mask):
        return None

    srgb = flat[mask, :3].astype(np.float32) / 255.0
    weights = alpha[mask].astype(np.float32) / 255.0
    srgb, weights = _deterministic_subsample(srgb, weights, max_pixels)

    points = linear_rgb_to_oklab(srgb_to_linear(srgb))
    centers = _mean_shift_centers(
        points, weights, bandwidth, max_seeds, max_iterations, DEFAULT_CONVERGENCE_THRESHOLD
    )
    if len(centers) == 1:
        dominant = centers[0]
    else:
        distances = np.linalg.norm(points[:, None, :] - centers[None, :, :], axis=2)
        cluster_weights = np.bincount(np.argmin(distances, axis=1), weights=weights, minlength=len(centers))
        dominant = centers[int(np.argmax(cluster_weights))]
    return rgb_to_hex(oklab_to_linear_rgb(dominant))
