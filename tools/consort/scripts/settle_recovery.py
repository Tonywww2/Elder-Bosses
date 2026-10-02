"""Settle two terminal recoveries into source idle, then rebake waist skin.

Attack windows, durations and Master motion remain unchanged. Never blend the
affine mesh control bones independently: derive them from the settled skeleton.
Run after conversion and bake_mesh_skin.py when regenerating these assets.
"""
from __future__ import annotations
import copy
import hashlib
import math
import warnings
import numpy as np
from scipy.spatial.transform import Rotation, Slerp
from paths import WORK, DATA, RUNTIME_RIG, ANIMATIONS
from bake_mesh_skin import Source, bake, digest, interp, read, write
from animation_literals import preserve_decimal_constants, refresh_output_hashes

TARGETS = (3006, 3025)


def settle(animation, idle, geometry, begin):
    duration = animation['animation_length']
    times = np.linspace(begin, duration, math.ceil((duration-begin)*60)+1)
    amount = (times-begin)/(duration-begin)
    amount = amount*amount*(3-2*amount)
    tracks = animation['bones']
    for name in tracks.keys() | idle['bones'].keys():
        if not name.startswith('frame_') or name == 'frame_000':
            continue
        channels = tracks.setdefault(name, {})
        target = idle['bones'].get(name, {})
        for key, neutral in [('position', [0,0,0]), ('scale', [1,1,1]), ('rotation', [0,0,0])]:
            old = channels.get(key, {})
            first = interp(old, np.array([begin]), neutral)[0]
            last = interp(target.get(key), np.array([0.]), neutral)[0]
            if key == 'rotation':
                rest = np.array(geometry[name]['rotation'])
                rotations = Rotation.from_euler('xyz', (np.array([first,last])+rest)*[-1,-1,1], degrees=True)
                with warnings.catch_warnings():
                    warnings.filterwarnings('ignore', message='Gimbal lock detected')
                    angles = Slerp([0,1], rotations)(amount).as_euler('xyz')
                previous = np.radians((first+rest)*[-1,-1,1])
                for i, value in enumerate(angles):
                    alternate = value+[math.pi, math.pi-2*value[1], math.pi]
                    choices = [v+2*math.pi*np.round((previous-v)/(2*math.pi)) for v in (value, alternate)]
                    angles[i] = min(choices, key=lambda v: np.linalg.norm(v-previous))
                    previous = angles[i]
                values = np.degrees(angles)*[-1,-1,1]-rest
            else:
                values = first+(last-first)*amount[:,None]
            # Keep every authored key before the recovery boundary byte-for-byte.
            channel = {k:v for k,v in old.items() if float(k)<begin}
            channel.update({f'{t:.8f}':v.round(8).tolist() for t,v in zip(times,values)})
            channels[key] = channel


def main():
    calibration = read(WORK/'geo/source_rig_calibration.geo.json')['minecraft:geometry'][0]
    geometry = {b['name']:b for b in calibration['bones']}
    source = Source(read(RUNTIME_RIG/'source_bone_map.json'), calibration)
    controls = read(WORK/'rig/mesh_refinement.json')['skin_controls']
    idle = next(iter(read(ANIMATIONS/'source_000020.animation.json')['animations'].values()))
    contracts = {a['tae_id']:a for a in read(DATA/'source_contracts.json')['animations']}
    skin = read(WORK/'rig/mesh_skin_bake.json')
    reports = []
    for tae in TARGETS:
        path = ANIMATIONS/f'source_{tae:06d}.animation.json'
        document = read(path)
        animation = next(iter(document['animations'].values()))
        before = copy.deepcopy(animation)
        damage_end = max(e['end_micros']/1e6 for e in contracts[tae]['events'] if e['type'] in (1,2))
        begin = max(animation['animation_length']-1.2, damage_end+.35)
        assert begin < animation['animation_length']
        animation['bones'] = {k:v for k,v in animation['bones'].items() if not k.startswith('mesh_skin_')}
        settle(animation, idle, geometry, begin)
        for name, channels in before['bones'].items():
            if name.startswith('mesh_skin_'):continue
            for key, channel in channels.items():
                for at, value in channel.items():
                    if float(at)<begin or name=='frame_000':
                        assert animation['bones'][name][key][at] == value
        assert animation['animation_length'] == before['animation_length']
        extra = set()
        for attempt in range(16):
            out, error, count, pending = bake(animation, controls, source, 60, extra)
            if error <= .04:break
            extra.update(pending)
        if error > .04:raise ValueError(f'{tae}: waist interpolation error {error}')
        animation['bones'].update(out)
        preserve_decimal_constants(document)
        write(path, document)
        info = next(r for r in skin['clips'] if r['clip']==path.name)
        info.update(samples=count, midpoint_corner_error_model_units=error,
                    core_source_channel_sha256=digest({k:v for k,v in animation['bones'].items() if k.startswith('frame_')}))
        info['recovery_adapter'] = 'settle_recovery.py'
        reports.append(dict(tae=tae, begin_seconds=begin, duration=animation['animation_length'],
                            last_damage_end_seconds=damage_end, waist_error=error,
                            pre_recovery_keys_unchanged=True, master_unchanged=True,
                            animation_sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        print(f'{tae}: settled last {animation["animation_length"]-begin:.2f}s; waist error {error:.6f}', flush=True)
    skin['source_frame_channels_unchanged'] = False
    skin['source_frame_adaptations'] = dict(script='settle_recovery.py', taes=list(TARGETS), region='terminal recovery only')
    skin['max_midpoint_corner_error_model_units'] = max(r['midpoint_corner_error_model_units'] for r in skin['clips'])
    write(WORK/'rig/mesh_skin_bake.json', skin)
    write(WORK/'rig/recovery_settle.json', dict(clips=reports, target_idle=20, independently_blended_skin=False))
    refresh_output_hashes(WORK)


if __name__ == '__main__':main()
