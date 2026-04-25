"""
Generates 15 bundled chime WAV files for the Chime Reminder app.

Run from the project root:
    python generate_chime.py

No external packages required (stdlib only).
Output: app/src/main/res/raw/chime*.wav
"""

import math
import struct
import os

SAMPLE_RATE = 44100
OUT_DIR = os.path.join(os.path.dirname(__file__), "app", "src", "main", "res", "raw")


# ── WAV writer ──────────────────────────────────────────────────────────────

def write_wav(path: str, samples: list[int]) -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    data_size = len(samples) * 2
    with open(path, "wb") as f:
        f.write(b"RIFF")
        f.write(struct.pack("<I", 36 + data_size))
        f.write(b"WAVE")
        f.write(b"fmt ")
        f.write(struct.pack("<I", 16))
        f.write(struct.pack("<H", 1))            # PCM
        f.write(struct.pack("<H", 1))            # mono
        f.write(struct.pack("<I", SAMPLE_RATE))
        f.write(struct.pack("<I", SAMPLE_RATE * 2))
        f.write(struct.pack("<H", 2))            # block align
        f.write(struct.pack("<H", 16))           # bits per sample
        f.write(b"data")
        f.write(struct.pack("<I", data_size))
        for s in samples:
            f.write(struct.pack("<h", max(-32768, min(32767, s))))


def clamp(v: int) -> int:
    return max(-32768, min(32767, v))


# ── Envelope helpers ─────────────────────────────────────────────────────────

def exp_env(t: float, duration: float, attack: float = 0.01, decay_rate: float = 4.0) -> float:
    p = t / duration
    if p < attack:
        return p / attack
    return math.exp(-decay_rate * (p - attack))


def adsr(t: float, a: float, d: float, s_level: float, r_start: float, duration: float) -> float:
    if t < a:
        return t / a
    if t < a + d:
        return 1.0 - (1.0 - s_level) * (t - a) / d
    if t < r_start:
        return s_level
    if t < duration:
        return s_level * (1.0 - (t - r_start) / (duration - r_start))
    return 0.0


# ── Classic chimes ─────────────────────────────────────────────────────────────

def chime_classic(duration=1.2, f_start=880.0, f_end=440.0, amp=28000) -> list[int]:
    """Classic: A5 descending to A4."""
    n = int(SAMPLE_RATE * duration)
    return [clamp(int(amp * exp_env(i / SAMPLE_RATE, duration) *
                      math.sin(2 * math.pi * (f_start + (f_end - f_start) * (i / n)) * i / SAMPLE_RATE)))
            for i in range(n)]


def chime_bell(duration=1.8, freq=523.25, amp=26000) -> list[int]:
    """Bell: C5 with 2nd and 3rd harmonic, slow decay."""
    n = int(SAMPLE_RATE * duration)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, duration, attack=0.005, decay_rate=2.5)
        v = amp * env * (
            0.65 * math.sin(2 * math.pi * freq * t) +
            0.25 * math.sin(2 * math.pi * freq * 2 * t) +
            0.10 * math.sin(2 * math.pi * freq * 3 * t)
        )
        out.append(clamp(int(v)))
    return out


def chime_soft(duration=1.8, freq=261.63, amp=22000) -> list[int]:
    """Soft: gentle C4, very slow decay."""
    n = int(SAMPLE_RATE * duration)
    return [clamp(int(amp * exp_env(i / SAMPLE_RATE, duration, attack=0.05, decay_rate=2.0) *
                      math.sin(2 * math.pi * freq * i / SAMPLE_RATE)))
            for i in range(n)]


def chime_high(duration=0.7, freq=1318.51, amp=24000) -> list[int]:
    """High Pitch: E6 quick bright ping."""
    n = int(SAMPLE_RATE * duration)
    return [clamp(int(amp * exp_env(i / SAMPLE_RATE, duration, attack=0.005, decay_rate=6.0) *
                      math.sin(2 * math.pi * freq * i / SAMPLE_RATE)))
            for i in range(n)]


def chime_low(duration=2.2, freq=196.0, amp=27000) -> list[int]:
    """Deep Bell: G3, long resonant decay."""
    n = int(SAMPLE_RATE * duration)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, duration, attack=0.008, decay_rate=1.8)
        v = amp * env * (
            0.75 * math.sin(2 * math.pi * freq * t) +
            0.20 * math.sin(2 * math.pi * freq * 2.76 * t) +
            0.05 * math.sin(2 * math.pi * freq * 5.4 * t)
        )
        out.append(clamp(int(v)))
    return out


def chime_double(amp=26000) -> list[int]:
    """Double: C5 then G5, two-note chime."""
    def note(freq, duration):
        n = int(SAMPLE_RATE * duration)
        return [clamp(int(amp * exp_env(i / SAMPLE_RATE, duration, decay_rate=4.5) *
                          math.sin(2 * math.pi * freq * i / SAMPLE_RATE)))
                for i in range(n)]
    silence = [0] * int(SAMPLE_RATE * 0.08)
    return note(523.25, 0.75) + silence + note(783.99, 0.75)


def chime_triple(amp=25000) -> list[int]:
    """Triple: C4, E4, G4 ascending arpeggio."""
    freqs = [261.63, 329.63, 392.0]
    out = []
    for freq in freqs:
        dur = 0.55
        n = int(SAMPLE_RATE * dur)
        out += [clamp(int(amp * exp_env(i / SAMPLE_RATE, dur, decay_rate=5.0) *
                          math.sin(2 * math.pi * freq * i / SAMPLE_RATE)))
                for i in range(n)]
        out += [0] * int(SAMPLE_RATE * 0.06)
    return out


def chime_ping(duration=0.45, freq=1396.91, amp=25000) -> list[int]:
    """Ping: F6 very sharp bright ping, instant."""
    n = int(SAMPLE_RATE * duration)
    return [clamp(int(amp * exp_env(i / SAMPLE_RATE, duration, attack=0.003, decay_rate=8.0) *
                      math.sin(2 * math.pi * freq * i / SAMPLE_RATE)))
            for i in range(n)]


def chime_warm(duration=1.6, amp=26000) -> list[int]:
    """Warm: B4 with slight detune for chorus warmth."""
    freq1, freq2 = 493.88, 495.4
    n = int(SAMPLE_RATE * duration)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, duration, attack=0.03, decay_rate=2.8)
        v = amp * env * 0.5 * (
            math.sin(2 * math.pi * freq1 * t) +
            math.sin(2 * math.pi * freq2 * t)
        )
        out.append(clamp(int(v)))
    return out


def chime_descend(amp=26000) -> list[int]:
    """Descend: E5, C5, G4 three-note descent."""
    freqs = [659.25, 523.25, 392.0]
    out = []
    for freq in freqs:
        dur = 0.65
        n = int(SAMPLE_RATE * dur)
        out += [clamp(int(amp * exp_env(i / SAMPLE_RATE, dur, attack=0.006, decay_rate=4.0) *
                          math.sin(2 * math.pi * freq * i / SAMPLE_RATE)))
                for i in range(n)]
        out += [0] * int(SAMPLE_RATE * 0.07)
    return out


# ── Sci-fi chimes ─────────────────────────────────────────────────────────────

def chime_laser(duration=0.6, f_start=1800.0, f_end=80.0, amp=26000) -> list[int]:
    """Laser: exponential frequency chirp sweeping down."""
    n = int(SAMPLE_RATE * duration)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, duration, attack=0.005, decay_rate=3.0)
        freq = f_start * math.exp(math.log(f_end / f_start) * t / duration)
        phase += 2 * math.pi * freq / SAMPLE_RATE
        out.append(clamp(int(amp * env * math.sin(phase))))
    return out


def chime_photon(duration=1.2, carrier=660.0, mod_freq=7.0, amp=26000) -> list[int]:
    """Photon: AM-modulated carrier, ethereal pulsing space tone."""
    n = int(SAMPLE_RATE * duration)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = adsr(t, 0.05, 0.1, 0.7, duration * 0.7, duration)
        mod = 0.5 + 0.5 * math.sin(2 * math.pi * mod_freq * t)
        v = amp * env * mod * math.sin(2 * math.pi * carrier * t)
        out.append(clamp(int(v)))
    return out


def chime_pulse(amp=25000) -> list[int]:
    """Pulse: four crisp rhythmic sci-fi beeps."""
    def beep(freq, duration):
        n = int(SAMPLE_RATE * duration)
        return [clamp(int(amp * exp_env(i / SAMPLE_RATE, duration, attack=0.005, decay_rate=10.0) *
                          math.sin(2 * math.pi * freq * i / SAMPLE_RATE)))
                for i in range(n)]
    gap = [0] * int(SAMPLE_RATE * 0.12)
    b = beep(900.0, 0.18)
    return b + gap + b + gap + b + gap + b


def chime_warp(duration=0.9, carrier=440.0, mod=110.0, amp=26000) -> list[int]:
    """Warp: FM synthesis with sweeping modulation index."""
    n = int(SAMPLE_RATE * duration)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, duration, attack=0.01, decay_rate=2.5)
        mi = 10.0 * math.sin(math.pi * t / duration)
        v = amp * env * math.sin(2 * math.pi * carrier * t + mi * math.sin(2 * math.pi * mod * t))
        out.append(clamp(int(v)))
    return out


def chime_zap(duration=0.35, f_start=200.0, f_end=2400.0, amp=27000) -> list[int]:
    """Zap: quick upward chirp, sci-fi energy burst."""
    n = int(SAMPLE_RATE * duration)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        env = exp_env(t, duration, attack=0.003, decay_rate=8.0)
        freq = f_start + (f_end - f_start) * math.sqrt(t / duration)
        phase += 2 * math.pi * freq / SAMPLE_RATE
        out.append(clamp(int(amp * env * math.sin(phase))))
    return out


# ── Main ──────────────────────────────────────────────────────────────────────

CHIMES = [
    ("chime",          chime_classic),
    ("chime_bell",     chime_bell),
    ("chime_descend",  chime_descend),
    ("chime_double",   chime_double),
    ("chime_high",     chime_high),
    ("chime_laser",    chime_laser),
    ("chime_low",      chime_low),
    ("chime_photon",   chime_photon),
    ("chime_ping",     chime_ping),
    ("chime_pulse",    chime_pulse),
    ("chime_soft",     chime_soft),
    ("chime_triple",   chime_triple),
    ("chime_warm",     chime_warm),
    ("chime_warp",     chime_warp),
    ("chime_zap",      chime_zap),
]

if __name__ == "__main__":
    for name, fn in CHIMES:
        path = os.path.join(OUT_DIR, f"{name}.wav")
        samples = fn()
        write_wav(path, samples)
        duration = len(samples) / SAMPLE_RATE
        print(f"  {name}.wav  — {duration:.2f}s  ({len(samples)} samples)")
    print(f"\nAll {len(CHIMES)} chimes written to {OUT_DIR}")
