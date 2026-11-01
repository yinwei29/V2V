#!/usr/bin/env python3
"""Copy verified public V2V artifacts for manual release upload. No API/provider credentials."""
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.request

build = os.environ['V2V_CIRCLECI_BUILD']
source = os.environ['V2V_CIRCLECI_SOURCE']
expected = os.environ['V2V_APK_SHA256'].lower()
tag = os.environ['V2V_TAG']
if not (re.fullmatch(r'[1-9][0-9]*', build) and re.fullmatch(r'[0-9a-f]{40}', source)
        and re.fullmatch(r'[0-9a-f]{64}', expected) and re.fullmatch(r'v[0-9A-Za-z._-]+', tag)):
    raise SystemExit('Invalid delivery selectors')
base = f'https://circleci.com/api/v1.1/project/github/yinwei29/V2V/{build}'
def get_json(url):
    with urllib.request.urlopen(url, timeout=45) as response:
        return json.load(response)
job = get_json(base)
if job.get('status') != 'success' or job.get('vcs_revision') != source:
    raise SystemExit('CircleCI build/source is not the specified successful V2V build')
artifacts = get_json(base + '/artifacts')
urls = {x['path']: x['url'] for x in artifacts}
out = Path('delivery')
out.mkdir(exist_ok=False)
for name in ['verification.json', 'installation.json', 'SHA256SUMS']:
    with urllib.request.urlopen(urls['deliverables/' + name], timeout=45) as response:
        (out / name).write_bytes(response.read())
verification = json.loads((out / 'verification.json').read_text())
installation = json.loads((out / 'installation.json').read_text())
if verification['version'] != tag[1:] or installation != verification['installation']:
    raise SystemExit('Release version/installation metadata mismatch')
if any(verification['junit'][k] for k in ['failures', 'errors', 'skipped']) or verification['junit']['tests'] <= 0:
    raise SystemExit('Unit verification is incomplete or unsuccessful')
if any(verification['lint'][k] for k in ['Error', 'Fatal']):
    raise SystemExit('Lint verification failed')
pin = Path('scripts/debug-signer.sha256').read_text().strip()
if installation['applicationId'] != 'com.yinwei.v2v.preview' or not installation['apkV2Verified'] or installation['certificateSha256'] != pin:
    raise SystemExit('Preview identity/signature mismatch')
checksum = verification['apkChecksums'].split()
if len(checksum) != 2 or checksum[0] != expected or not re.fullmatch(r'V2V-[0-9A-Za-z._-]+-preview.apk', checksum[1]):
    raise SystemExit('APK identity/checksum metadata mismatch')
if (out / 'SHA256SUMS').read_text().strip() != verification['apkChecksums']:
    raise SystemExit('Checksum file mismatch')
hash_value = hashlib.sha256()
with urllib.request.urlopen(urls['deliverables/' + checksum[1]], timeout=60) as response, (out / checksum[1]).open('wb') as file:
    while chunk := response.read(1048576):
        hash_value.update(chunk)
        file.write(chunk)
if hash_value.hexdigest() != expected:
    raise SystemExit('Downloaded APK digest mismatch')
print(f'Verified V2V {tag}: CircleCI build {build}, source {source}, APK SHA256 {expected}')
