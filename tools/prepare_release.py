#!/usr/bin/env python3
"""Validate a version tag and package the distributable NeoForge mod."""
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


def package(config, version, output):
    jars = sorted(path for path in (ROOT / 'build/libs').glob('*.jar')
                  if not path.name.endswith(('-sources.jar', '-javadoc.jar', '-dev.jar')))
    if len(jars) != 1:
        raise ValueError(f'Expected exactly one distributable JAR, found {len(jars)}')
    jar = jars[0]
    with zipfile.ZipFile(jar) as archive:
        if archive.testzip() is not None:
            raise ValueError('JAR integrity check failed')
        metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        if not any(mod.get('modId') == config['mod_id'] and mod.get('version') == version
                   for mod in metadata.get('mods', [])):
            raise ValueError('JAR mod ID or version does not match this release')
        dependencies = metadata['dependencies'][config['mod_id']]
        expected = {'minecraft': '[' + config['minecraft_version'] + ']',
                    'neoforge': '[' + config['neo_version'] + ',)', 'create': '[6.0.10,6.0.11)'}
        for name, version_range in expected.items():
            if not any(dep.get('modId') == name and dep.get('type') == 'required'
                       and dep.get('versionRange') == version_range for dep in dependencies):
                raise ValueError(f'Unexpected {name} dependency in JAR')
        if 'net/createplant/CreatePlant.class' not in archive.namelist():
            raise ValueError('JAR is missing the main mod class')

    output.mkdir(parents=True, exist_ok=True)
    jar_bytes = jar.read_bytes()
    (output / jar.name).write_bytes(jar_bytes)
    zipped = output / 'create-plant-release.zip'
    with zipfile.ZipFile(zipped, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        archive.write(jar, arcname=jar.name)
    with zipfile.ZipFile(zipped) as archive:
        if archive.namelist() != [jar.name] or archive.testzip() is not None or archive.read(jar.name) != jar_bytes:
            raise ValueError('Release ZIP does not contain exactly the distributable JAR')
    assets = [output / jar.name, zipped]
    sums = ''.join(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in assets)
    (output / 'SHA256SUMS').write_text(sums, encoding='ascii')
    print(json.dumps({'jar': jar.name, 'jar_bytes': len(jar_bytes),
                      'output': str(output), 'sha256': hashlib.sha256(jar_bytes).hexdigest()}))


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
