"""Regenerate launcher icons with white background. Run from project root."""
from PIL import Image
import os

SRC = "timer-512.png"
SCALE = 0.50
BG = (255, 255, 255, 255)  # white

SIZES = {
    "mipmap-mdpi":    {"legacy": 48,  "fg": 108},
    "mipmap-hdpi":    {"legacy": 72,  "fg": 162},
    "mipmap-xhdpi":   {"legacy": 96,  "fg": 216},
    "mipmap-xxhdpi":  {"legacy": 144, "fg": 324},
    "mipmap-xxxhdpi": {"legacy": 192, "fg": 432},
}

BASE = os.path.join("app", "src", "main", "res")

src = Image.open(SRC).convert("RGBA")

for folder, dims in SIZES.items():
    path = os.path.join(BASE, folder)
    os.makedirs(path, exist_ok=True)

    # Legacy icons (square with background)
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        size = dims["legacy"]
        canvas = Image.new("RGBA", (size, size), BG)
        icon_size = int(size * SCALE)
        icon = src.resize((icon_size, icon_size), Image.LANCZOS)
        offset = (size - icon_size) // 2
        canvas.paste(icon, (offset, offset), icon)
        canvas.convert("RGB").save(os.path.join(path, name))

    # Foreground layer (transparent, for adaptive icon)
    fg_size = dims["fg"]
    fg_canvas = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    icon_size = int(fg_size * SCALE)
    icon = src.resize((icon_size, icon_size), Image.LANCZOS)
    offset = (fg_size - icon_size) // 2
    fg_canvas.paste(icon, (offset, offset), icon)
    fg_canvas.save(os.path.join(path, "ic_launcher_foreground.png"))

    print(f"Done: {folder}")

print("All icons regenerated with white background.")
