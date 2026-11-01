#!/usr/bin/env python3
"""v10.8: generates Settai's small sound effects (procedural, no samples) into app/src/main/res/raw.
22.05 kHz mono 16-bit WAV. Re-run to regenerate: python3 tools/gen_sfx.py"""
import math, random, struct, wave, os
SR = 22050
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'raw')
rnd = random.Random(1008)

def write(name, xs, gain=0.9):
    peak = max(1e-6, max(abs(x) for x in xs))
    with wave.open(os.path.join(OUT, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, x / peak * gain)) * 32000)) for x in xs))

def lp(xs, a):
    y = 0.0; out = []
    for x in xs: y += a * (x - y); out.append(y)
    return out

def noise(n): return [rnd.uniform(-1, 1) for _ in range(n)]
def env(n, attack, decay_pow=2.0):
    a = max(1, int(attack * SR)); return [min(1, i / a) * (1 - i / n) ** decay_pow for i in range(n)]

def crack():
    n = int(0.16 * SR); ns = noise(n); e = env(n, 0.001, 4)
    tone = [math.sin(2 * math.pi * 1800 * i / SR) * 0.3 for i in range(n)]
    return [(ns[i] + tone[i]) * e[i] for i in range(n)]

def shatter():
    n = int(0.55 * SR); xs = [0.0] * n
    for _ in range(26):   # glassy tinkles
        st = rnd.randint(0, int(n * 0.7)); f = rnd.uniform(2500, 6000); ln = rnd.randint(900, 3000)
        for i in range(ln):
            if st + i < n: xs[st + i] += math.sin(2 * math.pi * f * i / SR) * (1 - i / ln) ** 2 * 0.5
    ns = noise(n); e = env(n, 0.002, 3)
    return [xs[i] + ns[i] * e[i] * 0.8 for i in range(n)]

def whoosh():
    n = int(0.42 * SR); ns = noise(n); out = []; y = 0.0
    for i in range(n):
        u = i / n; a = 0.02 + 0.25 * math.sin(math.pi * u)
        y += a * (ns[i] - y); out.append(y * math.sin(math.pi * u))
    return out

def boom(dur=0.9, f0=70, crackle=0.0):
    n = int(dur * SR); ns = lp(noise(n), 0.08); e = env(n, 0.003, 2.5); out = []
    ph = 0.0
    for i in range(n):
        f = f0 * (1.6 - 0.6 * i / n); ph += 2 * math.pi * f / SR
        out.append((math.sin(ph) * 0.9 + ns[i] * 2.2) * e[i])
    if crackle > 0:
        for _ in range(int(60 * crackle)):
            st = rnd.randint(int(n * 0.25), n - 200)
            for j in range(120): out[st + j] += rnd.uniform(-1, 1) * (1 - j / 120) * 0.6
    return out

def crackle():
    n = int(0.9 * SR); out = [0.0] * n
    for _ in range(70):
        st = rnd.randint(0, n - 300); a = rnd.uniform(0.3, 1.0) * (1 - st / n)
        for j in range(200): out[st + j] += rnd.uniform(-1, 1) * (1 - j / 200) ** 2 * a
    return out

def whistle():
    n = int(0.8 * SR); out = []; ph = 0.0
    for i in range(n):
        u = i / n; f = 2600 - 1500 * u + 60 * math.sin(2 * math.pi * 9 * u)
        ph += 2 * math.pi * f / SR
        out.append(math.sin(ph) * math.sin(math.pi * min(1, u * 1.15)) * 0.7 + rnd.uniform(-1, 1) * 0.05)
    return out

def fizz():
    n = int(0.7 * SR); ns = noise(n); out = []; y = 0.0
    for i in range(n):
        y += 0.6 * (ns[i] - y)
        out.append((ns[i] - y) * (0.6 + 0.4 * math.sin(2 * math.pi * 13 * i / SR)) * min(1, i / 400, (n - i) / 400))
    return out

# ---- v10.21 cartoon show sounds (added after the old ones so their random stream is unchanged) ----
def tone(n, f0, f1, wave='sine', decay=3.0, attack=0.002):
    out = []; ph = 0.0; e = env(n, attack, decay)
    for i in range(n):
        u = i / n; f = f0 * (f1 / f0) ** u; ph += 2 * math.pi * f / SR
        s = math.sin(ph)
        if wave == 'tri': s = 2 / math.pi * math.asin(s)
        out.append(s * e[i])
    return out

def pluck():          # pizzicato tiptoe note (pitch-shifted per step by the player)
    n = int(0.22 * SR); return [a + 0.4 * b for a, b in zip(tone(n, 660, 640, 'tri', 5), tone(n, 1320, 1300, 'sine', 8))]

def zip_():           # quick rising whoosh: zipping off
    n = int(0.28 * SR); ns = noise(n); out = []; y = 0.0; ph = 0.0
    for i in range(n):
        u = i / n; y += (0.05 + 0.5 * u) * (ns[i] - y); ph += 2 * math.pi * (300 + 1500 * u) / SR
        out.append((y * 0.8 + math.sin(ph) * 0.25) * math.sin(math.pi * u))
    return out

def boing():          # spring
    n = int(0.5 * SR); out = []; ph = 0.0
    for i in range(n):
        u = i / n; f = 220 + 160 * math.sin(2 * math.pi * 11 * u) * (1 - u); ph += 2 * math.pi * f / SR
        out.append(math.sin(ph) * (1 - u) ** 1.5 * min(1, i / 60))
    return out

def splat():          # wet smack
    n = int(0.3 * SR); ns = lp(noise(n), 0.25); e = env(n, 0.001, 3)
    thump = tone(n, 140, 60, 'sine', 4)
    return [(ns[i] * 2.2 + thump[i]) * e[i] for i in range(n)]

def thud():           # body hits the floor
    n = int(0.35 * SR); ns = lp(noise(n), 0.06); e = env(n, 0.001, 4)
    t = tone(n, 110, 45, 'sine', 3)
    return [(t[i] + ns[i] * 3) * e[i] for i in range(n)]

def clang():          # metal pan
    n = int(0.6 * SR); out = [0.0] * n
    for f, a, dcy in ((520, 1, 3), (1190, 0.6, 4), (1870, 0.45, 5), (2730, 0.3, 6), (3410, 0.2, 7)):
        ph = 0.0
        for i in range(n):
            ph += 2 * math.pi * f / SR; out[i] += math.sin(ph) * a * (1 - i / n) ** dcy
    ns = noise(400)
    for i in range(400): out[i] += ns[i] * (1 - i / 400) * 0.8
    return out

def bonk():           # pan meets face: low dull clang + boing
    n = int(0.55 * SR); a = clang()[:n]; b = tone(n, 180, 90, 'sine', 2)
    return [a[i] * 0.5 + b[i] for i in range(n)]

def slide():          # slide whistle going down
    n = int(0.6 * SR); out = []; ph = 0.0
    for i in range(n):
        u = i / n; f = 1400 * (1 - 0.65 * u) + 25 * math.sin(2 * math.pi * 6 * u); ph += 2 * math.pi * f / SR
        out.append(math.sin(ph) * math.sin(math.pi * min(1, u * 1.1)) + rnd.uniform(-1, 1) * 0.03)
    return out

def pop():            # popping in / out of the hole
    n = int(0.12 * SR); return tone(n, 900, 300, 'sine', 3)

def wahwah():         # the sad trombone: four sinking notes
    out = []
    for k, (f, ln) in enumerate(((392, 0.32), (370, 0.32), (349, 0.32), (330, 0.9))):
        n = int(ln * SR); ph = 0.0
        for i in range(n):
            u = i / n; vib = 1 + (0.02 * math.sin(2 * math.pi * 6 * i / SR) if k == 3 else 0)
            ph += 2 * math.pi * f * vib / SR
            s = math.sin(ph) + 0.5 * math.sin(2 * ph) + 0.3 * math.sin(3 * ph)
            out.append(s * min(1, i / 300) * (1 - u) ** (0.4 if k < 3 else 1.5) * 0.6)
    return lp(out, 0.35)

def tada():           # a tiny music-box ending
    out = []
    for f, ln in ((784, 0.14), (988, 0.14), (1175, 0.14), (1568, 0.6)):
        n = int(ln * SR); out += [a + 0.3 * b for a, b in zip(tone(n, f, f, 'sine', 2.5), tone(n, f * 2, f * 2, 'sine', 4))]
    return out

def show_sounds():
    write('sfx_pluck', pluck(), 0.6); write('sfx_zip', zip_(), 0.6); write('sfx_boing', boing(), 0.7)
    write('sfx_splat', splat(), 0.85); write('sfx_thud', thud(), 0.9); write('sfx_clang', clang(), 0.6)
    write('sfx_bonk', bonk(), 0.8); write('sfx_slide', slide(), 0.5); write('sfx_pop', pop(), 0.6)
    write('sfx_wahwah', wahwah(), 0.6); write('sfx_tada', tada(), 0.55)

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    write('sfx_crack', crack()); write('sfx_shatter', shatter()); write('sfx_whoosh', whoosh(), 0.7)
    write('sfx_mega', boom(1.1, 55, 0.6)); write('sfx_fw_boom', boom(0.75, 85)); write('sfx_fw_crackle', crackle(), 0.6)
    write('sfx_whistle', whistle(), 0.6); write('sfx_fizz', fizz(), 0.45)
    show_sounds()
