#!/usr/bin/env python3
"""Split Monogatari lockup into icon + wordmark assets.

Input:
  ./Monogatari-Logo.png

Outputs:
  android/src/main/res/drawable/monogatari_icon.png
  android/src/main/res/drawable/monogatari_wordmark.png
"""

from __future__ import annotations

from collections import deque
from pathlib import Path

from PIL import Image, ImageFilter

INPUT_CANDIDATES = (
    Path("Monogatari-Logo.png"),
    Path("MonogatariLogo.png"),
)
OUTPUT_ICON = Path("android/src/main/res/drawable/monogatari_icon.png")
OUTPUT_WORDMARK = Path("android/src/main/res/drawable/monogatari_wordmark.png")

# Fallbacks are (left, top, right, bottom), tuned for vertical lockup layout.
FALLBACK_ICON_BOX = (110, 40, 910, 700)
FALLBACK_WORDMARK_BOX = (60, 700, 960, 980)

BLACK_SOFT_START = 8
BLACK_SOFT_END = 54
BLUR_RADIUS = 0.8
ALPHA_MIN_COMPONENT = 24


def _soft_alpha_from_black(img: Image.Image) -> Image.Image:
    rgba = img.convert("RGBA")
    pixels = rgba.load()
    width, height = rgba.size

    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            dist = (r * r + g * g + b * b) ** 0.5
            if dist <= BLACK_SOFT_START:
                new_alpha = 0
            elif dist >= BLACK_SOFT_END:
                new_alpha = a
            else:
                t = (dist - BLACK_SOFT_START) / (BLACK_SOFT_END - BLACK_SOFT_START)
                new_alpha = int(a * t)
            pixels[x, y] = (r, g, b, new_alpha)

    alpha = rgba.split()[3].filter(ImageFilter.GaussianBlur(radius=BLUR_RADIUS))
    rgba.putalpha(alpha)
    return rgba


def _component_boxes(alpha_img: Image.Image, threshold: int = ALPHA_MIN_COMPONENT) -> list[tuple[int, int, int, int]]:
    width, height = alpha_img.size
    alpha = alpha_img.load()
    visited = [[False for _ in range(width)] for _ in range(height)]
    boxes: list[tuple[int, int, int, int]] = []

    for y in range(height):
        for x in range(width):
            if visited[y][x] or alpha[x, y] < threshold:
                continue

            q: deque[tuple[int, int]] = deque([(x, y)])
            visited[y][x] = True
            min_x = max_x = x
            min_y = max_y = y
            size = 0

            while q:
                cx, cy = q.popleft()
                size += 1
                min_x = min(min_x, cx)
                min_y = min(min_y, cy)
                max_x = max(max_x, cx)
                max_y = max(max_y, cy)

                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if 0 <= nx < width and 0 <= ny < height and not visited[ny][nx] and alpha[nx, ny] >= threshold:
                        visited[ny][nx] = True
                        q.append((nx, ny))

            if size > 500:
                boxes.append((min_x, min_y, max_x + 1, max_y + 1))

    return boxes


def _find_icon_and_wordmark_boxes(rgba: Image.Image) -> tuple[tuple[int, int, int, int], tuple[int, int, int, int], bool]:
    boxes = _component_boxes(rgba.split()[3])
    if len(boxes) < 2:
        return FALLBACK_ICON_BOX, FALLBACK_WORDMARK_BOX, False

    boxes = sorted(boxes, key=lambda b: (b[1], b[0]))
    upper = boxes[0]
    lower = boxes[-1]

    if lower[1] <= upper[1]:
        return FALLBACK_ICON_BOX, FALLBACK_WORDMARK_BOX, False

    return upper, lower, True


def _pad_box(box: tuple[int, int, int, int], width: int, height: int, pad: int = 10) -> tuple[int, int, int, int]:
    l, t, r, b = box
    return max(0, l - pad), max(0, t - pad), min(width, r + pad), min(height, b + pad)


def main() -> None:
    input_path = next(
        (candidate for candidate in INPUT_CANDIDATES if candidate.exists()),
        None,
    )

    if input_path is None:
        expected = ", ".join(str(path) for path in INPUT_CANDIDATES)
        raise SystemExit(f"Input not found. Expected one of: {expected}")

    rgba = _soft_alpha_from_black(Image.open(input_path))

    icon_box, wordmark_box, detected = _find_icon_and_wordmark_boxes(rgba)
    icon_box = _pad_box(icon_box, *rgba.size)
    wordmark_box = _pad_box(wordmark_box, *rgba.size)

    OUTPUT_ICON.parent.mkdir(parents=True, exist_ok=True)
    rgba.crop(icon_box).save(OUTPUT_ICON, format="PNG")
    rgba.crop(wordmark_box).save(OUTPUT_WORDMARK, format="PNG")

    mode = "auto-detect" if detected else "fallback-crops"
    print(f"Generated assets using {mode}:")
    print(f"  - input: {input_path}")
    print(f"  - {OUTPUT_ICON}")
    print(f"  - {OUTPUT_WORDMARK}")


if __name__ == "__main__":
    main()
