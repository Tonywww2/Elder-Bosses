"""Build/deploy only the softer holy cue, plus local A/B previews.

Requires numpy and FFmpeg (FFMPEG env var, PATH, or imageio_ffmpeg).
The original v1 cue remains available for comparison; other sounds are hash-checked.
"""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'build/python-tools'))
import numpy as np

RATE = 48000
AUDIO = ROOT / 'models/promised_consort/audio'
OUTPUT = AUDIO / 'prototypes/v2'
RUNTIME = ROOT / 'src/main/resources/assets/elder_bosses/sounds/entity/promised_consort/holy_echo.ogg'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def relative(path):
    return path.relative_to(ROOT).as_posix()


def db(value):
    return float(20 * np.log10(max(float(value), 1e-12)))


def synthesize():
    t = np.arange(int(0.92 * RATE)) / RATE
    rng = np.random.default_rng(1704)
    hz = np.fft.rfftfreq(len(t), 1 / RATE)

    def air(low, high):
        # Smooth spectral skirts avoid the ringing of a hard FFT cut-off.
        window = (1 - np.exp(-(hz / low) ** 4)) * np.exp(-(hz / high) ** 4)
        signal = np.fft.irfft(np.fft.rfft(rng.normal(size=len(t))) * window, n=len(t))
        return signal / np.sqrt(np.mean(signal ** 2))

    def envelope(attack, decay):
        onset = np.sin(np.minimum(t / attack, 1) * np.pi / 2) ** 2
        release = np.sin(np.minimum((t[-1] - t) / 0.18, 1) * np.pi / 2) ** 2
        return onset * np.exp(-t / decay) * release

    # Broad, unpitched bands: no glass transient and no isolated bell/sine partials.
    dry = 0.72 * air(240, 1250) * envelope(0.035, 0.17)
    dry += 0.30 * air(850, 2900) * envelope(0.055, 0.23)
    wash = dry.copy()
    for seconds, gain in [(0.043, 0.16), (0.079, -0.12), (0.127, 0.09), (0.181, 0.06)]:
        delay = round(seconds * RATE)
        wash[delay:] += dry[:-delay] * gain
    wash -= np.mean(wash)
    wash *= np.sin(np.minimum(t / 0.005, 1) * np.pi / 2) ** 2
    wash *= np.sin(np.minimum((t[-1] - t) / 0.12, 1) * np.pi / 2) ** 2
    gain = 10 ** (-17 / 20) / np.max(np.abs(wash))
    return wash * gain, db(gain)


def main():
    ffmpeg = os.environ.get('FFMPEG') or shutil.which('ffmpeg')
    if not ffmpeg:
        import imageio_ffmpeg
        ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()

    def run(args, data=None):
        return subprocess.run([ffmpeg, '-hide_banner', '-nostdin', '-v', 'error', *args],
                              input=data, capture_output=True, check=True).stdout

    def decode(path, rate=RATE):
        return np.frombuffer(run(['-i', str(path), '-ar', str(rate), '-ac', '1',
                                  '-f', 'f32le', '-acodec', 'pcm_f32le', '-']), dtype='<f4')

    def encode(path, samples):
        codec = ['-c:a', 'pcm_s24le'] if path.suffix == '.wav' else ['-c:a', 'libvorbis', '-q:a', '6']
        run(['-y', '-f', 'f32le', '-ar', str(RATE), '-ac', '1', '-i', 'pipe:0', *codec,
             '-fflags', '+bitexact', '-flags:a', '+bitexact', str(path)], samples.astype('<f4').tobytes())

    def measure(path):
        samples = decode(path)
        audible = np.flatnonzero(np.abs(samples) >= 10 ** (-60 / 20))
        assert np.isfinite(samples).all() and len(audible), 'Invalid or silent audio'
        power = np.abs(np.fft.rfft(samples)) ** 2
        hz = np.fft.rfftfreq(len(samples), 1 / RATE)
        stats = {'samples': len(samples), 'duration': len(samples) / RATE, 'channels': 1,
                 'sample_rate': RATE, 'peak_dbfs': db(np.max(np.abs(samples))),
                 'rms_dbfs': db(np.sqrt(np.mean(samples ** 2))), 'dc_offset': float(np.mean(samples)),
                 'leading_quiet_seconds': int(audible[0]) / RATE,
                 'trailing_quiet_seconds': (len(samples) - 1 - int(audible[-1])) / RATE,
                 'oversampled_peak_dbfs': db(np.max(np.abs(decode(path, RATE * 4)))),
                 'first_20ms_peak_dbfs': db(np.max(np.abs(samples[:RATE // 50]))),
                 'energy_above_4khz_fraction': float(power[hz >= 4000].sum() / power.sum()),
                 'strongest_frequency_bin_fraction': float(power.max() / power.sum()),
                 'pcm_sha256': hashlib.sha256(samples.tobytes()).hexdigest()}
        assert stats['oversampled_peak_dbfs'] < -1 and stats['rms_dbfs'] > -50
        assert abs(stats['dc_offset']) < 0.005 and stats['leading_quiet_seconds'] < 0.08
        return stats

    manifest_path = AUDIO / 'manifest.json'
    manifest_text = manifest_path.read_bytes().decode('utf-8')
    manifest = json.loads(manifest_text)
    previous = manifest.get('holy_refinement', {}).get('previous_output') or next(
        item for item in manifest['outputs'] if item['id'] == 'holy_echo')
    baseline = ROOT / previous['ogg']
    assert digest(baseline) == previous['sha256'], 'Original comparison cue changed'
    protected = {ROOT / item['path']: item['sha256'] for item in manifest['runtime_exports'] if item['id'] != 'holy_echo'}
    protected.update({path: digest(path) for path in (ROOT / 'src/main/resources/assets/elder_bosses/sounds/music/promised_consort').glob('*.ogg')})
    protected[ROOT / manifest['sword_profiles']['path']] = manifest['sword_profiles']['sha256']
    for path, expected in protected.items():
        assert digest(path) == expected, f'Unexpected input change: {path}'

    OUTPUT.mkdir(parents=True, exist_ok=True)
    signal, gain_db = synthesize()
    wav, ogg = OUTPUT / 'holy_echo.wav', OUTPUT / 'holy_echo.ogg'
    encode(wav, signal)
    encode(ogg, signal)
    before, after = measure(baseline), measure(ogg)
    assert after['peak_dbfs'] < before['peak_dbfs'] - 5, 'Transient reduction lost'
    assert after['first_20ms_peak_dbfs'] < before['first_20ms_peak_dbfs'] - 5, 'Attack is too sharp'
    assert after['strongest_frequency_bin_fraction'] < before['strongest_frequency_bin_fraction'] / 4, 'Isolated chime remains'

    # A deliberately dense example of four pairs of simultaneous pillars. No normalization:
    # retain the actual difference between v1 (two cues/tick) and v2 (one cue/tick).
    for name, sample, count in [('holy_burst_before', decode(baseline), 2), ('holy_burst_after', decode(ogg), 1)]:
        mix = np.zeros(RATE * 3)
        for seconds in (0.25, 0.5, 0.75, 1.0):
            offset = round(seconds * RATE)
            mix[offset:offset + len(sample)] += sample * count * 0.5
        assert np.max(np.abs(mix)) < 0.98, 'Preview clips'
        encode(OUTPUT / (name + '.ogg'), mix)

    peaks = [float(np.max(np.abs(chunk))) for chunk in np.array_split(signal, 450)]
    lines = ''.join(f'<path d="M{i*2+1} {40-p*240:.2f}V{40+p*240:.2f}"/>' for i, p in enumerate(peaks))
    waveform = OUTPUT / 'holy_echo.svg'
    waveform.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 80">'
                        '<rect width="900" height="80" fill="#edf2ef"/><g stroke="#326c5c">' + lines + '</g></svg>\n', encoding='utf-8')
    current = {'id': 'holy_echo', 'wav': relative(wav), 'ogg': relative(ogg), 'waveform': relative(waveform),
               'sha256': digest(ogg), 'wav_sha256': digest(wav), 'gain_db': gain_db, 'measurement': after,
               'recipe': {'revision': 'holy_soft_v2', 'duration': 0.92, 'peak_db': -17, 'seed': 1704,
                          'purpose': 'Soft diffuse holy-light wash beneath the physical blade',
                          'sources': 'Original deterministic noise synthesis; no external recordings',
                          'layers': ['240-1250 Hz soft body, 35 ms attack', '850-2900 Hz diffuse air, 55 ms attack',
                                     '43/79/127/181 ms low-gain diffusion, smooth release']},
               'builder': {'file': relative(Path(__file__).resolve()), 'sha256': digest(Path(__file__))}}
    shutil.copyfile(ogg, RUNTIME)
    manifest['outputs'] = [current if item['id'] == 'holy_echo' else item for item in manifest['outputs']]
    for item in manifest['runtime_exports']:
        if item['id'] == 'holy_echo':
            item['sha256'] = current['sha256']
    manifest['revision'] = 'audio_holy_soft_v2'
    manifest['scope'] = 'Six action sounds integrated; original +3dB base mix with independently mastered holy_soft_v2 override'
    manifest['style'] = 'Weighty steel and stone, low gravity pressure, soft diffuse holy light'
    manifest['integration']['current_revision_checks'] = manifest['integration']['current_revision_checks'].replace(
        'six OGG samples unchanged', 'five OGG samples unchanged; holy_echo replaced with soft wash v2')
    manifest['holy_refinement'] = {'revision': 'holy_soft_v2', 'previous_output': previous,
                                  'before': before, 'after': after, 'max_holy_cues_per_boss_per_tick': 1,
                                  'audition': relative(AUDIO / 'holy_audition.html'),
                                  'preview_note': 'Four pairs of pillars 250 ms apart at volume 0.5; no normalization. Offline example, not in-world capture.',
                                  'world_listening': 'pending'}
    manifest['user_listening_accepted'] = False
    manifest['world_tested'] = False
    result = json.dumps(manifest, indent=2, ensure_ascii=False) + '\n'
    manifest_path.write_bytes(result.replace('\n', '\r\n' if '\r\n' in manifest_text else '\n').encode('utf-8'))
    for path, expected in protected.items():
        assert digest(path) == expected, f'Unrelated asset changed: {path}'
    assert digest(RUNTIME) == current['sha256']
    print(json.dumps({'before': before, 'after': after, 'unrelated_assets_preserved': len(protected),
                      'runtime_sha256': digest(RUNTIME)}, indent=2))


if __name__ == '__main__':
    main()
