"""Makes the upgraded leads' item textures from vanilla's lead texture.

    python tools/gen_lead_textures.py <path to a 1.20.1 client jar or client-extra jar>

Vanilla's lead.png has eight colours: four browns for the rope (darkest to lightest), three greys for the clasp and a
light tip. Each tier recolours them pixel for pixel: the rope in the tier's material (iron, diamond, netherite, from the
ingot textures' own palettes) and the clasp in the same material, lighter. Same shape, so the upgraded leads read as
leads at a glance. Writes src/main/resources/assets/unleashedlead/textures/item/<tier>_lead.png.
"""
import io
import os
import sys
import zipfile

from PIL import Image

# vanilla lead.png colours -> role
ROPE = [(94, 47, 16), (133, 70, 27), (166, 103, 61), (174, 115, 75)]  # darkest to lightest
CLASP = [(114, 96, 84), (166, 155, 147), (181, 168, 159)]  # dark, mid, light
TIP = (139, 112, 95)

TIERS = {
    "iron_lead": {
        "rope": [(88, 88, 88), (130, 130, 130), (168, 168, 168), (200, 202, 205)],
        "clasp": [(94, 94, 94), (168, 168, 168), (216, 216, 216)],
        "tip": (150, 150, 150),
    },
    "diamond_lead": {
        "rope": [(17, 114, 122), (26, 170, 167), (32, 197, 181), (74, 237, 217)],
        "clasp": [(20, 94, 83), (74, 237, 217), (161, 251, 232)],
        "tip": (44, 224, 216),
    },
    "netherite_lead": {
        "rope": [(39, 28, 29), (60, 50, 50), (77, 65, 67), (90, 87, 90)],
        "clasp": [(49, 41, 42), (90, 87, 90), (115, 113, 115)],
        "tip": (76, 65, 67),
    },
}


def main():
    jar = sys.argv[1]
    with zipfile.ZipFile(jar) as z:
        lead = Image.open(io.BytesIO(z.read("assets/minecraft/textures/item/lead.png"))).convert("RGBA")
    out_dir = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "unleashedlead",
                           "textures", "item")
    os.makedirs(out_dir, exist_ok=True)
    for name, tier in TIERS.items():
        mapping = {}
        mapping.update(zip(ROPE, tier["rope"]))
        mapping.update(zip(CLASP, tier["clasp"]))
        mapping[TIP] = tier["tip"]
        img = Image.new("RGBA", lead.size)
        for y in range(lead.size[1]):
            for x in range(lead.size[0]):
                r, g, b, a = lead.getpixel((x, y))
                if a == 0:
                    continue
                if (r, g, b) not in mapping:
                    raise SystemExit("lead.png has a colour this script doesn't know: %s" % ((r, g, b),))
                img.putpixel((x, y), mapping[(r, g, b)] + (a,))
        img.save(os.path.join(out_dir, name + ".png"))
        print("wrote", name)


if __name__ == "__main__":
    main()
