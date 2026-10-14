#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p deliverables
cp app/build/outputs/apk/debug/app-debug.apk deliverables/V2V-0.1.0-m1-debug.apk
(cd deliverables && sha256sum V2V-0.1.0-m1-debug.apk > SHA256SUMS)
