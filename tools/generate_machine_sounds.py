#!/usr/bin/env python3
"""Original, deterministic mono industrial loops; encodes Vorbis with libsndfile.

No recordings or third-party sound assets are used. The first 0.18 seconds are
crossfaded from the continuation after the loop so the buffer boundary is smooth.
"""
import ctypes
import ctypes.util
import math
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/create_refinery_n_reactors/sounds/machine'
RATE, SECONDS = 22050, 6
SHORT_SECONDS = 4
# Fundamental, tone strength, airflow strength, pulse rate, pulse depth and optional sound texture.
PROFILES = {
    'reactor_circulation': (70, .32, .36, 3, .10),
    'fusion_drive': (110, .39, .20, 2, .06),
    'turbine_high': (420, .22, .34, 7, .04),
    'turbine_low': (150, .30, .44, 5, .08),
    'compressor': (90, .27, .27, 4, .62),
    'agitator': (80, .31, .22, 2, .28),
    'hot_process': (60, .12, .58, 3, .18),
    'fluid_process': (130, .08, .32, 6, .55),
    'blower': (180, .22, .43, 4, .12),
    'crane_drive': (140, .35, .14, 8, .15),
    'flare_roar': (48, .06, .95, 1.1, .25),
    'centrifuge': (560, .32, .15, 13, .08, 'rotor'),
    'cubic_press': (65, .37, .20, 1.5, .72, 'piston'),
    'boiler': (37, .04, .75, 3.5, .32, 'bubbles'),
    'coldbox': (248, .14, .64, 9, .32, 'air'),
    'shell_exchanger': (98, .10, .45, 2, .12, 'liquid'),
    'large_exchanger': (47, .15, .63, 1.5, .20, 'liquid'),
    'jacket': (205, .15, .37, 4.5, .10, 'liquid'),
    'absorber': (119, .06, .55, 6.5, .60, 'bubbles'),
    'packed_tower': (275, .08, .54, 5.5, .37, 'air'),
    'reaction_tower': (320, .17, .57, 6, .29, 'fire'),
    'vertical_separator': (53, .06, .65, 2.5, .57, 'bubbles'),
    'separator_drum': (102, .06, .65, 3, .72, 'bubbles'),
    'distillation': (85, .06, .72, 2, .34, 'bubbles'),
    'electric_furnace': (61, .21, .68, 12, .30, 'electric'),
    'electrolysis': (167, .14, .45, 8, .35, 'bubbles'),
    'cooling_tower': (41, .06, .94, 2, .07, 'liquid'),
    'condenser': (124, .06, .75, 2.5, .25, 'air'),
    'waste_melter': (53, .16, .62, 4, .28, 'electric'),
    'solvent_extractor': (107, .23, .40, 3.5, .42, 'liquid'),
    'converter': (51, .09, .93, 5, .24, 'fire'),
    'fuel_cell': (192, .17, .40, 9.5, .18, 'electric'),
    'fusion_crane': (195, .27, .25, 3, .26, 'rotor'),
    'quarry': (55, .30, .46, 6.5, .43, 'piston'),
    'gasifier': (43, .10, .68, 3, .43, 'fire'),
    'motor_generator': (120, .34, .22, 8, .14, 'electric'),
    'flywheel': (73, .29, .45, 4.5, .11, 'rotor'),
    'vacuum_pump': (215, .25, .35, 10, .31, 'rotor'),
    'electric_pump': (147, .29, .25, 7, .30, 'electric'),
    'material_pump': (82, .25, .37, 5, .36, 'piston'),
    'relief_scrubber': (254, .03, .72, 10, .56, 'bubbles'),
    'gas_holder': (68, .14, .42, 1.5, .30, 'rotor'),
    'constructor': (182, .28, .27, 5.5, .57, 'piston'),
    'dispatch': (232, .18, .20, 4, .63, 'piston'),
    'pressurised_gas': (430, .025, .72, 6, .14, 'air'),
    'high_pressure_gas': (770, .095, .85, 8.5, .09, 'air'),
}


class Info(ctypes.Structure):
    _fields_ = [('frames', ctypes.c_longlong), ('samplerate', ctypes.c_int),
                ('channels', ctypes.c_int), ('format', ctypes.c_int),
                ('sections', ctypes.c_int), ('seekable', ctypes.c_int)]


def samples(name, profile):
    frequency, tone, air, pulse, depth, *texture = profile
    texture = texture[0] if texture else None
    rng = random.Random(name)
    count = RATE * (SHORT_SECONDS if texture else SECONDS)
    fade = round(RATE * .18)
    raw, slow, fast = [], 0., 0.
    for i in range(count + fade):
        t = i / RATE
        noise = rng.uniform(-1, 1)
        slow += .025 * (noise - slow)
        fast += .24 * (noise - fast)
        envelope = 1 - depth + depth * (.5 + .5 * math.sin(math.tau * pulse * t))
        rotor = (math.sin(math.tau * frequency * t)
                 + .32 * math.sin(math.tau * frequency * 2 * t)
                 + .12 * math.sin(math.tau * frequency * 3 * t))
        turbulence = slow * 2.1 + fast * .40
        if name in ('turbine_high', 'blower'):
            turbulence += (noise - fast) * .22
        if name == 'fluid_process':
            turbulence *= 1 + .35 * math.sin(math.tau * 13 * t)
        if texture == 'air':
            turbulence = .45 * slow + .75 * fast + .64 * (noise - fast)
            rotor = math.sin(math.tau * frequency * t + .22 * math.sin(math.tau * 2.5 * t))
        elif texture == 'liquid':
            turbulence = 3.2 * slow + .42 * fast
            envelope *= .84 + .16 * math.sin(math.tau * 7.5 * t)
        elif texture == 'bubbles':
            bubble = max(0, math.sin(math.tau * 1.5 * pulse * t + .45 * math.sin(math.tau * 1.5 * t))) ** 6
            turbulence = 1.35 * slow + .55 * fast + bubble * (2.0 * slow + .24 * math.sin(math.tau * frequency * 2 * t))
        elif texture == 'piston':
            hit = (1 - (t * pulse % 1)) ** 22
            rotor = .50 * rotor + hit * (1.2 * math.sin(math.tau * frequency * t) + .55 * math.sin(math.tau * frequency * 5 * t))
            turbulence = .8 * slow + (.3 + hit) * fast
        elif texture == 'electric':
            rotor *= .82 + .18 * math.cos(math.tau * 60 * t)
            turbulence = .75 * fast + .85 * (noise - fast) * max(0, math.sin(math.tau * pulse * 2 * t)) ** 8
        elif texture == 'rotor':
            rotor += .32 * math.sin(math.tau * (frequency * 2 + 3) * t)
            turbulence = 1.45 * slow + .75 * fast
        elif texture == 'fire':
            turbulence = 2.8 * slow + .70 * fast + .45 * (noise - fast) * max(0, math.sin(math.tau * 8.5 * t)) ** 3
        raw.append(envelope * (tone * rotor + air * turbulence))
    result = raw[:count]
    for i in range(fade):
        weight = math.sin(i / fade * math.pi / 2) ** 2
        result[i] = weight * raw[i] + (1 - weight) * raw[count + i]
    mean = sum(result) / len(result)
    result = [value - mean for value in result]
    # Vorbis can add peaks to broadband hiss, so leave extra headroom on those loops.
    peak = max(abs(value) for value in result)
    ceiling = .44 if texture == 'air' else .58
    return [value * ceiling / peak for value in result]


def main():
    lib = ctypes.CDLL(ctypes.util.find_library('sndfile') or 'libsndfile.so.1')
    lib.sf_open.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(Info)]
    lib.sf_open.restype = ctypes.c_void_p
    lib.sf_writef_float.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.c_longlong]
    lib.sf_writef_float.restype = ctypes.c_longlong
    lib.sf_close.argtypes = [ctypes.c_void_p]
    OUT.mkdir(parents=True, exist_ok=True)
    for name, profile in PROFILES.items():
        if len(sys.argv)>1 and name not in sys.argv[1:]: continue
        data = samples(name, profile)
        info = Info(len(data), RATE, 1, 0x200060, 0, 0)  # OGG | VORBIS
        handle = lib.sf_open(str(OUT / (name + '.ogg')).encode(), 0x20, ctypes.byref(info))
        if not handle:
            raise RuntimeError('Could not encode Vorbis: ' + name)
        try:
            buffer = (ctypes.c_float * len(data))(*data)
            assert lib.sf_writef_float(handle, buffer, len(data)) == len(data), name
        finally:
            assert lib.sf_close(handle) == 0, name
        rms = math.sqrt(sum(value * value for value in data) / len(data))
        print(f'{name}: mono {RATE} Hz, {len(data) / RATE:.0f}s, peak={max(map(abs, data)):.3f}, RMS={rms:.3f}')


if __name__ == '__main__':
    main()
