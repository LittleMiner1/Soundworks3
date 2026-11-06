#!/usr/bin/env python3
"""Generates textures, models, blockstates and item definitions for the new Soundworks blocks."""
import json, os, random, math
from PIL import Image, ImageDraw

ROOT = '/home/claude/Soundworks-main/src/main/resources/assets/soundworks/'
TEX = ROOT + 'textures/block/'
MOD = ROOT + 'models/block/'
BST = ROOT + 'blockstates/'
ITM = ROOT + 'items/'
for d in (TEX, MOD, BST, ITM):
    os.makedirs(d, exist_ok=True)

# ---- palette (taken from the turntable's wood strip and the existing steel/black parts) ----
W_DK, W_D, W_M, W_L, W_H = (52, 28, 16), (80, 46, 26), (98, 58, 32), (140, 92, 58), (176, 124, 82)
S_DK, S_D, S_M, S_L, S_L2, S_H = (36, 38, 45), (56, 58, 61), (72, 77, 86), (107, 111, 116), (124, 130, 135), (174, 181, 189)
BLK, BLK2 = (14, 14, 18), (20, 21, 26)
BRASS_D, BRASS, BRASS_L = (150, 82, 14), (240, 190, 20), (250, 188, 78)
CU_D, CU, CU_L = (120, 60, 34), (184, 100, 60), (224, 138, 84)

def h(x, y, s=0):
    v = (x * 73856093) ^ (y * 19349663) ^ (s * 83492791)
    v = (v ^ (v >> 13)) * 1274126177
    return (v >> 8) & 0xFF

def sh(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)

def new(w, hgt):
    return Image.new('RGBA', (w, hgt), (0, 0, 0, 0))

def rect(im, x, y, w, hh, c):
    ImageDraw.Draw(im).rectangle([x, y, x + w - 1, y + hh - 1], fill=c + (255,) if len(c) == 3 else c)

def bevel(im, x, y, w, hh, light, dark, edge=None):
    d = ImageDraw.Draw(im)
    if edge:
        d.rectangle([x, y, x + w - 1, y + hh - 1], outline=edge + (255,))
        x, y, w, hh = x + 1, y + 1, w - 2, hh - 2
    d.line([x, y, x + w - 1, y], fill=light + (255,))
    d.line([x, y, x, y + hh - 1], fill=light + (255,))
    d.line([x, y + hh - 1, x + w - 1, y + hh - 1], fill=dark + (255,))
    d.line([x + w - 1, y, x + w - 1, y + hh - 1], fill=dark + (255,))

def save(im, name):
    im.save(TEX + name + '.png')

# ---------------- tiles (64x64, 4 px per model unit) ----------------
def wood_tile(vertical=False, dark=False):
    im = new(64, 64)
    px = im.load()
    seed = 7 if not dark else 11
    for p in range(4):
        base = sh(W_M, (h(p, 3, seed) % 15) - 7 - (14 if dark else 0))
        for y in range(16):
            for x in range(64):
                g = h(x // 7 + p * 5, y + p * 16, seed) % 12 - 6
                if (y + p * 3) % 5 == 0:
                    g -= 7
                c = sh(base, g)
                if y == 0: c = sh(W_D, -(10 if dark else 0))
                elif y == 1: c = sh(W_L, -(14 if dark else 0))
                elif y == 15: c = sh(W_D, -(8 if dark else 0))
                px[x, p * 16 + y] = c + (255,)
        jx = (13 + p * 19) % 64
        for y in range(1, 16):
            px[jx, p * 16 + y] = sh(W_DK, 0) + (255,)
            px[(jx + 1) % 64, p * 16 + y] = sh(W_L, -6) + (255,)
    return im

def steel_tile(base=S_M):
    im = new(64, 64)
    px = im.load()
    col = [h(x, 0, 5) % 17 - 8 for x in range(64)]
    for y in range(64):
        for x in range(64):
            n = h(x, y, 9) % 7 - 3
            px[x, y] = sh(base, col[x] + n) + (255,)
    return im

def copper_tile():
    im = new(64, 64)
    px = im.load()
    for y in range(64):
        for x in range(64):
            n = h(x // 2, y, 4) % 21 - 10
            c = CU if (y % 16) > 1 else CU_L
            if (y % 16) == 15: c = CU_D
            px[x, y] = sh(c, n) + (255,)
    return im

def brass_tile():
    im = new(64, 64)
    px = im.load()
    for y in range(64):
        for x in range(64):
            n = h(x, y // 2, 8) % 17 - 8
            px[x, y] = sh(BRASS_D if (y % 8) == 7 else BRASS, n) + (255,)
    return im

def black_tile():
    im = new(64, 64)
    px = im.load()
    for y in range(64):
        for x in range(64):
            px[x, y] = sh(BLK, h(x, y, 2) % 5 - 2) + (255,)
    return im

save(wood_tile(), 'tile_wood')
save(wood_tile(dark=True), 'tile_wood_dark')
save(steel_tile(), 'tile_steel')
save(steel_tile(S_D), 'tile_steel_dark')
save(copper_tile(), 'tile_copper')
save(brass_tile(), 'tile_brass')
save(black_tile(), 'tile_black')

# ---------------- record box (wood, same colors as the turntable) ----------------
SLEEVES = [(176, 64, 52), (60, 96, 150), (214, 172, 62), (74, 124, 86), (124, 66, 124), (222, 220, 208), (38, 40, 48), (190, 110, 50), (50, 130, 140)]

def wood_frame(im, w, hh, t=6):
    px = im.load()
    for y in range(hh):
        for x in range(w):
            inside = t <= x < w - t and t <= y < hh - t
            if inside: continue
            c = sh(W_M, h(x, y, 21) % 13 - 6)
            px[x, y] = c + (255,)
    bevel(im, 0, 0, w, hh, W_H, W_D, W_DK)
    bevel(im, t - 1, t - 1, w - 2 * (t - 1), hh - 2 * (t - 1), W_D, W_L)
    for cx, cy in ((2, 2), (w - 4, 2), (2, hh - 4), (w - 4, hh - 4)):
        rect(im, cx, cy, 2, 2, BRASS)

def record_front(standing):
    im = new(64, 64)
    wood_frame(im, 64, 64)
    rect(im, 6, 6, 52, 52, (26, 19, 15))
    px = im.load()
    for y in range(6, 58):                         # soft inner shadow
        for x in range(6, 58):
            d = min(x - 6, 57 - x, y - 6, 57 - y)
            if d < 2: px[x, y] = (18, 13, 10, 255)
    rnd = random.Random(5 if standing else 9)
    pos = 7
    while pos < 56:
        wdt = rnd.choice((3, 4, 4, 5))
        if pos + wdt > 57: wdt = 57 - pos
        if wdt < 2: break
        c = rnd.choice(SLEEVES)
        if standing:
            top = 14 + rnd.choice((0, 0, 1, 2))
            rect(im, pos, 8, wdt, top - 8, BLK)                  # vinyl peeking out
            rect(im, pos, 8, 1, top - 8, S_DK)
            rect(im, pos, top, wdt, 57 - top, c)
            rect(im, pos, top, 1, 57 - top, sh(c, 38))
            rect(im, pos + wdt - 1, top, 1, 57 - top, sh(c, -42))
            rect(im, pos, top, wdt, 1, sh(c, 55))
            if wdt >= 4:
                rect(im, pos + 1, top + 5, wdt - 2, 4, sh(c, 60))   # label strip on the spine
        else:
            rect(im, 7, pos, 44, wdt, c)
            rect(im, 51, pos, 6, wdt, BLK)
            rect(im, 7, pos, 44, 1, sh(c, 50))
            rect(im, 7, pos + wdt - 1, 44, 1, sh(c, -42))
            rect(im, 51, pos, 1, wdt, S_DK)
            if wdt >= 4:
                rect(im, 12, pos + 1, 10, wdt - 2, sh(c, 60))
        pos += wdt
    return im

save(record_front(True), 'record_box_front_standing')
save(record_front(False), 'record_box_front_lying')
side = wood_tile()
bevel(side, 0, 0, 64, 64, W_L, W_D, W_DK)
save(side, 'record_box_side')
top = wood_tile(dark=False)
bevel(top, 0, 0, 64, 64, W_H, W_D, W_DK)
bevel(top, 4, 4, 56, 56, W_D, W_L)
save(top, 'record_box_top')

# ---------------- server ----------------
def server_front():
    im = steel_tile(S_D).crop((0, 0, 64, 56))
    bevel(im, 0, 0, 64, 56, S_L2, S_DK, BLK)
    bevel(im, 2, 2, 60, 52, S_L, S_D)
    rect(im, 4, 4, 56, 48, BLK2)
    for i in range(4):                              # vents
        rect(im, 7, 7 + i * 3, 30, 1, (6, 6, 9))
        rect(im, 7, 8 + i * 3, 30, 1, (52, 55, 62))
    rect(im, 44, 5, 14, 14, S_L)                    # lamp socket
    rect(im, 45, 6, 12, 12, S_DK)
    rect(im, 46, 7, 10, 10, BLK)
    rnd = random.Random(3)
    for i in range(9):
        x = 5 + i * 6
        rect(im, x, 24, 5, 26, (6, 6, 9))
        rect(im, x, 24, 5, 1, (2, 2, 4))
        rect(im, x + 4, 24, 1, 26, (60, 64, 72))
        rect(im, x, 49, 5, 1, (60, 64, 72))
        c = rnd.choice(SLEEVES)
        rect(im, x + 1, 27, 3, 12, c)                # record sleeve
        rect(im, x + 1, 27, 3, 1, sh(c, 50))
        rect(im, x + 1, 41, 3, 1, (20, 165, 175))    # activity led
        rect(im, x + 1, 44, 3, 3, (36, 38, 45))
    return im

def server_side():
    im = steel_tile(S_M).crop((0, 0, 64, 56))
    bevel(im, 0, 0, 64, 56, S_L2, S_DK, BLK)
    bevel(im, 2, 2, 60, 52, S_L, S_D)
    for i in range(7):
        rect(im, 10, 12 + i * 5, 44, 2, (14, 15, 19))
        rect(im, 10, 14 + i * 5, 44, 1, S_L2)
    return im

def server_top():
    im = steel_tile(S_M)
    bevel(im, 0, 0, 64, 64, S_H, S_DK, BLK)
    bevel(im, 3, 3, 58, 58, S_D, S_L2)
    d = ImageDraw.Draw(im)
    d.ellipse([22, 22, 41, 41], fill=S_DK + (255,), outline=BLK + (255,))
    d.ellipse([27, 27, 36, 36], fill=S_L + (255,))
    return im

save(server_front(), 'server_front')
save(server_side(), 'server_side')
save(server_top(), 'server_top')

def lamp(name, core, glow, ring):
    im = new(12, 12)
    d = ImageDraw.Draw(im)
    d.ellipse([0, 0, 11, 11], fill=ring + (255,))
    d.ellipse([1, 1, 10, 10], fill=glow + (255,))
    d.ellipse([2, 2, 9, 9], fill=core + (255,))
    rect(im, 3, 3, 2, 2, sh(core, 70))
    save(im, name)

lamp('server_lamp_red', (230, 40, 40), (150, 20, 20), S_D)
lamp('server_lamp_green', (70, 235, 100), (30, 140, 55), S_D)
lamp('server_lamp_off', (24, 56, 32), (14, 34, 20), S_D)

# ---------------- speaker ----------------
def speaker_front():
    im = new(48, 56)
    wood_frame(im, 48, 56, 4)
    px = im.load()
    for y in range(4, 52):
        for x in range(4, 44):
            px[x, y] = sh((34, 29, 26), h(x, y, 6) % 7 - 3 + (3 if (x + y) % 2 == 0 else 0)) + (255,)
    d = ImageDraw.Draw(im)
    def cone(cx, cy, r, ring):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ring + (255,))
        d.ellipse([cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1], fill=BLK + (255,))
        d.ellipse([cx - r + 3, cy - r + 3, cx + r - 3, cy + r - 3], fill=(46, 48, 56, 255))
        d.ellipse([cx - r + 5, cy - r + 5, cx + r - 5, cy + r - 5], fill=BLK2 + (255,))
        d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=S_M + (255,))
        d.arc([cx - r, cy - r, cx + r, cy + r], 200, 290, fill=S_H + (255,))
    cone(24, 15, 8, S_L2)
    cone(24, 38, 12, S_L2)
    rect(im, 21, 49, 6, 1, BRASS)
    return im

save(speaker_front(), 'speaker_front')

# ---------------- radio (1940s) ----------------
def radio_front():
    im = new(52, 32)
    wood_frame(im, 52, 32, 3)
    px = im.load()
    for y in range(3, 29):                           # cloth grille
        for x in range(3, 26):
            px[x, y] = sh((172, 142, 88), h(x, y, 12) % 9 - 4 + (-12 if x % 3 == 0 else 0)) + (255,)
    for x in range(5, 25, 4):                        # decorative wood slats
        rect(im, x, 4, 1, 24, W_D)
        rect(im, x + 1, 4, 1, 24, W_L)
    bevel(im, 3, 3, 23, 26, W_DK, W_H)
    rect(im, 27, 3, 1, 26, W_DK)                     # divider
    rect(im, 28, 4, 21, 14, BRASS_D)                 # dial window
    rect(im, 29, 5, 19, 12, (232, 216, 172))
    for i in range(10):
        hh = 4 if i % 3 == 0 else 2
        rect(im, 30 + i * 2, 6, 1, hh, (70, 50, 30))
    rect(im, 30, 12, 17, 1, (70, 50, 30))
    rect(im, 40, 5, 1, 11, (196, 40, 36))            # needle
    bevel(im, 28, 4, 21, 14, BRASS_L, BRASS_D)
    d = ImageDraw.Draw(im)
    for cx in (34, 43):                              # knobs
        d.ellipse([cx - 4, 22 - 4, cx + 4, 22 + 4], fill=BRASS + (255,))
        d.ellipse([cx - 3, 22 - 3, cx + 3, 22 + 3], fill=(58, 33, 20, 255))
        rect(im, cx - 1, 22 - 3, 1, 3, BRASS_L)
    return im

save(radio_front(), 'radio_front')

# ---------------- terminal ----------------
def terminal_screen():
    im = new(56, 36)
    rect(im, 0, 0, 56, 36, BLK)
    bevel(im, 0, 0, 56, 36, S_D, BLK)
    rect(im, 3, 3, 50, 30, (8, 22, 26))
    px = im.load()
    for y in range(3, 33):
        for x in range(3, 53):
            if y % 2 == 0:
                px[x, y] = sh(px[x, y][:3], 3) + (255,)
    rect(im, 5, 5, 46, 5, (12, 40, 46))              # search bar
    ImageDraw.Draw(im).rectangle([5, 5, 50, 9], outline=(20, 165, 175, 255))
    rect(im, 7, 7, 14, 1, (110, 220, 230))
    rnd = random.Random(8)
    for i in range(5):
        ln = rnd.randint(18, 40)
        rect(im, 6, 13 + i * 4, ln, 2, (60, 220, 90) if i != 1 else (140, 255, 160))
        rect(im, 6 + ln + 2, 13 + i * 4, 4, 2, (20, 165, 175))
    rect(im, 6, 13 + 1 * 4 - 1, 44, 4, (20, 80, 50, 120))
    rect(im, 50, 30, 2, 2, (60, 220, 90))
    return im

save(terminal_screen(), 'terminal_screen')

# ================= models =================
def uvfull():
    return [0, 0, 16, 16]

def el(frm, to, faces, rotation=None):
    e = {'from': list(frm), 'to': list(to), 'faces': faces}
    if rotation: e['rotation'] = rotation
    return e

def tilef(tex, w, hh):
    return {'uv': [0, 0, w, hh], 'texture': tex}

def box_tile(frm, to, tex):
    w, hh, dp = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    return el(frm, to, {
        'north': tilef(tex, w, hh), 'south': tilef(tex, w, hh),
        'east': tilef(tex, dp, hh), 'west': tilef(tex, dp, hh),
        'up': tilef(tex, w, dp), 'down': tilef(tex, w, dp)})

def wj(name, data):
    with open(MOD + name + '.json', 'w') as f:
        json.dump(data, f, indent=1)

def model(textures, elements, parent='minecraft:block/block'):
    t = dict(textures)
    return {'parent': parent, 'textures': t, 'elements': elements}

def item(name, model_id):
    with open(ITM + name + '.json', 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'soundworks:block/' + model_id}}, f, indent=1)

def tx(n): return 'soundworks:block/' + n

# ---- record box
for kind in ('standing', 'lying'):
    wj('record_box_' + kind, {'parent': 'minecraft:block/cube', 'textures': {
        'particle': tx('record_box_side'), 'down': tx('record_box_top'), 'up': tx('record_box_top'),
        'north': tx('record_box_front_' + kind), 'east': tx('record_box_side'),
        'south': tx('record_box_side'), 'west': tx('record_box_side')}})
rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
variants = {}
for f, y in rot.items():
    for st in ('true', 'false'):
        variants['facing=%s,standing=%s' % (f, st)] = {'model': 'soundworks:block/record_box_' + ('standing' if st == 'true' else 'lying'), 'y': y}
json.dump({'variants': variants}, open(BST + 'record_box.json', 'w'), indent=1)
item('record_box', 'record_box_standing')

# ---- server
body = [
    el([0, 0, 0], [16, 14, 16], {
        'north': {'uv': uvfull(), 'texture': '#front'}, 'south': {'uv': uvfull(), 'texture': '#side'},
        'east': {'uv': uvfull(), 'texture': '#side'}, 'west': {'uv': uvfull(), 'texture': '#side'},
        'up': {'uv': uvfull(), 'texture': '#top'}, 'down': tilef('#dark', 16, 16)}),
    box_tile([7, 14, 7], [9, 16, 9], '#steel'),
    box_tile([1, 14, 14], [4, 15, 15], '#steel'),
]
body_tex = {'front': tx('server_front'), 'side': tx('server_side'), 'top': tx('server_top'),
            'steel': tx('tile_steel'), 'dark': tx('tile_steel_dark'), 'particle': tx('tile_steel')}
wj('server', model(body_tex, body))
lamp_el = lambda: [el([11.5, 9.5, -0.4], [14.5, 12.5, 0.2], {k: {'uv': uvfull(), 'texture': '#lamp'} for k in ('north', 'south', 'east', 'west', 'up', 'down')})]
for n in ('red', 'green', 'off'):
    wj('server_lamp_' + n, {'parent': 'minecraft:block/block', 'textures': {'lamp': tx('server_lamp_' + n), 'particle': tx('tile_steel')}, 'elements': lamp_el()})
t = dict(body_tex); t['lamp'] = tx('server_lamp_green')
wj('server_item', model(t, body + lamp_el()))
lamp_model = {0: 'red', 1: 'green', 2: 'green', 3: 'off'}
mp = []
for f, y in rot.items():
    mp.append({'when': {'facing': f}, 'apply': {'model': 'soundworks:block/server', 'y': y}})
    for lv in range(4):
        mp.append({'when': {'AND': [{'facing': f}, {'lamp': str(lv)}]}, 'apply': {'model': 'soundworks:block/server_lamp_' + lamp_model[lv], 'y': y}})
json.dump({'multipart': mp}, open(BST + 'server.json', 'w'), indent=1)
item('server', 'server_item')

# ---- antenna (steel mast with copper tip)
ant = [
    box_tile([4, 0, 4], [12, 2, 12], '#steel_dark'),
    box_tile([5, 2, 5], [11, 3, 11], '#steel'),
    box_tile([7, 3, 7], [9, 15, 9], '#steel'),
    box_tile([2, 5, 7.25], [14, 6, 8.75], '#copper'),
    box_tile([3.5, 8.5, 7.25], [12.5, 9.5, 8.75], '#copper'),
    box_tile([5, 12, 7.25], [11, 13, 8.75], '#copper'),
    box_tile([7.25, 5, 2], [8.75, 6, 14], '#copper'),
    box_tile([7.25, 8.5, 3.5], [8.75, 9.5, 12.5], '#copper'),
    box_tile([7.25, 12, 5], [8.75, 13, 11], '#copper'),
    box_tile([7, 15, 7], [9, 16, 9], '#copper'),
    box_tile([9, 2, 7], [11, 3, 9], '#brass'),
]
wj('antenna', model({'steel': tx('tile_steel'), 'steel_dark': tx('tile_steel_dark'), 'copper': tx('tile_copper'),
                     'brass': tx('tile_brass'), 'particle': tx('tile_steel')}, ant))
json.dump({'variants': {'': {'model': 'soundworks:block/antenna'}}}, open(BST + 'antenna.json', 'w'), indent=1)
item('antenna', 'antenna')

# ---- speaker
spk = [
    el([2, 0, 3], [14, 14, 13], {
        'north': {'uv': uvfull(), 'texture': '#front'},
        'south': tilef('#wood', 12, 14), 'east': tilef('#wood', 10, 14), 'west': tilef('#wood', 10, 14),
        'up': tilef('#wood', 12, 10), 'down': tilef('#dark', 12, 10)}),
    box_tile([1.5, 0, 2.5], [14.5, 1, 13.5], '#wood_dark'),
    box_tile([7.25, 14, 7.5], [8.75, 15.5, 9], '#copper'),
    box_tile([6, 14, 6], [10, 14.6, 10], '#steel'),
]
wj('speaker', model({'front': tx('speaker_front'), 'wood': tx('tile_wood'), 'wood_dark': tx('tile_wood_dark'),
                     'dark': tx('tile_steel_dark'), 'copper': tx('tile_copper'), 'steel': tx('tile_steel'), 'particle': tx('tile_wood')}, spk))
variants = {'facing=%s' % f: {'model': 'soundworks:block/speaker', 'y': y} for f, y in rot.items()}
json.dump({'variants': variants}, open(BST + 'speaker.json', 'w'), indent=1)
item('speaker', 'speaker')

# ---- radio (1940s cabinet with arched top)
rad = [
    box_tile([1.5, 0, 4], [14.5, 1, 12], '#wood_dark'),
    el([1.5, 1, 4], [14.5, 9, 12], {
        'north': {'uv': uvfull(), 'texture': '#front'},
        'south': tilef('#wood', 13, 8), 'east': tilef('#wood', 8, 8), 'west': tilef('#wood', 8, 8),
        'up': tilef('#wood', 13, 8), 'down': tilef('#wood_dark', 13, 8)}),
    box_tile([2.5, 9, 4.5], [13.5, 10, 11.5], '#wood'),
    box_tile([4, 10, 5], [12, 11, 11], '#wood'),
    box_tile([2, 0, 4.5], [3.5, 1.5, 6], '#brass'),
    box_tile([12.5, 0, 4.5], [14, 1.5, 6], '#brass'),
    box_tile([7.4, 11, 7.4], [8.6, 14, 8.6], '#copper'),
    box_tile([6, 13, 7.4], [10, 13.8, 8.6], '#copper'),
]
wj('radio', model({'front': tx('radio_front'), 'wood': tx('tile_wood'), 'wood_dark': tx('tile_wood_dark'),
                   'brass': tx('tile_brass'), 'copper': tx('tile_copper'), 'particle': tx('tile_wood')}, rad))
variants = {'facing=%s' % f: {'model': 'soundworks:block/radio', 'y': y} for f, y in rot.items()}
json.dump({'variants': variants}, open(BST + 'radio.json', 'w'), indent=1)
item('radio', 'radio')

# ---- terminal: table version (tilted screen) and wall version (flat screen)
screen_faces = lambda: {
    'north': {'uv': uvfull(), 'texture': '#screen'},
    'south': tilef('#steel', 14, 9), 'east': tilef('#steel', 2, 9), 'west': tilef('#steel', 2, 9),
    'up': tilef('#steel', 14, 2), 'down': tilef('#steel', 14, 2)}
table = [
    box_tile([3, 0, 5], [13, 1, 11], '#steel_dark'),
    box_tile([7, 1, 7.5], [9, 5, 9.5], '#steel_dark'),
    el([1, 4, 7], [15, 13, 9], screen_faces(), {'origin': [8, 5, 8], 'axis': 'x', 'angle': -22.5}),
]
tt = {'screen': tx('terminal_screen'), 'steel': tx('tile_steel'), 'steel_dark': tx('tile_steel_dark'), 'particle': tx('tile_steel')}
wj('terminal_table', model(tt, table))
wall = [
    el([1, 3, 13], [15, 12, 16], {
        'north': {'uv': uvfull(), 'texture': '#screen'}, 'south': tilef('#steel', 14, 9),
        'east': tilef('#steel', 3, 9), 'west': tilef('#steel', 3, 9),
        'up': tilef('#steel', 14, 3), 'down': tilef('#steel', 14, 3)}),
    box_tile([5, 12, 14], [11, 13, 16], '#steel_dark'),
]
wj('terminal_wall', model(tt, wall))
variants = {}
for f, y in rot.items():
    variants['wall=false,facing=%s' % f] = {'model': 'soundworks:block/terminal_table', 'y': y}
    variants['wall=true,facing=%s' % f] = {'model': 'soundworks:block/terminal_wall', 'y': y}
json.dump({'variants': variants}, open(BST + 'terminal.json', 'w'), indent=1)
item('terminal', 'terminal_table')
print('ok')
