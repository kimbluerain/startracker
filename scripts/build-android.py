#!/usr/bin/env python3
"""Build with the pinned local toolchain; reuse an optional HTTP(S) proxy safely."""
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlsplit

root = Path(__file__).resolve().parents[1]
env = os.environ.copy()
env.setdefault('JAVA_HOME', str(Path.home() / 'Library/Java/JavaVirtualMachines/jdk-21.0.13+9/Contents/Home'))
env.setdefault('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk'))
args = ['./gradlew', *(sys.argv[1:] or [':app:assembleDebug']), '--console=plain']
proxy = urlsplit(env.get('HTTPS_PROXY', env.get('https_proxy', '')))
if proxy.hostname:
    if proxy.username or proxy.password:
        raise SystemExit('Authenticated proxy: configure Gradle locally; credentials are never passed as command arguments.')
    for protocol in ('https', 'http'):
        args += [f'-D{protocol}.proxyHost={proxy.hostname}', f'-D{protocol}.proxyPort={proxy.port or 80}']
raise SystemExit(subprocess.call(args, cwd=root / 'android', env=env))
