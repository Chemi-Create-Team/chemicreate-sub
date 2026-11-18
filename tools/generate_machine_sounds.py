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
# Fundamental, tone strength, airflow strength, pulse rate and pulse depth.
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
}


class Info(ctypes.Structure):
    _fields_ = [('frames', ctypes.c_longlong), ('samplerate', ctypes.c_int),
                ('channels', ctypes.c_int), ('format', ctypes.c_int),
                ('sections', ctypes.c_int), ('seekable', ctypes.c_int)]


def samples(name, profile):
    frequency, tone, air, pulse, depth = profile
    rng = random.Random(name)
    count, fade = RATE * SECONDS, round(RATE * .18)
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
        raw.append(envelope * (tone * rotor + air * turbulence))
    result = raw[:count]
    for i in range(fade):
        weight = math.sin(i / fade * math.pi / 2) ** 2
        result[i] = weight * raw[i] + (1 - weight) * raw[count + i]
    mean = sum(result) / len(result)
    result = [value - mean for value in result]
    # A restrained common peak leaves headroom when several machines are nearby.
    peak = max(abs(value) for value in result)
    return [value * .58 / peak for value in result]


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
        print(f'{name}: mono {RATE} Hz, {SECONDS}s, peak=.58, RMS={rms:.3f}')


if __name__ == '__main__':
    main()
