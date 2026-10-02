"""Build licensed Invasion excerpts and an original synthesized shield-parry impact.

Usage: python tools/malenia/scripts/prepare_audio_v16.py path/to/invasion.mp3
The MP3 comes from the author's public player, under CC BY 4.0. No game audio is used.
"""
from pathlib import Path
import hashlib, json, os, re, subprocess, sys
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'build/python-tools'))
import numpy as np
import imageio_ffmpeg
FFMPEG = os.environ.get('FFMPEG') or imageio_ffmpeg.get_ffmpeg_exe()
RATE = 44100
WORK = ROOT / 'build/malenia-v16/audio'
DEST = ROOT / 'src/main/resources/assets/elder_bosses/sounds'
DOC = ROOT / 'tools/malenia/audio'
for directory in [WORK, DOC, DEST / 'music/malenia', DEST / 'entity/malenia']:
    directory.mkdir(parents=True, exist_ok=True)

def run(*args):
    return subprocess.run([FFMPEG, '-hide_banner', '-nostdin', *map(str,args)], capture_output=True, check=True)

def encode(samples, name, target, loudness, metadata):
    raw = WORK / (name + '.f32'); raw.write_bytes(samples.astype('<f4').tobytes())
    channels = 1 if samples.ndim == 1 else samples.shape[1]
    inp = ['-f','f32le','-ar',RATE,'-ac',channels,'-i',raw]
    stats = json.loads(re.findall(r'\{[^{}]+\}', run(*inp, '-af', f'loudnorm=I={loudness}:TP=-2:LRA=9:print_format=json', '-f','null','-').stderr.decode())[-1])
    norm = f'loudnorm=I={loudness}:TP=-2:LRA=9:linear=true:measured_I={stats["input_i"]}:measured_TP={stats["input_tp"]}:measured_LRA={stats["input_lra"]}:measured_thresh={stats["input_thresh"]}:offset={stats["target_offset"]}'
    args = ['-y',*inp,'-af',norm,'-ar',RATE,'-c:a','libvorbis','-q:a','5']
    for key,value in metadata.items(): args += ['-metadata',key+'='+value]
    run(*args,target)
    decoded=np.frombuffer(run('-v','error','-i',target,'-f','f32le','-acodec','pcm_f32le','-').stdout,dtype='<f4').reshape(-1,channels)
    assert np.isfinite(decoded).all() and .01<float(np.abs(decoded).max())<.99
    return {'runtime':target.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
            'duration_seconds':len(decoded)/RATE,'sample_rate':RATE,'channels':channels,'peak':float(np.abs(decoded).max()),
            'loop_boundary_step':float(np.abs(decoded[0]-decoded[-1]).max()),'target_lufs':loudness}

source=Path(sys.argv[1]);samples=np.frombuffer(run('-v','error','-i',source,'-ar',RATE,'-ac','2','-f','f32le','-').stdout,dtype='<f4').reshape(-1,2).copy()
assert len(samples)/RATE > 225, 'Expected complete Invasion recording'
tracks=[]
for name,start,end in [('phase_one',52,120),('phase_two',136,204)]:
    segment=samples[int(start*RATE):int(end*RATE)]
    # End-to-start overlap gives a continuous periodic buffer; the first sample follows the pre-tail.
    overlap=2*RATE; blend=np.linspace(0,1,overlap)[:,None]
    join=segment[-overlap:]*(1-blend)+segment[:overlap]*blend
    loop=np.concatenate([segment[overlap:-overlap],join])
    # Guard the Vorbis buffer seam with an eight-millisecond raised-cosine ramp.
    ramp=(.5-.5*np.cos(np.linspace(0,np.pi,int(RATE*.008))))[:,None]
    loop[:len(ramp)]*=ramp;loop[-len(ramp):]*=ramp[::-1]
    entry=encode(loop,name,DEST/'music/malenia'/f'{name}.ogg',-21 if name=='phase_one' else -19,
            {'artist':'Sascha Ende','title':'Invasion - Malenia '+name+' loop','license':'CC BY 4.0',
             'comment':'https://ende.app/en/song/206-invasion; 2-second loop crossfade and loudness adaptation'})
    assert entry['loop_boundary_step']<.003
    entry['source_excerpt_seconds']=[start,end];tracks.append(entry)

# Original impact: low shield-body thump, sharp broadband attack and inharmonic ringing metal.
rng=np.random.default_rng(1609); t=np.arange(int(RATE*.85))/RATE
attack=1-np.exp(-t/0.0007)
signal=.55*np.sin(2*np.pi*(130*t-28*t*t))*np.exp(-t*37)
noise=rng.normal(0,1,len(t)); high=noise-np.concatenate([[0],noise[:-1]])
signal += .19*high*np.exp(-t*125)
for frequency,gain,decay in [(730,.27,7),(1260,.22,8),(2190,.24,10),(3370,.14,14),(4810,.07,20)]:
    signal += gain*np.sin(2*np.pi*frequency*t+.4*np.sin(2*np.pi*13*t))*np.exp(-t*decay)
signal*=attack;signal*=np.minimum(1,(.85-t)/.05);signal=np.tanh(signal*1.15)*.70
parry=encode(signal,'parry_success',DEST/'entity/malenia/parry_success.ogg',-14,
        {'artist':'Elder Bosses','title':'Malenia shield parry success','comment':'Original deterministic synthesis; no sampled game audio'})
report={'music_title':'Invasion','author':'Sascha Ende','source':'https://ende.app/en/song/206-invasion',
        'license':'CC BY 4.0','license_url':'https://creativecommons.org/licenses/by/4.0/',
        'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'source_duration_seconds':len(samples)/RATE,
        'changes':'Two 66-second battle loops cut from the orchestral/choir score; 2-second cyclic crossfade, 8ms seam ramps, per-phase loudness normalization and Ogg Vorbis encoding.',
        'original_game_recording_used':False,'tracks':tracks,'parry':parry,'parry_source':'Original procedural synthesis',
        'in_game_listening_verified':False}
(DOC/'manifest.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
(DOC/'audition.html').write_text('''<!doctype html><meta charset="utf-8"><title>Malenia audio audition</title>
<style>body{background:#19151b;color:#efdfcf;font:18px system-ui;max-width:800px;margin:60px auto}audio{width:100%}</style>
<h1>Malenia audio audition</h1><p>Invasion — Sascha Ende · CC BY 4.0. Phase excerpts and loop crossfades by Elder Bosses.</p>
<h2>Phase one · 66 seconds</h2><audio controls loop src="../../../src/main/resources/assets/elder_bosses/sounds/music/malenia/phase_one.ogg"></audio>
<h2>Phase two · 66 seconds</h2><audio controls loop src="../../../src/main/resources/assets/elder_bosses/sounds/music/malenia/phase_two.ogg"></audio>
<h2>Parry success · original synthesized impact</h2><audio controls src="../../../src/main/resources/assets/elder_bosses/sounds/entity/malenia/parry_success.ogg"></audio>
''',encoding='utf-8')
print(json.dumps(report,indent=2))
