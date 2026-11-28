#!/usr/bin/env python3
"""Validate a version tag and package the distributable NeoForge mods (core and both addons)."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def properties(path):
    values = {}
    for raw in path.read_text(encoding='utf-8').splitlines():
        line = raw.strip()
        if not line or line.startswith(('#', '!')) or '=' not in line:
            continue
        key, value = line.split('=', 1)
        values[key.strip()] = value.strip()
    return values


def release_metadata(tag):
    config = properties(ROOT / 'gradle.properties')
    version = config['mod_version']
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?', version):
        raise ValueError(f'Unsupported release version: {version}')
    if tag != 'v' + version:
        raise ValueError(f'Tag {tag!r} must match mod_version: v{version}')
    return config, version


ADDONS = {'create_rnr_atomics': 'net/createplant/atomics/CreateRnrAtomics.class',
          'create_rnr_logistics': 'net/createplant/logistics/CreateRnrLogistics.class'}


def check_jar(jar, config, version):
    """Return the mod id of a distributable JAR after checking its metadata against this release."""
    with zipfile.ZipFile(jar) as archive:
        if archive.testzip() is not None:
            raise ValueError(f'{jar.name}: JAR integrity check failed')
        metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        mods = [mod for mod in metadata.get('mods', []) if mod.get('version') == version]
        if len(mods) != 1 or mods[0].get('modId') not in {config['mod_id'], *ADDONS}:
            raise ValueError(f'{jar.name}: JAR mod ID or version does not match this release')
        mod_id = mods[0]['modId']
        dependencies = metadata['dependencies'][mod_id]
        expected = {'minecraft': '[' + config['minecraft_version'] + ']',
                    'neoforge': '[' + config['neo_version'] + ',)', 'create': '[6.0.10,6.0.11)'}
        if mod_id in ADDONS:
            expected[config['mod_id']] = '[' + version + ',)'
        for name, version_range in expected.items():
            if not any(dep.get('modId') == name and dep.get('type') == 'required'
                       and dep.get('versionRange') == version_range for dep in dependencies):
                raise ValueError(f'{jar.name}: unexpected {name} dependency in JAR')
        main_class = ADDONS.get(mod_id, 'net/createplant/CreatePlant.class')
        if main_class not in archive.namelist():
            raise ValueError(f'{jar.name}: JAR is missing the main mod class')
    return mod_id


def package(config, version, output):
    jars = sorted(path for path in (ROOT / 'build/libs').glob('*.jar')
                  if not path.name.endswith(('-sources.jar', '-javadoc.jar', '-dev.jar')))
    ids = [check_jar(jar, config, version) for jar in jars]
    if sorted(ids) != sorted([config['mod_id'], *ADDONS]):
        raise ValueError(f'Expected the core and both addon JARs once each, found {ids}')

    output.mkdir(parents=True, exist_ok=True)
    for jar in jars:
        (output / jar.name).write_bytes(jar.read_bytes())
    zipped = output / 'create-plant-release.zip'
    with zipfile.ZipFile(zipped, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for jar in jars:
            archive.write(jar, arcname=jar.name)
    with zipfile.ZipFile(zipped) as archive:
        if sorted(archive.namelist()) != sorted(jar.name for jar in jars) or archive.testzip() is not None \
                or any(archive.read(jar.name) != jar.read_bytes() for jar in jars):
            raise ValueError('Release ZIP does not contain exactly the distributable JARs')
    assets = [output / jar.name for jar in jars] + [zipped]
    sums = ''.join(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in assets)
    (output / 'SHA256SUMS').write_text(sums, encoding='ascii')
    print(json.dumps({'jars': [jar.name for jar in jars], 'output': str(output)}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--metadata-only', action='store_true')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/release')
    args = parser.parse_args()
    config, version = release_metadata(args.tag)
    if args.metadata_only:
        print('version=' + version)
        print('prerelease=' + str('-' in version.split('+', 1)[0]).lower())
    else:
        package(config, version, args.output)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        print(f'Release preparation failed: {error}', file=sys.stderr)
        raise SystemExit(1)
