#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=$(python3 - <<'PY'
import json,re
with open('app/build/outputs/apk/debug/output-metadata.json') as f:
    version=json.load(f)['elements'][0]['versionName']
assert re.fullmatch(r'[A-Za-z0-9._-]+',version)
print(version)
PY
)
mkdir -p deliverables
filename="V2V-${version}-debug.apk"
cp app/build/outputs/apk/debug/app-debug.apk "deliverables/$filename"
(cd deliverables && sha256sum "$filename" > SHA256SUMS)
