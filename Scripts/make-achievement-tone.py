#!/usr/bin/env python3
"""Writes res/raw/achievement.wav, the chime a new achievement lands with.

The iOS app synthesizes this tone at launch (`Feedback.synthesize`, case
`.achievement`); these are the same partials, so both apps sound alike: a quick
bell run an octave above the "solved" chord, landing on a detuned pair that
shimmers.

Usage:
    Scripts/make-achievement-tone.py
"""
import math
import os
import struct
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/res/raw/achievement.wav")

RATE = 44100
DURATION = 1.8
ATTACK = 0.005
# frequency, amplitude, start, decay
PARTIALS = [
    (659.25, 0.11, 0.0, 6), (1318.5, 0.025, 0.0, 10),
    (783.99, 0.11, 0.07, 6), (1568, 0.025, 0.07, 10),
    (1046.5, 0.1, 0.14, 3.5), (2093, 0.02, 0.14, 8),
    (1317.5, 0.06, 0.24, 2.4), (1319.5, 0.06, 0.24, 2.4),
]

frames = bytearray()
for frame in range(int(DURATION * RATE)):
    t = frame / RATE
    sample = 0.0
    for frequency, amplitude, start, decay in PARTIALS:
        if t < start:
            continue
        local = t - start
        sample += amplitude * math.sin(2 * math.pi * frequency * local) * math.exp(-local * decay) * min(1, local / ATTACK)
    # A short fade-out prevents a click at the end.
    sample *= max(0.0, min(1.0, (DURATION - t) * 20))
    frames += struct.pack("<h", max(-32767, min(32767, int(sample * 32767))))

with wave.open(OUT, "wb") as out:
    out.setnchannels(1)
    out.setsampwidth(2)
    out.setframerate(RATE)
    out.writeframes(bytes(frames))
print(OUT)
