#!/usr/bin/env python3
"""Generate original, non-speech M1 test signals; no third-party audio assets."""
import math
import struct
import wave
from pathlib import Path

assets = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
assets.mkdir(parents=True, exist_ok=True)
for name, frequency in [('original.wav', 440), ('translation-fixture.wav', 660)]:
    with wave.open(str(assets / name), 'wb') as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(48000)
        output.writeframes(b''.join(
            struct.pack('<h', round(0.25 * 32767 * math.sin(2 * math.pi * frequency * i / 48000)))
            for i in range(96000)
        ))
