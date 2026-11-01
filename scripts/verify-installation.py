#!/usr/bin/env python3
"""Check the actual APK signature and install identity with official Android SDK tools."""
from pathlib import Path
import json
import os
import re
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
apk = Path(sys.argv[1]).resolve()
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
    raise SystemExit('Android SDK missing; cannot claim installation verification')
tools = Path(sdk) / 'build-tools' / '35.0.0'
signature = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', signature)
expected = (root / 'scripts/debug-signer.sha256').read_text().strip()
if len(certs) != 1 or certs[0].lower() != expected:
    raise SystemExit('APK signer does not match pinned Preview identity')
if not re.search(r'Verified using v2 scheme.*: true', signature):
    raise SystemExit('APK v2 signature verification failed')
badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
metadata = json.loads((root / 'app/build/outputs/apk/debug/output-metadata.json').read_text())
version = metadata['elements'][0]
if not package or package.groups() != ('com.yinwei.v2v.preview', str(version['versionCode']), version['versionName']):
    raise SystemExit('APK install package/version differs from expected Preview metadata')
if "sdkVersion:'29'" not in badging or "targetSdkVersion:'35'" not in badging:
    raise SystemExit('Unexpected Android compatibility metadata')
if not re.search(r"launchable-activity: name='com.yinwei.v2v.ui.MainActivity'", badging):
    raise SystemExit('Preview launcher class does not resolve to existing V2V implementation')
result = dict(applicationId=package[1], versionCode=int(package[2]), versionName=package[3],
              minSdk=29, targetSdk=35, certificateSha256=expected, apkV2Verified=True,
              parallelToLegacyV2V=True, deviceInstallTested=False)
(root / 'deliverables/installation.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
