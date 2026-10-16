#!/usr/bin/env python3
"""Validate dynamically keyed UI resources, coverage and format arguments before Android packaging."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
base = root / 'app/src/main'
files = [base / 'res' / name / 'ui_strings.xml' for name in ('values', 'values-zh-rTW', 'values-in', 'values-ja')]
catalogs = [{node.attrib['name']: ''.join(node.itertext()) for node in ET.parse(file).getroot()} for file in files]
keys = set(catalogs[0])
assert keys and all(set(catalog) == keys for catalog in catalogs), 'Missing locale entries'
for key in keys:
    formats = [re.findall(r'%(?:\d+\$)?(?:\.\d+)?[dsf]|%%', c[key]) for c in catalogs]
    assert all(f == formats[0] for f in formats), f'Format mismatch: {key}'
    assert all(c[key].strip('"').strip() for c in catalogs), f'Empty label: {key}'

def resource_key(text):
    value = 0
    encoded = text.encode('utf-16-be')
    for index in range(0, len(encoded), 2):
        value = (31 * value + int.from_bytes(encoded[index:index+2], 'big')) & 0xffffffff
    return f'ui_{value:x}'

for source in (base / 'java').rglob('*.kt'):
    for literal in re.findall(r'uiText\("((?:[^"\\]|\\.)*)"\)', source.read_text()):
        text = literal.replace('\\"', '"').replace('\\n', '\n')
        assert resource_key(text) in keys, f'Missing resource for {source.name}: {text}'
for source in base.rglob('*.xml'):
    for reference in re.findall(r'@string/(ui_[0-9a-f]+)', source.read_text()):
        assert reference in keys, f'Missing XML resource: {reference}'
print(f'UI catalogs valid: {len(keys)} labels in {len(catalogs)} locales; formatting and Kotlin references verified')
