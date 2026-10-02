"""Synthesize an original dark orchestral victory cue for both boss banners.

No recording or sample from Elden Ring is included. The one-shot uses a low
gong, a minor choral chord and high metallic overtones to echo its mood.
"""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys
import math
import random
from array import array

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "build/python-tools"))
import imageio_ffmpeg

RATE = 44100
DURATION = 3.5
FFMPEG = os.environ.get("FFMPEG") or imageio_ffmpeg.get_ffmpeg_exe()
OUTPUT = ROOT / "src/main/resources/assets/elder_bosses/sounds/ui/boss_victory_banner.ogg"

count = round(RATE * DURATION)
rng = random.Random(27937)

# A weighty attack under a slow, ominous D-minor resolution.
gong = array('f')
choir = array('f')
shimmer = array('f')
previous_noise = 0.0
for index in range(count):
    t = index / RATE
    g = sum(gain * math.sin(2 * math.pi * frequency * t * (1 + .005 * math.exp(-t * 11)))
            * math.exp(-t * decay) for frequency, gain, decay in
            [(73.42, .36, 1.6), (110, .20, 1.9), (187.1, .15, 2.4),
             (287.3, .11, 3.1), (491.7, .075, 4.3), (844, .045, 5.8)])
    gong.append(g * (1 - math.exp(-t / .004)))
    c = 0.0
    for frequency, strength in [(146.83, .13), (174.61, .11), (220, .12), (293.66, .07)]:
        vibrato = .0023 * math.sin(2 * math.pi * 4.3 * t + frequency)
        c += strength * (math.sin(2 * math.pi * frequency * (t + vibrato))
                         + .32 * math.sin(2 * math.pi * frequency * 2 * t + .3))
    choir.append(c * (1 - math.exp(-t / .18)) * math.exp(-max(t - .42, 0) * 1.35))
    noise = rng.gauss(0, 1)
    s = .045 * (noise - previous_noise) * math.exp(-t * 7) * (1 - math.exp(-t / .024))
    previous_noise = noise
    if t >= .24:
        onset = t - .24
        s += sum(.033 * math.sin(2 * math.pi * frequency * onset) * math.exp(-onset * 4.8)
                 for frequency in (440, 659.25, 880))
    shimmer.append(s)

# Bright ascending dust before the long tail, softened to sit behind the text.
stereo = array('f')
offsets = [round(RATE * delay) for delay in (.022, .067, .041, .091)]
for index in range(count):
    t = index / RATE
    mono = gong[index] + choir[index] + shimmer[index]
    tail = min(1, (DURATION - t) / .32)
    for choir_delay, gong_delay in (offsets[:2], offsets[2:]):
        c = choir[index - choir_delay] if index >= choir_delay else 0
        g = gong[index - gong_delay] if index >= gong_delay else 0
        stereo.append(math.tanh((mono + .11 * c + .09 * g) * 1.65) * tail)
peak = max(abs(value) for value in stereo)
# Leave headroom for Vorbis reconstruction peaks after the louder compression.
stereo = array('f', (value * .88 / peak for value in stereo))

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
raw = ROOT / "build/victory-banner.f32"
raw.parent.mkdir(parents=True, exist_ok=True)
raw.write_bytes(stereo.tobytes())
subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                "-f", "f32le", "-ar", str(RATE), "-ac", "2", "-i", str(raw),
                "-c:a", "libvorbis", "-q:a", "5", "-metadata", "artist=Elder Bosses",
                "-metadata", "title=Boss Victory Banner",
                "-metadata", "comment=Original synthesis; no sampled game audio", str(OUTPUT)], check=True)
decoded = subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-nostdin",
                          "-i", str(OUTPUT), "-f", "f32le", "-acodec", "pcm_f32le", "-"],
                         capture_output=True, check=True).stdout
samples = array('f')
samples.frombytes(decoded)
decoded_peak = max(abs(value) for value in samples)
assert all(math.isfinite(value) for value in samples) and .05 < decoded_peak < .99
assert abs(len(samples) / 2 / RATE - DURATION) < .05
report = {"source": "Original deterministic synthesis", "sampled_game_audio": False,
          "runtime": OUTPUT.relative_to(ROOT).as_posix(),
          "sha256": hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
          "duration_seconds": round(len(samples) / 2 / RATE, 3), "sample_rate": RATE,
          "channels": 2, "peak": round(decoded_peak, 4), "playback_pitch": .78,
          "playback_duration_seconds": round(DURATION/.78,3)}
(ROOT / "tools/shared/victory_audio.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report))
