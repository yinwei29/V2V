#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# CI signing identity must not silently change when the cache expires. Never echo secret data.
python3 - <<'PYTHON'
import base64, os
from pathlib import Path
secret = os.environ.get('V2V_DEBUG_KEYSTORE_BASE64')
if secret:
    try:
        data = base64.b64decode(secret, validate=True)
    except Exception:
        raise SystemExit('Invalid signing variable; no contents logged')
    destination = Path.home() / '.android' / 'debug.keystore'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    destination.chmod(0o600)
PYTHON
keystore="${HOME}/.android/debug.keystore"
if [[ ! -f "$keystore" ]]; then
    echo 'Signing keystore missing. Restore the pinned key through the protected CircleCI variable; see docs/INSTALLATION.md.' >&2
    exit 1
fi
actual=$(keytool -exportcert -keystore "$keystore" -alias androiddebugkey -storepass android 2>/dev/null | sha256sum | cut -d ' ' -f 1)
expected=$(tr -d '\r\n' < scripts/debug-signer.sha256)
if [[ "$actual" != "$expected" ]]; then
    echo 'Signing certificate differs from the pinned Preview identity; stopping before publishing an incompatible update.' >&2
    exit 1
fi
printf 'Pinned Preview signing certificate verified: %s\n' "$actual"
