# -*- coding: utf-8 -*-
import os
from PIL import Image, ImageDraw, ImageFont

W = 512
img = Image.new("RGB", (W, W), (194, 114, 66))
d = ImageDraw.Draw(img)
for y in range(W):
    t = y / (W - 1)
    r = int(194 + (62 - 194) * t)
    g = int(114 + (81 - 114) * t)
    b = int(66 + (73 - 66) * t)
    d.line([(0, y), (W, y)], fill=(r, g, b))

d.rounded_rectangle([26, 26, W - 26, W - 26], radius=64, outline=(241, 234, 219), width=10)

font = None
for path in [r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf", r"C:\Windows\Fonts\simsun.ttc"]:
    if os.path.exists(path):
        try:
            font = ImageFont.truetype(path, 300)
            break
        except Exception:
            pass
if font is not None:
    d.text((W // 2, W // 2 - 10), "米", font=font, fill=(241, 234, 219), anchor="mm")

out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "res", "drawable", "app_logo.png")
os.makedirs(os.path.dirname(out), exist_ok=True)
img.save(out, "PNG")
print("logo", out, img.size)