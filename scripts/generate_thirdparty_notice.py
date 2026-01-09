#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Generate THIRDPARTY_NOTICE.txt from build-generated OSS license resources.

Behavior:
- Searches common generated locations (e.g. under app/build/generated/third_party_licenses)
  for the files `third_party_license_metadata` and `third_party_licenses`.
- Each metadata line is expected in the format: "offset:length Library Name".
- The `third_party_licenses` blob is read in binary and offsets/lengths are treated as bytes.
- The extracted bytes are decoded using UTF-8, with a fallback to latin-1 when UTF-8 decoding fails.

Note: The build must have run (so the generated resources exist) before running this script.
"""

import os
import sys
import argparse

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
# Search roots: prefer build-generated third_party_licenses, fall back to src/main/res/raw
SEARCH_ROOTS = [
    os.path.join(ROOT, 'app', 'build', 'generated', 'third_party_licenses'),
    os.path.join(ROOT, 'app', 'src', 'main', 'res', 'raw'),
]

METADATA_NAME = 'third_party_license_metadata'
LICENSES_NAME = 'third_party_licenses'
OUTPUT_NAME = 'THIRDPARTY_NOTICE.txt'


def find_file(root, name):
    for dirpath, dirnames, filenames in os.walk(root):
        if name in filenames:
            return os.path.join(dirpath, name)
    return None


def main():
    parser = argparse.ArgumentParser(description='Generate THIRDPARTY notice from metadata and combined licenses')
    parser.add_argument('--root', help='search root (absolute or relative to project root)')
    parser.add_argument('--metadata', help='explicit path to third_party_license_metadata')
    parser.add_argument('--licenses', help='explicit path to third_party_licenses')
    parser.add_argument('--output', help='output filename', default=OUTPUT_NAME)
    parser.add_argument('--fetch', action='store_true', help='If set, follow license URLs and attempt to fetch full license text')
    parser.add_argument('--fetch-timeout', type=float, default=8.0, help='Timeout seconds for HTTP fetch when --fetch is used')
    parser.add_argument('--embed-all', action='store_true', help='If set with --fetch, embed all fetched texts regardless of detected license kind')
    args = parser.parse_args()

    # Priority: use explicit --metadata and --licenses if provided
    metadata_path = None
    licenses_path = None
    # Force single canonical output filename to avoid generating multiple notice files
    out_name = OUTPUT_NAME

    if args.metadata and args.licenses:
        metadata_path = args.metadata
        licenses_path = args.licenses
        # 相対パスならプロジェクトルートに対する相対とみなす
        if not os.path.isabs(metadata_path):
            metadata_path = os.path.join(ROOT, metadata_path)
        if not os.path.isabs(licenses_path):
            licenses_path = os.path.join(ROOT, licenses_path)

    else:
        # If --root is given, search it first
        search_roots = []
        if args.root:
            r = args.root if os.path.isabs(args.root) else os.path.join(ROOT, args.root)
            search_roots.append(r)
        # 既存の既定ルートを追加
        search_roots.extend(SEARCH_ROOTS)

        # Find existing search roots
        existing_roots = [p for p in search_roots if os.path.isdir(p)]
        if not existing_roots:
            print(f"No search roots found. Tried: {search_roots}")
            sys.exit(2)

        # 各ルートでファイルを探す（最初に見つかったものを使用）
        for root in existing_roots:
            if not metadata_path:
                metadata_path = find_file(root, METADATA_NAME)
            if not licenses_path:
                licenses_path = find_file(root, LICENSES_NAME)
            if metadata_path and licenses_path:
                break

    if not metadata_path:
        print(f"Metadata file not found: {METADATA_NAME}")
        sys.exit(2)
    if not licenses_path:
        print(f"Combined licenses file not found: {LICENSES_NAME}")
        sys.exit(2)

    print(f"using metadata: {metadata_path}")
    print(f"using licenses: {licenses_path}")
    print(f"output (forced): {out_name}")

    # Read metadata and build raw entries list
    entries = []  # list of (lib_name, content_or_url)
    with open(metadata_path, 'r', encoding='utf-8', errors='replace') as f_meta, \
         open(licenses_path, 'rb') as f_lic:
        for lineno, rawline in enumerate(f_meta, start=1):
            line = rawline.strip()
            if not line:
                continue
            if ' ' not in line:
                print(f"Warning: invalid metadata line format (line {lineno}): {line}")
                continue
            parts = line.split(' ', 1)
            range_info = parts[0]
            lib_name = parts[1] if len(parts) > 1 else '<unknown>'

            if ':' not in range_info:
                print(f"Warning: invalid range format (line {lineno}): {range_info}")
                continue
            start_str, length_str = range_info.split(':', 1)
            try:
                offset = int(start_str)
                length = int(length_str)
            except ValueError:
                print(f"Warning: offset/length not integers (line {lineno}): {range_info}")
                continue

            try:
                f_lic.seek(offset)
            except OSError as e:
                print(f"Error: failed to seek to offset (line {lineno}): {e}")
                continue

            content_bytes = f_lic.read(length)
            try:
                content = content_bytes.decode('utf-8')
            except Exception:
                try:
                    content = content_bytes.decode('latin-1')
                except Exception:
                    content = repr(content_bytes)

            entries.append((lib_name, content))

    # Optionally fetch URLs and group identical license texts together
    fetch = args.fetch
    fetch_timeout = args.fetch_timeout
    # helper: find first http(s) URL in text
    import re
    url_re = re.compile(r'https?://\S+')

    # heuristic classifier used to decide whether to embed full text
    def classify_license_text(text):
        if not text:
            return 'Unknown'
        lower = text.lower()
        if 'apache license' in lower or 'apache.org/licenses' in lower:
            return 'Apache-2.0'
        if 'mit license' in lower or 'permission is hereby granted, free of charge' in lower:
            return 'MIT'
        if 'bsd' in lower and ('2-clause' in lower or '3-clause' in lower or 'bsd license' in lower):
            if '3-clause' in lower or 'three-clause' in lower:
                return 'BSD-3-Clause'
            return 'BSD-2-Clause'
        if 'isc license' in lower:
            return 'ISC'
        if 'gnu lesser general public' in lower or 'lgpl' in lower:
            return 'LGPL'
        if 'gnu affero general public' in lower or 'agpl' in lower:
            return 'AGPL'
        if 'gnu general public' in lower or ' gpl ' in lower:
            return 'GPL'
        if 'mozilla public' in lower or 'mpl' in lower:
            return 'MPL'
        if 'eclipse public' in lower or 'epl' in lower:
            return 'EPL'
        if 'open font license' in lower or 'ofl' in lower or 'sil' in lower:
            return 'SIL-OFL'
        if 'creative commons' in lower or 'creativecommons' in lower:
            return 'CC'
        # fallback: look for common license urls
        if 'apache.org/licenses' in lower:
            return 'Apache-2.0'
        if 'opensource.org/licenses/mit' in lower:
            return 'MIT'
        return 'Unknown'

    # default embed whitelist: license kinds for which we will include full text
    EMBED_WHITELIST = set(['Apache-2.0', 'MIT', 'BSD-2-Clause', 'BSD-3-Clause', 'ISC', 'GPL', 'LGPL', 'AGPL', 'MPL', 'EPL', 'SIL-OFL', 'CC'])

    # prepare mapping: key -> {'libs': [names], 'text': license_text, 'urls': set()}
    groups = {}

    # attempt to import requests if fetching is enabled
    http_get = None
    if fetch:
        try:
            import requests
            def http_get(u, timeout):
                return requests.get(u, timeout=timeout, headers={'User-Agent': 'ThirdPartyNoticeFetcher/1.0'})
        except Exception:
            # fallback to urllib
            import urllib.request
            def http_get(u, timeout):
                req = urllib.request.Request(u, headers={'User-Agent': 'ThirdPartyNoticeFetcher/1.0'})
                with urllib.request.urlopen(req, timeout=timeout) as r:
                    class R:
                        status = r.getcode()
                        text = r.read().decode('utf-8', errors='replace')
                    return R()

    for lib_name, content in entries:
        # classify content as URL-only if it's short and contains http
        m = url_re.search(content)
        license_text = None
        license_url = None
        # If the content we read from the combined licenses is itself HTML, avoid embedding it.
        # Try to extract a canonical URL from the HTML (rel="canonical" href=...) or any http(s) URL.
        if re.search(r'(?i)<!doctype|<html', content):
            can = None
            # try to find rel="canonical" first
            mcan = re.search(r'rel=["\']canonical["\'][^>]*href=["\']([^"\']+)["\']', content, flags=re.IGNORECASE)
            if mcan:
                can = mcan.group(1)
            else:
                murl = url_re.search(content)
                if murl:
                    can = murl.group(0)
            if can:
                print(f"Info: extracted URL from embedded HTML for {lib_name}: {can}; recording as URL reference.")
                license_text = f"[License URL] {can}"
                license_url = can
            else:
                print(f"Info: embedded HTML found for {lib_name} but no URL extracted; omitting HTML from notice.")
                license_text = f"[License HTML content omitted]"
                license_url = None
            # proceed to grouping
            key = '\n'.join([line.rstrip() for line in license_text.splitlines() if line.strip()])
            if key in groups:
                groups[key]['libs'].append(lib_name)
                if license_url:
                    groups[key]['urls'].add(license_url)
            else:
                groups[key] = {'libs': [lib_name], 'text': license_text, 'urls': set([license_url]) if license_url else set()}
            continue

        # classify as URL if short and contains http
        if m and len(content.strip()) < 800:
            license_url = m.group(0)
            fetched = None
            if fetch:
                try:
                    resp = http_get(license_url, timeout=fetch_timeout)
                    status = getattr(resp, 'status', None)
                    if status is None:
                        # requests.Response
                        status = resp.status_code
                        text = resp.text
                    else:
                        text = resp.text
                    if 200 <= status < 400 and text:
                        fetched = text
                except Exception as e:
                    print(f"Warning: failed to fetch {license_url}: {e}")
            if fetched:
                fetched = fetched.strip()
                # If the fetched content looks like an HTML page, do not embed it as license text.
                # Many web pages (terms pages) return HTML; embedding raw HTML is noisy and not a license.
                if re.search(r'(?i)<!doctype|<html', fetched):
                    print(f"Info: fetched content from {license_url} appears to be HTML; keeping URL reference instead of embedding.")
                    license_text = f"[License URL] {license_url}"
                else:
                    kind = classify_license_text(fetched)
                    # decide whether to embed based on whitelist or override
                    if args.embed_all or kind in EMBED_WHITELIST:
                        license_text = fetched
                    else:
                        # keep URL-only when license kind not in whitelist
                        license_text = f"[License URL] {license_url}"
            else:
                # fallback to storing URL as the 'text'
                license_text = f"[License URL] {license_url}"
        else:
            license_text = content.strip()

        # normalize key by collapsing whitespace
        key = '\n'.join([line.rstrip() for line in license_text.splitlines() if line.strip()])
        if key in groups:
            groups[key]['libs'].add(lib_name)
            if license_url:
                groups[key]['urls'].add(license_url)
        else:
            groups[key] = {'libs': set([lib_name]), 'text': license_text, 'urls': set([license_url]) if license_url else set()}

    # write output grouped
    with open(out_name, 'w', encoding='utf-8') as f_out:
        header = ("NOTICES\n\n"
                  "This repository incorporates material as listed below or described in the code. "
                  "The following third-party software components may be included in or distributed with this project.\n\n"
                  "For each component, the entry contains either the full license text or, if the full text is not present, a URL or short reference to where the license can be found.\n\n")
        f_out.write(header)

        # Track libraries that have already been emitted to avoid duplicates across groups
        seen_libs = set()
        for key, info in groups.items():
            # filter libs that are already emitted
            libs = [lib for lib in sorted(info['libs']) if lib not in seen_libs]
            if not libs:
                # nothing to emit for this group
                continue

            f_out.write('================================================================\n')
            # determine whether this group is fonts
            names_lower = [n.lower() for n in libs]
            text_lower = (info['text'] or '').lower()
            is_font = any(('mplus' in n or 'yomogi' in n or 'font' in n or 'typeface' in n or n.endswith('.ttf') or n.endswith('.otf')) for n in names_lower) or ('open font license' in text_lower or '\nofl' in text_lower or ' ofl ' in text_lower)

            # write header as Font(s) when detected, otherwise Library(ies)
            if is_font:
                if len(libs) == 1:
                    f_out.write(f"Font: {libs[0]}\n")
                else:
                    f_out.write('Fonts:\n')
                    for lib in libs:
                        f_out.write(f" - {lib}\n")
            else:
                if len(libs) == 1:
                    f_out.write(f"Library: {libs[0]}\n")
                else:
                    f_out.write('Libraries:\n')
                    for lib in libs:
                        f_out.write(f" - {lib}\n")

            f_out.write('================================================================\n')
            # write license text
            f_out.write(info['text'] + '\n\n')

            # mark emitted libs
            for lib in libs:
                seen_libs.add(lib)

    print(f"{out_name} has been generated ({len(groups)} grouped entries from {len(entries)} raw entries).")

    # Cleanup: remove other THIRDPARTY_NOTICE_* files to ensure only the canonical file exists
    removed = []
    for fname in os.listdir(ROOT):
        if fname.startswith('THIRDPARTY_NOTICE') and fname != out_name and fname.endswith('.txt'):
            try:
                os.remove(os.path.join(ROOT, fname))
                removed.append(fname)
            except Exception:
                pass
    if removed:
        print('Removed duplicate notice files: ' + ', '.join(removed))


if __name__ == '__main__':
    main()

