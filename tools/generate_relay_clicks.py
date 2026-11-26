#!/usr/bin/env python3
"""Original relay clicks (armature strike plus contact bounce), deterministic, encoded with libsndfile like the machine loops."""
import ctypes, math, random, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_machine_sounds import Info  # noqa: E402

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/sounds/relay'
RATE = 22050


def click(seed):
    rnd = random.Random(seed)
    n = int(RATE * .09)
    ring, thunk = 2600 + seed * 370, 190 + seed * 23
    data = [0.0] * n
    for strike, gain in ((0, 1.0), (int(RATE * (.004 + .001 * seed)), .45), (int(RATE * .009), .2)):
        for i in range(n - strike):
            t = i / RATE
            v = gain * (.55 * math.sin(math.tau * ring * t) * math.exp(-t * 160)
                        + .45 * math.sin(math.tau * thunk * t) * math.exp(-t * 70)
                        + .5 * (rnd.random() * 2 - 1) * math.exp(-t * 900))
            data[strike + i] += v
    peak = max(map(abs, data))
    return [v / peak * .8 for v in data]


def main():
    lib = ctypes.CDLL('libsndfile.so.1')
    lib.sf_open.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(Info)]
    lib.sf_open.restype = ctypes.c_void_p
    lib.sf_writef_float.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.c_longlong]
    lib.sf_close.argtypes = [ctypes.c_void_p]
    OUT.mkdir(parents=True, exist_ok=True)
    for seed in range(3):
        data = click(seed)
        info = Info(len(data), RATE, 1, 0x200060, 0, 0)
        handle = lib.sf_open(str(OUT / f'click{seed}.ogg').encode(), 0x20, ctypes.byref(info))
        assert handle
        lib.sf_writef_float(handle, (ctypes.c_float * len(data))(*data), len(data))
        lib.sf_close(handle)


if __name__ == '__main__':
    main()
