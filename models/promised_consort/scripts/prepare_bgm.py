"""Prepare the licensed no-choir/choir loop pair; original Elden Ring audio is not used.

python prepare_bgm.py path/to/Colossal-Boss-Battle-Theme.zip
Requires FFmpeg (FFMPEG env var or imageio_ffmpeg) and numpy for decoded-audio verification.
Only the two named WAV members are read; the archive is never extracted wholesale.
"""
from pathlib import Path
import hashlib, json, os, re, subprocess, sys, zipfile, wave
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'build/python-tools'))
import numpy as np

DEST=ROOT/'src/main/resources/assets/elder_bosses/sounds/music/promised_consort'
DOCUMENTS=ROOT/'models/promised_consort/audio/bgm'
URL='https://opengameart.org/content/colossal-boss-battle-theme'
LICENSE='https://creativecommons.org/licenses/by/3.0/'
MEMBERS={'phase_one':'Colossal Boss Battle Theme/Blackmoor Colossus Loop (No Vocals).wav',
         'phase_two':'Colossal Boss Battle Theme/Blackmoor Colossus Loop.wav'}

def main():
    archive=Path(sys.argv[1]);ffmpeg=os.environ.get('FFMPEG')
    if not ffmpeg:
        import imageio_ffmpeg
        ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
    def run(args):return subprocess.run([ffmpeg,'-hide_banner','-nostdin',*args],capture_output=True,check=True)
    DEST.mkdir(parents=True,exist_ok=True);DOCUMENTS.mkdir(parents=True,exist_ok=True)
    work=ROOT/'build/bgm-source';work.mkdir(parents=True,exist_ok=True)
    entries=[]
    with zipfile.ZipFile(archive) as source:
        for name,member in MEMBERS.items():
            data=source.read(member);wav=work/(name+'.wav');wav.write_bytes(data)
            with wave.open(str(wav)) as source_wave: duration=source_wave.getnframes()/source_wave.getframerate()
            seam=f'afade=t=in:st=0:d=0.008,afade=t=out:st={duration-0.008:.9f}:d=0.008'
            first=run(['-i',str(wav),'-af',seam+',loudnorm=I=-20:TP=-2:LRA=11:print_format=json','-f','null','-'])
            stats=json.loads(re.findall(r'\{[^{}]+\}',first.stderr.decode())[-1])
            normalize='loudnorm=I=-20:TP=-2:LRA=11:linear=true:measured_I='+stats['input_i']+':measured_TP='+stats['input_tp']+':measured_LRA='+stats['input_lra']+':measured_thresh='+stats['input_thresh']+':offset='+stats['target_offset']
            target=DEST/(name+'.ogg')
            run(['-y','-i',str(wav),'-af',seam+','+normalize,'-ar','44100','-ac','2','-c:a','libvorbis','-q:a','5',
                 '-metadata','artist=Matthew Pablo','-metadata','title=Blackmoor Colossus - '+name,
                 '-metadata','license='+LICENSE,'-metadata','comment='+URL+'; loudness normalized for Elder Bosses',str(target)])
            decoded=run(['-v','error','-i',str(target),'-f','f32le','-acodec','pcm_f32le','-'])
            samples=np.frombuffer(decoded.stdout,dtype='<f4').reshape(-1,2)
            peak=float(np.abs(samples).max());boundary=float(np.abs(samples[0]-samples[-1]).max())
            assert np.isfinite(samples).all() and 0.02<peak<0.99,'Silent, nonfinite or clipped output'
            assert boundary<0.08,'Audible loop discontinuity; inspect before shipping'
            entries.append({'phase':name,'source_member':member,'source_sha256':hashlib.sha256(data).hexdigest(),
                'runtime':target.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
                'duration_seconds':len(samples)/44100,'sample_rate':44100,'channels':2,'decoded_peak':peak,
                'loop_boundary_step':boundary,'normalization_target_lufs':-20,'bytes':target.stat().st_size})
    assert abs(entries[0]['duration_seconds']-entries[1]['duration_seconds'])<0.001
    report={'title':'Blackmoor Colossus','author':'Matthew Pablo','source':URL,'license':'CC BY 3.0',
            'license_url':LICENSE,'download_url':'https://opengameart.org/sites/default/files/Colossal%20Boss%20Battle%20Theme_0.zip',
            'archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'elden_ring_audio_used':False,
            'changes':'Original author-provided loop versions, 8ms boundary fades, two-pass loudness normalization, stereo Ogg Vorbis encoding. Phase one has no choir; phase two includes choir. No melody recreation or original-game recording.',
            'audition_status':'in-game listening pending','tracks':entries}
    (DOCUMENTS/'manifest.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=='__main__':main()
