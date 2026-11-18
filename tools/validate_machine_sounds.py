#!/usr/bin/env python3
"""Decode machine loops and check signal integrity, resource paths and subtitles.

This is a numerical asset check, not an assessment of perceived sound quality.
Requires libsndfile with Vorbis support; no Python audio package is needed.
"""
import argparse
import ast
import ctypes
import ctypes.util
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NAMESPACE = 'create_refinery_n_reactors'
RATE, SECONDS = 22050, 6


class Info(ctypes.Structure):
    _fields_ = [('frames', ctypes.c_longlong), ('samplerate', ctypes.c_int),
                ('channels', ctypes.c_int), ('format', ctypes.c_int),
                ('sections', ctypes.c_int), ('seekable', ctypes.c_int)]


def sound_library():
    lib = ctypes.CDLL(ctypes.util.find_library('sndfile') or 'libsndfile.so.1')
    lib.sf_open.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(Info)]
    lib.sf_open.restype = ctypes.c_void_p
    lib.sf_readf_double.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_double), ctypes.c_longlong]
    lib.sf_readf_double.restype = ctypes.c_longlong
    lib.sf_close.argtypes = [ctypes.c_void_p]
    lib.sf_close.restype = ctypes.c_int
    lib.sf_strerror.argtypes = [ctypes.c_void_p]
    lib.sf_strerror.restype = ctypes.c_char_p
    lib.sf_version_string.restype = ctypes.c_char_p
    return lib


def decode(lib, path):
    info = Info()
    handle = lib.sf_open(str(path).encode(), 0x10, ctypes.byref(info))
    assert handle, f'Cannot decode {path.name}: {lib.sf_strerror(None).decode()}'
    try:
        assert info.channels == 1 and info.samplerate == RATE, (path.name, info.channels, info.samplerate)
        assert info.frames == RATE * SECONDS, (path.name, 'Expected exactly six seconds', info.frames)
        assert info.format & 0x0fff0000 == 0x200000 and info.format & 0xffff == 0x0060, \
            (path.name, 'Expected Ogg/Vorbis', hex(info.format))
        buffer = (ctypes.c_double * info.frames)()
        frames = lib.sf_readf_double(handle, buffer, info.frames)
        assert frames == info.frames, (path.name, 'Truncated decoder output', frames, info.frames)
        extra = (ctypes.c_double * 1)()
        assert lib.sf_readf_double(handle, extra, 1) == 0, (path.name, 'Unexpected trailing frames')
        return list(buffer)
    finally:
        assert lib.sf_close(handle) == 0, 'Decoder close failed: ' + path.name


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, round((len(ordered) - 1) * fraction))]


def signal_metrics(samples):
    assert all(math.isfinite(value) for value in samples), 'Non-finite audio sample'
    peak = max(abs(value) for value in samples)
    rms = math.sqrt(math.fsum(value * value for value in samples) / len(samples))
    dc = math.fsum(samples) / len(samples)
    differences = [samples[i + 1] - samples[i] for i in range(len(samples) - 1)]
    delta_p999 = percentile([abs(value) for value in differences], .999)
    second_p999 = percentile([abs(differences[i + 1] - differences[i]) for i in range(len(differences) - 1)], .999)
    join = samples[0] - samples[-1]
    slope_jump = max(abs(join - differences[-1]), abs(differences[0] - join))
    jump_limit = max(.02, 3 * delta_p999)
    slope_limit = max(.02, 3 * second_p999)
    checks = {'non_silent_level': .005 <= rms < .5 and peak >= .02,
              'mix_headroom': peak < .8, 'dc_bias': abs(dc) <= .003 and abs(dc) / max(rms, 1e-12) <= .03,
              'loop_boundary': abs(join) <= jump_limit, 'loop_slope': slope_jump <= slope_limit}
    return {'sample_rate_hz': RATE, 'channels': 1, 'frames': len(samples), 'duration_seconds': len(samples) / RATE,
            'rms': rms, 'peak': peak, 'peak_dbfs': 20 * math.log10(max(peak, 1e-12)), 'dc_mean': dc, 'dc_fraction_of_rms': abs(dc) / max(rms, 1e-12),
            'clipped_samples': sum(abs(value) >= 1 for value in samples),
            'loop_boundary_jump': abs(join), 'loop_boundary_jump_limit': jump_limit,
            'loop_slope_jump': slope_jump, 'loop_slope_jump_limit': slope_limit,
            'adjacent_delta_p999': delta_p999, 'second_difference_p999': second_p999,
            'checks': checks, 'status': 'passed' if all(checks.values()) else 'failed'}


def definitions(profiles, required):
    path = ASSETS / 'sounds.json'
    if not path.exists():
        assert not required, 'sounds.json has not been created yet'
        return {'status': 'pending', 'reason': 'sounds.json has not been created yet'}
    events = json.loads(path.read_text())
    languages = {locale: json.loads((ASSETS / 'lang' / (locale + '.json')).read_text()) for locale in ('en_us', 'ja_jp')}
    matched, checked = set(), []
    for event, spec in events.items():
        references = spec.get('sounds', [])
        machine_files = []
        for sound in references:
            name = sound if isinstance(sound, str) else sound['name']
            namespace, resource = name.split(':', 1) if ':' in name else ('minecraft', name)
            if namespace != NAMESPACE or not resource.startswith('machine/'):
                continue
            assert isinstance(sound, str) or sound.get('type', 'file') == 'file', (event, 'Expected a file source')
            assert not resource.endswith('.ogg') and (ASSETS / 'sounds' / (resource + '.ogg')).is_file(), (event, name)
            profile = resource.removeprefix('machine/')
            assert profile in profiles, (event, 'Unrecognised machine loop', profile)
            matched.add(profile)
            machine_files.append(name)
        if not machine_files:
            continue
        subtitle = spec.get('subtitle')
        assert subtitle and all(isinstance(values.get(subtitle), str) and values[subtitle].strip() for values in languages.values()), \
            (event, 'Missing English/Japanese subtitle', subtitle)
        checked.append({'event': event, 'files': machine_files, 'subtitle': subtitle})
    assert matched == profiles, ('Machine loops missing from sounds.json', sorted(profiles - matched))
    return {'status': 'passed', 'events': checked}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-definitions', action='store_true', help='fail while sounds.json or its subtitles are missing')
    parser.add_argument('--output', type=Path, default=Path('/workspace/outputs/powders-compact-reactor-sounds/audio-validation.json'))
    args = parser.parse_args()
    source = ast.parse((ROOT / 'tools/generate_machine_sounds.py').read_text())
    profiles = set(next(ast.literal_eval(node.value) for node in source.body if isinstance(node, ast.Assign)
                        and any(isinstance(target, ast.Name) and target.id == 'PROFILES' for target in node.targets)))
    files = {path.stem: path for path in (ASSETS / 'sounds/machine').glob('*.ogg')}
    assert profiles and files.keys() == profiles, ('Missing or unexpected loop assets', sorted(profiles), sorted(files))
    lib = sound_library()
    report = {'assessment': 'Numerical decode/signal/resource validation; no listening assessment is claimed.',
              'decoder': lib.sf_version_string().decode(),
              'source_generator_sha256': hashlib.sha256((ROOT / 'tools/generate_machine_sounds.py').read_bytes()).hexdigest(),
              'limits': {'rms_min': .005, 'peak_max_exclusive': .8, 'absolute_dc_max': .003, 'dc_rms_fraction_max': .03,
                         'boundary_rule': 'maximum of 0.02 full-scale and three times the 99.9th percentile of ordinary sample deltas'},
              'loops': {}}
    failures = []
    for name, path in sorted(files.items()):
        try:
            metrics = signal_metrics(decode(lib, path)) | {'bytes': path.stat().st_size, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
            report['loops'][name] = metrics
            failures.extend(name + ': ' + check for check, passed in metrics['checks'].items() if not passed)
        except AssertionError as error:
            report['loops'][name] = {'status': 'failed', 'reason': str(error)}
            failures.append(name + ': ' + str(error))
    try:
        report['resources'] = definitions(profiles, args.require_definitions)
    except AssertionError as error:
        report['resources'] = {'status': 'failed', 'reason': str(error)}
        failures.append('resources: ' + str(error))
    report['status'] = 'failed' if failures else 'passed' if report['resources']['status'] == 'passed' else 'audio_passed_resources_pending'
    report['failures'] = failures
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(f'{len(files)} machine loops checked; status {report["status"]}, resources {report["resources"]["status"]}. Report: {args.output}')
    if failures:
        raise SystemExit('\n'.join(failures))


if __name__ == '__main__':
    main()
