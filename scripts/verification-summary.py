#!/usr/bin/env python3
"""Package actual CircleCI results; no account credentials or user content."""
from pathlib import Path
import json
import xml.etree.ElementTree as E
root = Path(__file__).resolve().parent.parent
suites = [E.parse(p).getroot() for p in (root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
if not suites:
    raise SystemExit('No JUnit results')
counts = {key: sum(int(s.get(key, '0')) for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
issues = E.parse(root / 'app/build/reports/lint-results-debug.xml').getroot().findall('issue')
lint = {level: sum(i.get('severity') == level for i in issues) for level in ('Error', 'Fatal', 'Warning')}
metadata = json.loads((root / 'app/build/outputs/apk/debug/output-metadata.json').read_text())
result = {'version': metadata['elements'][0]['versionName'], 'junit': counts, 'lint': lint,
          'apkChecksums': (root / 'deliverables/SHA256SUMS').read_text().strip(),
          'installation': json.loads((root / 'deliverables/installation.json').read_text()),
          'liveProviderTested': False, 'androidDeviceTested': False}
(root / 'deliverables/verification.json').write_text(json.dumps(result, indent=2) + '\n')
if counts['failures'] or counts['errors'] or lint['Error'] or lint['Fatal']:
    raise SystemExit('Verification failed')
