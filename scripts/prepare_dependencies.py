#!/usr/bin/env python3
"""Prepare pinned official AE2 and source-built FortyTwoCn GT without ambient Maven local.
Run with JDK 21 (javac, not only a JRE) on PATH/JAVA_HOME. Requires git, Python 3,
network access to the repositories used by the pinned upstream Gradle builds.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / 'dependencies.lock.json').read_text())
WORK = ROOT / 'work' / 'dependencies'
DEPS = ROOT / 'dependencies'
INIT = ROOT / 'gradle' / 'dependency-provenance.init.gradle'


def sha(path):
    return hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()


def run(args, cwd, env=None):
    subprocess.run([str(a) for a in args], cwd=cwd, env=env, check=True)


def checkout(url, commit, destination):
    if not destination.exists():
        destination.mkdir(parents=True)
        run(['git', 'init'], destination)
        run(['git', 'fetch', '--depth=1', url, commit], destination)
        run(['git', 'checkout', '--detach', 'FETCH_HEAD'], destination)
    actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=destination, text=True).strip()
    if actual != commit:
        raise RuntimeError(f'Wrong source revision in {destination}: {actual}')
    return destination


def gradle(repo, args, url, commit, patch='none'):
    env = os.environ.copy()
    # This explicitly isolates Maven local, including the patched ModularUI publication.
    env['JAVA_TOOL_OPTIONS'] = env.get('JAVA_TOOL_OPTIONS', '') + f' -Dmaven.repo.local="{WORK / "m2"}"'
    wrapper = ['cmd', '/c', 'gradlew.bat'] if os.name == 'nt' else ['sh', './gradlew']
    run(wrapper + ['--no-daemon', '--max-workers=3', '-I', str(INIT),
        f'-PdependencySourceUrl={url}', f'-PdependencySourceCommit={commit}',
        f'-PdependencyPatchSha256={patch}'] + args, repo, env)


def main():
    DEPS.mkdir(exist_ok=True)
    WORK.mkdir(parents=True, exist_ok=True)
    ae = LOCK['ae2']
    target = DEPS / ae['artifact']
    if not target.exists() or sha(target) != ae['sha256']:
        with urllib.request.urlopen(ae['download']) as response, target.open('wb') as output:
            shutil.copyfileobj(response, output)
    if sha(target) != ae['sha256']:
        raise RuntimeError('Official AE2 release checksum mismatch')

    guide = LOCK['guideme']
    guide_target = DEPS / guide['artifact']
    if not guide_target.exists() or sha(guide_target) != guide['sha256']:
        with urllib.request.urlopen(guide['download']) as response, guide_target.open('wb') as output:
            shutil.copyfileobj(response, output)
    if sha(guide_target) != guide['sha256']:
        raise RuntimeError('GuideME checksum mismatch')

    gt = LOCK['gt']
    target = DEPS / gt['artifact']
    if '--rebuild' in sys.argv or not target.exists() or sha(target) != gt['sha256']:
        source = checkout(gt['url'], gt['commit'], WORK / 'gt')
        mui_revision = LOCK['modularui']['commit']
        mui_url = LOCK['modularui']['url']
        mui = checkout(mui_url, mui_revision, WORK / 'modularui')
        patch = source / 'patches/modularui/jei-19.57-preview.diff'
        if sha(patch) != LOCK['modularui']['patch_sha256']:
            raise RuntimeError('GT ModularUI patch checksum mismatch')
        status = subprocess.check_output(['git', 'status', '--porcelain'], cwd=mui, text=True)
        if not status:
            run(['git', 'apply', '--check', patch], mui)
            run(['git', 'apply', patch], mui)
        else:
            # Only the exact tracked patch is accepted on re-runs.
            run(['git', 'apply', '--reverse', '--check', patch], mui)
            untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=mui)
            if untracked:
                raise RuntimeError('Unexpected untracked files in patched ModularUI checkout')
        actual = subprocess.check_output(['git', 'diff', '--binary'], cwd=mui)
        if hashlib.sha256(actual).hexdigest() != LOCK['modularui']['patched_git_diff_sha256']:
            raise RuntimeError('Patched ModularUI source differs from pinned patch result')
        gradle(mui, ['test', 'publishToMavenLocal'], mui_url, mui_revision, sha(patch))
        if subprocess.check_output(['git', 'status', '--porcelain'], cwd=source, text=True):
            raise RuntimeError('GT checkout has unexpected source edits')
        gradle(source, ['jar', 'verifyMixinConfigs'], gt['url'], gt['commit'])
        shutil.copy2(source / 'build/libs' / gt['artifact'], target)
        if gt['sha256'] is not None and sha(target) != gt['sha256']:
            raise RuntimeError('Source-built GT checksum mismatch; retain build logs and compare JDK/toolchain')

    compile_only = DEPS / 'compileOnly'
    compile_only.mkdir(exist_ok=True)
    with zipfile.ZipFile(target) as archive:
        manifest = archive.read('META-INF/MANIFEST.MF').decode()
        if gt['commit'] not in manifest.replace('\r\n ', ''):
            raise RuntimeError('GT artifact lacks pinned source provenance')
        for item in archive.namelist():
            if item.startswith('META-INF/jarjar/') and item.endswith('.jar'):
                (compile_only / Path(item).name).write_bytes(archive.read(item))
    evidence = {'guideme': {'sha256': sha(guide_target), 'version': guide['version']},
                'ae2': {'sha256': sha(DEPS / ae['artifact']), 'commit': ae['commit']},
                'gt': {'sha256': sha(target), 'commit': gt['commit'], 'url': gt['url']}}
    (DEPS / 'provenance.json').write_text(json.dumps(evidence, indent=2) + '\n')
    print(json.dumps(evidence, indent=2))


if __name__ == '__main__':
    main()
