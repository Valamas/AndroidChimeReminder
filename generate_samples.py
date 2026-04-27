"""
Generates 50 chime samples across 5 categories for review.

Run from the project root:
    python generate_samples.py

Output: chime_samples/
  animal_      cricket, bee, bird, frog, etc.
  nature_      rain, drip, thunder, wind, etc.
  instrument_  kalimba, marimba, steel drum, etc.
  kitchen_     timer, cork, kettle, etc.
  ambient_     sonar, heartbeat, typewriter, etc.
"""

import math, struct, os, random

SAMPLE_RATE = 44100
OUT_DIR = os.path.join(os.path.dirname(__file__), "chime_samples")

def write_wav(name, samples):
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, name)
    data_size = len(samples) * 2
    with open(path, "wb") as f:
        f.write(b"RIFF"); f.write(struct.pack("<I", 36 + data_size))
        f.write(b"WAVE"); f.write(b"fmt "); f.write(struct.pack("<I", 16))
        f.write(struct.pack("<H", 1)); f.write(struct.pack("<H", 1))
        f.write(struct.pack("<I", SAMPLE_RATE)); f.write(struct.pack("<I", SAMPLE_RATE * 2))
        f.write(struct.pack("<H", 2)); f.write(struct.pack("<H", 16))
        f.write(b"data"); f.write(struct.pack("<I", data_size))
        for s in samples:
            f.write(struct.pack("<h", max(-32768, min(32767, int(s)))))

def clamp(v): return max(-32768, min(32767, int(v)))
def silence(secs): return [0] * int(SAMPLE_RATE * secs)
def exp_env(t, duration, attack=0.01, decay_rate=4.0):
    p = t / max(duration, 1e-9)
    if p < attack: return p / attack
    return math.exp(-decay_rate * (p - attack))
def sine(freq, duration, amp, attack=0.01, decay_rate=4.0):
    n = int(SAMPLE_RATE * duration)
    return [clamp(amp * exp_env(i/SAMPLE_RATE, duration, attack, decay_rate) *
                  math.sin(2*math.pi*freq*i/SAMPLE_RATE)) for i in range(n)]

# seeded noise for reproducibility
rng = random.Random(42)
def noise(): return rng.uniform(-1, 1)

# ── ANIMAL (10) ──────────────────────────────────────────────────────────────

def animal_01_cricket():
    "Three rapid cricket chirps — 4200 Hz amplitude-modulated clicks"
    def chirp():
        dur = 0.08
        n = int(SAMPLE_RATE * dur)
        out = []
        for i in range(n):
            t = i / SAMPLE_RATE
            env = math.sin(math.pi * t / dur) ** 2
            click_rate = 60.0
            gate = 1.0 if math.sin(2*math.pi*click_rate*t) > 0 else 0.0
            out.append(clamp(22000 * env * gate * math.sin(2*math.pi*4200*t)))
        return out
    return chirp() + silence(0.07) + chirp() + silence(0.07) + chirp()

def animal_02_bee_buzz():
    "Bee buzz — 220 Hz carrier AM-modulated at 180 Hz, short burst"
    n = int(SAMPLE_RATE * 0.9)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 0.9, attack=0.05, decay_rate=2.5)
        am = 0.5 + 0.5 * math.sin(2*math.pi*180*t)
        carrier = math.sin(2*math.pi*220*t) + 0.3*math.sin(2*math.pi*440*t)
        out.append(clamp(20000 * env * am * carrier))
    return out

def animal_03_bird_tweet():
    "Bird tweet — FM frequency sweep 1800→3200→2000 Hz"
    dur = 0.5
    n = int(SAMPLE_RATE * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        p = t / dur
        env = math.sin(math.pi * p) ** 0.7
        freq = 1800 + 1400*math.sin(math.pi*p) + 200*math.sin(2*math.pi*8*t)
        phase += 2*math.pi*freq/SAMPLE_RATE
        out.append(clamp(24000 * env * math.sin(phase)))
    return out

def animal_04_frog_ribbit():
    "Frog ribbit — two-part croak at 180 Hz with FM grunt"
    def croak(duration, f0):
        n = int(SAMPLE_RATE * duration)
        out = []
        phase = 0.0
        for i in range(n):
            t = i / SAMPLE_RATE
            env = exp_env(t, duration, attack=0.02, decay_rate=5.0)
            mod = 3.0 * math.exp(-8*t)
            freq = f0 + 30*math.sin(2*math.pi*12*t)
            phase += 2*math.pi*freq/SAMPLE_RATE
            out.append(clamp(22000 * env * math.sin(phase + mod*math.sin(2*math.pi*f0*2*t))))
        return out
    return croak(0.18, 180) + silence(0.05) + croak(0.28, 160)

def animal_05_cat_purr():
    "Cat purr — 25 Hz fundamental with harmonics, slow rumble"
    n = int(SAMPLE_RATE * 2.5)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 2.5, attack=0.3, decay_rate=1.0)
        gate = 0.6 + 0.4*math.sin(2*math.pi*25*t)
        v = (0.5*math.sin(2*math.pi*25*t) +
             0.3*math.sin(2*math.pi*50*t) +
             0.2*math.sin(2*math.pi*75*t))
        out.append(clamp(18000 * env * gate * v))
    return out

def animal_06_owl_hoot():
    "Owl hoot — two slow mournful hoots at 320 Hz with vibrato"
    def hoot(dur):
        n = int(SAMPLE_RATE * dur)
        out = []
        for i in range(n):
            t = i / SAMPLE_RATE
            env = exp_env(t, dur, attack=0.08, decay_rate=3.0)
            freq = 320 + 8*math.sin(2*math.pi*5*t)
            out.append(clamp(22000 * env * (
                0.7*math.sin(2*math.pi*freq*t) +
                0.3*math.sin(2*math.pi*freq*2*t))))
        return out
    return hoot(0.5) + silence(0.2) + hoot(0.7)

def animal_07_dolphin_click():
    "Dolphin click train — rapid ultrasonic-style clicks"
    def click():
        n = int(SAMPLE_RATE * 0.015)
        return [clamp(26000 * exp_env(i/SAMPLE_RATE, 0.015, attack=0.01, decay_rate=20.0) *
                      math.sin(2*math.pi*3500*i/SAMPLE_RATE)) for i in range(n)]
    out = []
    for gap_ms in [0.04, 0.03, 0.025, 0.02, 0.018, 0.016]:
        out += click() + silence(gap_ms)
    return out

def animal_08_horse_whinny():
    "Horse whinny — rising then falling FM sweep 400→900→300 Hz"
    dur = 1.2
    n = int(SAMPLE_RATE * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        p = t / dur
        env = math.sin(math.pi * p) ** 0.5
        if p < 0.4:
            freq = 400 + (900-400)*(p/0.4)
        else:
            freq = 900 - (900-300)*((p-0.4)/0.6)
        freq += 40*math.sin(2*math.pi*15*t)
        phase += 2*math.pi*freq/SAMPLE_RATE
        out.append(clamp(20000 * env * math.sin(phase)))
    return out

def animal_09_bee_arrive():
    "Bee approaching — buzz that rises in pitch and volume"
    dur = 1.0
    n = int(SAMPLE_RATE * dur)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        p = t / dur
        env = p ** 1.5
        am = 0.5 + 0.5*math.sin(2*math.pi*(150 + 60*p)*t)
        carrier = math.sin(2*math.pi*(180 + 80*p)*t)
        out.append(clamp(18000 * env * am * carrier))
    return out

def animal_10_bird_morning():
    "Morning bird song — three varied tweets"
    def tweet(f_start, f_end, dur, amp=23000):
        n = int(SAMPLE_RATE * dur)
        out = []
        phase = 0.0
        for i in range(n):
            p = i / n
            env = math.sin(math.pi * p) ** 0.8
            freq = f_start + (f_end - f_start)*p + 150*math.sin(2*math.pi*12*i/SAMPLE_RATE)
            phase += 2*math.pi*freq/SAMPLE_RATE
            out.append(clamp(amp * env * math.sin(phase)))
        return out
    return (tweet(2000, 3000, 0.2) + silence(0.08) +
            tweet(2800, 1800, 0.15) + silence(0.06) +
            tweet(2200, 3500, 0.25))


# ── NATURE (10) ──────────────────────────────────────────────────────────────

def nature_01_raindrop():
    "Single raindrop on water — plop with ripple decay"
    n = int(SAMPLE_RATE * 0.8)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 0.8, attack=0.004, decay_rate=5.0)
        freq = 800 * math.exp(-8*t) + 200
        out.append(clamp(24000 * env * math.sin(2*math.pi*freq*t)))
    return out

def nature_02_water_drip():
    "Dripping tap — three drops with slight pitch variation"
    def drop(freq):
        n = int(SAMPLE_RATE * 0.6)
        out = []
        for i in range(n):
            t = i / SAMPLE_RATE
            env = exp_env(t, 0.6, attack=0.003, decay_rate=6.0)
            f = freq * math.exp(-5*t) + freq*0.3
            out.append(clamp(22000 * env * math.sin(2*math.pi*f*t)))
        return out
    return drop(900) + silence(0.4) + drop(850) + silence(0.35) + drop(920)

def nature_03_thunder_crack():
    "Thunder — low rumble built from dense harmonics"
    dur = 2.5
    n = int(SAMPLE_RATE * dur)
    out = []
    rng2 = random.Random(99)
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, dur, attack=0.02, decay_rate=2.0)
        v = sum(rng2.uniform(0.5,1.0)*math.sin(2*math.pi*f*t + rng2.uniform(0, 6.28))
                for f in [40, 55, 70, 85, 100, 120, 150])
        out.append(clamp(22000 * env * v / 7))
    return out

def nature_04_wind_gust():
    "Wind gust — bandpass-filtered noise sweep"
    dur = 2.0
    n = int(SAMPLE_RATE * dur)
    out = []
    lp = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        p = t / dur
        env = math.sin(math.pi * p) ** 1.5
        raw = noise()
        freq_lp = 400 + 300*math.sin(math.pi*p)
        alpha = 1 - math.exp(-2*math.pi*freq_lp/SAMPLE_RATE)
        lp = lp + alpha*(raw - lp)
        out.append(clamp(16000 * env * lp))
    return out

def nature_05_stream_babble():
    "Babbling stream — layered filtered noise at different rates"
    dur = 3.0
    n = int(SAMPLE_RATE * dur)
    out = []
    lp1, lp2, lp3 = 0.0, 0.0, 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, dur, attack=0.5, decay_rate=0.8)
        r = noise()
        a1 = 1 - math.exp(-2*math.pi*300/SAMPLE_RATE)
        a2 = 1 - math.exp(-2*math.pi*600/SAMPLE_RATE)
        a3 = 1 - math.exp(-2*math.pi*1200/SAMPLE_RATE)
        lp1 += a1*(r - lp1); lp2 += a2*(r - lp2); lp3 += a3*(r - lp3)
        v = 0.4*lp1 + 0.35*lp2 + 0.25*lp3
        out.append(clamp(14000 * env * v))
    return out

def nature_06_wave_crash():
    "Ocean wave crash — noise swell and retreat"
    dur = 3.5
    n = int(SAMPLE_RATE * dur)
    out = []
    lp = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        p = t / dur
        swell = math.sin(math.pi * min(p*2, 1.0)) if p < 0.5 else (1.0 - (p-0.5)*2)**2
        raw = noise()
        alpha = 1 - math.exp(-2*math.pi*800/SAMPLE_RATE)
        lp = lp + alpha*(raw - lp)
        out.append(clamp(20000 * swell * lp))
    return out

def nature_07_ice_crack():
    "Ice cracking — sharp transient with crystalline decay"
    dur = 1.2
    n = int(SAMPLE_RATE * dur)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, dur, attack=0.001, decay_rate=7.0)
        v = (0.4*math.sin(2*math.pi*800*t) +
             0.3*math.sin(2*math.pi*1600*t) +
             0.2*math.sin(2*math.pi*2400*t) +
             0.1*noise())
        out.append(clamp(26000 * env * v))
    return out

def nature_08_bamboo_knock():
    "Hollow bamboo knock — woody thud at 400 Hz"
    n = int(SAMPLE_RATE * 1.0)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 1.0, attack=0.003, decay_rate=8.0)
        v = (0.6*math.sin(2*math.pi*400*t) +
             0.25*math.sin(2*math.pi*1050*t) +
             0.15*math.sin(2*math.pi*1800*t))
        out.append(clamp(25000 * env * v))
    return out

def nature_09_fire_crackle():
    "Campfire crackle — random pops over a low rumble"
    dur = 2.0
    n = int(SAMPLE_RATE * dur)
    out = []
    rng3 = random.Random(77)
    pop_times = sorted(rng3.uniform(0, dur) for _ in range(12))
    for i in range(n):
        t = i / SAMPLE_RATE
        base = 6000 * (0.4 + 0.6*rng3.random()*0.01) * math.sin(2*math.pi*60*t) * 0.1
        pop_v = sum(20000 * math.exp(-80*(t-pt)) * (rng3.random()*2-1)
                    for pt in pop_times if 0 <= t-pt < 0.05)
        out.append(clamp(base + pop_v))
    return out

def nature_10_cave_drip():
    "Cave drip — deep resonant single drop echoing"
    n = int(SAMPLE_RATE * 2.0)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 2.0, attack=0.003, decay_rate=3.0)
        freq = 320 * math.exp(-3*t) + 120
        v = (0.6*math.sin(2*math.pi*freq*t) +
             0.3*math.sin(2*math.pi*freq*2*t) +
             0.1*math.sin(2*math.pi*freq*3*t))
        echo_env = 0.4 * exp_env(max(0, t-0.3), 1.7, attack=0.003, decay_rate=3.0) if t > 0.3 else 0
        out.append(clamp(24000 * (env + echo_env) * v))
    return out


# ── INSTRUMENT (10) ──────────────────────────────────────────────────────────

def instrument_01_kalimba():
    "Kalimba pluck — metallic tine with inharmonic partials"
    freq = 659.25  # E5
    n = int(SAMPLE_RATE * 2.5)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 2.5, attack=0.004, decay_rate=2.2)
        v = (0.55*math.sin(2*math.pi*freq*t) +
             0.25*math.sin(2*math.pi*freq*2.03*t) +
             0.15*math.sin(2*math.pi*freq*3.11*t) +
             0.05*math.sin(2*math.pi*freq*4.7*t))
        out.append(clamp(26000 * env * v))
    return out

def instrument_02_marimba():
    "Marimba hit — woody resonance at C5"
    n = int(SAMPLE_RATE * 1.5)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 1.5, attack=0.005, decay_rate=3.5)
        v = (0.6*math.sin(2*math.pi*523.25*t) +
             0.25*math.sin(2*math.pi*523.25*4*t) +
             0.15*math.sin(2*math.pi*523.25*10*t))
        out.append(clamp(26000 * env * v))
    return out

def instrument_03_steel_drum():
    "Steel drum — Caribbean pan note at D5"
    freq = 587.33
    n = int(SAMPLE_RATE * 2.0)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 2.0, attack=0.006, decay_rate=2.5)
        v = (0.5*math.sin(2*math.pi*freq*t) +
             0.3*math.sin(2*math.pi*freq*2*t) +
             0.12*math.sin(2*math.pi*freq*3.1*t) +
             0.08*math.sin(2*math.pi*freq*5.4*t))
        out.append(clamp(25000 * env * v))
    return out

def instrument_04_xylophone():
    "Xylophone — bright G5 with quick attack"
    return (sine(783.99, 0.8, 26000, attack=0.004, decay_rate=5.0) +
            silence(0.05) + sine(1046.5, 0.8, 26000, attack=0.004, decay_rate=5.0))

def instrument_05_music_box():
    "Music box — delicate C major arpeggio"
    out = []
    for freq in [523.25, 659.25, 783.99, 1046.5]:
        n = int(SAMPLE_RATE * 0.5)
        for i in range(n):
            t = i / SAMPLE_RATE
            env = exp_env(t, 0.5, attack=0.003, decay_rate=4.5)
            v = (0.65*math.sin(2*math.pi*freq*t) +
                 0.25*math.sin(2*math.pi*freq*2*t) +
                 0.10*math.sin(2*math.pi*freq*3*t))
            out.append(clamp(22000 * env * v))
        out += silence(0.03)
    return out

def instrument_06_sitar_pluck():
    "Sitar pluck — G3 with sympathetic string buzz"
    freq = 196.0
    dur = 2.5
    n = int(SAMPLE_RATE * dur)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, dur, attack=0.005, decay_rate=2.0)
        buzz = 0.15 * math.sin(2*math.pi*196.0*1.002*t + 3*math.exp(-3*t))
        v = (0.5*math.sin(2*math.pi*freq*t) +
             0.25*math.sin(2*math.pi*freq*2*t) +
             0.15*math.sin(2*math.pi*freq*3*t) + buzz)
        out.append(clamp(24000 * env * v))
    return out

def instrument_07_pan_flute():
    "Pan flute — breathy A4 with air noise"
    dur = 1.8
    n = int(SAMPLE_RATE * dur)
    out = []
    lp = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, dur, attack=0.12, decay_rate=1.8)
        tone = 0.75*math.sin(2*math.pi*440*t) + 0.25*math.sin(2*math.pi*880*t)
        raw = noise()
        alpha = 1 - math.exp(-2*math.pi*2000/SAMPLE_RATE)
        lp = lp + alpha*(raw - lp)
        breath = 0.25 * lp
        out.append(clamp(22000 * env * (tone + breath)))
    return out

def instrument_08_mbira():
    "Mbira (thumb piano) — two tines together, D5 and F#5"
    n = int(SAMPLE_RATE * 2.0)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 2.0, attack=0.004, decay_rate=2.5)
        v = (0.5*math.sin(2*math.pi*587.33*t + 2.1*math.sin(2*math.pi*587.33*2*t)) +
             0.5*math.sin(2*math.pi*739.99*t + 2.1*math.sin(2*math.pi*739.99*2*t)))
        out.append(clamp(24000 * env * v * 0.5))
    return out

def instrument_09_glass_harp():
    "Glass harp — sustained B4 from a wet finger on glass"
    dur = 2.5
    n = int(SAMPLE_RATE * dur)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, dur, attack=0.2, decay_rate=1.2)
        trem = 1.0 + 0.06*math.sin(2*math.pi*6.5*t)
        v = (0.7*math.sin(2*math.pi*493.88*t) +
             0.2*math.sin(2*math.pi*987.77*t) +
             0.1*math.sin(2*math.pi*1481.0*t))
        out.append(clamp(22000 * env * trem * v))
    return out

def instrument_10_handpan():
    "Handpan — resonant D4 with lush harmonics"
    freq = 293.66
    dur = 3.0
    n = int(SAMPLE_RATE * dur)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, dur, attack=0.007, decay_rate=1.5)
        v = (0.55*math.sin(2*math.pi*freq*t) +
             0.25*math.sin(2*math.pi*freq*2*t) +
             0.12*math.sin(2*math.pi*freq*3*t) +
             0.05*math.sin(2*math.pi*freq*4*t) +
             0.03*math.sin(2*math.pi*freq*5.2*t))
        out.append(clamp(27000 * env * v))
    return out


# ── KITCHEN (10) ─────────────────────────────────────────────────────────────

def kitchen_01_microwave_beep():
    "Microwave done — three short electronic beeps"
    def beep():
        return sine(1480.0, 0.2, 24000, attack=0.005, decay_rate=12.0)
    return beep() + silence(0.15) + beep() + silence(0.15) + beep()

def kitchen_02_timer_ding():
    "Kitchen timer ding — bright C6 bell"
    return sine(1046.5, 1.2, 26000, attack=0.004, decay_rate=4.0)

def kitchen_03_cork_pop():
    "Champagne cork pop — low thud burst"
    n = int(SAMPLE_RATE * 0.5)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 0.5, attack=0.002, decay_rate=10.0)
        freq = 150 * math.exp(-15*t) + 80
        v = math.sin(2*math.pi*freq*t) + 0.3*noise()*math.exp(-20*t)
        out.append(clamp(28000 * env * v))
    return out

def kitchen_04_kettle_whistle():
    "Kettle starting to whistle — rising steam tone"
    dur = 2.0
    n = int(SAMPLE_RATE * dur)
    out = []
    lp = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        p = t / dur
        env = p ** 1.2
        freq = 1200 + 400*p
        tone = 0.6*math.sin(2*math.pi*freq*t) + 0.4*math.sin(2*math.pi*freq*2*t)
        raw = noise()
        alpha = 1 - math.exp(-2*math.pi*3000/SAMPLE_RATE)
        lp = lp + alpha*(raw - lp)
        out.append(clamp(20000 * env * (tone + 0.3*lp)))
    return out

def kitchen_05_coffee_drip():
    "Espresso drip — rhythmic liquid drops"
    def drip(freq):
        n = int(SAMPLE_RATE * 0.25)
        out = []
        for i in range(n):
            t = i / SAMPLE_RATE
            env = exp_env(t, 0.25, attack=0.004, decay_rate=12.0)
            out.append(clamp(20000 * env * math.sin(2*math.pi*freq*t)))
        return out
    return drip(600) + silence(0.3) + drip(580) + silence(0.25) + drip(620) + silence(0.28) + drip(590)

def kitchen_06_glass_clink():
    "Wine glasses clinking — bright ring at 900 Hz"
    n = int(SAMPLE_RATE * 2.0)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 2.0, attack=0.003, decay_rate=2.0)
        v = (0.6*math.sin(2*math.pi*900*t) +
             0.3*math.sin(2*math.pi*1800*t) +
             0.1*math.sin(2*math.pi*2700*t))
        out.append(clamp(24000 * env * v))
    return out

def kitchen_07_toaster_pop():
    "Toaster lever pop — mechanical spring snap"
    n = int(SAMPLE_RATE * 0.4)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 0.4, attack=0.001, decay_rate=12.0)
        spring = (0.5*math.sin(2*math.pi*300*t) +
                  0.3*math.sin(2*math.pi*700*t) +
                  0.2*noise()*math.exp(-30*t))
        out.append(clamp(26000 * env * spring))
    return out

def kitchen_08_spoon_tap():
    "Spoon on ceramic mug — dull tap at 650 Hz"
    n = int(SAMPLE_RATE * 0.8)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, 0.8, attack=0.002, decay_rate=7.0)
        v = (0.55*math.sin(2*math.pi*650*t) +
             0.30*math.sin(2*math.pi*1380*t) +
             0.15*math.sin(2*math.pi*2100*t))
        out.append(clamp(24000 * env * v))
    return out

def kitchen_09_blender_pulse():
    "Blender short pulse — motor noise burst"
    dur = 0.6
    n = int(SAMPLE_RATE * dur)
    out = []
    lp = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.sin(math.pi * t / dur) ** 2
        raw = noise()
        alpha = 1 - math.exp(-2*math.pi*500/SAMPLE_RATE)
        lp = lp + alpha*(raw - lp)
        motor = 0.4*math.sin(2*math.pi*180*t) + 0.3*math.sin(2*math.pi*360*t)
        out.append(clamp(16000 * env * (0.5*lp + 0.5*motor)))
    return out

def kitchen_10_fridge_alert():
    "Fridge door open alert — two descending tones"
    return (sine(880.0, 0.4, 23000, attack=0.01, decay_rate=6.0) +
            silence(0.1) +
            sine(659.25, 0.6, 23000, attack=0.01, decay_rate=4.0))


# ── AMBIENT (10) ─────────────────────────────────────────────────────────────

def ambient_01_sonar_ping():
    "Sonar ping — distinctive descending sweep with echo"
    def ping(amp):
        dur = 0.8
        n = int(SAMPLE_RATE * dur)
        out = []
        phase = 0.0
        for i in range(n):
            t = i / SAMPLE_RATE
            env = exp_env(t, dur, attack=0.008, decay_rate=4.0)
            freq = 1200 * math.exp(-2*t) + 200
            phase += 2*math.pi*freq/SAMPLE_RATE
            out.append(clamp(amp * env * math.sin(phase)))
        return out
    return ping(26000) + silence(0.6) + ping(12000)

def ambient_02_heartbeat():
    "Heartbeat — lub-dub pattern"
    def thump(freq, dur, amp):
        n = int(SAMPLE_RATE * dur)
        return [clamp(amp * exp_env(i/SAMPLE_RATE, dur, attack=0.01, decay_rate=8.0) *
                      math.sin(2*math.pi*freq*i/SAMPLE_RATE)) for i in range(n)]
    return (thump(80, 0.15, 26000) + silence(0.06) +
            thump(65, 0.2, 22000) + silence(0.7))

def ambient_03_clock_tick():
    "Grandfather clock tick-tock"
    def tick(freq):
        n = int(SAMPLE_RATE * 0.05)
        return [clamp(25000 * exp_env(i/SAMPLE_RATE, 0.05, attack=0.002, decay_rate=25.0) *
                      math.sin(2*math.pi*freq*i/SAMPLE_RATE)) for i in range(n)]
    return tick(1200) + silence(0.45) + tick(900) + silence(0.45)

def ambient_04_typewriter():
    "Typewriter — three key strikes and carriage return"
    def key():
        n = int(SAMPLE_RATE * 0.08)
        out = []
        for i in range(n):
            t = i / SAMPLE_RATE
            env = exp_env(t, 0.08, attack=0.002, decay_rate=18.0)
            v = (0.5*math.sin(2*math.pi*1100*t) +
                 0.3*noise()*math.exp(-40*t) +
                 0.2*math.sin(2*math.pi*550*t))
            out.append(clamp(22000 * env * v))
        return out
    def carriage():
        n = int(SAMPLE_RATE * 0.25)
        out = []
        for i in range(n):
            t = i / SAMPLE_RATE
            env = exp_env(t, 0.25, attack=0.005, decay_rate=6.0)
            out.append(clamp(20000 * env * (0.6*math.sin(2*math.pi*400*t) + 0.4*noise()*0.3)))
        return out
    return key() + silence(0.12) + key() + silence(0.10) + key() + silence(0.18) + carriage()

def ambient_05_radar_sweep():
    "Radar ping — rising chirp with rotation feel"
    dur = 1.5
    n = int(SAMPLE_RATE * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        p = t / dur
        env = math.exp(-2*p) * math.sin(math.pi*p)**0.3
        freq = 300 + 800*p
        phase += 2*math.pi*freq/SAMPLE_RATE
        out.append(clamp(24000 * env * math.sin(phase)))
    return out

def ambient_06_bubble():
    "Underwater bubble rising"
    dur = 0.7
    n = int(SAMPLE_RATE * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        p = t / dur
        env = math.sin(math.pi * p) ** 1.5
        freq = 400 + 600*p*p
        phase += 2*math.pi*freq/SAMPLE_RATE
        out.append(clamp(22000 * env * math.sin(phase)))
    return out

def ambient_07_morse_sos():
    "Morse SOS — · · · — — — · · ·"
    def dot(): return sine(800, 0.1, 24000, attack=0.005, decay_rate=12.0) + silence(0.1)
    def dash(): return sine(800, 0.3, 24000, attack=0.005, decay_rate=10.0) + silence(0.1)
    out = []
    for _ in range(3): out += dot()
    out += silence(0.1)
    for _ in range(3): out += dash()
    out += silence(0.1)
    for _ in range(3): out += dot()
    return out

def ambient_08_geiger_click():
    "Geiger counter — random radioactive clicks"
    dur = 1.5
    n = int(SAMPLE_RATE * dur)
    out = [0] * n
    rng4 = random.Random(55)
    click_times = sorted(rng4.uniform(0, dur) for _ in range(14))
    for ct in click_times:
        idx = int(ct * SAMPLE_RATE)
        for j in range(min(int(SAMPLE_RATE*0.02), n-idx)):
            t = j / SAMPLE_RATE
            env = math.exp(-150*t)
            out[idx+j] += clamp(26000 * env * (noise()*0.7 + 0.3*math.sin(2*math.pi*2000*t)))
    return [clamp(v) for v in out]

def ambient_09_space_station():
    "Space station ambience — slow low hum with distant beep"
    dur = 3.0
    n = int(SAMPLE_RATE * dur)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        hum = (0.4*math.sin(2*math.pi*60*t) +
               0.3*math.sin(2*math.pi*120*t) +
               0.2*math.sin(2*math.pi*180*t))
        beep = 0.0
        if 1.5 < t < 1.7:
            beep = math.sin(2*math.pi*880*t) * exp_env(t-1.5, 0.2, attack=0.01, decay_rate=8.0)
        out.append(clamp(14000 * (hum*0.3 + beep*0.7)))
    return out

def ambient_10_singing_bowl():
    "Tibetan singing bowl strike — long resonant A3 wash"
    freq = 220.0
    dur = 4.0
    n = int(SAMPLE_RATE * dur)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, dur, attack=0.01, decay_rate=0.9)
        trem = 1.0 + 0.04*math.sin(2*math.pi*6*t)
        v = (0.6*math.sin(2*math.pi*freq*t) +
             0.25*math.sin(2*math.pi*freq*2.76*t) +
             0.10*math.sin(2*math.pi*freq*5.4*t) +
             0.05*math.sin(2*math.pi*freq*8.9*t))
        out.append(clamp(25000 * env * trem * v))
    return out


# ── Main ─────────────────────────────────────────────────────────────────────

SAMPLES = [
    ("animal_01_cricket.wav",       animal_01_cricket),
    ("animal_02_bee_buzz.wav",      animal_02_bee_buzz),
    ("animal_03_bird_tweet.wav",    animal_03_bird_tweet),
    ("animal_04_frog_ribbit.wav",   animal_04_frog_ribbit),
    ("animal_05_cat_purr.wav",      animal_05_cat_purr),
    ("animal_06_owl_hoot.wav",      animal_06_owl_hoot),
    ("animal_07_dolphin_click.wav", animal_07_dolphin_click),
    ("animal_08_horse_whinny.wav",  animal_08_horse_whinny),
    ("animal_09_bee_arrive.wav",    animal_09_bee_arrive),
    ("animal_10_bird_morning.wav",  animal_10_bird_morning),

    ("nature_01_raindrop.wav",      nature_01_raindrop),
    ("nature_02_water_drip.wav",    nature_02_water_drip),
    ("nature_03_thunder_crack.wav", nature_03_thunder_crack),
    ("nature_04_wind_gust.wav",     nature_04_wind_gust),
    ("nature_05_stream_babble.wav", nature_05_stream_babble),
    ("nature_06_wave_crash.wav",    nature_06_wave_crash),
    ("nature_07_ice_crack.wav",     nature_07_ice_crack),
    ("nature_08_bamboo_knock.wav",  nature_08_bamboo_knock),
    ("nature_09_fire_crackle.wav",  nature_09_fire_crackle),
    ("nature_10_cave_drip.wav",     nature_10_cave_drip),

    ("instrument_01_kalimba.wav",   instrument_01_kalimba),
    ("instrument_02_marimba.wav",   instrument_02_marimba),
    ("instrument_03_steel_drum.wav",instrument_03_steel_drum),
    ("instrument_04_xylophone.wav", instrument_04_xylophone),
    ("instrument_05_music_box.wav", instrument_05_music_box),
    ("instrument_06_sitar_pluck.wav",instrument_06_sitar_pluck),
    ("instrument_07_pan_flute.wav", instrument_07_pan_flute),
    ("instrument_08_mbira.wav",     instrument_08_mbira),
    ("instrument_09_glass_harp.wav",instrument_09_glass_harp),
    ("instrument_10_handpan.wav",   instrument_10_handpan),

    ("kitchen_01_microwave_beep.wav",kitchen_01_microwave_beep),
    ("kitchen_02_timer_ding.wav",   kitchen_02_timer_ding),
    ("kitchen_03_cork_pop.wav",     kitchen_03_cork_pop),
    ("kitchen_04_kettle_whistle.wav",kitchen_04_kettle_whistle),
    ("kitchen_05_coffee_drip.wav",  kitchen_05_coffee_drip),
    ("kitchen_06_glass_clink.wav",  kitchen_06_glass_clink),
    ("kitchen_07_toaster_pop.wav",  kitchen_07_toaster_pop),
    ("kitchen_08_spoon_tap.wav",    kitchen_08_spoon_tap),
    ("kitchen_09_blender_pulse.wav",kitchen_09_blender_pulse),
    ("kitchen_10_fridge_alert.wav", kitchen_10_fridge_alert),

    ("ambient_01_sonar_ping.wav",   ambient_01_sonar_ping),
    ("ambient_02_heartbeat.wav",    ambient_02_heartbeat),
    ("ambient_03_clock_tick.wav",   ambient_03_clock_tick),
    ("ambient_04_typewriter.wav",   ambient_04_typewriter),
    ("ambient_05_radar_sweep.wav",  ambient_05_radar_sweep),
    ("ambient_06_bubble.wav",       ambient_06_bubble),
    ("ambient_07_morse_sos.wav",    ambient_07_morse_sos),
    ("ambient_08_geiger_click.wav", ambient_08_geiger_click),
    ("ambient_09_space_station.wav",ambient_09_space_station),
    ("ambient_10_singing_bowl.wav", ambient_10_singing_bowl),
]

if __name__ == "__main__":
    # Remove old samples
    import glob
    for f in glob.glob(os.path.join(OUT_DIR, "*.wav")):
        os.remove(f)
    print(f"Cleared old samples from {OUT_DIR}/\n")

    for name, fn in SAMPLES:
        samples = fn()
        write_wav(name, samples)
        print(f"  {name}  ({len(samples)/SAMPLE_RATE:.2f}s)")
    print(f"\nAll {len(SAMPLES)} samples written to {OUT_DIR}/")
