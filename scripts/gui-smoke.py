#!/usr/bin/env python3
"""Run the packaged plugin through an isolated synthetic-only JOSM GUI load.

Requires Python 3, Xvfb, and the pinned local Java/JOSM dependencies. Uses a
cookie-authenticated TCP X display because Unix X sockets fail in some containers.
No production JOSM preferences, GPS, or API credentials are accessed.
"""
import os
import argparse
from pathlib import Path
import secrets
import shutil
import signal
import socket
import struct
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parent.parent
TOOLCHAIN = Path.home() / '.hermes/cache/toolchains'
JAVA = os.environ.get('JAVA', str(TOOLCHAIN / 'jdk17/bin/java'))
JOSM = Path(os.environ.get('JOSM_JAR', str(TOOLCHAIN / 'josm-19678.jar'))).resolve()


def authority_record(display, cookie):
    fields = (b'', str(display).encode('ascii'), b'MIT-MAGIC-COOKIE-1', cookie)
    return struct.pack('>H', 65535) + b''.join(struct.pack('>H', len(x)) + x for x in fields)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--renderer", action="store_true", help="also measure actual 50k-point MapView pan/zoom paints")
    parser.add_argument("--points", type=int, choices=(50000, 100000, 250000), default=50000,
                        help="aggregate synthetic points for the optional renderer measurement")
    args = parser.parse_args()
    if not (ROOT / 'dist/dawarich.jar').is_file():
        raise SystemExit('Build dist/dawarich.jar before running GUI smoke.')
    output_root = ROOT / 'build'
    output_root.mkdir(exist_ok=True)
    base = Path(tempfile.mkdtemp(prefix='gui-smoke-', dir=output_root))
    base.chmod(0o700)
    home = base / 'profile'
    (home / 'plugins').mkdir(parents=True)
    shutil.copy(ROOT / 'dist/dawarich.jar', home / 'plugins/dawarich.jar')
    (home / 'preferences.xml').write_text(
        '<preferences xmlns="http://josm.openstreetmap.de/preferences-1.0" version="19678">'
        '<list key="plugins"><entry value="dawarich"/></list>'
        '<tag key="pluginmanager.version" value="19678"/>'
        '<tag key="pluginmanager.lastupdate" value="' + str(int(time.time())) + '"/>'
        '<tag key="pluginmanager.check-for-updates" value="false"/></preferences>', encoding='utf-8')
    display = None
    for candidate in range(90, 150):
        with socket.socket() as probe:
            try:
                probe.bind(('127.0.0.1', 6000 + candidate))
                display = candidate
                break
            except OSError:
                pass
    if display is None:
        raise SystemExit('No free display port for isolated Xvfb smoke.')
    auth = base / 'Xauthority'
    auth.write_bytes(authority_record(display, secrets.token_bytes(16)))
    auth.chmod(0o600)
    xvfb = None
    java = None
    try:
        with (base / 'xvfb.log').open('w') as xlog, (base / 'josm.log').open('w') as jlog:
            xvfb = subprocess.Popen(['Xvfb', ':' + str(display), '-screen', '0', '1280x900x24',
                                     '-listen', 'tcp', '-auth', str(auth)], stdout=xlog, stderr=subprocess.STDOUT)
            ready = False
            for _ in range(50):
                if xvfb.poll() is not None:
                    raise RuntimeError('Xvfb exited; inspect the local smoke log.')
                try:
                    with socket.create_connection(('127.0.0.1', 6000 + display), timeout=0.2):
                        ready = True
                        break
                except OSError:
                    time.sleep(0.1)
            if not ready:
                raise RuntimeError('Xvfb did not become ready.')
            java = subprocess.Popen([
                JAVA, '-Djosm.home=' + str(home), '-Dsmoke.out=' + str(base),
                '-Dsmoke.render=' + str(args.renderer).lower(),
                '-Dsmoke.points=' + str(args.points),
                '-cp', str(JOSM), str(ROOT / 'test/gui/JosmGuiSmoke.java')], cwd=base,
                env=dict(os.environ, DISPLAY='127.0.0.1:' + str(display), XAUTHORITY=str(auth)),
                stdout=jlog, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                code = java.wait(timeout=60)
            except subprocess.TimeoutExpired:
                os.killpg(java.pid, signal.SIGTERM)
                java.wait(timeout=5)
                raise RuntimeError('GUI smoke timed out; inspect the local smoke log.') from None
        text = (base / 'josm.log').read_text(encoding='utf-8', errors='replace')
        for line in text.splitlines():
            if line.startswith('SMOKE_'):
                print(line)
        print('Synthetic-only smoke receipts:', base.relative_to(ROOT))
        if code != 0 or 'SMOKE_OSM_UNCHANGED=true; SHUTDOWN_RELEASED=true' not in text:
            raise SystemExit('Packaged GUI smoke failed; inspect build/gui-smoke-*/josm.log.')
    finally:
        if java is not None and java.poll() is None:
            os.killpg(java.pid, signal.SIGTERM)
            java.wait(timeout=5)
        if xvfb is not None and xvfb.poll() is None:
            xvfb.terminate()
            xvfb.wait(timeout=5)
        auth.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
